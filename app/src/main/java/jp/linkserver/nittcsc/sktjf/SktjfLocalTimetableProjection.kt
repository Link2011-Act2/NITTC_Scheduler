package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import jp.linkserver.nittcsc.logic.TimetableTerm
import jp.linkserver.nittcsc.logic.normalizeSubjectRomanNumerals

internal enum class SktjfLocalProjectionIssue { INVALID_PARTITION, RELINK_REQUIRED, INVALID_SOURCE_LINKS, INCOMPLETE_LESSON, NEEDS_ADDITIONAL_STORAGE }
internal sealed interface SktjfLocalProjection {
    data class Ready(val timetable: SktjfLocalTimetable) : SktjfLocalProjection
    data class Blocked(val issue: SktjfLocalProjectionIssue) : SktjfLocalProjection
}

/** Converts active legacy fields without guessing provenance or modifying existing rows. */
internal fun projectSktjfLocalTimetable(
    year: Int, term: TimetableTerm, classId: String, rows: List<LessonEntity>,
    baseline: SktjfBaseline?, links: List<SktjfSourceLink>, requiresRelinking: Boolean, allowTimed: Boolean = false
): SktjfLocalProjection {
    fun blocked(issue: SktjfLocalProjectionIssue) = SktjfLocalProjection.Blocked(issue)
    val termCode = if (term == TimetableTerm.FIRST) "S" else "F"
    if (rows.any { it.id <= 0 || it.academicYear != year || it.timetableTerm != term || it.dayOfWeek !in 1..7 || it.slotIndex !in 0..11 } ||
        rows.map { it.id }.distinct().size != rows.size || rows.map { it.dayOfWeek to it.slotIndex }.distinct().size != rows.size ||
        (baseline != null && (baseline.academicYear != year || baseline.term != termCode || baseline.classId != classId)))
        return blocked(SktjfLocalProjectionIssue.INVALID_PARTITION)
    if (requiresRelinking) return blocked(SktjfLocalProjectionIssue.RELINK_REQUIRED)
    if (runCatching { SktjfSourceLinks.encode(links) }.isFailure || links.any { it.sourceLessonId !in baseline?.lessons.orEmpty() })
        return blocked(SktjfLocalProjectionIssue.INVALID_SOURCE_LINKS)
    val bySlot = links.associateBy { Triple(it.day, it.period, it.cycle) }
    val result = mutableListOf<SktjfLocalLesson>()
    for (row in rows) {
        val values = if (row.mode == LessonMode.WEEKLY) listOf(Triple(SktjfCycle.WEEKLY, row.weeklySubject to row.weeklyTeacher, row.weeklyLocation))
        else listOf(Triple(SktjfCycle.A, row.aSubject to row.aTeacher, row.aLocation), Triple(SktjfCycle.B, row.bSubject to row.bTeacher, row.bLocation))
        for ((cycle, fields, room) in values) {
            val (subject, teacher) = fields
            if (subject.isBlank() && teacher.isBlank() && room.isNullOrBlank()) continue
            val link = bySlot[Triple(row.dayOfWeek, row.slotIndex + 1, cycle)]
            val original = link?.let { baseline!!.lessons.getValue(it.sourceLessonId) }
            if (original != null && ((!allowTimed && original.weekRange != null) || original.conditions.all.isNotEmpty() || original.conditions.none.isNotEmpty() || original.teachers.size > 1 || original.rooms.size > 1))
                return blocked(SktjfLocalProjectionIssue.NEEDS_ADDITIONAL_STORAGE)
            val kind = if (original != null && normalizeSubjectRomanNumerals(subject) == normalizeSubjectRomanNumerals(original.subject)) original.kind else SktjfLessonKind.NORMAL
            val content = SktjfLessonContent(row.dayOfWeek, row.slotIndex + 1, cycle, subject,
                listOfNotNull(teacher.takeIf { it.isNotBlank() }), listOfNotNull(room?.takeIf { it.isNotBlank() }),
                original?.conditions ?: SktjfConditions(emptySet(), emptySet()), kind, original?.remote,
                if (allowTimed) original?.weekRange else null)
            result += SktjfLocalLesson(sktjfLegacyKey(row.id, cycle), link?.sourceLessonId, content)
        }
    }
    return SktjfLocalProjection.Ready(SktjfLocalTimetable(year, termCode, classId, result))
}
