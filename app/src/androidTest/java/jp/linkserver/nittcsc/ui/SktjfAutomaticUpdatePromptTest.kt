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
        val visible = mutableStateOf(true)
        val settings = mutableStateOf<SettingsEntity?>(null)
        val check: suspend (Int, String, (SktjfUpdateKey) -> String?) -> SktjfAutomaticCheckResult = { _, _, acknowledged ->
            if (acknowledged(key) == revision) SktjfAutomaticCheckResult.NoUpdate
            else SktjfAutomaticCheckResult.Available(SktjfAutomaticOffer(key, SktjfUpdateKind.INITIAL, document))
        }
        compose.setContent {
            MaterialTheme {
                if (visible.value) SktjfAutomaticUpdatePrompt(fixture.model, UsageModeEntity(mode = "NITTC"), listOf(profile), emptyList(),
                    settings.value, 0, {}, fixture.prefs, check)
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
