package jp.linkserver.nittcsc.ui

import androidx.compose.animation.AnimatedVisibility
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.SyncRegisteredDeviceEntity
import jp.linkserver.nittcsc.sync.DiscoveredSyncDevice
import jp.linkserver.nittcsc.sync.DirectConnectResult
import jp.linkserver.nittcsc.sync.PreparedSyncSession
import jp.linkserver.nittcsc.sync.SyncChoice
import jp.linkserver.nittcsc.sync.SyncConflict
import jp.linkserver.nittcsc.sync.SyncDiagnostics
import jp.linkserver.nittcsc.sync.SyncResult
import kotlinx.coroutines.launch

private enum class WifiDiscoveryPhase { IDLE, SEARCHING }
private enum class WifiSyncFullScreenPhase { PREPARING, APPLYING, DONE, ERROR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncDeviceDiscoveryScreen(
    registeredDevices: List<SyncRegisteredDeviceEntity>,
    localListeningPort: Int,
    diagnostics: SyncDiagnostics,
    onBack: () -> Unit,
    onDiscoverDevices: suspend () -> List<DiscoveredSyncDevice>,
    onConnectToHost: suspend (host: String, port: Int) -> DirectConnectResult,
    onRunSelfConnectivityTest: suspend () -> DirectConnectResult,
    onPreparePasswordSync: suspend (DiscoveredSyncDevice, String) -> SyncResult,
    onPrepareTrustedSync: suspend (String) -> SyncResult,
    onApplyPreparedSync: suspend (PreparedSyncSession, Map<String, SyncChoice>) -> SyncResult,
    onRegisterTrustedDevice: suspend (DiscoveredSyncDevice, String) -> SyncResult,
    formatTimestamp: (Long) -> String
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var phase by remember { mutableStateOf(WifiDiscoveryPhase.IDLE) }
    var discoveredDevices by remember { mutableStateOf<List<DiscoveredSyncDevice>>(emptyList()) }
    val selectedDeviceId = remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isMessageError by remember { mutableStateOf(false) }
    var passwordPromptTitle by remember { mutableStateOf<String?>(null) }
    var passwordPromptAction by remember { mutableStateOf<(suspend (String) -> Unit)?>(null) }
    var promptPassword by remember { mutableStateOf("") }
    var pendingSession by remember { mutableStateOf<PreparedSyncSession?>(null) }
    var pendingConflicts by remember { mutableStateOf<List<SyncConflict>>(emptyList()) }
    var syncingFullScreenVisible by remember { mutableStateOf(false) }
    var syncingFullScreenPhase by remember { mutableStateOf(WifiSyncFullScreenPhase.PREPARING) }
    var syncingFullScreenMessage by remember { mutableStateOf("") }
    val conflictChoices = remember { mutableStateMapOf<String, SyncChoice>() }
    val registeredIds = remember(registeredDevices) { registeredDevices.map { it.deviceId }.toSet() }
    val selectedOne = discoveredDevices.firstOrNull { it.deviceId == selectedDeviceId.value }

    fun setMessage(msg: String, error: Boolean = false) {
        message = msg
        isMessageError = error
    }

    fun handleSyncResult(result: SyncResult) {
        if (!result.ok) {
            setMessage(result.message, error = true)
            syncingFullScreenVisible = true
            syncingFullScreenPhase = WifiSyncFullScreenPhase.ERROR
            syncingFullScreenMessage = result.message
            return
        }
        if (result.session != null && result.conflicts.isNotEmpty()) {
            syncingFullScreenVisible = false
            pendingSession = result.session
            pendingConflicts = result.conflicts
            result.conflicts.forEach { conflictChoices[it.datasetKey] = SyncChoice.LOCAL }
            setMessage(result.message)
            return
        }
        if (result.session != null) {
            syncingFullScreenVisible = true
            syncingFullScreenPhase = WifiSyncFullScreenPhase.APPLYING
            syncingFullScreenMessage = "同期を適用中..."
            scope.launch {
                val applied = runCatching { onApplyPreparedSync(result.session, emptyMap()) }
                    .getOrElse { e -> SyncResult(false, e.message ?: "同期エラー") }
                setMessage(applied.message, error = !applied.ok)
                syncingFullScreenVisible = true
                syncingFullScreenPhase = if (applied.ok) WifiSyncFullScreenPhase.DONE else WifiSyncFullScreenPhase.ERROR
                syncingFullScreenMessage = applied.message
            }
            return
        }
        setMessage(result.message)
        syncingFullScreenVisible = true
        syncingFullScreenPhase = WifiSyncFullScreenPhase.DONE
        syncingFullScreenMessage = result.message
    }

    fun startDiscovery() {
        busy = true
        phase = WifiDiscoveryPhase.SEARCHING
        discoveredDevices = emptyList()
        selectedDeviceId.value = null
        setMessage(context.getString(R.string.sync_wifi_searching))
        scope.launch {
            runCatching { onDiscoverDevices() }
                .onSuccess {
                    discoveredDevices = it
                    setMessage(
                        if (it.isEmpty()) context.getString(R.string.sync_wifi_no_available_devices)
                        else context.getString(R.string.sync_wifi_found_count, it.size)
                    )
                }
                .onFailure {
                    setMessage(it.message ?: context.getString(R.string.sync_wifi_search_failed), error = true)
                }
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_wifi_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (phase == WifiDiscoveryPhase.SEARCHING) {
                        IconButton(onClick = { startDiscovery() }, enabled = !busy) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.sync_wifi_cd_refresh_search))
                        }
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
            WifiStatusBanner(message = message, isError = isMessageError)

            // --- コンテンツ ---
            Box(modifier = Modifier.weight(1f)) {
                when (phase) {
                    WifiDiscoveryPhase.IDLE -> {
                        WifiIdleContent(
                            localListeningPort = localListeningPort,
                            diagnostics = diagnostics,
                            onStartDiscovery = { startDiscovery() },
                            onConnectToHost = { host, port ->
                                busy = true
                                setMessage(context.getString(R.string.sync_wifi_connecting))
                                scope.launch {
                                    val result = onConnectToHost(host, port)
                                    val device = result.device
                                    if (!result.ok || device == null) {
                                        setMessage(result.message, error = true)
                                    } else {
                                        discoveredDevices = listOf(device)
                                        selectedDeviceId.value = device.deviceId
                                        phase = WifiDiscoveryPhase.SEARCHING
                                        setMessage(result.message)
                                    }
                                    busy = false
                                }
                            },
                            onRunSelfTest = {
                                busy = true
                                scope.launch {
                                    val result = onRunSelfConnectivityTest()
                                    setMessage(result.message, error = !result.ok)
                                    busy = false
                                }
                            },
                            busy = busy
                        )
                    }

                    WifiDiscoveryPhase.SEARCHING -> {
                        if (syncingFullScreenVisible) {
                            WifiSyncingFullScreenContent(
                                phase = syncingFullScreenPhase,
                                message = syncingFullScreenMessage,
                                onDone = { syncingFullScreenVisible = false }
                            )
                        } else {
                            WifiSearchingContent(
                                discoveredDevices = discoveredDevices,
                                registeredIds = registeredIds,
                                registeredDevices = registeredDevices,
                                selectedDeviceId = selectedDeviceId.value,
                                onSelectDevice = { selectedDeviceId.value = it },
                                busy = busy,
                                onSyncNow = {
                                    val target = selectedOne ?: return@WifiSearchingContent
                                    if (registeredIds.contains(target.deviceId)) {
                                        busy = true
                                        syncingFullScreenVisible = true
                                        syncingFullScreenPhase = WifiSyncFullScreenPhase.PREPARING
                                        syncingFullScreenMessage = "同期準備中..."
                                        scope.launch {
                                            val result = onPrepareTrustedSync(target.deviceId)
                                            handleSyncResult(result)
                                            busy = false
                                        }
                                    } else {
                                        passwordPromptTitle = context.getString(R.string.sync_wifi_prompt_sync_password)
                                        passwordPromptAction = { entered ->
                                            syncingFullScreenVisible = true
                                            syncingFullScreenPhase = WifiSyncFullScreenPhase.PREPARING
                                            syncingFullScreenMessage = "同期準備中..."
                                            val result = onPreparePasswordSync(target, entered)
                                            handleSyncResult(result)
                                        }
                                    }
                                },
                                onRegister = {
                                    val target = selectedOne ?: return@WifiSearchingContent
                                    passwordPromptTitle = context.getString(R.string.sync_wifi_prompt_register_password)
                                    passwordPromptAction = { entered ->
                                        val result = onRegisterTrustedDevice(target, entered)
                                        setMessage(result.message, error = !result.ok)
                                    }
                                },
                                onStop = {
                                    phase = WifiDiscoveryPhase.IDLE
                                    discoveredDevices = emptyList()
                                    selectedDeviceId.value = null
                                    message = null
                                    syncingFullScreenVisible = false
                                },
                                diagnostics = diagnostics,
                                formatTimestamp = formatTimestamp
                            )
                        }
                    }
                }
            }
        }
    }

    // パスワード入力ダイアログ
    if (passwordPromptTitle != null && passwordPromptAction != null) {
        AlertDialog(
            onDismissRequest = {
                passwordPromptTitle = null
                passwordPromptAction = null
                promptPassword = ""
            },
            title = { Text(passwordPromptTitle!!) },
            text = {
                OutlinedTextField(
                    value = promptPassword,
                    onValueChange = { promptPassword = it },
                    label = { Text(stringResource(R.string.sync_label_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val action = passwordPromptAction ?: return@Button
                        val entered = promptPassword
                        passwordPromptTitle = null
                        passwordPromptAction = null
                        promptPassword = ""
                        busy = true
                        scope.launch {
                            runCatching { action(entered) }
                                .onFailure { e -> setMessage(e.message ?: "同期エラー", error = true) }
                            busy = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.btn_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    passwordPromptTitle = null
                    passwordPromptAction = null
                    promptPassword = ""
                }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    // 競合解決ダイアログ
    if (pendingSession != null && pendingConflicts.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {
                pendingSession = null
                pendingConflicts = emptyList()
                conflictChoices.clear()
            },
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
                            pendingConflicts.forEach { conflict ->
                                conflictChoices[conflict.datasetKey] =
                                    if (conflict.remoteUpdatedAt > conflict.localUpdatedAt) SyncChoice.REMOTE else SyncChoice.LOCAL
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.sync_btn_conflict_use_newer_all))
                    }
                    pendingConflicts.forEach { conflict ->
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
                                    WifiConflictChip(
                                        label = conflict.localDeviceName,
                                        timestamp = formatTimestamp(conflict.localUpdatedAt),
                                        isNewer = localIsNewer,
                                        selected = conflictChoices[conflict.datasetKey] != SyncChoice.REMOTE,
                                        onClick = { conflictChoices[conflict.datasetKey] = SyncChoice.LOCAL },
                                        modifier = Modifier.weight(1f)
                                    )
                                    WifiConflictChip(
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
                Button(
                    onClick = {
                        val session = pendingSession ?: return@Button
                        val resolutions = pendingConflicts.associate {
                            it.datasetKey to (conflictChoices[it.datasetKey] ?: SyncChoice.LOCAL)
                        }
                        pendingSession = null
                        pendingConflicts = emptyList()
                        conflictChoices.clear()
                        busy = true
                        syncingFullScreenVisible = true
                        syncingFullScreenPhase = WifiSyncFullScreenPhase.APPLYING
                        syncingFullScreenMessage = "競合解決内容で同期中..."
                        scope.launch {
                            val result = onApplyPreparedSync(session, resolutions)
                            setMessage(result.message, error = !result.ok)
                            syncingFullScreenVisible = true
                            syncingFullScreenPhase = if (result.ok) WifiSyncFullScreenPhase.DONE else WifiSyncFullScreenPhase.ERROR
                            syncingFullScreenMessage = result.message
                            busy = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.sync_btn_apply_with_choices))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingSession = null
                    pendingConflicts = emptyList()
                    conflictChoices.clear()
                }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

// --- ステータスバナー ---
@Composable
private fun WifiSyncingFullScreenContent(
    phase: WifiSyncFullScreenPhase,
    message: String,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (phase) {
            WifiSyncFullScreenPhase.PREPARING,
            WifiSyncFullScreenPhase.APPLYING -> {
                CircularProgressIndicator(modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.sync_btn_syncing),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            WifiSyncFullScreenPhase.DONE -> {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.nearby_done_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            WifiSyncFullScreenPhase.ERROR -> {
                Icon(
                    imageVector = Icons.Filled.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.nearby_error_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (phase == WifiSyncFullScreenPhase.DONE || phase == WifiSyncFullScreenPhase.ERROR) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.nearby_btn_done))
            }
        }
    }
}

// --- ステータスバナー ---
@Composable
private fun WifiStatusBanner(message: String?, isError: Boolean) {
    AnimatedVisibility(visible = !message.isNullOrBlank()) {
        Surface(
            color = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = message ?: stringResource(R.string.empty_string),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

// --- IDLE コンテンツ ---
@Composable
private fun WifiIdleContent(
    localListeningPort: Int,
    diagnostics: SyncDiagnostics,
    onStartDiscovery: () -> Unit,
    onConnectToHost: (String, Int) -> Unit,
    onRunSelfTest: () -> Unit,
    busy: Boolean
) {
    var diagExpanded by remember { mutableStateOf(false) }
    var ipExpanded by remember { mutableStateOf(false) }
    var manualHost by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("46784") }

    Column(modifier = Modifier.fillMaxSize()) {
        // メインコンテンツ（中央揃え）
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.Wifi,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.sync_wifi_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.sync_wifi_idle_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onStartDiscovery,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.sync_wifi_btn_start_search))
            }
        }

        // この端末の情報（折り畳み）
        HorizontalDivider()
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { diagExpanded = !diagExpanded }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(R.string.sync_wifi_section_this_device_info), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (diagnostics.serverRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = if (diagnostics.serverRunning) stringResource(R.string.sync_wifi_server_waiting) else stringResource(R.string.sync_wifi_server_stopped),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (diagnostics.serverRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            if (diagnostics.localAddresses.isNotEmpty())
                                stringResource(R.string.sync_wifi_ip_port_label, diagnostics.localAddresses.first(), localListeningPort)
                            else
                                stringResource(R.string.sync_wifi_port_label, localListeningPort),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        if (diagExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AnimatedVisibility(visible = diagExpanded) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            stringResource(
                                R.string.sync_wifi_ip_label,
                                diagnostics.localAddresses.joinToString(", ").ifBlank { stringResource(R.string.sync_wifi_ip_unavailable) }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = onRunSelfTest,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.sync_wifi_btn_self_test))
                        }
                    }
                }
            }
        }

        // IP直接接続（折り畳み）
        HorizontalDivider()
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { ipExpanded = !ipExpanded }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.sync_wifi_section_direct_connect), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Icon(
                        if (ipExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AnimatedVisibility(visible = ipExpanded) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = manualHost,
                                onValueChange = { manualHost = it },
                                modifier = Modifier.weight(1f),
                                label = { Text(stringResource(R.string.sync_wifi_label_ip_address)) },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = manualPort,
                                onValueChange = { manualPort = it.filter(Char::isDigit).take(5) },
                                modifier = Modifier.weight(0.45f),
                                label = { Text(stringResource(R.string.sync_wifi_label_port)) },
                                singleLine = true
                            )
                        }
                        Button(
                            onClick = {
                                if (manualHost.trim().isNotBlank()) {
                                    onConnectToHost(manualHost.trim(), manualPort.toIntOrNull() ?: 46784)
                                }
                            },
                            enabled = !busy && manualHost.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (busy) stringResource(R.string.sync_wifi_connecting) else stringResource(R.string.sync_wifi_btn_connect_this_ip))
                        }
                    }
                }
            }
        }
    }
}

