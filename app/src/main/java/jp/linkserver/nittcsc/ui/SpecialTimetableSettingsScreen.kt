package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.SettingsEntity
import jp.linkserver.nittcsc.ui.components.AppCard
import jp.linkserver.nittcsc.ui.components.AppFlexibleTopAppBar
import jp.linkserver.nittcsc.ui.components.AppIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpecialTimetableSettingsScreen(
    settings: SettingsEntity?,
    onBack: () -> Unit,
    onToggleSemesterTimetables: (Boolean) -> Unit,
    onToggleAbTimetable: (Boolean) -> Unit,
    onToggleExamTimetable: (Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            AppFlexibleTopAppBar(
                title = { Text(stringResource(R.string.special_timetable_settings_title)) },
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        AdaptiveContentPane(modifier = Modifier.padding(padding), maxWidth = ListContentMaxWidth) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppCard(modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Column {
                        SettingsSwitchRow(
                            title = stringResource(R.string.label_semester_timetables),
                            description = stringResource(R.string.desc_semester_timetables),
                            checked = settings?.enableSemesterTimetables != false,
                            onCheckedChange = onToggleSemesterTimetables
                        )
                        HorizontalDivider()
                        SettingsSwitchRow(
                            title = stringResource(R.string.label_enable_ab_timetable),
                            description = stringResource(R.string.desc_enable_ab_timetable),
                            checked = settings?.enableAbTimetable != false,
                            onCheckedChange = onToggleAbTimetable
                        )
                        HorizontalDivider()
                        SettingsSwitchRow(
                            title = stringResource(R.string.label_enable_exam_timetable),
                            description = stringResource(R.string.desc_enable_exam_timetable),
                            checked = settings?.enableExamTimetable != false,
                            onCheckedChange = onToggleExamTimetable
                        )
                    }
                }
                Text(stringResource(R.string.msg_settings_auto_save),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
