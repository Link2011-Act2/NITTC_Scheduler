package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SecondTermSetupTest {
    private fun lesson(year: Int, term: TimetableTerm, subject: String) = LessonEntity(
        academicYear = year, timetableTerm = term, dayOfWeek = 1, slotIndex = 0,
        mode = LessonMode.WEEKLY, weeklySubject = subject, weeklyTeacher = "",
        aSubject = "", aTeacher = "", bSubject = "", bTeacher = ""
    )

    @Test
    fun `only years with entered first term and empty second term need setup`() {
        assertEquals(
            setOf(2026),
            unconfiguredSecondTermYears(listOf(
                lesson(2026, TimetableTerm.FIRST, "数学"),
                lesson(2026, TimetableTerm.SECOND, ""),
                lesson(2027, TimetableTerm.FIRST, "国語"),
                lesson(2027, TimetableTerm.SECOND, "英語"),
                lesson(2028, TimetableTerm.FIRST, "")
            ))
        )
    }
}
