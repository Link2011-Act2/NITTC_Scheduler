package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.logic.QrDisplaySettings
import jp.linkserver.nittcsc.logic.QrShareCodec

@Composable
internal fun QrDisplaySettingsDialog(
    settings: QrDisplaySettings, busy: Boolean, error: String?,
    onApply: (QrDisplaySettings) -> Unit, onDismiss: () -> Unit
) {
    var chunkInput by remember(settings) { mutableStateOf(settings.chunkBytes.toString()) }
    var intervalInput by remember(settings) { mutableStateOf(settings.frameIntervalMs.toString()) }
    val chunkBytes = chunkInput.toIntOrNull()
    val intervalMs = intervalInput.toLongOrNull()
    val chunkValid = chunkBytes != null && chunkBytes in QrShareCodec.MIN_CHUNK_BYTES..QrShareCodec.IMAGE_CHUNK_BYTES
    val intervalValid = intervalMs != null && intervalMs in QrDisplaySettings.MIN_FRAME_INTERVAL_MS..QrDisplaySettings.MAX_FRAME_INTERVAL_MS
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.qr_display_settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = chunkInput, onValueChange = { chunkInput = it.take(5) },
                    label = { Text(stringResource(R.string.qr_display_chunk_bytes)) },
                    supportingText = { Text(stringResource(R.string.qr_display_chunk_range, QrShareCodec.MIN_CHUNK_BYTES, QrShareCodec.IMAGE_CHUNK_BYTES)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, enabled = !busy, isError = !chunkValid)
                OutlinedTextField(value = intervalInput, onValueChange = { intervalInput = it.take(5) },
                    label = { Text(stringResource(R.string.qr_display_interval)) },
                    supportingText = { Text(stringResource(R.string.qr_display_interval_range, QrDisplaySettings.MIN_FRAME_INTERVAL_MS, QrDisplaySettings.MAX_FRAME_INTERVAL_MS)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, enabled = !busy, isError = !intervalValid)
                if (intervalValid) Text(stringResource(R.string.qr_display_rate, 1000f / intervalMs.toFloat()), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.qr_display_settings_description, QrShareCodec.MAX_PARTS), style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !busy, onClick = {
                    val defaults = QrDisplaySettings()
                    chunkInput = defaults.chunkBytes.toString()
                    intervalInput = defaults.frameIntervalMs.toString()
                }) { Text(stringResource(R.string.qr_display_restore_defaults)) }
                if (busy) CircularProgressIndicator()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && chunkValid && intervalValid, onClick = {
                if (chunkValid && intervalValid) onApply(QrDisplaySettings(chunkBytes, intervalMs))
            }) { Text(stringResource(R.string.qr_display_apply)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.qr_cancel)) } }
    )
}
