package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.SktjfSourceStateBackup
import jp.linkserver.nittcsc.data.SktjfSourceStateEntity

internal sealed interface SktjfCalendarPreparation {
    data class Ready(val plan: SktjfCalendarMergePlan) : SktjfCalendarPreparation
    data class Blocked(val issue: SktjfLocalProjectionIssue) : SktjfCalendarPreparation
}

internal fun prepareSktjfCalendarMerge(
    document: SktjfDocument, rows: List<DayTypeEntity>, state: SktjfSourceStateEntity?
): SktjfCalendarPreparation {
    val baseline = if (state == null) null else runCatching {
        sktjfCalendarBaseline(document, state)
    }.getOrElse { return SktjfCalendarPreparation.Blocked(SktjfLocalProjectionIssue.INVALID_SOURCE_LINKS) }
    if (state?.requiresRelinking == true) return SktjfCalendarPreparation.Blocked(SktjfLocalProjectionIssue.RELINK_REQUIRED)
    return runCatching {
        val local = projectSktjfLocalCalendar(document.academicYear, document.term, document.classId, rows, baseline)
        SktjfCalendarPreparation.Ready(SktjfCalendarMergePlan.create(baseline, local, document))
    }.getOrElse { SktjfCalendarPreparation.Blocked(SktjfLocalProjectionIssue.INVALID_PARTITION) }
}

internal fun sktjfCalendarBaseline(document: SktjfDocument, state: SktjfSourceStateEntity): SktjfCalendarBaseline {
    SktjfSourceStateBackup.validate(state)
    require(state.academicYear == document.academicYear && state.term == document.term)
    return SktjfBaselineCodec.decode(state.baselineJson).calendar.copy(classId = document.classId)
}