// --- SEARCHING コンテンツ ---
@Composable
private fun WifiSearchingContent(
    discoveredDevices: List<DiscoveredSyncDevice>,
    registeredIds: Set<String>,
    registeredDevices: List<SyncRegisteredDeviceEntity>,
    selectedDeviceId: String?,
    onSelectDevice: (String) -> Unit,
    busy: Boolean,
    onSyncNow: () -> Unit,
    onRegister: () -> Unit,
    onStop: () -> Unit,
    diagnostics: SyncDiagnostics,
    formatTimestamp: (Long) -> String
) {
    val context = LocalContext.current
    var logsExpanded by remember { mutableStateOf(false) }

    fun copyConnectionLogsToClipboard() {
        if (diagnostics.recentLogs.isEmpty()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val text = diagnostics.recentLogs.joinToString(separator = "\n")
        val clip = ClipData.newPlainText("connection_logs", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, context.getString(R.string.sync_wifi_logs_copied), Toast.LENGTH_SHORT).show()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (discoveredDevices.isNotEmpty()) {
            // デバイス一覧表示中のみ上部ステータスを表示
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Filled.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    if (busy) stringResource(R.string.sync_wifi_searching_nearby_devices) else stringResource(R.string.sync_wifi_found_devices),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
        }

        // デバイスリスト
        if (discoveredDevices.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    if (busy) stringResource(R.string.sync_wifi_searching) else stringResource(R.string.sync_wifi_no_devices_found),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.sync_wifi_no_devices_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(discoveredDevices, key = { it.deviceId }) { device ->
                    val isRegistered = registeredIds.contains(device.deviceId)
                    val isSelected = selectedDeviceId == device.deviceId
                    ListItem(
                        headlineContent = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(device.deviceName, fontWeight = FontWeight.SemiBold)
                                if (isRegistered) {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            stringResource(R.string.sync_label_registered),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        },
                        supportingContent = {
                            val meta = buildList {
                                if (device.userNickname.isNotBlank()) add(device.userNickname)
                                add(device.host)
                            }.joinToString(" / ")
                            Text(
                                meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Filled.PhoneAndroid,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = if (isSelected) ({
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectDevice(device.deviceId) }
                            .padding(horizontal = 4.dp)
                    )
                    HorizontalDivider()
                }
            }
        }

        // 操作ボタン
        Surface(
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val target = discoveredDevices.firstOrNull { it.deviceId == selectedDeviceId }
                val isRegistered = target != null && registeredIds.contains(target.deviceId)
                Button(
                    onClick = onSyncNow,
                    enabled = target != null && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.sync_wifi_btn_sync_now))
                }
                if (target != null && !isRegistered) {
                    OutlinedButton(
                        onClick = onRegister,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.sync_wifi_btn_register_trusted))
                    }
                }
                if (target != null && isRegistered) {
                    registeredDevices.firstOrNull { it.deviceId == target.deviceId }?.let { reg ->
                        Text(
                            stringResource(R.string.sync_wifi_last_sync, formatTimestamp(reg.lastTasksSyncAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.sync_wifi_btn_stop_search))
                }
            }
        }

        // 接続ログ（折り畳み）
        HorizontalDivider()
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { logsExpanded = !logsExpanded }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(R.string.sync_wifi_section_connection_logs), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (diagnostics.recentLogs.isNotEmpty()) {
                            Text(
                                stringResource(R.string.sync_wifi_logs_count, diagnostics.recentLogs.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { copyConnectionLogsToClipboard() },
                            enabled = diagnostics.recentLogs.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = stringResource(R.string.sync_wifi_copy_logs),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            if (logsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                AnimatedVisibility(visible = logsExpanded) {
                    if (diagnostics.recentLogs.isEmpty()) {
                        Text(
                            stringResource(R.string.sync_wifi_no_logs),
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .padding(bottom = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .padding(bottom = 8.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                diagnostics.recentLogs.forEach { line ->
                                    Text(
                                        line,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
private fun WifiConflictChip(
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
