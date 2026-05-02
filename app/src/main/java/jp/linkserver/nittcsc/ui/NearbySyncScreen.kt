package jp.linkserver.nittcsc.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.sync.NearbyEndpoint
import jp.linkserver.nittcsc.sync.NearbyPhase
import jp.linkserver.nittcsc.sync.NearbyState
import jp.linkserver.nittcsc.sync.SyncChoice
import jp.linkserver.nittcsc.sync.SyncConflict
import androidx.compose.foundation.selection.toggleable

private fun nearbyPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbySyncScreen(
    nearbyState: NearbyState,
    onBack: () -> Unit,
    onStartSearching: () -> Unit,
    onConnectToEndpoint: (NearbyEndpoint) -> Unit,
    onAcceptConnection: () -> Unit,
    onRejectConnection: () -> Unit,
    onStopAll: () -> Unit,
    onApplyConflictResolutions: (Map<String, SyncChoice>) -> Unit = {},
    formatTimestamp: (Long) -> String = { it.toString() }
) {
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }
    var conflictSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(nearbyState.phase, nearbyState.pendingConflicts.size) {
        if (nearbyState.phase != NearbyPhase.CONFLICT || nearbyState.pendingConflicts.isEmpty()) {
            conflictSubmitting = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            onStartSearching()
        } else {
            permissionDenied = true
        }
    }

    // 画面を離れるときに必ず停止
    DisposableEffect(Unit) {
        onDispose { onStopAll() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nearby_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        onStopAll()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- ステータスバナー ---
            StatusBanner(state = nearbyState)

            // --- コンテンツ ---
            Box(modifier = Modifier.weight(1f)) {
                when (nearbyState.phase) {
                    NearbyPhase.IDLE -> {
                        IdleContent(
                            permissionDenied = permissionDenied,
                            onStart = {
                                permissionDenied = false
                                permissionLauncher.launch(nearbyPermissions())
                            }
                        )
                    }

                    NearbyPhase.SEARCHING -> {
                        SearchingContent(
                            endpoints = nearbyState.discoveredEndpoints,
                            connectingEndpointId = nearbyState.pendingEndpointId,
                            onConnect = onConnectToEndpoint,
                            onStop = onStopAll
                        )
                    }

                    NearbyPhase.SYNCING -> {
                        SyncingContent()
                    }

                    NearbyPhase.CONFLICT -> {
                        // ダイアログ表示中はSYNCING画面を背景として維持
                        SyncingContent()
                    }

                    NearbyPhase.DONE -> {
                        DoneContent(
                            onDone = {
                                onStopAll()
                                onBack()
                            }
                        )
                    }

                    NearbyPhase.ERROR -> {
                        ErrorContent(
                            message = nearbyState.message,
                            onRetry = {
                                permissionLauncher.launch(nearbyPermissions())
                            }
                        )
                    }

                    NearbyPhase.AUTH_CONFIRM -> {
                        // AUTH_CONFIRMはダイアログで処理
                        SearchingContent(
                            endpoints = nearbyState.discoveredEndpoints,
                            connectingEndpointId = nearbyState.pendingEndpointId,
                            onConnect = onConnectToEndpoint,
                            onStop = onStopAll
                        )
                    }
                }
            }
        }
    }

    // --- 認証コード確認ダイアログ ---
    if (nearbyState.phase == NearbyPhase.AUTH_CONFIRM && nearbyState.pendingAuthCode != null) {
        AlertDialog(
            onDismissRequest = { onRejectConnection() },
            title = { Text(stringResource(R.string.nearby_dialog_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.nearby_dialog_confirm_message, nearbyState.pendingEndpointName ?: ""),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(R.string.nearby_dialog_confirm_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = nearbyState.pendingAuthCode,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = onAcceptConnection) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.nearby_btn_code_matches), modifier = Modifier.padding(start = 4.dp))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onRejectConnection) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.nearby_btn_reject), modifier = Modifier.padding(start = 4.dp))
                }
            }
        )
    }

    // --- 競合解決ダイアログ ---
    if (nearbyState.phase == NearbyPhase.CONFLICT && nearbyState.pendingConflicts.isNotEmpty() && !conflictSubmitting) {
        val conflictChoices = remember(nearbyState.pendingConflicts) { mutableStateMapOf<String, SyncChoice>().also { map ->
            nearbyState.pendingConflicts.forEach { map[it.datasetKey] = SyncChoice.LOCAL }
        }}
        AlertDialog(
            onDismissRequest = { /* 競合は必ず解決してもらう */ },
            title = { Text(stringResource(R.string.sync_dialog_conflict_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.sync_dialog_conflict_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            nearbyState.pendingConflicts.forEach { conflict ->
                                conflictChoices[conflict.datasetKey] =
                                    if (conflict.remoteUpdatedAt > conflict.localUpdatedAt) SyncChoice.REMOTE else SyncChoice.LOCAL
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.sync_btn_conflict_use_newer_all))
                    }
                    nearbyState.pendingConflicts.forEach { conflict ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(conflict.label, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val localIsNewer = conflict.localUpdatedAt >= conflict.remoteUpdatedAt
                                    NearbyConflictChip(
                                        label = conflict.localDeviceName,
                                        timestamp = formatTimestamp(conflict.localUpdatedAt),
                                        isNewer = localIsNewer,
                                        selected = conflictChoices[conflict.datasetKey] != SyncChoice.REMOTE,
                                        onClick = { conflictChoices[conflict.datasetKey] = SyncChoice.LOCAL },
                                        modifier = Modifier.weight(1f)
                                    )
                                    NearbyConflictChip(
                                        label = conflict.remoteDeviceName,
                                        timestamp = formatTimestamp(conflict.remoteUpdatedAt),
                                        isNewer = !localIsNewer,
                                        selected = conflictChoices[conflict.datasetKey] == SyncChoice.REMOTE,
                                        onClick = { conflictChoices[conflict.datasetKey] = SyncChoice.REMOTE },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val resolutions = nearbyState.pendingConflicts.associate {
                        it.datasetKey to (conflictChoices[it.datasetKey] ?: SyncChoice.LOCAL)
                    }
                    conflictSubmitting = true
                    onApplyConflictResolutions(resolutions)
                }) {
                    Text(stringResource(R.string.sync_btn_apply_with_choices))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val resolutions = nearbyState.pendingConflicts.associate { conflict ->
                        conflict.datasetKey to
                            if (conflict.remoteUpdatedAt > conflict.localUpdatedAt) SyncChoice.REMOTE else SyncChoice.LOCAL
                    }
                    conflictSubmitting = true
                    onApplyConflictResolutions(resolutions)
                }) {
                    Text(stringResource(R.string.sync_btn_conflict_use_newer_all))
                }
            }
        )
    }
}

