package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import jp.linkserver.nittcsc.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.data.LessonNotificationExclusionEntity
import jp.linkserver.nittcsc.data.LessonStartNotificationChipMode
import jp.linkserver.nittcsc.ui.components.AppSettingsGroup

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
    var subject by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var matchTeacher by remember { mutableStateOf(false) }
    var showSubjectSuggestions by remember { mutableStateOf(false) }
    var showLiveUpdateEarlyMinutesMenu by remember { mutableStateOf(false) }
    var showLiveUpdateDisplayDetails by rememberSaveable { mutableStateOf(false) }

    val filteredSubjectSuggestions = remember(subject, subjectSuggestions) {
        val query = subject.trim()
        subjectSuggestions
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { query.isBlank() || it.contains(query, ignoreCase = true) }
            .distinct()
            .sorted()
            .take(8)
            .toList()
    }
    val teacherCandidates = remember(subject, subjectTeacherCandidates) {
        val key = subject.trim()
        if (key.isBlank()) emptyList()
        else subjectTeacherCandidates.entries
            .firstOrNull { it.key.equals(key, ignoreCase = true) }
        ?.value.orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val canAdd = subject.trim().isNotBlank()

    AppSettingsGroup {
        section("notification-content", standardContainer = { sectionContent ->
                Column(Modifier.fillMaxWidth().animateContentSize()) { sectionContent() }
        }) {
            item("label_lesson_start_notification") {
                SettingsSwitchRow(
                    title = stringResource(R.string.label_lesson_start_notification),
                    description = stringResource(R.string.desc_lesson_start_notification),
                    checked = enabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            if (enabled) {
                section("label_lesson_start_notification_minutes", standardContainer = { sectionContent ->
                        Column(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            sectionContent()
                        }
                }) {
                    item("label_lesson_start_notification_minutes", contentPadding = PaddingValues(20.dp)) {
                        NumberSettingRow(
                            label = stringResource(R.string.label_lesson_start_notification_minutes),
                            value = minutesBefore,
                            unit = stringResource(R.string.unit_minutes_before),
                            onValueChange = { onMinutesBeforeChange(it.filter { c -> c.isDigit() }.take(3)) }
                        )
                    }

                    if (!notificationsEnabled) {
                        item("warning_notifications_disabled", contentPadding = PaddingValues(20.dp)) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.WarningAmber,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = stringResource(R.string.warning_notifications_disabled),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    OutlinedButton(onClick = onOpenNotificationSettings) {
                                        Text(stringResource(R.string.btn_open_notification_settings))
                                    }
                                }
                            }
                        }
                    }

                    if (liveUpdatesEnabled && liveUpdatesSupported && !promotedNotificationsEnabled) {
                        item("warning_promoted_notifications_disabled", contentPadding = PaddingValues(20.dp)) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.WarningAmber,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = stringResource(R.string.warning_promoted_notifications_disabled),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    OutlinedButton(onClick = onOpenPromotedNotificationSettings) {
                                        Text(stringResource(R.string.btn_open_live_updates_settings))
                                    }
                                }
                            }
                        }
                    }

                    section("label_lesson_start_live_updates", standardContainer = { sectionContent ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                sectionContent()
                            }
                    }) {
                        section("label_lesson_start_live_updates", standardContainer = { sectionContent ->
                                Column {
                                    sectionContent()
                                }
                        }) {
                            item("label_lesson_start_live_updates") {
                                SettingsSwitchRow(
                                    title = stringResource(R.string.label_lesson_start_live_updates),
                                    description = if (liveUpdatesSupported) {
                                        stringResource(R.string.desc_lesson_start_live_updates)
                                    } else {
                                        stringResource(R.string.desc_lesson_start_live_updates_unavailable)
                                    },
                                    checked = liveUpdatesEnabled,
                                    enabled = liveUpdatesSupported,
                                    onCheckedChange = onToggleLiveUpdates
                                )
                            }

                            if (liveUpdatesEnabled && liveUpdatesSupported) {
                                item("desc_lesson_start_live_update_early_minutes", contentPadding = PaddingValues(20.dp)) {
                                    Column(
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = stringResource(R.string.desc_lesson_start_live_update_early_minutes),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            ExposedDropdownMenuBox(
                                                expanded = showLiveUpdateEarlyMinutesMenu,
                                                onExpandedChange = {
                                                    showLiveUpdateEarlyMinutesMenu = !showLiveUpdateEarlyMinutesMenu
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                OutlinedTextField(
                                                    value = if (liveUpdateEarlyMinutes == 0) {
                                                        stringResource(R.string.lesson_start_live_update_early_none)
                                                    } else {
                                                        stringResource(
                                                            R.string.lesson_start_live_update_early_value,
                                                            liveUpdateEarlyMinutes
                                                        )
                                                    },
                                                    onValueChange = {},
                                                    readOnly = true,
                                                    label = {
                                                        Text(stringResource(R.string.label_lesson_start_live_update_early_minutes))
                                                    },
                                                    trailingIcon = {
                                                        ExposedDropdownMenuDefaults.TrailingIcon(
                                                            expanded = showLiveUpdateEarlyMinutesMenu
                                                        )
                                                    },
                                                    modifier = Modifier
                                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                                        .fillMaxWidth()
                                                )
                                                ExposedDropdownMenu(
                                                    expanded = showLiveUpdateEarlyMinutesMenu,
                                                    onDismissRequest = { showLiveUpdateEarlyMinutesMenu = false }
                                                ) {
                                                    listOf(0, 1, 2, 3, 5).forEach { minutes ->
                                                        DropdownMenuItem(
                                                            text = {
                                                                Text(
                                                                    if (minutes == 0) {
                                                                        stringResource(R.string.lesson_start_live_update_early_none)
                                                                    } else {
                                                                        stringResource(
                                                                            R.string.lesson_start_live_update_early_value,
                                                                            minutes
                                                                        )
                                                                    }
                                                                )
                                                            },
                                                            onClick = {
                                                                showLiveUpdateEarlyMinutesMenu = false
                                                                onUpdateLiveUpdateEarlyMinutes(minutes)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                showLiveUpdateDisplayDetails = !showLiveUpdateDisplayDetails
                                            },
                                            shape = MaterialTheme.shapes.medium,
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            tonalElevation = 0.dp,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.outlineVariant
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = stringResource(R.string.label_lesson_start_live_update_display_details),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = stringResource(R.string.desc_lesson_start_live_update_display_details),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Icon(
                                                    imageVector = if (showLiveUpdateDisplayDetails) {
                                                        Icons.Filled.ExpandLess
                                                    } else {
                                                        Icons.Filled.ExpandMore
                                                    },
                                                    contentDescription = null
                                                )
                                            }
                                        }

                                        if (showLiveUpdateDisplayDetails) {
                                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(
                                                        text = stringResource(R.string.label_lesson_start_progress_direction),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    LessonStartRadioOptionRow(
                                                        selected = !progressCountsDown,
                                                        title = stringResource(R.string.label_lesson_start_progress_increasing),
                                                        description = stringResource(R.string.desc_lesson_start_progress_increasing),
                                                        onClick = { onToggleProgressCountsDown(false) }
                                                    )
                                                    LessonStartRadioOptionRow(
                                                        selected = progressCountsDown,
                                                        title = stringResource(R.string.label_lesson_start_progress_decreasing),
                                                        description = stringResource(R.string.desc_lesson_start_progress_decreasing),
                                                        onClick = { onToggleProgressCountsDown(true) }
                                                    )
                                                }

                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(
                                                        text = stringResource(R.string.label_lesson_start_chip_mode),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    LessonStartRadioOptionRow(
                                                        selected = chipMode == LessonStartNotificationChipMode.CHRONOMETER,
                                                        title = stringResource(R.string.label_lesson_start_chip_mode_chronometer),
                                                        description = stringResource(R.string.desc_lesson_start_chip_mode_chronometer),
                                                        onClick = {
                                                            onUpdateChipMode(LessonStartNotificationChipMode.CHRONOMETER)
                                                        }
                                                    )
                                                    LessonStartRadioOptionRow(
                                                        selected = chipMode == LessonStartNotificationChipMode.MINUTE_TEXT,
                                                        title = stringResource(R.string.label_lesson_start_chip_mode_minute_text),
                                                        description = stringResource(R.string.desc_lesson_start_chip_mode_minute_text),
                                                        onClick = {
                                                            onUpdateChipMode(LessonStartNotificationChipMode.MINUTE_TEXT)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                        }

                    }

                    standardOnly("HorizontalDivider_9") {
                        HorizontalDivider()
                    }

                    item("label_lesson_start_notification_exclusions", contentPadding = PaddingValues(20.dp)) {
                        Text(
                            text = stringResource(R.string.label_lesson_start_notification_exclusions),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    item("desc_lesson_start_notification_exclusions", contentPadding = PaddingValues(20.dp)) {
                        Text(
                            text = stringResource(R.string.desc_lesson_start_notification_exclusions),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    item("label_task_subject", contentPadding = PaddingValues(20.dp)) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = subject,
                                onValueChange = {
                                    subject = it
                                    showSubjectSuggestions = false
                                },
                                label = { Text(stringResource(R.string.label_task_subject)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    if (filteredSubjectSuggestions.isNotEmpty()) {
                                        IconButton(onClick = { showSubjectSuggestions = true }) {
                                            Icon(
                                                imageVector = Icons.Filled.Search,
                                                contentDescription = stringResource(R.string.label_task_subject)
                                            )
                                        }
                                    }
                                }
                            )
                            DropdownMenu(
                                expanded = showSubjectSuggestions && filteredSubjectSuggestions.isNotEmpty(),
                                onDismissRequest = { showSubjectSuggestions = false },
                                modifier = Modifier.fillMaxWidth(0.95f)
                            ) {
                                filteredSubjectSuggestions.forEach { candidate ->
                                    DropdownMenuItem(
                                        text = { Text(candidate) },
                                        onClick = {
                                            subject = candidate
                                            showSubjectSuggestions = false
                                            val candidates = subjectTeacherCandidates[candidate].orEmpty()
                                            if (candidates.size == 1) {
                                                teacher = candidates.first()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item("label_lesson_start_notification_match_teacher", contentPadding = PaddingValues(20.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                matchTeacher = !matchTeacher
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = matchTeacher,
                                onCheckedChange = { checked -> matchTeacher = checked }
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.label_lesson_start_notification_match_teacher),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = stringResource(R.string.desc_lesson_start_notification_match_teacher),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (matchTeacher) {
                        item("label_task_teacher", contentPadding = PaddingValues(20.dp)) {
                            OutlinedTextField(
                                value = teacher,
                                onValueChange = { teacher = it },
                                label = { Text(stringResource(R.string.label_task_teacher)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (teacherCandidates.isNotEmpty()) {
                            item("Row_15", contentPadding = PaddingValues(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    teacherCandidates.take(4).forEach { candidate ->
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier.clickable { teacher = candidate }
                                        ) {
                                            Text(
                                                text = candidate,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item("btn_add_lesson_start_notification_exclusion", contentPadding = PaddingValues(20.dp)) {
                        Button(
                            onClick = {
                                onAddExclusion(
                                    subject.trim(),
                                    teacher.trim().takeIf { it.isNotBlank() },
                                    matchTeacher && teacher.trim().isNotBlank()
                                )
                                subject = ""
                                teacher = ""
                                matchTeacher = false
                            },
                            enabled = canAdd,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.btn_add_lesson_start_notification_exclusion))
                        }
                    }

                    if (exclusions.isEmpty()) {
                        item("msg_no_lesson_start_notification_exclusions", contentPadding = PaddingValues(20.dp)) {
                            Text(
                                text = stringResource(R.string.msg_no_lesson_start_notification_exclusions),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        item("label_lesson_start_notification_exclusion_teacher", contentPadding = PaddingValues(20.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                exclusions.forEach { exclusion ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = exclusion.subject,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (exclusion.matchTeacher && !exclusion.teacher.isNullOrBlank()) {
                                                Text(
                                                    text = stringResource(
                                                        R.string.label_lesson_start_notification_exclusion_teacher,
                                                        exclusion.teacher
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        TextButton(onClick = { onDeleteExclusion(exclusion) }) {
                                            Text(stringResource(R.string.btn_delete))
                                        }
                                    }
                                }
                            }
                        }
                    }

                }
            }

        }
    }
}

@Composable
private fun LessonStartRadioOptionRow(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

