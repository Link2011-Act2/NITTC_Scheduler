package jp.linkserver.nittcsc.data

import org.json.JSONObject

internal const val LEGACY_SYNC_PROTOCOL_VERSION = 0
internal const val CURRENT_SYNC_PROTOCOL_VERSION = 1
internal const val SYNC_PROTOCOL_VERSION_KEY = "syncProtocolVersion"

internal fun JSONObject.putCurrentSyncProtocolVersion(): JSONObject {
    return put(SYNC_PROTOCOL_VERSION_KEY, CURRENT_SYNC_PROTOCOL_VERSION)
}

internal fun JSONObject.syncProtocolVersionOrLegacy(): Int {
    return optInt(SYNC_PROTOCOL_VERSION_KEY, LEGACY_SYNC_PROTOCOL_VERSION)
        .coerceAtLeast(LEGACY_SYNC_PROTOCOL_VERSION)
}
