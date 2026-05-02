package jp.linkserver.nittcsc.sync

import android.content.Context
import android.os.Build
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import jp.linkserver.nittcsc.data.SchedulerRepository
import jp.linkserver.nittcsc.sync.SyncChoice
import jp.linkserver.nittcsc.sync.SyncConflict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

enum class NearbyPhase { IDLE, SEARCHING, AUTH_CONFIRM, SYNCING, CONFLICT, DONE, ERROR }

data class NearbyEndpoint(
    val endpointId: String,
    val name: String
)

data class NearbyState(
    val phase: NearbyPhase = NearbyPhase.IDLE,
    val discoveredEndpoints: List<NearbyEndpoint> = emptyList(),
    val pendingAuthCode: String? = null,
    val pendingEndpointId: String? = null,
    val pendingEndpointName: String? = null,
    val message: String = "",
    val isError: Boolean = false,
    val pendingConflicts: List<SyncConflict> = emptyList(),
    val pendingLocalPayload: String? = null,
    val pendingRemotePayload: String? = null
)

class NearbySyncManager(
    context: Context,
    private val repository: SchedulerRepository
) {
    private val appContext = context.applicationContext
    private val connectionsClient = Nearby.getConnectionsClient(appContext)
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(NearbyState())
    val state: StateFlow<NearbyState> = _state.asStateFlow()

    companion object {
        private const val SERVICE_ID = "jp.linkserver.nittcsc.nearby_sync"
        private val STRATEGY = Strategy.P2P_CLUSTER
        private const val MESSAGE_TYPE_SYNC = "sync"
        private const val MESSAGE_TYPE_VERIFY = "sync_verify"
        private const val MESSAGE_TYPE_MERGED = "sync_merged"
        private const val MERGED_WAIT_TIMEOUT_MS = 180_000L
    }

    private var localName: String = Build.MODEL
    var conflictAutoNewerFirst: Boolean = false
    private var receivedPayloadChannel = Channel<JSONObject>(Channel.BUFFERED)
    private val payloadBuffer = mutableMapOf<Long, ByteArray>()
    private var standbyActive = false
    private val requestedConnectionIds = mutableSetOf<String>()
    private val endpointInitiatorMap = mutableMapOf<String, Boolean>()

    fun setLocalName(name: String) {
        localName = name.ifBlank { Build.MODEL }
    }

    /** アプリ起動中は常に広告を流してほかの端末から見つけられるようにする（検索画面を開かなくてもよい）。 */
    fun startStandbyAdvertising() {
        if (standbyActive) return
        standbyActive = true
        val advOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(localName, SERVICE_ID, connectionLifecycleCallback, advOptions)
            .addOnFailureListener { /* スタンバイ失敗は無視 */ }
    }

    fun stopStandbyAdvertising() {
        if (!standbyActive) return
        standbyActive = false
        // 検索中でない場合のみ広告を止める
        if (_state.value.phase == NearbyPhase.IDLE) {
            connectionsClient.stopAdvertising()
        }
    }

    fun startSearching() {
        receivedPayloadChannel.close()
        receivedPayloadChannel = Channel(Channel.BUFFERED)
        payloadBuffer.clear()

        _state.value = NearbyState(phase = NearbyPhase.SEARCHING, message = "近くのデバイスを検索中...")

        // 既存の探索/広告が残っていると STATUS_ALREADY_DISCOVERING になるため先に停止する
        connectionsClient.stopDiscovery()
        // スタンバイ広告はいったん止めてから正式な広告を開始する（同一 SERVICE_ID で重複しないよう）
        connectionsClient.stopAdvertising()
        standbyActive = false

        val advOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(localName, SERVICE_ID, connectionLifecycleCallback, advOptions)
            .addOnFailureListener { e ->
                _state.value = _state.value.copy(
                    phase = NearbyPhase.ERROR,
                    message = "アドバタイズ開始失敗: ${e.message}",
                    isError = true
                )
            }

        val discOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discOptions)
            .addOnFailureListener { e ->
                _state.value = _state.value.copy(
                    phase = NearbyPhase.ERROR,
                    message = "検索開始失敗: ${e.message}",
                    isError = true
                )
            }
    }

    fun requestConnectionTo(endpoint: NearbyEndpoint) {
        requestedConnectionIds += endpoint.endpointId
        _state.value = _state.value.copy(
            pendingEndpointId = endpoint.endpointId,
            pendingEndpointName = endpoint.name,
            message = "${endpoint.name} に接続要求を送信中...",
            isError = false
        )
        connectionsClient.requestConnection(localName, endpoint.endpointId, connectionLifecycleCallback)
            .addOnFailureListener { e ->
                requestedConnectionIds -= endpoint.endpointId
                _state.value = _state.value.copy(
                    pendingEndpointId = null,
                    pendingEndpointName = null,
                    message = "接続要求失敗: ${e.message}",
                    isError = true
                )
            }
    }

    fun acceptConnection() {
        val endpointId = _state.value.pendingEndpointId ?: return
        connectionsClient.acceptConnection(endpointId, payloadCallback)
        _state.value = _state.value.copy(
            pendingAuthCode = null,
            message = "承認しました。相手の確認を待っています..."
        )
    }

    fun rejectConnection() {
        val endpointId = _state.value.pendingEndpointId ?: return
        connectionsClient.rejectConnection(endpointId)
        requestedConnectionIds -= endpointId
        endpointInitiatorMap.remove(endpointId)
        // 検索中に割り込んできた場合は SEARCHING に戻す、スタンバイ中は IDLE に戻す
        val nextPhase = if (standbyActive || _state.value.discoveredEndpoints.isEmpty()) NearbyPhase.IDLE else NearbyPhase.SEARCHING
        _state.value = _state.value.copy(
            phase = nextPhase,
            pendingAuthCode = null,
            pendingEndpointId = null,
            pendingEndpointName = null,
            message = "接続を拒否しました。"
        )
    }

    fun stopAll() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        receivedPayloadChannel.close()
        payloadBuffer.clear()
        requestedConnectionIds.clear()
        endpointInitiatorMap.clear()
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        standbyActive = false
        _state.value = NearbyState()
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            endpointInitiatorMap[endpointId] = requestedConnectionIds.contains(endpointId)
            _state.value = _state.value.copy(
                phase = NearbyPhase.AUTH_CONFIRM,
                pendingEndpointId = endpointId,
                pendingEndpointName = info.endpointName,
                pendingAuthCode = info.authenticationDigits,
                message = "${info.endpointName} から接続要求があります。"
            )
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                endpointInitiatorMap[endpointId] = endpointInitiatorMap[endpointId] ?: requestedConnectionIds.contains(endpointId)
                // チャンネルをリセット（stopAll や前回の同期で閉じられている可能性があるため）
                receivedPayloadChannel.close()
                receivedPayloadChannel = Channel(Channel.BUFFERED)
                payloadBuffer.clear()
                _state.value = _state.value.copy(
                    phase = NearbyPhase.SYNCING,
                    pendingEndpointId = endpointId,
                    pendingAuthCode = null,
                    message = "接続しました。同期中..."
                )
                scope.launch { performSync(endpointId) }
            } else {
                _state.value = _state.value.copy(
                    phase = NearbyPhase.ERROR,
                    pendingEndpointId = null,
                    pendingAuthCode = null,
                    message = "接続失敗: ${result.status.statusMessage ?: "不明なエラー"}",
                    isError = true
                )
                requestedConnectionIds -= endpointId
                endpointInitiatorMap.remove(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            requestedConnectionIds -= endpointId
            endpointInitiatorMap.remove(endpointId)
            val current = _state.value
            if (current.phase != NearbyPhase.DONE && current.phase != NearbyPhase.ERROR) {
                _state.value = current.copy(
                    phase = NearbyPhase.IDLE,
                    pendingEndpointId = null,
                    message = "切断されました。"
                )
            }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val endpoint = NearbyEndpoint(endpointId, info.endpointName)
            val updated = _state.value.discoveredEndpoints
                .filterNot { it.endpointId == endpointId } + endpoint
            _state.value = _state.value.copy(discoveredEndpoints = updated)
        }

        override fun onEndpointLost(endpointId: String) {
            val updated = _state.value.discoveredEndpoints
                .filterNot { it.endpointId == endpointId }
            _state.value = _state.value.copy(discoveredEndpoints = updated)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                payloadBuffer[payload.id] = payload.asBytes() ?: return
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                val bytes = payloadBuffer.remove(update.payloadId) ?: return
                val text = String(bytes, Charsets.UTF_8)
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                receivedPayloadChannel.trySend(json)
            }
        }
    }

    private suspend fun performSync(endpointId: String) {
        try {
            val isInitiator = endpointInitiatorMap[endpointId] == true
            val localPayload = withContext(Dispatchers.IO) { repository.exportSyncPayload() }
            val outgoing = JSONObject()
                .put("type", MESSAGE_TYPE_SYNC)
                .put("payload", localPayload)
            connectionsClient.sendPayload(
                endpointId,
                Payload.fromBytes(outgoing.toString().toByteArray(Charsets.UTF_8))
            )

            val incoming = withTimeoutOrNull(20_000L) {
                receivedPayloadChannel.receive()
            } ?: throw Exception("タイムアウト: 相手からのデータを受信できませんでした。")

            if (incoming.optString("type") != MESSAGE_TYPE_SYNC) {
                throw Exception("不正なデータを受信しました。")
            }

            val remotePayload = incoming.getJSONObject("payload")
            val conflicts = detectNearbyConflicts(localPayload, remotePayload)

            if (conflicts.isNotEmpty() && !conflictAutoNewerFirst) {
                if (isInitiator) {
                    // 送信元のみユーザーに競合解決を委ねる
                    _state.value = _state.value.copy(
                        phase = NearbyPhase.CONFLICT,
                        message = "競合があります。",
                        pendingConflicts = conflicts,
                        pendingLocalPayload = localPayload.toString(),
                        pendingRemotePayload = remotePayload.toString()
                    )
                } else {
                    // 受信側は送信元の選択結果（確定マージ）を待つ
                    _state.value = _state.value.copy(
                        phase = NearbyPhase.SYNCING,
                        message = "相手端末の競合解決を待っています..."
                    )
                    applyMergedFromPeerAndVerify(endpointId)
                }
            } else {
                // 自動解決（競合なし or 新しい方優先設定）
                val resolutions = conflicts.associate { conflict ->
                    conflict.datasetKey to
                        if (conflict.remoteUpdatedAt > conflict.localUpdatedAt) SyncChoice.REMOTE else SyncChoice.LOCAL
                }
                applySyncAndVerify(endpointId, localPayload, remotePayload, resolutions, sendMergedToPeer = false)
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                phase = NearbyPhase.ERROR,
                message = "同期失敗: ${e.message}",
                isError = true
            )
        }
    }

    fun applyConflictResolutions(resolutions: Map<String, SyncChoice>) {
        val localStr = _state.value.pendingLocalPayload ?: return
        val remoteStr = _state.value.pendingRemotePayload ?: return
        val endpointId = _state.value.pendingEndpointId ?: return
        scope.launch {
            try {
                val local = JSONObject(localStr)
                val remote = JSONObject(remoteStr)
                applySyncAndVerify(endpointId, local, remote, resolutions, sendMergedToPeer = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    phase = NearbyPhase.ERROR,
                    message = "同期失敗: ${e.message}",
                    isError = true
                )
            }
        }
    }

    private suspend fun applySyncAndVerify(
        endpointId: String,
        localPayload: JSONObject,
        remotePayload: JSONObject,
        resolutions: Map<String, SyncChoice>,
        sendMergedToPeer: Boolean
    ) {
        val merged = withContext(Dispatchers.IO) {
            val merged = buildMergeWithResolutions(localPayload, remotePayload, resolutions)
            repository.applySyncPayload(merged)
            merged
        }

        if (sendMergedToPeer) {
            val mergedOutgoing = JSONObject()
                .put("type", MESSAGE_TYPE_MERGED)
                .put("payload", merged)
            connectionsClient.sendPayload(
                endpointId,
                Payload.fromBytes(mergedOutgoing.toString().toByteArray(Charsets.UTF_8))
            )
        }

        verifyConsistency(endpointId)
    }

    private suspend fun applyMergedFromPeerAndVerify(endpointId: String) {
        val mergedIncoming = withTimeoutOrNull(MERGED_WAIT_TIMEOUT_MS) {
            receivedPayloadChannel.receive()
        } ?: throw Exception("タイムアウト: 相手の競合解決結果を受信できませんでした。")

        if (mergedIncoming.optString("type") != MESSAGE_TYPE_MERGED) {
            throw Exception("不正な競合解決結果を受信しました。")
        }
        val mergedPayload = mergedIncoming.getJSONObject("payload")

        withContext(Dispatchers.IO) {
            repository.applySyncPayload(mergedPayload)
        }

        verifyConsistency(endpointId)
    }

    private suspend fun verifyConsistency(endpointId: String) {

        val verifyLocalPayload = withContext(Dispatchers.IO) { repository.exportSyncPayload() }
        val verifyOutgoing = JSONObject()
            .put("type", MESSAGE_TYPE_VERIFY)
            .put("payload", verifyLocalPayload)
        connectionsClient.sendPayload(
            endpointId,
            Payload.fromBytes(verifyOutgoing.toString().toByteArray(Charsets.UTF_8))
        )

        val verifyIncoming = withTimeoutOrNull(20_000L) {
            receivedPayloadChannel.receive()
        } ?: throw Exception("タイムアウト: 整合性確認データを受信できませんでした。")

        if (verifyIncoming.optString("type") != MESSAGE_TYPE_VERIFY) {
            throw Exception("不正な整合性確認データを受信しました。")
        }
        val verifyRemotePayload = verifyIncoming.getJSONObject("payload")
        if (!isConsistentPayload(verifyLocalPayload, verifyRemotePayload)) {
            throw Exception("整合性確認に失敗しました。再同期してください。")
        }

        _state.value = _state.value.copy(
            phase = NearbyPhase.DONE,
            message = "同期が完了しました。",
            pendingConflicts = emptyList(),
            pendingLocalPayload = null,
            pendingRemotePayload = null
        )
    }

    private fun isConsistentPayload(local: JSONObject, remote: JSONObject): Boolean {
        return datasets.all { key ->
            val localContent = local.opt(key)?.toString() ?: ""
            val remoteContent = remote.opt(key)?.toString() ?: ""
            localContent == remoteContent
        }
    }

    private val datasets = listOf(
        SchedulerRepository.DATASET_TASKS,
        SchedulerRepository.DATASET_PLANS,
        SchedulerRepository.DATASET_SCHEDULE_SETTINGS,
        SchedulerRepository.DATASET_LESSONS,
        SchedulerRepository.DATASET_DAY_TYPES,
        SchedulerRepository.DATASET_LONG_BREAKS,
        SchedulerRepository.DATASET_CANCELLED_LESSONS
    )

    private fun detectNearbyConflicts(local: JSONObject, remote: JSONObject): List<SyncConflict> {
        val localDeviceName = local.getJSONObject("device").optString("deviceName", "この端末")
        val remoteDeviceName = remote.getJSONObject("device").optString("deviceName", "相手端末")
        val localMeta = local.getJSONObject("metadata")
        val remoteMeta = remote.getJSONObject("metadata")
        val conflicts = mutableListOf<SyncConflict>()
        datasets.forEach { key ->
            val localContent = local.opt(key)?.toString() ?: ""
            val remoteContent = remote.opt(key)?.toString() ?: ""
            if (localContent == remoteContent) return@forEach
            val localTs = localMeta.getJSONObject(key).optLong("updatedAt", 0L)
            val remoteTs = remoteMeta.getJSONObject(key).optLong("updatedAt", 0L)
            // 手動解決モードでは、時刻判定より安全側で「内容差分があれば競合」にする。
            val shouldFlagConflict = if (!conflictAutoNewerFirst) true else (localTs > 0 && remoteTs > 0)
            if (shouldFlagConflict) {
                conflicts += SyncConflict(
                    datasetKey = key,
                    label = when (key) {
                        SchedulerRepository.DATASET_TASKS -> "課題"
                        SchedulerRepository.DATASET_PLANS -> "予定"
                        SchedulerRepository.DATASET_SCHEDULE_SETTINGS -> "時間割設定"
                        SchedulerRepository.DATASET_LESSONS -> "時間割"
                        SchedulerRepository.DATASET_DAY_TYPES -> "A/B表"
                        SchedulerRepository.DATASET_LONG_BREAKS -> "長期休み"
                        else -> key
                    },
                    localUpdatedAt = localTs,
                    remoteUpdatedAt = remoteTs,
                    localDeviceName = localDeviceName,
                    remoteDeviceName = remoteDeviceName
                )
            }
        }
        return conflicts
    }

    private fun buildMergeWithResolutions(
        local: JSONObject,
        remote: JSONObject,
        resolutions: Map<String, SyncChoice> = emptyMap()
    ): JSONObject {
        val merged = JSONObject(local.toString())
        val localMeta = local.getJSONObject("metadata")
        val remoteMeta = remote.getJSONObject("metadata")
        val newMeta = JSONObject()
        datasets.forEach { key ->
            val localTs = localMeta.getJSONObject(key).optLong("updatedAt", 0L)
            val remoteTs = remoteMeta.getJSONObject(key).optLong("updatedAt", 0L)
            val useRemote = when (
                resolutions[key]
                    ?: if (key == SchedulerRepository.DATASET_CANCELLED_LESSONS) resolutions[SchedulerRepository.DATASET_LESSONS] else null
            ) {
                SyncChoice.REMOTE -> true
                SyncChoice.LOCAL -> false
                null -> remoteTs > localTs
            }
            if (useRemote) {
                merged.put(key, remote.get(key))
                newMeta.put(key, remoteMeta.getJSONObject(key))
            } else {
                newMeta.put(key, localMeta.getJSONObject(key))
            }
        }
        merged.put("metadata", newMeta)
        return merged
    }
}
