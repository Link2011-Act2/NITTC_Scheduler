package jp.linkserver.nittcsc.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.uiDesignDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ui_design_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class UiDesignPreferences(
    private val dataStore: DataStore<Preferences>
) {
    constructor(context: Context) : this(context.applicationContext.uiDesignDataStore)

    private val safePreferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is CancellationException) throw error
        emit(emptyPreferences())
    }

    val uiDesignMode: Flow<UiDesignMode> = safePreferences.map { preferences ->
        UiDesignMode.fromStoredValue(preferences[Keys.UI_DESIGN_MODE])
    }

    val expressiveWarningAcknowledged: Flow<Boolean> = safePreferences.map { preferences ->
        preferences[Keys.EXPRESSIVE_WARNING_ACKNOWLEDGED] ?: false
    }

    suspend fun setUiDesignMode(mode: UiDesignMode) {
        dataStore.edit { preferences ->
            preferences[Keys.UI_DESIGN_MODE] = mode.name
        }
    }

    suspend fun acknowledgeExpressiveWarning() {
        dataStore.edit { preferences ->
            preferences[Keys.EXPRESSIVE_WARNING_ACKNOWLEDGED] = true
        }
    }

    private object Keys {
        val UI_DESIGN_MODE = stringPreferencesKey("ui_design_mode")
        val EXPRESSIVE_WARNING_ACKNOWLEDGED =
            booleanPreferencesKey("material_3_expressive_warning_acknowledged")
    }
}
