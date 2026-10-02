package jp.linkserver.nittcsc.data

import jp.linkserver.nittcsc.logic.QrShareException
import jp.linkserver.nittcsc.logic.TimetableTerm
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class QrShareJsonTest {
    private val date = LocalDate.of(2026, 10, 2)
    private fun payload() = QrSharePayload(
        2026, setOf(QrShareSection.SECOND, QrShareSection.DAY_TYPES, QrShareSection.NOTES, QrShareSection.TASKS), 10, 1,
        classLabel = "3E", createdAt = 100L,
        lessons = listOf(LessonEntity(id = 999, academicYear = 2026, timetableTerm = TimetableTerm.SECOND, dayOfWeek = 1, slotIndex = 0,
            mode = LessonMode.ALTERNATING, weeklySubject = "", weeklyTeacher = "", aSubject = "数学", aTeacher = "担当A", bSubject = "英語", bTeacher = "担当B")),
        dayTypes = listOf(DayTypeEntity(date, DayType.B, 1, DayType.A)),
        notes = listOf(LessonNoteEntity(date, 0, "共有するメモ")),
        tasks = listOf(QrSharedItem("数学", "担当A", "レポート", "提出内容", date, 23, 59, false, null, date, 1, true))
    )

    @Test fun roundTripsPortableDataWithoutIdsOrSettings() {
        val json = QrShareJson.encode(payload())
        val read = QrShareJson.decode(json)
        assertEquals("3E", read.classLabel)
        assertEquals(payload().sections, read.sections)
        assertEquals("英語", read.lessons.single().bSubject)
        assertEquals(payload().dayTypes, read.dayTypes)
        assertEquals(payload().tasks, read.tasks)
        assertEquals(0L, read.lessons.single().id)
        val task = read.tasks.single().asTask()
        assertNull(task.lessonId)
        assertNull(task.calendarEventId)
        assertNull(task.reminderCalendarEventId)
        assertFalse(task.reminderEnabled)
        assertFalse(json.contains("deviceId"))
        assertFalse(json.contains("settings"))
        assertFalse(json.contains("calendarEventId"))
    }

    @Test fun distinguishesUnselectedFromSelectedEmptyDataset() {
        val json = QrShareJson.encode(payload().copy(sections = setOf(QrShareSection.NOTES), notes = emptyList()))
        val root = JSONObject(json)
        assertTrue(root.has("notes"))
        assertFalse(root.has("tasks"))
        assertEquals(setOf(QrShareSection.NOTES), QrShareJson.decode(json).sections)
    }

    @Test fun rejectsInvalidRowsBeforeAnyImport() {
        val root = JSONObject(QrShareJson.encode(payload()))
        root.getJSONArray("tasks").getJSONArray(0).put(5, 24)
        invalid(root)
    }

    @Test fun rejectsDeepNestingAndAllowsBracketsAndQuotesInText() {
        try { QrShareJson.decode("{\"notes\":" + "[".repeat(10000) + "0" + "]".repeat(10000) + "}"); fail("Expected invalid nesting") }
        catch (_: QrShareException) { }
        val data = payload().copy(notes = listOf(LessonNoteEntity(date, 0, "[メモ] {内容} \\\"引用\\\"")))
        assertEquals(data.notes.single().text, QrShareJson.decode(QrShareJson.encode(data)).notes.single().text)
    }

    @Test fun rejectsDuplicateDayKeysAndUnexpectedDatasets() {
        val root = JSONObject(QrShareJson.encode(payload()))
        root.getJSONArray("days").put(root.getJSONArray("days").getJSONArray(0))
        invalid(root)
        val other = JSONObject(QrShareJson.encode(payload())).put("settings", JSONObject().put("hfToken", "secret"))
        invalid(other)
    }

    @Test fun rejectsDataForAnUnselectedTermAndDifferentYear() {
        val root = JSONObject(QrShareJson.encode(payload()))
        root.getJSONArray("lessons").getJSONArray(0).put(0, "FIRST")
        invalid(root)
        val other = JSONObject(QrShareJson.encode(payload().copy(sections = setOf(QrShareSection.CANCELLATIONS), cancellations = listOf(CancelledLessonEntity(date.minusYears(1), 0)))))
        invalid(other)
    }

    private fun invalid(root: JSONObject) {
        try { QrShareJson.decode(root.toString()); fail("Expected invalid payload") } catch (_: QrShareException) { }
    }
}
