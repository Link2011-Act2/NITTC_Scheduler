package jp.linkserver.nittcsc.ml

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.work.WorkInfo
import androidx.work.WorkManager

class ModelDownloadHelper(private val context: Context) {
    private val manager = ModelDownloadManager(context)
    private val workManager = WorkManager.getInstance(context)

    /**
     * Start a background download. Returns a LiveData that observes the download progress.
     * The download continues even if the app is closed.
     */
    fun startBackgroundDownload(
        modelUrl: String,
        fileName: String,
        authToken: String? = null
    ): LiveData<BackgroundDownloadState> {
        manager.downloadModelInBackground(modelUrl, fileName, authToken)
        
        return workManager.getWorkInfosByTagLiveData("model_download_$fileName")
            .map { workInfoList ->
                when {
                    workInfoList.isEmpty() -> BackgroundDownloadState.Idle
                    workInfoList[0].state == WorkInfo.State.RUNNING -> {
                        val progress = workInfoList[0].progress.getInt("progress", 0)
                        BackgroundDownloadState.Downloading(progress.toFloat() / 100f)
                    }
                    workInfoList[0].state == WorkInfo.State.SUCCEEDED -> {
                        BackgroundDownloadState.Success
                    }
                    workInfoList[0].state == WorkInfo.State.FAILED -> {
                        BackgroundDownloadState.Error(
                            workInfoList[0].outputData.getString("error") 
                                ?: "Unknown error"
                        )
                    }
                    workInfoList[0].state == WorkInfo.State.CANCELLED -> {
                        BackgroundDownloadState.Cancelled
                    }
                    else -> BackgroundDownloadState.Idle
                }
            }
    }

    /**
     * Cancel a background download by file name.
     */
    fun cancelDownload(fileName: String) {
        workManager.cancelUniqueWork("model_download_$fileName")
    }

    fun getDownloadedModels(): List<String> {
        return manager.getDownloadedModels().map { it.name }
    }

    fun deleteModel(fileName: String): Boolean {
        return manager.deleteModel(fileName)
    }
}

sealed class BackgroundDownloadState {
    data object Idle : BackgroundDownloadState()
    data class Downloading(val progress: Float) : BackgroundDownloadState()
    data object Success : BackgroundDownloadState()
    data class Error(val message: String) : BackgroundDownloadState()
    data object Cancelled : BackgroundDownloadState()
}
