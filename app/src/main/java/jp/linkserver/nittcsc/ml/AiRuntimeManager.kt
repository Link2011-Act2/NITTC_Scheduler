package jp.linkserver.nittcsc.ml

import android.content.Context
import android.os.Build
import android.os.Process
import jp.linkserver.nittcsc.BuildConfig
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.logic.AI_RUNTIME_VERSION
import jp.linkserver.nittcsc.logic.AiRuntimeArtifact
import jp.linkserver.nittcsc.logic.extractVerifiedAiRuntime
import jp.linkserver.nittcsc.logic.selectAiRuntimeArtifact
import jp.linkserver.nittcsc.logic.verifyAiRuntimeFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import org.nehuatl.llamacpp.LlamaNativeLoader
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class AiRuntimeAvailability { CHECKING, MISSING, BUNDLED, DOWNLOADED, UNSUPPORTED }

class AiRuntimeManager(context: Context) {
    private val appContext = context.applicationContext

    companion object {
        // 画面を閉じて再度開いた場合も、検証・ダウンロード・ロードの競合を防ぐ。
        private val mutex = Mutex()
        private val allowedHosts = setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
        private val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.MINUTES)
            .followSslRedirects(false)
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                if (request.url.scheme != "https" || request.url.host !in allowedHosts) {
                    throw IOException("Unexpected AI runtime download host")
                }
                chain.proceed(request)
            }
            .build()
    }

    private fun artifact(): AiRuntimeArtifact? {
        if (!Process.is64Bit()) return null
        val features = runCatching {
            File("/proc/cpuinfo").useLines { lines ->
                lines.firstOrNull { it.startsWith("Features") }
                    ?.substringAfter(':')?.trim()?.split(Regex("\\s+"))?.toSet().orEmpty()
            }
        }.getOrDefault(emptySet())
        return selectAiRuntimeArtifact(Build.SUPPORTED_ABIS.firstOrNull(), features)
    }

    private fun directory() = File(appContext.noBackupFilesDir, "ai-runtime/$AI_RUNTIME_VERSION")
    private fun libraryFile(artifact: AiRuntimeArtifact) = File(directory(), artifact.fileName)

    private suspend fun <T> withCancellableCall(call: Call, block: suspend (Response) -> T): T = coroutineScope {
        // WorkManagerのキャンセル時は、readTimeoutを待たずにソケットも閉じる。
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try { call.execute().use { block(it) } } finally { watcher.cancel() }
    }

    suspend fun availability(): AiRuntimeAvailability = withContext(Dispatchers.IO) {
        mutex.withLock {
            val artifact = artifact() ?: return@withLock AiRuntimeAvailability.UNSUPPORTED
            if (verifyAiRuntimeFile(libraryFile(artifact), artifact)) AiRuntimeAvailability.DOWNLOADED
            else if (BuildConfig.BUNDLED_AI_RUNTIME) AiRuntimeAvailability.BUNDLED
            else AiRuntimeAvailability.MISSING
        }
    }

    suspend fun prepareForInference() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val artifact = artifact()
                ?: throw IllegalStateException(appContext.getString(R.string.ai_runtime_unsupported))
            try {
                val file = libraryFile(artifact)
                if (verifyAiRuntimeFile(file, artifact)) {
                    check(file.setReadOnly()) { "Cannot protect AI runtime" }
                    LlamaNativeLoader.loadDownloaded(file)
                } else if (BuildConfig.BUNDLED_AI_RUNTIME) {
                    LlamaNativeLoader.loadBundled(artifact.libraryName)
                } else {
                    throw IllegalStateException(appContext.getString(R.string.ai_runtime_required))
                }
            } catch (error: LinkageError) {
                // クラス初期化の前にロードすることで、失敗後も取得・再試行できる。
                throw IllegalStateException(appContext.getString(R.string.ai_runtime_load_failed), error)
            }
        }
    }

    suspend fun download(onProgress: suspend (Int) -> Unit) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val artifact = artifact()
                ?: throw IllegalStateException(appContext.getString(R.string.ai_runtime_unsupported))
            val destination = libraryFile(artifact)
            if (verifyAiRuntimeFile(destination, artifact)) return@withLock
            check(directory().isDirectory || directory().mkdirs()) { "Cannot create AI runtime directory" }
            val archive = File(directory(), "${artifact.assetName}.partial")
            val pending = File(directory(), "${artifact.fileName}.partial")
            // OS終了で残った部分ファイルは次回取得時に作り直す。
            archive.delete()
            pending.delete()
            try {
                val request = Request.Builder().url(artifact.downloadUrl).build()
                withCancellableCall(client.newCall(request)) { response ->
                    if (response.code == 404) {
                        throw IOException(appContext.getString(R.string.ai_runtime_unpublished))
                    }
                    if (!response.isSuccessful) throw IOException(appContext.getString(R.string.ai_runtime_download_failed))
                    val body = response.body ?: throw IOException("Empty response")
                    val total = body.contentLength()
                    check(total <= 8 * 1024 * 1024) { "AI runtime archive too large" }
                    body.byteStream().use { input ->
                        archive.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var received = 0L
                            var lastProgress = -1
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count == -1) break
                                received += count
                                check(received <= 8 * 1024 * 1024) { "AI runtime archive too large" }
                                output.write(buffer, 0, count)
                                val progress = if (total > 0) (received * 95 / total).toInt().coerceIn(0, 95) else 0
                                if (progress != lastProgress) {
                                    onProgress(progress)
                                    lastProgress = progress
                                }
                            }
                        }
                    }
                }
                val coroutineContext = currentCoroutineContext()
                archive.inputStream().use { input ->
                    extractVerifiedAiRuntime(input, pending, artifact) { coroutineContext.ensureActive() }
                }
                coroutineContext.ensureActive()
                // 同じディレクトリ内のrenameで、検証途中のファイルを公開しない。
                if (destination.exists()) check(destination.delete()) { "Cannot replace invalid AI runtime" }
                check(pending.renameTo(destination)) { "Cannot install AI runtime" }
                onProgress(100)
            } finally {
                archive.delete()
                pending.delete()
            }
        }
    }
}
