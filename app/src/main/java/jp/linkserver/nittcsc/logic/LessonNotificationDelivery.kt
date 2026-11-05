package jp.linkserver.nittcsc.logic

enum class LessonLivePhase { NONE, STARTING, ACTIVE, COMPLETED, FAILED }

data class LessonDeliveryRecord(
    val identity: String,
    val standardSent: Boolean = false,
    val phase: LessonLivePhase = LessonLivePhase.NONE,
    val liveSignature: String = "",
    val owner: String = "",
    val heartbeatElapsed: Long = 0,
    val bootCount: Int = -1
)

enum class LessonStandardDelivery { DONE, WAIT_FOR_LIVE, SEND }

fun LessonDeliveryRecord.canPostLive(signature: String, workerOwner: String): Boolean =
    !standardSent && phase != LessonLivePhase.COMPLETED &&
        (owner == workerOwner || liveSignature != signature || phase !in listOf(LessonLivePhase.STARTING, LessonLivePhase.ACTIVE))

const val LESSON_LIVE_START_GRACE_MS = 15_000L
const val LESSON_LIVE_HEARTBEAT_TIMEOUT_MS = 45_000L

fun LessonDeliveryRecord.hasFreshLiveLease(nowElapsed: Long, currentBoot: Int): Boolean =
    bootCount == currentBoot && nowElapsed >= heartbeatElapsed &&
        nowElapsed - heartbeatElapsed < if (phase == LessonLivePhase.STARTING)
            LESSON_LIVE_START_GRACE_MS else LESSON_LIVE_HEARTBEAT_TIMEOUT_MS

/** 成功したLive Updatesだけを優先し、停止したWorkerの古い記録では抑制しない。 */
fun lessonStandardDelivery(
    record: LessonDeliveryRecord,
    liveSignature: String,
    liveAvailable: Boolean,
    liveVisible: Boolean,
    nowElapsed: Long,
    bootCount: Int,
    sinceStandardAtMillis: Long
): LessonStandardDelivery {
    if (record.standardSent || record.phase == LessonLivePhase.COMPLETED) return LessonStandardDelivery.DONE
    if (!liveAvailable) return LessonStandardDelivery.SEND
    if (record.liveSignature == liveSignature && record.hasFreshLiveLease(nowElapsed, bootCount)) {
        if (record.phase == LessonLivePhase.STARTING || record.phase == LessonLivePhase.ACTIVE && liveVisible)
            return LessonStandardDelivery.WAIT_FOR_LIVE
    }
    // 前倒し0分では通常Workerが先に起動することもある。失敗済みの起動は待たない。
    if (record.phase == LessonLivePhase.NONE && sinceStandardAtMillis < LESSON_LIVE_START_GRACE_MS)
        return LessonStandardDelivery.WAIT_FOR_LIVE
    return LessonStandardDelivery.SEND
}
