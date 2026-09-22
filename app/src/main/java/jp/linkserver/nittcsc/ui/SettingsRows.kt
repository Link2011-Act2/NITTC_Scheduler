package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.ui.components.SwitchPreferenceRow
import jp.linkserver.nittcsc.ui.components.ValuePreferenceRow

@Composable
internal fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) = SwitchPreferenceRow(title, description, checked, onCheckedChange, enabled)

/** ダイアログ内だけに下書きを持ち、確定時に既存の保存処理へ渡す。 */
@Composable
internal fun TextSettingRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    summary: String? = null,
    unit: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    transform: (String) -> String = { it }
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    ValuePreferenceRow(
        title = label,
        value = if (value.isBlank()) stringResource(R.string.settings_value_unset) else "$value$unit",
        summary = summary,
        onClick = { draft = value; editing = true }
    )
    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(label) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = transform(it) },
                        label = { Text(label) },
                        suffix = { if (unit.isNotEmpty()) Text(unit) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onValueChange(draft); editing = false }) {
                    Text(stringResource(R.string.btn_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}

@Composable
internal fun NumberSettingRow(label: String, value: String, unit: String, onValueChange: (String) -> Unit) {
    TextSettingRow(label, value, onValueChange, unit = unit, keyboardType = KeyboardType.Number,
        transform = { it.filter(Char::isDigit).take(3) })
}

@Composable
internal fun TimeSettingRow(
    label: String,
    hour: String,
    minute: String,
    onHourChange: (String) -> Unit,
    onMinuteChange: (String) -> Unit
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draftHour by rememberSaveable { mutableStateOf("") }
    var draftMinute by rememberSaveable { mutableStateOf("") }
    ValuePreferenceRow(
        title = label,
        value = if (hour.isBlank() && minute.isBlank()) stringResource(R.string.settings_value_unset)
            else "${hour.ifBlank { "--" }}:${minute.ifBlank { "--" }.padStart(2, '0')}",
        onClick = { draftHour = hour; draftMinute = minute; editing = true }
    )
    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(label) },
            text = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draftHour,
                        onValueChange = { draftHour = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(R.string.label_hour)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = draftMinute,
                        onValueChange = { draftMinute = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(R.string.label_minute)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onHourChange(draftHour)
                    onMinuteChange(draftMinute)
                    editing = false
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}

@Composable
internal fun <T> ListSettingRow(
    title: String,
    value: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    summary: String? = null,
    optionSummary: @Composable (T) -> String? = { null }
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    ValuePreferenceRow(title, optionLabel(value), { editing = true }, summary)
    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                    options.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().selectable(
                                selected = option == value,
                                role = Role.RadioButton,
                                onClick = { onSelect(option); editing = false }
                            ).padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            RadioButton(selected = option == value, onClick = null)
                            Column(Modifier.weight(1f)) {
                                Text(optionLabel(option))
                                optionSummary(option)?.let {
                                    Text(it, style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editing = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}
