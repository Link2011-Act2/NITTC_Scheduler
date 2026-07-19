package jp.linkserver.nittcsc.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.AlarmManager
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
import jp.linkserver.nittcsc.data.ChangedLessonEntity
import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.ExamLessonEntity
import jp.linkserver.nittcsc.data.hasEnteredContent
import jp.linkserver.nittcsc.data.HolidaySpecialLabel
import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import jp.linkserver.nittcsc.data.LessonStartNotificationChipMode
import jp.linkserver.nittcsc.data.LessonNotificationExclusionEntity
import jp.linkserver.nittcsc.data.ResolvedLesson
import jp.linkserver.nittcsc.data.SettingsEntity
import jp.linkserver.nittcsc.logic.ClassSlot
import jp.linkserver.nittcsc.logic.JapaneseHolidayCalculator
import jp.linkserver.nittcsc.logic.PeriodLabelStyle
import jp.linkserver.nittcsc.logic.formatPeriodLabel
import jp.linkserver.nittcsc.logic.generateClassSlots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

        val specialLabel = dao.getDayType(date)?.holidaySpecialLabel
        val examLessonsForDate = dao.getExamLessonsForDate(date)
        val isExamDate = dao.getExamDaySchedule(date) != null &&
            examLessonsForDate.any { it.hasEnteredContent() } &&
            (specialLabel == HolidaySpecialLabel.MIDTERM || specialLabel == HolidaySpecialLabel.FINAL)
        val examLesson = if (isExamDate) {
            examLessonsForDate.firstOrNull { it.slotIndex == slotIndex }
        } else {
            null
        }
        val slot = if (isExamDate) {
            examLesson?.toClassSlot(settings.periodLabelStyle) ?: return Result.success()
        } else {
            settings.classSlots().firstOrNull { it.index == slotIndex } ?: return Result.success()
        }
        if (!isExamDate && dao.getCancelledLesson(date, slotIndex) != null) return Result.success()

        val lesson = if (isExamDate) {
            examLesson?.toResolvedLesson()
        } else {
            resolveEffectiveLesson(
                date = date,
                slotIndex = slotIndex,
                dayTypeEntities = dao.getDayTypesOnce().associateBy { it.date },
                lessons = dao.getLessonsOnce().associateBy { it.dayOfWeek to it.slotIndex },
                changedLessons = dao.getChangedLessonsOnce().associateBy { it.date to it.slotIndex }
            )
        } ?: return Result.success()
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
        val actualLessonStart = LocalDateTime.of(date, slot.start)
        val liveUpdatesActive =
            settings.lessonStartNotificationLiveUpdatesEnabled &&
            Build.VERSION.SDK_INT >= 36 &&
            minutesBefore > 0 &&
            canPostPromotedNotifications()
        val liveUpdateEarlyMinutes = settings.lessonStartNotificationLiveUpdateEarlyMinutes.coerceIn(0, 5)

        if (liveUpdatesActive) {
            val liveUpdateTarget = actualLessonStart.minusMinutes(liveUpdateEarlyMinutes.toLong())
            val remainingMillis = Duration.between(
                LocalDateTime.now(ZoneId.systemDefault()),
                liveUpdateTarget
            ).toMillis()
            if (remainingMillis >= -LATE_NOTIFICATION_GRACE_MS) {
                showLiveUpdateCountdown(
                    notificationId = notificationId,
                    lesson = lesson,
                    slot = slot,
                    liveUpdateTarget = liveUpdateTarget,
                    minutesBefore = minutesBefore,
                    progressCountsDown = settings.lessonStartNotificationProgressCountsDown,
                    chipMode = settings.lessonStartNotificationChipMode,
                    pendingIntent = pendingIntent
                )
            } else {
                showLateStandardNotification(
                    notificationId = notificationId,
                    lesson = lesson,
                    slot = slot,
                    actualLessonStart = actualLessonStart,
                    configuredMinutesBefore = minutesBefore,
                    pendingIntent = pendingIntent
                )
            }
        } else {
            val standardNotificationAt = actualLessonStart.minusMinutes(minutesBefore.toLong())
            val waitMillis = Duration.between(
                LocalDateTime.now(ZoneId.systemDefault()),
                standardNotificationAt
            ).toMillis()
            if (waitMillis > 0L) delay(waitMillis)

            val remainingMillis = Duration.between(
                LocalDateTime.now(ZoneId.systemDefault()),
                actualLessonStart
            ).toMillis()
            if (remainingMillis < -LATE_NOTIFICATION_GRACE_MS) return Result.success()
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

    private fun showLateStandardNotification(
        notificationId: Int,
        lesson: ResolvedLesson,
        slot: ClassSlot,
        actualLessonStart: LocalDateTime,
        configuredMinutesBefore: Int,
        pendingIntent: PendingIntent
    ) {
        val remainingMillis = Duration.between(
            LocalDateTime.now(ZoneId.systemDefault()),
            actualLessonStart
        ).toMillis()
        if (remainingMillis < -LATE_NOTIFICATION_GRACE_MS) return
        notifySafely(
            notificationId,
            buildStandardNotification(
                displayMinutesBefore(configuredMinutesBefore, remainingMillis),
                lesson,
                slot,
                pendingIntent
            )
        )
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
        liveUpdateTarget: LocalDateTime,
        minutesBefore: Int,
        progressCountsDown: Boolean,
        chipMode: LessonStartNotificationChipMode,
        pendingIntent: PendingIntent
    ) {
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
            setForegroundSafely(notificationId, notification)
            notifySafely(notificationId, notification)

            delay(nextLiveUpdateDelayMillis(remainingMillis))
            remainingMillis = Duration.between(
                LocalDateTime.now(ZoneId.systemDefault()),
                liveUpdateTarget
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
            .setSmallIcon(R.drawable.ic_notification_school)
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
            periodLabelStyle = periodLabelStyle,
            lunchAfterPeriod = lunchAfterPeriod
        )
    }

    private fun ExamLessonEntity.toClassSlot(periodLabelStyle: PeriodLabelStyle): ClassSlot {
        return ClassSlot(
            index = slotIndex,
            label = formatPeriodLabel(slotIndex, periodLabelStyle),
            start = java.time.LocalTime.of(startHour, startMinute),
            end = java.time.LocalTime.of(endHour, endMinute)
        )
    }

    private fun ExamLessonEntity.toResolvedLesson(): ResolvedLesson? {
        if (subject.isBlank()) return null
        return ResolvedLesson(subject, teacher, location.takeIf { it.isNotBlank() })
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
        private const val ALARM_PREFERENCES = "lesson_start_notification_alarms"
        private const val ALARM_KEYS = "scheduled_alarm_keys"
        private val rescheduleMutex = Mutex()

        suspend fun rescheduleAll(context: Context) {
            val appContext = context.applicationContext
            rescheduleMutex.withLock {
                withContext(Dispatchers.IO) {
                    WorkManager.getInstance(appContext)
                        .cancelAllWorkByTag(WORK_TAG)
                        .result
                        .get()
                    cancelScheduledAlarms(appContext)
                }
                scheduleUpcoming(appContext)
            }
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
            val examLessonsByDate = dao.getExamLessonsOnce().groupBy { it.date }
            val examScheduleDates = dao.getExamDaySchedulesOnce()
                .map { it.date }
                .filterTo(mutableSetOf()) { date ->
                    examLessonsByDate[date].orEmpty().any { it.hasEnteredContent() } && when (dayTypeEntities[date]?.holidaySpecialLabel) {
                        HolidaySpecialLabel.MIDTERM, HolidaySpecialLabel.FINAL -> true
                        else -> false
                    }
                }
            val exclusions = dao.getLessonNotificationExclusionsOnce()
            val minutesBefore = settings.lessonStartNotificationMinutesBefore.coerceIn(0, 360).toLong()
            val potentialLiveUpdates =
                settings.lessonStartNotificationLiveUpdatesEnabled &&
                    Build.VERSION.SDK_INT >= 36 &&
                    minutesBefore > 0L
            val liveUpdateEarlyMinutes = if (potentialLiveUpdates) {
                settings.lessonStartNotificationLiveUpdateEarlyMinutes.coerceIn(0, 5).toLong()
            } else {
                0L
            }

            for (date in today.toDateRange(endDate)) {
                val isExamDate = date in examScheduleDates
                val dateExamLessons = examLessonsByDate[date].orEmpty().associateBy { it.slotIndex }
                val dateSlots = if (isExamDate) {
                    dateExamLessons.values.sortedBy { it.slotIndex }.map { exam ->
                        ClassSlot(
                            index = exam.slotIndex,
                            label = formatPeriodLabel(exam.slotIndex, settings.periodLabelStyle),
                            start = java.time.LocalTime.of(exam.startHour, exam.startMinute),
                            end = java.time.LocalTime.of(exam.endHour, exam.endMinute)
                        )
                    }
                } else {
                    slots
                }
                for (slot in dateSlots) {
                    if (!isExamDate && cancelledLessons.contains(date to slot.index)) continue
                    val lesson = if (isExamDate) {
                        dateExamLessons[slot.index]?.let { exam ->
                            if (exam.subject.isBlank()) null else ResolvedLesson(
                                exam.subject,
                                exam.teacher,
                                exam.location.takeIf { it.isNotBlank() }
                            )
                        }
                    } else {
                        resolveEffectiveLessonForSchedule(
                            date = date,
                            slotIndex = slot.index,
                            dayTypeEntities = dayTypeEntities,
                            lessons = lessons,
                            changedLessons = changedLessons
                        )
                    } ?: continue
                    if (lesson.subject.isBlank() || isExcluded(lesson, exclusions)) continue

                    val lessonStart = LocalDateTime.of(date, slot.start)
                    if (!lessonStart.isAfter(now)) continue
                    val notificationAt = lessonStart
                        .minusMinutes(liveUpdateEarlyMinutes)
                        .minusMinutes(minutesBefore)
                    val exactAlarmScheduled = scheduleExactAlarm(
                        context,
                        date,
                        slot.index,
                        notificationAt
                    )
                    val fallbackNotificationAt = if (exactAlarmScheduled) {
                        notificationAt.plusMinutes(1)
                    } else {
                        notificationAt
                    }
                    val delayMillis = Duration.between(now, fallbackNotificationAt)
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

        fun enqueueNow(context: Context, date: LocalDate, slotIndex: Int) {
            val request = OneTimeWorkRequestBuilder<LessonStartNotificationWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(KEY_DATE, date.toString())
                        .putInt(KEY_SLOT_INDEX, slotIndex)
                        .build()
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    uniqueWorkName(date, slotIndex),
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }

        private fun scheduleExactAlarm(
            context: Context,
            date: LocalDate,
            slotIndex: Int,
            notificationAt: LocalDateTime
        ): Boolean {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                return false
            }
            val triggerAtMillis = notificationAt
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            if (triggerAtMillis <= System.currentTimeMillis()) return false

            return runCatching {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    alarmPendingIntent(context, date, slotIndex)
                )
                val preferences = context.getSharedPreferences(ALARM_PREFERENCES, Context.MODE_PRIVATE)
                val keys = preferences.getStringSet(ALARM_KEYS, emptySet()).orEmpty().toMutableSet()
                keys += alarmKey(date, slotIndex)
                preferences.edit().putStringSet(ALARM_KEYS, keys).apply()
                true
            }.onFailure {
                Log.w(TAG, "Cannot schedule exact lesson notification alarm", it)
            }.getOrDefault(false)
        }

        private fun cancelScheduledAlarms(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            val preferences = context.getSharedPreferences(ALARM_PREFERENCES, Context.MODE_PRIVATE)
            preferences.getStringSet(ALARM_KEYS, emptySet()).orEmpty().forEach { key ->
                val parts = key.split('|')
                val date = parts.getOrNull(0)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: return@forEach
                val slotIndex = parts.getOrNull(1)?.toIntOrNull() ?: return@forEach
                alarmManager.cancel(alarmPendingIntent(context, date, slotIndex))
            }
            preferences.edit().remove(ALARM_KEYS).apply()
        }

        private fun alarmPendingIntent(
            context: Context,
            date: LocalDate,
            slotIndex: Int
        ): PendingIntent {
            val intent = Intent(context, LessonStartNotificationAlarmReceiver::class.java).apply {
                data = Uri.parse("nittcsc://lesson-start-notification/$date/$slotIndex")
                putExtra(LessonStartNotificationAlarmReceiver.EXTRA_DATE, date.toString())
                putExtra(LessonStartNotificationAlarmReceiver.EXTRA_SLOT_INDEX, slotIndex)
            }
            return PendingIntent.getBroadcast(
                context,
                notificationId(date, slotIndex),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun alarmKey(date: LocalDate, slotIndex: Int): String = "$date|$slotIndex"

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
                periodLabelStyle = periodLabelStyle,
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
