package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.LongBreakEntity
import jp.linkserver.nittcsc.data.QrSharePayload
import jp.linkserver.nittcsc.data.QrShareSection
import java.time.LocalDate

/** QRだけで使う共有範囲。通常のA/B表や同期の保存形式は変えない。 */
enum class QrDayTypesScope { YEAR, FIRST, SECOND }

fun qrDayTypesRange(year: Int, scope: QrDayTypesScope, month: Int, day: Int): ClosedRange<LocalDate> {
    val boundary = secondTermStartDate(year, month, day)
    return when (scope) {
        QrDayTypesScope.YEAR -> academicYearStart(year)..academicYearEnd(year)
        QrDayTypesScope.FIRST -> academicYearStart(year)..boundary.minusDays(1)
        QrDayTypesScope.SECOND -> boundary..academicYearEnd(year)
    }
}

/** nullは全年度を置き換える旧QR。旧データを新しい範囲として解釈しない。 */
fun QrSharePayload.qrDayTypesRange(): ClosedRange<LocalDate>? =
    if (formatVersion < 3 || QrShareSection.DAY_TYPES !in sections) null
    else qrDayTypesRange(academicYear, dayTypesScope, secondTermStartMonth, secondTermStartDay)

fun LongBreakEntity.overlaps(range: ClosedRange<LocalDate>): Boolean =
    startDate <= range.endInclusive && endDate >= range.start

/** 範囲内を置き換え、既存の範囲外部分は保持。受信した休みは元の開始・終了日で保存する。 */
internal fun qrReplaceLongBreaks(
    existing: List<LongBreakEntity>, incoming: List<LongBreakEntity>, range: ClosedRange<LocalDate>
): List<LongBreakEntity> {
    val retained = existing.flatMap { old ->
        if (!old.overlaps(range)) listOf(old)
        else buildList {
            if (old.startDate < range.start) add(old.copy(id = 0, endDate = range.start.minusDays(1)))
            if (old.endDate > range.endInclusive) add(old.copy(id = 0, startDate = range.endInclusive.plusDays(1)))
        }
    }
    // 同じ名前で連続する部分を結合し、別学期から同じ春休みを受けても重複させない。
    val combined = (retained + incoming.map { it.copy(id = 0) }).groupBy { it.name }.flatMap { (_, breaks) ->
        val result = mutableListOf<LongBreakEntity>()
        for (item in breaks.sortedBy { it.startDate }) {
            val previous = result.lastOrNull()
            if (previous != null && (previous.id == 0L || item.id == 0L) && item.startDate <= previous.endDate.plusDays(1)) {
                result[result.lastIndex] = previous.copy(id = 0, endDate = maxOf(previous.endDate, item.endDate))
            } else result += item
        }
        result
    }
    // 内容が変わらない行は端末内IDを保ち、繰り返し取り込みで作り直さない。
    return combined.map { item -> if (item.id != 0L) item else existing.firstOrNull {
        it.name == item.name && it.startDate == item.startDate && it.endDate == item.endDate
    } ?: item }.sortedWith(compareBy<LongBreakEntity> { it.startDate }.thenBy { it.name })
}
