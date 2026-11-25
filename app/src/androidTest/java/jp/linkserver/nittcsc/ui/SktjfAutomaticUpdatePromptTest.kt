package jp.linkserver.nittcsc.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.logic.academicYearForDate
import jp.linkserver.nittcsc.logic.TimetableTerm
import jp.linkserver.nittcsc.sktjf.*
import jp.linkserver.nittcsc.viewmodel.SchedulerViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class SktjfAutomaticUpdatePromptTest {
    @get:Rule val compose = createComposeRule()

    private fun withFixture(test: (Fixture) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val store = ViewModelStore()
        val prefsName = "sktjf-notice-test-${UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        lateinit var model: SchedulerViewModel
        compose.runOnIdle { model = SchedulerViewModel(SchedulerRepository(db, UiDesignPreferences(context))); store.put("model", model) }
        try { test(Fixture(context, model, prefs)) }
        finally { compose.runOnIdle { store.clear() }; db.close(); context.deleteSharedPreferences(prefsName) }
    }

    private data class Fixture(val context: Context, val model: SchedulerViewModel, val prefs: android.content.SharedPreferences)

    @Test fun unhandledRevisionReappearsAfterRecreationAndCancelAcknowledgesIt() = withFixture { fixture ->
        val today = LocalDate.now()
        val year = academicYearForDate(today)
        val profile = NittcProfileEntity(year, "UNDERGRADUATE", 1, "1-1", false, false)
        val key = SktjfUpdateKey(year, "S", "1-1")
        val revision = "$year.S.0"
        val document = SktjfDocument(year, "S", today, today, "1-1", revision, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val row = LessonEntity(id = 1, academicYear = year, timetableTerm = TimetableTerm.FIRST, dayOfWeek = 1,
            slotIndex = 0, mode = LessonMode.WEEKLY, weeklySubject = "数学", weeklyTeacher = "",
            aSubject = "", aTeacher = "", bSubject = "", bTeacher = "")
        val preview = SktjfApplyPreview(document, SktjfApplySnapshot(null, null, emptyList(), listOf(row),
            emptyList(), emptyList(), emptyList()), emptyList(), emptyList())
        val visible = mutableStateOf(true)
        val settings = mutableStateOf<SettingsEntity?>(null)
        val check: suspend (Int, String, (SktjfUpdateKey) -> String?) -> SktjfAutomaticCheckResult = { _, _, acknowledged ->
            if (acknowledged(key) == revision) SktjfAutomaticCheckResult.NoUpdate
            else SktjfAutomaticCheckResult.Available(SktjfAutomaticOffer(key, SktjfUpdateKind.INITIAL, document))
        }
        compose.setContent {
            MaterialTheme {
                if (visible.value) SktjfAutomaticUpdatePrompt(fixture.model, UsageModeEntity(mode = "NITTC"), listOf(profile), emptyList(),
                    settings.value, 0, {}, fixture.prefs, check, { SktjfPreviewResult.Ready(preview) })
            }
        }
        compose.runOnIdle { settings.value = SettingsEntity(termStart = today, termEnd = today) }
        val title = fixture.context.getString(R.string.sktjf_notice_title)
        compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        assertNull(fixture.prefs.getString("notice:$year:S:1-1", null))
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle { visible.value = true }
        compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(fixture.context.getString(R.string.nittc_cancel)).performClick()
        compose.waitForIdle()
        assertEquals(revision, fixture.prefs.getString("notice:$year:S:1-1", null))
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle { visible.value = true }
        compose.waitForIdle()
        compose.onNodeWithText(title).assertDoesNotExist()
    }

    @Test fun emptySemesterIsAppliedWithoutOffer() = withFixture { fixture ->
        val today = LocalDate.now(); val year = academicYearForDate(today)
        val profile = NittcProfileEntity(year, "UNDERGRADUATE", 1, "1-1", false, false)
        val key = SktjfUpdateKey(year, "S", "1-1")
        val document = SktjfDocument(year, "S", today, today, "1-1", "$year.S.0", emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val preview = SktjfApplyPreview(document, SktjfApplySnapshot(null, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList()), emptyList(), emptyList())
        val applied = mutableStateOf(false)
        compose.setContent { MaterialTheme {
            SktjfAutomaticUpdatePrompt(fixture.model, UsageModeEntity(mode = "NITTC"), listOf(profile), emptyList(),
                SettingsEntity(termStart = today, termEnd = today), 0, {}, fixture.prefs,
                { _, _, _ -> SktjfAutomaticCheckResult.Available(SktjfAutomaticOffer(key, SktjfUpdateKind.INITIAL, document)) },
                { SktjfPreviewResult.Ready(preview) }, { _, _, _ -> applied.value = true; SktjfApplyResult.Applied })
        } }
        compose.waitUntil(10_000) { applied.value }
        compose.onNodeWithText(fixture.context.getString(R.string.sktjf_notice_title)).assertDoesNotExist()
    }

    @Test fun timedEmptySemesterUsesTimedApplyWithoutOffer() = withFixture { fixture ->
        val today = LocalDate.now(); val year = academicYearForDate(today)
        val profile = NittcProfileEntity(year, "UNDERGRADUATE", 1, "1-1", false, false)
        val key = SktjfUpdateKey(year, "S", "1-1")
        val doc = SktjfDocument(year, "S", today, today, "1-1", "$year.S.0", emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val candidate = SktjfV2Candidate(doc, emptyMap(), emptyMap())
        val preparation = prepareSktjfV2Merge(candidate, emptySet(), emptyList(), emptyList(), null) as SktjfV2MergePreparation.Ready
        val preview = SktjfV2ApplyPreview(SktjfApplySnapshot(null, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList()), preparation)
        val applied = mutableStateOf(false)
        compose.setContent { MaterialTheme {
            SktjfAutomaticUpdatePrompt(fixture.model, UsageModeEntity(mode = "NITTC"), listOf(profile), emptyList(),
                SettingsEntity(termStart = today, termEnd = today), 0, {}, fixture.prefs,
                { _, _, _ -> SktjfAutomaticCheckResult.Available(SktjfAutomaticOffer(key, SktjfUpdateKind.INITIAL, doc, candidate)) },
                { error("timed candidate reached the ordinary preview") },
                { value, _, _ -> assertNotNull(value.timed); applied.value = true; SktjfApplyResult.Applied },
                { preview })
        } }
        compose.waitUntil(10_000) { applied.value }
        compose.onNodeWithText(fixture.context.getString(R.string.sktjf_notice_title)).assertDoesNotExist()
    }

    @Test fun automaticOfferWithCourseAlternativesRequestsChoiceBeforeApplying() = withFixture { fixture ->
        val today = LocalDate.now(); val year = academicYearForDate(today)
        val profile = NittcProfileEntity(year, "UNDERGRADUATE", 1, "1-1", false, false)
        val key = SktjfUpdateKey(year, "S", "1-1")
        val first = SktjfLesson("first", 1, 1, SktjfCycle.WEEKLY, "授業1", emptyList(), emptyList(),
            SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null, emptyList())
        val document = SktjfDocument(year, "S", today, today, "1-1", "$year.S.0", emptyList(), emptyList(), emptyList(),
            listOf(first, first.copy(id = "second", subject = "授業2")), emptyList())
        var previewed: SktjfDocument? = null
        val applied = mutableStateOf(false)
        compose.setContent { MaterialTheme {
            SktjfAutomaticUpdatePrompt(fixture.model, UsageModeEntity(mode = "NITTC"), listOf(profile), emptyList(),
                SettingsEntity(termStart = today, termEnd = today), 0, {}, fixture.prefs,
                { _, _, _ -> SktjfAutomaticCheckResult.Available(SktjfAutomaticOffer(key, SktjfUpdateKind.INITIAL, document)) },
                { selected -> previewed = selected; SktjfPreviewResult.Ready(SktjfApplyPreview(selected,
                    SktjfApplySnapshot(null, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList()), emptyList(), emptyList())) },
                { _, _, _ -> applied.value = true; SktjfApplyResult.Applied })
        } }
        val notice = fixture.context.getString(R.string.sktjf_notice_title)
        compose.waitUntil(10_000) { compose.onAllNodesWithText(notice).fetchSemanticsNodes().isNotEmpty() }
        assertNull(previewed); assertFalse(applied.value)
        compose.onNodeWithText(fixture.context.getString(R.string.sktjf_notice_apply)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("受講する授業を選択").fetchSemanticsNodes().isNotEmpty() }
        assertNull(previewed); assertFalse(applied.value)
        compose.onNodeWithText("授業2").performClick()
        compose.onNodeWithText("授業を決定").performClick()
        compose.waitUntil(10_000) { applied.value }
        assertEquals(listOf("second"), previewed?.lessons?.map { it.id })
        assertEquals(document.revision, fixture.prefs.getString("notice:$year:S:1-1", null))
    }

    @Test fun cancellingReviewDoesNotAcknowledgeRevisionButApplyingDoes() = withFixture { fixture ->
        val today = LocalDate.now()
        val year = academicYearForDate(today)
        val profile = NittcProfileEntity(year, "UNDERGRADUATE", 1, "1-1", false, false)
        val key = SktjfUpdateKey(year, "S", "1-1")
        val revision = "$year.S.1"
        val document = SktjfDocument(year, "S", today, today, "1-1", revision, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val row = LessonEntity(id = 1, academicYear = year, timetableTerm = TimetableTerm.FIRST, dayOfWeek = 1,
            slotIndex = 0, mode = LessonMode.WEEKLY, weeklySubject = "数学", weeklyTeacher = "",
            aSubject = "", aTeacher = "", bSubject = "", bTeacher = "")
        val preview = SktjfApplyPreview(document, SktjfApplySnapshot(null, null, emptyList(), listOf(row),
            emptyList(), emptyList(), emptyList()), emptyList(), emptyList())
        val visible = mutableStateOf(true)
        val applied = mutableStateOf(false)
        val check: suspend (Int, String, (SktjfUpdateKey) -> String?) -> SktjfAutomaticCheckResult = { _, _, acknowledged ->
            if (acknowledged(key) == revision) SktjfAutomaticCheckResult.NoUpdate
            else SktjfAutomaticCheckResult.Available(SktjfAutomaticOffer(key, SktjfUpdateKind.REVISION, document))
        }
        compose.setContent { MaterialTheme {
            if (visible.value) SktjfAutomaticUpdatePrompt(fixture.model, UsageModeEntity(mode = "NITTC"), listOf(profile), emptyList(),
                SettingsEntity(termStart = today, termEnd = today), 0, {}, fixture.prefs, check,
                { SktjfPreviewResult.Ready(preview) }, { _, _, _ -> applied.value = true; SktjfApplyResult.Applied })
        } }
        val title = fixture.context.getString(R.string.sktjf_notice_title)
        val accept = fixture.context.getString(R.string.sktjf_notice_apply)
        val noticeKey = "notice:$year:S:1-1"
        compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(accept).performClick()
        compose.onNodeWithText("選択した内容を適用").assertExists()
        assertNull(fixture.prefs.getString(noticeKey, null))
        compose.onNodeWithText(fixture.context.getString(R.string.nittc_cancel)).performClick()
        assertFalse(applied.value)
        assertNull(fixture.prefs.getString(noticeKey, null))
        compose.runOnIdle { visible.value = false }
        fixture.prefs.edit().clear().commit()
        compose.runOnIdle { visible.value = true }
        compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(accept).performClick()
        compose.onNodeWithText("選択した内容を適用").performClick()
        compose.waitUntil(10_000) { applied.value }
        compose.waitForIdle()
        assertEquals(revision, fixture.prefs.getString(noticeKey, null))
    }

    @Test fun unhandledYearOfferReappearsWhileReminderIsPreserved() = withFixture { fixture ->
        val today = LocalDate.now()
        val year = academicYearForDate(today)
        val nextYear = year + 1
        val visible = mutableStateOf(true)
        val profile = NittcProfileEntity(year, "UNDERGRADUATE", 1, "1-1", false, false)
        val check: suspend (Int, String, (SktjfUpdateKey) -> String?) -> SktjfAutomaticCheckResult = { _, _, _ ->
            SktjfAutomaticCheckResult.NeedsAffiliation(nextYear, "$nextYear.S.0")
        }
        compose.setContent {
            MaterialTheme {
                if (visible.value) SktjfAutomaticUpdatePrompt(fixture.model, UsageModeEntity(mode = "NITTC"), listOf(profile), emptyList(),
                    SettingsEntity(termStart = today, termEnd = today), 0, {}, fixture.prefs, check)
            }
        }
        val title = fixture.context.getString(R.string.nittc_year_transition_title, nextYear)
        compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(fixture.prefs.getBoolean(affiliationReminderKey(nextYear), false))
        assertFalse(fixture.prefs.getBoolean("affiliation-prompted:$nextYear", false))
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle { visible.value = true }
        compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        assertFalse(fixture.prefs.getBoolean("affiliation-prompted:$nextYear", false))
    }
}
