package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.LessonDraft
import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

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
            ),
            currentDate = LocalDate.of(2026, 6, 1),
            semesterTimetablesEnabled = true
        )

        assertEquals(listOf("数学", "英語"), options.subjectSuggestions)
        assertEquals(listOf("佐藤", "鈴木"), options.subjectTeacherCandidates["数学"])
        assertEquals(listOf("山田"), options.subjectTeacherCandidates["英語"])
    }

    @Test
    fun usesOnlyCurrentSemesterTeachersAndLocationsForExistingSubjects() {
        val options = options(listOf(
            lesson(weeklySubject = "英語", weeklyTeacher = "前期教員", weeklyLocation = "旧教室"),
            lesson(academicYear = 2025, term = TimetableTerm.SECOND,
                weeklySubject = "英語", weeklyTeacher = "過年度教員"),
            lesson(term = TimetableTerm.SECOND,
                weeklySubject = "英語", weeklyTeacher = "後期教員", weeklyLocation = "新教室")
        ))

        assertEquals(listOf("後期教員"), options.subjectTeacherCandidates["英語"])
        assertEquals(listOf("新教室"), options.subjectLocationCandidates["英語"])
        assertEquals(listOf("新教室"), options.subjectTeacherLocationCandidates["英語" to "後期教員"])
        assertEquals(listOf(NaturalLanguageLessonCandidate("英語", "後期教員")), options.naturalLanguageCandidates)
        assertEquals("後期教員", options.resolveTeacherForAutoFill("英語", "前期教員"))
    }

    @Test
    fun fallsBackToOtherSemestersAndYearsOnlyForSubjectsAbsentFromCurrentSemester() {
        val options = options(listOf(
            lesson(weeklySubject = "歴史", weeklyTeacher = "前期教員"),
            lesson(academicYear = 2025, term = TimetableTerm.SECOND,
                weeklySubject = "歴史", weeklyTeacher = "過年度教員"),
            lesson(term = TimetableTerm.SECOND, weeklySubject = "数学", weeklyTeacher = "今期教員")
        ))

        assertEquals(listOf("前期教員", "過年度教員"), options.subjectTeacherCandidates["歴史"])
        assertEquals("前期教員", options.resolveTeacherForAutoFill("歴史", "前期教員"))
        assertEquals("画像の教員", options.resolveTeacherForAutoFill("未登録教科", "画像の教員"))
        assertEquals("今期教員", options.resolveTeacherForAutoFill("数学", null))
    }

    @Test
    fun doesNotFallBackWhenCurrentSemesterSubjectHasNoTeacher() {
        val options = options(listOf(
            lesson(weeklySubject = "英語", weeklyTeacher = "前期教員"),
            lesson(term = TimetableTerm.SECOND, weeklySubject = "英語", weeklyTeacher = " ")
        ))

        assertEquals(emptyList<String>(), options.subjectTeacherCandidates["英語"])
        assertNull(options.resolveTeacherForAutoFill("英語", "前期教員"))
        assertEquals(listOf(NaturalLanguageLessonCandidate("英語", null)), options.naturalLanguageCandidates)
    }

    @Test
    fun keepsMultipleCurrentTeachersWithoutChoosingAnOldTeacher() {
        val options = options(listOf(
            lesson(weeklySubject = "英語", weeklyTeacher = "前期教員"),
            lesson(term = TimetableTerm.SECOND, mode = LessonMode.ALTERNATING,
                aSubject = "英語", aTeacher = "A教員", bSubject = "英語", bTeacher = "B教員")
        ))

        assertEquals(listOf("A教員", "B教員"), options.subjectTeacherCandidates["英語"])
        assertNull(options.resolveTeacherForAutoFill("英語", "前期教員"))
        assertEquals("B教員", options.resolveTeacherForAutoFill("英語", "B教員"))
    }

    @Test
    fun matchesSubjectNamesIgnoringCaseAndSurroundingWhitespace() {
        val options = options(listOf(
            lesson(weeklySubject = " English ", weeklyTeacher = "Old"),
            lesson(term = TimetableTerm.SECOND, weeklySubject = "english", weeklyTeacher = " Current ")
        ))

        assertEquals(listOf("english"), options.subjectSuggestions)
        assertEquals(listOf("Current"), options.subjectTeacherCandidates["english"])
        assertEquals("Current", options.resolveTeacherForAutoFill(" ENGLISH ", "Old"))
    }

    @Test
    fun switchesCandidatesAtConfiguredSecondSemesterStart() {
        val lessons = listOf(
            lesson(weeklySubject = "数学", weeklyTeacher = "前期"),
            lesson(term = TimetableTerm.SECOND, weeklySubject = "数学", weeklyTeacher = "後期")
        )

        assertEquals(listOf("前期"), options(lessons, LocalDate.of(2026, 9, 30)).subjectTeacherCandidates["数学"])
        assertEquals(listOf("後期"), options(lessons).subjectTeacherCandidates["数学"])
        assertEquals(listOf("前期"), options(lessons, LocalDate.of(2026, 9, 14), 9, 15).subjectTeacherCandidates["数学"])
        assertEquals(listOf("後期"), options(lessons, LocalDate.of(2026, 9, 15), 9, 15).subjectTeacherCandidates["数学"])
    }

    @Test
    fun usesAcademicYearOfTodayRatherThanAnotherPreparedYear() {
        val lessons = listOf(
            lesson(term = TimetableTerm.SECOND, weeklySubject = "数学", weeklyTeacher = "2026後期"),
            lesson(academicYear = 2027, weeklySubject = "数学", weeklyTeacher = "2027前期"),
            lesson(academicYear = 2025, term = TimetableTerm.SECOND,
                weeklySubject = "数学", weeklyTeacher = "2025後期")
        )

        assertEquals(listOf("2026後期"), options(lessons, LocalDate.of(2027, 1, 10)).subjectTeacherCandidates["数学"])
        assertEquals(listOf("2027前期"), options(lessons, LocalDate.of(2027, 4, 1)).subjectTeacherCandidates["数学"])
    }

    @Test
    fun usesFirstSemesterWhenSemesterTimetablesAreDisabled() {
        val options = buildLessonAutocompleteOptions(
            listOf(
                lesson(weeklySubject = "数学", weeklyTeacher = "基本時間割"),
                lesson(term = TimetableTerm.SECOND, weeklySubject = "数学", weeklyTeacher = "後期")
            ),
            currentDate = LocalDate.of(2026, 10, 1), semesterTimetablesEnabled = false
        )
        assertEquals(listOf("基本時間割"), options.subjectTeacherCandidates["数学"])
    }

    @Test
    fun appliesTeacherPreferenceToWeeklyAndAlternatingImportedDrafts() {
        val options = options(listOf(
            lesson(term = TimetableTerm.SECOND, weeklySubject = "数学", weeklyTeacher = "今期数学"),
            lesson(term = TimetableTerm.SECOND, weeklySubject = "英語", weeklyTeacher = "今期英語")
        ))
        val result = options.resolveDraftTeachersForAutoFill(LessonDraft(
            weeklySubject = "数学", weeklyTeacher = "画像数学", weeklyLocation = "画像教室",
            aSubject = "英語", aTeacher = "画像英語", bSubject = "歴史", bTeacher = "画像歴史"
        ))

        assertEquals("今期数学", result.weeklyTeacher)
        assertEquals("今期英語", result.aTeacher)
        assertEquals("画像歴史", result.bTeacher)
        assertEquals("画像教室", result.weeklyLocation)
    }

    @Test
    fun naturalLanguageParsingCannotSelectTeacherFromOtherSemesterForCurrentSubject() {
        val options = options(listOf(
            lesson(weeklySubject = "数学", weeklyTeacher = "前期教員"),
            lesson(term = TimetableTerm.SECOND, weeklySubject = "数学", weeklyTeacher = "後期教員")
        ))
        val result = NaturalLanguageTaskParser.parse(
            "前期教員の数学レポートを明日まで", options.naturalLanguageCandidates,
            LocalDate.of(2026, 10, 1), java.time.LocalTime.NOON
        )
        assertEquals("後期教員", result?.teacher)
    }

    @Test
    fun replacesAutomaticTeacherWhenSemesterChanges() {
        assertEquals(
            TeacherAutoFillResult("後期教員", "後期教員"),
            updateTeacherAutoFill("前期教員", "前期教員", listOf("後期教員"))
        )
    }

    @Test
    fun clearsPreviousAutomaticTeacherWhenNewSubjectHasNoUniqueTeacher() {
        assertEquals(TeacherAutoFillResult("", null), updateTeacherAutoFill("旧教員", "旧教員", emptyList()))
        assertEquals(
            TeacherAutoFillResult("", null),
            updateTeacherAutoFill("旧教員", "旧教員", listOf("A教員", "B教員"))
        )
        assertEquals(
            TeacherAutoFillResult("A教員", "A教員"),
            updateTeacherAutoFill("A教員", "A教員", listOf("A教員", "B教員"))
        )
    }

    @Test
    fun preservesManuallyEnteredAndSavedTeachersWhenCandidatesChange() {
        assertEquals(
            TeacherAutoFillResult("手入力", null),
            updateTeacherAutoFill("手入力", null, listOf("今期教員"))
        )
        assertEquals(TeacherAutoFillResult("保存済み", null), updateTeacherAutoFill("保存済み", null, emptyList()))
    }

    private fun options(
        lessons: List<LessonEntity>,
        date: LocalDate = LocalDate.of(2026, 10, 1),
        month: Int = 10,
        day: Int = 1
    ) = buildLessonAutocompleteOptions(lessons, date, true, month, day)

    private fun lesson(
        mode: LessonMode = LessonMode.WEEKLY,
        academicYear: Int = 2026,
        term: TimetableTerm = TimetableTerm.FIRST,
        weeklySubject: String = "",
        weeklyTeacher: String = "",
        weeklyLocation: String? = null,
        aSubject: String = "",
        aTeacher: String = "",
        bSubject: String = "",
        bTeacher: String = ""
    ) = LessonEntity(
        academicYear = academicYear,
        timetableTerm = term,
        dayOfWeek = 1,
        slotIndex = 0,
        mode = mode,
        weeklySubject = weeklySubject,
        weeklyTeacher = weeklyTeacher,
        weeklyLocation = weeklyLocation,
        aSubject = aSubject,
        aTeacher = aTeacher,
        bSubject = bSubject,
        bTeacher = bTeacher
    )
}
