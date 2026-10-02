package jp.linkserver.nittcsc.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import com.google.zxing.qrcode.QRCodeReader

internal object QrImageDecoding {
    fun read(source: LuminanceSource, pure: Boolean = false): List<String> {
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        if (pure) return try {
            listOf(QRCodeReader().decode(bitmap, mapOf(DecodeHintType.PURE_BARCODE to true)).text)
        } catch (_: ReaderException) { emptyList() }
        return try {
            QRCodeMultiReader().decodeMultiple(bitmap, mapOf(DecodeHintType.TRY_HARDER to true)).map { it.text }
        } catch (_: ReaderException) { emptyList() }
    }
}
