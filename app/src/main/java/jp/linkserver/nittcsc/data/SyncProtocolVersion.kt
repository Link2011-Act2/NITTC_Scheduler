package jp.linkserver.nittcsc.data

import org.json.JSONObject

internal const val LEGACY_SYNC_PROTOCOL_VERSION = 0
internal const val CURRENT_SYNC_PROTOCOL_VERSION = 3
internal const val SYNC_PROTOCOL_VERSION_KEY = "syncProtocolVersion"

internal fun JSONObject.putCurrentSyncProtocolVersion(): JSONObject {
    return put(SYNC_PROTOCOL_VERSION_KEY, CURRENT_SYNC_PROTOCOL_VERSION)
}

internal fun JSONObject.syncProtocolVersionOrLegacy(): Int {
    return optInt(SYNC_PROTOCOL_VERSION_KEY, LEGACY_SYNC_PROTOCOL_VERSION)
        .coerceAtLeast(LEGACY_SYNC_PROTOCOL_VERSION)
}

internal fun requireCurrentSyncProtocol(payload: JSONObject) {
    val incomingVersion = payload.syncProtocolVersionOrLegacy()
    requireCompatibleSyncProtocolVersions(
        localVersion = CURRENT_SYNC_PROTOCOL_VERSION,
        remoteVersion = incomingVersion
    )
}

internal fun requireCompatibleSyncProtocolVersions(localVersion: Int, remoteVersion: Int) {
    require(localVersion == remoteVersion && localVersion == CURRENT_SYNC_PROTOCOL_VERSION) {
        "同期プロトコルのバージョンが異なります。両方の端末でアプリを最新版に更新してください。" +
            " (SKTTP/$remoteVersion / SKTTP/$localVersion)"
    }
}

internal fun requireCompatibleSyncProtocols(local: JSONObject, remote: JSONObject) {
    val localVersion = local.syncProtocolVersionOrLegacy()
    val remoteVersion = remote.syncProtocolVersionOrLegacy()
    requireCompatibleSyncProtocolVersions(localVersion, remoteVersion)
}
