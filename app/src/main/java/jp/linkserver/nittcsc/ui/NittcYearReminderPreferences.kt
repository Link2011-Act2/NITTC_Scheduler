package jp.linkserver.nittcsc.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import jp.linkserver.nittcsc.logic.academicYearForDate
import java.time.LocalDate

internal const val SKTJF_UPDATE_NOTICE_PREFERENCES = "sktjf_update_notices"
internal fun affiliationReminderKey(year: Int) = "affiliation-required:$year"

@Composable
internal fun rememberUpcomingAffiliationAnnounced(today: LocalDate): Boolean {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(SKTJF_UPDATE_NOTICE_PREFERENCES, Context.MODE_PRIVATE) }
    val key = affiliationReminderKey(academicYearForDate(today) + 1)
    var announced by remember(prefs, key) { mutableStateOf(prefs.getBoolean(key, false)) }
    DisposableEffect(prefs, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == key) announced = prefs.getBoolean(key, false)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return announced
}
