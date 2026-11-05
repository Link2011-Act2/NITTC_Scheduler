package jp.linkserver.nittcsc.reminder

import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import jp.linkserver.nittcsc.logic.*
import kotlinx.coroutines.delay
import org.json.JSONObject

/** 通常通知とLive Updatesを同じロック・永続記録で調停する。通知IDは分離したままにする。 */
internal class LessonNotificationDeliveryCoordinator(
    context: Context,
    private val key: String,
    private val identity: String,
    private val liveSignature: String,
    private val liveNotificationId: Int,
    private val gate: ReminderDeliveryGate
) {
    private val preferences = context.getSharedPreferences("lesson_notification_delivery_state_v3", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("lesson_notification_deliveries", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    private fun read(): LessonDeliveryRecord {
        val json = runCatching { JSONObject(preferences.getString(key, "{}").orEmpty()) }.getOrNull()
        if (json?.optString("identity") != identity) return LessonDeliveryRecord(identity,
            standardSent = legacy.getString("$key|false", null) == identity)
        return LessonDeliveryRecord(identity, json.optBoolean("standardSent"),
            runCatching { LessonLivePhase.valueOf(json.optString("phase")) }.getOrDefault(LessonLivePhase.NONE),
            json.optString("liveSignature"), json.optString("owner"), json.optLong("heartbeatElapsed"), json.optInt("bootCount", -1))
    }

    private fun save(record: LessonDeliveryRecord) {
        val json = JSONObject().put("identity", identity).put("standardSent", record.standardSent)
            .put("phase", record.phase.name).put("liveSignature", record.liveSignature).put("owner", record.owner)
            .put("heartbeatElapsed", record.heartbeatElapsed).put("bootCount", record.bootCount)
        // 配信記録はプロセス終了後も確定している必要がある。
        check(preferences.edit().putString(key, json.toString()).commit())
    }

    private fun liveVisible(): Boolean = runCatching {
        manager.activeNotifications.any { it.id == liveNotificationId }
    }.getOrDefault(false)

    suspend fun postLive(owner: String, post: suspend () -> Boolean): Boolean = gate.exclusively {
        val state = read()
        if (!state.canPostLive(liveSignature, owner)) return@exclusively false
        val starting = state.copy(phase = LessonLivePhase.STARTING, liveSignature = liveSignature,
            owner = owner, heartbeatElapsed = SystemClock.elapsedRealtime(), bootCount = bootCount)
        if (state.phase != LessonLivePhase.ACTIVE || state.owner != owner) save(starting)
        if (!post()) {
            save(starting.copy(phase = LessonLivePhase.FAILED))
            manager.cancel(liveNotificationId)
            return@exclusively false
        }
        // notify()の呼び出しだけで配信成功にしない。システム側への登録を確認する。
        var visible = liveVisible()
        repeat(10) {
            if (!visible) { delay(100); visible = liveVisible() }
        }
        if (!visible || state.phase != LessonLivePhase.ACTIVE || state.owner != owner ||
            SystemClock.elapsedRealtime() - state.heartbeatElapsed >= 15_000) {
            save(starting.copy(phase = if (visible) LessonLivePhase.ACTIVE else LessonLivePhase.FAILED,
                heartbeatElapsed = SystemClock.elapsedRealtime()))
        }
        if (!visible) manager.cancel(liveNotificationId)
        visible
    }

    suspend fun finishLive(owner: String, completed: Boolean): Boolean = gate.exclusively {
        val state = read()
        if (state.owner != owner || state.liveSignature != liveSignature) return@exclusively false
        save(state.copy(phase = if (completed && state.phase == LessonLivePhase.ACTIVE)
            LessonLivePhase.COMPLETED else LessonLivePhase.FAILED))
        manager.cancel(liveNotificationId)
        !completed && !state.standardSent
    }

    suspend fun deliverStandard(
        liveAvailable: Boolean,
        sinceStandardAtMillis: Long,
        beforeDecision: () -> Unit,
        scheduleRecovery: () -> Unit,
        cancelLiveWork: () -> Unit,
        post: () -> Boolean
    ) = gate.exclusively {
        beforeDecision()
        val state = read()
        when (lessonStandardDelivery(state, liveSignature, liveAvailable, liveVisible(),
            SystemClock.elapsedRealtime(), bootCount, sinceStandardAtMillis)) {
            LessonStandardDelivery.DONE -> Unit
            LessonStandardDelivery.WAIT_FOR_LIVE -> scheduleRecovery()
            LessonStandardDelivery.SEND -> {
                // 古いLive Workerが次の更新で再投稿できないよう、同じロック内で所有権を外す。
                save(state.copy(phase = LessonLivePhase.FAILED, owner = ""))
                cancelLiveWork()
                manager.cancel(liveNotificationId)
                if (post()) {
                    save(state.copy(standardSent = true, phase = LessonLivePhase.FAILED, owner = ""))
                    legacy.edit().putString("$key|false", identity).commit()
                }
            }
        }
    }
}
