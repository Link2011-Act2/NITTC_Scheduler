package jp.linkserver.nittcsc.qrbenchmark

import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Build
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.RGBLuminanceSource
import jp.linkserver.nittcsc.qr.QrCameraDecoder
import jp.linkserver.nittcsc.logic.QrShareCollector
import zxingcpp.BarcodeReader
import java.io.File
import java.util.concurrent.TimeUnit

/** Separate application; cannot open or mutate the scheduler database. */
class BenchmarkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val status = TextView(this).apply { textSize = 18f; setPadding(24,48,24,24); text = getString(R.string.running) }
        setContentView(status)
        Thread {
            try {
                runBenchmark { message -> runOnUiThread { status.text = message } }
                runOnUiThread { status.text = getString(R.string.complete) }
                Log.i("QrBenchmark", "DONE")
            } catch (e: Exception) {
                Log.e("QrBenchmark", "FAILED", e)
                File(filesDir,"error.txt").writeText(e.stackTraceToString())
                runOnUiThread { status.text = getString(R.string.failed,e.message) }
            }
        }.start()
    }

    private fun runBenchmark(progress: (String) -> Unit) {
        val ml = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        val cpp = BarcodeReader(BarcodeReader.Options(formats=setOf(BarcodeReader.Format.QR_CODE),
            tryHarder=true, tryRotate=true, tryInvert=true, tryDownscale=true))
        var streamDecoder = QrCameraDecoder()
        var streamName = ""
        var collectors = List(3) { QrShareCollector() }
        val completed = mutableSetOf<Int>()
        val rows = assets.open("cases.tsv").bufferedReader().readLines()
        File(filesDir,"device.txt").writeText("${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT}\nJava ZXing 3.5.4, ZXing-C++ 3.1.1, ML Kit bundled 17.3.0")
        File(filesDir,"results.tsv").bufferedWriter().use { out ->
            out.appendLine("id\tgroup\tdetails\tstream\tengine\tfirst_correct\tthree_correct\twrong\tfirst_ms\tthree_ms\tstream_completed")
            rows.forEachIndexed { index, line ->
                val row = line.split('\t')
                val id=row[0]; val group=row[1]; val details=row[4]; val stream=row.getOrElse(5){""}
                if (stream != streamName) { streamName=stream; streamDecoder=QrCameraDecoder(); collectors=List(3){QrShareCollector()}; completed.clear() }
                val bitmap=assets.open(row[2]).use { BitmapFactory.decodeStream(it) }
                val pixels=IntArray(bitmap.width*bitmap.height)
                bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                val source=RGBLuminanceSource(bitmap.width,bitmap.height,pixels)
                val expected=assets.open(row[3]).bufferedReader().readText()
                val input=InputImage.fromBitmap(bitmap,0)
                val decoder=if(group=="stream") streamDecoder else QrCameraDecoder()
                // Rotate engine order to distribute warming/thermal drift across all engines.
                val order=List(3){(index+it)%3}
                for(engine in order) {
                    var first=false; var correct=false; var wrong=false; var firstMs=0.0; var totalMs=0.0
                    // Streams get one attempt per new image. Still cases allow up to three repeated frames,
                    // needed by the existing decoder's quarter-turn/inversion recovery policy.
                    val attempts=if(group=="stream") 1 else 3
                    for(attempt in 0 until attempts) {
                        val start=System.nanoTime()
                        val texts=when(engine) {
                            0 -> listOfNotNull(decoder.read(source)?.text)
                            1 -> cpp.read(bitmap).mapNotNull { it.text }
                            else -> Tasks.await(ml.process(input),30,TimeUnit.SECONDS).mapNotNull { it.rawValue }
                        }
                        val elapsed=(System.nanoTime()-start)/1_000_000.0
                        if(attempt==0) { firstMs=elapsed; first=if(expected.isEmpty()) texts.isEmpty() else expected in texts }
                        totalMs+=elapsed
                        wrong=wrong || texts.any { it!=expected }
                        correct=if(expected.isEmpty()) texts.isEmpty() else expected in texts
                        if(group=="stream" && correct && engine !in completed) {
                            val json=collectors[engine].add(expected)
                            if(json!=null) { check(json==assets.open("stream-json.txt").bufferedReader().readText()); completed+=engine }
                        }
                        if(correct) break
                    }
                    val name=listOf("java_current","cpp","mlkit")[engine]
                    out.appendLine(listOf(id,group,details,stream,name,first,correct,wrong,firstMs,totalMs,engine in completed).joinToString("\t"))
                }
                bitmap.recycle()
                if(index%20==0) { out.flush(); progress(getString(R.string.progress,index+1,rows.size)); Log.i("QrBenchmark","${index+1}/${rows.size}") }
            }
        }
        ml.close()
        File(filesDir,"done.txt").writeText("${rows.size} cases completed")
    }
}
