package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.logic.OfficialOpportunitySeries
import java.time.DayOfWeek
import java.time.LocalDate

/** Resolve source timing first, then return the current local record without replacing its contents. */
internal fun resolveLocalSktjfOpportunity(
    baseline: SktjfStoredBaseline,
    localLessons: List<SktjfLocalLesson>,
    seriesId: String,
    period: Int,
    date: LocalDate,
    cycle: SktjfCycle,
    attributes: Set<String>,
): SktjfWeekSelection<SktjfLocalLesson> {
    val group = baseline.series.singleOrNull { it.id == seriesId } ?: return SktjfWeekSelection.UnconfirmedWeek
    val localSlot = localLessons.filter { lesson ->
        lesson.content.dayOfWeek == group.dayOfWeek && lesson.content.period == period &&
            (lesson.content.cycle == SktjfCycle.WEEKLY || lesson.content.cycle == cycle) &&
            lesson.content.conditions.matches(attributes)
    }
    if (localSlot.any { it.sourceLessonId == null || it.sourceLessonId !in group.lessonIds })
        return SktjfWeekSelection.Conflict
    return when (val selected = resolveStoredSktjfOpportunity(baseline, seriesId, period, date, cycle, attributes)) {
        SktjfWeekSelection.Empty -> SktjfWeekSelection.Empty
        SktjfWeekSelection.UnconfirmedWeek -> SktjfWeekSelection.UnconfirmedWeek
        SktjfWeekSelection.Conflict -> SktjfWeekSelection.Conflict
        is SktjfWeekSelection.Selected -> {
            val matches = localSlot.filter { it.sourceLessonId == selected.value }
            when (matches.size) {
                0 -> SktjfWeekSelection.Empty // A user deletion must not restore the source record.
                1 -> SktjfWeekSelection.Selected(matches.single())
                else -> SktjfWeekSelection.Conflict
            }
        }
    }
}

/** Older snapshots without official term boundaries cannot establish a count. */
internal fun resolveStoredSktjfOpportunity(
    baseline: SktjfStoredBaseline,
    seriesId: String,
    period: Int,
    date: LocalDate,
    cycle: SktjfCycle,
    attributes: Set<String>,
): SktjfWeekSelection<String> {
    val bounds = baseline.termBounds ?: return SktjfWeekSelection.UnconfirmedWeek
    return resolveStoredSktjfOpportunity(baseline, seriesId, period, date, cycle, attributes, bounds.start, bounds.end)
}

/** Select source IDs from a saved distribution snapshot; local lesson content stays separate. */
internal fun resolveStoredSktjfOpportunity(
    baseline: SktjfStoredBaseline,
    seriesId: String,
    period: Int,
    date: LocalDate,
    cycle: SktjfCycle,
    attributes: Set<String>,
    termStart: LocalDate,
    termEnd: LocalDate,
): SktjfWeekSelection<String> {
    val group = baseline.series.singleOrNull { it.id == seriesId } ?: return SktjfWeekSelection.UnconfirmedWeek
    if (period !in group.periods) return SktjfWeekSelection.Empty
    val policy = group.countingPolicy ?: return SktjfWeekSelection.UnconfirmedWeek
    if (policy.confirmedNonTeachingDates.any { it !in termStart..termEnd } ||
        policy.officialPeriodOverrides.any { (day, periods) -> day !in termStart..termEnd || periods.keys.any { it + 1 !in group.periods } })
        return SktjfWeekSelection.UnconfirmedWeek
    val options = group.lessonIds.mapNotNull { id ->
        val lesson = baseline.lessons.lessons[id] ?: return SktjfWeekSelection.UnconfirmedWeek
        if (lesson.period != period) null else SktjfWeekCandidate(id, lesson.cycle, lesson.conditions, lesson.weekRange?.range)
    }
    val indices = group.periods.map { it - 1 }.toSet()
    val series = OfficialOpportunitySeries(group.id, DayOfWeek.of(group.dayOfWeek), indices)
    return selectSktjfOfficialOpportunityCandidate(options, date, cycle, attributes, baseline.calendar,
        termStart, termEnd, series, group.cycle, policy.confirmedNonTeachingDates,
        policy.officialPeriodOverrides.filterValues { it.values.any { value -> value != false } }.keys) { day, content ->
        policy.decision(day, content, indices)
    }
}

