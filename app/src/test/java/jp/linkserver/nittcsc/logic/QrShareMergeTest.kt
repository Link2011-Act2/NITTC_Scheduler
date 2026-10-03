package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.LessonNoteEntity
import jp.linkserver.nittcsc.data.QrSharedItem
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class QrShareMergeTest {
    private val date = LocalDate.of(2026, 10, 3)
    private fun item(title: String = "レポート") = QrSharedItem(
        "数学", "担当A", title, "提出内容", date, 23, 59, false, null, date, 0, true
    )

    @Test fun addsOnlyNewTasksAndKeepsExistingPersonalState() {
        val current = item().asTask().copy(
            id = 42, lessonId = 123, isCompleted = true, completedDate = date,
            description = "自分の追記", priority = 1, calendarEventId = 50,
            reminderEnabled = true, reminderDate = date.minusDays(1), reminderCalendarEventId = 51
        )
        val unrelated = item("自分の課題").asTask().copy(id = 43)
        val existing = listOf(current, unrelated)
        val incoming = item("新しい課題")
        val result = qrShareMerge(existing, listOf(item(), incoming), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(listOf(incoming), result.tasks)
        assertEquals(1, result.summary.addedTasks)
        assertEquals(1, result.summary.duplicateTasks)
        assertEquals(listOf(current, unrelated), existing)
        assertTrue(existing[0].isCompleted)
        assertTrue(existing[0].reminderEnabled)
        assertEquals(50L, existing[0].calendarEventId)
    }

    @Test fun ignoresCompletionCreationDatePriorityAndDescriptionForDuplicates() {
        val existing = item().asPlan().copy(id = 7, reminderEnabled = true)
        val received = item().copy(completed = true, completedDate = date, createdDate = date.minusDays(2),
            priority = 1, description = "修正された説明", useTeacherMatching = false)
        val result = qrShareMerge(emptyList(), emptyList(), listOf(existing), listOf(received), emptyList(), emptyList())
        assertTrue(result.plans.isEmpty())
        assertEquals(1, result.summary.duplicatePlans)
    }

    @Test fun distinguishesSubjectTeacherTitleAndExactDueTime() {
        val variations = listOf(
            item().copy(subject = "英語"), item().copy(teacher = "担当B"), item("別の課題"),
            item().copy(dueDate = date.plusDays(1)), item().copy(dueHour = 22), item().copy(dueMinute = 58)
        )
        val result = qrShareMerge(listOf(item().asTask()), variations, emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(variations, result.tasks)
        assertEquals(0, result.summary.duplicateTasks)
    }

    @Test fun normalizesOuterWhitespaceAndAbsentTeacher() {
        val current = item().copy(teacher = null).asTask()
        val incoming = item().copy(subject = " 数学 ", teacher = " ", title = " レポート ")
        val result = qrShareMerge(listOf(current), listOf(incoming), emptyList(), emptyList(), emptyList(), emptyList())
        assertTrue(result.tasks.isEmpty())
        assertEquals(1, result.summary.duplicateTasks)
    }

    @Test fun deduplicatesWithinPayloadAndTreatsPlansAndTasksSeparately() {
        val result = qrShareMerge(emptyList(), listOf(item(), item()), emptyList(), listOf(item(), item()), emptyList(), emptyList())
        assertEquals(listOf(item()), result.tasks)
        assertEquals(listOf(item()), result.plans)
        assertEquals(2, result.summary.added)
        assertEquals(2, result.summary.duplicates)
    }

    @Test fun addsMissingNotesSkipsIdenticalAndKeepsConflictingNotesByDefault() {
        val same = LessonNoteEntity(date, 0, "同じ内容", 10)
        val conflict = LessonNoteEntity(date, 1, "自分のメモ", 20)
        val unrelated = LessonNoteEntity(date.minusYears(1), 1, "他年度のメモ", 30)
        val newNote = LessonNoteEntity(date, 2, "新しいメモ", 40)
        val incomingConflict = conflict.copy(text = "受信内容", updatedAt = 50)
        val result = qrShareMerge(emptyList(), emptyList(), emptyList(), emptyList(), listOf(same, conflict, unrelated),
            listOf(same.copy(updatedAt = 60), incomingConflict, newNote))
        assertEquals(listOf(newNote), result.addedNotes)
        assertTrue(result.replacedNotes.isEmpty())
        assertEquals(listOf(QrNoteConflict(conflict, incomingConflict)), result.noteConflicts)
        assertEquals(1, result.summary.addedNotes)
        assertEquals(1, result.summary.duplicateNotes)
        assertEquals(1, result.summary.keptNotes)
    }

    @Test fun replacesOnlyIndividuallyApprovedNotes() {
        val first = LessonNoteEntity(date, 0, "メモ1", 10)
        val second = LessonNoteEntity(date, 1, "メモ2", 20)
        val receivedFirst = first.copy(text = "受信1", updatedAt = 30)
        val result = qrShareMerge(emptyList(), emptyList(), emptyList(), emptyList(), listOf(first, second),
            listOf(receivedFirst, second.copy(text = "受信2")), setOf(first))
        assertEquals(listOf(receivedFirst), result.replacedNotes)
        assertEquals(1, result.summary.replacedNotes)
        assertEquals(1, result.summary.keptNotes)
    }

    @Test fun doesNotOverwriteNoteEditedAfterApproval() {
        val approved = LessonNoteEntity(date, 0, "確認時の内容", 10)
        for (current in listOf(approved.copy(text = "編集済み"), approved.copy(updatedAt = 20))) {
            val result = qrShareMerge(emptyList(), emptyList(), emptyList(), emptyList(), listOf(current),
                listOf(approved.copy(text = "受信内容")), setOf(approved))
            assertTrue(result.replacedNotes.isEmpty())
            assertEquals(1, result.summary.keptNotes)
        }
    }

    @Test fun reimportingSameQrDoesNotAddItemsOrChangeNotes() {
        val note = LessonNoteEntity(date, 0, "メモ", 10)
        val first = qrShareMerge(emptyList(), listOf(item()), emptyList(), listOf(item()), emptyList(), listOf(note))
        val second = qrShareMerge(first.tasks.map { it.asTask().copy(id = 1, reminderEnabled = true) }, listOf(item()),
            first.plans.map { it.asPlan().copy(id = 2, isCompleted = true) }, listOf(item()), first.addedNotes, listOf(note))
        assertTrue(second.tasks.isEmpty())
        assertTrue(second.plans.isEmpty())
        assertTrue(second.addedNotes.isEmpty())
        assertTrue(second.replacedNotes.isEmpty())
        assertEquals(0, second.summary.added)
        assertEquals(3, second.summary.duplicates)
    }
}
