package jp.linkserver.nittcsc.logic

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LessonNotificationDeliveryTest {
    private val identity = "2026-11-09T08:30"
    private val liveSignature = "early3"
    private val boot = 12
    private val now = 100_000L
    private fun decide(state: LessonDeliveryRecord, available: Boolean = true, visible: Boolean = true,
        elapsed: Long = now, currentBoot: Int = boot, sinceStandard: Long = 60_000) =
        lessonStandardDelivery(state, liveSignature, available, visible, elapsed, currentBoot, sinceStandard)
    private fun active() = LessonDeliveryRecord(identity, phase = LessonLivePhase.ACTIVE,
        liveSignature = liveSignature, owner = "live-worker", heartbeatElapsed = now, bootCount = boot)

    @Test fun successfullyPostedLiveSuppressesStandardAndNormalCompletionIsRemembered() {
        assertEquals(LessonStandardDelivery.WAIT_FOR_LIVE, decide(active()))
        assertEquals(LessonStandardDelivery.DONE, decide(active().copy(phase = LessonLivePhase.COMPLETED), visible = false))
    }

    @Test fun failedStartAndDeniedPromotionUseStandardWithoutConsumingDelivery() {
        val failed = active().copy(phase = LessonLivePhase.FAILED)
        assertEquals(LessonStandardDelivery.SEND, decide(failed, visible = false, sinceStandard = 0))
        assertEquals(LessonStandardDelivery.SEND, decide(LessonDeliveryRecord(identity), available = false, sinceStandard = 0))
        assertEquals(LessonStandardDelivery.DONE, decide(failed.copy(standardSent = true)))
    }

    @Test fun sameTimeStartWaitsForLiveButDoesNotWaitForeverForFailedWorker() {
        val empty = LessonDeliveryRecord(identity)
        assertEquals(LessonStandardDelivery.WAIT_FOR_LIVE, decide(empty, visible = false, sinceStandard = 0))
        assertEquals(LessonStandardDelivery.SEND, decide(empty, visible = false, sinceStandard = LESSON_LIVE_START_GRACE_MS))
        val starting = active().copy(phase = LessonLivePhase.STARTING)
        assertEquals(LessonStandardDelivery.WAIT_FOR_LIVE, decide(starting, visible = false))
        assertEquals(LessonStandardDelivery.SEND, decide(starting, visible = false, elapsed = now + LESSON_LIVE_START_GRACE_MS))
    }

    @Test fun missingNotificationDeadProcessRebootAndSettingChangePermitFallback() {
        assertEquals(LessonStandardDelivery.SEND, decide(active(), visible = false))
        assertEquals(LessonStandardDelivery.SEND, decide(active(), elapsed = now + LESSON_LIVE_HEARTBEAT_TIMEOUT_MS))
        assertEquals(LessonStandardDelivery.SEND, decide(active(), currentBoot = boot + 1))
        assertEquals(LessonStandardDelivery.SEND, decide(active().copy(liveSignature = "old-setting")))
    }

    @Test fun successfulStandardPreventsLateLiveAndOtherWorkerCannotTakeOverActiveLive() {
        assertFalse(active().copy(standardSent = true).canPostLive(liveSignature, "late-worker"))
        assertFalse(active().canPostLive(liveSignature, "alarm-fallback-worker"))
        assertTrue(active().canPostLive(liveSignature, "live-worker"))
        assertTrue(active().copy(phase = LessonLivePhase.FAILED).canPostLive(liveSignature, "retry-worker"))
        assertFalse(active().copy(phase = LessonLivePhase.COMPLETED).canPostLive(liveSignature, "retry-worker"))
    }

    @Test fun alarmFallbackAndLiveShareOneGateRegardlessOfArrivalOrder() = runBlocking {
        for (liveFirst in listOf(false, true)) {
            var persisted = LessonDeliveryRecord(identity)
            val gate = ReminderDeliveryGate()
            var standards = 0
            var lives = 0
            val standard: suspend () -> Unit = {
                gate.exclusively {
                    if (decide(persisted, visible = persisted.phase == LessonLivePhase.ACTIVE, sinceStandard = 0) == LessonStandardDelivery.SEND) {
                        standards++; persisted = persisted.copy(standardSent = true)
                    }
                }
            }
            val live: suspend () -> Unit = {
                gate.exclusively {
                    if (!persisted.standardSent && persisted.phase != LessonLivePhase.ACTIVE) {
                        lives++; persisted = active()
                    }
                }
            }
            if (liveFirst) { live(); standard() } else { standard(); live() }
            coroutineScope { List(10) { async { standard() } }.awaitAll() }
            assertEquals(1, lives)
            assertEquals(0, standards)
            // プロセスを再作成しても同じ永続記録で抑制できる。
            ReminderDeliveryGate().exclusively { assertEquals(LessonStandardDelivery.WAIT_FOR_LIVE, decide(persisted)) }
        }
    }
}
