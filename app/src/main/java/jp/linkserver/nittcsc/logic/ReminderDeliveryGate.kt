package jp.linkserver.nittcsc.logic

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** AlarmとWorkManagerの並行実行を直列化し、再起動後も配信済みの署名を確認する。 */
class ReminderDeliveryGate {
    private val mutex = Mutex()

    suspend fun <T> exclusively(action: suspend () -> T): T = mutex.withLock { action() }

    suspend fun deliverOnce(
        signature: String,
        readDelivered: () -> String?,
        saveDelivered: (String) -> Unit,
        send: () -> Boolean
    ): Boolean = mutex.withLock {
        if (readDelivered() == signature) return@withLock false
        if (!send()) return@withLock false
        saveDelivered(signature)
        true
    }
}
