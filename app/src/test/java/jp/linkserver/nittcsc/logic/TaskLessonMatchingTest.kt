package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.ResolvedLesson
import jp.linkserver.nittcsc.data.TaskEntity
import jp.linkserver.nittcsc.data.PlanEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TaskLessonMatchingTest {
    private val date = LocalDate.of(2026, 7, 13)

    @Test fun romanNumeralVariantsMatchExactlyAndDoNotMatchOtherLevels() {
        assertTrue(taskMatchesLesson(task(subject = "英語II"), ResolvedLesson("英語Ⅱ", "")))
        assertTrue(taskMatchesLesson(task(subject = "数学Ⅳ"), ResolvedLesson("数学IV", "")))
        assertTrue(taskMatchesLesson(task(subject = "保体I"), ResolvedLesson("保体Ⅰ", "")))
        assertFalse(taskMatchesLesson(task(subject = "英語I"), ResolvedLesson("英語II", "")))
        assertFalse(taskMatchesLesson(task(subject = "英語II").copy(extraLessonId = "other"), ResolvedLesson("英語Ⅱ", "")))
    }

    @Test
    fun matchesSubjectAndRequestedTeacher() {
        val task = task(subject = "英語", teacher = "山田", useTeacherMatching = true)

        assertTrue(taskMatchesLesson(task, ResolvedLesson("英語", "佐藤、山田")))
        assertFalse(taskMatchesLesson(task, ResolvedLesson("英語", "佐藤")))
        assertFalse(taskMatchesLesson(task, ResolvedLesson("数学", "山田")))
    }

    @Test
    fun examSlotCanBeSelectedBySubjectWithoutDueTimeOverlap() {
        val task = task(subject = "数学", dueHour = 17, dueMinute = 0)
        val lessonSlots = listOf(
            ClassSlot(0, "1時間目", LocalTime.of(8, 50), LocalTime.of(9, 40)) to
                ResolvedLesson("英語", "山田"),
            ClassSlot(1, "2時間目", LocalTime.of(10, 0), LocalTime.of(10, 50)) to
                ResolvedLesson("数学", "佐藤")
        )

        assertNull(findTaskLessonSlotIndex(task, lessonSlots, ignoreDueTime = false))
        assertEquals(1, findTaskLessonSlotIndex(task, lessonSlots, ignoreDueTime = true))
    }

    @Test
    fun supplementaryLessonStillMatchesRelatedTask() {
        val task = task(subject = "補講", teacher = "山田", useTeacherMatching = true)

        assertTrue(taskMatchesLesson(task, ResolvedLesson("補講", "山田", "3-A")))
    }

    @Test
    fun explicitlyLinkedExtraDoesNotMatchOrdinaryLessonByName() {
        val linked = task(subject = "数学").copy(extraLessonId = "extra-1")
        assertFalse(taskMatchesLesson(linked, ResolvedLesson("数学", "先生")))
        assertTrue(taskMatchesLesson(linked.copy(extraLessonId = null), ResolvedLesson("数学", "先生")))
    }

    @Test
    fun linkedLessonIdPreventsMatchingAnotherSlotAndStillDistinguishesAlternatingSubjects() {
        val linked = task(subject = "英語II").copy(lessonId = 12)
        val sameSlot = ResolvedLesson("英語Ⅱ", "", lessonId = 12)
        val otherSlot = ResolvedLesson("英語Ⅱ", "", lessonId = 13)
        assertTrue(taskMatchesLesson(linked, sameSlot))
        assertFalse(taskMatchesLesson(linked, otherSlot))
        assertFalse(taskMatchesLesson(linked, sameSlot.copy(subject = "英語I")))
        assertTrue(taskMatchesLesson(linked, sameSlot.copy(lessonId = null)))

        val plan = PlanEntity(lessonId = 12, subject = "英語II", title = "予定", dueDate = date, createdDate = date)
        assertTrue(planMatchesLesson(plan, sameSlot))
        assertFalse(planMatchesLesson(plan, otherSlot))
    }

    private fun task(
        subject: String,
        teacher: String? = null,
        useTeacherMatching: Boolean = false,
        dueHour: Int = 8,
        dueMinute: Int = 50
    ) = TaskEntity(
        subject = subject,
        teacher = teacher,
        title = "課題",
        dueDate = date,
        dueHour = dueHour,
        dueMinute = dueMinute,
        createdDate = date,
        useTeacherMatching = useTeacherMatching
    )
}
