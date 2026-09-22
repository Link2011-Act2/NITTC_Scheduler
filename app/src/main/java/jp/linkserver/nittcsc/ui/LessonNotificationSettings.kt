package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.LessonNotificationExclusionEntity
import jp.linkserver.nittcsc.data.LessonStartNotificationChipMode
import jp.linkserver.nittcsc.ui.components.AppSettingsGroup
import jp.linkserver.nittcsc.ui.components.AppSettingsExpandableItem
import jp.linkserver.nittcsc.ui.components.NavigationPreferenceRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LessonStartNotificationSettingsContent(
    enabled: Boolean,
    notificationsEnabled: Boolean,
    promotedNotificationsEnabled: Boolean,
    liveUpdatesEnabled: Boolean,
    liveUpdatesSupported: Boolean,
    progressCountsDown: Boolean,
    liveUpdateEarlyMinutes: Int,
    chipMode: LessonStartNotificationChipMode,
    minutesBefore: String,
    exclusions: List<LessonNotificationExclusionEntity>,
    subjectSuggestions: List<String>,
    subjectTeacherCandidates: Map<String, List<String>>,
    onToggleEnabled: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenPromotedNotificationSettings: () -> Unit,
    onToggleLiveUpdates: (Boolean) -> Unit,
    onToggleProgressCountsDown: (Boolean) -> Unit,
    onUpdateLiveUpdateEarlyMinutes: (Int) -> Unit,
    onUpdateChipMode: (LessonStartNotificationChipMode) -> Unit,
    onMinutesBeforeChange: (String) -> Unit,
    onAddExclusion: (String, String?, Boolean) -> Unit,
    onDeleteExclusion: (LessonNotificationExclusionEntity) -> Unit
) {
    var showExclusionEditor by rememberSaveable { mutableStateOf(false) }
    var showLiveUpdateDisplayDetails by rememberSaveable { mutableStateOf(false) }

    AppSettingsGroup {
        item("enabled") {
            SettingsSwitchRow(
                stringResource(R.string.label_lesson_start_notification),
                stringResource(R.string.desc_lesson_start_notification), enabled,
                onCheckedChange = onToggleEnabled
            )
        }
        if (enabled) {
            item("minutes") {
                NumberSettingRow(stringResource(R.string.label_lesson_start_notification_minutes),
                    minutesBefore, stringResource(R.string.unit_minutes_before), onMinutesBeforeChange)
            }
            if (!notificationsEnabled) {
                item("notification-permission") {
                    NavigationPreferenceRow(stringResource(R.string.btn_open_notification_settings),
                        stringResource(R.string.warning_notifications_disabled), onOpenNotificationSettings)
                }
            }
            if (liveUpdatesEnabled && liveUpdatesSupported && !promotedNotificationsEnabled) {
                item("promoted-permission") {
                    NavigationPreferenceRow(stringResource(R.string.btn_open_live_updates_settings),
                        stringResource(R.string.warning_promoted_notifications_disabled), onOpenPromotedNotificationSettings)
                }
            }
            item("live-updates") {
                SettingsSwitchRow(
                    stringResource(R.string.label_lesson_start_live_updates),
                    stringResource(if (liveUpdatesSupported) R.string.desc_lesson_start_live_updates
                        else R.string.desc_lesson_start_live_updates_unavailable),
                    liveUpdatesEnabled, enabled = liveUpdatesSupported, onCheckedChange = onToggleLiveUpdates
                )
            }
            if (liveUpdatesEnabled && liveUpdatesSupported) {
                item("early-minutes") {
                    ListSettingRow(
                        title = stringResource(R.string.label_lesson_start_live_update_early_minutes),
                        value = liveUpdateEarlyMinutes,
                        options = listOf(0, 1, 2, 3, 5),
                        optionLabel = { minutes ->
                            if (minutes == 0) stringResource(R.string.lesson_start_live_update_early_none)
                            else stringResource(R.string.lesson_start_live_update_early_value, minutes)
                        },
                        summary = stringResource(R.string.desc_lesson_start_live_update_early_minutes),
                        onSelect = onUpdateLiveUpdateEarlyMinutes
                    )
                }
                item("display-details") {
                    AppSettingsExpandableItem(
                        stringResource(R.string.label_lesson_start_live_update_display_details),
                        stringResource(R.string.desc_lesson_start_live_update_display_details),
                        showLiveUpdateDisplayDetails,
                        { showLiveUpdateDisplayDetails = !showLiveUpdateDisplayDetails }
                    )
                }
                if (showLiveUpdateDisplayDetails) {
                    item("progress-direction") {
                        ListSettingRow(
                            title = stringResource(R.string.label_lesson_start_progress_direction),
                            value = progressCountsDown,
                            options = listOf(false, true),
                            optionLabel = { stringResource(if (it) R.string.label_lesson_start_progress_decreasing
                                else R.string.label_lesson_start_progress_increasing) },
                            optionSummary = { stringResource(if (it) R.string.desc_lesson_start_progress_decreasing
                                else R.string.desc_lesson_start_progress_increasing) },
                            onSelect = onToggleProgressCountsDown
                        )
                    }
                    item("chip-mode") {
                        ListSettingRow(
                            title = stringResource(R.string.label_lesson_start_chip_mode),
                            value = chipMode,
                            options = listOf(LessonStartNotificationChipMode.CHRONOMETER, LessonStartNotificationChipMode.MINUTE_TEXT),
                            optionLabel = { stringResource(if (it == LessonStartNotificationChipMode.CHRONOMETER)
                                R.string.label_lesson_start_chip_mode_chronometer else R.string.label_lesson_start_chip_mode_minute_text) },
                            optionSummary = { stringResource(if (it == LessonStartNotificationChipMode.CHRONOMETER)
                                R.string.desc_lesson_start_chip_mode_chronometer else R.string.desc_lesson_start_chip_mode_minute_text) },
                            onSelect = onUpdateChipMode
                        )
                    }
                }
            }
            item("add-exclusion") {
                NavigationPreferenceRow(
                    stringResource(R.string.btn_add_lesson_start_notification_exclusion),
                    stringResource(R.string.desc_lesson_start_notification_exclusions),
                    { showExclusionEditor = true }
                )
            }
            if (exclusions.isEmpty()) {
                item("no-exclusions") {
                    Text(stringResource(R.string.msg_no_lesson_start_notification_exclusions),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                item("exclusions-heading") {
                    Text(stringResource(R.string.label_lesson_start_notification_exclusions),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                exclusions.forEach { exclusion ->
                    item("exclusion-${exclusion.id}") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(exclusion.subject, style = MaterialTheme.typography.bodyLarge)
                                if (exclusion.matchTeacher && !exclusion.teacher.isNullOrBlank()) {
                                    Text(stringResource(R.string.label_lesson_start_notification_exclusion_teacher, exclusion.teacher),
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            TextButton(onClick = { onDeleteExclusion(exclusion) }) { Text(stringResource(R.string.btn_delete)) }
                        }
                    }
                }
            }
        }
    }
    if (showExclusionEditor) {
        LessonNotificationExclusionDialog(subjectSuggestions, subjectTeacherCandidates,
            onDismiss = { showExclusionEditor = false }, onAdd = onAddExclusion)
    }
}

@Composable
private fun LessonNotificationExclusionDialog(
    subjectSuggestions: List<String>,
    subjectTeacherCandidates: Map<String, List<String>>,
    onDismiss: () -> Unit,
    onAdd: (String, String?, Boolean) -> Unit
) {
    var subject by rememberSaveable { mutableStateOf("") }
    var teacher by rememberSaveable { mutableStateOf("") }
    var matchTeacher by rememberSaveable { mutableStateOf(false) }
    var showSubjectSuggestions by rememberSaveable { mutableStateOf(false) }
    val filteredSubjects = remember(subject, subjectSuggestions) {
        subjectSuggestions.asSequence().map { it.trim() }.filter { it.isNotBlank() }
            .filter { subject.isBlank() || it.contains(subject.trim(), ignoreCase = true) }
            .distinct().sorted().take(8).toList()
    }
    val teachers = remember(subject, subjectTeacherCandidates) {
        subjectTeacherCandidates.entries.firstOrNull { it.key.equals(subject.trim(), ignoreCase = true) }
            ?.value.orEmpty().map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.btn_add_lesson_start_notification_exclusion)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it; showSubjectSuggestions = false },
                        label = { Text(stringResource(R.string.label_task_subject)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (filteredSubjects.isNotEmpty()) {
                                IconButton(onClick = { showSubjectSuggestions = true }) {
                                    Icon(Icons.Filled.Search, stringResource(R.string.label_task_subject))
                                }
                            }
                        }
                    )
                    DropdownMenu(expanded = showSubjectSuggestions && filteredSubjects.isNotEmpty(),
                        onDismissRequest = { showSubjectSuggestions = false }) {
                        filteredSubjects.forEach { candidate ->
                            DropdownMenuItem(text = { Text(candidate) }, onClick = {
                                subject = candidate
                                showSubjectSuggestions = false
                                subjectTeacherCandidates[candidate].orEmpty().singleOrNull()?.let { teacher = it }
                            })
                        }
                    }
                }
                SettingsSwitchRow(
                    stringResource(R.string.label_lesson_start_notification_match_teacher),
                    stringResource(R.string.desc_lesson_start_notification_match_teacher),
                    matchTeacher, onCheckedChange = { matchTeacher = it }
                )
                if (matchTeacher) {
                    OutlinedTextField(value = teacher, onValueChange = { teacher = it },
                        label = { Text(stringResource(R.string.label_task_teacher)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        teachers.take(4).forEach { candidate ->
                            TextButton(onClick = { teacher = candidate }) { Text(candidate) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = subject.trim().isNotBlank(), onClick = {
                onAdd(subject.trim(), teacher.trim().takeIf { it.isNotBlank() }, matchTeacher && teacher.trim().isNotBlank())
                onDismiss()
            }) { Text(stringResource(R.string.btn_add_lesson_start_notification_exclusion)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}
