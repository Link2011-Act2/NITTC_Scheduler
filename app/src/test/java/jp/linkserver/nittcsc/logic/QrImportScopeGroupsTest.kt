package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.QrSharePayload
import jp.linkserver.nittcsc.data.QrShareSection
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class QrImportScopeGroupsTest {
    private fun payload(vararg sections: QrShareSection, scope: QrDayTypesScope = QrDayTypesScope.SECOND, version: Int = 3) =
        QrSharePayload(2026, sections.toSet(), 9, 23, dayTypesScope = scope, formatVersion = version)

    @Test fun secondTimetableAndSecondAbShareOneYearAndSemester() {
        val groups = qrImportScopeGroups(payload(QrShareSection.SECOND, QrShareSection.DAY_TYPES))
        assertEquals(1, groups.size)
        assertEquals(2026, groups.single().academicYear)
        assertEquals(QrImportPeriod.SECOND, groups.single().period)
        assertEquals(listOf(QrShareSection.SECOND, QrShareSection.DAY_TYPES), groups.single().sections)
        assertEquals(LocalDate.of(2026, 9, 23)..LocalDate.of(2027, 3, 31), groups.single().dayTypesRange)
    }

    @Test fun firstTimetableAndFirstAbShareOneYearAndSemester() {
        val group = qrImportScopeGroups(payload(QrShareSection.FIRST, QrShareSection.DAY_TYPES, scope = QrDayTypesScope.FIRST)).single()
        assertEquals(QrImportPeriod.FIRST, group.period)
        assertEquals(listOf(QrShareSection.FIRST, QrShareSection.DAY_TYPES), group.sections)
        assertEquals(LocalDate.of(2026, 4, 1)..LocalDate.of(2026, 9, 22), group.dayTypesRange)
    }

    @Test fun yearAbIsNeverClassifiedAsSecondSemester() {
        val groups = qrImportScopeGroups(payload(QrShareSection.SECOND, QrShareSection.DAY_TYPES, scope = QrDayTypesScope.YEAR))
        assertEquals(listOf(QrImportPeriod.SECOND, QrImportPeriod.YEAR), groups.map { it.period })
        assertEquals(listOf(QrShareSection.DAY_TYPES), groups.last().sections)
        assertEquals(LocalDate.of(2026, 4, 1)..LocalDate.of(2027, 3, 31), groups.last().dayTypesRange)
    }

    @Test fun oppositeSemesterAbIsNotMergedWithTimetable() {
        val groups = qrImportScopeGroups(payload(QrShareSection.FIRST, QrShareSection.DAY_TYPES))
        assertEquals(listOf(QrImportPeriod.FIRST, QrImportPeriod.SECOND), groups.map { it.period })
        assertEquals(listOf(QrShareSection.FIRST), groups.first().sections)
        assertEquals(listOf(QrShareSection.DAY_TYPES), groups.last().sections)
    }

    @Test fun bothSemestersKeepSeparateHeadingsAndAbJoinsOnlyItsOwnSemester() {
        val groups = qrImportScopeGroups(payload(QrShareSection.FIRST, QrShareSection.SECOND, QrShareSection.DAY_TYPES))
        assertEquals(listOf(QrImportPeriod.FIRST, QrImportPeriod.SECOND), groups.map { it.period })
        assertNull(groups.first().dayTypesRange)
        assertEquals(listOf(QrShareSection.SECOND, QrShareSection.DAY_TYPES), groups.last().sections)
    }

    @Test fun oldQrKeepsAllYearsReplacementRegardlessOfScopeMetadata() {
        for (version in listOf(1, 2)) for (scope in QrDayTypesScope.entries) {
            val groups = qrImportScopeGroups(payload(QrShareSection.SECOND, QrShareSection.DAY_TYPES, scope = scope, version = version))
            assertEquals(listOf(QrImportPeriod.SECOND, QrImportPeriod.ALL_YEARS), groups.map { it.period })
            assertNull(groups.last().academicYear)
            assertNull(groups.last().dayTypesRange)
            assertEquals(listOf(QrShareSection.DAY_TYPES), groups.last().sections)
        }
    }

    @Test fun cancellationsChangesAndExamsRemainAnnualAlongsideSemesterData() {
        val groups = qrImportScopeGroups(payload(QrShareSection.SECOND, QrShareSection.DAY_TYPES,
            QrShareSection.CANCELLATIONS, QrShareSection.CHANGES, QrShareSection.EXAMS))
        assertEquals(listOf(QrImportPeriod.SECOND, QrImportPeriod.YEAR), groups.map { it.period })
        assertEquals(listOf(QrShareSection.CANCELLATIONS, QrShareSection.CHANGES, QrShareSection.EXAMS), groups.last().sections)
        assertNull(groups.last().dayTypesRange)
    }

    @Test fun annualAbAndAnnualDataMayShareTheirActualScope() {
        val group = qrImportScopeGroups(payload(QrShareSection.DAY_TYPES, QrShareSection.EXAMS, scope = QrDayTypesScope.YEAR)).single()
        assertEquals(QrImportPeriod.YEAR, group.period)
        assertEquals(listOf(QrShareSection.DAY_TYPES, QrShareSection.EXAMS), group.sections)
    }

    @Test fun personalItemsDoNotProduceReplacementGroupsOrUnselectedAb() {
        assertTrue(qrImportScopeGroups(payload(QrShareSection.TASKS, QrShareSection.NOTES, QrShareSection.PLANS)).isEmpty())
        assertEquals(listOf(QrShareSection.SECOND), qrImportScopeGroups(payload(QrShareSection.SECOND)).single().sections)
    }

    @Test fun presentationDoesNotMutatePayloadAndHasStableOrderingAcrossInputOrder() {
        val sections = QrShareSection.entries.toSet()
        val data = payload().copy(sections = sections, secondTermStartMonth = 1, secondTermStartDay = 15)
        val original = data.copy()
        val groups = qrImportScopeGroups(data)
        assertEquals(original, data)
        assertEquals(groups, qrImportScopeGroups(data.copy(sections = sections.reversed().toSet())))
        assertEquals(LocalDate.of(2027, 1, 15), groups.single { QrShareSection.DAY_TYPES in it.sections }.dayTypesRange?.start)
        assertEquals(setOf(QrShareSection.FIRST, QrShareSection.SECOND, QrShareSection.DAY_TYPES,
            QrShareSection.CANCELLATIONS, QrShareSection.CHANGES, QrShareSection.EXAMS), groups.flatMap { it.sections }.toSet())
    }
}
