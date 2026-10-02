package jp.linkserver.nittcsc.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.logic.AI_RUNTIME_VERSION
import jp.linkserver.nittcsc.ml.AiRuntimeAvailability
import jp.linkserver.nittcsc.ml.AiRuntimeDownloadWorker
import jp.linkserver.nittcsc.ml.AiRuntimeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AiRuntimeUiState(
    val availability: AiRuntimeAvailability = AiRuntimeAvailability.CHECKING,
    val downloading: Boolean = false,
    val progress: Int = 0,
    val errorRes: Int? = null
) {
    val canInfer: Boolean get() = availability == AiRuntimeAvailability.BUNDLED ||
        availability == AiRuntimeAvailability.DOWNLOADED
}

class AiRuntimeViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = AiRuntimeManager(application)
    private val workManager = WorkManager.getInstance(application)
    private val workName = "download-$AI_RUNTIME_VERSION"
    private val mutableState = MutableStateFlow(AiRuntimeUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(workName).collect { infos ->
                val work = infos.firstOrNull()
                val active = work?.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)
                // 取得中はmanagerのロックを待たず、進捗をそのまま表示する。
                val availability = if (active) mutableState.value.availability else manager.availability()
                mutableState.value = AiRuntimeUiState(
                    availability = availability,
                    downloading = active,
                    progress = work?.progress?.getInt("progress", 0) ?: 0,
                    errorRes = if (work?.state == WorkInfo.State.FAILED) {
                        if (work.outputData.getString("error_code") == "unpublished") R.string.ai_runtime_unpublished
                        else R.string.ai_runtime_download_failed
                    } else null
                )
            }
        }
    }

    fun download() {
        if (mutableState.value.downloading) return
        mutableState.value = mutableState.value.copy(downloading = true, errorRes = null, progress = 0)
        val request = OneTimeWorkRequestBuilder<AiRuntimeDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)
    }

    fun cancelDownload() {
        workManager.cancelUniqueWork(workName)
    }
}
