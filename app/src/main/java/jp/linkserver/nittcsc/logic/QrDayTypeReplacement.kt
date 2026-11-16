package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.DayTypeEntity
import java.time.LocalDate

/** QR does not carry school events. Preserve them, including days absent from its range. */
internal fun qrDayTypeReplacement(
    existing: List<DayTypeEntity>, incoming: List<DayTypeEntity>, range: ClosedRange<LocalDate>?
): List<DayTypeEntity> {
    val old = existing.filter { range == null || it.date in range }.associateBy { it.date }
    val received = incoming.map { row ->
        val previous = old[row.date]
        row.copy(schoolEventName = previous?.schoolEventName, schoolEventKind = previous?.schoolEventKind)
    }
    val dates = received.mapTo(mutableSetOf()) { it.date }
    return received + old.values.filter { it.schoolEventName != null && it.date !in dates }
}
