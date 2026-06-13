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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import jp.linkserver.nittcsc.MainActivity
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.AppDatabase
import jp.linkserver.nittcsc.data.ChangedLessonEntity
import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import jp.linkserver.nittcsc.data.LessonNotificationExclusionEntity
import jp.linkserver.nittcsc.data.ResolvedLesson
import jp.linkserver.nittcsc.data.SettingsEntity
import jp.linkserver.nittcsc.logic.ClassSlot
import jp.linkserver.nittcsc.logic.JapaneseHolidayCalculator
import jp.linkserver.nittcsc.logic.generateClassSlots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
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

        val dao = AppDatabase.getInstance(applicationContext).schedulerDao()
        val settings = dao.getSettings() ?: return Result.success()
        if (!settings.lessonStartNotificationEnabled) return Result.success()

        val slot = settings.classSlots().firstOrNull { it.index == slotIndex }
            ?: return Result.success()
        if (dao.getCancelledLesson(date, slotIndex) != null) return Result.success()

        val lesson = resolveEffectiveLesson(
            date = date,
            slotIndex = slotIndex,
            dayTypeEntities = dao.getDayTypesOnce().associateBy { it.date },
            lessons = dao.getLessonsOnce().associateBy { it.dayOfWeek to it.slotIndex },
            changedLessons = dao.getChangedLessonsOnce().associateBy { it.date to it.slotIndex }
        ) ?: return Result.success()
        if (lesson.subject.isBlank()) return Result.success()
        if (isExcluded(lesson, dao.getLessonNotificationExclusionsOnce())) return Result.success()

        createNotificationChannel()

        val notificationId = notificationId(date, slotIndex)
        val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val minutesBefore = settings.lessonStartNotificationMinutesBefore.coerceIn(0, 360)
        val lessonStart = LocalDateTime.of(date, slot.start)
        val remainingMillis = Duration.between(
            LocalDateTime.now(ZoneId.systemDefault()),
            lessonStart
        ).toMillis()
        if (remainingMillis < -LATE_NOTIFICATION_GRACE_MS) return Result.success()
        if (
            settings.lessonStartNotificationLiveUpdatesEnabled &&
            Build.VERSION.SDK_INT >= 36 &&
            minutesBefore > 0 &&
            canPostPromotedNotifications()
        ) {
            showLiveUpdateCountdown(
                notificationId = notificationId,
                lesson = lesson,
                slot = slot,
                date = date,
                minutesBefore = minutesBefore,
                progressCountsDown = settings.lessonStartNotificationProgressCountsDown,
                pendingIntent = pendingIntent
            )
        } else {
            val displayMinutesBefore = displayMinutesBefore(
                configuredMinutesBefore = minutesBefore,
                remainingMillis = remainingMillis
            )
            notifySafely(
                notificationId,
                buildStandardNotification(displayMinutesBefore, lesson, slot, pendingIntent)
            )
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
            .setSmallIcon(R.drawable.ic_notification_school)
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
        date: LocalDate,
        minutesBefore: Int,
        progressCountsDown: Boolean,
        pendingIntent: PendingIntent
    ) {
        val lessonStart = LocalDateTime.of(date, slot.start)
        val lessonStartMillis = lessonStart
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val totalSeconds = (minutesBefore * 60).coerceAtLeast(1)
        var remainingMillis = Duration.between(
            LocalDateTime.now(ZoneId.systemDefault()),
            lessonStart
        ).toMillis()

        while (remainingMillis > 0L && !isStopped) {
            val notification = buildLiveUpdateNotification(
                lesson = lesson,
                slot = slot,
                lessonStartMillis = lessonStartMillis,
                remainingMillis = remainingMillis,
                totalSeconds = totalSeconds,
                progressCountsDown = progressCountsDown,
                pendingIntent = pendingIntent
            )
            setForegroundSafely(notificationId, notification)
            notifySafely(notificationId, notification)

            delay(nextLiveUpdateDelayMillis(remainingMillis))
            remainingMillis = Duration.between(
                LocalDateTime.now(ZoneId.systemDefault()),
                lessonStart
            ).toMillis()
        }

        if (!isStopped) {
            notifySafely(
                notificationId,
                buildStandardNotification(
                    minutesBefore = 0,
                    lesson = lesson,
                    slot = slot,
                    pendingIntent = pendingIntent
                )
            )
        }
    }

    @Suppress("NewApi")
    private fun buildLiveUpdateNotification(
        lesson: ResolvedLesson,
        slot: ClassSlot,
        lessonStartMillis: Long,
        remainingMillis: Long,
        totalSeconds: Int,
        progressCountsDown: Boolean,
        pendingIntent: PendingIntent
    ): Notification {
        val remainingSeconds = remainingSeconds(remainingMillis).coerceIn(0, totalSeconds)
        val progress = if (progressCountsDown) {
            remainingSeconds
        } else {
            totalSeconds - remainingSeconds
        }.coerceIn(0, totalSeconds)
        val chipText = buildLiveUpdateChipText(remainingSeconds)

        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_school)
            .setContentTitle(applicationContext.getString(R.string.lesson_start_live_update_title))
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
            .setShortCriticalText(chipText)
            .setSettingsText(
                applicationContext.getString(
                    R.string.lesson_start_notification_time_summary,
                    slot.label,
                    String.format("%02d:%02d", slot.start.hour, slot.start.minute)
                )
            )
            .setRequestPromotedOngoing(true)
            .build()
    }

    private fun canPostPromotedNotifications(): Boolean {
        return runCatching {
            NotificationManagerCompat.from(applicationContext).canPostPromotedNotifications()
        }.onFailure {
            Log.w(TAG, "Cannot check promoted notification availability", it)
        }.getOrDefault(false)
    }

    private suspend fun setForegroundSafely(notificationId: Int, notification: Notification) {
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
        } catch (_: Exception) {
            // Foreground promotion can fail on some devices; the normal notify path below still shows the update.
        }
    }

    private fun notifySafely(notificationId: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(applicationContext)
                .notify(notificationId, notification)
        } catch (_: SecurityException) {
            // The POST_NOTIFICATIONS permission can be revoked independently of this feature.
        }
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

    private fun buildLiveUpdateChipText(remainingSeconds: Int): String {
        return if (remainingSeconds < 60) {
            applicationContext.getString(
                R.string.lesson_start_live_update_chip_seconds,
                remainingSeconds
            )
        } else {
            applicationContext.getString(
                R.string.lesson_start_live_update_chip_minutes,
                (remainingSeconds / 60).coerceAtLeast(1)
            )
        }
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

    private fun resolveEffectiveLesson(
        date: LocalDate,
        slotIndex: Int,
        dayTypeEntities: Map<LocalDate, DayTypeEntity>,
        lessons: Map<Pair<Int, Int>, LessonEntity>,
        changedLessons: Map<Pair<LocalDate, Int>, ChangedLessonEntity>
    ): ResolvedLesson? {
        if (date.dayOfWeek.value !in 1..5) return null
        val dayTypeEntity = dayTypeEntities[date]
        val dayType = dayTypeEntity?.dayType ?: defaultDayType(date)
        if (dayType == DayType.HOLIDAY) return null

        val lessonDayOfWeek = dayTypeEntity?.overrideLessonDayOfWeek ?: date.dayOfWeek.value
        val lessonDayType = dayTypeEntity?.overrideLessonDayType ?: dayType
        val base = lessons[lessonDayOfWeek to slotIndex]?.let { resolveLesson(lessonDayType, it) }
            ?: return null
        val changed = changedLessons[date to slotIndex]
        return if (changed != null) {
            ResolvedLesson(changed.subject, changed.teacher, changed.location)
        } else {
            base
        }
    }

    private fun resolveLesson(dayType: DayType, lesson: LessonEntity): ResolvedLesson? {
        return when (lesson.mode) {
            LessonMode.WEEKLY -> {
                if (lesson.weeklySubject.isBlank()) null
                else ResolvedLesson(lesson.weeklySubject, lesson.weeklyTeacher, lesson.weeklyLocation)
            }
            LessonMode.ALTERNATING -> when (dayType) {
                DayType.A -> if (lesson.aSubject.isBlank()) null else ResolvedLesson(lesson.aSubject, lesson.aTeacher, lesson.aLocation)
                DayType.B -> if (lesson.bSubject.isBlank()) null else ResolvedLesson(lesson.bSubject, lesson.bTeacher, lesson.bLocation)
                DayType.HOLIDAY -> null
            }
        }
    }

    private fun defaultDayType(date: LocalDate): DayType {
        val weekend = date.dayOfWeek.value >= DayOfWeek.SATURDAY.value
        return if (weekend || JapaneseHolidayCalculator.isHoliday(date)) DayType.HOLIDAY else DayType.A
    }

    private fun SettingsEntity.classSlots(): List<ClassSlot> {
        return generateClassSlots(
            periodsPerDay = periodsPerDay,
            periodDurationMin = periodDurationMin,
            breakBetweenPeriodsMin = breakBetweenPeriodsMin,
            lunchBreakMin = lunchBreakMin,
            firstPeriodStartHour = firstPeriodStartHour,
            firstPeriodStartMinute = firstPeriodStartMinute,
            useKosenMode = useKosenMode,
            lunchAfterPeriod = lunchAfterPeriod
        )
    }

    companion object {
        private const val CHANNEL_ID = "lesson_start_notifications"
        private const val KEY_DATE = "date"
        private const val KEY_SLOT_INDEX = "slot_index"
        private const val TAG = "LessonStartNotification"
        private const val WORK_TAG = "lesson_start_notifications"
        private const val HORIZON_DAYS = 30L
        private const val ONE_MINUTE_MS = 60_000L
        private const val LIVE_UPDATE_FAST_REFRESH_THRESHOLD_MS = 10 * ONE_MINUTE_MS
        private const val LIVE_UPDATE_SMOOTH_REFRESH_MS = 2_000L
        private const val LIVE_UPDATE_KEEP_ALIVE_MS = 15_000L
        private const val LATE_NOTIFICATION_GRACE_MS = 60_000L

        suspend fun rescheduleAll(context: Context) {
            val appContext = context.applicationContext
            withContext(Dispatchers.IO) {
                WorkManager.getInstance(appContext)
                    .cancelAllWorkByTag(WORK_TAG)
                    .result
                    .get()
            }
            scheduleUpcoming(appContext)
        }

        private suspend fun scheduleUpcoming(context: Context) {
            val dao = AppDatabase.getInstance(context).schedulerDao()
            val settings = dao.getSettings() ?: return
            if (!settings.lessonStartNotificationEnabled) return

            val today = LocalDate.now()
            val now = LocalDateTime.now(ZoneId.systemDefault())
            val endDate = minOf(settings.termEnd, today.plusDays(HORIZON_DAYS))
            if (endDate.isBefore(today)) return

            val slots = settings.classSlots()
            val dayTypeEntities = dao.getDayTypesOnce().associateBy { it.date }
            val lessons = dao.getLessonsOnce().associateBy { it.dayOfWeek to it.slotIndex }
            val changedLessons = dao.getChangedLessonsOnce().associateBy { it.date to it.slotIndex }
            val cancelledLessons = dao.getCancelledLessonsOnce().map { it.date to it.slotIndex }.toSet()
            val exclusions = dao.getLessonNotificationExclusionsOnce()
            val minutesBefore = settings.lessonStartNotificationMinutesBefore.coerceIn(0, 360).toLong()

            for (date in today.toDateRange(endDate)) {
                for (slot in slots) {
                    if (cancelledLessons.contains(date to slot.index)) continue
                    val lesson = resolveEffectiveLessonForSchedule(
                        date = date,
                        slotIndex = slot.index,
                        dayTypeEntities = dayTypeEntities,
                        lessons = lessons,
                        changedLessons = changedLessons
                    ) ?: continue
                    if (lesson.subject.isBlank() || isExcluded(lesson, exclusions)) continue

                    val lessonStart = LocalDateTime.of(date, slot.start)
                    if (!lessonStart.isAfter(now)) continue
                    val notificationAt = lessonStart.minusMinutes(minutesBefore)
                    val delayMillis = Duration.between(now, notificationAt)
                        .toMillis()
                        .coerceAtLeast(0L)

                    val request = OneTimeWorkRequestBuilder<LessonStartNotificationWorker>()
                        .setInputData(
                            Data.Builder()
                                .putString(KEY_DATE, date.toString())
                                .putInt(KEY_SLOT_INDEX, slot.index)
                                .build()
                        )
                        .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                        .addTag(WORK_TAG)
                        .build()

                    WorkManager.getInstance(context)
                        .enqueueUniqueWork(
                            uniqueWorkName(date, slot.index),
                            ExistingWorkPolicy.REPLACE,
                            request
                        )
                }
            }
        }

        private fun resolveEffectiveLessonForSchedule(
            date: LocalDate,
            slotIndex: Int,
            dayTypeEntities: Map<LocalDate, DayTypeEntity>,
            lessons: Map<Pair<Int, Int>, LessonEntity>,
            changedLessons: Map<Pair<LocalDate, Int>, ChangedLessonEntity>
        ): ResolvedLesson? {
            if (date.dayOfWeek.value !in 1..5) return null
            val dayTypeEntity = dayTypeEntities[date]
            val dayType = dayTypeEntity?.dayType ?: defaultDayTypeForSchedule(date)
            if (dayType == DayType.HOLIDAY) return null

            val lessonDayOfWeek = dayTypeEntity?.overrideLessonDayOfWeek ?: date.dayOfWeek.value
            val lessonDayType = dayTypeEntity?.overrideLessonDayType ?: dayType
            val base = lessons[lessonDayOfWeek to slotIndex]?.let { resolveLessonForSchedule(lessonDayType, it) }
                ?: return null
            val changed = changedLessons[date to slotIndex]
            return if (changed != null) {
                ResolvedLesson(changed.subject, changed.teacher, changed.location)
            } else {
                base
            }
        }

        private fun resolveLessonForSchedule(dayType: DayType, lesson: LessonEntity): ResolvedLesson? {
            return when (lesson.mode) {
                LessonMode.WEEKLY -> {
                    if (lesson.weeklySubject.isBlank()) null
                    else ResolvedLesson(lesson.weeklySubject, lesson.weeklyTeacher, lesson.weeklyLocation)
                }
                LessonMode.ALTERNATING -> when (dayType) {
                    DayType.A -> if (lesson.aSubject.isBlank()) null else ResolvedLesson(lesson.aSubject, lesson.aTeacher, lesson.aLocation)
                    DayType.B -> if (lesson.bSubject.isBlank()) null else ResolvedLesson(lesson.bSubject, lesson.bTeacher, lesson.bLocation)
                    DayType.HOLIDAY -> null
                }
            }
        }

        private fun defaultDayTypeForSchedule(date: LocalDate): DayType {
            val weekend = date.dayOfWeek.value >= DayOfWeek.SATURDAY.value
            return if (weekend || JapaneseHolidayCalculator.isHoliday(date)) DayType.HOLIDAY else DayType.A
        }

        private fun SettingsEntity.classSlots(): List<ClassSlot> {
            return generateClassSlots(
                periodsPerDay = periodsPerDay,
                periodDurationMin = periodDurationMin,
                breakBetweenPeriodsMin = breakBetweenPeriodsMin,
                lunchBreakMin = lunchBreakMin,
                firstPeriodStartHour = firstPeriodStartHour,
                firstPeriodStartMinute = firstPeriodStartMinute,
                useKosenMode = useKosenMode,
                lunchAfterPeriod = lunchAfterPeriod
            )
        }

        private fun uniqueWorkName(date: LocalDate, slotIndex: Int): String {
            return "lesson_start_notification_${date}_$slotIndex"
        }

        private fun notificationId(date: LocalDate, slotIndex: Int): Int {
            return (40_000 + (date.toEpochDay() % 10_000).toInt() * 10 + slotIndex).coerceAtLeast(40_000)
        }

        private fun LocalDate.toDateRange(endDate: LocalDate): Sequence<LocalDate> {
            return generateSequence(this) { current ->
                current.plusDays(1).takeIf { !it.isAfter(endDate) }
            }
        }
    }
}

private fun isExcluded(
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
