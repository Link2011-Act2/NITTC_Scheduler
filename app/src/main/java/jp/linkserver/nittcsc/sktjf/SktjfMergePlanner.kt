package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.logic.normalizeSubjectRomanNumerals

/** Content used in comparisons deliberately excludes revision and PDF reference metadata. */
internal data class SktjfLessonContent(
    val dayOfWeek: Int, val period: Int, val cycle: SktjfCycle, val subject: String,
    val teachers: List<String>, val rooms: List<String>, val conditions: SktjfConditions,
    val kind: SktjfLessonKind, val remote: Boolean?, val weekRange: SktjfV2Range? = null
)

internal fun SktjfLesson.content() = SktjfLessonContent(
    dayOfWeek, period, cycle, subject, teachers.toList(), rooms.toList(),
    SktjfConditions(conditions.all.toSet(), conditions.none.toSet()), kind, remote, weekRange
)
private fun SktjfLessonContent.detached() = copy(teachers = teachers.toList(), rooms = rooms.toList(),
    conditions = SktjfConditions(conditions.all.toSet(), conditions.none.toSet()))
private fun equivalentLessonContent(left: SktjfLessonContent?, right: SktjfLessonContent?): Boolean = when {
    left == null || right == null -> left == right
    else -> left.copy(subject = normalizeSubjectRomanNumerals(left.subject)) ==
        right.copy(subject = normalizeSubjectRomanNumerals(right.subject))
}

/** localKey is an opaque existing storage identity; null is reserved for not-yet-inserted rows. */
internal data class SktjfLocalLesson(
    val localKey: String, val sourceLessonId: String?, val content: SktjfLessonContent
)
internal data class SktjfLocalTimetable(
    val academicYear: Int, val term: String, val classId: String, val lessons: List<SktjfLocalLesson>
)
internal data class SktjfMergedLesson(
    val localKey: String?, val sourceLessonId: String?, val content: SktjfLessonContent
)
internal data class SktjfBaseline(
    val academicYear: Int, val term: String, val classId: String,
    val lessons: Map<String, SktjfLessonContent>
)
internal enum class SktjfConflictReason { BOTH_CHANGED, LOCAL_CHANGED, BASELINE_MISSING, OVERLAPPING_LOCAL }
internal data class SktjfLessonConflict(
    val sourceLessonId: String, val reason: SktjfConflictReason, val previous: SktjfLessonContent?,
    val current: SktjfLocalLesson?, val incoming: SktjfLessonContent,
    val overlappingLocal: List<SktjfLocalLesson> = emptyList(), val isDeletion: Boolean = false
)
internal enum class SktjfConflictChoice { KEEP_LOCAL, APPLY_INCOMING }
internal enum class SktjfLessonReviewStatus { UNCHANGED, AUTOMATIC, PROTECTED, CONFLICT }
internal data class SktjfLessonReview(
    val sourceLessonId: String?, val day: Int, val period: Int, val cycle: SktjfCycle,
    val current: SktjfLessonContent?, val incoming: SktjfLessonContent?, val status: SktjfLessonReviewStatus
)
internal data class SktjfResolvedLessonMerge(val lessons: List<SktjfMergedLesson>, val baseline: SktjfBaseline,
    val replacedLocalKeys: Set<String> = emptySet(), val clearedLocalKeys: Set<String> = emptySet())
private data class SktjfChange(val existingKey: String?, val sourceId: String, val content: SktjfLessonContent, val delete: Boolean = false)

