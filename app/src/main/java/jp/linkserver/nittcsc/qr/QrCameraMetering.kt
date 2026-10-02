package jp.linkserver.nittcsc.qr

import android.os.SystemClock
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
import jp.linkserver.nittcsc.logic.QrAutoMetering
import jp.linkserver.nittcsc.logic.QrMeteringRegion
import java.util.concurrent.TimeUnit

/** メインスレッドから操作する。AFを繰り返さず、QR付近でカメラ自身のAEを継続する。 */
internal class QrCameraMetering(private val view: PreviewView) {
    private var camera: Camera? = null
    private val auto = QrAutoMetering()

    fun bind(camera: Camera?) {
        this.camera = camera
        reset()
    }

    fun reset() = auto.reset()

    fun focus(x: Float, y: Float) {
        val bound = camera ?: return
        if (view.width == 0 || view.height == 0) return
        val point = view.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
        if (bound.cameraInfo.isFocusMeteringSupported(action)) {
            auto.manualFocus(SystemClock.elapsedRealtime())
            bound.cameraControl.startFocusAndMetering(action)
        }
    }

    fun update(corners: FloatArray?) {
        val bound = camera ?: return
        if (view.width == 0 || view.height == 0) return
        val now = SystemClock.elapsedRealtime()
        if (corners != null && corners.size == 8 && corners.all { it.isFinite() }) {
            val xs = (0..3).map { corners[it * 2] / view.width }
            val ys = (0..3).map { corners[it * 2 + 1] / view.height }
            val width = xs.max() - xs.min()
            val height = ys.max() - ys.min()
            auto.recordQr(QrMeteringRegion(
                xs.average().toFloat().coerceIn(0f, 1f),
                ys.average().toFloat().coerceIn(0f, 1f),
                (minOf(width, height) * 0.6f).coerceIn(0.08f, 0.5f)
            ), now)
        }
        val target = auto.next(now) ?: return
        val point = view.meteringPointFactory.createPoint(target.x * view.width, target.y * view.height, target.size)
        // 解除期限を設けず、明るさの変化をAEに追従させる。AE非対応なら端末の標準測光を使う。
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AE).disableAutoCancel().build()
        if (bound.cameraInfo.isFocusMeteringSupported(action)) bound.cameraControl.startFocusAndMetering(action)
    }
}
