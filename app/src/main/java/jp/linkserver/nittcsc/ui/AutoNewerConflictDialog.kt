package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.sync.SyncConflict
import kotlinx.coroutines.delay

@Composable
internal fun AutoNewerConflictDialog(
    conflicts: List<SyncConflict>,
    formatTimestamp: (Long) -> String,
    onApprove: () -> Unit,
    onCancel: () -> Unit
) {
    var remainingSeconds by remember(conflicts) { mutableStateOf(10) }
    var submitted by remember(conflicts) { mutableStateOf(false) }

    fun approve() {
        if (submitted) return
        submitted = true
        onApprove()
    }

    fun cancel() {
        if (submitted) return
        submitted = true
        onCancel()
    }

    LaunchedEffect(conflicts) {
        repeat(10) {
            delay(1_000)
            remainingSeconds--
        }
        approve()
    }

    AlertDialog(
        onDismissRequest = ::cancel,
        title = { Text(stringResource(R.string.sync_dialog_auto_conflict_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.sync_dialog_auto_conflict_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                conflicts.forEach { conflict ->
                    val remoteWins = conflict.remoteUpdatedAt > conflict.localUpdatedAt
                    val overwrittenDevice = if (remoteWins) conflict.localDeviceName else conflict.remoteDeviceName
                    val winningDevice = if (remoteWins) conflict.remoteDeviceName else conflict.localDeviceName
                    val overwrittenAt = if (remoteWins) conflict.localUpdatedAt else conflict.remoteUpdatedAt
                    val winningAt = if (remoteWins) conflict.remoteUpdatedAt else conflict.localUpdatedAt
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(conflict.label, fontWeight = FontWeight.Bold)
                            Text(
                                overwrittenDevice,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                            Text(
                                formatTimestamp(overwrittenAt),
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                "↓",
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                winningDevice,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                formatTimestamp(winningAt),
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = ::approve, enabled = !submitted) {
                Text(stringResource(R.string.sync_btn_auto_conflict_approve, remainingSeconds.coerceAtLeast(0)))
            }
        },
        dismissButton = {
            TextButton(onClick = ::cancel) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