/** Missing counting rules cannot produce a confirmed lesson. */
internal fun resolveSktjfV2Opportunity(
    candidate: SktjfV2Candidate,
    seriesId: String,
    period: Int,
    date: LocalDate,
    cycle: SktjfCycle,
    attributes: Set<String>,
): SktjfWeekSelection<SktjfLesson> {
    val policy = candidate.series.singleOrNull { it.id == seriesId }?.countingPolicy ?: return SktjfWeekSelection.UnconfirmedWeek
    return resolveSktjfV2Opportunity(candidate, seriesId, period, date, cycle, attributes, policy)
}

/** Resolve only an explicitly grouped slot; no adjacent periods are inferred. */
internal fun resolveSktjfV2Opportunity(
    candidate: SktjfV2Candidate,
    seriesId: String,
    period: Int,
    date: LocalDate,
    cycle: SktjfCycle,
    attributes: Set<String>,
    decide: (LocalDate, SktjfCalendarContent) -> SktjfOpportunityDecision,
): SktjfWeekSelection<SktjfLesson> {
    val group = candidate.series.singleOrNull { it.id == seriesId } ?: return SktjfWeekSelection.UnconfirmedWeek
    if (period !in group.periods) return SktjfWeekSelection.Empty
    val lessons = candidate.documentForMerge().lessons.filter { it.id in group.lessonIds && it.period == period }
    val options = lessons.map { SktjfWeekCandidate(it, it.cycle, it.conditions, it.weekRange?.range) }
    val officialSeries = OfficialOpportunitySeries(group.id, DayOfWeek.of(group.dayOfWeek), group.periods.map { it - 1 }.toSet())
    return selectSktjfOfficialOpportunityCandidate(options, date, cycle, attributes, candidate.sourceBaseline().calendar,
        candidate.document.start, candidate.document.end, officialSeries, group.cycle, decide = decide)
}

/** A source-backed policy is supplied explicitly; personal schedule edits are not inputs. */
internal fun resolveSktjfV2Opportunity(
    candidate: SktjfV2Candidate,
    seriesId: String,
    period: Int,
    date: LocalDate,
    cycle: SktjfCycle,
    attributes: Set<String>,
    policy: SktjfCountingPolicy,
): SktjfWeekSelection<SktjfLesson> {
    val group = candidate.series.singleOrNull { it.id == seriesId } ?: return SktjfWeekSelection.UnconfirmedWeek
    if (period !in group.periods) return SktjfWeekSelection.Empty
    val sourceIds = candidate.document.sources.map { it.id }.toSet()
    if (policy.sources.any { it.sourceId !in sourceIds } ||
        policy.confirmedNonTeachingDates.any { it !in candidate.document.start..candidate.document.end } ||
        policy.officialPeriodOverrides.keys.any { it !in candidate.document.start..candidate.document.end })
        return SktjfWeekSelection.UnconfirmedWeek
    val baseline = candidate.sourceBaseline().let { stored ->
        stored.copy(series = stored.series.map { if (it.id == seriesId) it.copy(countingPolicy = policy) else it })
    }
    return when (val selection = resolveStoredSktjfOpportunity(baseline, seriesId, period, date, cycle, attributes)) {
        SktjfWeekSelection.Empty -> SktjfWeekSelection.Empty
        SktjfWeekSelection.UnconfirmedWeek -> SktjfWeekSelection.UnconfirmedWeek
        SktjfWeekSelection.Conflict -> SktjfWeekSelection.Conflict
        is SktjfWeekSelection.Selected -> candidate.documentForMerge().lessons.singleOrNull { it.id == selection.value }
            ?.let { SktjfWeekSelection.Selected(it) } ?: SktjfWeekSelection.UnconfirmedWeek
    }
}
