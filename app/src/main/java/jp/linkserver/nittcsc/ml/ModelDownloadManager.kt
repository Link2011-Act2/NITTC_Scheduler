package jp.linkserver.nittcsc.ml

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Float, val speedMbps: Float) : DownloadState()
    data object Success : DownloadState()
    data class Error(val exception: Throwable) : DownloadState()
}

class ModelDownloadManager(private val context: Context) {
    private val client = getUnsafeOkHttpClient()
    private val incompleteSuffix = ".partial"

    private fun getUnsafeOkHttpClient(): OkHttpClient {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            return OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .writeTimeout(5, TimeUnit.MINUTES)
                .callTimeout(60, TimeUnit.MINUTES)
                .build()
        } catch (e: Exception) {
            return OkHttpClient()
        }
    }

    /**
     * Downloads a model file from the specified URL into internal storage.
     */
    fun downloadModel(modelUrl: String, fileName: String, authToken: String? = null): Flow<DownloadState> = flow {
        try {
            emit(DownloadState.Downloading(0f, 0f))
            
            val requestBuilder = Request.Builder().url(modelUrl)
            if (!authToken.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $authToken")
            }
            val request = requestBuilder.build()
            val call = client.newCall(request)
            
            val response = call.execute()
            
            if (!response.isSuccessful) {
                throw Exception("Failed to download model: ${response.code}")
            }
            
            val body = response.body ?: throw Exception("Empty response body")
            val totalBytes = body.contentLength()
            var bytesDownloaded = 0L
            
            val dir = File(context.filesDir, "models")
            if (!dir.exists()) dir.mkdirs()
            
            val file = File(dir, fileName)
            val tempFile = File(dir, "$fileName$incompleteSuffix")
            if (tempFile.exists()) tempFile.delete()
            val startTime = System.currentTimeMillis()
            var lastUpdateTime = startTime

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead = 0
                    while (currentCoroutineContext().isActive && input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        
                        val currentTime = System.currentTimeMillis()
                        val elapsedSeconds = (currentTime - startTime) / 1000f
                        
                        if (currentTime - lastUpdateTime > 500) { // Update every 500ms
                            val speedMbps = if (elapsedSeconds > 0) {
                                (bytesDownloaded * 8f) / (1024f * 1024f * elapsedSeconds)
                            } else 0f
                            
                            val progress = if (totalBytes > 0) {
                                (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            
                            // タイムアウト条件：2時間（7200秒）経過＆平均速度が0.1Mbps以下
                            if (elapsedSeconds > 7200 && speedMbps < 0.1f) {
                                call.cancel()
                                tempFile.delete()
                                throw Exception("ダウンロードがタイムアウトしました: 2時間以上経っており、回線速度が0.1Mbps以下です")
                            }
                            
                            emit(DownloadState.Downloading(progress, speedMbps))
                            lastUpdateTime = currentTime
                        }
                    }
                    
                    if (!currentCoroutineContext().isActive) {
                        call.cancel()
                        tempFile.delete()
                        throw CancellationException("Download cancelled")
                    }
                }
            }

            if (totalBytes > 0 && bytesDownloaded != totalBytes) {
                tempFile.delete()
                throw Exception("Downloaded file is incomplete")
            }

            if (file.exists() && !file.delete()) {
                tempFile.delete()
                throw Exception("Failed to replace existing model file")
            }
            if (!tempFile.renameTo(file)) {
                tempFile.delete()
                throw Exception("Failed to finalize downloaded model")
            }
            
            emit(DownloadState.Success)
        } catch (e: CancellationException) {
            cleanupIncompleteFile(fileName)
            throw e
        } catch (e: Exception) {
            cleanupIncompleteFile(fileName)
            emit(DownloadState.Error(e))
        }
    }.flowOn(Dispatchers.IO)
    
    fun getDownloadedModels(): List<File> {
        val dir = File(context.filesDir, "models")
        return if (dir.exists()) {
            dir.listFiles()
                ?.filter { it.isFile && !it.name.endsWith(incompleteSuffix) }
                ?.toList()
                ?: emptyList()
        } else emptyList()
    }
    
    fun deleteModel(fileName: String): Boolean {
        val file = File(context.filesDir, "models/$fileName")
        val partialFile = File(context.filesDir, "models/$fileName$incompleteSuffix")
        val deletedMain = !file.exists() || file.delete()
        val deletedPartial = !partialFile.exists() || partialFile.delete()
        return deletedMain && deletedPartial
    }

    fun deleteModels(fileNames: Collection<String>): Boolean =
        fileNames.fold(true) { acc, fileName -> deleteModel(fileName) && acc }
    
    /**
     * Builds a OneTimeWorkRequest for downloading a single file.
     * Used for chaining multiple downloads as a unique work sequence.
     */
    fun buildDownloadRequest(
        modelUrl: String,
        fileName: String,
        authToken: String? = null,
        modelId: String,
        modelName: String,
        assetIndex: Int,
        assetCount: Int
    ): OneTimeWorkRequest {
        val inputData = Data.Builder()
            .putString("model_url", modelUrl)
            .putString("file_name", fileName)
            .putString("model_id", modelId)
            .putString("model_name", modelName)
            .putInt("asset_index", assetIndex)
            .putInt("asset_count", assetCount)
            .apply { if (!authToken.isNullOrBlank()) putString("auth_token", authToken) }
            .build()
        return OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(inputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("model_download")
            .addTag("model_download_$fileName")
            .addTag("model_dl_$modelId")
            .build()
    }

    /**
     * Downloads a model file in the background using WorkManager.
     * The download continues even if the app is closed and shows notification progress.
     */
    fun downloadModelInBackground(
        modelUrl: String,
        fileName: String,
        authToken: String? = null
    ): String {
        val inputData = Data.Builder()
            .putString("model_url", modelUrl)
            .putString("file_name", fileName)
            .apply {
                if (!authToken.isNullOrBlank()) {
                    putString("auth_token", authToken)
                }
            }
            .build()

        val downloadRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(inputData)
            .addTag("model_download")
            .addTag(fileName)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "model_download_$fileName",
            androidx.work.ExistingWorkPolicy.KEEP,
            downloadRequest
        )

        return downloadRequest.id.toString()
    }
    
    /**
     * Gets the work status for a background download.
     */
    fun getBackgroundDownloadStatus(fileName: String) = 
        WorkManager.getInstance(context).getWorkInfosByTagLiveData("model_download_$fileName")

    private fun cleanupIncompleteFile(fileName: String) {
        File(context.filesDir, "models/$fileName$incompleteSuffix").delete()
    }
}
