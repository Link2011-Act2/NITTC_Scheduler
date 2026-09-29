package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.SettingsEntity
import jp.linkserver.nittcsc.logic.validSecondTermStart

@Composable
internal fun SecondTermStartSetting(
    settings: SettingsEntity?,
    onChange: (Int, Int) -> Unit
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var month by rememberSaveable(settings?.secondTermStartMonth) {
        mutableStateOf((settings?.secondTermStartMonth ?: 10).toString())
    }
    var day by rememberSaveable(settings?.secondTermStartDay) {
        mutableStateOf((settings?.secondTermStartDay ?: 1).toString())
    }
    Column {
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier.fillMaxWidth().clickable { editing = true }
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(stringResource(R.string.label_second_term_start), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    R.string.value_second_term_start,
                    settings?.secondTermStartMonth ?: 10,
                    settings?.secondTermStartDay ?: 1
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (editing) {
        val parsedMonth = month.toIntOrNull()
        val parsedDay = day.toIntOrNull()
        val valid = parsedMonth != null && parsedDay != null &&
            validSecondTermStart(parsedMonth, parsedDay)
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.label_second_term_start)) },
            text = {
                Column {
                    Row {
                        OutlinedTextField(
                            value = month,
                            onValueChange = { month = it.filter(Char::isDigit).take(2) },
                            label = { Text(stringResource(R.string.label_month)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        OutlinedTextField(
                            value = day,
                            onValueChange = { day = it.filter(Char::isDigit).take(2) },
                            label = { Text(stringResource(R.string.label_day)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        stringResource(R.string.desc_second_term_start),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    onChange(checkNotNull(parsedMonth), checkNotNull(parsedDay))
                    editing = false
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}
