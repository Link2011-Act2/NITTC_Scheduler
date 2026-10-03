package jp.linkserver.nittcsc.data

import jp.linkserver.nittcsc.logic.QrShareException
import jp.linkserver.nittcsc.logic.TimetableTerm
import jp.linkserver.nittcsc.logic.qrShareMerge
import jp.linkserver.nittcsc.logic.qrLessonReference
import jp.linkserver.nittcsc.logic.QrDayTypesScope
import jp.linkserver.nittcsc.logic.qrDayTypesRange
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class QrShareJsonTest {
    private val date = LocalDate.of(2026, 10, 2)
    private fun payload() = QrSharePayload(
        2026, setOf(QrShareSection.SECOND, QrShareSection.DAY_TYPES, QrShareSection.NOTES, QrShareSection.TASKS), 10, 1,
        classLabel = "3E", createdAt = 100L,
        scheduleTimes = SettingsEntity(termStart = date, termEnd = date.plusMonths(5)).qrScheduleTimes(),
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

    @Test fun mergesLegacyVersionOneQrWithoutReplacingExistingItems() {
        val incoming = QrShareJson.decode("""
            {"format":"SKTTP/QR","version":1,"year":2026,"label":"3E","created":100,
             "semesterStart":[10,1],"sections":["NOTES","TASKS"],
             "notes":[["2026-10-02",0,"共有するメモ"]],
             "tasks":[["数学","担当A","レポート","提出内容","2026-10-02",23,59,false,null,"2026-10-02",1,true],
                      ["英語",null,"予習",null,"2026-10-03",20,0,false,null,"2026-10-02",0,false]]}
        """.trimIndent())
        val existing = incoming.tasks.first().asTask().copy(id = 42, isCompleted = true, reminderEnabled = true)
        val result = qrShareMerge(listOf(existing), incoming.tasks, emptyList(), incoming.plans,
            listOf(LessonNoteEntity(date, 0, "共有するメモ", 1)), incoming.notes)
        assertEquals("予習", result.tasks.single().title)
        assertEquals(1, result.summary.added)
        assertEquals(2, result.summary.duplicates)
        assertTrue(result.replacedNotes.isEmpty())
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

    @Test fun includesClockSettingsOnlyWithRegularTimetable() {
        val root = JSONObject(QrShareJson.encode(payload()))
        assertEquals(3, root.getInt("version"))
        assertEquals(payload().scheduleTimes, QrShareJson.decode(root.toString()).scheduleTimes)
        root.remove("scheduleTimes")
        invalid(root)
        val personal = JSONObject(QrShareJson.encode(payload().copy(sections = setOf(QrShareSection.TASKS))))
        assertFalse(personal.has("scheduleTimes"))
        val invalidTimes = JSONObject(QrShareJson.encode(payload()))
        invalidTimes.getJSONArray("scheduleTimes").put(5, 24)
        invalid(invalidTimes)
    }

    @Test fun roundTripsPortableLessonLinksForTasksAndPlansWithoutDeviceIds() {
        val reference = qrLessonReference(payload().lessons.single())
        val linked = payload().tasks.single().copy(lessonReference = reference)
        val data = payload().copy(sections = setOf(QrShareSection.TASKS, QrShareSection.PLANS), tasks = listOf(linked), plans = listOf(linked))
        val json = QrShareJson.encode(data)
        val read = QrShareJson.decode(json)
        assertEquals(reference, read.tasks.single().lessonReference)
        assertEquals(reference, read.plans.single().lessonReference)
        assertFalse(json.contains("lessonId"))
        assertFalse(json.contains("calendarEventId"))
        val invalidRef = JSONObject(json)
        invalidRef.getJSONArray("tasks").getJSONArray(0).getJSONArray(12).put(3, 12)
        invalid(invalidRef)
        val invalidHash = JSONObject(json)
        invalidHash.getJSONArray("tasks").getJSONArray(0).getJSONArray(12).put(4, "invalid")
        invalid(invalidHash)
    }

    private fun examPayload() = payload().copy(
        sections = setOf(QrShareSection.EXAMS),
        examDays = listOf(DayTypeEntity(date, DayType.HOLIDAY, holidaySpecialLabel = HolidaySpecialLabel.MIDTERM)),
        examDaySchedules = listOf(ExamDayScheduleEntity(date, 8, 30, "後期中間", 1)),
        examLessons = listOf(ExamLessonEntity(date, 0, 8, 50, 9, 40, "数学", "担当A", "教室", "電卓を持参", 1))
    )

    @Test fun sharesExamsIndependentlyOfRegularTimetableAndAbTable() {
        val json = QrShareJson.encode(examPayload())
        val root = JSONObject(json)
        val read = QrShareJson.decode(json)
        assertEquals(setOf(QrShareSection.EXAMS), read.sections)
        assertFalse(root.has("lessons"))
        assertFalse(root.has("scheduleTimes"))
        assertFalse(root.has("days"))
        assertEquals(HolidaySpecialLabel.MIDTERM, read.examDays.single().holidaySpecialLabel)
        assertEquals(examPayload().examDaySchedules.single().copy(updatedAt = 0), read.examDaySchedules.single().copy(updatedAt = 0))
        assertEquals(examPayload().examLessons.single().copy(updatedAt = 0), read.examLessons.single().copy(updatedAt = 0))
        assertEquals(1, read.count(QrShareSection.EXAMS))
    }

    @Test fun rejectsInvalidExamTimesOrphanRowsDuplicateKeysAndOtherYears() {
        val reversed = JSONObject(QrShareJson.encode(examPayload()))
        reversed.getJSONArray("examLessons").getJSONArray(0).put(4, 8).put(5, 40)
        invalid(reversed)
        val orphan = JSONObject(QrShareJson.encode(examPayload()))
        orphan.getJSONArray("examLessons").getJSONArray(0).put(0, "2026-10-03")
        invalid(orphan)
        val duplicate = JSONObject(QrShareJson.encode(examPayload()))
        duplicate.getJSONArray("examLessons").put(duplicate.getJSONArray("examLessons").getJSONArray(0))
        invalid(duplicate)
        val otherYear = JSONObject(QrShareJson.encode(examPayload()))
        otherYear.getJSONArray("examDays").getJSONArray(0).put(0, "2025-10-02")
        invalid(otherYear)
        val wrongLabel = JSONObject(QrShareJson.encode(examPayload()))
        wrongLabel.getJSONArray("examDays").getJSONArray(0).put(1, "EVENT")
        invalid(wrongLabel)
    }

    @Test fun rejectsContradictoryExamAndAbDayLabels() {
        val data = examPayload().copy(sections = setOf(QrShareSection.EXAMS, QrShareSection.DAY_TYPES), dayTypes = payload().dayTypes)
        invalid(JSONObject(QrShareJson.encode(data)))
        val consistent = data.copy(dayTypes = data.examDays)
        assertEquals(consistent.examDays, QrShareJson.decode(QrShareJson.encode(consistent)).examDays)
    }

    private fun invalid(root: JSONObject) {
        try { QrShareJson.decode(root.toString()); fail("Expected invalid payload") } catch (_: QrShareException) { }
    }

    @Test fun scopedAbRoundTripRejectsMissingScopeOtherSemesterAndUnrelatedBreaks() {
        val ab = payload().copy(sections = setOf(QrShareSection.DAY_TYPES), dayTypesScope = QrDayTypesScope.SECOND,
            longBreaks = listOf(LongBreakEntity(name = "春休み", startDate = LocalDate.of(2027, 3, 20), endDate = LocalDate.of(2027, 4, 10))))
        val read = QrShareJson.decode(QrShareJson.encode(ab))
        assertEquals(QrDayTypesScope.SECOND, read.dayTypesScope)
        assertEquals(ab.longBreaks, read.longBreaks)
        val missing = JSONObject(QrShareJson.encode(ab))
        missing.remove("daysScope")
        invalid(missing)
        invalid(JSONObject(QrShareJson.encode(ab)).put("daysScope", "FIRST"))
        val unrelated = ab.copy(longBreaks = listOf(LongBreakEntity(name = "前年度", startDate = date.minusYears(1), endDate = date.minusYears(1))))
        invalid(JSONObject(QrShareJson.encode(unrelated)))
        invalid(JSONObject(QrShareJson.encode(ab.copy(dayTypes = listOf(DayTypeEntity(date.plusYears(1), DayType.A))))))
    }

    @Test fun legacyAbKeepsAllYearMeaningAndRoundTripsVersionOneAndTwo() {
        for (version in listOf(1, 2)) {
            val old = payload().copy(formatVersion = version, sections = setOf(QrShareSection.DAY_TYPES),
                dayTypes = listOf(DayTypeEntity(date.minusYears(1), DayType.B)))
            val root = JSONObject(QrShareJson.encode(old))
            assertFalse(root.has("daysScope"))
            val read = QrShareJson.decode(root.toString())
            assertNull(read.qrDayTypesRange())
            assertEquals(old.dayTypes, read.dayTypes)
        }
    }

    @Test fun examsOutsideSharedSemesterDoNotRequireAbRowsForOtherSemester() {
        val data = examPayload().copy(sections = setOf(QrShareSection.EXAMS, QrShareSection.DAY_TYPES),
            dayTypesScope = QrDayTypesScope.FIRST, dayTypes = emptyList())
        assertEquals(data.examDays, QrShareJson.decode(QrShareJson.encode(data)).examDays)
    }
}
