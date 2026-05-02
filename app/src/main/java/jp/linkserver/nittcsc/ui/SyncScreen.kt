package jp.linkserver.nittcsc.ui

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.os.Build
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.sync.PreparedSyncSession
import jp.linkserver.nittcsc.sync.SyncChoice
import jp.linkserver.nittcsc.sync.SyncConflict
import jp.linkserver.nittcsc.sync.SyncResult
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class WifiSyncPhase {
    IDLE,
    PREPARING,
    CONFLICT,
    APPLYING,
    DONE,
    ERROR
}

private data class WifiSyncStatus(
    val phase: WifiSyncPhase = WifiSyncPhase.IDLE,
    val message: String = ""
)

private fun syncSetupNearbyPermissions(): Array<String> {
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

private fun shouldShowWifiErrorGuide(message: String): Boolean {
    return message.contains("未設定") ||
        message.contains("パスワード認証に失敗") ||
        message.contains("信頼済み端末の認証に失敗")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SyncScreen(
    state: SchedulerUiState,
    onBack: () -> Unit,
    onSaveProfile: (userNickname: String, deviceName: String, password: String, autoSyncEnabled: Boolean, conflictAutoNewerFirst: Boolean) -> Unit,
    onOpenDiscovery: () -> Unit,
    onOpenNearbySync: () -> Unit,
    onToggleTlsSync: (Boolean) -> Unit,
    onPrepareTrustedSync: suspend (String) -> SyncResult,
    onApplyPreparedSync: suspend (PreparedSyncSession, Map<String, SyncChoice>) -> SyncResult,
    onDeleteRegisteredDevice: suspend (String) -> SyncResult,
    formatTimestamp: (Long) -> String = { it.toString() }
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val nearbyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 初回セットアップ時の権限要求。拒否でも同期プロフィール保存は完了扱い */ }
    val profile = state.syncProfile
    var userNickname by remember(profile?.userNickname) { mutableStateOf(profile?.userNickname ?: "") }
    var deviceName by remember(profile?.deviceName) { mutableStateOf(profile?.deviceName ?: "") }
    var password by remember { mutableStateOf("") }
    var autoSyncEnabled by remember(profile?.autoSyncEnabled) { mutableStateOf(profile?.autoSyncEnabled ?: false) }
    var conflictAutoNewerFirst by remember(profile?.conflictAutoNewerFirst) { mutableStateOf(profile?.conflictAutoNewerFirst ?: false) }
    val tlsSyncEnabled = state.settings?.enableTlsSync ?: false

    // テキスト系フィールドはデバウンス付き自動保存
    LaunchedEffect(userNickname, deviceName) {
        delay(800)
        if (profile != null && userNickname.isNotBlank() && deviceName.isNotBlank()) {
            val resolvedPwd = if (password.isNotBlank()) password else ""
            onSaveProfile(userNickname, deviceName, resolvedPwd, autoSyncEnabled, conflictAutoNewerFirst)
        }
    }
    var message by remember { mutableStateOf<String?>(null) }
    var activeSyncDeviceId by remember { mutableStateOf<String?>(null) }
    val syncStatusByDeviceId = remember { mutableStateMapOf<String, WifiSyncStatus>() }
    var editingPassword by remember { mutableStateOf(false) }
    var showPasswordResetConfirm by remember { mutableStateOf(false) }
    var deleteConfirmDeviceId by remember { mutableStateOf<String?>(null) }
    var pendingSession by remember { mutableStateOf<PreparedSyncSession?>(null) }
    var pendingConflicts by remember { mutableStateOf<List<SyncConflict>>(emptyList()) }
    val pendingSyncQueue = remember { mutableStateListOf<String>() }
    val selectedDeviceIds = remember { mutableStateListOf<String>() }
    var syncQueueProcessing by remember { mutableStateOf(false) }
    val conflictChoices = remember { mutableStateMapOf<String, SyncChoice>() }
    val batchResultTargetDeviceIds = remember { mutableStateListOf<String>() }
    val batchSuccessDeviceNames = remember { mutableStateListOf<String>() }
    val profileSavedMessage = stringResource(R.string.sync_profile_saved)
    val requiresInitialSetup = profile != null && (profile.userNickname.isBlank() || profile.passwordLength == 0)

    if (requiresInitialSetup) {
        var setupError by remember { mutableStateOf<String?>(null) }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.sync_title_local_sync)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "最初に同期プロフィールを設定してください",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "はじめて同期を使うため、ユーザー名とパスワードの設定が必要です。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = userNickname,
                            onValueChange = {
                                userNickname = it
                                setupError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.sync_label_user_nickname)) },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = deviceName,
                            onValueChange = {
                                deviceName = it
                                setupError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.sync_label_device_name)) },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                setupError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.sync_label_device_password)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                        setupError?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Button(
                            onClick = {
                                val nickname = userNickname.trim()
                                val pwd = password.trim()
                                if (nickname.isBlank()) {
                                    setupError = "ユーザー名を入力してください。"
                                    return@Button
                                }
                                if (pwd.isBlank()) {
                                    setupError = "パスワードを入力してください。"
                                    return@Button
                                }
                                val resolvedDeviceName = deviceName.trim().ifBlank {
                                    profile?.deviceName?.takeIf { it.isNotBlank() } ?: Build.MODEL
                                }
                                onSaveProfile(nickname, resolvedDeviceName, pwd, autoSyncEnabled, conflictAutoNewerFirst)
                                nearbyPermissionLauncher.launch(syncSetupNearbyPermissions())
                                deviceName = resolvedDeviceName
                                password = ""
                                editingPassword = false
                                message = profileSavedMessage
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.btn_save))
                        }
                    }
                }
            }
        }
        return
    }

    LaunchedEffect(profile?.deviceId) {
        if (profile != null && deviceName.isBlank()) {
            deviceName = profile.deviceName
        }
        if (profile?.passwordLength == 0) {
            editingPassword = true
        }
    }

    LaunchedEffect(state.syncDiagnostics.isSearchingForIp) {
        if (state.syncDiagnostics.isSearchingForIp && activeSyncDeviceId != null) {
            syncStatusByDeviceId[activeSyncDeviceId!!] = WifiSyncStatus(
                phase = WifiSyncPhase.PREPARING,
                message = "IPが見つかりません、再検索します..."
            )
        }
    }

    fun startQueuedSyncIfNeeded() {
        if (syncQueueProcessing || pendingSession != null || pendingSyncQueue.isEmpty()) return
        syncQueueProcessing = true
        scope.launch {
            var pausedByConflict = false
            while (pendingSyncQueue.isNotEmpty() && pendingSession == null) {
                val deviceId = pendingSyncQueue.removeAt(0)
                activeSyncDeviceId = deviceId
                syncStatusByDeviceId[deviceId] = WifiSyncStatus(
                    phase = WifiSyncPhase.PREPARING,
                    message = "同期準備中..."
                )
                val result = runCatching { onPrepareTrustedSync(deviceId) }.getOrElse { e ->
                    syncStatusByDeviceId[deviceId] = WifiSyncStatus(
                        phase = WifiSyncPhase.ERROR,
                        message = e.message ?: "同期準備中にエラーが発生しました。"
                    )
                    continue
                }
                when {
                    result.ok && result.session != null && result.conflicts.isEmpty() -> {
                        syncStatusByDeviceId[deviceId] = WifiSyncStatus(
                            phase = WifiSyncPhase.APPLYING,
                            message = "同期を適用中..."
                        )
                        val applied = runCatching { onApplyPreparedSync(result.session, emptyMap()) }.getOrElse { e ->
                            SyncResult(false, e.message ?: "同期の適用中にエラーが発生しました。")
                        }
                        syncStatusByDeviceId[deviceId] = WifiSyncStatus(
                            phase = if (applied.ok) WifiSyncPhase.DONE else WifiSyncPhase.ERROR,
                            message = applied.message
                        )
                        if (applied.ok && batchResultTargetDeviceIds.contains(deviceId)) {
                            val successName = state.registeredDevices.firstOrNull { it.deviceId == deviceId }?.deviceName
                            if (!successName.isNullOrBlank() && !batchSuccessDeviceNames.contains(successName)) {
                                batchSuccessDeviceNames.add(successName)
                            }
                        }
                    }
                    result.ok && result.session != null && result.conflicts.isNotEmpty() && conflictAutoNewerFirst -> {
                        val resolutions = result.conflicts.associate { conflict ->
                            conflict.datasetKey to if (conflict.remoteUpdatedAt > conflict.localUpdatedAt) SyncChoice.REMOTE else SyncChoice.LOCAL
                        }
                        syncStatusByDeviceId[deviceId] = WifiSyncStatus(
                            phase = WifiSyncPhase.APPLYING,
                            message = "競合を自動解決して同期中..."
                        )
                        val applied = runCatching { onApplyPreparedSync(result.session, resolutions) }.getOrElse { e ->
                            SyncResult(false, e.message ?: "同期の適用中にエラーが発生しました。")
                        }
                        syncStatusByDeviceId[deviceId] = WifiSyncStatus(
                            phase = if (applied.ok) WifiSyncPhase.DONE else WifiSyncPhase.ERROR,
                            message = applied.message
                        )
                        if (applied.ok && batchResultTargetDeviceIds.contains(deviceId)) {
                            val successName = state.registeredDevices.firstOrNull { it.deviceId == deviceId }?.deviceName
                            if (!successName.isNullOrBlank() && !batchSuccessDeviceNames.contains(successName)) {
                                batchSuccessDeviceNames.add(successName)
                            }
                        }
                    }
                    result.ok && result.session != null && result.conflicts.isNotEmpty() -> {
                        pendingSession = result.session
                        pendingConflicts = result.conflicts
                        result.conflicts.forEach { conflictChoices[it.datasetKey] = SyncChoice.LOCAL }
                        syncStatusByDeviceId[deviceId] = WifiSyncStatus(
                            phase = WifiSyncPhase.CONFLICT,
                            message = "競合を解決してください。"
                        )
                        pausedByConflict = true
                        break
                    }
                    else -> {
                        syncStatusByDeviceId[deviceId] = WifiSyncStatus(
                            phase = WifiSyncPhase.ERROR,
                            message = result.message
                        )
                    }
                }
            }
            syncQueueProcessing = false
            if (!pausedByConflict && pendingSyncQueue.isEmpty() && pendingSession == null) {
                // 最後の結果は activeSyncDeviceId を維持してダイアログ表示する
            }
        }
    }

    fun enqueueSyncTargets(deviceIds: List<String>, collectBatchResult: Boolean = false) {
        if (collectBatchResult) {
            batchResultTargetDeviceIds.clear()
            batchResultTargetDeviceIds.addAll(deviceIds)
            batchSuccessDeviceNames.clear()
        }
        deviceIds.forEach { id ->
            if (!pendingSyncQueue.contains(id)) {
                pendingSyncQueue.add(id)
            }
        }
        startQueuedSyncIfNeeded()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_title_local_sync)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.sync_section_device_search), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.sync_device_search_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onOpenDiscovery,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.sync_btn_search_wifi))
                    }
                    OutlinedButton(
                        onClick = onOpenNearbySync,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.sync_btn_search_nearby))
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.sync_section_registered_devices), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val selectionMode = selectedDeviceIds.isNotEmpty()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            enabled = state.registeredDevices.isNotEmpty() && pendingSession == null,
                            onClick = {
                                selectedDeviceIds.clear()
                                enqueueSyncTargets(
                                    state.registeredDevices.map { it.deviceId },
                                    collectBatchResult = true
                                )
                            }
                        ) {
                            Text("登録済み全てと同期")
                        }
                        if (selectionMode) {
                            OutlinedButton(
                                enabled = pendingSession == null,
                                onClick = {
                                    enqueueSyncTargets(
                                        selectedDeviceIds.toList(),
                                        collectBatchResult = true
                                    )
                                    selectedDeviceIds.clear()
                                }
                            ) {
                                Text("選択した${selectedDeviceIds.size}台を同期")
                            }
                            TextButton(onClick = { selectedDeviceIds.clear() }) {
                                Text("解除")
                            }
                        }
                    }
                    if (selectionMode) {
                        Text(
                            text = "選択モード: タップで選択/解除",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.registeredDevices.isEmpty()) {
                        Text(stringResource(R.string.sync_no_registered_devices), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.registeredDevices.forEach { device ->
                            val expandedKey = device.deviceId.toString()
                            var detailExpanded by remember { mutableStateOf(false) }
                            val isSelected = selectedDeviceIds.contains(device.deviceId)
                            val selectionMode = selectedDeviceIds.isNotEmpty()
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // ヘッダー行（タップで展開）
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    if (selectionMode) {
                                                        if (isSelected) selectedDeviceIds.remove(device.deviceId) else selectedDeviceIds.add(device.deviceId)
                                                    } else {
                                                        detailExpanded = !detailExpanded
                                                    }
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    if (isSelected) selectedDeviceIds.remove(device.deviceId) else selectedDeviceIds.add(device.deviceId)
                                                }
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(device.deviceName, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                listOf(device.userNickname, "${device.host}:${device.port}").filter { it.isNotBlank() }.joinToString(" / "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (selectionMode) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            Icon(
                                                if (detailExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    // 折り畳み部分（日時情報）
                                    AnimatedVisibility(visible = detailExpanded) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                stringResource(R.string.sync_registered_schedule_settings, formatTimestamp(device.lastScheduleSettingsSyncAt)),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                stringResource(
                                                    R.string.sync_registered_tasks_plans,
                                                    formatTimestamp(device.lastTasksSyncAt),
                                                    formatTimestamp(device.lastPlansSyncAt)
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    val syncStatus = syncStatusByDeviceId[device.deviceId] ?: WifiSyncStatus()
                                    val thisDeviceBusy = syncStatus.phase == WifiSyncPhase.PREPARING || syncStatus.phase == WifiSyncPhase.APPLYING
                                    val lockedByOtherDevice = activeSyncDeviceId != null && activeSyncDeviceId != device.deviceId
                                    val queuedThisDevice = pendingSyncQueue.contains(device.deviceId)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Button(
                                            enabled = !thisDeviceBusy && !queuedThisDevice && pendingSession == null && !selectionMode,
                                            onClick = {
                                                enqueueSyncTargets(listOf(device.deviceId))
                                            }
                                        ) {
                                            Text(
                                                when {
                                                    thisDeviceBusy -> stringResource(R.string.sync_btn_syncing)
                                                    queuedThisDevice -> "待機中..."
                                                    lockedByOtherDevice -> "キューに追加"
                                                    else -> stringResource(R.string.sync_btn_manual_sync)
                                                }
                                            )
                                        }
                                        IconButton(
                                            enabled = !thisDeviceBusy && activeSyncDeviceId == null && pendingSyncQueue.isEmpty() && !selectionMode,
                                            onClick = { deleteConfirmDeviceId = device.deviceId }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "登録を削除"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.sync_section_this_device), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = userNickname,
                        onValueChange = { userNickname = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.sync_label_user_nickname)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = { deviceName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.sync_label_device_name)) },
                        singleLine = true
                    )
                    if (editingPassword || (profile?.passwordLength ?: 0) == 0) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.sync_label_device_password)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                    } else {
                        OutlinedTextField(
                            value = "•".repeat(profile?.passwordLength ?: 0),
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.sync_label_device_password)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            readOnly = true
                        )
                        TextButton(
                            onClick = { showPasswordResetConfirm = true },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(stringResource(R.string.sync_btn_change_password))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.sync_label_auto_sync), fontWeight = FontWeight.SemiBold)
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = stringResource(R.string.sync_label_experimental),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                            Text(
                                stringResource(R.string.sync_desc_auto_sync),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoSyncEnabled,
                            onCheckedChange = {
                                autoSyncEnabled = it
                                onSaveProfile(userNickname, deviceName, "", it, conflictAutoNewerFirst)
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.sync_label_conflict_newer_first), fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.sync_desc_conflict_newer_first),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = conflictAutoNewerFirst,
                            onCheckedChange = {
                                conflictAutoNewerFirst = it
                                onSaveProfile(userNickname, deviceName, "", autoSyncEnabled, it)
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.label_enable_tls_sync), fontWeight = FontWeight.SemiBold)
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = stringResource(R.string.sync_label_experimental),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                            Text(
                                stringResource(R.string.desc_enable_tls_sync),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = tlsSyncEnabled,
                            onCheckedChange = onToggleTlsSync
                        )
                    }
                    TextButton(
                        onClick = {
                            onSaveProfile(userNickname, deviceName, password, autoSyncEnabled, conflictAutoNewerFirst)
                            if (password.isNotBlank()) {
                                password = ""
                                editingPassword = false
                            }
                            message = profileSavedMessage
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.btn_save), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            message?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    val activeSyncStatus = activeSyncDeviceId?.let { syncStatusByDeviceId[it] }
    if (activeSyncDeviceId != null && activeSyncStatus != null) {
        val activeDeviceName = state.registeredDevices.find { it.deviceId == activeSyncDeviceId }?.deviceName ?: ""
        AlertDialog(
            onDismissRequest = {
                if (activeSyncStatus.phase == WifiSyncPhase.DONE || activeSyncStatus.phase == WifiSyncPhase.ERROR) {
                    activeSyncDeviceId?.let { id -> syncStatusByDeviceId[id] = WifiSyncStatus() }
                    activeSyncDeviceId = null
                }
            },
            title = {
                Text(
                    when (activeSyncStatus.phase) {
                        WifiSyncPhase.DONE -> "同期完了"
                        WifiSyncPhase.ERROR -> "同期失敗"
                        WifiSyncPhase.CONFLICT -> "競合を解決中"
                        else -> "同期中"
                    }
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (activeSyncStatus.phase) {
                        WifiSyncPhase.PREPARING,
                        WifiSyncPhase.APPLYING,
                        WifiSyncPhase.CONFLICT -> CircularProgressIndicator()
                        WifiSyncPhase.DONE -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                        WifiSyncPhase.ERROR -> Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(42.dp))
                        WifiSyncPhase.IDLE -> Unit
                    }
                    if (activeDeviceName.isNotBlank()) {
                        Text(activeDeviceName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    if (batchSuccessDeviceNames.isNotEmpty() &&
                        (activeSyncStatus.phase == WifiSyncPhase.DONE || activeSyncStatus.phase == WifiSyncPhase.ERROR)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("同期成功端末", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                batchSuccessDeviceNames.forEach { deviceName ->
                                    Text("・$deviceName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Text(
                        activeSyncStatus.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (activeSyncStatus.phase == WifiSyncPhase.ERROR && shouldShowWifiErrorGuide(activeSyncStatus.message)) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("対処方法", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text("1. 相手端末の同期画面で初回設定（ユーザー名・デバイス名・パスワード）を保存", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("2. 必要なら相手端末側で登録済みデバイスを再登録", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("3. もう一度『今すぐ同期』を実行", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (activeSyncStatus.phase == WifiSyncPhase.DONE || activeSyncStatus.phase == WifiSyncPhase.ERROR) {
                    Button(onClick = {
                        activeSyncDeviceId?.let { id -> syncStatusByDeviceId[id] = WifiSyncStatus() }
                        activeSyncDeviceId = null
                        batchResultTargetDeviceIds.clear()
                        batchSuccessDeviceNames.clear()
                    }) {
                        Text("閉じる")
                    }
                }
            }
        )
    }

    deleteConfirmDeviceId?.let { targetId ->
        val targetDevice = state.registeredDevices.firstOrNull { it.deviceId == targetId }
        AlertDialog(
            onDismissRequest = { deleteConfirmDeviceId = null },
            title = { Text("登録済みデバイスを削除") },
            text = {
                Text("${targetDevice?.deviceName ?: "このデバイス"} を登録済みデバイスから削除します。よろしいですか？")
            },
            confirmButton = {
                Button(onClick = {
                    deleteConfirmDeviceId = null
                    scope.launch {
                        val deleted = onDeleteRegisteredDevice(targetId)
                        message = deleted.message
                        if (activeSyncDeviceId == targetId) {
                            syncStatusByDeviceId[targetId] = WifiSyncStatus()
                            activeSyncDeviceId = null
                        }
                    }
                }) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmDeviceId = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showPasswordResetConfirm) {
        AlertDialog(
            onDismissRequest = { showPasswordResetConfirm = false },
            title = { Text(stringResource(R.string.sync_dialog_change_password_title)) },
            text = { Text(stringResource(R.string.sync_dialog_change_password_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showPasswordResetConfirm = false
                        editingPassword = true
                        password = ""
                    }
                ) {
                    Text(stringResource(R.string.sync_btn_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordResetConfirm = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

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
                                    SyncConflictChip(
                                        label = conflict.localDeviceName,
                                        timestamp = formatTimestamp(conflict.localUpdatedAt),
                                        isNewer = localIsNewer,
                                        selected = conflictChoices[conflict.datasetKey] != SyncChoice.REMOTE,
                                        onClick = { conflictChoices[conflict.datasetKey] = SyncChoice.LOCAL },
                                        modifier = Modifier.weight(1f)
                                    )
                                    SyncConflictChip(
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
                        val targetDeviceId = session.target.deviceId
                        pendingSession = null
                        pendingConflicts = emptyList()
                        conflictChoices.clear()
                        activeSyncDeviceId = targetDeviceId
                        syncStatusByDeviceId[targetDeviceId] = WifiSyncStatus(
                            phase = WifiSyncPhase.APPLYING,
                            message = "競合解決内容で同期中..."
                        )
                        scope.launch {
                            val result = runCatching { onApplyPreparedSync(session, resolutions) }
                                .getOrElse { e -> SyncResult(false, e.message ?: "同期エラー") }
                            syncStatusByDeviceId[targetDeviceId] = WifiSyncStatus(
                                phase = if (result.ok) WifiSyncPhase.DONE else WifiSyncPhase.ERROR,
                                message = result.message
                            )
                            if (result.ok && batchResultTargetDeviceIds.contains(targetDeviceId)) {
                                val successName = state.registeredDevices.firstOrNull { it.deviceId == targetDeviceId }?.deviceName
                                if (!successName.isNullOrBlank() && !batchSuccessDeviceNames.contains(successName)) {
                                    batchSuccessDeviceNames.add(successName)
                                }
                            }
                            startQueuedSyncIfNeeded()
                        }
                    }
                ) {
                    Text(stringResource(R.string.sync_btn_apply_with_choices))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val targetDeviceId = pendingSession?.target?.deviceId
                    pendingSession = null
                    pendingConflicts = emptyList()
                    conflictChoices.clear()
                    if (targetDeviceId != null) {
                        syncStatusByDeviceId[targetDeviceId] = WifiSyncStatus(
                            phase = WifiSyncPhase.ERROR,
                            message = "同期をキャンセルしました。"
                        )
                    }
                    startQueuedSyncIfNeeded()
                }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
private fun SyncConflictChip(
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

@Composable
private fun WifiSyncStatusHero(status: WifiSyncStatus) {
    val containerColor = when (status.phase) {
        WifiSyncPhase.DONE -> MaterialTheme.colorScheme.tertiaryContainer
        WifiSyncPhase.ERROR -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when (status.phase) {
        WifiSyncPhase.DONE -> MaterialTheme.colorScheme.onTertiaryContainer
        WifiSyncPhase.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val title = when (status.phase) {
        WifiSyncPhase.PREPARING -> "同期準備中"
        WifiSyncPhase.CONFLICT -> "競合を解決してください"
        WifiSyncPhase.APPLYING -> "同期中"
        WifiSyncPhase.DONE -> "同期完了"
        WifiSyncPhase.ERROR -> "同期失敗"
        WifiSyncPhase.IDLE -> ""
    }

    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (status.phase) {
                WifiSyncPhase.PREPARING,
                WifiSyncPhase.APPLYING,
                WifiSyncPhase.CONFLICT -> {
                    CircularProgressIndicator(modifier = Modifier.height(34.dp), strokeWidth = 3.dp)
                }
                WifiSyncPhase.DONE -> {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = contentColor
                    )
                }
                WifiSyncPhase.ERROR -> {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = contentColor
                    )
                }
                WifiSyncPhase.IDLE -> Unit
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = status.message,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor
            )
        }
    }
}

