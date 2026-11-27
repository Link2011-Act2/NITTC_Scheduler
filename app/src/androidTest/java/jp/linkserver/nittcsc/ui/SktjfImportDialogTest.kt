package jp.linkserver.nittcsc.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.room.Room
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.runtime.mutableStateMapOf
import jp.linkserver.nittcsc.R
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.logic.TimetableTerm
import jp.linkserver.nittcsc.logic.LessonWeekRange
import jp.linkserver.nittcsc.sktjf.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class SktjfImportDialogTest {
    @get:Rule val compose = createComposeRule()
    private fun existingRow() = LessonEntity(id = 1, academicYear = 2026, timetableTerm = TimetableTerm.FIRST,
        dayOfWeek = 1, slotIndex = 0, mode = LessonMode.WEEKLY, weeklySubject = "物理", weeklyTeacher = "",
        aSubject = "", aTeacher = "", bSubject = "", bTeacher = "")

    @Test fun calendarReviewShowsBothValuesAndKeepsChoicesAcrossMonths() {
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        val first = LocalDate.of(2026, 10, 7)
        val second = first.plusMonths(1)
        val before = SktjfCalendarContent(SktjfDayType.A, null, null, null)
        val after = SktjfCalendarContent(SktjfDayType.HOLIDAY, null, null,
            SktjfEvent("工場見学", SktjfEventKind.EXCURSION))
        fun conflict(date: LocalDate) = SktjfCalendarConflict(date, null, before, after,
            SktjfCalendarConflictReason.BOTH_CHANGED, projectSktjfCalendarDay(
                SktjfCalendarDay(date, after.dayType, null, null, after.event, emptyList())))
        val document = SktjfDocument(2026, "F", first, second, "1-4", "2026.F.1",
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val snapshot = SktjfApplySnapshot(null, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val preview = SktjfApplyPreview(document, snapshot, emptyList(), listOf(conflict(first), conflict(second)),
            calendarDays = mapOf(first.minusDays(1) to before))
        val choices = mutableStateMapOf<LocalDate, SktjfConflictChoice>()
        compose.setContent { MaterialTheme { Column { SktjfCalendarReview(preview, choices, pending = false) } } }
        compose.onNodeWithTag("sktjf-date-${first.minusDays(1)}").assertExists()
        compose.onNodeWithTag("sktjf-date-$first").performClick()
        compose.onNodeWithText(resources.getString(R.string.sktjf_incoming_value, calendarDetails(resources, after))).assertExists()
        compose.onNodeWithText(resources.getString(R.string.sktjf_merge_chosen_incoming)).performClick()
        compose.onNodeWithText(resources.getString(R.string.sktjf_merge_close)).performClick()
        compose.onNodeWithTag("sktjf-date-$first").assertIsSelected()
        compose.onNodeWithContentDescription(resources.getString(R.string.cd_next_month)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("sktjf-date-$second").assertExists()
        compose.onNodeWithContentDescription(resources.getString(R.string.cd_previous_month)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("sktjf-date-$first").assertIsSelected()
        assertEquals(mapOf(first to SktjfConflictChoice.APPLY_INCOMING), choices)
    }

    @Test fun mergeDetailsShowOpportunityRange() {
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        val lesson = SktjfLesson("x", 1, 1, SktjfCycle.WEEKLY, "製図", emptyList(), emptyList(),
            SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null, emptyList()).content()
        assertEquals("8回目まで：製図", lessonSubjectWithRange(resources,
            lesson.copy(weekRange = SktjfV2Range(LessonWeekRange(end = 8), "～8週"))))
        assertEquals("9回目以降：製図", lessonSubjectWithRange(resources,
            lesson.copy(weekRange = SktjfV2Range(LessonWeekRange(start = 9), "9週～"))))
    }

    @Test fun timedAlternativeCanBeEditedAndDifferentSubjectCanKeepLinkedTasks() {
        val date = LocalDate.of(2026, 4, 6)
        val lesson = SktjfLesson("after", 1, 1, SktjfCycle.WEEKLY, "卒研", emptyList(), emptyList(),
            SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null, emptyList()).content()
            .copy(weekRange = SktjfV2Range(LessonWeekRange(start = 9), "9週～"))
        val extra = SktjfVisibleExtra("extra", "after", lesson)
        val task = TaskEntity(extraLessonId = "extra", subject = "卒研", title = "課題", dueDate = date, createdDate = date)
        var saved: SktjfExtraLessonDraft? = null
        compose.setContent { MaterialTheme {
            TimedAlternativeEditors(listOf(extra), listOf(task), emptyList()) { item, draft ->
                assertEquals("extra", item.id); saved = draft; true
            }
        } }
        compose.onNodeWithText("9回目以降：卒研").performClick()
        compose.onNodeWithText("卒研").performTextClearance()
        compose.onNodeWithText("授業名").performTextInput("別科目")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("関連する課題と予定の科目名も変更しますか？").assertExists()
        compose.onNodeWithText("授業のみ変更").performClick()
        compose.waitForIdle()
        assertEquals("別科目", saved?.subject)
        assertEquals(false, saved?.followDifferentSubject)
    }

    @Test fun loadingShowsCurrentOperation() {
        val discovery = CompletableDeferred<SktjfDiscoveryResult>()
        compose.setContent { MaterialTheme {
            SktjfImportDialog(UsageModeEntity(mode = "NITTC"),
                listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)), 2026,
                { _, _ -> discovery.await() }, { _, _ -> SktjfFetchResult.ReleaseNotFound },
                { SktjfPreviewResult.Blocked }, { _, _, _ -> SktjfApplyResult.Blocked }, {}, {})
        } }
        compose.onNodeWithText("配信を確認").performClick()
        compose.onNodeWithText("配信を探しています…").assertExists()
        discovery.complete(SktjfDiscoveryResult.NotPublished)
        compose.waitForIdle()
        compose.onNodeWithText("この時間割はまだ配信されていません").assertExists()
    }

    @Test fun failedDownloadNeverPreviewsOrAppliesAndCanBeRetried() {
        var result: SktjfFetchResult = SktjfFetchResult.NetworkFailure
        var previewCount = 0
        var applyCount = 0
        compose.setContent { MaterialTheme {
            SktjfImportDialog(UsageModeEntity(mode = "NITTC"),
                listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)), 2026,
                { _, _ -> SktjfDiscoveryResult.Found("2026.S.0") }, { _, _ -> result },
                { previewCount++; SktjfPreviewResult.Blocked },
                { _, _, _ -> applyCount++; SktjfApplyResult.Blocked }, {}, {})
        } }
        compose.onNodeWithText("配信を確認").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("通信できませんでした。接続を確認してください").assertExists()
        assertEquals(0, previewCount)
        assertEquals(0, applyCount)

        compose.runOnIdle { result = SktjfFetchResult.InvalidData }
        compose.onNodeWithText("配信を確認").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("配信を読み込めませんでした").assertExists()
        assertEquals(0, previewCount)
        assertEquals(0, applyCount)
    }

    @Test fun emptySemesterAppliesWithoutMergeScreen() {
        val date = LocalDate.of(2026, 4, 6)
        val doc = SktjfDocument(2026, "S", date, date, "1-1", "2026.S.0",
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val snapshot = SktjfApplySnapshot(null, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val incomingDay = SktjfCalendarDay(date, SktjfDayType.B, null, null, null, emptyList())
        val dayConflict = SktjfCalendarConflict(date, null,
            SktjfCalendarContent(SktjfDayType.A, null, null, null), incomingDay.content(),
            SktjfCalendarConflictReason.BOTH_CHANGED, projectSktjfCalendarDay(incomingDay))
        val prepared = SktjfApplyPreview(doc, snapshot, emptyList(), listOf(dayConflict))
        val manifest = SktjfManifest(2026, "S", "2026.S.0", Instant.parse("2026-04-01T00:00:00Z"), emptyList())
        var applied = false
        var dayChoices: Map<LocalDate, SktjfConflictChoice>? = null
        compose.setContent { MaterialTheme {
            SktjfImportDialog(UsageModeEntity(mode = "NITTC"),
                listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)), 2026,
                { _, _ -> SktjfDiscoveryResult.Found("2026.S.0") },
                { _, _ -> SktjfFetchResult.Success(manifest, doc) }, { SktjfPreviewResult.Ready(prepared) },
                { _, _, days -> dayChoices = days; SktjfApplyResult.Applied }, { applied = true }, {})
        } }
        compose.onNodeWithText("配信を確認").performClick()
        compose.waitForIdle()
        assertTrue(applied)
        assertEquals(mapOf(date to SktjfConflictChoice.APPLY_INCOMING), dayChoices)
        compose.onNodeWithText("時間割の変更を確認").assertDoesNotExist()
    }

    @Test fun timedCandidateNeverFallsBackToOrdinaryPreview() {
        val date = LocalDate.of(2026, 4, 6)
        val doc = SktjfDocument(2026, "S", date, date, "1-1", "2026.S.0",
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val candidate = SktjfV2Candidate(doc, emptyMap(), emptyMap())
        val manifest = SktjfManifest(2026, "S", "2026.S.0", Instant.parse("2026-04-01T00:00:00Z"), emptyList())
        var ordinaryPreviews = 0; var timedPreviews = 0; var applies = 0
        compose.setContent { MaterialTheme {
            SktjfImportDialog(UsageModeEntity(mode = "NITTC"),
                listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)), 2026,
                { _, _ -> SktjfDiscoveryResult.Found("2026.S.0") },
                { _, _ -> SktjfFetchResult.Success(manifest, doc, candidate) },
                { ordinaryPreviews++; SktjfPreviewResult.Blocked },
                { _, _, _ -> applies++; SktjfApplyResult.Blocked }, {}, {},
                previewTimed = { timedPreviews++; null })
        } }
        compose.onNodeWithText("配信を確認").performClick()
        compose.waitForIdle()
        assertEquals(1, timedPreviews)
        assertEquals(0, ordinaryPreviews)
        assertEquals(0, applies)
    }

    @Test fun timedSwitchDoesNotTurnAnUngroupedCollisionIntoAnElective() {
        val date = LocalDate.of(2026, 4, 6)
        val condition = SktjfConditions(emptySet(), emptySet())
        val first = SktjfLesson("first", 1, 1, SktjfCycle.WEEKLY, "製図", emptyList(), emptyList(), condition, SktjfLessonKind.NORMAL, null, emptyList())
        val after = first.copy(id = "after", subject = "卒研")
        val choiceA = first.copy(id = "choice-a", period = 2, subject = "選択A")
        val choiceB = choiceA.copy(id = "choice-b", subject = "選択B")
        val doc = SktjfDocument(2026, "S", date, date, "1-1", "2026.S.0", emptyList(), emptyList(), emptyList(),
            listOf(first, after, choiceA, choiceB), emptyList())
        val group = SktjfV2WeekSeries("shared", 1, setOf(1), setOf("first", "after"), SktjfCycle.WEEKLY, emptyList())
        val candidate = SktjfV2Candidate(doc, mapOf("first" to SktjfV2Range(LessonWeekRange(end = 8), "8回まで"),
            "after" to SktjfV2Range(LessonWeekRange(start = 9), "9回から")), emptyMap(), listOf(group))
        val manifest = SktjfManifest(2026, "S", "2026.S.0", Instant.parse("2026-04-01T00:00:00Z"), emptyList())
        var previewed: SktjfV2Candidate? = null
        compose.setContent { MaterialTheme {
            SktjfImportDialog(UsageModeEntity(mode = "NITTC"),
                listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)), 2026,
                { _, _ -> SktjfDiscoveryResult.Found("2026.S.0") },
                { _, _ -> SktjfFetchResult.Success(manifest, doc, candidate) },
                { SktjfPreviewResult.Blocked }, { _, _, _ -> SktjfApplyResult.Blocked }, {}, {},
                previewTimed = { previewed = it; null })
        } }
        compose.onNodeWithText("配信を確認").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("受講する授業を選択").assertDoesNotExist()
        compose.onNodeWithText(ApplicationProvider.getApplicationContext<Context>().getString(jp.linkserver.nittcsc.R.string.sktjf_cannot_apply)).assertExists()
        assertNull(previewed)
    }

    @Test fun timedDistributionAppliesToEmptyTermAndSwitchesAtNinthOpportunity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val start = LocalDate.of(2026, 4, 6); val end = start.plusDays(62)
            val profile = NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)
            val source = SktjfReference("src", 1, null)
            val before = SktjfLesson("before", 1, 1, SktjfCycle.WEEKLY, "製図", emptyList(), emptyList(),
                SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null, listOf(source))
            val after = before.copy(id = "after", subject = "卒研")
            val before2 = before.copy(id = "before2", period = 2)
            val after2 = after.copy(id = "after2", period = 2)
            val days = (0L..62L).map { start.plusDays(it) }.filter { it.dayOfWeek.value < 6 }
                .map { SktjfCalendarDay(it, SktjfDayType.A, null, null, null, listOf(source)) }
            val doc = SktjfDocument(2026, "S", start, end, "1-1", "2026.S.0",
                listOf(SktjfSource("src", "test.pdf", "a".repeat(64), null)),
                listOf(SktjfPeriod(1, LocalTime.of(8, 40), LocalTime.of(10, 10)),
                    SktjfPeriod(2, LocalTime.of(10, 20), LocalTime.of(11, 50))),
                listOf(SktjfSlot(1, 1, SktjfSlotState.PRESENT, false, listOf(source)),
                    SktjfSlot(1, 2, SktjfSlotState.PRESENT, false, listOf(source))), listOf(before, after, before2, after2), days)
            val group = SktjfV2WeekSeries("shared", 1, setOf(1, 2), setOf("before", "after", "before2", "after2"), SktjfCycle.WEEKLY,
                listOf(source), SktjfCountingPolicy(true, false, emptyMap(), true, sources = listOf(source)))
            val candidate = SktjfV2Candidate(doc, mapOf("before" to SktjfV2Range(LessonWeekRange(end = 8), "～8週"),
                "after" to SktjfV2Range(LessonWeekRange(start = 9), "9週～"),
                "before2" to SktjfV2Range(LessonWeekRange(end = 8), "～8週"),
                "after2" to SktjfV2Range(LessonWeekRange(start = 9), "9週～")), emptyMap(), listOf(group))
            val manifest = SktjfManifest(2026, "S", "2026.S.0", Instant.parse("2026-04-01T00:00:00Z"), emptyList())
            val repo = SchedulerRepository(db, UiDesignPreferences(context))
            runBlocking {
                val dao = db.schedulerDao()
                dao.upsertSettings(SettingsEntity(termStart = start, termEnd = end, enableSemesterTimetables = true))
                dao.upsertUsageMode(UsageModeEntity(mode = "NITTC")); dao.upsertNittcProfiles(listOf(profile))
            }
            var applied = false
            compose.setContent { MaterialTheme {
                SktjfImportDialog(UsageModeEntity(mode = "NITTC"), listOf(profile), 2026,
                    { _, _ -> SktjfDiscoveryResult.Found("2026.S.0") },
                    { _, _ -> SktjfFetchResult.Success(manifest, doc, candidate) },
                    repo::previewSktjf, repo::applySktjf, { applied = true }, {}, previewTimed = repo::previewTimedSktjf)
            } }
            compose.onNodeWithText("配信を確認").performClick()
            compose.waitUntil(15_000) { applied }
            compose.onNodeWithText("受講する授業を選択").assertDoesNotExist()
            compose.onNodeWithText("時間割の変更を確認").assertDoesNotExist()
            runBlocking {
                val schedule = requireNotNull(EffectiveSchoolSchedule.load(db.schedulerDao()))
                assertEquals(listOf("製図", "製図"), schedule.lessonsOn(start.plusWeeks(7)).map { it.content.subject })
                assertEquals(listOf("卒研", "卒研"), schedule.lessonsOn(start.plusWeeks(8)).map { it.content.subject })
            }
        } finally { db.close() }
    }

    @Test fun ungroupedConflictingCoursesAreNotAssumedToBeElectives() {
        val date = LocalDate.of(2026, 4, 6)
        val conditions = SktjfConditions(emptySet(), emptySet())
        val first = SktjfLesson("a1", 1, 1, SktjfCycle.A, "授業1", emptyList(), emptyList(), conditions, SktjfLessonKind.NORMAL, null, emptyList())
        val second = first.copy(id = "a2", subject = "授業2")
        val otherCycle = first.copy(id = "b", subject = "B授業", cycle = SktjfCycle.B)
        val doc = SktjfDocument(2026, "S", date, date, "1-1", "2026.S.0", emptyList(), emptyList(), emptyList(),
            listOf(first, second, otherCycle), emptyList())
        val manifest = SktjfManifest(2026, "S", "2026.S.0", Instant.parse("2026-04-01T00:00:00Z"), emptyList())
        var previewed: SktjfDocument? = null
        compose.setContent { MaterialTheme {
            SktjfImportDialog(UsageModeEntity(mode = "NITTC"),
                listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)), 2026,
                { _, _ -> SktjfDiscoveryResult.Found("2026.S.0") },
                { _, _ -> SktjfFetchResult.Success(manifest, doc) },
                { previewed = it; SktjfPreviewResult.Blocked }, { _, _, _ -> SktjfApplyResult.Blocked }, {}, {})
        } }
        compose.onNodeWithText("配信を確認").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("受講する授業を選択").assertDoesNotExist()
        compose.onNodeWithText(ApplicationProvider.getApplicationContext<Context>().getString(jp.linkserver.nittcsc.R.string.sktjf_cannot_apply)).assertExists()
        assertNull(previewed)
    }

    @Test fun cancellingRejectedUngroupedCoursesDoesNotPreviewOrApply() {
        val date = LocalDate.of(2026, 4, 6)
        val first = SktjfLesson("a", 1, 1, SktjfCycle.WEEKLY, "授業1", emptyList(), emptyList(),
            SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null, emptyList())
        val doc = SktjfDocument(2026, "S", date, date, "1-1", "2026.S.0", emptyList(), emptyList(), emptyList(),
            listOf(first, first.copy(id = "b", subject = "授業2")), emptyList())
        val manifest = SktjfManifest(2026, "S", "2026.S.0", Instant.parse("2026-04-01T00:00:00Z"), emptyList())
        var previews = 0; var applies = 0
        compose.setContent { MaterialTheme {
            SktjfImportDialog(UsageModeEntity(mode = "NITTC"),
                listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)), 2026,
                { _, _ -> SktjfDiscoveryResult.Found("2026.S.0") }, { _, _ -> SktjfFetchResult.Success(manifest, doc) },
                { previews++; SktjfPreviewResult.Blocked }, { _, _, _ -> applies++; SktjfApplyResult.Blocked }, {}, {})
        } }
        compose.onNodeWithText("配信を確認").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("受講する授業を選択").assertDoesNotExist()
        compose.onNodeWithText(ApplicationProvider.getApplicationContext<Context>().getString(jp.linkserver.nittcsc.R.string.sktjf_cannot_apply)).assertExists()
        compose.onNodeWithText("キャンセル").performClick()
        compose.onNodeWithText("配信を確認").assertExists()
        assertEquals(0, previews); assertEquals(0, applies)
    }

    @Test fun weekBasedSwitchIsNotPresentedAsCourseSelection() {
        val date = LocalDate.of(2026, 4, 6)
        val first = SktjfLesson("a", 1, 1, SktjfCycle.WEEKLY, "製図", emptyList(), emptyList(),
            SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null, emptyList(),
            SktjfV2Range(LessonWeekRange(end = 8), "8週まで"))
        val second = first.copy(id = "b", subject = "卒研", weekRange = SktjfV2Range(LessonWeekRange(start = 9), "9週から"))
        val doc = SktjfDocument(2026, "S", date, date, "1-1", "2026.S.0", emptyList(), emptyList(), emptyList(), listOf(first, second), emptyList())
        val manifest = SktjfManifest(2026, "S", "2026.S.0", Instant.parse("2026-04-01T00:00:00Z"), emptyList())
        var previews = 0
        compose.setContent { MaterialTheme {
            SktjfImportDialog(UsageModeEntity(mode = "NITTC"),
                listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)), 2026,
                { _, _ -> SktjfDiscoveryResult.Found("2026.S.0") }, { _, _ -> SktjfFetchResult.Success(manifest, doc) },
                { previews++; SktjfPreviewResult.Blocked }, { _, _, _ -> SktjfApplyResult.Blocked }, {}, {})
        } }
        compose.onNodeWithText("配信を確認").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("受講する授業を選択").assertDoesNotExist()
        assertEquals(0, previews)
    }

    @Test fun automaticUpdateCanBeExcludedFromTheGrid() {
        val date = LocalDate.of(2026, 4, 6)
        val doc = SktjfDocument(2026, "S", date, date, "1-1", "2026.S.1",
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val old = SktjfLessonContent(1, 1, SktjfCycle.WEEKLY, "古い数学", emptyList(), emptyList(),
            SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null)
        val review = SktjfLessonReview("source", 1, 1, SktjfCycle.WEEKLY, old, old.copy(subject = "新しい数学"), SktjfLessonReviewStatus.AUTOMATIC)
        val snapshot = SktjfApplySnapshot(null, null, emptyList(), listOf(existingRow()), emptyList(), emptyList(), emptyList())
        val prepared = SktjfApplyPreview(doc, snapshot, emptyList(), emptyList(), lessonReview = listOf(review))
        var choices: Map<String, SktjfConflictChoice>? = null
        compose.setContent { MaterialTheme {
            SktjfImportDialog(UsageModeEntity(mode = "NITTC"),
                listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)), 2026,
                { _, _ -> SktjfDiscoveryResult.NotPublished }, { _, _ -> SktjfFetchResult.ReleaseNotFound },
                { SktjfPreviewResult.Blocked },
                { _, lessons, _ -> choices = lessons; SktjfApplyResult.Applied }, {}, {}, initialPreview = prepared)
        } }
        compose.onNodeWithTag("sktjf-merge-1-1").performClick()
        compose.onNodeWithText("現在の内容を残す").performClick()
        compose.onNodeWithText("閉じる").performClick()
        compose.onNodeWithText("選択した内容を適用").performClick()
        compose.waitForIdle()
        assertEquals(mapOf("source" to SktjfConflictChoice.KEEP_LOCAL), choices)
    }

    @Test fun bulkChoiceCanBeChangedForBothWeeksOfOneSlot() {
        val date = LocalDate.of(2026, 4, 6)
        val doc = SktjfDocument(2026, "S", date, date, "1-1", "2026.S.0", emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val a = SktjfLessonContent(1, 1, SktjfCycle.A, "英語I", emptyList(), emptyList(), SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null)
        val b = a.copy(cycle = SktjfCycle.B, subject = "数学I")
        val conflicts = listOf(
            SktjfLessonConflict("a", SktjfConflictReason.BOTH_CHANGED, a, SktjfLocalLesson("a", "a", a), a.copy(subject = "英語II")),
            SktjfLessonConflict("b", SktjfConflictReason.BOTH_CHANGED, b, SktjfLocalLesson("b", "b", b), b.copy(subject = "数学II"))
        )
        val review = conflicts.map { SktjfLessonReview(it.sourceLessonId, 1, 1, it.incoming.cycle, it.current?.content, it.incoming, SktjfLessonReviewStatus.CONFLICT) }
        val snapshot = SktjfApplySnapshot(null, null, emptyList(), listOf(existingRow()), emptyList(), emptyList(), emptyList())
        val prepared = SktjfApplyPreview(doc, snapshot, conflicts, emptyList(), lessonReview = review)
        var choices: Map<String, SktjfConflictChoice>? = null
        compose.setContent { MaterialTheme {
            SktjfImportDialog(UsageModeEntity(mode = "NITTC"),
                listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)), 2026,
                { _, _ -> SktjfDiscoveryResult.NotPublished }, { _, _ -> SktjfFetchResult.ReleaseNotFound },
                { SktjfPreviewResult.Blocked }, { _, selected, _ -> choices = selected; SktjfApplyResult.Applied }, {}, {},
                initialPreview = prepared, automaticUpdate = true)
        } }
        compose.onNodeWithText("新しい内容をまとめて選ぶ").performClick()
        compose.onNodeWithTag("sktjf-merge-1-1").performClick()
        compose.onNodeWithText("現在の内容を残す").performClick()
        compose.onNodeWithText("閉じる").performClick()
        compose.onNodeWithText("選択した内容を適用").performClick()
        compose.waitForIdle()
        assertEquals(mapOf("a" to SktjfConflictChoice.KEEP_LOCAL, "b" to SktjfConflictChoice.KEEP_LOCAL), choices)
    }

    @Test fun manualReapplicationDefaultsToRestoringDeletedLesson() {
        val date = LocalDate.of(2026, 4, 6)
        val doc = SktjfDocument(2026, "S", date, date, "1-1", "2026.S.0", emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val incoming = SktjfLessonContent(1, 1, SktjfCycle.WEEKLY, "英語II", emptyList(), emptyList(),
            SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null)
        val conflict = SktjfLessonConflict("source", SktjfConflictReason.LOCAL_CHANGED, incoming, null, incoming)
        val source = SktjfSourceStateEntity(2026, "S", "1-1", "2026.S.0", "{}", false, "[]")
        val snapshot = SktjfApplySnapshot(null, null, emptyList(), emptyList(), emptyList(), listOf(source), emptyList())
        val review = SktjfLessonReview("source", 1, 1, SktjfCycle.WEEKLY, null, incoming, SktjfLessonReviewStatus.PROTECTED)
        val prepared = SktjfApplyPreview(doc, snapshot, listOf(conflict), emptyList(), lessonReview = listOf(review))
        var choices: Map<String, SktjfConflictChoice>? = null
        compose.setContent { MaterialTheme {
            SktjfImportDialog(UsageModeEntity(mode = "NITTC"),
                listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)), 2026,
                { _, _ -> SktjfDiscoveryResult.NotPublished }, { _, _ -> SktjfFetchResult.ReleaseNotFound },
                { SktjfPreviewResult.Blocked }, { _, selected, _ -> choices = selected; SktjfApplyResult.Applied }, {}, {},
                initialPreview = prepared)
        } }
        compose.onNodeWithText("配信を採用").assertExists()
        compose.onNodeWithText("選択した内容を適用").performClick()
        compose.runOnIdle { assertEquals(mapOf("source" to SktjfConflictChoice.APPLY_INCOMING), choices) }
    }

    @Test fun gridShowsTheWholeResultWhenAlternatingChangesToWeekly() {
        val date = LocalDate.of(2026, 4, 6)
        val doc = SktjfDocument(2026, "S", date, date, "1-1", "2026.S.1", emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val a = SktjfLessonContent(1, 1, SktjfCycle.A, "A授業", emptyList(), emptyList(), SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null)
        val b = a.copy(cycle = SktjfCycle.B, subject = "手動B")
        val weekly = a.copy(cycle = SktjfCycle.WEEKLY, subject = "毎週授業")
        val conflict = SktjfLessonConflict("old-b", SktjfConflictReason.BOTH_CHANGED, b.copy(subject = "B授業"),
            SktjfLocalLesson("base:1:B", "old-b", b), b, isDeletion = true)
        val review = listOf(
            SktjfLessonReview("new-weekly", 1, 1, SktjfCycle.WEEKLY, null, weekly, SktjfLessonReviewStatus.AUTOMATIC),
            SktjfLessonReview("old-a", 1, 1, SktjfCycle.A, a, null, SktjfLessonReviewStatus.AUTOMATIC),
            SktjfLessonReview("old-b", 1, 1, SktjfCycle.B, b, null, SktjfLessonReviewStatus.CONFLICT))
        val snapshot = SktjfApplySnapshot(null, null, emptyList(), listOf(existingRow()), emptyList(), emptyList(), emptyList())
        val prepared = SktjfApplyPreview(doc, snapshot, listOf(conflict), emptyList(), lessonReview = review)
        compose.setContent { MaterialTheme {
            SktjfImportDialog(UsageModeEntity(mode = "NITTC"),
                listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)), 2026,
                { _, _ -> SktjfDiscoveryResult.NotPublished }, { _, _ -> SktjfFetchResult.ReleaseNotFound },
                { SktjfPreviewResult.Blocked }, { _, _, _ -> SktjfApplyResult.Blocked }, {}, {},
                initialPreview = prepared, automaticUpdate = true)
        } }
        compose.onNodeWithText("A A授業\nB 手動B").assertExists()
        compose.onNodeWithTag("sktjf-merge-1-1").performClick()
        compose.onNodeWithText("新しい内容を適用").performClick()
        compose.onNodeWithText("閉じる").performClick()
        compose.onNodeWithText("毎週授業").assertExists()
        compose.onNodeWithText("A A授業\nB 手動B").assertDoesNotExist()
    }

    @Test fun applyIncomingForAllConflictsEnablesApply() {
        val date = LocalDate.of(2026, 4, 6)
        val doc = SktjfDocument(2026, "S", date, date, "1-1", "2026.S.0",
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val incoming = SktjfLessonContent(1, 1, SktjfCycle.WEEKLY, "数学", emptyList(), emptyList(),
            SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null)
        val conflict = SktjfLessonConflict("source", SktjfConflictReason.BOTH_CHANGED, null,
            SktjfLocalLesson("local", "source", incoming.copy(subject = "物理")), incoming)
        val snapshot = SktjfApplySnapshot(null, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val prepared = SktjfApplyPreview(doc, snapshot, listOf(conflict), emptyList())
        var choices: Map<String, SktjfConflictChoice>? = null
        compose.setContent { MaterialTheme {
            SktjfImportDialog(UsageModeEntity(mode = "NITTC"),
                listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)), 2026,
                { _, _ -> SktjfDiscoveryResult.NotPublished }, { _, _ -> SktjfFetchResult.ReleaseNotFound },
                { SktjfPreviewResult.Blocked },
                { _, lessons, _ -> choices = lessons; SktjfApplyResult.Applied }, {}, {}, initialPreview = prepared, automaticUpdate = true)
        } }
        compose.onNodeWithText("選択した内容を適用").assertIsEnabled()
        compose.onNodeWithText("現在を維持").assertExists()
        compose.onNodeWithText("新しい内容をまとめて選ぶ").performClick()
        compose.onNodeWithText("配信を採用").assertExists()
        compose.onNodeWithText("数学").assertExists()
        compose.onNodeWithText("選択した内容を適用").performClick()
        compose.waitForIdle()
        assertEquals(mapOf("source" to SktjfConflictChoice.APPLY_INCOMING), choices)
    }

    @Test fun conflictsRequireExplicitChoiceAndCanBeSelectedTogether() {
        val date = LocalDate.of(2026, 4, 6)
        val doc = SktjfDocument(2026, "S", date, date, "1-1", "2026.S.0",
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val incoming = SktjfLessonContent(1, 1, SktjfCycle.WEEKLY, "数学", emptyList(), emptyList(),
            SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null)
        val local = SktjfLocalLesson("local", "source", incoming.copy(subject = "物理"))
        val conflict = SktjfLessonConflict("source", SktjfConflictReason.BOTH_CHANGED, null, local, incoming)
        val snapshot = SktjfApplySnapshot(null, null, emptyList(), listOf(existingRow()), emptyList(), emptyList(), emptyList())
        val prepared = SktjfApplyPreview(doc, snapshot, listOf(conflict), emptyList())
        val manifest = SktjfManifest(2026, "S", "2026.S.0", Instant.parse("2026-04-01T00:00:00Z"), emptyList())
        var choices: Map<String, SktjfConflictChoice>? = null
        var applied = false
        compose.setContent { MaterialTheme {
            SktjfImportDialog(UsageModeEntity(mode = "NITTC"),
                listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)), 2026,
                { _, _ -> SktjfDiscoveryResult.Found("2026.S.0") },
                { _, _ -> SktjfFetchResult.Success(manifest, doc) }, { SktjfPreviewResult.Ready(prepared) },
                { _, lessons, _ -> choices = lessons; SktjfApplyResult.Applied }, { applied = true }, {}, automaticUpdate = true)
        } }
        compose.onNodeWithText("配信を確認").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("選択した内容を適用").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(mapOf("source" to SktjfConflictChoice.KEEP_LOCAL), choices)
        assertTrue(applied)
    }

    @Test fun staleNextYearPreviewKeepsItsYearForRechecking() {
        val date = LocalDate.of(2027, 10, 4)
        val doc = SktjfDocument(2027, "F", date, date, "2M", "2027.F.0",
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val snapshot = SktjfApplySnapshot(null, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val prepared = SktjfApplyPreview(doc, snapshot, emptyList(), emptyList())
        compose.setContent { MaterialTheme {
            SktjfImportDialog(UsageModeEntity(mode = "NITTC"),
                listOf(NittcProfileEntity(2027, "UNDERGRADUATE", 2, "2M", false, false)), 2026,
                { _, _ -> SktjfDiscoveryResult.NotPublished }, { _, _ -> SktjfFetchResult.ReleaseNotFound },
                { SktjfPreviewResult.Blocked }, { _, _, _ -> SktjfApplyResult.Stale }, {}, {}, initialPreview = prepared)
        } }
        compose.onNodeWithText("選択した内容を適用").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("2027").assertExists()
        compose.onNodeWithText("時間割が変更されました。もう一度確認してください").assertExists()
    }
}
