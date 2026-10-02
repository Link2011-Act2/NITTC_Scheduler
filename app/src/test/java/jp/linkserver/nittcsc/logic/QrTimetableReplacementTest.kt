package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import org.junit.Assert.*
import org.junit.Test

class QrTimetableReplacementTest {
    private fun lesson(id: Long, slot: Int, term: TimetableTerm = TimetableTerm.SECOND, year: Int = 2026, subject: String = "数学") = LessonEntity(
        id = id, academicYear = year, timetableTerm = term, dayOfWeek = 1, slotIndex = slot,
        mode = LessonMode.WEEKLY, weeklySubject = subject, weeklyTeacher = "先生",
        aSubject = "", aTeacher = "", bSubject = "", bTeacher = ""
    )

    @Test fun keepsLocalIdsClearsMissingSlotsAndAllocatesNewSlots() {
        val replacements = qrTimetableReplacement(
            listOf(lesson(42, 0), lesson(43, 1)),
            listOf(lesson(999, 0, subject = "英語"), lesson(1000, 2)), 2026, TimetableTerm.SECOND
        ).associateBy { it.slotIndex }
        assertEquals(42L, replacements.getValue(0).id)
        assertEquals("英語", replacements.getValue(0).weeklySubject)
        assertEquals(43L, replacements.getValue(1).id)
        assertEquals("", replacements.getValue(1).weeklySubject)
        assertEquals("", replacements.getValue(1).weeklyTeacher)
        assertEquals(0L, replacements.getValue(2).id)
    }

    @Test fun excludesOtherTermsAndYears() {
        val rows = listOf(lesson(1, 0), lesson(2, 1, TimetableTerm.FIRST), lesson(3, 2, year = 2025))
        val replacements = qrTimetableReplacement(rows, rows, 2026, TimetableTerm.SECOND)
        assertEquals(listOf(1L), replacements.map { it.id })
    }
}
