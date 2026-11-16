package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.NittcProfileEntity
import jp.linkserver.nittcsc.data.SchedulerUsageMode
import jp.linkserver.nittcsc.data.SktjfSourceStateBackup
import jp.linkserver.nittcsc.data.SktjfSourceStateEntity

private data class OriginSlot(val year: Int, val term: String, val day: Int, val period: Int, val cycle: SktjfCycle)

/** Only linked, current-class source records can affect notification behavior. */
internal class SktjfNotificationOrigins private constructor(private val origins: Map<OriginSlot, SktjfLessonOrigin>) {
    fun find(year: Int, term: String, day: Int, period: Int, cycle: SktjfCycle): SktjfLessonOrigin? =
        origins[OriginSlot(year, term, day, period, cycle)]

    companion object {
        fun create(mode: SchedulerUsageMode?, profiles: List<NittcProfileEntity>, states: List<SktjfSourceStateEntity>): SktjfNotificationOrigins {
            if (mode != SchedulerUsageMode.NITTC) return SktjfNotificationOrigins(emptyMap())
            val classes = profiles.associate { it.academicYear to it.classId }
            val result = mutableMapOf<OriginSlot, SktjfLessonOrigin>()
            for (state in states) {
                if (state.requiresRelinking || classes[state.academicYear] != state.classId) continue
                // Unusable provenance must never suppress a user's manual notification.
                runCatching {
                    SktjfSourceStateBackup.validate(state)
                    val baseline = SktjfBaselineCodec.decode(state.baselineJson).lessons.lessons
                    SktjfSourceLinks.decode(state.linksJson).map { link ->
                        val source = baseline.getValue(link.sourceLessonId)
                        OriginSlot(state.academicYear, state.term, link.day, link.period, link.cycle) to
                            SktjfLessonOrigin(source.kind, source.subject)
                    }
                }.getOrNull()?.forEach { (slot, origin) -> result[slot] = origin }
            }
            return SktjfNotificationOrigins(result)
        }
    }
}
