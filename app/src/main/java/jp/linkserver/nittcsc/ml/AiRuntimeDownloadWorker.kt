package jp.linkserver.nittcsc.ml

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import jp.linkserver.nittcsc.R
import kotlinx.coroutines.CancellationException

class AiRuntimeDownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        return try {
            AiRuntimeManager(applicationContext).download { progress ->
                setProgress(workDataOf("progress" to progress))
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // 通信・検証の失敗は自動で繰り返さず、画面で再試行を選べるようにする。
            val message = if (error is java.io.IOException &&
                error.message == applicationContext.getString(R.string.ai_runtime_unpublished)) {
                "unpublished"
            } else "download_failed"
            Result.failure(workDataOf("error_code" to message))
        }
    }
}
