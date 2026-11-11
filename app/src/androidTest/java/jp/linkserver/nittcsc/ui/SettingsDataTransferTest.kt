package jp.linkserver.nittcsc.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.SettingsEntity
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.theme.AppTheme
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class SettingsDataTransferTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bothDesignsShareJsonAndConfirmSelectedJsonBeforeImport() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val json = "{\"version\":1,\"tasks\":[]}"
        val fixture = File(context.cacheDir, "settings-transfer-test.json").apply { writeText(json) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", fixture)
        var shared: Intent? = null
        var selectedTypes: Array<String>? = null
        var imported: String? = null
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? = when (intent.action) {
                Intent.ACTION_CHOOSER -> {
                    @Suppress("DEPRECATION")
                    shared = intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                }
                Intent.ACTION_OPEN_DOCUMENT -> {
                    selectedTypes = intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)
                    Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri))
                }
                else -> null
            }
        }
        var mode by mutableStateOf(UiDesignMode.MATERIAL_3)
        compose.setContent {
            AppTheme(mode, dynamicColor = false) {
                SettingsScreen(
                    state = jp.linkserver.nittcsc.viewmodel.SchedulerUiState(
                        settings = SettingsEntity(termStart = LocalDate.now(), termEnd = LocalDate.now().plusMonths(1))
                    ),
                    onBack = {}, onAbout = {}, onToggleLocalAi = {}, onToggleDrawerNavigation = {},
                    onToggleAddTasksToCalendar = {}, onToggleCurrentTimeMarker = {},
                    onToggleUnifyTaskPlanView = {}, onToggleShowWeekdayOnDates = {},
                    onToggleAdvancedTimeSettingsUi = {}, onExportAllAsJson = { json },
                    onImportAllFromJson = { imported = it }
                )
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            for (design in UiDesignMode.entries) {
                compose.runOnIdle { mode = design; shared = null; imported = null }
                compose.onNodeWithText(context.getString(R.string.btn_export_json)).performScrollTo().performClick()
                compose.waitUntil(10_000) { shared != null }
                val intent = checkNotNull(shared)
                assertEquals(Intent.ACTION_SEND, intent.action)
                assertEquals("application/json", intent.type)
                assertTrue(intent.getStringExtra(Intent.EXTRA_SUBJECT)!!.matches(Regex("nittcsc_settings_\\d{8}_\\d{6}\\.json")))
                @Suppress("DEPRECATION")
                val exported = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)!!
                assertEquals(json, context.contentResolver.openInputStream(exported)!!.bufferedReader().use { it.readText() })
                assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                compose.onNodeWithText(context.getString(R.string.btn_import_json)).performClick()
                compose.onNodeWithText(context.getString(R.string.dialog_import_confirm_title)).assertIsDisplayed()
                assertNull(imported)
                assertArrayEquals(arrayOf("application/json", "text/plain"), selectedTypes)
                compose.onNodeWithText(context.getString(R.string.dialog_import_confirm_action)).performClick()
                compose.waitUntil(10_000) { imported != null }
                assertEquals(json, imported)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            fixture.delete()
        }
    }
}
