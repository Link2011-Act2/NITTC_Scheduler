package jp.linkserver.nittcsc.logic

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ReminderDeliveryGateTest {
    @Test fun alarmFallbackAndReschedulingDeliverOnlyOnce() = runBlocking {
        var persisted: String? = null
        val count = AtomicInteger()
        val gate = ReminderDeliveryGate()
        coroutineScope {
            List(10) { async(Dispatchers.Default) {
                gate.deliverOnce("task/1/morning/2026-11-09T08:10", { persisted }, { persisted = it }) {
                    count.incrementAndGet(); true
                }
            } }.awaitAll()
        }
        // プロセスを作り直しても、永続記録があれば再配信しない。
        ReminderDeliveryGate().deliverOnce("task/1/morning/2026-11-09T08:10", { persisted }, { persisted = it }) {
            count.incrementAndGet(); true
        }
        assertEquals(1, count.get())
    }

    @Test fun deniedPermissionDoesNotConsumeNotificationAndChangedDateCanDeliver() = runBlocking {
        var persisted: String? = null
        val gate = ReminderDeliveryGate()
        assertFalse(gate.deliverOnce("old", { persisted }, { persisted = it }) { false })
        assertNull(persisted)
        assertTrue(gate.deliverOnce("old", { persisted }, { persisted = it }) { true })
        assertTrue(gate.deliverOnce("new-date", { persisted }, { persisted = it }) { true })
    }
}