@Composable
private fun StatusBanner(state: NearbyState) {
    AnimatedVisibility(visible = state.message.isNotBlank()) {
        Surface(
            color = if (state.isError)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = state.message,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.isError)
                    MaterialTheme.colorScheme.onErrorContainer
                else
                    MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun IdleContent(permissionDenied: Boolean, onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.Bluetooth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.nearby_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.nearby_idle_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (permissionDenied) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(R.string.nearby_permission_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.nearby_btn_start_search))
        }
    }
}

@Composable
private fun SearchingContent(
    endpoints: List<NearbyEndpoint>,
    connectingEndpointId: String?,
    onConnect: (NearbyEndpoint) -> Unit,
    onStop: () -> Unit
) {
    val hasPendingConnection = !connectingEndpointId.isNullOrBlank()
    Column(modifier = Modifier.fillMaxSize()) {
        if (endpoints.isNotEmpty()) {
            // デバイス一覧表示中のみ上部ステータスを表示
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    stringResource(R.string.nearby_searching),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
        }

        if (endpoints.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.nearby_searching),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.nearby_no_devices),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.nearby_no_devices_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(endpoints, key = { it.endpointId }) { endpoint ->
                    val isConnecting = endpoint.endpointId == connectingEndpointId
                    ListItem(
                        headlineContent = { Text(endpoint.name, fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    if (isConnecting) R.string.nearby_connecting_selected
                                    else R.string.nearby_tap_to_connect
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            if (isConnecting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !hasPendingConnection) { onConnect(endpoint) }
                            .padding(horizontal = 4.dp)
                    )
                    HorizontalDivider()
                }
            }
        }

        OutlinedButton(
            onClick = onStop,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.nearby_btn_stop_search))
        }
    }
}

@Composable
private fun SyncingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.nearby_syncing), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.nearby_syncing_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DoneContent(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.nearby_done_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.nearby_done_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.nearby_btn_done))
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.nearby_error_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.nearby_btn_retry))
        }
    }
}

@Composable
private fun NearbyConflictChip(
    label: String,
    timestamp: String,
    isNewer: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.toggleable(value = selected, onValueChange = { onClick() }),
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            if (isNewer) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        stringResource(R.string.sync_label_newer),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Text(timestamp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
