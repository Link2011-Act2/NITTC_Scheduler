package jp.linkserver.nittcsc.sync

import android.net.wifi.WifiManager
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import jp.linkserver.nittcsc.data.AppDatabase
import jp.linkserver.nittcsc.data.SchedulerRepository
import jp.linkserver.nittcsc.data.SyncProfileEntity
import jp.linkserver.nittcsc.data.SyncRegisteredDeviceEntity
import jp.linkserver.nittcsc.data.SyncTrustedPeerEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.SequenceInputStream
import java.math.BigInteger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.ProviderException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal
import kotlin.math.abs
import kotlin.coroutines.resume
import kotlin.text.Charsets.UTF_8

data class DiscoveredSyncDevice(
    val deviceId: String,
    val userNickname: String,
    val deviceName: String,
    val host: String,
    val port: Int,
    val isRegistered: Boolean
)

enum class SyncChoice {
    LOCAL,
    REMOTE
}

data class SyncConflict(
    val datasetKey: String,
    val label: String,
    val localUpdatedAt: Long,
    val remoteUpdatedAt: Long,
    val localDeviceName: String,
    val remoteDeviceName: String
)

data class PreparedSyncSession(
    val target: DiscoveredSyncDevice,
    val authMode: String,
    val authValue: String,
    val remotePayload: String,
    val conflicts: List<SyncConflict>
)

data class SyncResult(
    val ok: Boolean,
    val message: String,
    val conflicts: List<SyncConflict> = emptyList(),
    val session: PreparedSyncSession? = null
)

data class DirectConnectResult(
    val ok: Boolean,
    val message: String,
    val device: DiscoveredSyncDevice? = null
)

data class SyncDiagnostics(
    val localAddresses: List<String> = emptyList(),
    val listeningPort: Int = 0,
    val serverRunning: Boolean = false,
    val recentLogs: List<String> = emptyList()
)

