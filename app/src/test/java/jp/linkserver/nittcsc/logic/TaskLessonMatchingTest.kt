package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.ResolvedLesson
import jp.linkserver.nittcsc.data.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TaskLessonMatchingTest {
    private val date = LocalDate.of(2026, 7, 13)

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
