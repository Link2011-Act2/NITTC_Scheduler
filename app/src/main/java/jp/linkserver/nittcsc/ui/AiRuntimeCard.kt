package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.ml.AiRuntimeAvailability
import jp.linkserver.nittcsc.viewmodel.AiRuntimeUiState

@Composable
fun AiRuntimeCard(state: AiRuntimeUiState, onDownload: () -> Unit, onCancel: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ai_runtime_title), style = MaterialTheme.typography.titleMedium)
            val description = when (state.availability) {
                AiRuntimeAvailability.CHECKING -> R.string.ai_runtime_checking
                AiRuntimeAvailability.MISSING -> R.string.ai_runtime_description
                AiRuntimeAvailability.BUNDLED -> R.string.ai_runtime_bundled
                AiRuntimeAvailability.DOWNLOADED -> R.string.ai_runtime_downloaded
                AiRuntimeAvailability.UNSUPPORTED -> R.string.ai_runtime_unsupported
            }
            Text(stringResource(description), style = MaterialTheme.typography.bodyMedium)
            state.errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            if (state.downloading) {
                LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.ai_runtime_progress, state.progress))
                TextButton(onClick = onCancel) { Text(stringResource(R.string.notif_cancel_action)) }
            } else if (state.availability == AiRuntimeAvailability.MISSING || state.availability == AiRuntimeAvailability.BUNDLED) {
                Button(onClick = onDownload) {
                    Text(stringResource(if (state.errorRes == null) R.string.ai_runtime_download else R.string.ai_runtime_retry))
                }
            }
        }
    }
}
