package jp.linkserver.nittcsc.logic

/** QR画像は共通のまま、表示間隔だけを切り替える。 */
internal enum class QrDisplaySpeed(val frameIntervalMs: Long) {
    STABLE(200L),
    STANDARD(125L),
    FAST(65L),
    ULTRA_FAST(50L)
}
