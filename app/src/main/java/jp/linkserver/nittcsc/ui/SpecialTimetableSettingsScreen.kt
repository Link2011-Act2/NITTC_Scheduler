package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.SettingsEntity
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.components.AppSettingsScaffold
import jp.linkserver.nittcsc.ui.components.PreferenceGroup
import jp.linkserver.nittcsc.ui.theme.LocalUiDesignMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpecialTimetableSettingsScreen(
    settings: SettingsEntity?,
    onBack: () -> Unit,
    onToggleSemesterTimetables: (Boolean) -> Unit,
    onToggleAbTimetable: (Boolean) -> Unit,
    onToggleExamTimetable: (Boolean) -> Unit
) {
    if (LocalUiDesignMode.current == UiDesignMode.MATERIAL_3) {
        LegacySpecialTimetableSettingsScreen(
            settings = settings,
            onBack = onBack,
            onToggleSemesterTimetables = onToggleSemesterTimetables,
            onToggleAbTimetable = onToggleAbTimetable,
            onToggleExamTimetable = onToggleExamTimetable
        )
        return
    }
    AppSettingsScaffold(
        title = stringResource(R.string.special_timetable_settings_title),
        onBack = onBack,
        scrollState = rememberScrollState()
    ) {
        PreferenceGroup {
            SettingsSwitchRow(
                title = stringResource(R.string.label_semester_timetables),
                description = stringResource(R.string.desc_semester_timetables),
                checked = settings?.enableSemesterTimetables != false,
                onCheckedChange = onToggleSemesterTimetables
            )
            SettingsSwitchRow(
                title = stringResource(R.string.label_enable_ab_timetable),
                description = stringResource(R.string.desc_enable_ab_timetable),
                checked = settings?.enableAbTimetable != false,
                onCheckedChange = onToggleAbTimetable
            )
            SettingsSwitchRow(
                title = stringResource(R.string.label_enable_exam_timetable),
                description = stringResource(R.string.desc_enable_exam_timetable),
                checked = settings?.enableExamTimetable != false,
                onCheckedChange = onToggleExamTimetable
            )
        }
        Text(stringResource(R.string.msg_settings_auto_save),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
