package jp.linkserver.nittcsc.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.core.content.FileProvider
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.GifHeaderParser
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import com.google.zxing.RGBLuminanceSource
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.QrShareJson
import jp.linkserver.nittcsc.data.QrSharePayload
import jp.linkserver.nittcsc.logic.QrShareCodec
import jp.linkserver.nittcsc.logic.QrShareException
import jp.linkserver.nittcsc.logic.QrShareFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object QrShareMedia {
    private const val MAX_FILE_BYTES = 16 * 1024 * 1024
    private const val IMAGE_WIDTH = 1024
    private const val IMAGE_HEIGHT = 1256

    private fun nativeQrBitmap(frame: String): Bitmap {
        val matrix = QrImageEncoding.matrix(frame)
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix[i % matrix.width, i / matrix.width]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.RGB_565)
    }

    /** 再生前に全画像を用意する。白黒のマスをRGB_565で保持し、拡大は表示時に行う。 */
    suspend fun displayBitmaps(frames: List<String>): List<Bitmap> = withContext(Dispatchers.Default) {
        frames.map { frame ->
            currentCoroutineContext().ensureActive()
            nativeQrBitmap(frame)
        }
    }

    data class SharedImage(val uri: Uri, val mimeType: String)

    suspend fun createImage(context: Context, payload: QrSharePayload, label: String,
        onProgress: suspend (Int, Int) -> Unit = { _, _ -> }): SharedImage = withContext(Dispatchers.Default) {
        require(label.isNotBlank() && label.length <= 60 && '\n' !in label && '\r' !in label)
        val data = payload.copy(classLabel = label.trim())
        val transfer = QrShareCodec.create(QrShareJson.encode(data))
        // 静止画は大きなQRに1枚で収める。超過時は読みやすい分割量でGIFにする。
        val single = transfer.frames(QrShareCodec.IMAGE_CHUNK_BYTES)
        val frames = if (single.size == 1) single else transfer.frames(QrShareCodec.GIF_CHUNK_BYTES)
        onProgress(0, frames.size)
        val gif = frames.size > 1
        val directory = File(context.cacheDir, "qr-share").also { check(it.isDirectory || it.mkdirs()) }
        val file = File(directory, "qr-${transfer.id}.${if (gif) "gif" else "png"}")
        try {
            file.outputStream().buffered().use { output ->
                if (gif) {
                    val encoder = QrImageEncoding.animation(output, IMAGE_WIDTH, IMAGE_HEIGHT)
                    val pixels = IntArray(IMAGE_WIDTH * IMAGE_HEIGHT)
                    val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
                    try {
                        frames.forEachIndexed { index, frame ->
                            currentCoroutineContext().ensureActive()
                            labelledBitmap(context, data, frame, index, frames.size, bitmap)
                            bitmap.getPixels(pixels, 0, IMAGE_WIDTH, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT)
                            encoder.add(pixels)
                            onProgress(index + 1, frames.size)
                        }
                    } finally { bitmap.recycle() }
                    encoder.finish()
                } else {
                    val bitmap = labelledBitmap(context, data, frames.single(), 0, 1)
                    try { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
                    finally { bitmap.recycle() }
                    onProgress(1, 1)
                }
            }
            SharedImage(FileProvider.getUriForFile(context, "${context.packageName}.provider", file), if (gif) "image/gif" else "image/png")
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    private fun labelledBitmap(context: Context, payload: QrSharePayload, frame: String, index: Int, count: Int,
        bitmap: Bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)): Bitmap {
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        // 2色に固定し、GIFの色量子化・ディザでQRのマスが変わることを防ぐ。
        val paint = Paint().apply { color = Color.BLACK; textAlign = Paint.Align.CENTER; textSize = 38f; isAntiAlias = false; isFilterBitmap = false }
        val title = android.text.TextUtils.ellipsize(context.getString(R.string.qr_image_title, payload.classLabel), android.text.TextPaint(paint), 940f, android.text.TextUtils.TruncateAt.END).toString()
        canvas.drawText(title, 512f, 56f, paint)
        canvas.drawText(context.getString(R.string.qr_image_year, payload.academicYear), 512f, 103f, paint.apply { textSize = 32f })
        val qr = nativeQrBitmap(frame)
        val size = (IMAGE_WIDTH / qr.width) * qr.width
        val offset = (IMAGE_WIDTH - size) / 2
        try { canvas.drawBitmap(qr, null, Rect(offset, 126 + offset, offset + size, 126 + offset + size), paint) }
        finally { qr.recycle() }
        canvas.drawText(context.getString(R.string.qr_image_instruction), 512f, 1190f, paint.apply { textSize = 32f })
        canvas.drawText(context.getString(R.string.qr_part_number, index + 1, count), 512f, 1233f, paint)
        return bitmap
    }

    /** 全フレームを順に処理する。同じ端末で受け取ったGIFもカメラなしで読み取れる。 */
    suspend fun readImage(context: Context, uri: Uri, onFrame: suspend (String) -> Boolean) = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                currentCoroutineContext().ensureActive()
                val size = input.read(buffer)
                if (size < 0) break
                if (out.size() + size > MAX_FILE_BYTES) throw QrShareException(QrShareFailure.TOO_LARGE)
                out.write(buffer, 0, size)
            }
            out.toByteArray()
        } ?: throw QrShareException(QrShareFailure.INVALID)
        var found = false
        suspend fun scan(bitmap: Bitmap): Boolean {
            currentCoroutineContext().ensureActive()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            // 自分で生成したPNG/GIFは、探索せず固定位置のQRを直接復号する。
            var results = when {
                bitmap.width == IMAGE_WIDTH && bitmap.height == IMAGE_HEIGHT ->
                    QrImageDecoding.read(source.crop(0, 126, IMAGE_WIDTH, IMAGE_WIDTH), pure = true)
                bitmap.width == bitmap.height -> QrImageDecoding.read(source, pure = true)
                else -> emptyList()
            }
            if (results.none(QrShareCodec::isShareFrame)) results = QrImageDecoding.read(source)
            for (result in results) {
                if (!QrShareCodec.isShareFrame(result)) continue
                found = true
                if (onFrame(result)) return true
            }
            return false
        }
        if (bytes.size >= 6 && bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII) in setOf("GIF87a", "GIF89a")) {
            validateQrGif(bytes)
            val parser = GifHeaderParser()
            val header = try { parser.setData(bytes).parseHeader() } finally { parser.clear() }
            if (header.status != GifDecoder.STATUS_OK || header.numFrames !in 1..QrShareCodec.MAX_PARTS ||
                header.width !in 1..2048 || header.height !in 1..2048) throw QrShareException(QrShareFailure.INVALID)
            val decoder = StandardGifDecoder(BitmapProvider, header, ByteBuffer.wrap(bytes))
            try {
                for (i in 0 until header.numFrames) {
                    decoder.advance()
                    val bitmap = decoder.nextFrame ?: throw QrShareException(QrShareFailure.DAMAGED)
                    val complete = try { scan(bitmap) } finally { bitmap.recycle() }
                    if (complete) break
                }
            } finally { decoder.clear() }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..16384 || bounds.outHeight !in 1..16384) throw QrShareException(QrShareFailure.INVALID)
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                while (bounds.outWidth / inSampleSize > 2048 || bounds.outHeight / inSampleSize > 2048) inSampleSize *= 2
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: throw QrShareException(QrShareFailure.INVALID)
            try { scan(bitmap) } finally { bitmap.recycle() }
        }
        if (!found) throw QrShareException(QrShareFailure.INVALID)
    }

    private object BitmapProvider : GifDecoder.BitmapProvider {
        override fun obtain(width: Int, height: Int, config: Bitmap.Config): Bitmap = Bitmap.createBitmap(width, height, config)
        override fun release(bitmap: Bitmap) { bitmap.recycle() }
        override fun obtainByteArray(size: Int) = ByteArray(size)
        override fun release(bytes: ByteArray) = Unit
        override fun obtainIntArray(size: Int) = IntArray(size)
        override fun release(array: IntArray) = Unit
    }
}
