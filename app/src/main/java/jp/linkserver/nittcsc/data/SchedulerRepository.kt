package jp.linkserver.nittcsc.data

import jp.linkserver.nittcsc.InternalFeatureFlags
import jp.linkserver.nittcsc.logic.CLASS_SLOTS
import jp.linkserver.nittcsc.data.HolidaySpecialLabel
import jp.linkserver.nittcsc.logic.ExportRange
import jp.linkserver.nittcsc.logic.GeneratedLesson
import jp.linkserver.nittcsc.logic.JapaneseHolidayCalculator
import jp.linkserver.nittcsc.logic.PeriodLabelStyle
import jp.linkserver.nittcsc.logic.applyChangedLesson
import jp.linkserver.nittcsc.logic.formatExamPeriodLabel
import jp.linkserver.nittcsc.logic.generateClassSlots
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.temporal.TemporalAdjusters

class SchedulerRepository(private val db: AppDatabase) {

    private val dao: SchedulerDao = db.schedulerDao()
    private val dataTransfer = SchedulerDataTransfer(this, db)

    companion object {
        private const val CURRENT_EXPORT_VERSION = 12
        private const val MIN_SUPPORTED_IMPORT_VERSION = 1
        private const val MAX_FUTURE_META_DRIFT_MS = 5 * 60 * 1000L
        const val DATASET_TASKS = "tasks"
        const val DATASET_PLANS = "plans"
        const val DATASET_LESSONS = "lessons"
        const val DATASET_DAY_TYPES = "dayTypes"
        const val DATASET_LONG_BREAKS = "longBreaks"
        const val DATASET_CANCELLED_LESSONS = "cancelledLessons"
        const val DATASET_CHANGED_LESSONS = "changedLessons"
        const val DATASET_LESSON_NOTES = "lessonNotes"
        const val DATASET_EXAM_TIMETABLES = "examTimetables"
        val SYNC_DATASET_KEYS = listOf(
            DATASET_TASKS,
            DATASET_PLANS,
            DATASET_LESSONS,
            DATASET_DAY_TYPES,
            DATASET_LONG_BREAKS,
            DATASET_CANCELLED_LESSONS,
            DATASET_CHANGED_LESSONS,
            DATASET_LESSON_NOTES,
            DATASET_EXAM_TIMETABLES
        )
    }

    val settingsFlow: Flow<SettingsEntity?> = dao.observeSettings()
    val dayTypesFlow: Flow<List<DayTypeEntity>> = dao.observeDayTypes()
    val cancelledLessonsFlow: Flow<List<CancelledLessonEntity>> = dao.observeCancelledLessons()
    val changedLessonsFlow: Flow<List<ChangedLessonEntity>> = dao.observeChangedLessons()
    val lessonNotesFlow: Flow<List<LessonNoteEntity>> = dao.observeLessonNotes()
    val examDaySchedulesFlow: Flow<List<ExamDayScheduleEntity>> = dao.observeExamDaySchedules()
    val examLessonsFlow: Flow<List<ExamLessonEntity>> = dao.observeExamLessons()
    val lessonNotificationExclusionsFlow: Flow<List<LessonNotificationExclusionEntity>> = dao.observeLessonNotificationExclusions()
    val longBreaksFlow: Flow<List<LongBreakEntity>> = dao.observeLongBreaks()
    val lessonsFlow: Flow<List<LessonEntity>> = dao.observeLessons()
    val tasksFlow: Flow<List<TaskEntity>> = dao.observeTasks()
    val incompleteTasksFlow: Flow<List<TaskEntity>> = dao.observeIncompleteTasks()
    val plansFlow: Flow<List<PlanEntity>> = dao.observePlans()
    val incompletePlansFlow: Flow<List<PlanEntity>> = dao.observeIncompletePlans()
    val syncProfileFlow: Flow<SyncProfileEntity?> = dao.observeSyncProfile()
    val syncRegisteredDevicesFlow: Flow<List<SyncRegisteredDeviceEntity>> = dao.observeSyncRegisteredDevices()

    suspend fun initialize(today: LocalDate = LocalDate.now()) {
        val settings = dao.getSettings()
        if (settings == null) {
            dao.upsertSettings(defaultSettings(today))
        } else if (
            !InternalFeatureFlags.NATURAL_LANGUAGE_TASK_ADD &&
            settings.enableNaturalLanguageTaskAdd
        ) {
            dao.upsertSettings(settings.copy(enableNaturalLanguageTaskAdd = false))
        }
        ensureLessonRows()
        syncDayTypes()
    }

    suspend fun resetToCurrentFiscalYear(today: LocalDate = LocalDate.now()) {
        db.withTransaction {
            val settings = defaultSettings(today)
            dao.upsertSettings(settings)
            syncDayTypes()
            touchSyncDatasetMeta(DATASET_DAY_TYPES)
        }
    }

    suspend fun updateTerm(startDate: LocalDate, endDate: LocalDate) {
        db.withTransaction {
            val current = dao.getSettings() ?: defaultSettings(LocalDate.now())
            dao.upsertSettings(
                current.copy(
                    termStart = minOf(startDate, endDate),
                    termEnd = maxOf(startDate, endDate)
                )
            )
            syncDayTypes()
            touchSyncDatasetMeta(DATASET_DAY_TYPES)
        }
    }

    suspend fun updateScheduleSettings(
        periodsPerDay: Int,
        periodDurationMin: Int,
        breakBetweenPeriodsMin: Int,
        lunchBreakMin: Int,
        lunchAfterPeriod: Int,
        firstPeriodStartHour: Int,
        firstPeriodStartMinute: Int,
        periodLabelStyle: PeriodLabelStyle,
        arrivalHour: Int,
        arrivalMinute: Int,
        departureHour: Int,
        departureMinute: Int
    ) {
        db.withTransaction {
            val current = dao.getSettings() ?: return@withTransaction
            dao.upsertSettings(
                current.copy(
                    periodsPerDay = periodsPerDay,
                    periodDurationMin = periodDurationMin,
                    breakBetweenPeriodsMin = breakBetweenPeriodsMin,
                    lunchBreakMin = lunchBreakMin,
                    lunchAfterPeriod = lunchAfterPeriod,
                    firstPeriodStartHour = firstPeriodStartHour,
                    firstPeriodStartMinute = firstPeriodStartMinute,
                    useKosenMode = periodLabelStyle == PeriodLabelStyle.PAIR_KOSHI,
                    periodLabelStyle = periodLabelStyle,
                    arrivalHour = arrivalHour,
                    arrivalMinute = arrivalMinute,
                    departureHour = departureHour,
                    departureMinute = departureMinute
                )
            )
            ensureLessonRows()
            touchSyncDatasetMeta(DATASET_LESSONS)
        }
    }

