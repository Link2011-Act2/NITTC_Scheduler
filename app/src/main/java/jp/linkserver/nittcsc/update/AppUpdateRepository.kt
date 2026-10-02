package jp.linkserver.nittcsc.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class AppUpdateState(
    val availableUpdate: AppUpdateInfo? = null,
    val notificationUpdate: AppUpdateInfo? = null,
    val showBanner: Boolean = false
)

internal interface AppUpdateStore {
    fun loadUpdate(): AppUpdateInfo?
    fun shouldCheck(): Boolean
    fun saveSuccessfulCheck(update: AppUpdateInfo?)
    fun isDismissed(tagName: String): Boolean
    fun dismiss(tagName: String)
}

class AppUpdateRepository internal constructor(
    private val store: AppUpdateStore,
    private val checkRelease: suspend () -> Result<AppUpdateInfo?>
) {
    private val _state = MutableStateFlow(AppUpdateState())
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()
    private val checkMutex = Mutex()
    private var hiddenBannerTag: String? = null

    suspend fun check(force: Boolean = false): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        checkMutex.withLock {
            // 起動時の復元と、設定からの抑止解除・確認用バージョン変更を反映する。
            publish(store.loadUpdate())
            if (!force && !store.shouldCheck()) {
                return@withLock Result.success(_state.value.availableUpdate)
            }
            val result = checkRelease()
            // キャンセルは確認失敗として扱わず、そのまま呼び出し元へ伝える。
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            result.onSuccess { update ->
                store.saveSuccessfulCheck(update)
                publish(update)
            }
        }
    }

    @Synchronized
    fun hideBanner() {
        hiddenBannerTag = _state.value.notificationUpdate?.tagName
        publish(_state.value.availableUpdate)
    }

    @Synchronized
    fun ignoreVersion(tagName: String) {
        store.dismiss(tagName)
        publish(_state.value.availableUpdate)
    }

    @Synchronized
    private fun publish(update: AppUpdateInfo?) {
        val notification = update?.takeUnless { store.isDismissed(it.tagName) }
        _state.value = AppUpdateState(
            availableUpdate = update,
            notificationUpdate = notification,
            showBanner = notification != null &&
                !notification.tagName.equals(hiddenBannerTag, ignoreCase = true)
        )
    }
}
