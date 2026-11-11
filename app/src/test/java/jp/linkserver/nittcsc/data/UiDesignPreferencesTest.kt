package jp.linkserver.nittcsc.data

import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
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

    private var dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val preferencesFile: File
        get() = File(temporaryFolder.root, "ui_design.preferences_pb")

    @After
    fun tearDown() = runBlocking {
        dataStoreScope.coroutineContext.job.cancelAndJoin()
    }

    @Test
    fun initialModeIsMaterial3() = runBlocking {
        assertFalse(preferencesFile.exists())
        val preferences = createPreferences()

        assertEquals(UiDesignMode.MATERIAL_3, preferences.uiDesignMode.first())
        assertFalse(preferences.expressiveWarningAcknowledged.first())
        assertFalse(preferencesFile.exists())
    }

    @Test
    fun expressiveSelectionPersistsAndCanReturnToMaterial3() = runBlocking {
        val preferences = createPreferences()

        preferences.setUiDesignMode(UiDesignMode.MATERIAL_3_EXPRESSIVE)
        assertEquals(UiDesignMode.MATERIAL_3_EXPRESSIVE, preferences.uiDesignMode.first())
        assertTrue(preferencesFile.isFile)

        val restoredPreferences = reopenPreferences()
        assertEquals(
            UiDesignMode.MATERIAL_3_EXPRESSIVE,
            restoredPreferences.uiDesignMode.first()
        )

        restoredPreferences.setUiDesignMode(UiDesignMode.MATERIAL_3)
        assertEquals(UiDesignMode.MATERIAL_3, restoredPreferences.uiDesignMode.first())
        assertEquals(UiDesignMode.MATERIAL_3, reopenPreferences().uiDesignMode.first())
    }

    @Test
    fun expressiveWarningAcknowledgementPersists() = runBlocking {
        val preferences = createPreferences()

        preferences.acknowledgeExpressiveWarning()

        val restoredPreferences = reopenPreferences()
        assertTrue(restoredPreferences.expressiveWarningAcknowledged.first())
        assertEquals(UiDesignMode.MATERIAL_3, restoredPreferences.uiDesignMode.first())
    }

    private lateinit var preferencesDataStore: androidx.datastore.core.DataStore<
        androidx.datastore.preferences.core.Preferences
    >

    private fun createPreferences(): UiDesignPreferences {
        preferencesDataStore = PreferenceDataStoreFactory.create(
            // Bare JVM Android stubs report SDK 0; FileStorage then uses renameTo,
            // which cannot replace an existing file on Windows. Keep this test's
            // real disk IO on the JVM filesystem without changing production storage.
            storage = OkioStorage(
                fileSystem = FileSystem.SYSTEM,
                serializer = PreferencesSerializer,
                producePath = { preferencesFile.path.toPath() }
            ),
            scope = dataStoreScope,
        )
        return UiDesignPreferences(preferencesDataStore)
    }

    private suspend fun reopenPreferences(): UiDesignPreferences {
        dataStoreScope.coroutineContext.job.cancelAndJoin()
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        return createPreferences()
    }
}