    suspend fun toggleLocalAi(enabled: Boolean) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(current.copy(enableLocalAi = enabled))
    }

    suspend fun toggleNaturalLanguageTaskAdd(enabled: Boolean) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(
            current.copy(
                enableNaturalLanguageTaskAdd =
                    enabled && InternalFeatureFlags.NATURAL_LANGUAGE_TASK_ADD
            )
        )
    }

    suspend fun updateExamTimetableSettings(
        periodsPerDay: Int,
        periodDurationMin: Int,
        breakBetweenPeriodsMin: Int,
        lunchBreakMin: Int,
        lunchAfterPeriod: Int,
        firstPeriodStartHour: Int,
        firstPeriodStartMinute: Int,
        arrivalHour: Int,
        arrivalMinute: Int
    ) {
        val current = dao.getSettings() ?: return
        val normalizedPeriods = periodsPerDay.coerceIn(1, 12)
        dao.upsertSettings(
            current.copy(
                examPeriodsPerDay = normalizedPeriods,
                examPeriodDurationMin = periodDurationMin.coerceIn(10, 180),
                examBreakBetweenPeriodsMin = breakBetweenPeriodsMin.coerceIn(0, 120),
                examLunchBreakMin = lunchBreakMin.coerceIn(0, 180),
                examLunchAfterPeriod = lunchAfterPeriod.coerceIn(0, normalizedPeriods),
                examFirstPeriodStartHour = firstPeriodStartHour.coerceIn(0, 23),
                examFirstPeriodStartMinute = firstPeriodStartMinute.coerceIn(0, 59),
                examArrivalHour = arrivalHour.coerceIn(0, 23),
                examArrivalMinute = arrivalMinute.coerceIn(0, 59)
            )
        )
    }

    suspend fun saveExamDaySchedule(
        schedule: ExamDayScheduleEntity,
        lessons: List<ExamLessonEntity>
    ) {
        db.withTransaction {
            dao.upsertExamDaySchedule(schedule)
            dao.deleteExamLessonsForDate(schedule.date)
            val normalizedLessons = lessons
                .filter { it.date == schedule.date }
                .sortedBy { it.slotIndex }
            if (normalizedLessons.isNotEmpty()) {
                dao.upsertExamLessons(normalizedLessons)
            }
            touchSyncDatasetMeta(DATASET_EXAM_TIMETABLES)
        }
    }

    suspend fun saveExamPeriodSchedules(
        schedules: List<ExamDayScheduleEntity>,
        lessons: List<ExamLessonEntity>
    ) {
        if (schedules.isEmpty()) return
        db.withTransaction {
            schedules.forEach { schedule ->
                dao.upsertExamDaySchedule(schedule)
                dao.deleteExamLessonsForDate(schedule.date)
            }
            val targetDates = schedules.map { it.date }.toSet()
            val normalizedLessons = lessons
                .filter { it.date in targetDates }
                .sortedWith(compareBy<ExamLessonEntity> { it.date }.thenBy { it.slotIndex })
            if (normalizedLessons.isNotEmpty()) {
                dao.upsertExamLessons(normalizedLessons)
            }
            touchSyncDatasetMeta(DATASET_EXAM_TIMETABLES)
        }
    }

    suspend fun updateExamLessonMemo(date: LocalDate, slotIndex: Int, text: String) {
        db.withTransaction {
            val lesson = dao.getExamLesson(date, slotIndex) ?: return@withTransaction
            val normalizedText = text.trim()
            if (lesson.memo == normalizedText) return@withTransaction
            dao.upsertExamLessons(
                listOf(
                    lesson.copy(
                        memo = normalizedText,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            )
            touchSyncDatasetMeta(DATASET_EXAM_TIMETABLES)
        }
    }

    suspend fun deleteExamDaySchedule(date: LocalDate) {
        db.withTransaction {
            dao.deleteExamLessonsForDate(date)
            dao.deleteExamDaySchedule(date)
            touchSyncDatasetMeta(DATASET_EXAM_TIMETABLES)
        }
    }

    suspend fun toggleDrawerNavigation(enabled: Boolean) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(current.copy(useDrawerNavigation = enabled))
    }

    suspend fun toggleAddTasksToCalendar(enabled: Boolean) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(current.copy(addTasksToCalendar = enabled))
    }

    suspend fun toggleSyncLessonsToCalendar(enabled: Boolean) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(current.copy(syncLessonsToCalendar = enabled))
    }

    suspend fun enableSyncLessonsToCalendar(start: LocalDate, end: LocalDate) {
        val current = dao.getSettings() ?: return
        val normalizedStart = minOf(start, end)
        val normalizedEnd = maxOf(start, end)
        dao.upsertSettings(
            current.copy(
                syncLessonsToCalendar = true,
                lessonCalendarSyncStart = normalizedStart,
                lessonCalendarSyncEnd = normalizedEnd
            )
        )
    }

    suspend fun updateLessonCalendarSyncRange(start: LocalDate, end: LocalDate) {
        val current = dao.getSettings() ?: return
        val normalizedStart = minOf(start, end)
        val normalizedEnd = maxOf(start, end)
        dao.upsertSettings(
            current.copy(
                lessonCalendarSyncStart = normalizedStart,
                lessonCalendarSyncEnd = normalizedEnd
            )
        )
    }

    suspend fun toggleCurrentTimeMarker(enabled: Boolean) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(current.copy(showCurrentTimeMarker = enabled))
    }

    suspend fun toggleUnifyTaskPlanView(enabled: Boolean) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(current.copy(unifyTaskPlanView = enabled))
    }

    suspend fun toggleShowWeekdayOnDates(enabled: Boolean) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(current.copy(showWeekdayOnDates = enabled))
    }

    suspend fun toggleTlsSync(enabled: Boolean) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(current.copy(enableTlsSync = enabled))
    }

    suspend fun toggleAdvancedTimeSettingsUi(enabled: Boolean) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(current.copy(useAdvancedTimeSettingsUi = enabled))
    }

    suspend fun toggleLessonStartNotifications(enabled: Boolean) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(current.copy(lessonStartNotificationEnabled = enabled))
    }

    suspend fun updateLessonStartNotificationMinutesBefore(minutesBefore: Int) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(
            current.copy(
                lessonStartNotificationMinutesBefore = minutesBefore.coerceIn(0, 360)
            )
        )
    }

    suspend fun toggleLessonStartNotificationLiveUpdates(enabled: Boolean) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(current.copy(lessonStartNotificationLiveUpdatesEnabled = enabled))
    }

    suspend fun toggleLessonStartNotificationProgressCountsDown(enabled: Boolean) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(current.copy(lessonStartNotificationProgressCountsDown = enabled))
    }

    suspend fun updateLessonStartNotificationLiveUpdateEarlyMinutes(minutes: Int) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(
            current.copy(
                lessonStartNotificationLiveUpdateEarlyMinutes = minutes.coerceIn(0, 5)
            )
        )
    }

    suspend fun updateLessonStartNotificationChipMode(mode: LessonStartNotificationChipMode) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(current.copy(lessonStartNotificationChipMode = mode))
    }

    suspend fun upsertLessonNotificationExclusion(
        subject: String,
        teacher: String?,
        matchTeacher: Boolean
    ) {
        val normalizedSubject = subject.trim()
        if (normalizedSubject.isBlank()) return
        dao.upsertLessonNotificationExclusion(
            LessonNotificationExclusionEntity(
                subject = normalizedSubject,
                teacher = teacher?.trim()?.takeIf { it.isNotEmpty() },
                matchTeacher = matchTeacher && !teacher.isNullOrBlank()
            )
        )
    }

    suspend fun deleteLessonNotificationExclusion(exclusionId: Long) {
        dao.deleteLessonNotificationExclusion(exclusionId)
    }

    suspend fun updateHfToken(token: String?) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(current.copy(hfToken = token))
    }

    suspend fun toggleDayType(date: LocalDate) {
        db.withTransaction {
            val existing = dao.getDayType(date)
            val current = existing?.dayType ?: DayType.A
            val next = when (current) {
                DayType.A -> DayType.B
                DayType.B -> DayType.HOLIDAY
                DayType.HOLIDAY -> DayType.A
            }
            dao.upsertDayType(
                DayTypeEntity(
                    date = date,
                    dayType = next,
                    overrideLessonDayOfWeek = existing?.overrideLessonDayOfWeek,
                    overrideLessonDayType = existing?.overrideLessonDayType,
                    holidaySpecialLabel = if (next == DayType.HOLIDAY) existing?.holidaySpecialLabel else null
                )
            )
            touchSyncDatasetMeta(DATASET_DAY_TYPES)
        }
    }

    suspend fun upsertDayType(date: LocalDate, dayType: DayType) {
        db.withTransaction {
            val existing = dao.getDayType(date)
            dao.upsertDayType(
                DayTypeEntity(
                    date = date,
                    dayType = dayType,
                    overrideLessonDayOfWeek = existing?.overrideLessonDayOfWeek,
                    overrideLessonDayType = existing?.overrideLessonDayType,
                    holidaySpecialLabel = if (dayType == DayType.HOLIDAY) existing?.holidaySpecialLabel else null
                )
            )
            touchSyncDatasetMeta(DATASET_DAY_TYPES)
        }
    }

    suspend fun upsertDayTypes(dates: List<LocalDate>, dayType: DayType) {
        db.withTransaction {
            val existing = dao.getDayTypesOnce().associateBy { it.date }
            val entities = dates.distinct().map { date ->
                DayTypeEntity(
                    date = date,
                    dayType = dayType,
                    overrideLessonDayOfWeek = existing[date]?.overrideLessonDayOfWeek,
                    overrideLessonDayType = existing[date]?.overrideLessonDayType,
                    holidaySpecialLabel = if (dayType == DayType.HOLIDAY) existing[date]?.holidaySpecialLabel else null
                )
            }
            if (entities.isNotEmpty()) {
                dao.upsertDayTypes(entities)
                touchSyncDatasetMeta(DATASET_DAY_TYPES)
            }
        }
    }

    suspend fun upsertLessonOverride(date: LocalDate, dayOfWeek: Int, dayType: DayType) {
        db.withTransaction {
            val existing = dao.getDayType(date)
            val currentDayType = existing?.dayType ?: if (
                date.dayOfWeek == DayOfWeek.SATURDAY ||
                date.dayOfWeek == DayOfWeek.SUNDAY ||
                JapaneseHolidayCalculator.isHoliday(date)
            ) {
                DayType.HOLIDAY
            } else {
                DayType.A
            }
            val shouldClearOverride = dayOfWeek == date.dayOfWeek.value && dayType == currentDayType

            dao.upsertDayType(
                if (shouldClearOverride) {
                    DayTypeEntity(
                        date = date,
                        dayType = currentDayType,
                        overrideLessonDayOfWeek = null,
                        overrideLessonDayType = null,
                        holidaySpecialLabel = if (currentDayType == DayType.HOLIDAY) existing?.holidaySpecialLabel else null
                    )
                } else {
                    DayTypeEntity(
                        date = date,
                        dayType = dayType,
                        overrideLessonDayOfWeek = dayOfWeek,
                        overrideLessonDayType = dayType,
                        holidaySpecialLabel = if (dayType == DayType.HOLIDAY) existing?.holidaySpecialLabel else null
                    )
                }
            )
            touchSyncDatasetMeta(DATASET_DAY_TYPES)
        }
    }

    suspend fun updateHolidaySpecialLabel(date: LocalDate, label: HolidaySpecialLabel?) {
        db.withTransaction {
            val existing = dao.getDayType(date) ?: DayTypeEntity(date = date, dayType = DayType.HOLIDAY)
            dao.upsertDayType(
                existing.copy(
                    holidaySpecialLabel = if (existing.dayType == DayType.HOLIDAY) label else null
                )
            )
            touchSyncDatasetMeta(DATASET_DAY_TYPES)
        }
    }

    suspend fun clearLessonOverride(date: LocalDate) {
        db.withTransaction {
            val existing = dao.getDayType(date) ?: return@withTransaction
            dao.upsertDayType(
                existing.copy(
                    overrideLessonDayOfWeek = null,
                    overrideLessonDayType = null
                )
            )
            touchSyncDatasetMeta(DATASET_DAY_TYPES)
        }
    }

    suspend fun setLessonCancelled(date: LocalDate, slotIndex: Int, cancelled: Boolean) {
        db.withTransaction {
            if (cancelled) {
                dao.upsertCancelledLesson(CancelledLessonEntity(date = date, slotIndex = slotIndex))
            } else {
                dao.deleteCancelledLesson(date, slotIndex)
            }
            touchSyncDatasetMeta(DATASET_CANCELLED_LESSONS)
        }
    }

    suspend fun upsertChangedLesson(
        date: LocalDate,
        slotIndex: Int,
        subject: String,
        teacher: String,
        location: String?
    ) {
        db.withTransaction {
            dao.upsertChangedLesson(
                ChangedLessonEntity(
                    date = date,
                    slotIndex = slotIndex,
                    subject = subject.trim(),
                    teacher = teacher.trim(),
                    location = location?.trim()?.takeIf { it.isNotEmpty() }
                )
            )
            touchSyncDatasetMeta(DATASET_CHANGED_LESSONS)
        }
    }

    suspend fun deleteChangedLesson(date: LocalDate, slotIndex: Int) {
        db.withTransaction {
            dao.deleteChangedLesson(date, slotIndex)
            touchSyncDatasetMeta(DATASET_CHANGED_LESSONS)
        }
    }

    suspend fun upsertLessonNote(date: LocalDate, slotIndex: Int, text: String) {
        val trimmed = text.trim()
        db.withTransaction {
            if (trimmed.isBlank()) {
                dao.deleteLessonNote(date, slotIndex)
            } else {
                dao.upsertLessonNote(
                    LessonNoteEntity(
                        date = date,
                        slotIndex = slotIndex,
                        text = trimmed,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            touchSyncDatasetMeta(DATASET_LESSON_NOTES)
        }
    }

    suspend fun deleteLessonNote(date: LocalDate, slotIndex: Int) {
        db.withTransaction {
            dao.deleteLessonNote(date, slotIndex)
            touchSyncDatasetMeta(DATASET_LESSON_NOTES)
        }
    }

    suspend fun upsertLongBreak(id: Long?, name: String, startDate: LocalDate, endDate: LocalDate) {
        db.withTransaction {
            val correctedStart = minOf(startDate, endDate)
            val correctedEnd = maxOf(startDate, endDate)
            dao.upsertLongBreak(
                LongBreakEntity(
                    id = id ?: 0,
                    name = name,
                    startDate = correctedStart,
                    endDate = correctedEnd
                )
            )
            syncDayTypes()
            touchSyncDatasetMeta(DATASET_LONG_BREAKS, DATASET_DAY_TYPES)
        }
    }

    suspend fun deleteLongBreak(longBreak: LongBreakEntity) {
        db.withTransaction {
            dao.deleteLongBreak(longBreak)
            syncDayTypes()
            touchSyncDatasetMeta(DATASET_LONG_BREAKS, DATASET_DAY_TYPES)
        }
    }

    suspend fun upsertLesson(dayOfWeek: Int, slotIndex: Int, draft: LessonDraft) {
        db.withTransaction {
            val existing = dao.getLesson(dayOfWeek, slotIndex)
            val weeklySubject = draft.weeklySubject.trim()
            val weeklyTeacher = draft.weeklyTeacher.trim()
            val weeklyLocation = draft.weeklyLocation.trim().takeIf { it.isNotEmpty() }
            val aSubject = draft.aSubject.trim()
            val aTeacher = draft.aTeacher.trim()
            val aLocation = draft.aLocation.trim().takeIf { it.isNotEmpty() }
            val bSubject = draft.bSubject.trim()
            val bTeacher = draft.bTeacher.trim()
            val bLocation = draft.bLocation.trim().takeIf { it.isNotEmpty() }
            dao.upsertLesson(
                LessonEntity(
                    id = existing?.id ?: 0,
                    dayOfWeek = dayOfWeek,
                    slotIndex = slotIndex,
                    mode = draft.mode,
                    weeklySubject = if (draft.mode == LessonMode.WEEKLY) weeklySubject else "",
                    weeklyTeacher = if (draft.mode == LessonMode.WEEKLY) weeklyTeacher else "",
                    weeklyLocation = if (draft.mode == LessonMode.WEEKLY) weeklyLocation else null,
                    aSubject = if (draft.mode == LessonMode.ALTERNATING) aSubject else "",
                    aTeacher = if (draft.mode == LessonMode.ALTERNATING) aTeacher else "",
                    aLocation = if (draft.mode == LessonMode.ALTERNATING) aLocation else null,
                    bSubject = if (draft.mode == LessonMode.ALTERNATING) bSubject else "",
                    bTeacher = if (draft.mode == LessonMode.ALTERNATING) bTeacher else "",
                    bLocation = if (draft.mode == LessonMode.ALTERNATING) bLocation else null
                )
            )
            touchSyncDatasetMeta(DATASET_LESSONS)
        }
    }

    suspend fun syncDayTypes() {
        val settings = dao.getSettings() ?: return
        val longBreaks = dao.getLongBreaksOnce()
        val breakRanges = longBreaks.map { it.startDate..it.endDate }
        val existing = dao.getDayTypesOnce().associateBy { it.date }

        val rebuilt = mutableListOf<DayTypeEntity>()
        for (date in settings.termStart.toDateRange(settings.termEnd)) {
            val autoHoliday = isAutoHoliday(date, breakRanges)
            val manual = existing[date]
            val resolved = when {
                autoHoliday -> DayType.HOLIDAY
                manual != null -> manual.dayType
                else -> DayType.A
            }
            rebuilt += DayTypeEntity(
                date = date,
                dayType = resolved,
                overrideLessonDayOfWeek = if (autoHoliday) null else manual?.overrideLessonDayOfWeek,
                overrideLessonDayType = if (autoHoliday) null else manual?.overrideLessonDayType,
                holidaySpecialLabel = if (resolved == DayType.HOLIDAY) manual?.holidaySpecialLabel else null
            )
        }

        dao.upsertDayTypes(rebuilt)
        dao.deleteDayTypesOutsideRange(settings.termStart, settings.termEnd)
    }

    suspend fun generateLessons(range: ExportRange, today: LocalDate = LocalDate.now()): List<GeneratedLesson> {
        syncDayTypes()

        val settings = dao.getSettings() ?: return emptyList()
        val dayTypeMap = dao.getDayTypesOnce().associateBy { it.date }
        val lessons = dao.getLessonsOnce().associateBy { it.dayOfWeek to it.slotIndex }
        val cancelledLessons = dao.getCancelledLessonsOnce()
            .mapTo(mutableSetOf()) { it.date to it.slotIndex }
        val examLessonsByDate = dao.getExamLessonsOnce().groupBy { it.date }
        val examScheduleDates = dao.getExamDaySchedulesOnce()
            .map { it.date }
            .filterTo(mutableSetOf()) { date ->
                examLessonsByDate[date].orEmpty().any { it.hasEnteredContent() } && when (dayTypeMap[date]?.holidaySpecialLabel) {
                    HolidaySpecialLabel.MIDTERM, HolidaySpecialLabel.FINAL -> true
                    else -> false
                }
            }

        val dateBounds = when (range) {
            is ExportRange.Custom -> range.start..range.end
            is ExportRange.ThisWeek -> {
                // 週表示でも過去日は含めない:
                // 平日: 今日〜今週金曜 / 土日: 次週月曜〜次週金曜
                val startDate = when (today.dayOfWeek) {
                    DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                    else -> today
                }
                val weekStart = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weekEnd = weekStart.plusDays(4)
                maxOf(startDate, settings.termStart)..minOf(weekEnd, settings.termEnd)
            }
        }

        if (dateBounds.start > dateBounds.endInclusive) return emptyList()

        return buildList {
            for (date in dateBounds.start.toDateRange(dateBounds.endInclusive)) {
                if (date in examScheduleDates) {
                    examLessonsByDate[date].orEmpty()
                        .sortedBy { it.slotIndex }
                        .filter { it.subject.isNotBlank() }
                        .forEach { exam ->
                            add(
                                GeneratedLesson(
                                    date = date,
                                    slot = jp.linkserver.nittcsc.logic.ClassSlot(
                                        index = exam.slotIndex,
                                        label = formatExamPeriodLabel(
                                            exam.slotIndex,
                                            settings.periodLabelStyle
                                        ),
                                        start = LocalTime.of(exam.startHour, exam.startMinute),
                                        end = LocalTime.of(exam.endHour, exam.endMinute)
                                    ),
                                    subject = exam.subject,
                                    teacher = exam.teacher
                                )
                            )
                        }
                    continue
                }
                if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) continue

                val dayTypeEntity = dayTypeMap[date]
                val dayType = dayTypeEntity?.dayType ?: DayType.A
                if (dayType == DayType.HOLIDAY) continue

                val dayKey = dayTypeEntity?.overrideLessonDayOfWeek ?: date.dayOfWeek.value
                val lessonDayType = dayTypeEntity?.overrideLessonDayType ?: dayType
                val slots = generateClassSlots(
                    settings.periodsPerDay, settings.periodDurationMin, settings.breakBetweenPeriodsMin,
                    settings.lunchBreakMin, settings.firstPeriodStartHour, settings.firstPeriodStartMinute,
                    settings.periodLabelStyle, settings.lunchAfterPeriod
                )
                for (slot in slots) {
                    if ((date to slot.index) in cancelledLessons) continue
                    val lesson = lessons[dayKey to slot.index] ?: continue
                    val resolved = dao.getChangedLesson(date, slot.index)?.let {
                        ResolvedLesson(it.subject, it.teacher, it.location)
                    } ?: resolveLesson(lessonDayType, lesson) ?: continue
                    if (resolved.subject.isBlank()) continue

                    add(
                        GeneratedLesson(
                            date = date,
                            slot = slot,
                            subject = resolved.subject,
                            teacher = resolved.teacher
                        )
                    )
                }
            }
        }
    }

    internal suspend fun ensureLessonRows() {
        val periodsPerDay = dao.getSettings()?.periodsPerDay ?: 4
        val existing = dao.getLessonsOnce().associateBy { it.dayOfWeek to it.slotIndex }
        for (day in 1..5) {
            for (slot in 0 until periodsPerDay) {
                if ((day to slot) !in existing) {
                    dao.upsertLesson(
                        LessonEntity(
                            dayOfWeek = day,
                            slotIndex = slot,
                            mode = LessonMode.WEEKLY,
                            weeklySubject = "",
                            weeklyTeacher = "",
                            weeklyLocation = null,
                            aSubject = "",
                            aTeacher = "",
                            aLocation = null,
                            bSubject = "",
                            bTeacher = "",
                            bLocation = null
                        )
                    )
                }
            }
        }
    }

    private fun resolveLesson(dayType: DayType, lesson: LessonEntity): ResolvedLesson? {
        return when (lesson.mode) {
            LessonMode.WEEKLY -> {
                if (lesson.weeklySubject.isBlank()) null
                else ResolvedLesson(lesson.weeklySubject, lesson.weeklyTeacher, lesson.weeklyLocation)
            }

            LessonMode.ALTERNATING -> {
                when (dayType) {
                    DayType.A -> {
                        if (lesson.aSubject.isBlank()) null
                        else ResolvedLesson(lesson.aSubject, lesson.aTeacher, lesson.aLocation)
                    }

                    DayType.B -> {
                        if (lesson.bSubject.isBlank()) null
                        else ResolvedLesson(lesson.bSubject, lesson.bTeacher, lesson.bLocation)
                    }

                    DayType.HOLIDAY -> null
                }
            }
        }
    }

    private suspend fun resolveBaseLessonForDate(date: LocalDate, slotIndex: Int): ResolvedLesson? {
        if (date.dayOfWeek.value !in 1..5) return null
        val dayTypeEntity = dao.getDayType(date)
        val dayType = dayTypeEntity?.dayType ?: DayType.A
        if (dayType == DayType.HOLIDAY) return null
        val lessonDayOfWeek = dayTypeEntity?.overrideLessonDayOfWeek ?: date.dayOfWeek.value
        val lessonDayType = dayTypeEntity?.overrideLessonDayType ?: dayType
        val lesson = dao.getLesson(lessonDayOfWeek, slotIndex) ?: return null
        return resolveLesson(lessonDayType, lesson)
    }

    private suspend fun resolveEffectiveLessonForDate(date: LocalDate, slotIndex: Int): ResolvedLesson? {
        if (date.dayOfWeek.value !in 1..5) return null
        val dayType = dao.getDayType(date)?.dayType ?: DayType.A
        if (dayType == DayType.HOLIDAY) return null
        return applyChangedLesson(
            baseLesson = resolveBaseLessonForDate(date, slotIndex),
            changedLesson = dao.getChangedLesson(date, slotIndex)
        )
    }

    private fun defaultSettings(today: LocalDate): SettingsEntity {
        val fiscalStartYear = if (today.month.value >= Month.APRIL.value) today.year else today.year - 1
        return SettingsEntity(
            id = 1,
            termStart = LocalDate.of(fiscalStartYear, Month.APRIL, 1),
            termEnd = LocalDate.of(fiscalStartYear + 1, Month.MARCH, 31),
            arrivalHour = 8,
            arrivalMinute = 30
        )
    }

    private fun isAutoHoliday(date: LocalDate, breakRanges: List<ClosedRange<LocalDate>>): Boolean {
        val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
        val longBreak = breakRanges.any { date in it }
        return weekend || longBreak || JapaneseHolidayCalculator.isHoliday(date)
    }

    internal fun clampFutureMetaTimestamp(value: Long, now: Long): Long {
        return value.coerceAtMost(now + MAX_FUTURE_META_DRIFT_MS)
    }

    internal suspend fun touchSyncDatasetMeta(vararg datasetKeys: String) {
        if (datasetKeys.isEmpty()) return
        val now = System.currentTimeMillis()
        val existingByKey = dao.getAllSyncDatasetMeta().associateBy { it.datasetKey }
        dao.upsertSyncDatasetMetaList(
            datasetKeys.distinct().map { key ->
                val previous = existingByKey[key]?.lastUpdatedAt ?: 0L
                SyncDatasetMetaEntity(
                    datasetKey = key,
                    lastUpdatedAt = clampFutureMetaTimestamp(maxOf(now, previous + 1L), now),
                    lastUpdatedByDeviceId = ""
                )
            }
        )
    }

    // Task管理メソッド

    suspend fun upsertTask(task: TaskEntity): TaskEntity {
        return db.withTransaction {
            val persistedTask = task.copy(updatedAt = System.currentTimeMillis())
            val rowId = dao.upsertTask(persistedTask)
            touchSyncDatasetMeta(DATASET_TASKS)
            if (persistedTask.id == 0L && rowId > 0L) {
                persistedTask.copy(id = rowId)
            } else {
                persistedTask
            }
        }
    }

    suspend fun upsertTasks(tasks: List<TaskEntity>) {
        if (tasks.isEmpty()) return
        db.withTransaction {
            val now = System.currentTimeMillis()
            dao.upsertTasks(tasks.map { it.copy(updatedAt = now) })
            touchSyncDatasetMeta(DATASET_TASKS)
        }
    }

    suspend fun getTaskById(id: Long): TaskEntity? {
        return dao.getTaskById(id)
    }

    suspend fun getTasksByDate(date: LocalDate): List<TaskEntity> {
        return dao.getTasksByDate(date)
    }

    suspend fun getTasksInRange(fromDate: LocalDate, toDate: LocalDate): List<TaskEntity> {
        return dao.getTasksInRange(fromDate, toDate)
    }

    suspend fun getIncompleteTasksOnce(): List<TaskEntity> {
        return dao.getIncompleteTasksOnce()
    }

    suspend fun getTasksByLessonId(lessonId: Long): List<TaskEntity> {
        return dao.getTasksByLessonId(lessonId)
    }

    suspend fun deleteTask(taskId: Long) {
        db.withTransaction {
            dao.deleteTask(taskId)
            touchSyncDatasetMeta(DATASET_TASKS)
        }
    }

    suspend fun deleteTasksByLessonId(lessonId: Long) {
        db.withTransaction {
            dao.deleteTasksByLessonId(lessonId)
            touchSyncDatasetMeta(DATASET_TASKS)
        }
    }

    suspend fun markTaskAsComplete(taskId: Long, completedDate: LocalDate = LocalDate.now()) {
        db.withTransaction {
            val task = dao.getTaskById(taskId) ?: return@withTransaction
            dao.upsertTask(task.copy(isCompleted = true, completedDate = completedDate, updatedAt = System.currentTimeMillis()))
            touchSyncDatasetMeta(DATASET_TASKS)
        }
    }

    suspend fun markTaskAsIncomplete(taskId: Long) {
        db.withTransaction {
            val task = dao.getTaskById(taskId) ?: return@withTransaction
            dao.upsertTask(task.copy(isCompleted = false, completedDate = null, updatedAt = System.currentTimeMillis()))
            touchSyncDatasetMeta(DATASET_TASKS)
        }
    }

    // Plan管理メソッド

    suspend fun upsertPlan(plan: PlanEntity): PlanEntity {
        return db.withTransaction {
            val persistedPlan = plan.copy(updatedAt = System.currentTimeMillis())
            val rowId = dao.upsertPlan(persistedPlan)
            touchSyncDatasetMeta(DATASET_PLANS)
            if (persistedPlan.id == 0L && rowId > 0L) {
                persistedPlan.copy(id = rowId)
            } else {
                persistedPlan
            }
        }
    }

    suspend fun upsertPlans(plans: List<PlanEntity>) {
        if (plans.isEmpty()) return
        db.withTransaction {
            val now = System.currentTimeMillis()
            dao.upsertPlans(plans.map { it.copy(updatedAt = now) })
            touchSyncDatasetMeta(DATASET_PLANS)
        }
    }

    suspend fun getPlanById(id: Long): PlanEntity? {
        return dao.getPlanById(id)
    }

    suspend fun getPlansByDate(date: LocalDate): List<PlanEntity> {
        return dao.getPlansByDate(date)
    }

    suspend fun getPlansInRange(fromDate: LocalDate, toDate: LocalDate): List<PlanEntity> {
        return dao.getPlansInRange(fromDate, toDate)
    }

    suspend fun getIncompletePlansOnce(): List<PlanEntity> {
        return dao.getIncompletePlansOnce()
    }

    suspend fun getPlansByLessonId(lessonId: Long): List<PlanEntity> {
        return dao.getPlansByLessonId(lessonId)
    }

    suspend fun deletePlan(planId: Long) {
        db.withTransaction {
            dao.deletePlan(planId)
            touchSyncDatasetMeta(DATASET_PLANS)
        }
    }

    suspend fun deletePlansByLessonId(lessonId: Long) {
        db.withTransaction {
            dao.deletePlansByLessonId(lessonId)
            touchSyncDatasetMeta(DATASET_PLANS)
        }
    }

    suspend fun markPlanAsComplete(planId: Long, completedDate: LocalDate = LocalDate.now()) {
        db.withTransaction {
            val plan = dao.getPlanById(planId) ?: return@withTransaction
            dao.upsertPlan(plan.copy(isCompleted = true, completedDate = completedDate, updatedAt = System.currentTimeMillis()))
            touchSyncDatasetMeta(DATASET_PLANS)
        }
    }

    suspend fun markPlanAsIncomplete(planId: Long) {
        db.withTransaction {
            val plan = dao.getPlanById(planId) ?: return@withTransaction
            dao.upsertPlan(plan.copy(isCompleted = false, completedDate = null, updatedAt = System.currentTimeMillis()))
            touchSyncDatasetMeta(DATASET_PLANS)
        }
    }

    /**
     * targetDateのためにlessonIdで学科と教師を解決するか、
     * 課題のuseTeacherMatchingフラグに基づいて決定
     */
    suspend fun resolveTaskSubjectAndTeacher(
        task: TaskEntity,
        targetDate: LocalDate
    ): Pair<String, String>? {
        if (task.lessonId == null) {
            // 直接指定されたsubjectとteacherを使う
            return if (task.subject.isNotBlank()) {
                task.subject to (task.teacher ?: "")
            } else {
                null
            }
        }

        val lesson = dao.getLesson(
            getDayOfWeekForDate(targetDate),
            getSlotIndexForDate(targetDate)
        ) ?: return null

        // useTeacherMatchingがfalseの場合はSUBJECT_ONLYで学科のみを使う
        val dayType = dao.getDayType(targetDate)?.dayType ?: DayType.A
        val resolved = resolveLesson(dayType, lesson) ?: return null

        return if (task.useTeacherMatching) {
            // SUBJECT_AND_TEACHER: 学科と教師の両方を使う
            resolved.subject to resolved.teacher
        } else {
            // SUBJECT_ONLY: 学科のみを使う
            task.subject to (task.teacher ?: resolved.teacher)
        }
    }

    private fun getDayOfWeekForDate(date: LocalDate): Int {
        return date.dayOfWeek.value
    }

    private fun getSlotIndexForDate(date: LocalDate): Int {
        // これは実装が必要な場合に使用もここではsuspendなので、
        // 実際にはViewModelで処理する可能性が高い
        return 0 // プレースホルダー
    }

    /**
     * 学科と教師を条件に、fromDate 以降の最初のレッスン日を検索
     * @param subject 検索対象の学科
     * @param teacher 検索対象の教師（useTeacherMatchingがtrueの場合のみ使用）
     * @param useTeacherMatching true: 学科と教師の両方で検索、false: 学科のみで検索
     * @param fromDate 検索開始日（デフォルト: 今日）
     * @return マッチするレッスン日、見つからない場合はnull
     */
    suspend fun calculateNextLessonDate(
        subject: String,
        teacher: String?,
        useTeacherMatching: Boolean,
        fromDate: LocalDate = LocalDate.now()
    ): LocalDate? {
        return calculateNextLessonDateTime(subject, teacher, useTeacherMatching, fromDate)?.first
    }

    suspend fun calculateNextLessonDateTime(
        subject: String,
        teacher: String?,
        useTeacherMatching: Boolean,
        fromDate: LocalDate = LocalDate.now(),
        fromTime: LocalTime = LocalTime.now()
    ): Pair<LocalDate, LocalTime>? {
        val settings = dao.getSettings() ?: return null
        val strictTeacherMatching = useTeacherMatching && !teacher.isNullOrBlank()

        val slots = generateClassSlots(
            periodsPerDay = settings.periodsPerDay,
            periodDurationMin = settings.periodDurationMin,
            breakBetweenPeriodsMin = settings.breakBetweenPeriodsMin,
            lunchBreakMin = settings.lunchBreakMin,
            firstPeriodStartHour = settings.firstPeriodStartHour,
            firstPeriodStartMinute = settings.firstPeriodStartMinute,
            periodLabelStyle = settings.periodLabelStyle,
            lunchAfterPeriod = settings.lunchAfterPeriod
        )

        suspend fun search(requireTeacherMatch: Boolean): Pair<LocalDate, LocalTime>? {
            for (date in fromDate.toDateRange(settings.termEnd)) {
                if (date.dayOfWeek.value !in 1..5) continue

                val dayTypeEntity = dao.getDayType(date)
                val dayType = dayTypeEntity?.dayType ?: DayType.A
                if (dayType == DayType.HOLIDAY) continue

                for (slot in slots) {
                    if (date == fromDate && slot.start < fromTime) continue
                    val slotIndex = slot.index
                    if (dao.getCancelledLesson(date, slotIndex) != null) continue
                    val resolved = resolveEffectiveLessonForDate(date, slotIndex) ?: continue

                    val matches = lessonMatchesSearch(
                        resolved = resolved,
                        subject = subject,
                        teacher = teacher,
                        requireTeacherMatch = requireTeacherMatch
                    )

                    if (matches) return date to slot.start
                }
            }
            return null
        }

        return search(strictTeacherMatching) ?: if (strictTeacherMatching) search(false) else null
    }

    suspend fun calculatePreviousLessonDateTime(
        subject: String,
        teacher: String?,
        useTeacherMatching: Boolean,
        fromDate: LocalDate = LocalDate.now(),
        currentTime: LocalTime = LocalTime.now()
    ): Pair<LocalDate, LocalTime>? {
        val settings = dao.getSettings() ?: return null
        val strictTeacherMatching = useTeacherMatching && !teacher.isNullOrBlank()

        val slots = generateClassSlots(
            periodsPerDay = settings.periodsPerDay,
            periodDurationMin = settings.periodDurationMin,
            breakBetweenPeriodsMin = settings.breakBetweenPeriodsMin,
            lunchBreakMin = settings.lunchBreakMin,
            firstPeriodStartHour = settings.firstPeriodStartHour,
            firstPeriodStartMinute = settings.firstPeriodStartMinute,
            periodLabelStyle = settings.periodLabelStyle,
            lunchAfterPeriod = settings.lunchAfterPeriod
        )

        suspend fun search(requireTeacherMatch: Boolean): Pair<LocalDate, LocalTime>? {
            var date = fromDate
            while (date >= settings.termStart) {
                if (date.dayOfWeek.value !in 1..5) {
                    date = date.minusDays(1)
                    continue
                }

                val dayTypeEntity = dao.getDayType(date)
                val dayType = dayTypeEntity?.dayType ?: DayType.A
                if (dayType == DayType.HOLIDAY) {
                    date = date.minusDays(1)
                    continue
                }

                for (slot in slots.sortedByDescending { it.index }) {
                    if (date == fromDate && slot.start >= currentTime) continue
                    val slotIndex = slot.index
                    if (dao.getCancelledLesson(date, slotIndex) != null) continue
                    val resolved = resolveEffectiveLessonForDate(date, slotIndex) ?: continue

                    val matches = lessonMatchesSearch(
                        resolved = resolved,
                        subject = subject,
                        teacher = teacher,
                        requireTeacherMatch = requireTeacherMatch
                    )

                    if (matches) return date to slot.start
                }

                date = date.minusDays(1)
            }

            return null
        }

        return search(strictTeacherMatching) ?: if (strictTeacherMatching) search(false) else null
    }

    suspend fun calculateNextLessonDateTimeSkipCurrent(
        subject: String,
        teacher: String?,
        useTeacherMatching: Boolean,
        fromDate: LocalDate = LocalDate.now(),
        currentTime: LocalTime = LocalTime.now()
    ): Pair<LocalDate, LocalTime>? {
        val settings = dao.getSettings() ?: return null
        val strictTeacherMatching = useTeacherMatching && !teacher.isNullOrBlank()

        val slots = generateClassSlots(
            periodsPerDay = settings.periodsPerDay,
            periodDurationMin = settings.periodDurationMin,
            breakBetweenPeriodsMin = settings.breakBetweenPeriodsMin,
            lunchBreakMin = settings.lunchBreakMin,
            firstPeriodStartHour = settings.firstPeriodStartHour,
            firstPeriodStartMinute = settings.firstPeriodStartMinute,
            periodLabelStyle = settings.periodLabelStyle,
            lunchAfterPeriod = settings.lunchAfterPeriod
        )
        
        val startDate = fromDate

        suspend fun searchToday(requireTeacherMatch: Boolean): Pair<LocalDate, LocalTime>? {
            val dayTypeEntity = dao.getDayType(startDate)
            val dayType = dayTypeEntity?.dayType ?: DayType.A
            if (startDate.dayOfWeek.value in 1..5 && dayType != DayType.HOLIDAY) {
                for (slot in slots) {
                    if (slot.start <= currentTime) continue

                    val slotIndex = slot.index
                    if (dao.getCancelledLesson(startDate, slotIndex) != null) continue
                    val resolved = resolveEffectiveLessonForDate(startDate, slotIndex) ?: continue

                    val matches = lessonMatchesSearch(
                        resolved = resolved,
                        subject = subject,
                        teacher = teacher,
                        requireTeacherMatch = requireTeacherMatch
                    )

                    if (matches) {
                        return startDate to slot.start
                    }
                }
            }
            return null
        }

        suspend fun searchAfterToday(requireTeacherMatch: Boolean): Pair<LocalDate, LocalTime>? {
            for (date in startDate.plusDays(1).toDateRange(settings.termEnd)) {
                if (date.dayOfWeek.value !in 1..5) continue

                val dayTypeEntity = dao.getDayType(date)
                val dayType = dayTypeEntity?.dayType ?: DayType.A
                if (dayType == DayType.HOLIDAY) continue

                for (slot in slots) {
                    val slotIndex = slot.index
                    if (dao.getCancelledLesson(date, slotIndex) != null) continue
                    val resolved = resolveEffectiveLessonForDate(date, slotIndex) ?: continue

                    val matches = lessonMatchesSearch(
                        resolved = resolved,
                        subject = subject,
                        teacher = teacher,
                        requireTeacherMatch = requireTeacherMatch
                    )

                    if (matches) {
                        return date to slot.start
                    }
                }
            }
            return null
        }

        searchToday(strictTeacherMatching)?.let { return it }
        searchAfterToday(strictTeacherMatching)?.let { return it }

        if (strictTeacherMatching) {
            searchToday(false)?.let { return it }
            return searchAfterToday(false)
        }

        return null
    }

    private fun lessonMatchesSearch(
        resolved: ResolvedLesson,
        subject: String,
        teacher: String?,
        requireTeacherMatch: Boolean
    ): Boolean {
        val normalizedResolvedSubject = resolved.subject.trim()
        val normalizedSubject = subject.trim()
        if (normalizedResolvedSubject.isBlank() || normalizedSubject.isBlank()) return false
        if (!normalizedResolvedSubject.equals(normalizedSubject, ignoreCase = true)) return false

        if (!requireTeacherMatch) return true

        val normalizedTeacher = teacher?.trim().orEmpty()
        if (normalizedTeacher.isBlank()) return true

        val resolvedTeacher = resolved.teacher.trim()
        if (resolvedTeacher.equals(normalizedTeacher, ignoreCase = true)) return true

        val resolvedTeacherCandidates = resolvedTeacher
            .replace('，', '、')
            .replace(',', '、')
            .replace('　', ' ')
            .split('、', ' ')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return resolvedTeacherCandidates.any { it.equals(normalizedTeacher, ignoreCase = true) }
    }

    suspend fun exportAllData(): String = dataTransfer.exportAllData()

    suspend fun exportSyncPayload(): org.json.JSONObject = dataTransfer.exportSyncPayload()

    suspend fun applySyncPayload(payload: org.json.JSONObject) {
        dataTransfer.applySyncPayload(payload)
    }

    suspend fun importAllData(json: String) {
        dataTransfer.importAllData(json)
    }
}

private fun LocalDate.toDateRange(endDate: LocalDate): Sequence<LocalDate> {
    return generateSequence(this) { current ->
        current.plusDays(1).takeIf { !it.isAfter(endDate) }
    }
}
