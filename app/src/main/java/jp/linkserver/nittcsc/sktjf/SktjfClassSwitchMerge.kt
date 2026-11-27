package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.SktjfExtraLessonEntity
import jp.linkserver.nittcsc.data.SktjfSourceStateEntity
import jp.linkserver.nittcsc.logic.TimetableTerm

/** A profile change can be pending while the shared semester rows still belong to the applied class. */
internal fun installedSktjfSources(states: List<SktjfSourceStateEntity>): Map<Pair<Int, String>, SktjfSourceStateEntity> =
    states.filterNot { it.requiresRelinking }.groupBy { it.academicYear to it.term }
        .mapNotNull { (partition, sources) -> sources.singleOrNull()?.let { partition to it } }.toMap()

internal data class SktjfClassSwitchSlot(
    val current: List<SktjfLocalLesson>,
    val manual: List<SktjfLocalLesson>,
    val incoming: List<SktjfLesson>,
    val conflicts: List<SktjfLessonConflict>,
    val review: List<SktjfLessonReview>,
)

/** Compare the shared rows with their installed class before considering the incoming class. */
internal fun prepareSktjfClassSwitch(
    document: SktjfDocument,
    snapshot: SktjfApplySnapshot,
    installed: SktjfSourceStateEntity?,
): SktjfLessonMergePlan? = runCatching {
    val previous = installed?.let { SktjfBaselineCodec.decode(it.baselineJson).lessons }
    val links = installed?.let { SktjfSourceLinks.decode(it.linksJson) }.orEmpty()
    val term = if (document.term == "S") TimetableTerm.FIRST else TimetableTerm.SECOND
    val projection = projectSktjfLocalTimetable(document.academicYear, term, previous?.classId ?: document.classId,
        snapshot.lessons, previous, links, requiresRelinking = false, allowTimed = true) as SktjfLocalProjection.Ready
    val alternatives = snapshot.extraLessons.filter { it.academicYear == document.academicYear && it.term == document.term }
    val localExtras = alternatives.mapNotNull { extra ->
        val stored = SktjfBaselineCodec.decode(extra.contentJson).lessons.lessons.getValue(extra.id)
        val original = previous?.takeIf { it.classId == extra.classId }?.lessons?.get(extra.sourceLessonId)
        val groups = snapshot.selectionGroups.filter { it.classId == extra.classId }
        val retired = groups.any { extra.sourceLessonId in sktjfGroupIds(it.retiredLessonIdsJson) }
        val group = groups.singleOrNull { extra.sourceLessonId in sktjfGroupIds(it.lessonIdsJson) }
        val unselectedWithoutBaseline = original == null && group != null && group.selectedLessonId != extra.sourceLessonId
        if (retired || unselectedWithoutBaseline || (original != null && equivalentLessonContent(stored, original) &&
                (group == null || group.selectedLessonId != extra.sourceLessonId))) return@mapNotNull null
        // Carry an edited or explicitly kept lesson as a local value without its old course rules.
        SktjfLocalLesson("extra:${extra.id}", extra.sourceLessonId.takeIf { original != null }, stored) to original
    }
    val local = projection.timetable.lessons.map { it to previous?.lessons?.get(it.sourceLessonId) } + localExtras
    val currentBySlot = local.groupBy { (value, _) -> value.content.dayOfWeek to value.content.period }
    val incomingBySlot = document.lessons.groupBy { it.dayOfWeek to it.period }
    val slots = (currentBySlot.keys + incomingBySlot.keys).map { (day, period) ->
        val existing = currentBySlot[day to period].orEmpty()
        val current = existing.map { it.first }
        val manual = existing.filterNot { (item, base) -> base != null && equivalentLessonContent(item.content, base) }.map { it.first }
        val incoming = incomingBySlot[day to period].orEmpty()
        val unchanged = manual.isEmpty() && current.size == incoming.size && current.all { item ->
            item.content.weekRange == null && incoming.count { equivalentLessonContent(item.content, it.content()) } == 1
        }
        val conflicts = if (manual.isEmpty()) emptyList() else incoming.map {
            SktjfLessonConflict(it.id, SktjfConflictReason.OVERLAPPING_LOCAL, null, null, it.content(), current)
        }
        val review = incoming.map { lesson ->
            val content = lesson.content()
            val old = current.firstOrNull { it.content.cycle == lesson.cycle } ?: current.firstOrNull()
            SktjfLessonReview(lesson.id, day, period, lesson.cycle, old?.content, content,
                when {
                    conflicts.isNotEmpty() -> SktjfLessonReviewStatus.CONFLICT
                    unchanged -> SktjfLessonReviewStatus.UNCHANGED
                    else -> SktjfLessonReviewStatus.AUTOMATIC
                })
        } + current.mapIndexed { index, item ->
            val removed = incoming.isEmpty() && item !in manual
            val id = if (removed) "class-switch:$day:$period:$index" else null
            SktjfLessonReview(id, day, period, item.content.cycle, item.content, null,
                if (removed) SktjfLessonReviewStatus.AUTOMATIC else if (incoming.isEmpty()) SktjfLessonReviewStatus.PROTECTED
                else SktjfLessonReviewStatus.UNCHANGED)
        }
        SktjfClassSwitchSlot(current, manual, incoming, conflicts, review)
    }
    SktjfLessonMergePlan.forClassSwitch(slots, SktjfBaseline(document.academicYear, document.term, document.classId,
        document.lessons.associate { it.id to it.content() }))
}.getOrNull()

internal fun resolveSktjfClassSwitch(
    slots: List<SktjfClassSwitchSlot>,
    choices: Map<String, SktjfConflictChoice>,
    baseline: SktjfBaseline,
): SktjfResolvedLessonMerge {
    require(choices.keys.all { key -> slots.any { slot -> slot.review.any { it.sourceLessonId == key } } })
    val cleared = mutableSetOf<String>()
    val merged = slots.flatMap { slot ->
        val ids = slot.review.mapNotNull { it.sourceLessonId }
        val keep = ids.any { choices[it] == SktjfConflictChoice.KEEP_LOCAL } ||
            slot.conflicts.any { choices[it.sourceLessonId] == null }
        val retained = when {
            keep -> slot.current
            slot.incoming.isEmpty() -> slot.manual
            else -> emptyList()
        }
        cleared += (slot.current - retained.toSet()).map { it.localKey }
        val local = retained.map { item ->
            SktjfMergedLesson(item.localKey, null, item.content.copy(
                conditions = SktjfConditions(emptySet(), emptySet()), weekRange = null,
                kind = SktjfLessonKind.NORMAL, remote = null))
        }
        local + if (keep) emptyList() else slot.incoming.map { SktjfMergedLesson(null, it.id, it.content()) }
    }
    return SktjfResolvedLessonMerge(merged, baseline, clearedLocalKeys = cleared)
}

internal fun storeSktjfKeptClassSwitchExtra(
    document: SktjfDocument, kept: SktjfMergedLesson,
): SktjfExtraLessonEntity {
    val id = requireNotNull(kept.localKey).removePrefix("extra:")
    val content = SktjfStoredBaseline(
        SktjfBaseline(document.academicYear, document.term, document.classId, mapOf(id to kept.content)),
        SktjfCalendarBaseline(document.academicYear, document.term, document.classId, emptyMap()))
    return SktjfExtraLessonEntity(id, document.academicYear, document.term, document.classId, null, SktjfBaselineCodec.encode(content))
}
