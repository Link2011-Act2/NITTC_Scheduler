package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LessonAutocompleteTest {
    @Test
    fun buildsSubjectsAndTeachersFromWeeklyAndAlternatingLessons() {
        val options = buildLessonAutocompleteOptions(
            listOf(
                lesson(
                    mode = LessonMode.WEEKLY,
                    weeklySubject = " 数学 ",
                    weeklyTeacher = "佐藤"
                ),
                lesson(
                    mode = LessonMode.ALTERNATING,
                    aSubject = "英語",
                    aTeacher = "山田",
                    bSubject = "数学",
                    bTeacher = "鈴木"
                )
            )
        )

        assertEquals(listOf("数学", "英語"), options.subjectSuggestions)
        assertEquals(listOf("佐藤", "鈴木"), options.subjectTeacherCandidates["数学"])
        assertEquals(listOf("山田"), options.subjectTeacherCandidates["英語"])
    }

    private fun lesson(
        mode: LessonMode,
        weeklySubject: String = "",
        weeklyTeacher: String = "",
        aSubject: String = "",
        aTeacher: String = "",
        bSubject: String = "",
        bTeacher: String = ""
    ) = LessonEntity(
        academicYear = 2026,
        dayOfWeek = 1,
        slotIndex = 0,
        mode = mode,
        weeklySubject = weeklySubject,
        weeklyTeacher = weeklyTeacher,
        aSubject = aSubject,
        aTeacher = aTeacher,
        bSubject = bSubject,
        bTeacher = bTeacher
    )
}
