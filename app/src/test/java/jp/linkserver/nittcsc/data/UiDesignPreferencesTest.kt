package jp.linkserver.nittcsc.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UiDesignPreferencesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun tearDown() {
        dataStoreScope.cancel()
    }

    @Test
    fun initialModeIsMaterial3() = runBlocking {
        val preferences = createPreferences()

        assertEquals(UiDesignMode.MATERIAL_3, preferences.uiDesignMode.first())
        assertFalse(preferences.expressiveWarningAcknowledged.first())
    }

    @Test
    fun expressiveSelectionPersistsAndCanReturnToMaterial3() = runBlocking {
        val preferences = createPreferences()

        preferences.setUiDesignMode(UiDesignMode.MATERIAL_3_EXPRESSIVE)
        assertEquals(UiDesignMode.MATERIAL_3_EXPRESSIVE, preferences.uiDesignMode.first())

        val restoredPreferences = UiDesignPreferences(preferencesDataStore)
        assertEquals(
            UiDesignMode.MATERIAL_3_EXPRESSIVE,
            restoredPreferences.uiDesignMode.first()
        )

        restoredPreferences.setUiDesignMode(UiDesignMode.MATERIAL_3)
        assertEquals(UiDesignMode.MATERIAL_3, preferences.uiDesignMode.first())
    }

    @Test
    fun expressiveWarningAcknowledgementPersists() = runBlocking {
        val preferences = createPreferences()

        preferences.acknowledgeExpressiveWarning()

        assertTrue(UiDesignPreferences(preferencesDataStore).expressiveWarningAcknowledged.first())
    }

    private lateinit var preferencesDataStore: androidx.datastore.core.DataStore<
        androidx.datastore.preferences.core.Preferences
    >

    private fun createPreferences(): UiDesignPreferences {
        preferencesDataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { temporaryFolder.newFile("ui_design.preferences_pb") }
        )
        return UiDesignPreferences(preferencesDataStore)
    }
}
