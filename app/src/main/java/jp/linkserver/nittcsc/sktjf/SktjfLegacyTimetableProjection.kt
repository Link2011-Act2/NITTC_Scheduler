package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.InactiveLessonModeDrafts
import jp.linkserver.nittcsc.data.LessonMode
import jp.linkserver.nittcsc.logic.TimetableTerm

internal enum class SktjfProjectionBlock {
    INVALID_PARTITION, MISSING_LOCAL_LESSON, IDENTITY_MOVED, NEEDS_ADDITIONAL_STORAGE, INVALID_INACTIVE_DRAFTS
}
internal sealed interface SktjfTimetableProjection {
    /** Metadata must be committed atomically with these rows; this result does not itself write to Room. */
    data class Ready(val rows: List<LessonEntity>, val metadata: Map<Pair<Int, Int>, List<SktjfMergedLesson>>) : SktjfTimetableProjection
    data class Blocked(val reason: SktjfProjectionBlock, val dayOfWeek: Int?, val period: Int?) : SktjfTimetableProjection
}

internal fun sktjfLegacyKey(id: Long, cycle: SktjfCycle): String = "base:$id:${cycle.name}"

/** Convert only losslessly representable rows; conditional or overlapping records require extended storage. */
internal fun projectSktjfTimetable(
    year: Int, term: TimetableTerm, classId: String, existing: List<LessonEntity>, resolved: SktjfResolvedLessonMerge
): SktjfTimetableProjection {
    fun blocked(reason: SktjfProjectionBlock, row: LessonEntity? = null) =
        SktjfTimetableProjection.Blocked(reason, row?.dayOfWeek, row?.slotIndex?.plus(1))
    if (resolved.lessons.any { it.content.weekRange != null }) return blocked(SktjfProjectionBlock.NEEDS_ADDITIONAL_STORAGE)
    val partition = resolved.baseline
    val termCode = if (term == TimetableTerm.FIRST) "S" else "F"
    if (partition.academicYear != year || partition.term != termCode || partition.classId != classId || existing.any { it.academicYear != year || it.timetableTerm != term || it.id <= 0 || it.dayOfWeek !in 1..7 || it.slotIndex !in 0..11 } ||
        existing.map { it.id }.distinct().size != existing.size || existing.map { it.dayOfWeek to it.slotIndex }.distinct().size != existing.size)
        return blocked(SktjfProjectionBlock.INVALID_PARTITION)
    fun hasData(subject: String, teacher: String, room: String?) = subject.isNotBlank() || teacher.isNotBlank() || !room.isNullOrBlank()
    val expected = mutableMapOf<String, Pair<Int, Int>>()
    for (row in existing) {
        fun remember(cycle: SktjfCycle, subject: String, teacher: String, room: String?) {
            if (hasData(subject, teacher, room)) expected[sktjfLegacyKey(row.id, cycle)] = row.dayOfWeek to row.slotIndex + 1
        }
        if (row.mode == LessonMode.WEEKLY) remember(SktjfCycle.WEEKLY, row.weeklySubject, row.weeklyTeacher, row.weeklyLocation)
        else {
            remember(SktjfCycle.A, row.aSubject, row.aTeacher, row.aLocation)
            remember(SktjfCycle.B, row.bSubject, row.bTeacher, row.bLocation)
        }
    }
    val keys = resolved.lessons.mapNotNull { it.localKey }
    val missingKeys = expected.keys - keys.toSet()
    if (keys.distinct().size != keys.size || missingKeys != (resolved.replacedLocalKeys + resolved.clearedLocalKeys) ||
        resolved.replacedLocalKeys.intersect(resolved.clearedLocalKeys).isNotEmpty() || resolved.replacedLocalKeys.any { missing ->
            val parts = missing.split(':'); parts.size != 3 || parts[0] != "base" ||
                keys.none { key -> key.startsWith("base:${parts[1]}:") && expected[key] == expected[missing] }
        }) return blocked(SktjfProjectionBlock.MISSING_LOCAL_LESSON)
    for (lesson in resolved.lessons) {
        val key = lesson.localKey ?: continue
        val oldSlot = expected[key] ?: return blocked(SktjfProjectionBlock.NEEDS_ADDITIONAL_STORAGE)
        if (oldSlot != (lesson.content.dayOfWeek to lesson.content.period)) return blocked(SktjfProjectionBlock.IDENTITY_MOVED)
    }
    val bySlot = resolved.lessons.groupBy { it.content.dayOfWeek to it.content.period }
    val rows = existing.associateBy { it.dayOfWeek to it.slotIndex + 1 }.toMutableMap()
    for (key in resolved.clearedLocalKeys) {
        val slot = expected[key] ?: return blocked(SktjfProjectionBlock.MISSING_LOCAL_LESSON)
        val row = rows.getValue(slot)
        rows[slot] = when (key.substringAfterLast(':')) {
            SktjfCycle.WEEKLY.name -> row.copy(weeklySubject = "", weeklyTeacher = "", weeklyLocation = null)
            SktjfCycle.A.name -> row.copy(aSubject = "", aTeacher = "", aLocation = null)
            SktjfCycle.B.name -> row.copy(bSubject = "", bTeacher = "", bLocation = null)
            else -> return blocked(SktjfProjectionBlock.MISSING_LOCAL_LESSON)
        }
    }
    for ((slot, lessons) in bySlot) {
        val (day, period) = slot
        if (day !in 1..7 || period !in 1..12) return blocked(SktjfProjectionBlock.INVALID_PARTITION)
        if (lessons.any { it.content.conditions.all.isNotEmpty() || it.content.conditions.none.isNotEmpty() ||
                it.content.teachers.size > 1 || it.content.rooms.size > 1 || (it.content.subject.isBlank() && it.localKey == null) })
            return SktjfTimetableProjection.Blocked(SktjfProjectionBlock.NEEDS_ADDITIONAL_STORAGE, day, period)
        val weekly = lessons.filter { it.content.cycle == SktjfCycle.WEEKLY }
        val a = lessons.filter { it.content.cycle == SktjfCycle.A }; val b = lessons.filter { it.content.cycle == SktjfCycle.B }
        if (weekly.size > 1 || a.size > 1 || b.size > 1 || (weekly.isNotEmpty() && (a.isNotEmpty() || b.isNotEmpty())))
            return SktjfTimetableProjection.Blocked(SktjfProjectionBlock.NEEDS_ADDITIONAL_STORAGE, day, period)
        val old = rows[slot] ?: LessonEntity(academicYear = year, timetableTerm = term, dayOfWeek = day, slotIndex = period - 1,
            mode = LessonMode.WEEKLY, weeklySubject = "", weeklyTeacher = "", aSubject = "", aTeacher = "", bSubject = "", bTeacher = "")
        val nextMode = if (weekly.isNotEmpty()) LessonMode.WEEKLY else LessonMode.ALTERNATING
        fun subject(items: List<SktjfMergedLesson>) = items.singleOrNull()?.content?.subject.orEmpty()
        fun teacher(items: List<SktjfMergedLesson>) = items.singleOrNull()?.content?.teachers?.singleOrNull().orEmpty()
        fun room(items: List<SktjfMergedLesson>) = items.singleOrNull()?.content?.rooms?.singleOrNull()
        val applied = if (nextMode == LessonMode.WEEKLY) old.copy(mode = nextMode, weeklySubject = subject(weekly), weeklyTeacher = teacher(weekly), weeklyLocation = room(weekly))
        else old.copy(mode = nextMode, aSubject = subject(a), aTeacher = teacher(a), aLocation = room(a), bSubject = subject(b), bTeacher = teacher(b), bLocation = room(b))
        rows[slot] = runCatching { InactiveLessonModeDrafts.preserve(old, applied) }.getOrElse {
            return blocked(SktjfProjectionBlock.INVALID_INACTIVE_DRAFTS, old)
        }
    }
    return SktjfTimetableProjection.Ready(rows.values.sortedWith(compareBy({ it.dayOfWeek }, { it.slotIndex })), bySlot)
}
