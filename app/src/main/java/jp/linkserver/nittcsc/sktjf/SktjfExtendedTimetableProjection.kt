package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.SktjfExtraLessonBackup
import jp.linkserver.nittcsc.data.SktjfExtraLessonEntity
import jp.linkserver.nittcsc.logic.TimetableTerm
import java.util.UUID

internal sealed interface SktjfExtendedProjection {
    data class Ready(val legacy: SktjfTimetableProjection.Ready, val extras: List<SktjfExtraLessonEntity>) : SktjfExtendedProjection
    data object Blocked : SktjfExtendedProjection
}

/** Keeps existing row identities and stores additional or conditional lessons separately. */
internal fun projectSktjfExtendedTimetable(
    year: Int, term: TimetableTerm, classId: String, rows: List<LessonEntity>,
    existingExtras: List<SktjfExtraLessonEntity>, resolved: SktjfResolvedLessonMerge,
    newId: () -> String = { UUID.randomUUID().toString() }
): SktjfExtendedProjection {
    // Until time-dependent storage and display are connected, never discard a week restriction.
    if (resolved.lessons.any { it.content.weekRange != null }) return SktjfExtendedProjection.Blocked
    val termCode = if (term == TimetableTerm.FIRST) "S" else "F"
    if (existingExtras.any { it.academicYear != year || it.term != termCode || it.classId != classId } ||
        runCatching { SktjfExtraLessonBackup.encode(existingExtras) }.isFailure) return SktjfExtendedProjection.Blocked
    val old = existingExtras.associateBy { it.id }
    val extraKeys = resolved.lessons.mapNotNull { it.localKey?.takeIf { key -> key.startsWith("extra:") }?.removePrefix("extra:") }
    if (extraKeys.distinct().size != extraKeys.size || !extraKeys.containsAll(old.keys) || extraKeys.any { it !in old }) return SktjfExtendedProjection.Blocked
    if (resolved.lessons.any { it.localKey != null && !it.localKey.startsWith("base:") && !it.localKey.startsWith("extra:") }) return SktjfExtendedProjection.Blocked
    val legacy = mutableListOf<SktjfMergedLesson>(); val extras = mutableListOf<SktjfExtraLessonEntity>()
    val grouped = resolved.lessons.groupBy { it.content.dayOfWeek to it.content.period }
    for (lesson in resolved.lessons) {
        val content = lesson.content; val key = lesson.localKey
        val peers = grouped.getValue(content.dayOfWeek to content.period)
        val canUseLegacy = content.conditions.all.isEmpty() && content.conditions.none.isEmpty() &&
            content.teachers.size <= 1 && content.rooms.size <= 1
        val compatiblePeers = peers.all { it.content.conditions.all.isEmpty() && it.content.conditions.none.isEmpty() &&
            it.content.teachers.size <= 1 && it.content.rooms.size <= 1 && it.localKey?.startsWith("extra:") != true } &&
            peers.map { it.content.cycle }.distinct().size == peers.size &&
            (peers.size == 1 || peers.none { it.content.cycle == SktjfCycle.WEEKLY })
        val useLegacy = key?.startsWith("base:") == true || (key == null && canUseLegacy && compatiblePeers)
        if (useLegacy) { legacy += lesson; continue }
        val id = if (key == null) newId() else key.removePrefix("extra:")
        val encoded = runCatching { SktjfBaselineCodec.encode(SktjfStoredBaseline(
            SktjfBaseline(year, termCode, classId, mapOf(id to content)), SktjfCalendarBaseline(year, termCode, classId, emptyMap()))) }.getOrNull()
            ?: return SktjfExtendedProjection.Blocked
        extras += SktjfExtraLessonEntity(id, year, termCode, classId, lesson.sourceLessonId, encoded)
    }
    if (runCatching { SktjfExtraLessonBackup.encode(extras) }.isFailure) return SktjfExtendedProjection.Blocked
    val projected = projectSktjfTimetable(year, term, classId, rows, resolved.copy(lessons = legacy)) as? SktjfTimetableProjection.Ready
        ?: return SktjfExtendedProjection.Blocked
    return SktjfExtendedProjection.Ready(projected, extras)
}
