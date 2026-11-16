package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.HolidaySpecialLabel
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DayTypeRebuildTest {
    private val date = LocalDate.of(2026, 4, 5)

    @Test fun explicitTransferredDaySurvivesAutomaticHoliday() {
        val existing = DayTypeEntity(date, DayType.B, overrideLessonDayOfWeek = 1, overrideLessonDayType = DayType.A)
        assertEquals(existing, rebuildDayType(date, existing, autoHoliday = true))
    }

    @Test fun ordinaryAutomaticHolidayAndExamLabelRemainUnchanged() {
        assertEquals(DayType.HOLIDAY, rebuildDayType(date, null, autoHoliday = true).dayType)
        assertEquals(DayType.HOLIDAY, rebuildDayType(date, DayTypeEntity(date, DayType.A), autoHoliday = true).dayType)
        val exam = DayTypeEntity(date, DayType.HOLIDAY, holidaySpecialLabel = HolidaySpecialLabel.MIDTERM)
        assertEquals(exam, rebuildDayType(date, exam, autoHoliday = true))
    }

    @Test fun rebuildingKeepsSchoolEventAlongsideRegularLessons() {
        val existing = DayTypeEntity(date, DayType.B, overrideLessonDayOfWeek = 1,
            schoolEventName = "清掃", schoolEventKind = "OTHER")
        assertEquals(existing, rebuildDayType(date, existing, autoHoliday = true))
    }
}