class LocalSyncManager(
    context: Context,
    private val repository: SchedulerRepository,
    private val db: AppDatabase
) {
    private val appContext = context.applicationContext
    private val dao = db.schedulerDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var serverSocket: ServerSocket? = null
    private var serverSslCtx: SSLContext? = null
    private var tcpPort: Int = 0
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null
    private val _diagnostics = MutableStateFlow(SyncDiagnostics())
    val diagnostics: StateFlow<SyncDiagnostics> = _diagnostics
    private val _incomingSyncEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val incomingSyncEvents: SharedFlow<String> = _incomingSyncEvents.asSharedFlow()
    private val usedAuthNonces = ConcurrentHashMap<String, Long>()
    private val authFailureStates = ConcurrentHashMap<String, AuthFailureState>()
    private val tlsHandshakeFailureStates = ConcurrentHashMap<String, AuthFailureState>()
    @Volatile private var cachedServerSslContext: SSLContext? = null
    @Volatile private var forcePlainServerForCompatibility: Boolean = false
    @Volatile private var transportSwitchInProgress: Boolean = false

    private data class AuthFailureState(
        var firstFailureAt: Long,
        var failureCount: Int,
        var blockedUntil: Long
    )

    private data class AuthValidationResult(
        val authValue: String?,
        val failureMessage: String? = null
    )

    companion object {
        private const val DISCOVERY_PORT = 46783
        const val DEFAULT_TCP_PORT = 46784
        private const val MAX_RECENT_LOGS = 500
        private const val DISCOVERY_MESSAGE = "NITTC_SYNC_DISCOVER_V1"
        private const val NSD_SERVICE_TYPE = "_nittc-sync._tcp."
        private const val TLS_KEY_ALIAS = "nittc-sync-tls-v6"
        private const val AUTH_TIMESTAMP_TOLERANCE_MS = 2 * 60_000L
        private const val AUTH_NONCE_TTL_MS = 5 * 60_000L
        private const val AUTH_FAILURE_WINDOW_MS = 60_000L
        private const val AUTH_FAILURE_LIMIT = 8
        private const val AUTH_BLOCK_MS = 60_000L
        private const val TLS_HANDSHAKE_FAIL_WINDOW_MS = 20_000L
        private const val TLS_HANDSHAKE_FAIL_LIMIT = 3
        private val TLS_PREFERRED_PROTOCOLS = arrayOf("TLSv1.2")
        private val TLS_PREFERRED_CIPHERS = arrayOf(
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
            "TLS_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_RSA_WITH_AES_128_CBC_SHA",
            "TLS_RSA_WITH_AES_256_CBC_SHA"
        )
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        ensureProfile()
        startServerIfNeeded()
        refreshDiagnostics()
    }

    fun shutdown() {
        scope.cancel()
        runCatching { serverSocket?.close() }
        serverSocket = null
        unregisterNsdService()
        appendLog("同期サーバーを停止しました。")
        refreshDiagnostics()
    }

    fun onTlsSettingChanged(enabled: Boolean) {
        scope.launch {
            appendLog("TLS通信設定を${if (enabled) "ON" else "OFF"}に変更しました。")
            forcePlainServerForCompatibility = false
            cachedServerSslContext = null
            runCatching { serverSocket?.close() }
            serverSocket = null
            unregisterNsdService()
            startServerIfNeeded()
            refreshDiagnostics()
        }
    }

    private suspend fun isTlsSyncEnabled(): Boolean {
        return dao.getSettings()?.enableTlsSync ?: false
    }

    suspend fun getProfile(): SyncProfileEntity = withContext(Dispatchers.IO) {
        ensureProfile()
    }

    suspend fun getListeningPort(): Int = withContext(Dispatchers.IO) {
        initialize()
        tcpPort
    }

    suspend fun runSelfConnectivityTest(): DirectConnectResult = withContext(Dispatchers.IO) {
        initialize()
        val targets = buildList {
            add("127.0.0.1")
            localAddresses().forEach { add(it) }
        }.distinct()

        var lastError = "自己接続テストに失敗しました。"
        for (host in targets) {
            val requestId = UUID.randomUUID().toString()
            val result = runCatching {
                sendRequest(
                    host,
                    tcpPort,
                    JSONObject()
                        .put("type", "hello")
                        .put("schema", "nittc-scheduler-sync")
                        .put("requestId", requestId)
                )
            }

            val response = result.getOrNull()
            if (
                response != null &&
                response.optString("schema") == "nittc-scheduler-sync" &&
                response.optString("requestId") == requestId
            ) {
                appendLog("自己接続テスト成功 $host:$tcpPort")
                return@withContext DirectConnectResult(true, "自己接続テスト成功: $host:$tcpPort")
            }

            if (response != null) {
                val raw = response.toString().take(200)
                appendLog(
                    "自己接続応答 $host:$tcpPort schema=${response.optString("schema")} requestId=${response.optString("requestId")} deviceId=${response.optString("deviceId")} raw=$raw"
                )
                lastError =
                    "想定外の応答です。schema=${response.optString("schema").ifBlank { "(空)" }} requestId=${response.optString("requestId").ifBlank { "(空)" }} raw=$raw"
            } else {
                lastError = result.exceptionOrNull()?.message ?: "応答を受信できませんでした。"
            }
            appendLog("自己接続テスト失敗 $host:$tcpPort - $lastError")
        }

        DirectConnectResult(false, "自己接続テスト失敗: $lastError")
    }

    suspend fun updateProfile(
        userNickname: String,
        deviceName: String,
        password: String,
        autoSyncEnabled: Boolean,
        conflictAutoNewerFirst: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val current = ensureProfile()
        dao.upsertSyncProfile(
            current.copy(
                userNickname = userNickname.trim(),
                deviceName = deviceName.trim(),
                passwordPlaintext = "",
                passwordHash = password.takeIf { it.isNotBlank() }?.let(::hashPassword) ?: current.passwordHash,
                passwordLength = password.takeIf { it.isNotBlank() }?.length ?: current.passwordLength,
                autoSyncEnabled = autoSyncEnabled,
                conflictAutoNewerFirst = conflictAutoNewerFirst
            )
        )
    }

    suspend fun discoverDevices(timeoutMs: Long = 1800L): List<DiscoveredSyncDevice> = withContext(Dispatchers.IO) {
        coroutineScope {
            initialize()
            appendLog("デバイス検索を開始しました。")
            val profile = ensureProfile()
            val registered = dao.getSyncRegisteredDevices().associateBy { it.deviceId }
            val found = linkedMapOf<String, DiscoveredSyncDevice>()
            discoverDevicesWithNsd(
                timeoutMs = timeoutMs,
                localDeviceId = profile.deviceId,
                registered = registered,
                found = found
            )
            withMulticastLock {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.soTimeout = 300
                    val bytes = DISCOVERY_MESSAGE.toByteArray()
                    val targets = discoveryTargets()
                    repeat(3) {
                        targets.forEach { target ->
                            runCatching {
                                socket.send(
                                    DatagramPacket(
                                        bytes,
                                        bytes.size,
                                        target,
                                        DISCOVERY_PORT
                                    )
                                )
                            }
                        }
                        delay(250)
                    }

                    val buffer = ByteArray(2048)
                    val endAt = System.currentTimeMillis() + timeoutMs
                    while (System.currentTimeMillis() < endAt) {
                        runCatching {
                            val responsePacket = DatagramPacket(buffer, buffer.size)
                            socket.receive(responsePacket)
                            val text = String(responsePacket.data, 0, responsePacket.length)
                            val json = JSONObject(text)
                            val deviceId = json.optString("deviceId")
                            if (deviceId.isBlank() || deviceId == profile.deviceId) return@runCatching
                            val host = json.optString("host").ifBlank { responsePacket.address.hostAddress }
                            val port = json.optInt("port", 0)
                            if (port <= 0) return@runCatching
                            found[deviceId] = DiscoveredSyncDevice(
                                deviceId = deviceId,
                                userNickname = json.optString("userNickname", ""),
                                deviceName = json.optString("deviceName", host),
                                host = host,
                                port = port,
                                isRegistered = registered.containsKey(deviceId)
                            )
                        }
                    }
                }
            }
            appendLog("デバイス検索を終了しました。検出: ${found.size} 台")
            found.values.sortedWith(compareBy<DiscoveredSyncDevice> { !it.isRegistered }.thenBy { it.deviceName.lowercase() })
        }
    }

    suspend fun registerTrustedDevice(device: DiscoveredSyncDevice, password: String): SyncResult = withContext(Dispatchers.IO) {
        initialize()
        val localProfile = ensureProfile()
        val request = JSONObject().also {
            it.put("type", "register")
            it.put("passwordHash", hashPassword(password))
            it.put("peerDeviceId", localProfile.deviceId)
            it.put("peerUserNickname", localProfile.userNickname)
            it.put("peerDeviceName", localProfile.deviceName)
        }
        var targetHost = device.host
        var targetPort = device.port
        val (response, certFingerprint) = runCatching {
            sendRequestWithFingerprint(targetHost, targetPort, request)
        }.recoverCatching { firstError ->
            appendLog("register接続失敗(${targetHost}:${targetPort})。deviceIdで再探索します: ${firstError.message}")
            val refreshed = refreshHostByDeviceId(device.deviceId, device.port)
                ?: throw firstError
            targetHost = refreshed.first
            targetPort = refreshed.second
            appendLog("register再試行先 ${device.host}:${device.port} -> $targetHost:$targetPort")
            sendRequestWithFingerprint(targetHost, targetPort, request)
        }.getOrElse { e ->
            return@withContext SyncResult(false, "登録に失敗しました: ${e.message ?: "接続エラー"}")
        }
        if (!response.optBoolean("ok", false)) {
            return@withContext SyncResult(false, response.optString("message", "登録に失敗しました。"))
        }
        val token = response.optString("trustToken", "")
        if (token.isBlank()) {
            return@withContext SyncResult(false, "信頼トークンを取得できませんでした。")
        }
        val now = System.currentTimeMillis()
        dao.upsertSyncRegisteredDevice(
            SyncRegisteredDeviceEntity(
                deviceId = device.deviceId,
                userNickname = device.userNickname,
                deviceName = device.deviceName,
                host = targetHost,
                port = targetPort,
                trustToken = token,
                serverCertFingerprint = certFingerprint,
                addedAt = now,
                lastSeenAt = now
            )
        )
        SyncResult(true, "自分のデバイスとして登録しました。")
    }

    suspend fun removeTrustedDevice(deviceId: String): SyncResult = withContext(Dispatchers.IO) {
        val existing = dao.getSyncRegisteredDevice(deviceId)
            ?: return@withContext SyncResult(false, "登録済みデバイスが見つかりません。")
        dao.deleteSyncRegisteredDevice(deviceId)
        SyncResult(true, "${existing.deviceName} を登録済みデバイスから削除しました。")
    }

    suspend fun preparePasswordSync(device: DiscoveredSyncDevice, password: String): SyncResult = withContext(Dispatchers.IO) {
        prepareSync(device, authMode = "password", authValue = hashPassword(password))
    }

    suspend fun prepareTrustedSync(deviceId: String): SyncResult = withContext(Dispatchers.IO) {
        val device = dao.getSyncRegisteredDevice(deviceId)
            ?: return@withContext SyncResult(false, "登録済みデバイスが見つかりません。")

        val savedTarget = DiscoveredSyncDevice(
            deviceId = device.deviceId,
            userNickname = device.userNickname,
            deviceName = device.deviceName,
            host = device.host,
            port = device.port,
            isRegistered = true
        )

        // まず保存済み IP で接続を試みる
        val primaryResult = runCatching {
            prepareSync(
                savedTarget,
                authMode = "trust",
                authValue = device.trustToken
            )
        }.getOrElse {
            appendLog("保存済みIP接続エラー(${device.host}:${device.port}): ${it.message ?: "不明なエラー"}")
            null
        }
        if (primaryResult?.ok == true) return@withContext primaryResult

        // 接続エラーだけでなく失敗応答（認証失敗など）でも、IPが変わっている可能性を考慮して再検索
        if (primaryResult != null) {
            appendLog("保存済みIPで同期準備失敗(${device.host}:${device.port}): ${primaryResult.message}")
        }
        appendLog("保存済みIP接続失敗(${device.host}:${device.port})。IP再検索中...")
        val refreshed = refreshHostByDeviceId(deviceId, device.port)
            ?: run {
                appendLog("deviceId一致で未検出。端末名/ユーザー名ヒントで再探索します...")
                refreshHostByIdentityHint(device)
            }
        if (refreshed == null) {
            appendLog("IP再検索失敗。デバイス: ${device.deviceName}")
            return@withContext primaryResult
                ?: SyncResult(false, "相手端末 (${device.deviceName}) に接続できませんでした。IPアドレスが変わっている可能性があります。")
        }

        // DB の host を最新 IP で更新
        dao.upsertSyncRegisteredDevice(device.copy(host = refreshed.first, port = refreshed.second, lastSeenAt = System.currentTimeMillis()))
        appendLog("IP更新: ${device.host} → ${refreshed.first}:${refreshed.second}")

        val refreshedTarget = DiscoveredSyncDevice(
            deviceId = device.deviceId,
            userNickname = device.userNickname,
            deviceName = device.deviceName,
            host = refreshed.first,
            port = refreshed.second,
            isRegistered = true
        )
        val retryResult = prepareSync(
            refreshedTarget,
            authMode = "trust",
            authValue = device.trustToken
        )
        if (!retryResult.ok && primaryResult != null && primaryResult.message.isNotBlank()) {
            return@withContext SyncResult(false, "${retryResult.message}（保存済みIP時: ${primaryResult.message}）")
        }
        retryResult
    }

    /** NSD + UDP ブロードキャストで targetDeviceId を持つ端末の最新 (host, port) を返す。見つからなければ null。 */
    private suspend fun refreshHostByDeviceId(targetDeviceId: String, fallbackPort: Int): Pair<String, Int>? = coroutineScope {
        initialize()
        val profile = ensureProfile()
        val registered = mapOf(targetDeviceId to (dao.getSyncRegisteredDevice(targetDeviceId) ?: return@coroutineScope null))
        val found = linkedMapOf<String, DiscoveredSyncDevice>()

        // NSD で探す（短めのタイムアウト）
        discoverDevicesWithNsd(
            timeoutMs = 1500L,
            localDeviceId = profile.deviceId,
            registered = registered,
            found = found
        )
        found[targetDeviceId]?.let { return@coroutineScope it.host to it.port }

        // UDP ブロードキャストでも探す
        withMulticastLock {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 300
                val bytes = DISCOVERY_MESSAGE.toByteArray()
                val targets = discoveryTargets()
                repeat(3) {
                    targets.forEach { target ->
                        runCatching { socket.send(DatagramPacket(bytes, bytes.size, target, DISCOVERY_PORT)) }
                    }
                    delay(200)
                }
                val buffer = ByteArray(2048)
                val endAt = System.currentTimeMillis() + 1200L
                while (System.currentTimeMillis() < endAt) {
                    runCatching {
                        val pkt = DatagramPacket(buffer, buffer.size)
                        socket.receive(pkt)
                        val json = JSONObject(String(pkt.data, 0, pkt.length))
                        if (json.optString("deviceId") == targetDeviceId) {
                            val host = json.optString("host").ifBlank { pkt.address.hostAddress }
                            val port = json.optInt("port", fallbackPort)
                            found[targetDeviceId] = DiscoveredSyncDevice(targetDeviceId, "", "", host, port, true)
                        }
                    }
                }
            }
        }
        found[targetDeviceId]?.let { it.host to it.port }
    }

    private suspend fun refreshHostByIdentityHint(target: SyncRegisteredDeviceEntity): Pair<String, Int>? = coroutineScope {
        val candidates = discoverDevices(timeoutMs = 1500L)
        if (candidates.isEmpty()) return@coroutineScope null

        val deviceName = target.deviceName.trim()
        val userNickname = target.userNickname.trim()
        val matched = candidates.filter {
            (deviceName.isNotBlank() && it.deviceName.equals(deviceName, ignoreCase = true)) ||
                (userNickname.isNotBlank() && it.userNickname.equals(userNickname, ignoreCase = true))
        }

        if (matched.isEmpty()) {
            appendLog("ヒント再探索: 一致候補なし（deviceName=${target.deviceName}, userNickname=${target.userNickname}）")
            return@coroutineScope null
        }

        val preferred = matched.firstOrNull {
            deviceName.isNotBlank() && userNickname.isNotBlank() &&
                it.deviceName.equals(deviceName, ignoreCase = true) &&
                it.userNickname.equals(userNickname, ignoreCase = true)
        } ?: matched.first()

        appendLog("ヒント再探索で候補発見: ${preferred.deviceName} ${preferred.host}:${preferred.port}")
        preferred.host to preferred.port
    }

    suspend fun connectToHost(host: String, port: Int = DEFAULT_TCP_PORT): DirectConnectResult = withContext(Dispatchers.IO) {
        initialize()
        val profile = ensureProfile()
        val registered = dao.getSyncRegisteredDevices().associateBy { it.deviceId }
        val normalizedHost = host.trim()
        if (normalizedHost.isBlank()) {
            return@withContext DirectConnectResult(false, "IPアドレスを入力してください。")
        }
        val response = runCatching {
            sendRequest(
                normalizedHost,
                port,
                JSONObject()
                    .put("type", "hello")
                    .put("schema", "nittc-scheduler-sync")
                    .put("requestId", UUID.randomUUID().toString())
            )
        }.getOrElse {
            appendLog("手動接続失敗 $normalizedHost:$port - ${it.message ?: "不明なエラー"}")
            return@withContext DirectConnectResult(false, "接続できませんでした: ${it.message ?: "タイムアウトまたは到達不可"}")
        }

        if (response.optString("schema") != "nittc-scheduler-sync") {
            appendLog("手動接続応答 raw=${response.toString().take(200)}")
            appendLog("手動接続失敗 $normalizedHost:$port - 同期サーバー応答ではない")
            return@withContext DirectConnectResult(false, "相手から同期サーバーとしての応答が返りませんでした。")
        }
        val deviceId = response.optString("deviceId")
        if (deviceId.isBlank()) {
            appendLog("手動接続失敗 $normalizedHost:$port - hello 応答不正")
            return@withContext DirectConnectResult(false, "相手端末から正しい応答が返りませんでした。")
        }
        if (deviceId == profile.deviceId) {
            appendLog("手動接続失敗 $normalizedHost:$port - 自分自身")
            return@withContext DirectConnectResult(false, "自分自身の端末に接続しています。")
        }
        val device = DiscoveredSyncDevice(
            deviceId = deviceId,
            userNickname = response.optString("userNickname", ""),
            deviceName = response.optString("deviceName", normalizedHost),
            host = normalizedHost,
            port = response.optInt("port", port),
            isRegistered = registered.containsKey(deviceId)
        )
        appendLog("手動接続成功 ${device.deviceName} ($normalizedHost:${device.port})")
        DirectConnectResult(true, "${device.deviceName} に接続しました。", device)
    }

    suspend fun applyPreparedSync(
        session: PreparedSyncSession,
        resolutions: Map<String, SyncChoice>
    ): SyncResult = withContext(Dispatchers.IO) {
        val localPayload = repository.exportSyncPayload()
        val remotePayload = JSONObject(session.remotePayload)
        val merged = buildMergedPayload(localPayload, remotePayload, session.target, resolutions)
        repository.applySyncPayload(merged)
        val expectedFingerprint = dao.getSyncRegisteredDevice(session.target.deviceId)
            ?.serverCertFingerprint?.takeIf { it.isNotBlank() }
        val applyResponse = runCatching {
            sendRequest(
                session.target.host,
                session.target.port,
                JSONObject().also {
                    it.put("type", "apply")
                    putAuth(it, session.authMode, session.authValue)
                    it.put("payload", merged)
                },
                expectedFingerprint
            )
        }.getOrElse { e ->
            appendLog("apply送信エラー ${session.target.host}:${session.target.port} - ${e.message}")
            return@withContext SyncResult(false, "相手端末への反映に失敗しました: ${e.message ?: "接続エラー"}")
        }
        if (!applyResponse.optBoolean("ok", false)) {
            return@withContext SyncResult(false, applyResponse.optString("message", "リモート端末への反映に失敗しました。"))
        }
        updateRegisteredDeviceAfterSync(session.target, merged)
        SyncResult(true, "同期が完了しました。")
    }

    suspend fun runAutoSync(): SyncResult = withContext(Dispatchers.IO) {
        val profile = ensureProfile()
        if (!profile.autoSyncEnabled) return@withContext SyncResult(true, "")
        val devices = dao.getSyncRegisteredDevices()
        if (devices.isEmpty()) return@withContext SyncResult(true, "")

        var synced = 0
        devices.forEach { device ->
            val result = prepareTrustedSync(device.deviceId)
            if (!result.ok || result.session == null) return@forEach
            if (result.conflicts.isNotEmpty()) return@forEach
            val applied = applyPreparedSync(result.session, emptyMap())
            if (applied.ok) synced++
        }
        SyncResult(true, if (synced > 0) "$synced 台と自動同期しました。" else "")
    }

    private suspend fun prepareSync(device: DiscoveredSyncDevice, authMode: String, authValue: String): SyncResult {
        initialize()
        val storedDevice = dao.getSyncRegisteredDevice(device.deviceId)
        val expectedFingerprint = storedDevice?.serverCertFingerprint?.takeIf { it.isNotBlank() }
        var targetHost = device.host
        var targetPort = device.port
        val snapshotPayload = JSONObject().also {
            it.put("type", "snapshot")
            putAuth(it, authMode, authValue)
        }
        val (remoteResponse, certFingerprint) = runCatching {
            sendRequestWithFingerprint(
                targetHost,
                targetPort,
                snapshotPayload,
                expectedFingerprint
            )
        }.recoverCatching { firstError ->
            appendLog("snapshot取得失敗(${targetHost}:${targetPort})。deviceIdで再探索します: ${firstError.message}")
            val refreshed = refreshHostByDeviceId(device.deviceId, device.port)
                ?: storedDevice?.let {
                    appendLog("snapshot: deviceId一致で未検出。端末名/ユーザー名ヒントで再探索します...")
                    refreshHostByIdentityHint(it)
                }
                ?: throw firstError
            targetHost = refreshed.first
            targetPort = refreshed.second
            appendLog("snapshot再試行先 ${device.host}:${device.port} -> $targetHost:$targetPort")
            sendRequestWithFingerprint(
                targetHost,
                targetPort,
                snapshotPayload,
                expectedFingerprint
            )
        }.getOrElse { e ->
            appendLog("snapshot取得エラー ${device.host}:${device.port} - ${e.message}")
            return SyncResult(false, "同期先に接続できませんでした: ${e.message ?: "接続エラー"}")
        }

        if (targetHost != device.host || targetPort != device.port) {
            storedDevice?.let {
                dao.upsertSyncRegisteredDevice(
                    it.copy(
                        host = targetHost,
                        port = targetPort,
                        lastSeenAt = System.currentTimeMillis()
                    )
                )
            }
        }
        if (!remoteResponse.optBoolean("ok", false)) {
            return SyncResult(false, remoteResponse.optString("message", "同期先に接続できませんでした。"))
        }
        // TOFU: 登録済み端末で初回TLS接続時（アップグレード直後など）にフィンガープリントを保存
        if (expectedFingerprint == null && certFingerprint.isNotBlank() && storedDevice != null) {
            dao.upsertSyncRegisteredDevice(storedDevice.copy(serverCertFingerprint = certFingerprint))
            appendLog("TLSフィンガープリントを保存: ${device.deviceName}")
        }
        val remotePayload = remoteResponse.getJSONObject("payload")
        val localPayload = repository.exportSyncPayload()
        val conflicts = detectConflicts(localPayload, remotePayload, device.deviceId)
        val session = PreparedSyncSession(
            target = device,
            authMode = authMode,
            authValue = authValue,
            remotePayload = remotePayload.toString(),
            conflicts = conflicts
        )
        return if (conflicts.isEmpty()) {
            SyncResult(true, "同期を準備しました。", session = session)
        } else {
            SyncResult(true, "競合があります。", conflicts = conflicts, session = session)
        }
    }

    private suspend fun detectConflicts(
        localPayload: JSONObject,
        remotePayload: JSONObject,
        remoteDeviceId: String
    ): List<SyncConflict> {
        val registered = dao.getSyncRegisteredDevice(remoteDeviceId)
        val localDeviceName = localPayload.getJSONObject("device").optString("deviceName", "この端末")
        val remoteDeviceName = remotePayload.getJSONObject("device").optString("deviceName", "相手端末")
        val conflicts = mutableListOf<SyncConflict>()
        listOf(
            SchedulerRepository.DATASET_TASKS,
            SchedulerRepository.DATASET_PLANS,
            SchedulerRepository.DATASET_SCHEDULE_SETTINGS,
            SchedulerRepository.DATASET_LESSONS,
            SchedulerRepository.DATASET_DAY_TYPES,
            SchedulerRepository.DATASET_LONG_BREAKS
        ).forEach { key ->
            val localContent = localPayload.opt(key)?.toString() ?: ""
            val remoteContent = remotePayload.opt(key)?.toString() ?: ""
            if (localContent == remoteContent) return@forEach

            val localUpdatedAt = localPayload.getJSONObject("metadata").getJSONObject(key).optLong("updatedAt", 0L)
            val remoteUpdatedAt = remotePayload.getJSONObject("metadata").getJSONObject(key).optLong("updatedAt", 0L)
            val lastSyncedAt = registered?.let {
                when (key) {
                    SchedulerRepository.DATASET_TASKS -> it.lastTasksSyncAt
                    SchedulerRepository.DATASET_PLANS -> it.lastPlansSyncAt
                    SchedulerRepository.DATASET_SCHEDULE_SETTINGS -> it.lastScheduleSettingsSyncAt
                    SchedulerRepository.DATASET_LESSONS -> it.lastLessonsSyncAt
                    SchedulerRepository.DATASET_DAY_TYPES -> it.lastDayTypesSyncAt
                    SchedulerRepository.DATASET_LONG_BREAKS -> it.lastLongBreaksSyncAt
                    else -> 0L
                }
            } ?: 0L
            val localChanged = localUpdatedAt > lastSyncedAt
            val remoteChanged = remoteUpdatedAt > lastSyncedAt
            if (localChanged && remoteChanged) {
                conflicts += SyncConflict(
                    datasetKey = key,
                    label = datasetLabel(key),
                    localUpdatedAt = localUpdatedAt,
                    remoteUpdatedAt = remoteUpdatedAt,
                    localDeviceName = localDeviceName,
                    remoteDeviceName = remoteDeviceName
                )
            }
        }
        return conflicts
    }

    private fun buildMergedPayload(
        localPayload: JSONObject,
        remotePayload: JSONObject,
        target: DiscoveredSyncDevice,
        resolutions: Map<String, SyncChoice>
    ): JSONObject {
        val merged = JSONObject(localPayload.toString())
        val metadata = JSONObject()
        val now = System.currentTimeMillis()
        val localMeta = localPayload.getJSONObject("metadata")
        val remoteMeta = remotePayload.getJSONObject("metadata")

        listOf(
            SchedulerRepository.DATASET_TASKS,
            SchedulerRepository.DATASET_PLANS,
            SchedulerRepository.DATASET_SCHEDULE_SETTINGS,
            SchedulerRepository.DATASET_LESSONS,
            SchedulerRepository.DATASET_DAY_TYPES,
            SchedulerRepository.DATASET_LONG_BREAKS,
            SchedulerRepository.DATASET_CANCELLED_LESSONS
        ).forEach { key ->
            val localUpdatedAt = localMeta.getJSONObject(key).optLong("updatedAt", 0L)
            val remoteUpdatedAt = remoteMeta.getJSONObject(key).optLong("updatedAt", 0L)
            val choice = (resolutions[key]
                ?: if (key == SchedulerRepository.DATASET_CANCELLED_LESSONS) resolutions[SchedulerRepository.DATASET_LESSONS] else null)
                ?: if (remoteUpdatedAt > localUpdatedAt) SyncChoice.REMOTE else SyncChoice.LOCAL
            val sourcePayload = if (choice == SyncChoice.LOCAL) localPayload else remotePayload
            val sourceMeta = if (choice == SyncChoice.LOCAL) localMeta else remoteMeta
            merged.put(key, sourcePayload.get(key))
            metadata.put(
                key,
                JSONObject().also { obj ->
                    obj.put("updatedAt", maxOf(now, sourceMeta.getJSONObject(key).optLong("updatedAt", 0L)))
                    obj.put("updatedByDeviceId", sourceMeta.getJSONObject(key).optString("updatedByDeviceId", if (choice == SyncChoice.LOCAL) "" else target.deviceId))
                }
            )
        }

        merged.put("metadata", metadata)
        return merged
    }

    private suspend fun updateRegisteredDeviceAfterSync(target: DiscoveredSyncDevice, mergedPayload: JSONObject) {
        val existing = dao.getSyncRegisteredDevice(target.deviceId) ?: return
        val meta = mergedPayload.getJSONObject("metadata")
        dao.upsertSyncRegisteredDevice(
            existing.copy(
                userNickname = target.userNickname,
                deviceName = target.deviceName,
                host = target.host,
                port = target.port,
                lastSeenAt = System.currentTimeMillis(),
                lastTasksSyncAt = meta.getJSONObject(SchedulerRepository.DATASET_TASKS).optLong("updatedAt", existing.lastTasksSyncAt),
                lastPlansSyncAt = meta.getJSONObject(SchedulerRepository.DATASET_PLANS).optLong("updatedAt", existing.lastPlansSyncAt),
                lastScheduleSettingsSyncAt = meta.getJSONObject(SchedulerRepository.DATASET_SCHEDULE_SETTINGS).optLong("updatedAt", existing.lastScheduleSettingsSyncAt),
                lastLessonsSyncAt = meta.getJSONObject(SchedulerRepository.DATASET_LESSONS).optLong("updatedAt", existing.lastLessonsSyncAt),
                lastDayTypesSyncAt = meta.getJSONObject(SchedulerRepository.DATASET_DAY_TYPES).optLong("updatedAt", existing.lastDayTypesSyncAt),
                lastLongBreaksSyncAt = meta.getJSONObject(SchedulerRepository.DATASET_LONG_BREAKS).optLong("updatedAt", existing.lastLongBreaksSyncAt),
                lastCancelledLessonsSyncAt = meta.getJSONObject(SchedulerRepository.DATASET_CANCELLED_LESSONS).optLong("updatedAt", existing.lastCancelledLessonsSyncAt)
            )
        )
    }

    private suspend fun startServerIfNeeded() {
        if (serverSocket != null) return
        val tlsEnabled = isTlsSyncEnabled() && !forcePlainServerForCompatibility
        val sslCtx = if (tlsEnabled) getOrCreateServerSslContextOrNull() else null
        serverSslCtx = sslCtx
        val socket = if (sslCtx != null) {
            runCatching { sslCtx.serverSocketFactory.createServerSocket(DEFAULT_TCP_PORT) }
                .getOrElse { sslCtx.serverSocketFactory.createServerSocket(0) }
        } else {
            runCatching { ServerSocket(DEFAULT_TCP_PORT) }
                .getOrElse { ServerSocket(0) }
        }
        configureTlsServerSocket(socket)
        serverSocket = socket
        tcpPort = socket.localPort
        appendLog("同期サーバー待受開始 port=$tcpPort tls=${sslCtx != null}${if (forcePlainServerForCompatibility) " compat=plain" else ""}")
        refreshDiagnostics()
        unregisterNsdService()
        registerNsdService()

        scope.launch {
            while (isActive) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                appendLog("着信を受け付けました: ${client.inetAddress.hostAddress}")
                launch { handleClient(client) }
            }
        }

        scope.launch {
            withMulticastLock {
                openDiscoverySocket().use { socket ->
                    socket.broadcast = true
                    val buffer = ByteArray(512)
                    while (isActive) {
                        runCatching {
                            val packet = DatagramPacket(buffer, buffer.size)
                            socket.receive(packet)
                            val text = String(packet.data, 0, packet.length)
                            if (text != DISCOVERY_MESSAGE) return@runCatching
                            val profile = ensureProfile()
                            val response = JSONObject().also {
                                it.put("deviceId", profile.deviceId)
                                it.put("userNickname", profile.userNickname)
                                it.put("deviceName", profile.deviceName)
                                it.put("port", tcpPort)
                                it.put("host", "")
                            }.toString().toByteArray()
                            socket.send(DatagramPacket(response, response.size, packet.address, packet.port))
                            appendLog("検索応答を返しました: ${packet.address.hostAddress}:${packet.port}")
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 10_000
            if (client is SSLSocket) {
                val handshakeOk = runCatching { client.startHandshake() }.isSuccess
                if (!handshakeOk) {
                    val remoteAddress = client.inetAddress?.hostAddress ?: "unknown"
                    appendLog("TLSハンドシェイク失敗 $remoteAddress")
                    onTlsHandshakeFailed(remoteAddress)
                    return
                }
            }

            client.soTimeout = 3_000
            val remoteAddress = client.inetAddress?.hostAddress ?: "unknown"
            val requestText = BufferedReader(InputStreamReader(client.getInputStream(), UTF_8)).readLine().orEmpty()
            if (!requestText.trimStart().startsWith("{")) {
                appendLog("非JSONリクエストを無視: $remoteAddress head=${requestText.take(16)}")
                return
            }
            val requestType = runCatching { JSONObject(requestText).optString("type", "(不明)") }.getOrDefault("(不明)")
            appendLog("受信: $remoteAddress type=$requestType")
            val response = runCatching { processRequest(JSONObject(requestText), remoteAddress) }
                .getOrElse {
                    appendLog("処理失敗: ${it.message ?: "不明なエラー"}")
                    JSONObject().also { obj ->
                        obj.put("ok", false)
                        obj.put("message", it.message ?: "不明なエラー")
                    }
                }
            BufferedWriter(OutputStreamWriter(client.getOutputStream(), UTF_8)).use { writer ->
                writer.write(response.toString())
                writer.newLine()
                writer.flush()
            }
            appendLog("応答送信: $remoteAddress")
        }
    }

    private fun onTlsHandshakeFailed(remoteAddress: String) {
        if (forcePlainServerForCompatibility) return
        val now = System.currentTimeMillis()
        val state = tlsHandshakeFailureStates[remoteAddress]
        if (state == null || now - state.firstFailureAt > TLS_HANDSHAKE_FAIL_WINDOW_MS) {
            tlsHandshakeFailureStates[remoteAddress] = AuthFailureState(
                firstFailureAt = now,
                failureCount = 1,
                blockedUntil = 0L
            )
            return
        }

        state.failureCount += 1
        tlsHandshakeFailureStates[remoteAddress] = state
        if (state.failureCount < TLS_HANDSHAKE_FAIL_LIMIT || transportSwitchInProgress) return

        transportSwitchInProgress = true
        forcePlainServerForCompatibility = true
        appendLog("TLS互換性問題を検出。平文待受へ自動切替します。")
        scope.launch {
            runCatching { serverSocket?.close() }
            serverSocket = null
            unregisterNsdService()
            startServerIfNeeded()
            refreshDiagnostics()
            transportSwitchInProgress = false
        }
    }

    private suspend fun processRequest(request: JSONObject, remoteAddress: String): JSONObject {
        return when (request.optString("type")) {
            "hello" -> {
                if (request.optString("schema") != "nittc-scheduler-sync") {
                    return JSONObject().put("ok", false).put("message", "未対応のリクエストです。")
                }
                val profile = ensureProfile()
                JSONObject()
                    .put("ok", true)
                    .put("schema", "nittc-scheduler-sync")
                    .put("deviceId", profile.deviceId)
                    .put("userNickname", profile.userNickname)
                    .put("deviceName", profile.deviceName)
                    .put("port", tcpPort)
                    .put("requestId", request.optString("requestId"))
            }
            "snapshot" -> {
                val auth = validateAuth(request, remoteAddress)
                if (auth.authValue == null) {
                    JSONObject().put("ok", false).put("message", auth.failureMessage ?: authFailureMessage(request))
                } else {
                    JSONObject().put("ok", true).put("payload", repository.exportSyncPayload())
                }
            }

            "apply" -> {
                val auth = validateAuth(request, remoteAddress)
                if (auth.authValue == null) {
                    JSONObject().put("ok", false).put("message", auth.failureMessage ?: authFailureMessage(request))
                } else {
                    repository.applySyncPayload(request.getJSONObject("payload"))
                    val peerName = if (request.optString("authMode") == "trust") {
                        val peer = dao.getSyncTrustedPeerByToken(request.optString("authValue"))
                        peer?.peerDeviceName?.takeIf { it.isNotBlank() }
                            ?: peer?.peerUserNickname?.takeIf { it.isNotBlank() }
                    } else null
                    _incomingSyncEvents.tryEmit(peerName ?: "")
                    JSONObject().put("ok", true)
                }
            }

            "register" -> {
                val profile = ensureProfile()
                val passwordHash = request.optString("passwordHash")
                if (profile.passwordHash.isBlank()) {
                    JSONObject().put("ok", false).put("message", "この端末の同期パスワードが未設定です。同期設定でユーザー名とパスワードを設定してください。")
                } else if (passwordHash != profile.passwordHash) {
                    JSONObject().put("ok", false).put("message", "パスワードが一致しません。")
                } else {
                    val token = UUID.randomUUID().toString()
                    val now = System.currentTimeMillis()
                    dao.upsertSyncTrustedPeer(
                        SyncTrustedPeerEntity(
                            peerDeviceId = request.optString("peerDeviceId"),
                            peerUserNickname = request.optString("peerUserNickname"),
                            peerDeviceName = request.optString("peerDeviceName"),
                            trustToken = token,
                            issuedAt = now,
                            lastUsedAt = now
                        )
                    )
                    JSONObject().put("ok", true).put("trustToken", token)
                }
            }

            else -> JSONObject().put("ok", false).put("message", "未対応のリクエストです。")
        }
    }

    private suspend fun authFailureMessage(request: JSONObject): String {
        val profile = ensureProfile()
        return when (request.optString("authMode")) {
            "password" -> {
                if (profile.passwordHash.isBlank()) {
                    "相手端末で同期パスワードが未設定です。相手端末の同期設定でユーザー名とパスワードを設定してから再試行してください。"
                } else {
                    "パスワード認証に失敗しました。入力したパスワードを確認してください。"
                }
            }
            "trust" -> {
                if (profile.passwordHash.isBlank()) {
                    "相手端末で同期パスワードが未設定です。相手端末の同期設定を完了してから、再度デバイス登録を行ってください。"
                } else {
                    "信頼済み端末の認証に失敗しました。デバイス登録をやり直すか、パスワード同期を実行してください。"
                }
            }
            else -> "認証に失敗しました。"
        }
    }

    private suspend fun validateAuth(request: JSONObject, remoteAddress: String): AuthValidationResult {
        val now = System.currentTimeMillis()
        val blockedUntil = authFailureStates[remoteAddress]?.blockedUntil ?: 0L
        if (blockedUntil > now) {
            return AuthValidationResult(
                authValue = null,
                failureMessage = "認証失敗が続いたため一時的にブロック中です。しばらく待ってから再試行してください。"
            )
        }

        // 新しいクライアントでは timestamp + nonce を必ず送信。古いクライアントとの互換のため未送信時は警告のみ。
        val authTimestamp = request.optLong("authTimestamp", 0L)
        val authNonce = request.optString("authNonce")
        if (authTimestamp > 0L) {
            if (abs(now - authTimestamp) > AUTH_TIMESTAMP_TOLERANCE_MS) {
                recordAuthFailure(remoteAddress, now)
                return AuthValidationResult(authValue = null, failureMessage = "認証情報の有効期限が切れています。再試行してください。")
            }
            if (authNonce.isBlank()) {
                recordAuthFailure(remoteAddress, now)
                return AuthValidationResult(authValue = null, failureMessage = "認証情報が不正です。")
            }
            cleanupExpiredAuthNonces(now)
            val nonceKey = "${request.optString("authMode")}|${request.optString("peerDeviceId")}|$authNonce"
            val previous = usedAuthNonces.putIfAbsent(nonceKey, now)
            if (previous != null) {
                recordAuthFailure(remoteAddress, now)
                return AuthValidationResult(authValue = null, failureMessage = "同じ認証リクエストが再利用されました。")
            }
        }

        val profile = ensureProfile()
        val result = when (request.optString("authMode")) {
            "password" -> request.optString("authValue").takeIf { it.isNotBlank() && it == profile.passwordHash }
            "trust" -> {
                val token = request.optString("authValue")
                val peer = dao.getSyncTrustedPeerByToken(token)
                val peerDeviceId = request.optString("peerDeviceId")
                if (peer != null && token == peer.trustToken && (peerDeviceId.isBlank() || peerDeviceId == peer.peerDeviceId)) token else null
            }

            else -> null
        }
        return if (result != null) {
            authFailureStates.remove(remoteAddress)
            AuthValidationResult(authValue = result)
        } else {
            recordAuthFailure(remoteAddress, now)
            AuthValidationResult(authValue = null)
        }
    }

    private fun recordAuthFailure(remoteAddress: String, now: Long) {
        val state = authFailureStates[remoteAddress]
        if (state == null || now - state.firstFailureAt > AUTH_FAILURE_WINDOW_MS) {
            authFailureStates[remoteAddress] = AuthFailureState(
                firstFailureAt = now,
                failureCount = 1,
                blockedUntil = 0L
            )
            return
        }
        state.failureCount += 1
        if (state.failureCount >= AUTH_FAILURE_LIMIT) {
            state.blockedUntil = now + AUTH_BLOCK_MS
        }
        authFailureStates[remoteAddress] = state
    }

    private fun cleanupExpiredAuthNonces(now: Long) {
        usedAuthNonces.entries.removeIf { (_, usedAt) -> now - usedAt > AUTH_NONCE_TTL_MS }
    }

    private val ACCEPT_ALL_TRUST_MANAGER = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
    }

    private fun getOrCreateServerSslContextOrNull(): SSLContext? {
        cachedServerSslContext?.let { return it }
        synchronized(this) {
            cachedServerSslContext?.let { return it }
            return runCatching { buildKeystoreSslContext() }
                .onSuccess {
                    appendLog("TLS初期化成功 alias=$TLS_KEY_ALIAS keyManagers=${it.socketFactory}")
                }
                .onFailure { e ->
                    appendLog("TLS初期化失敗(${e.javaClass.simpleName}): ${e.message}")
                    e.cause?.let { cause -> appendLog("  caused by(${cause.javaClass.simpleName}): ${cause.message}") }
                }
                .getOrNull()
                ?.also { cachedServerSslContext = it }
        }
    }

    private fun buildKeystoreSslContext(): SSLContext {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(TLS_KEY_ALIAS)) {
            val now = System.currentTimeMillis()
            val certNotBefore = Date(now - 24L * 60L * 60L * 1000L)
            val certNotAfter = Date(now + 10L * 365L * 24L * 60L * 60L * 1000L)
            val certSerial = BigInteger.valueOf((now and 0x7FFFFFFFL).coerceAtLeast(1L))

            val candidates = listOf(
                Triple(
                    "RSA-PKCS1 SHA256",
                    arrayOf(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1),
                    arrayOf(KeyProperties.DIGEST_SHA256)
                ),
                Triple(
                    "RSA-PSS+PKCS1 SHA256/384/512",
                    arrayOf(
                        KeyProperties.SIGNATURE_PADDING_RSA_PSS,
                        KeyProperties.SIGNATURE_PADDING_RSA_PKCS1
                    ),
                    arrayOf(
                        KeyProperties.DIGEST_SHA256,
                        KeyProperties.DIGEST_SHA384,
                        KeyProperties.DIGEST_SHA512
                    )
                )
            )

            var generated = false
            for ((profileName, paddings, digests) in candidates) {
                val result = runCatching {
                    val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
                    kpg.initialize(
                        KeyGenParameterSpec.Builder(
                            TLS_KEY_ALIAS,
                            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_DECRYPT
                        )
                            .setKeySize(2048)
                            .setSignaturePaddings(*paddings)
                            .setDigests(*digests)
                            .setCertificateSubject(X500Principal("CN=NITTC-Sync"))
                            .setCertificateSerialNumber(certSerial)
                            .setCertificateNotBefore(certNotBefore)
                            .setCertificateNotAfter(certNotAfter)
                            .build()
                    )
                    kpg.generateKeyPair()
                }
                if (result.isSuccess) {
                    appendLog("TLS鍵ペアを生成しました profile=$profileName")
                    generated = true
                    break
                }

                val e = result.exceptionOrNull()
                appendLog("TLS鍵生成失敗 profile=$profileName (${e?.javaClass?.simpleName}): ${e?.message}")
                e?.cause?.let { appendLog("  caused by(${it.javaClass.simpleName}): ${it.message}") }
            }

            if (!generated) {
                throw ProviderException("TLS鍵ペアの生成に失敗しました（互換フォールバックも失敗）")
            }
        }
        appendLog("TLS KeyStore aliases: ${keyStore.aliases().toList()}")
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        // Android Keystore の場合パスワードは null を渡す
        kmf.init(keyStore, null)
        appendLog("TLS KeyManagers: ${kmf.keyManagers.size}")
        return SSLContext.getInstance("TLS").apply {
            init(kmf.keyManagers, arrayOf(ACCEPT_ALL_TRUST_MANAGER), null)
        }
    }

    /** Android Keystoreが利用できない端末向けのフォールバック（起動時のみ生成、再起動で変わる） */
    private fun buildInMemorySslContext(): SSLContext {
        val kpg = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val keyPair = kpg.generateKeyPair()
        val keyStore = KeyStore.getInstance("PKCS12").apply { load(null) }
        // 自己署名証明書は Android Keystore の証明書を再利用できないため、
        // フォールバック時は TOFU フィンガープリント検証なしで動作（認証は trustToken に依存）
        val tmArray = arrayOf<javax.net.ssl.TrustManager>(ACCEPT_ALL_TRUST_MANAGER)
        val kmf = javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm())
        // ダミーのKeyStoreを使うため、実際にはAndroid内蔵のX509KeyManagerを活用
        return SSLContext.getInstance("TLS").apply {
            init(null, tmArray, null)
        }
    }

    private fun configureTlsServerSocket(socket: ServerSocket) {
        val sslServerSocket = socket as? SSLServerSocket ?: return
        val supportedProtocols = sslServerSocket.supportedProtocols.toSet()
        val enabledProtocols = TLS_PREFERRED_PROTOCOLS.filter { it in supportedProtocols }
        if (enabledProtocols.isNotEmpty()) {
            sslServerSocket.enabledProtocols = enabledProtocols.toTypedArray()
        }

        val supportedCipherSuites = sslServerSocket.supportedCipherSuites.toSet()
        val enabledCipherSuites = TLS_PREFERRED_CIPHERS.filter { it in supportedCipherSuites }
        if (enabledCipherSuites.isNotEmpty()) {
            sslServerSocket.enabledCipherSuites = enabledCipherSuites.toTypedArray()
        }
        sslServerSocket.useClientMode = false
        appendLog(
            "TLS待受設定 protocols=${sslServerSocket.enabledProtocols.joinToString()} ciphers=${sslServerSocket.enabledCipherSuites.joinToString()}"
        )
    }

    private fun configureTlsClientSocket(socket: SSLSocket, protocols: Array<String>?) {
        val supportedProtocols = socket.supportedProtocols.toSet()
        val preferredProtocols = protocols?.toList() ?: TLS_PREFERRED_PROTOCOLS.toList()
        val enabledProtocols = preferredProtocols.filter { it in supportedProtocols }
        if (enabledProtocols.isNotEmpty()) {
            socket.enabledProtocols = enabledProtocols.toTypedArray()
        }

        val supportedCipherSuites = socket.supportedCipherSuites.toSet()
        val enabledCipherSuites = TLS_PREFERRED_CIPHERS.filter { it in supportedCipherSuites }
        if (enabledCipherSuites.isNotEmpty()) {
            socket.enabledCipherSuites = enabledCipherSuites.toTypedArray()
        }
    }

    private fun getLocalCertFingerprint(): String {
        return runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val cert = keyStore.getCertificate(TLS_KEY_ALIAS) ?: return ""
            MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                .joinToString("") { "%02x".format(it) }
        }.getOrDefault("")
    }

    private fun createClientSocketFactory(expectedFingerprint: String?): javax.net.ssl.SSLSocketFactory {
        val tm: X509TrustManager = if (expectedFingerprint.isNullOrBlank()) {
            ACCEPT_ALL_TRUST_MANAGER
        } else {
            object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    val fp = MessageDigest.getInstance("SHA-256").digest(chain[0].encoded)
                        .joinToString("") { b -> "%02x".format(b) }
                    if (fp != expectedFingerprint) {
                        throw CertificateException("TLS証明書フィンガープリントが一致しません。再登録が必要です。")
                    }
                }
            }
        }
        return SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(tm), null)
        }.socketFactory
    }

    private suspend fun sendRequest(
        host: String,
        port: Int,
        payload: JSONObject,
        expectedFingerprint: String? = null
    ): JSONObject = sendRequestWithFingerprint(host, port, payload, expectedFingerprint).first

    private suspend fun sendRequestWithFingerprint(
        host: String,
        port: Int,
        payload: JSONObject,
        expectedFingerprint: String? = null
    ): Pair<JSONObject, String> {
        fun tryTlsRequest(protocols: Array<String>?): Pair<JSONObject, String> {
            val socketFactory = createClientSocketFactory(expectedFingerprint)
            val rawSocket = Socket()
            rawSocket.connect(InetSocketAddress(host, port), 10_000)
            val sslSocket = socketFactory.createSocket(rawSocket, host, port, true) as SSLSocket
            return sslSocket.use {
                sslSocket.soTimeout = 30_000
                configureTlsClientSocket(sslSocket, protocols)
                sslSocket.startHandshake()
                val peerCert = sslSocket.session.peerCertificates.firstOrNull() as? X509Certificate
                val fingerprint = peerCert?.let { cert ->
                    MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                        .joinToString("") { b -> "%02x".format(b) }
                } ?: ""
                val writer = BufferedWriter(OutputStreamWriter(sslSocket.getOutputStream(), UTF_8))
                writer.write(payload.toString())
                writer.newLine()
                writer.flush()
                val response = BufferedReader(InputStreamReader(sslSocket.getInputStream(), UTF_8)).readLine().orEmpty()
                appendLog("TLS送信成功 $host:$port ${payload.optString("type")}")
                if (payload.optString("type") == "hello") {
                    appendLog("hello応答(TLS) $host:$port raw=${response.take(200)}")
                }
                if (response.isBlank()) throw IllegalStateException("同期サーバーから空の応答が返りました。")
                Pair(JSONObject(response), fingerprint)
            }
        }

        fun tryPlainRequest(): Pair<JSONObject, String> {
            val rawSocket = Socket()
            rawSocket.connect(InetSocketAddress(host, port), 10_000)
            return rawSocket.use { sock ->
                sock.soTimeout = 30_000
                val writer = BufferedWriter(OutputStreamWriter(sock.getOutputStream(), UTF_8))
                writer.write(payload.toString())
                writer.newLine()
                writer.flush()
                val response = BufferedReader(InputStreamReader(sock.getInputStream(), UTF_8)).readLine().orEmpty()
                if (response.isBlank()) throw IllegalStateException("同期サーバーから空の応答が返りました。")
                Pair(JSONObject(response), "")
            }
        }

        fun tryTlsWithRetries(): Pair<JSONObject, String> {
            val first = runCatching {
                appendLog("TLS接続開始 $host:$port ${payload.optString("type")}")
                tryTlsRequest(null)
            }
            if (first.isSuccess) return first.getOrThrow()

            val firstEx = first.exceptionOrNull()
            val tls12 = runCatching {
                appendLog("TLS1.2で再試行 $host:$port ${payload.optString("type")}")
                tryTlsRequest(arrayOf("TLSv1.2"))
            }
            if (tls12.isSuccess) return tls12.getOrThrow()

            val ex = tls12.exceptionOrNull() ?: firstEx
            appendLog("TLS失敗(${ex?.javaClass?.simpleName}): ${ex?.message}")
            ex?.cause?.let { appendLog("  caused by(${it.javaClass.simpleName}): ${it.message}") }
            throw ex ?: IllegalStateException("TLS接続に失敗しました。")
        }

        // 設定に応じて優先方式を決めつつ、失敗時は逆方式へ自動フォールバックする
        val preferTls = isTlsSyncEnabled()
        var lastError: Throwable? = null
        val attempts = if (preferTls) listOf("tls", "plain") else listOf("plain", "tls")
        for (mode in attempts) {
            when (mode) {
                "tls" -> {
                    val tlsResult = runCatching { tryTlsWithRetries() }
                    if (tlsResult.isSuccess) return tlsResult.getOrThrow()
                    lastError = tlsResult.exceptionOrNull()
                    appendLog("TLS通信失敗。平文を試行します。")
                }

                else -> {
                    appendLog("平文TCP送信 $host:$port ${payload.optString("type")}")
                    val plainResult = runCatching { tryPlainRequest() }
                    if (plainResult.isSuccess) {
                        if (!preferTls) appendLog("平文通信成功。TLSは未使用。")
                        return plainResult.getOrThrow()
                    }
                    lastError = plainResult.exceptionOrNull()
                    appendLog("平文通信失敗(${lastError?.javaClass?.simpleName}): ${lastError?.message}")
                    appendLog("平文通信失敗。TLSを試行します。")
                }
            }
        }

        throw lastError ?: IllegalStateException("同期通信に失敗しました。")
    }

    private suspend fun putAuth(payload: JSONObject, authMode: String, authValue: String) {
        val now = System.currentTimeMillis()
        val nonce = UUID.randomUUID().toString()
        val profile = ensureProfile()
        payload.put("authMode", authMode)
        payload.put("authValue", authValue)
        payload.put("authTimestamp", now)
        payload.put("authNonce", nonce)
        if (profile.deviceId.isNotBlank()) {
            payload.put("peerDeviceId", profile.deviceId)
        }
    }

    private suspend fun ensureProfile(): SyncProfileEntity {
        val existing = dao.getSyncProfile()
        if (existing != null && existing.deviceId.isNotBlank()) return existing

        val profile = SyncProfileEntity(
            id = 1,
            deviceId = UUID.randomUUID().toString(),
            userNickname = "",
            deviceName = defaultDeviceName(),
            passwordPlaintext = "",
            passwordHash = "",
            passwordLength = 0
        )
        dao.upsertSyncProfile(profile)
        return profile
    }

    private fun datasetLabel(key: String): String {
        return when (key) {
            SchedulerRepository.DATASET_TASKS -> "課題"
            SchedulerRepository.DATASET_PLANS -> "予定"
            SchedulerRepository.DATASET_SCHEDULE_SETTINGS -> "時間割設定"
            SchedulerRepository.DATASET_LESSONS -> "時間割"
            SchedulerRepository.DATASET_DAY_TYPES -> "A/B表"
            SchedulerRepository.DATASET_LONG_BREAKS -> "長期休み"
            SchedulerRepository.DATASET_CANCELLED_LESSONS -> "休講情報"
            else -> key
        }
    }

    fun formatTimestamp(value: Long): String {
        if (value <= 0L) return "未同期"
        return DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(value))
    }

    private fun defaultDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        return listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank {
            Build.DEVICE ?: "Android"
        }
    }

    private fun hashPassword(raw: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun registerNsdService() {
        val manager = nsdManager ?: return
        if (nsdRegistrationListener != null) return
        val listener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) = Unit
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) = Unit
        }
        val serviceInfo = NsdServiceInfo().apply {
            serviceType = NSD_SERVICE_TYPE
            serviceName = "NITTC-${Build.MODEL.take(12)}-${UUID.randomUUID().toString().take(6)}"
            port = tcpPort
        }
        runCatching {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
            nsdRegistrationListener = listener
        }
    }

    private fun unregisterNsdService() {
        nsdRegistrationListener?.let { listener ->
            runCatching { nsdManager?.unregisterService(listener) }
        }
        nsdRegistrationListener = null
    }

    private fun openDiscoverySocket(): DatagramSocket {
        return DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(DISCOVERY_PORT))
        }
    }

    private fun discoveryTargets(): List<InetAddress> {
        val result = linkedSetOf<InetAddress>()
        runCatching { result += InetAddress.getByName("255.255.255.255") }
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList().forEach { networkInterface ->
                if (!networkInterface.isUp || networkInterface.isLoopback) return@forEach
                networkInterface.interfaceAddresses.forEach { interfaceAddress ->
                    interfaceAddress.broadcast?.let { result += it }
                }
            }
        }
        return result.toList()
    }

    private suspend fun <T> withMulticastLock(block: suspend () -> T): T {
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifiManager?.createMulticastLock("nittc-sync-discovery")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        return try {
            block()
        } finally {
            runCatching { lock?.release() }
        }
    }

    private suspend fun discoverDevicesWithNsd(
        timeoutMs: Long,
        localDeviceId: String,
        registered: Map<String, SyncRegisteredDeviceEntity>,
        found: LinkedHashMap<String, DiscoveredSyncDevice>
    ) {
        val manager = nsdManager ?: return
        suspendCancellableCoroutine<Unit> { continuation ->
            val discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    runCatching { manager.stopServiceDiscovery(this) }
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    runCatching { manager.stopServiceDiscovery(this) }
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onDiscoveryStarted(serviceType: String?) = Unit

                override fun onDiscoveryStopped(serviceType: String?) {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (serviceInfo.serviceType != NSD_SERVICE_TYPE) return
                    runCatching {
                        manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) = Unit

                            override fun onServiceResolved(resolved: NsdServiceInfo) {
                                val host = resolved.host?.hostAddress ?: return
                                scope.launch {
                                    runCatching {
                                        val response = sendRequest(
                                            host,
                                            resolved.port,
                                            JSONObject().put("type", "hello").put("schema", "nittc-scheduler-sync")
                                        )
                                        val deviceId = response.optString("deviceId")
                                        if (deviceId.isBlank() || deviceId == localDeviceId) return@launch
                                        found[deviceId] = DiscoveredSyncDevice(
                                            deviceId = deviceId,
                                            userNickname = response.optString("userNickname", ""),
                                            deviceName = response.optString("deviceName", host),
                                            host = host,
                                            port = resolved.port,
                                            isRegistered = registered.containsKey(deviceId)
                                        )
                                        appendLog("NSDで検出: ${response.optString("deviceName", host)} ($host:${resolved.port})")
                                    }
                                }
                            }
                        })
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            }

            runCatching {
                manager.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            }.onFailure {
                continuation.resume(Unit)
            }

            scope.launch {
                delay(timeoutMs)
                runCatching { manager.stopServiceDiscovery(discoveryListener) }
            }

            continuation.invokeOnCancellation {
                runCatching { manager.stopServiceDiscovery(discoveryListener) }
            }
        }
    }

    private fun appendLog(message: String) {
        val timestamp = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        val current = _diagnostics.value.recentLogs.toMutableList()
        current.add(0, "[$timestamp] $message")
        _diagnostics.value = _diagnostics.value.copy(recentLogs = current.take(MAX_RECENT_LOGS))
    }

    private fun refreshDiagnostics() {
        _diagnostics.value = _diagnostics.value.copy(
            localAddresses = localAddresses(),
            listeningPort = tcpPort,
            serverRunning = serverSocket != null && serverSocket?.isClosed == false
        )
    }

    private fun localAddresses(): List<String> {
        val result = linkedSetOf<String>()
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList().forEach { networkInterface ->
                if (!networkInterface.isUp || networkInterface.isLoopback) return@forEach
                networkInterface.inetAddresses.toList()
                    .mapNotNull { it.hostAddress?.substringBefore('%') }
                    .filter { it.contains('.') }
                    .forEach { result += it }
            }
        }
        return result.toList().sorted()
    }
}