internal class SktjfLessonMergePlan private constructor(
    private val current: List<SktjfLocalLesson>,
    private val changes: List<SktjfChange>,
    val conflicts: List<SktjfLessonConflict>,
    val reviewItems: List<SktjfLessonReview>,
    private val nextBaseline: SktjfBaseline,
    private val removedBaseline: Map<String, SktjfLessonContent>
) {
    /** Every conflict needs an explicit choice; replacement retains the existing row identity. */
    fun resolve(choices: Map<String, SktjfConflictChoice>): SktjfResolvedLessonMerge {
        val conflictIds = conflicts.map { it.sourceLessonId }.toSet()
        val changeIds = changes.map { it.sourceId }.toSet()
        require(choices.keys.containsAll(conflictIds) && choices.keys.all { it in conflictIds || it in changeIds })
        val rows = current.map { SktjfMergedLesson(it.localKey, it.sourceLessonId, it.content) }.toMutableList()
        val replacedKeys = mutableSetOf<String>()
        val clearedKeys = mutableSetOf<String>()
        fun apply(change: SktjfChange) {
            if (change.delete) {
                val key = requireNotNull(change.existingKey)
                require(rows.removeAll { it.localKey == key })
                clearedKeys += key
                return
            }
            val result = SktjfMergedLesson(change.existingKey, change.sourceId, change.content)
            if (change.existingKey == null) rows += result else {
                val index = rows.indexOfFirst { it.localKey == change.existingKey }
                require(index >= 0); rows[index] = result
            }
        }
        changes.forEach { change ->
            if (choices[change.sourceId] != SktjfConflictChoice.KEEP_LOCAL) apply(change)
        }
        conflicts.filter { !it.isDeletion && it.current?.sourceLessonId != null &&
            it.current.sourceLessonId != it.sourceLessonId && choices.getValue(it.sourceLessonId) == SktjfConflictChoice.KEEP_LOCAL
        }.forEach { conflict ->
            val index = rows.indexOfFirst { it.localKey == conflict.current?.localKey }
            require(index >= 0)
            rows[index] = rows[index].copy(sourceLessonId = conflict.sourceLessonId)
        }
        val handled = mutableSetOf<String>()
        val weeklyGroups = conflicts.filter { it.reason == SktjfConflictReason.OVERLAPPING_LOCAL &&
            it.current == null && it.overlappingLocal.size == 1 &&
            it.overlappingLocal.single().content.cycle == SktjfCycle.WEEKLY && it.incoming.cycle != SktjfCycle.WEEKLY }
            .groupBy { it.overlappingLocal.single().localKey }
        for ((key, group) in weeklyGroups) {
            if (group.none { choices.getValue(it.sourceLessonId) == SktjfConflictChoice.APPLY_INCOMING }) continue
            require(group.map { it.incoming.cycle }.distinct().size == group.size)
            val original = group.first().overlappingLocal.single()
            require(original.sourceLessonId == null)
            val index = rows.indexOfFirst { it.localKey == key }; require(index >= 0)
            rows.removeAt(index)
            val byCycle = group.associateBy { it.incoming.cycle }
            for ((indexInRow, cycle) in listOf(SktjfCycle.A, SktjfCycle.B).withIndex()) {
                val conflict = byCycle[cycle]
                val incoming = conflict != null && choices.getValue(conflict.sourceLessonId) == SktjfConflictChoice.APPLY_INCOMING
                rows += if (incoming) SktjfMergedLesson(if (indexInRow == 0) key else null, conflict.sourceLessonId, conflict.incoming)
                    else SktjfMergedLesson(if (indexInRow == 0) key else null, original.sourceLessonId, original.content.copy(cycle = cycle))
            }
            handled += group.map { it.sourceLessonId }
        }
        conflicts.filter { it.sourceLessonId !in handled && choices.getValue(it.sourceLessonId) == SktjfConflictChoice.APPLY_INCOMING }.forEach {
            if (it.isDeletion) {
                if (it.current != null) apply(SktjfChange(it.current.localKey, it.sourceLessonId, it.incoming, delete = true))
                return@forEach
            }
            val key = if (it.reason == SktjfConflictReason.OVERLAPPING_LOCAL) {
                require(it.current == null && it.overlappingLocal.isNotEmpty())
                if (it.incoming.cycle == SktjfCycle.WEEKLY && it.overlappingLocal.size > 1) {
                    val keys = it.overlappingLocal.map { local -> local.localKey }
                    val ids = keys.map { key -> key.split(':').let { parts ->
                        require(parts.size == 3 && parts[0] == "base"); parts[1]
                    } }.toSet()
                    require(ids.size == 1 && it.overlappingLocal.map { local -> local.content.cycle }.toSet() == setOf(SktjfCycle.A, SktjfCycle.B))
                    val keep = keys.first()
                    keys.drop(1).forEach { removed ->
                        require(rows.removeAll { row -> row.localKey == removed })
                        replacedKeys += removed
                    }
                    keep
                } else {
                    val existing = it.overlappingLocal.single()
                    require(existing.content.cycle == it.incoming.cycle || it.incoming.cycle == SktjfCycle.WEEKLY)
                    existing.localKey
                }
            } else it.current?.localKey
            apply(SktjfChange(key, it.sourceLessonId, it.incoming))
        }
        val retained = removedBaseline.filterKeys { choices[it] == SktjfConflictChoice.KEEP_LOCAL }
        return SktjfResolvedLessonMerge(rows.toList(), nextBaseline.copy(lessons = nextBaseline.lessons + retained), replacedKeys, clearedKeys)
    }

    companion object {
        fun create(previous: SktjfBaseline?, current: SktjfLocalTimetable, incoming: SktjfDocument): SktjfLessonMergePlan {
            require(previous == null || (previous.academicYear == incoming.academicYear && previous.term == incoming.term && previous.classId == incoming.classId))
            require(current.academicYear == incoming.academicYear && current.term == incoming.term && current.classId == incoming.classId)
            val local = current.lessons.map { it.copy(content = it.content.detached()) }
            fun valid(content: SktjfLessonContent, allowPartial: Boolean = false) = content.dayOfWeek in 1..7 && content.period in 1..12 &&
                (content.subject.isNotBlank() || (allowPartial && (content.teachers.isNotEmpty() || content.rooms.isNotEmpty()))) &&
                content.conditions.all.none { it in content.conditions.none }
            require(local.all { valid(it.content, allowPartial = true) } && previous?.lessons.orEmpty().values.all { valid(it) })
            require(local.all { it.localKey.isNotBlank() } && local.map { it.localKey }.distinct().size == local.size)
            val sourced = local.filter { it.sourceLessonId != null }
            require(sourced.map { it.sourceLessonId }.distinct().size == sourced.size)
            require(incoming.lessons.map { it.id }.distinct().size == incoming.lessons.size)
            val slots = incoming.slotCoverage.associateBy { it.dayOfWeek to it.period }
            require(slots.size == incoming.slotCoverage.size)
            val old = previous?.lessons.orEmpty().mapValues { it.value.detached() }
            val bySource = sourced.associateBy { it.sourceLessonId }
            val changes = mutableListOf<SktjfChange>()
            val conflicts = mutableListOf<SktjfLessonConflict>()
            val reservedVariants = mutableSetOf<String>()
            val incomingById = incoming.lessons.associate { lesson ->
                require(slots[lesson.dayOfWeek to lesson.period]?.state == SktjfSlotState.PRESENT)
                lesson.id to lesson.content()
            }
            for ((id, next) in incomingById) {
                val base = old[id]; val existing = bySource[id]
                // A profile change can select a different source lesson for the same slot.
                // Treat that as an update of the existing row, not as another lesson.
                if (existing == null) {
                    val previousVariants = local.filter { candidate ->
                        val sourceId = candidate.sourceLessonId
                        candidate.localKey !in reservedVariants && sourceId != null && sourceId in old && sourceId !in incomingById &&
                            candidate.content.dayOfWeek == next.dayOfWeek && candidate.content.period == next.period &&
                            candidate.content.cycle == next.cycle &&
                            (next.weekRange == null || normalizeSubjectRomanNumerals(old.getValue(sourceId).subject) == normalizeSubjectRomanNumerals(next.subject))
                    }
                    require(previousVariants.size <= 1)
                    val variant = previousVariants.singleOrNull()
                    if (variant != null) {
                        reservedVariants += variant.localKey
                        val previous = old.getValue(variant.sourceLessonId!!)
                        if (equivalentLessonContent(variant.content, previous)) changes += SktjfChange(variant.localKey, id, next)
                        else conflicts += SktjfLessonConflict(id, SktjfConflictReason.BOTH_CHANGED, previous, variant, next)
                        continue
                    }
                }
                if (base == null && existing != null) {
                    conflicts += SktjfLessonConflict(id, SktjfConflictReason.BASELINE_MISSING, null, existing, next)
                    continue
                }
                if (base != null && equivalentLessonContent(existing?.content, next)) continue
                // Editing a weekly slot into A/B (or the reverse) changes its local keys,
                // so the old source link no longer points to the active local records.
                // Treat the active records as an explicit overlap, not as a deletion:
                // accepting the incoming version must replace that same timetable row.
                if (base != null && existing == null) {
                    val overlappingLocal = local.filter { other ->
                        (other.sourceLessonId == null || (other.sourceLessonId !in old && other.sourceLessonId !in incomingById)) &&
                            overlaps(other.content, next)
                    }
                    if (overlappingLocal.isNotEmpty()) {
                        conflicts += SktjfLessonConflict(id, SktjfConflictReason.OVERLAPPING_LOCAL, base, null, next, overlappingLocal)
                        continue
                    }
                }
                if (base != null && equivalentLessonContent(next, base)) {
                    if (!equivalentLessonContent(existing?.content, base))
                        conflicts += SktjfLessonConflict(id, SktjfConflictReason.LOCAL_CHANGED, base, existing, next)
                    continue
                }
                if (base != null && !equivalentLessonContent(existing?.content, base)) {
                    conflicts += SktjfLessonConflict(id, SktjfConflictReason.BOTH_CHANGED, base, existing, next)
                    continue
                }
                val otherLocal = local.filter { other ->
                    other.localKey != existing?.localKey &&
                        (other.sourceLessonId == null || (other.sourceLessonId !in old && other.sourceLessonId !in incomingById)) &&
                        overlaps(other.content, next)
                }
                if (otherLocal.isNotEmpty()) conflicts += SktjfLessonConflict(id, SktjfConflictReason.OVERLAPPING_LOCAL, base, existing, next, otherLocal)
                else changes += SktjfChange(existing?.localKey, id, next)
            }
            val claimed = changes.mapNotNull { it.existingKey }.toSet() + conflicts.mapNotNull { it.current?.localKey }
            val removed = mutableSetOf<String>()
            for ((id, base) in old) {
                if (id in incomingById) continue
                val slot = slots[base.dayOfWeek to base.period] ?: continue
                if (slot.state == SktjfSlotState.EXCLUDED || slot.containsExcluded) continue
                val existing = bySource[id]
                if (existing != null && existing.localKey in claimed) { removed += id; continue }
                removed += id
                if (existing == null) continue
                if (equivalentLessonContent(existing.content, base)) changes += SktjfChange(existing.localKey, id, base, delete = true)
                else conflicts += SktjfLessonConflict(id, SktjfConflictReason.BOTH_CHANGED, base, existing, base, isDeletion = true)
            }
            val nextBaseline = SktjfBaseline(incoming.academicYear, incoming.term, incoming.classId, (old - removed) + incomingById)
            val changesById = changes.associateBy { it.sourceId }
            val conflictsById = conflicts.associateBy { it.sourceLessonId }
            val reviews = mutableListOf<SktjfLessonReview>()
            for ((id, next) in incomingById) {
                val conflict = conflictsById[id]; val change = changesById[id]
                val currentValue = conflict?.current?.content ?: conflict?.overlappingLocal?.firstOrNull()?.content ?:
                    change?.existingKey?.let { key -> local.firstOrNull { it.localKey == key }?.content } ?: bySource[id]?.content
                val status = when {
                    conflict?.reason == SktjfConflictReason.LOCAL_CHANGED -> SktjfLessonReviewStatus.PROTECTED
                    conflict != null -> SktjfLessonReviewStatus.CONFLICT
                    change != null -> SktjfLessonReviewStatus.AUTOMATIC
                    !equivalentLessonContent(currentValue, next) -> SktjfLessonReviewStatus.PROTECTED
                    else -> SktjfLessonReviewStatus.UNCHANGED
                }
                reviews += SktjfLessonReview(id, next.dayOfWeek, next.period, next.cycle, currentValue, next, status)
            }
            for (id in removed) {
                val base = old.getValue(id); val conflict = conflictsById[id]; val change = changesById[id]
                if (conflict != null || change?.delete == true) reviews += SktjfLessonReview(id, base.dayOfWeek, base.period,
                    base.cycle, bySource[id]?.content, null, if (conflict != null) SktjfLessonReviewStatus.CONFLICT else SktjfLessonReviewStatus.AUTOMATIC)
            }
            val coveredKeys = reviews.mapNotNull { review ->
                review.sourceLessonId?.let { id -> conflictsById[id]?.current?.localKey ?: changesById[id]?.existingKey ?:
                    bySource[id]?.localKey }
            }.toSet() + conflicts.flatMap { it.overlappingLocal }.map { it.localKey }
            local.filter { it.localKey !in coveredKeys }.forEach { item ->
                reviews += SktjfLessonReview(null, item.content.dayOfWeek, item.content.period, item.content.cycle,
                    item.content, null, if (item.sourceLessonId == null) SktjfLessonReviewStatus.PROTECTED else SktjfLessonReviewStatus.UNCHANGED)
            }
            return SktjfLessonMergePlan(local.toList(), changes.toList(), conflicts.toList(), reviews.toList(), nextBaseline,
                removed.associateWith { old.getValue(it) })
        }

        private fun overlaps(a: SktjfLessonContent, b: SktjfLessonContent): Boolean {
            if (a.dayOfWeek != b.dayOfWeek || a.period != b.period) return false
            if (a.cycle != SktjfCycle.WEEKLY && b.cycle != SktjfCycle.WEEKLY && a.cycle != b.cycle) return false
            return a.conditions.all.none { it in b.conditions.none } && b.conditions.all.none { it in a.conditions.none }
        }
    }
}
