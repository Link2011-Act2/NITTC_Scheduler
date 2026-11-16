package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.logic.SktjfMergeDecision
import jp.linkserver.nittcsc.logic.mergeSktjfValue
import java.time.LocalDate

internal data class SktjfCalendarContent(
    val dayType: SktjfDayType?, val overrideDayOfWeek: Int?, val overrideDayType: SktjfDayType?, val event: SktjfEvent?
)
internal fun SktjfCalendarDay.content() = SktjfCalendarContent(dayType, overrideDayOfWeek, overrideDayType, event)
internal data class SktjfCalendarBaseline(
    val academicYear: Int, val term: String, val classId: String, val days: Map<LocalDate, SktjfCalendarContent>
)
internal enum class SktjfCalendarConflictReason { BOTH_CHANGED, NEEDS_EXTENDED_HANDLING }
internal data class SktjfCalendarConflict(
    val date: LocalDate, val previous: SktjfCalendarContent?, val current: SktjfCalendarContent?,
    val incoming: SktjfCalendarContent, val reason: SktjfCalendarConflictReason,
    val projection: SktjfCalendarProjection
)
internal data class SktjfLocalCalendar(
    val academicYear: Int, val term: String, val classId: String, val days: Map<LocalDate, SktjfCalendarContent>
)
internal data class SktjfResolvedCalendarMerge(val days: Map<LocalDate, SktjfCalendarContent>, val baseline: SktjfCalendarBaseline)

internal class SktjfCalendarMergePlan private constructor(
    private val local: Map<LocalDate, SktjfCalendarContent>,
    private val updates: Map<LocalDate, SktjfCalendarContent>,
    val conflicts: List<SktjfCalendarConflict>,
    private val nextBaseline: SktjfCalendarBaseline
) {
    fun resolve(choices: Map<LocalDate, SktjfConflictChoice>): SktjfResolvedCalendarMerge {
        require(choices.keys == conflicts.map { it.date }.toSet())
        val result = (local + updates).toMutableMap()
        for (conflict in conflicts) if (choices.getValue(conflict.date) == SktjfConflictChoice.APPLY_INCOMING) {
            require(conflict.projection is SktjfCalendarProjection.Supported)
            result[conflict.date] = conflict.incoming
        }
        return SktjfResolvedCalendarMerge(result.toMap(), nextBaseline)
    }

    companion object {
        fun create(previous: SktjfCalendarBaseline?, local: SktjfLocalCalendar, incoming: SktjfDocument): SktjfCalendarMergePlan {
            fun same(year: Int, term: String, classId: String) = year == incoming.academicYear && term == incoming.term && classId == incoming.classId
            require(previous == null || same(previous.academicYear, previous.term, previous.classId))
            require(same(local.academicYear, local.term, local.classId))
            require(incoming.calendar.map { it.date }.distinct().size == incoming.calendar.size)
            val old = previous?.days.orEmpty().toMap(); val current = local.days.toMap()
            val updates = mutableMapOf<LocalDate, SktjfCalendarContent>()
            val conflicts = mutableListOf<SktjfCalendarConflict>()
            val incomingDays = incoming.calendar.associate { source ->
                val next = source.content(); val base = old[source.date]; val value = current[source.date]
                val projection = projectSktjfCalendarDay(source)
                // An unrepresentable source day remains unresolved even when the same revision
                // is checked again after the user chose to retain the local value.
                if (projection is SktjfCalendarProjection.NeedsExtendedHandling && value != next) {
                    conflicts += SktjfCalendarConflict(source.date, base, value, next, SktjfCalendarConflictReason.NEEDS_EXTENDED_HANDLING, projection)
                } else when (mergeSktjfValue(base, value, next)) {
                    is SktjfMergeDecision.Keep -> Unit
                    is SktjfMergeDecision.Apply -> if (projection is SktjfCalendarProjection.Supported) updates[source.date] = next
                        else conflicts += SktjfCalendarConflict(source.date, base, value, next, SktjfCalendarConflictReason.NEEDS_EXTENDED_HANDLING, projection)
                    is SktjfMergeDecision.Conflict -> conflicts += SktjfCalendarConflict(source.date, base, value, next, SktjfCalendarConflictReason.BOTH_CHANGED, projection)
                }
                source.date to next
            }
            return SktjfCalendarMergePlan(current, updates, conflicts,
                SktjfCalendarBaseline(incoming.academicYear, incoming.term, incoming.classId, old + incomingDays))
        }
    }
}
