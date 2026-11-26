package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.SktjfSourceStateBackup
import jp.linkserver.nittcsc.data.SktjfSourceStateEntity
import jp.linkserver.nittcsc.data.SktjfExtraLessonEntity
import jp.linkserver.nittcsc.data.SktjfExtraLessonBackup
import jp.linkserver.nittcsc.logic.TimetableTerm

internal sealed interface SktjfLessonPreparation {
    data class Ready(val plan: SktjfLessonMergePlan) : SktjfLessonPreparation
    data class Blocked(val issue: SktjfLocalProjectionIssue) : SktjfLessonPreparation
}

/** Reconstruct current source links and create a conflict plan; this never writes data. */
internal fun prepareSktjfLessonMerge(
    document: SktjfDocument, rows: List<LessonEntity>, state: SktjfSourceStateEntity?,
    extras: List<SktjfExtraLessonEntity> = emptyList(), allowTimed: Boolean = false,
    independentSourceIds: Set<String> = emptySet()
): SktjfLessonPreparation {
    val term = when (document.term) { "S" -> TimetableTerm.FIRST; "F" -> TimetableTerm.SECOND; else -> return SktjfLessonPreparation.Blocked(SktjfLocalProjectionIssue.INVALID_PARTITION) }
    val decoded = if (state == null) null else runCatching {
        SktjfSourceStateBackup.validate(state)
        require(state.academicYear == document.academicYear && state.term == document.term && state.classId == document.classId)
        SktjfBaselineCodec.decode(state.baselineJson) to SktjfSourceLinks.decode(state.linksJson)
    }.getOrElse { return SktjfLessonPreparation.Blocked(SktjfLocalProjectionIssue.INVALID_SOURCE_LINKS) }
    val baseline = decoded?.first?.lessons
    val projection = projectSktjfLocalTimetable(document.academicYear, term, document.classId, rows,
        baseline, decoded?.second.orEmpty(), state?.requiresRelinking ?: false, allowTimed)
    if (projection is SktjfLocalProjection.Blocked) return SktjfLessonPreparation.Blocked(projection.issue)
    val local = (projection as SktjfLocalProjection.Ready).timetable
    val additional = runCatching {
        SktjfExtraLessonBackup.encode(extras) // Validate duplicates and every stored record before merging.
        extras.map { extra ->
            require(extra.academicYear == document.academicYear && extra.term == document.term && extra.classId == document.classId)
            val content = SktjfBaselineCodec.decode(extra.contentJson).lessons.lessons.getValue(extra.id)
            SktjfLocalLesson("extra:${extra.id}", extra.sourceLessonId, content)
        }
    }.getOrElse { return SktjfLessonPreparation.Blocked(SktjfLocalProjectionIssue.INVALID_SOURCE_LINKS) }
    return runCatching {
        SktjfLessonPreparation.Ready(SktjfLessonMergePlan.create(baseline, local.copy(lessons = local.lessons + additional), document, independentSourceIds))
    }.getOrElse { SktjfLessonPreparation.Blocked(SktjfLocalProjectionIssue.INVALID_SOURCE_LINKS) }
}
