package jp.linkserver.nittcsc.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import jp.linkserver.nittcsc.MainActivity
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.AppDatabase
import jp.linkserver.nittcsc.data.EffectiveSchoolSchedule
import jp.linkserver.nittcsc.data.LessonStartNotificationChipMode
import jp.linkserver.nittcsc.data.LessonNotificationExclusionEntity
import jp.linkserver.nittcsc.data.ResolvedLesson
import jp.linkserver.nittcsc.data.SettingsEntity
import jp.linkserver.nittcsc.logic.ClassSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class LessonStartNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val date = runCatching { LocalDate.parse(inputData.getString(KEY_DATE).orEmpty()) }
            .getOrNull()
            ?: return Result.success()
        val slotIndex = inputData.getInt(KEY_SLOT_INDEX, -1)
        if (slotIndex < 0) return Result.success()

        if (!applicationContext.canPostAppNotifications()) return Result.success()
        val schedule = EffectiveSchoolSchedule.load(AppDatabase.getInstance(applicationContext).schedulerDao())
            ?: return Result.success()
        val settings = schedule.settings
        if (!settings.lessonStartNotificationEnabled) return Result.success()
        val candidate = schedule.lessonsOn(date).firstOrNull { it.slotIndex == slotIndex } ?: return Result.success()
        val exclusions = AppDatabase.getInstance(applicationContext).schedulerDao().getLessonNotificationExclusionsOnce()
        val lesson = candidate.content
        if (isExcluded(lesson, exclusions)) return Result.success()
        val live = inputData.getBoolean(KEY_LIVE, false)
        val signature = signature(candidate, settings, live)
        if (inputData.getString(KEY_SIGNATURE) != signature) return Result.success()
        val slot = ClassSlot(candidate.slotIndex, candidate.slotLabel, candidate.start, candidate.end)
        val actualLessonStart = candidate.startsAt
        val minutesBefore = settings.lessonStartNotificationMinutesBefore.coerceIn(0, 360)
        val scheduledAt = notificationAt(candidate, settings, live)
        val now = LocalDateTime.now()
        if (now.isBefore(scheduledAt) || now.isAfter(actualLessonStart.plusMinutes(1))) return Result.success()
        createNotificationChannel()
        val notificationId = notificationId(date, slotIndex, live)
        val pendingIntent = PendingIntent.getActivity(applicationContext, notificationId,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val timing = jp.linkserver.nittcsc.logic.lessonReminderTiming(actualLessonStart, minutesBefore,
            settings.lessonStartNotificationLiveUpdatesEnabled, Build.VERSION.SDK_INT >= 36,
            settings.lessonStartNotificationLiveUpdateEarlyMinutes)
        val liveAvailable = timing.liveAt != null && canPostPromotedNotifications()
        val standardSignature = signature(candidate, settings, false)
        val coordinator = LessonNotificationDeliveryCoordinator(applicationContext, "$date|$slotIndex",
            timing.standardAt.toString(), signature(candidate, settings, true), notificationId(date, slotIndex, true), deliveryGate)
        if (live) {
            val target = timing.liveEndsAt ?: return Result.success()
            if (!liveAvailable || !now.isBefore(target)) return Result.success()
            var completed = false
            try {
                completed = showLiveUpdateCountdown(notificationId, lesson, slot, target, minutesBefore,
                    settings.lessonStartNotificationProgressCountsDown, settings.lessonStartNotificationChipMode,
                    pendingIntent, coordinator)
            } finally {
                // WorkManagerの停止・Foreground化失敗でも、成功記録を残して通常通知を失わない。
                withContext(NonCancellable) {
                    if (coordinator.finishLive(id.toString(), completed)) {
                        deliveryGate.exclusively {
                            enqueueRecovery(applicationContext, date, slotIndex, standardSignature, timing.standardAt)
                        }
                    }
                }
            }
        } else {
            coordinator.deliverStandard(liveAvailable, Duration.between(timing.standardAt, now).toMillis(),
                beforeDecision = { clearRecoveryCheck(applicationContext, date, slotIndex, standardSignature, inputData.getLong(KEY_RECOVERY_AT, 0)) },
                scheduleRecovery = { enqueueRecovery(applicationContext, date, slotIndex, standardSignature, LocalDateTime.now().plusSeconds(30)) },
                cancelLiveWork = {
                    val manager = WorkManager.getInstance(applicationContext)
                    manager.cancelUniqueWork(uniqueWorkName(date, slotIndex, true))
                    manager.cancelUniqueWork(uniqueWorkName(date, slotIndex, true) + "_delivery")
                },
                post = {
                    notifySafely(notificationId, buildStandardNotification(
                        displayMinutesBefore(minutesBefore, Duration.between(LocalDateTime.now(), actualLessonStart).toMillis()),
                        lesson, slot, pendingIntent))
                })
        }
        return Result.success()
    }

    private fun buildStandardNotification(
        minutesBefore: Int,
        lesson: ResolvedLesson,
        slot: ClassSlot,
        pendingIntent: PendingIntent
    ): Notification {
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_person_raised_hand)
            .setContentTitle(applicationContext.getString(R.string.lesson_start_notification_title))
            .setContentText(
                buildLessonStartText(minutesBefore, lesson.subject)
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    buildNotificationBody(minutesBefore, lesson, slot)
                )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private suspend fun showLiveUpdateCountdown(
        notificationId: Int,
        lesson: ResolvedLesson,
        slot: ClassSlot,
        liveUpdateTarget: LocalDateTime,
        minutesBefore: Int,
        progressCountsDown: Boolean,
        chipMode: LessonStartNotificationChipMode,
        pendingIntent: PendingIntent,
        coordinator: LessonNotificationDeliveryCoordinator
    ): Boolean {
        val lessonStartMillis = liveUpdateTarget
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val totalSeconds = (minutesBefore * 60).coerceAtLeast(1)
        var remainingMillis = Duration.between(
            LocalDateTime.now(ZoneId.systemDefault()),
            liveUpdateTarget
        ).toMillis()

        while (remainingMillis > 0L && !isStopped) {
            val notification = buildLiveUpdateNotification(
                lesson = lesson,
                slot = slot,
                lessonStartMillis = lessonStartMillis,
                remainingMillis = remainingMillis,
                totalSeconds = totalSeconds,
                progressCountsDown = progressCountsDown,
                chipMode = chipMode,
                pendingIntent = pendingIntent
            )
            if (!coordinator.postLive(id.toString()) {
                withTimeoutOrNull(10_000) {
                    canPostPromotedNotifications() && setForegroundSafely(notificationId, notification) &&
                        notifySafely(notificationId, notification)
                } ?: false
            }) return false

            delay(nextLiveUpdateDelayMillis(remainingMillis))
            remainingMillis = Duration.between(
                LocalDateTime.now(ZoneId.systemDefault()),
                liveUpdateTarget
            ).toMillis()
        }
        return !isStopped && remainingMillis <= 0
    }

    @Suppress("NewApi")
    private fun buildLiveUpdateNotification(
        lesson: ResolvedLesson,
        slot: ClassSlot,
        lessonStartMillis: Long,
        remainingMillis: Long,
        totalSeconds: Int,
        progressCountsDown: Boolean,
        chipMode: LessonStartNotificationChipMode,
        pendingIntent: PendingIntent
    ): Notification {
        val remainingSeconds = remainingSeconds(remainingMillis).coerceIn(0, totalSeconds)
        val progress = if (progressCountsDown) {
            remainingSeconds
        } else {
            totalSeconds - remainingSeconds
        }.coerceIn(0, totalSeconds)

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_person_raised_hand)
            .setContentTitle(
                applicationContext.getString(
                    R.string.lesson_start_live_update_title,
                    lesson.subject
                )
            )
            .setContentText(applicationContext.getString(R.string.lesson_start_live_update_body, lesson.subject))
            .setStyle(
                NotificationCompat.ProgressStyle()
                    .addProgressSegment(NotificationCompat.ProgressStyle.Segment(totalSeconds))
                    .setProgress(progress)
                    .setStyledByProgress(true)
            )
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setWhen(lessonStartMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setSettingsText(
                applicationContext.getString(
                    R.string.lesson_start_notification_time_summary,
                    slot.label,
                    String.format("%02d:%02d", slot.start.hour, slot.start.minute)
                )
            )
            .setRequestPromotedOngoing(true)

        if (chipMode == LessonStartNotificationChipMode.MINUTE_TEXT) {
            builder.setShortCriticalText(buildLiveUpdateMinuteChipText(remainingMillis))
        }

        return builder.build()
    }

    private fun canPostPromotedNotifications(): Boolean {
        return runCatching {
            NotificationManagerCompat.from(applicationContext).canPostPromotedNotifications()
        }.onFailure {
            Log.w(TAG, "Cannot check promoted notification availability", it)
        }.getOrDefault(false)
    }

    private suspend fun setForegroundSafely(notificationId: Int, notification: Notification): Boolean {
        try {
            setForeground(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ForegroundInfo(
                        notificationId,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    ForegroundInfo(notificationId, notification)
                }
            )
            return true
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Cannot promote live update worker; standard reminder remains scheduled", error)
            return false
        }
    }

    private fun notifySafely(notificationId: Int, notification: Notification): Boolean {
        if (!applicationContext.canPostAppNotifications()) return false
        try {
            NotificationManagerCompat.from(applicationContext)
                .notify(notificationId, notification)
            return true
        } catch (_: SecurityException) {
            // The POST_NOTIFICATIONS permission can be revoked independently of this feature.
        }
        return false
    }

    private fun nextLiveUpdateDelayMillis(remainingMillis: Long): Long {
        val nextDelay = if (remainingMillis <= LIVE_UPDATE_FAST_REFRESH_THRESHOLD_MS) {
            minOf(LIVE_UPDATE_SMOOTH_REFRESH_MS, remainingMillis)
        } else {
            minOf(ONE_MINUTE_MS, remainingMillis - LIVE_UPDATE_FAST_REFRESH_THRESHOLD_MS)
        }
        return minOf(nextDelay, LIVE_UPDATE_KEEP_ALIVE_MS).coerceAtLeast(1_000L)
    }

    private fun remainingSeconds(remainingMillis: Long): Int {
        return ((remainingMillis + 999L) / 1_000L).toInt().coerceAtLeast(1)
    }

    private fun buildLiveUpdateMinuteChipText(remainingMillis: Long): String {
        val remainingSeconds = remainingSeconds(remainingMillis)
        return if (remainingMillis < ONE_MINUTE_MS) {
            applicationContext.getString(
                R.string.lesson_start_live_update_chip_seconds,
                remainingSeconds
            )
        } else {
            applicationContext.getString(
                R.string.lesson_start_live_update_chip_minutes,
                (remainingMillis / ONE_MINUTE_MS).toInt().coerceAtLeast(1)
            )
        }
    }

    private fun displayMinutesBefore(
        configuredMinutesBefore: Int,
        remainingMillis: Long
    ): Int {
        if (remainingMillis <= 0L) return 0
        if (configuredMinutesBefore <= 0) return 0
        return ceil(remainingMillis / ONE_MINUTE_MS.toDouble())
            .toInt()
            .coerceIn(1, configuredMinutesBefore)
    }

    private fun buildLiveLessonStartText(remainingSeconds: Int, subject: String): String {
        return if (remainingSeconds < 60) {
            applicationContext.getString(
                R.string.lesson_start_notification_body_seconds,
                remainingSeconds,
                subject
            )
        } else {
            applicationContext.getString(
                R.string.lesson_start_notification_body,
                ceil(remainingSeconds / 60.0).toInt().coerceAtLeast(1),
                subject
            )
        }
    }

    private fun buildNotificationBody(
        minutesBefore: Int,
        lesson: ResolvedLesson,
        slot: ClassSlot
    ): String {
        return buildString {
            append(buildLessonStartText(minutesBefore, lesson.subject))
            append("\n")
            append(
                applicationContext.getString(
                    R.string.lesson_start_notification_time_summary,
                    slot.label,
                    String.format("%02d:%02d", slot.start.hour, slot.start.minute)
                )
            )
            if (lesson.teacher.isNotBlank()) {
                append("\n")
                append(applicationContext.getString(R.string.lesson_start_notification_teacher_summary, lesson.teacher))
            }
            if (!lesson.location.isNullOrBlank()) {
                append("\n")
                append(applicationContext.getString(R.string.lesson_start_notification_location_summary, lesson.location))
            }
        }
    }

    private fun buildLessonStartText(minutesBefore: Int, subject: String): String {
        return if (minutesBefore <= 0) {
            applicationContext.getString(R.string.lesson_start_notification_body_now, subject)
        } else {
            applicationContext.getString(
                R.string.lesson_start_notification_body,
                minutesBefore,
                subject
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.lesson_start_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = applicationContext.getString(R.string.lesson_start_notification_channel_desc)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "lesson_start_notifications"
        private const val KEY_DATE = "date"
        private const val KEY_SLOT_INDEX = "slot_index"
        private const val KEY_RECOVERY_AT = "recovery_at"
        private const val RECOVERY_PREFERENCES = "lesson_notification_recovery_checks"
        internal const val KEY_LIVE = "live_update"
        internal const val KEY_SIGNATURE = "schedule_signature"
        private const val TAG = "LessonStartNotification"
        private const val WORK_TAG = "lesson_start_notifications"
        private const val HORIZON_DAYS = 30L
        private const val ONE_MINUTE_MS = 60_000L
        private const val LIVE_UPDATE_FAST_REFRESH_THRESHOLD_MS = 10 * ONE_MINUTE_MS
        private const val LIVE_UPDATE_SMOOTH_REFRESH_MS = 2_000L
        private const val LIVE_UPDATE_KEEP_ALIVE_MS = 15_000L
        private const val ALARM_PREFERENCES = "lesson_start_notification_alarms"
        private const val ALARM_KEYS = "scheduled_alarm_keys"
        private const val PLAN_KEY = "schedule_v2"
        private val rescheduleMutex = Mutex()
        private val deliveryGate = jp.linkserver.nittcsc.logic.ReminderDeliveryGate()

        private fun signature(candidate: jp.linkserver.nittcsc.logic.UpcomingLessonCandidate<ResolvedLesson>,
            settings: SettingsEntity, live: Boolean): String = listOf("countdown-end-v1", candidate, live,
            settings.lessonStartNotificationMinutesBefore,
            if (live) listOf(settings.lessonStartNotificationLiveUpdatesEnabled,
                settings.lessonStartNotificationLiveUpdateEarlyMinutes,
                settings.lessonStartNotificationProgressCountsDown, settings.lessonStartNotificationChipMode) else "standard"
        ).toString()

        private fun notificationAt(candidate: jp.linkserver.nittcsc.logic.UpcomingLessonCandidate<ResolvedLesson>,
            settings: SettingsEntity, live: Boolean): LocalDateTime {
            val timing = jp.linkserver.nittcsc.logic.lessonReminderTiming(candidate.startsAt,
                settings.lessonStartNotificationMinutesBefore, settings.lessonStartNotificationLiveUpdatesEnabled,
                Build.VERSION.SDK_INT >= 36, settings.lessonStartNotificationLiveUpdateEarlyMinutes)
            return if (live) timing.liveAt ?: timing.standardAt else timing.standardAt
        }


        /** 変更のないWorkerはKEEPし、実行中のカウントダウンを再登録で停止しない。 */
        suspend fun rescheduleAll(context: Context, forceWorkUpdate: Boolean = false) = rescheduleMutex.withLock {
            val appContext = context.applicationContext
            val dao = AppDatabase.getInstance(appContext).schedulerDao()
            val schedule = EffectiveSchoolSchedule.load(dao) ?: return@withLock
            val settings = schedule.settings
            val preferences = appContext.getSharedPreferences(ALARM_PREFERENCES, Context.MODE_PRIVATE)
            val workManager = WorkManager.getInstance(appContext)
            if (!preferences.contains(PLAN_KEY)) {
                // 旧版の一体型WorkerとAlarmだけを、一度限り解除する。
                withContext(Dispatchers.IO) { workManager.cancelAllWorkByTag(WORK_TAG).result.get() }
                preferences.getStringSet(ALARM_KEYS, emptySet()).orEmpty().forEach { key ->
                    val parts = key.split('|')
                    val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return@forEach
                    val index = parts.getOrNull(1)?.toIntOrNull() ?: return@forEach
                    val legacyIntent = Intent(appContext, LessonStartNotificationAlarmReceiver::class.java).apply {
                        data = Uri.parse("nittcsc://lesson-start-notification/$date/$index")
                    }
                    val legacyId = (40_000 + (date.toEpochDay() % 10_000).toInt() * 10 + index).coerceAtLeast(40_000)
                    val legacyAlarm = PendingIntent.getBroadcast(appContext, legacyId, legacyIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    ExactReminderAlarmScheduler.cancel(appContext, legacyAlarm)
                    NotificationManagerCompat.from(appContext).cancel(legacyId)
                }
                preferences.edit().remove(ALARM_KEYS).apply()
            }
            val previous = org.json.JSONObject(preferences.getString(PLAN_KEY, "{}").orEmpty())
            val next = org.json.JSONObject()
            val today = LocalDate.now()
            val now = LocalDateTime.now()
            val exclusions = dao.getLessonNotificationExclusionsOnce()
            if (settings.lessonStartNotificationEnabled) {
                val end = minOf(settings.termEnd, today.plusDays(HORIZON_DAYS))
                var date = maxOf(today, settings.termStart)
                while (date <= end) {
                    schedule.lessonsOn(date).filter { !isExcluded(it.content, exclusions) }.forEach { candidate ->
                        if (candidate.startsAt.plusMinutes(1).isBefore(now)) return@forEach
                        for (live in listOf(false, true)) {
                            if (live && (Build.VERSION.SDK_INT < 36 || !settings.lessonStartNotificationLiveUpdatesEnabled ||
                                settings.lessonStartNotificationMinutesBefore <= 0)) continue
                            val at = notificationAt(candidate, settings, live)
                            val key = alarmKey(date, candidate.slotIndex, live)
                            val signature = signature(candidate, settings, live)
                            val planSignature = "$signature|${ZoneId.systemDefault()}"
                            if (!live && previous.optString(key) != planSignature) {
                                workManager.cancelUniqueWork(uniqueWorkName(date, candidate.slotIndex, false) + "_recovery")
                            }
                            next.put(key, planSignature)
                            val exact = ExactReminderAlarmScheduler.schedule(appContext, at,
                                alarmPendingIntent(appContext, date, candidate.slotIndex, live, signature))
                            val fallbackAt = if (exact) at.plusMinutes(1) else at
                            val request = OneTimeWorkRequestBuilder<LessonStartNotificationWorker>()
                                .setInputData(workData(date, candidate.slotIndex, live, signature))
                                .setInitialDelay(Duration.between(now, fallbackAt).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
                                .addTag(WORK_TAG).build()
                            workManager.enqueueUniqueWork(uniqueWorkName(date, candidate.slotIndex, live),
                                if (previous.optString(key) == planSignature && (!forceWorkUpdate || !at.isAfter(now)))
                                    ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE, request)
                        }
                    }
                    date = date.plusDays(1)
                }
            }
            previous.keys().forEach { key ->
                if (!next.has(key)) {
                    val parts = key.split('|')
                    val date = LocalDate.parse(parts[0])
                    val index = parts[1].toInt()
                    val live = parts[2].toBoolean()
                    workManager.cancelUniqueWork(uniqueWorkName(date, index, live))
                    workManager.cancelUniqueWork(uniqueWorkName(date, index, live) + "_delivery")
                    if (!live) workManager.cancelUniqueWork(uniqueWorkName(date, index, false) + "_recovery")
                    ExactReminderAlarmScheduler.cancel(appContext, alarmPendingIntent(appContext, date, index, live, null))
                    // 終了済みの通常通知は残す。有効な授業の変更・OFF時には取り消す。
                    if (live || date >= today && !wasAnyDelivered(appContext, date, index))
                        NotificationManagerCompat.from(appContext).cancel(notificationId(date, index, live))
                }
            }
            preferences.edit().putString(PLAN_KEY, next.toString()).apply()
        }

        fun enqueueNow(context: Context, date: LocalDate, slotIndex: Int, live: Boolean = false, signature: String? = null) {
            if (signature == null) return
            val builder = OneTimeWorkRequestBuilder<LessonStartNotificationWorker>()
                .setInputData(workData(date, slotIndex, live, signature)).addTag(WORK_TAG)
            if (Build.VERSION.SDK_INT >= 31) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                uniqueWorkName(date, slotIndex, live) + "_delivery", ExistingWorkPolicy.KEEP, builder.build())
        }

        private fun enqueueRecovery(context: Context, date: LocalDate, index: Int, signature: String, notBefore: LocalDateTime) {
            val preferences = context.getSharedPreferences(RECOVERY_PREFERENCES, Context.MODE_PRIVATE)
            val key = "$date|$index|$signature"
            val now = System.currentTimeMillis()
            val pending = preferences.getLong(key, 0)
            if (pending > now - 60_000 && pending != 0L) return
            val at = maxOf(now + 1_000, notBefore.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            val data = Data.Builder().putAll(workData(date, index, false, signature)).putLong(KEY_RECOVERY_AT, at).build()
            val request = OneTimeWorkRequestBuilder<LessonStartNotificationWorker>().setInputData(data)
                .setInitialDelay(at - now, TimeUnit.MILLISECONDS).addTag(WORK_TAG).build()
            // 自分が再確認Workerの場合も、実行中の処理をキャンセルせず次の確認をつなぐ。
            WorkManager.getInstance(context).enqueueUniqueWork(uniqueWorkName(date, index, false) + "_recovery",
                ExistingWorkPolicy.APPEND_OR_REPLACE, request)
            preferences.edit().putLong(key, at).apply()
        }

        private fun clearRecoveryCheck(context: Context, date: LocalDate, index: Int, signature: String, at: Long) {
            if (at == 0L) return
            val preferences = context.getSharedPreferences(RECOVERY_PREFERENCES, Context.MODE_PRIVATE)
            val key = "$date|$index|$signature"
            if (preferences.getLong(key, 0) == at) preferences.edit().remove(key).apply()
        }

        private fun workData(date: LocalDate, index: Int, live: Boolean, signature: String): Data = Data.Builder()
            .putString(KEY_DATE, date.toString()).putInt(KEY_SLOT_INDEX, index)
            .putBoolean(KEY_LIVE, live).putString(KEY_SIGNATURE, signature).build()

        private fun alarmPendingIntent(context: Context, date: LocalDate, slotIndex: Int, live: Boolean, signature: String?): PendingIntent {
            val intent = Intent(context, LessonStartNotificationAlarmReceiver::class.java).apply {
                data = Uri.parse("nittcsc://lesson-start-notification/$date/$slotIndex" + if (live) "/live" else "")
                putExtra(LessonStartNotificationAlarmReceiver.EXTRA_DATE, date.toString())
                putExtra(LessonStartNotificationAlarmReceiver.EXTRA_SLOT_INDEX, slotIndex)
                putExtra(KEY_LIVE, live)
                putExtra(KEY_SIGNATURE, signature)
            }
            return PendingIntent.getBroadcast(context, notificationId(date, slotIndex, live), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun alarmKey(date: LocalDate, index: Int, live: Boolean) = "$date|$index|$live"
        private fun uniqueWorkName(date: LocalDate, index: Int, live: Boolean) =
            "lesson_start_notification_${date}_${index}" + if (live) "_live" else ""
        private fun notificationId(date: LocalDate, index: Int, live: Boolean = false): Int =
            40_000 + (date.toEpochDay() % 10_000).toInt() * 20 + index + if (live) 1_000_000 else 0

        private fun deliveryPreferences(context: Context) = context.getSharedPreferences("lesson_notification_deliveries", Context.MODE_PRIVATE)
        private fun wasAnyDelivered(context: Context, date: LocalDate, index: Int) =
            deliveryPreferences(context).contains(alarmKey(date, index, false))

    }
}

internal fun isExcluded(
    lesson: ResolvedLesson,
    exclusions: List<LessonNotificationExclusionEntity>
): Boolean {
    return exclusions.any { exclusion ->
        if (!lesson.subject.trim().equals(exclusion.subject.trim(), ignoreCase = true)) {
            return@any false
        }
        if (!exclusion.matchTeacher) {
            return@any true
        }
        val expectedTeacher = exclusion.teacher?.trim().orEmpty()
        if (expectedTeacher.isBlank()) {
            return@any true
        }
        teacherMatches(expectedTeacher, lesson.teacher)
    }
}

private fun teacherMatches(expectedTeacher: String, actualTeacher: String): Boolean {
    if (actualTeacher.trim().equals(expectedTeacher.trim(), ignoreCase = true)) return true
    return actualTeacher
        .replace('，', '、')
        .replace(',', '、')
        .replace('　', ' ')
        .split('、', ' ')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .any { it.equals(expectedTeacher.trim(), ignoreCase = true) }
}
