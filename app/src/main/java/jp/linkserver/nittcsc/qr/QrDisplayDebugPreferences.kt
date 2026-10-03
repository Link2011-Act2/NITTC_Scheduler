package jp.linkserver.nittcsc.qr

import android.content.Context
import jp.linkserver.nittcsc.BuildConfig
import jp.linkserver.nittcsc.logic.QrDisplaySettings
import jp.linkserver.nittcsc.update.isIntDevBuild

private const val QR_DISPLAY_DEBUG_PREFS = "qr_display_debug"
private const val KEY_CHUNK_BYTES = "chunk_bytes"
private const val KEY_FRAME_INTERVAL = "frame_interval_ms"

/** IntDev専用の端末ローカル設定。通常版では保存済みの値も使用しない。 */
internal fun loadQrDisplayDebugSettings(context: Context): QrDisplaySettings {
    val defaults = QrDisplaySettings()
    if (!isIntDevBuild(BuildConfig.VERSION_NAME)) return defaults
    return runCatching {
        val prefs = context.getSharedPreferences(QR_DISPLAY_DEBUG_PREFS, Context.MODE_PRIVATE)
        QrDisplaySettings(prefs.getInt(KEY_CHUNK_BYTES, defaults.chunkBytes),
            prefs.getLong(KEY_FRAME_INTERVAL, defaults.frameIntervalMs))
    }.getOrDefault(defaults)
}

internal fun saveQrDisplayDebugSettings(context: Context, settings: QrDisplaySettings) {
    if (!isIntDevBuild(BuildConfig.VERSION_NAME)) return
    context.getSharedPreferences(QR_DISPLAY_DEBUG_PREFS, Context.MODE_PRIVATE).edit()
        .putInt(KEY_CHUNK_BYTES, settings.chunkBytes)
        .putLong(KEY_FRAME_INTERVAL, settings.frameIntervalMs)
        .apply()
}
