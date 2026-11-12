package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.QrSharePayload
import jp.linkserver.nittcsc.data.QrShareSection
import java.time.LocalDate

internal enum class QrImportPeriod { FIRST, SECOND, YEAR, ALL_YEARS }

/** 読み取り結果の表示専用。置換範囲や転送データを書き換えない。 */
internal data class QrImportScopeGroup(
    val academicYear: Int?,
    val period: QrImportPeriod,
    val sections: List<QrShareSection>,
    val dayTypesRange: ClosedRange<LocalDate>? = null
)

internal fun qrImportScopeGroups(data: QrSharePayload): List<QrImportScopeGroup> {
    val range = data.qrDayTypesRange()
    val dayTypesPeriod = if (range == null) QrImportPeriod.ALL_YEARS else when (data.dayTypesScope) {
        QrDayTypesScope.FIRST -> QrImportPeriod.FIRST
        QrDayTypesScope.SECOND -> QrImportPeriod.SECOND
        QrDayTypesScope.YEAR -> QrImportPeriod.YEAR
    }
    val grouped = linkedMapOf<QrImportPeriod, MutableList<QrShareSection>>()
    listOf(QrShareSection.FIRST, QrShareSection.SECOND, QrShareSection.DAY_TYPES,
        QrShareSection.CANCELLATIONS, QrShareSection.CHANGES, QrShareSection.EXAMS).forEach { section ->
        if (section in data.sections) {
            val period = when (section) {
                QrShareSection.FIRST -> QrImportPeriod.FIRST
                QrShareSection.SECOND -> QrImportPeriod.SECOND
                QrShareSection.DAY_TYPES -> dayTypesPeriod
                else -> QrImportPeriod.YEAR
            }
            grouped.getOrPut(period) { mutableListOf() }.add(section)
        }
    }
    return QrImportPeriod.entries.mapNotNull { period ->
        grouped[period]?.let { sections ->
            QrImportScopeGroup(
                academicYear = data.academicYear.takeUnless { period == QrImportPeriod.ALL_YEARS },
                period = period,
                sections = sections.toList(),
                dayTypesRange = range.takeIf { QrShareSection.DAY_TYPES in sections }
            )
        }
    }
}
