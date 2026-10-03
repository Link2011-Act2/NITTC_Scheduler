package jp.linkserver.nittcsc.logic

/** IntDevの画面表示向け調整値。画像・GIFの共有設定には適用しない。 */
internal data class QrDisplaySettings(
    val chunkBytes: Int = QrShareCodec.SCREEN_CHUNK_BYTES,
    val frameIntervalMs: Long = DEFAULT_FRAME_INTERVAL_MS
) {
    init {
        require(chunkBytes in QrShareCodec.MIN_CHUNK_BYTES..QrShareCodec.IMAGE_CHUNK_BYTES)
        require(frameIntervalMs in MIN_FRAME_INTERVAL_MS..MAX_FRAME_INTERVAL_MS)
    }

    companion object {
        const val DEFAULT_FRAME_INTERVAL_MS = 125L
        const val MIN_FRAME_INTERVAL_MS = 50L
        const val MAX_FRAME_INTERVAL_MS = 2000L
    }
}
