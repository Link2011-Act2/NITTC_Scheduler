package jp.linkserver.nittcsc.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.view.Choreographer
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.camera.view.PreviewView
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import androidx.core.graphics.withScale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** 読み取り中は共通の陰影。成功時だけ背景のぼかし・波紋に使う映像を取得する。 */
internal class QrScannerCameraEffects(context: Context) : View(context) {
    private var preview: PreviewView? = null
    private var texture: TextureView? = null
    private var bitmap: Bitmap? = null
    private var textureBitmap: Bitmap? = null
    private val snapshotCanvas = Canvas()
    private val snapshotMatrix = Matrix()
    private val textureMatrix = Matrix()
    private val snapshotPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var hasSnapshot = false
    private var windowKey = Long.MIN_VALUE
    private val window = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blur = if (Build.VERSION.SDK_INT >= 31) runCatching { CameraCompletionBlur31() }.getOrNull() else null
    private var blurFailed = false
    private var ripple = if (Build.VERSION.SDK_INT >= 33) runCatching { CameraRipple33() }.getOrNull() else null
    private var captureFailed = false
    private var scrim = Color.BLACK
    private var primary = Color.WHITE
    private var vignette: Shader? = null
    private var rippleProgress = -1f
    private var blurProgress = 0f
    private var completionCaptureUntil = 0L
    private var centerX = 0f
    private var centerY = 0f
    private var active = false
    private val choreographer = Choreographer.getInstance()
    private var snapshotTimestamp = Long.MIN_VALUE
    private val capture = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!needsCapture()) return
            if (width > 0 && height > 0) {
                try {
                    val source = texture?.takeIf { it.isAttachedToWindow } ?: findTexture(preview).also { texture = it }
                    val timestamp = source?.surfaceTexture?.timestamp
                    if (source?.isAvailable == true && source.width > 0 && source.height > 0 &&
                        preview?.previewStreamState?.value == PreviewView.StreamState.STREAMING &&
                        (!hasSnapshot || timestamp == null || timestamp == 0L || timestamp != snapshotTimestamp)) {
                        val w = minOf(240, width)
                        val h = (height.toFloat() * w / width).toInt().coerceAtLeast(1)
                        if (bitmap?.width != w || bitmap?.height != h) {
                            if (Build.VERSION.SDK_INT >= 31) blur?.clear()
                            bitmap?.recycle()
                            bitmap = createBitmap(w, h)
                            snapshotCanvas.setBitmap(bitmap)
                            hasSnapshot = false
                        }
                        val ratio = minOf(1f, 480f / maxOf(source.width, source.height))
                        val rawWidth = (source.width * ratio).toInt().coerceAtLeast(1)
                        val rawHeight = (source.height * ratio).toInt().coerceAtLeast(1)
                        if (textureBitmap?.width != rawWidth || textureBitmap?.height != rawHeight) {
                            textureBitmap?.recycle()
                            textureBitmap = createBitmap(rawWidth, rawHeight)
                        }
                        val raw = textureBitmap!!
                        source.getBitmap(raw)
                        // getBitmapはCameraXのView側のFILL_CENTER変換を含まない。
                        // Textureの回転補正→Viewのscale/translation→画面座標の順に合成する。
                        snapshotMatrix.setScale(source.width.toFloat() / raw.width, source.height.toFloat() / raw.height)
                        source.getTransform(textureMatrix)
                        snapshotMatrix.postConcat(textureMatrix)
                        var child: View = source
                        while (child !== preview) {
                            snapshotMatrix.postConcat(child.matrix)
                            val parent = child.parent as? View ?: break
                            snapshotMatrix.postTranslate((child.left - parent.scrollX).toFloat(), (child.top - parent.scrollY).toFloat())
                            child = parent
                        }
                        val viewport = preview
                        if (child === viewport && viewport.width > 0 && viewport.height > 0) {
                            snapshotMatrix.postScale(w.toFloat() / viewport.width, h.toFloat() / viewport.height)
                            snapshotCanvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                            snapshotCanvas.drawBitmap(raw, snapshotMatrix, snapshotPaint)
                            hasSnapshot = true
                            snapshotTimestamp = timestamp ?: 0L
                            invalidate()
                        }
                    }
                } catch (_: RuntimeException) { captureFailed = true }
            }
            scheduleCapture()
        }
    }

    fun configure(source: PreviewView?, scrimColor: Int, primaryColor: Int, running: Boolean) {
        val restart = preview !== source || active != running
        if (preview !== source) { preview = source; texture = null; captureFailed = false; blurFailed = false; hasSnapshot = false }
        if (scrim != scrimColor) { scrim = scrimColor; updateVignette() }
        primary = primaryColor
        active = running
        if (restart) {
            choreographer.removeFrameCallback(capture)
            if (running) { captureFailed = false; snapshotTimestamp = Long.MIN_VALUE }
            scheduleCapture()
        }
    }

    private fun scheduleCapture() {
        if (needsCapture()) choreographer.postFrameCallback(capture)
    }

    private fun needsCapture(): Boolean = active && isAttachedToWindow && !captureFailed && (
        (Build.VERSION.SDK_INT >= 31 && blur != null && !blurFailed && blurProgress > 0f &&
            SystemClock.elapsedRealtime() < completionCaptureUntil && (!hasSnapshot || blurProgress < 1f)) ||
        (Build.VERSION.SDK_INT >= 33 && ripple != null && rippleProgress in 0f..0.999f)
    )

    fun updateWindow(path: Path, key: Long, progress: Float, blurAmount: Float, x: Float, y: Float) {
        if (windowKey == key && rippleProgress == progress && blurProgress == blurAmount) return
        val wasCapturing = needsCapture()
        windowKey = key
        window.set(path)
        rippleProgress = progress
        if (blurProgress == 0f && blurAmount > 0f) {
            completionCaptureUntil = SystemClock.elapsedRealtime() + 1_200L
            hasSnapshot = false
        }
        blurProgress = blurAmount.coerceIn(0f, 1f)
        centerX = x
        centerY = y
        if (!wasCapturing) scheduleCapture()
        else if (!needsCapture()) choreographer.removeFrameCallback(capture)
        invalidate()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // 同じ全画面座標のPreviewViewへ渡す。Composeの重なったAndroidView間で
        // 透過Viewのhit testに任せず、DOWNから複数指・CANCELまで同じ受け手に届ける。
        val camera = preview ?: return false
        if (!active || !camera.isAttachedToWindow) return false
        return camera.dispatchTouchEvent(event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= 33 && ripple == null) ripple = runCatching { CameraRipple33() }.getOrNull()
        scheduleCapture()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        snapshotTimestamp = Long.MIN_VALUE
        updateVignette()
    }

    private fun updateVignette() {
        if (width == 0 || height == 0) return
        vignette = RadialGradient(width / 2f, height / 2f, hypot(width.toFloat(), height.toFloat()) * 0.6f,
            intArrayOf(Color.TRANSPARENT, withAlpha(scrim, 0.12f), withAlpha(scrim, 0.65f)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val image = bitmap
        if (blurProgress > 0f) {
            var blurred = false
            if (Build.VERSION.SDK_INT >= 31 && blur != null && !blurFailed && !captureFailed &&
                image != null && hasSnapshot && canvas.isHardwareAccelerated) {
                try {
                    blur.draw(canvas, image, width, height, resources.displayMetrics.density, blurProgress)
                    blurred = true
                } catch (_: RuntimeException) { blurFailed = true }
            }
            // 非対応端末では、成功時だけ背景の陰影を滑らかに強める。
            if (!blurred) canvas.drawColor(withAlpha(scrim, 0.18f * blurProgress))
        }
        canvas.withSave {
            clipOutPath(window)
            drawColor(withAlpha(scrim, 0.25f))
            paint.shader = vignette
            paint.alpha = 255
            paint.style = Paint.Style.FILL
            drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
        }
        if (rippleProgress in 0f..0.999f) {
            val effect = ripple
            if (Build.VERSION.SDK_INT >= 33 && effect != null && image != null && hasSnapshot && canvas.isHardwareAccelerated) {
                try { effect.draw(canvas, image, width, height, centerX, centerY, rippleProgress, primary) }
                catch (_: RuntimeException) { ripple = null; drawRippleFallback(canvas) }
            } else drawRippleFallback(canvas)
        }
    }

    private fun drawRippleFallback(canvas: Canvas) {
        val p = rippleProgress
        val radius = hypot(width.toFloat(), height.toFloat()) * p
        val strength = (1f - p) * sin(p * Math.PI).toFloat()
        paint.color = withAlpha(primary, strength * 0.22f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = minOf(width, height) * 0.75f
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.style = Paint.Style.FILL
        repeat(48) { i ->
            val angle = i * 2.39996f
            val sparkle = ((i * 37) % 11) / 11f
            paint.color = withAlpha(primary, strength * (0.2f + sparkle * 0.3f))
            canvas.drawCircle(centerX + cos(angle) * radius, centerY + sin(angle) * radius,
                (1f + sparkle) * resources.displayMetrics.density, paint)
        }
        paint.alpha = 255
    }

    override fun onDetachedFromWindow() {
        choreographer.removeFrameCallback(capture)
        if (Build.VERSION.SDK_INT >= 31) blur?.clear()
        ripple = null
        bitmap?.recycle()
        bitmap = null
        snapshotCanvas.setBitmap(null)
        textureBitmap?.recycle()
        textureBitmap = null
        hasSnapshot = false
        texture = null
        super.onDetachedFromWindow()
    }

    private fun findTexture(view: View?): TextureView? {
        if (view is TextureView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findTexture(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun withAlpha(color: Int, alpha: Float): Int = (color and 0x00ffffff) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)
}

@RequiresApi(31)
private class CameraCompletionBlur31 {
    private val imageNode = RenderNode("QR completion blur")
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var radius = -1f

    fun draw(canvas: Canvas, bitmap: Bitmap, w: Int, h: Int, density: Float, amount: Float) {
        val scale = w.toFloat() / bitmap.width
        val nextRadius = 8f * density / scale
        if (radius != nextRadius) {
            imageNode.setRenderEffect(RenderEffect.createBlurEffect(nextRadius, nextRadius, Shader.TileMode.CLAMP))
            radius = nextRadius
        }
        imageNode.setPosition(0, 0, bitmap.width, bitmap.height)
        imageNode.beginRecording().drawBitmap(bitmap, 0f, 0f, imagePaint)
        imageNode.endRecording()
        val layer = canvas.saveLayerAlpha(0f, 0f, w.toFloat(), h.toFloat(), (amount * 255).toInt())
        try {
            canvas.withScale(scale, h.toFloat() / bitmap.height) { drawRenderNode(imageNode) }
        } finally { canvas.restoreToCount(layer) }
    }

    fun clear() = imageNode.discardDisplayList()
}

@RequiresApi(33)
private class CameraRipple33 {
    private val shader = RuntimeShader(SOURCE)
    private val paint = Paint().apply { this.shader = this@CameraRipple33.shader }
    private var image: Bitmap? = null
    private var camera: BitmapShader? = null
    private val matrix = Matrix()

    fun draw(canvas: Canvas, bitmap: Bitmap, w: Int, h: Int, x: Float, y: Float, progress: Float, color: Int) {
        if (image !== bitmap) {
            image = bitmap
            camera = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        matrix.setScale(w.toFloat() / bitmap.width, h.toFloat() / bitmap.height)
        camera!!.setLocalMatrix(matrix)
        shader.setInputShader("camera", camera!!)
        shader.setFloatUniform("resolution", w.toFloat(), h.toFloat())
        shader.setFloatUniform("center", x, y)
        shader.setFloatUniform("progress", progress)
        shader.setColorUniform("tint", color)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    companion object {
        private const val SOURCE = """
            uniform shader camera;
            uniform float2 resolution;
            uniform float2 center;
            uniform float progress;
            layout(color) uniform half4 tint;
            float noise(float2 p) { return fract(sin(dot(p, float2(12.9898,78.233))) * 43758.5453); }
            half4 main(float2 p) {
                float2 delta = p - center;
                float d = length(delta);
                float radius = progress * length(resolution);
                // ガウス分布の半値幅を、画面短辺の約75％にする。
                float width = min(resolution.x, resolution.y) * 0.45;
                float band = exp(-pow((d-radius)/width, 2.0));
                float decay = (1.0-progress) * smoothstep(0.0, 0.12, progress);
                float sparkle = step(0.985, noise(floor(p/3.0) + floor(progress*40.0))) * band * decay;
                float distortion = sin((d-radius)*0.12) * band * decay * 7.0;
                half3 base = camera.eval(p + delta/max(d,1.0)*distortion).rgb;
                half alpha = half(band*decay*0.18 + sparkle*0.32);
                return half4(mix(base, tint.rgb, half(0.3+sparkle)) * alpha, alpha);
            }
        """
    }
}
