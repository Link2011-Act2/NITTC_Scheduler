package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import jp.linkserver.nittcsc.data.SktjfSourceStateEntity
import jp.linkserver.nittcsc.logic.TimetableTerm
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SktjfEmptySemesterTest {
    private val date = LocalDate.of(2026, 4, 1)
    private val doc = SktjfDocument(2026, "S", date, date, "1-1", "2026.S.0",
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    private fun preview(rows: List<LessonEntity>) = SktjfApplyPreview(doc,
        SktjfApplySnapshot(null, null, emptyList(), rows, emptyList(), emptyList(), emptyList()), emptyList(), emptyList())
    private val blank = LessonEntity(id = 1, academicYear = 2026, timetableTerm = TimetableTerm.FIRST,
        dayOfWeek = 1, slotIndex = 0, mode = LessonMode.WEEKLY, weeklySubject = "", weeklyTeacher = "",
        aSubject = "", aTeacher = "", bSubject = "", bTeacher = "")

    @Test fun blankRowsDoNotRequireMerge() {
        assertFalse(preview(emptyList()).hasExistingLessons())
        assertFalse(preview(listOf(blank)).hasExistingLessons())
        assertFalse(preview(listOf(blank.copy(mode = LessonMode.ALTERNATING))).hasExistingLessons())
    }

    @Test fun activeWeeklyAndAlternatingLessonsRequireMerge() {
        assertTrue(preview(listOf(blank.copy(weeklySubject = "数学"))).hasExistingLessons())
        assertTrue(preview(listOf(blank.copy(mode = LessonMode.ALTERNATING, bSubject = "保体"))).hasExistingLessons())
    }

    @Test fun lessonsInOtherYearsOrSemestersDoNotAffectTheTarget() {
        assertFalse(preview(listOf(blank.copy(academicYear = 2025, weeklySubject = "数学"))).hasExistingLessons())
        assertFalse(preview(listOf(blank.copy(timetableTerm = TimetableTerm.SECOND, weeklySubject = "数学"))).hasExistingLessons())
    }

    @Test fun previousImportRequiresReviewEvenAfterEveryLessonIsDeleted() {
        val source = SktjfSourceStateEntity(2026, "S", "1-1", "2026.S.0", "{}", false, "[]")
        val blankPreview = preview(listOf(blank))
        assertTrue(blankPreview.copy(snapshot = blankPreview.snapshot.copy(sourceStates = listOf(source))).hasExistingLessons())
        assertFalse(blankPreview.copy(snapshot = blankPreview.snapshot.copy(sourceStates = listOf(source.copy(term = "F")))).hasExistingLessons())
    }
}
