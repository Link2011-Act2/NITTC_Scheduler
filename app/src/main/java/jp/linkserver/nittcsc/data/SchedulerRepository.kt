package jp.linkserver.nittcsc.data

import jp.linkserver.nittcsc.logic.CLASS_SLOTS
import jp.linkserver.nittcsc.logic.ExportRange
import jp.linkserver.nittcsc.logic.GeneratedLesson
import jp.linkserver.nittcsc.logic.JapaneseHolidayCalculator
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

    companion object {
        private const val CURRENT_EXPORT_VERSION = 5
        private const val MIN_SUPPORTED_IMPORT_VERSION = 1
        const val DATASET_TASKS = "tasks"
        const val DATASET_PLANS = "plans"
        const val DATASET_SCHEDULE_SETTINGS = "scheduleSettings"
        const val DATASET_LESSONS = "lessons"
        const val DATASET_DAY_TYPES = "dayTypes"
        const val DATASET_LONG_BREAKS = "longBreaks"
        const val DATASET_CANCELLED_LESSONS = "cancelledLessons"
        val SYNC_DATASET_KEYS = listOf(
            DATASET_TASKS,
            DATASET_PLANS,
            DATASET_SCHEDULE_SETTINGS,
            DATASET_LESSONS,
            DATASET_DAY_TYPES,
            DATASET_LONG_BREAKS,
            DATASET_CANCELLED_LESSONS
        )
    }

    val settingsFlow: Flow<SettingsEntity?> = dao.observeSettings()
    val dayTypesFlow: Flow<List<DayTypeEntity>> = dao.observeDayTypes()
    val cancelledLessonsFlow: Flow<List<CancelledLessonEntity>> = dao.observeCancelledLessons()
    val longBreaksFlow: Flow<List<LongBreakEntity>> = dao.observeLongBreaks()
    val lessonsFlow: Flow<List<LessonEntity>> = dao.observeLessons()
    val tasksFlow: Flow<List<TaskEntity>> = dao.observeTasks()
    val incompleteTasksFlow: Flow<List<TaskEntity>> = dao.observeIncompleteTasks()
    val plansFlow: Flow<List<PlanEntity>> = dao.observePlans()
    val incompletePlansFlow: Flow<List<PlanEntity>> = dao.observeIncompletePlans()
    val syncProfileFlow: Flow<SyncProfileEntity?> = dao.observeSyncProfile()
    val syncRegisteredDevicesFlow: Flow<List<SyncRegisteredDeviceEntity>> = dao.observeSyncRegisteredDevices()

    suspend fun initialize(today: LocalDate = LocalDate.now()) {
        if (dao.getSettings() == null) {
            dao.upsertSettings(defaultSettings(today))
        }
        ensureLessonRows()
        syncDayTypes()
    }

    suspend fun resetToCurrentFiscalYear(today: LocalDate = LocalDate.now()) {
        val settings = defaultSettings(today)
        dao.upsertSettings(settings)
        syncDayTypes()
        touchSyncDatasetMeta(DATASET_SCHEDULE_SETTINGS, DATASET_DAY_TYPES)
    }

    suspend fun updateTerm(startDate: LocalDate, endDate: LocalDate) {
        val current = dao.getSettings() ?: defaultSettings(LocalDate.now())
        dao.upsertSettings(
            current.copy(
                termStart = minOf(startDate, endDate),
                termEnd = maxOf(startDate, endDate)
            )
        )
        syncDayTypes()
        touchSyncDatasetMeta(DATASET_SCHEDULE_SETTINGS, DATASET_DAY_TYPES)
    }

    suspend fun updateScheduleSettings(
        periodsPerDay: Int,
        periodDurationMin: Int,
        breakBetweenPeriodsMin: Int,
        lunchBreakMin: Int,
        lunchAfterPeriod: Int,
        firstPeriodStartHour: Int,
        firstPeriodStartMinute: Int,
        useKosenMode: Boolean,
        arrivalHour: Int,
        arrivalMinute: Int,
        departureHour: Int,
        departureMinute: Int
    ) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(
            current.copy(
                periodsPerDay = periodsPerDay,
                periodDurationMin = periodDurationMin,
                breakBetweenPeriodsMin = breakBetweenPeriodsMin,
                lunchBreakMin = lunchBreakMin,
                lunchAfterPeriod = lunchAfterPeriod,
                firstPeriodStartHour = firstPeriodStartHour,
                firstPeriodStartMinute = firstPeriodStartMinute,
                useKosenMode = useKosenMode,
                arrivalHour = arrivalHour,
                arrivalMinute = arrivalMinute,
                departureHour = departureHour,
                departureMinute = departureMinute
            )
        )
        ensureLessonRows()
        touchSyncDatasetMeta(DATASET_SCHEDULE_SETTINGS, DATASET_LESSONS)
    }

    suspend fun toggleLocalAi(enabled: Boolean) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(current.copy(enableLocalAi = enabled))
    }

    suspend fun toggleDrawerNavigation(enabled: Boolean) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(current.copy(useDrawerNavigation = enabled))
    }

    suspend fun toggleAddTasksToCalendar(enabled: Boolean) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(current.copy(addTasksToCalendar = enabled))
    }

    suspend fun toggleCurrentTimeMarker(enabled: Boolean) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(current.copy(showCurrentTimeMarker = enabled))
    }

    suspend fun toggleUnifyTaskPlanView(enabled: Boolean) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(current.copy(unifyTaskPlanView = enabled))
    }

    suspend fun toggleTlsSync(enabled: Boolean) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(current.copy(enableTlsSync = enabled))
    }

    suspend fun updateHfToken(token: String?) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(current.copy(hfToken = token))
    }

    suspend fun toggleDayType(date: LocalDate) {
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
                overrideLessonDayType = existing?.overrideLessonDayType
            )
        )
        touchSyncDatasetMeta(DATASET_DAY_TYPES)
    }

    suspend fun upsertDayType(date: LocalDate, dayType: DayType) {
        val existing = dao.getDayType(date)
        dao.upsertDayType(
            DayTypeEntity(
                date = date,
                dayType = dayType,
                overrideLessonDayOfWeek = existing?.overrideLessonDayOfWeek,
                overrideLessonDayType = existing?.overrideLessonDayType
            )
        )
        touchSyncDatasetMeta(DATASET_DAY_TYPES)
    }

    suspend fun upsertDayTypes(dates: List<LocalDate>, dayType: DayType) {
        val existing = dao.getDayTypesOnce().associateBy { it.date }
        val entities = dates.distinct().map { date ->
            DayTypeEntity(
                date = date,
                dayType = dayType,
                overrideLessonDayOfWeek = existing[date]?.overrideLessonDayOfWeek,
                overrideLessonDayType = existing[date]?.overrideLessonDayType
            )
        }
        if (entities.isNotEmpty()) {
            dao.upsertDayTypes(entities)
            touchSyncDatasetMeta(DATASET_DAY_TYPES)
        }
    }

    suspend fun upsertLessonOverride(date: LocalDate, dayOfWeek: Int, dayType: DayType) {
        dao.upsertDayType(
            DayTypeEntity(
                date = date,
                dayType = dayType,
                overrideLessonDayOfWeek = dayOfWeek,
                overrideLessonDayType = dayType
            )
        )
        touchSyncDatasetMeta(DATASET_DAY_TYPES)
    }

    suspend fun clearLessonOverride(date: LocalDate) {
        val existing = dao.getDayType(date) ?: return
        dao.upsertDayType(
            existing.copy(
                overrideLessonDayOfWeek = null,
                overrideLessonDayType = null
            )
        )
        touchSyncDatasetMeta(DATASET_DAY_TYPES)
    }

    suspend fun setLessonCancelled(date: LocalDate, slotIndex: Int, cancelled: Boolean) {
        if (cancelled) {
            dao.upsertCancelledLesson(CancelledLessonEntity(date = date, slotIndex = slotIndex))
        } else {
            dao.deleteCancelledLesson(date, slotIndex)
        }
        touchSyncDatasetMeta(DATASET_CANCELLED_LESSONS)
    }

    suspend fun upsertLongBreak(id: Long?, name: String, startDate: LocalDate, endDate: LocalDate) {
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

    suspend fun deleteLongBreak(longBreak: LongBreakEntity) {
        dao.deleteLongBreak(longBreak)
        syncDayTypes()
        touchSyncDatasetMeta(DATASET_LONG_BREAKS, DATASET_DAY_TYPES)
    }

    suspend fun upsertLesson(dayOfWeek: Int, slotIndex: Int, draft: LessonDraft) {
        val existing = dao.getLesson(dayOfWeek, slotIndex)
        dao.upsertLesson(
            LessonEntity(
                id = existing?.id ?: 0,
                dayOfWeek = dayOfWeek,
                slotIndex = slotIndex,
                mode = draft.mode,
                weeklySubject = draft.weeklySubject.trim(),
                weeklyTeacher = draft.weeklyTeacher.trim(),
                weeklyLocation = draft.weeklyLocation.trim().takeIf { it.isNotEmpty() },
                aSubject = draft.aSubject.trim(),
                aTeacher = draft.aTeacher.trim(),
                aLocation = draft.aLocation.trim().takeIf { it.isNotEmpty() },
                bSubject = draft.bSubject.trim(),
                bTeacher = draft.bTeacher.trim(),
                bLocation = draft.bLocation.trim().takeIf { it.isNotEmpty() }
            )
        )
        touchSyncDatasetMeta(DATASET_LESSONS)
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
                overrideLessonDayType = if (autoHoliday) null else manual?.overrideLessonDayType
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
                if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) continue

                val dayTypeEntity = dayTypeMap[date]
                val dayType = dayTypeEntity?.dayType ?: DayType.A
                if (dayType == DayType.HOLIDAY) continue

                val dayKey = dayTypeEntity?.overrideLessonDayOfWeek ?: date.dayOfWeek.value
                val lessonDayType = dayTypeEntity?.overrideLessonDayType ?: dayType
                val slots = generateClassSlots(
                    settings.periodsPerDay, settings.periodDurationMin, settings.breakBetweenPeriodsMin,
                    settings.lunchBreakMin, settings.firstPeriodStartHour, settings.firstPeriodStartMinute,
                    settings.useKosenMode, settings.lunchAfterPeriod
                )
                for (slot in slots) {
                    val lesson = lessons[dayKey to slot.index] ?: continue
                    val resolved = resolveLesson(lessonDayType, lesson) ?: continue
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

    private suspend fun ensureLessonRows() {
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

    private fun defaultSettings(today: LocalDate): SettingsEntity {
        val fiscalStartYear = if (today.month.value >= Month.APRIL.value) today.year else today.year - 1
        return SettingsEntity(
            id = 1,
            termStart = LocalDate.of(fiscalStartYear, Month.APRIL, 1),
            termEnd = LocalDate.of(fiscalStartYear + 1, Month.MARCH, 31)
        )
    }

    private fun isAutoHoliday(date: LocalDate, breakRanges: List<ClosedRange<LocalDate>>): Boolean {
        val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
        val longBreak = breakRanges.any { date in it }
        return weekend || longBreak || JapaneseHolidayCalculator.isHoliday(date)
    }

    private suspend fun touchSyncDatasetMeta(vararg datasetKeys: String) {
        if (datasetKeys.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.upsertSyncDatasetMetaList(
            datasetKeys.distinct().map { key ->
                SyncDatasetMetaEntity(
                    datasetKey = key,
                    lastUpdatedAt = now,
                    lastUpdatedByDeviceId = ""
                )
            }
        )
    }

    // Task管理メソッド

    suspend fun upsertTask(task: TaskEntity) {
        dao.upsertTask(task)
        touchSyncDatasetMeta(DATASET_TASKS)
    }

    suspend fun upsertTasks(tasks: List<TaskEntity>) {
        if (tasks.isEmpty()) return
        dao.upsertTasks(tasks)
        touchSyncDatasetMeta(DATASET_TASKS)
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
        dao.deleteTask(taskId)
        touchSyncDatasetMeta(DATASET_TASKS)
    }

    suspend fun deleteTasksByLessonId(lessonId: Long) {
        dao.deleteTasksByLessonId(lessonId)
        touchSyncDatasetMeta(DATASET_TASKS)
    }

    suspend fun markTaskAsComplete(taskId: Long, completedDate: LocalDate = LocalDate.now()) {
        val task = dao.getTaskById(taskId) ?: return
        dao.upsertTask(task.copy(isCompleted = true, completedDate = completedDate))
        touchSyncDatasetMeta(DATASET_TASKS)
    }

    suspend fun markTaskAsIncomplete(taskId: Long) {
        val task = dao.getTaskById(taskId) ?: return
        dao.upsertTask(task.copy(isCompleted = false, completedDate = null))
        touchSyncDatasetMeta(DATASET_TASKS)
    }

    // Plan管理メソッド

    suspend fun upsertPlan(plan: PlanEntity) {
        dao.upsertPlan(plan)
        touchSyncDatasetMeta(DATASET_PLANS)
    }

    suspend fun upsertPlans(plans: List<PlanEntity>) {
        if (plans.isEmpty()) return
        dao.upsertPlans(plans)
        touchSyncDatasetMeta(DATASET_PLANS)
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
        dao.deletePlan(planId)
        touchSyncDatasetMeta(DATASET_PLANS)
    }

    suspend fun deletePlansByLessonId(lessonId: Long) {
        dao.deletePlansByLessonId(lessonId)
        touchSyncDatasetMeta(DATASET_PLANS)
    }

    suspend fun markPlanAsComplete(planId: Long, completedDate: LocalDate = LocalDate.now()) {
        val plan = dao.getPlanById(planId) ?: return
        dao.upsertPlan(plan.copy(isCompleted = true, completedDate = completedDate))
        touchSyncDatasetMeta(DATASET_PLANS)
    }

    suspend fun markPlanAsIncomplete(planId: Long) {
        val plan = dao.getPlanById(planId) ?: return
        dao.upsertPlan(plan.copy(isCompleted = false, completedDate = null))
        touchSyncDatasetMeta(DATASET_PLANS)
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
            useKosenMode = settings.useKosenMode,
            lunchAfterPeriod = settings.lunchAfterPeriod
        )

        suspend fun search(requireTeacherMatch: Boolean): Pair<LocalDate, LocalTime>? {
            for (date in fromDate.toDateRange(settings.termEnd)) {
                if (date.dayOfWeek.value !in 1..5) continue

                val dayTypeEntity = dao.getDayType(date)
                val dayType = dayTypeEntity?.dayType ?: DayType.A
                if (dayType == DayType.HOLIDAY) continue
                val lessonDayOfWeek = dayTypeEntity?.overrideLessonDayOfWeek ?: date.dayOfWeek.value
                val lessonDayType = dayTypeEntity?.overrideLessonDayType ?: dayType

                for (slot in slots) {
                    if (date == fromDate && slot.start < fromTime) continue
                    val slotIndex = slot.index
                    if (dao.getCancelledLesson(date, slotIndex) != null) continue
                    val lesson = dao.getLesson(lessonDayOfWeek, slotIndex) ?: continue
                    val resolved = resolveLesson(lessonDayType, lesson) ?: continue

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
            useKosenMode = settings.useKosenMode,
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
                val lessonDayOfWeek = dayTypeEntity?.overrideLessonDayOfWeek ?: date.dayOfWeek.value
                val lessonDayType = dayTypeEntity?.overrideLessonDayType ?: dayType

                for (slot in slots.sortedByDescending { it.index }) {
                    if (date == fromDate && slot.start >= currentTime) continue
                    val slotIndex = slot.index
                    if (dao.getCancelledLesson(date, slotIndex) != null) continue
                    val lesson = dao.getLesson(lessonDayOfWeek, slotIndex) ?: continue
                    val resolved = resolveLesson(lessonDayType, lesson) ?: continue

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
            useKosenMode = settings.useKosenMode,
            lunchAfterPeriod = settings.lunchAfterPeriod
        )
        
        val startDate = fromDate

        suspend fun searchToday(requireTeacherMatch: Boolean): Pair<LocalDate, LocalTime>? {
            val dayTypeEntity = dao.getDayType(startDate)
            val dayType = dayTypeEntity?.dayType ?: DayType.A
            if (startDate.dayOfWeek.value in 1..5 && dayType != DayType.HOLIDAY) {
                val lessonDayOfWeek = dayTypeEntity?.overrideLessonDayOfWeek ?: startDate.dayOfWeek.value
                val lessonDayType = dayTypeEntity?.overrideLessonDayType ?: dayType
                for (slot in slots) {
                    if (slot.start <= currentTime) continue

                    val slotIndex = slot.index
                    if (dao.getCancelledLesson(startDate, slotIndex) != null) continue
                    val lesson = dao.getLesson(lessonDayOfWeek, slotIndex) ?: continue
                    val resolved = resolveLesson(lessonDayType, lesson) ?: continue

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

        searchToday(strictTeacherMatching)?.let { return it }
        if (strictTeacherMatching) {
            searchToday(false)?.let { return it }
        }
        
        // 今日に授業がなければ、以降の日を検索
        return calculateNextLessonDateTime(
            subject = subject,
            teacher = teacher,
            useTeacherMatching = if (strictTeacherMatching) false else useTeacherMatching,
            fromDate = startDate.plusDays(1),
            fromTime = LocalTime.MIN
        )
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

        return resolved.teacher.trim().equals(normalizedTeacher, ignoreCase = true)
    }

    suspend fun exportAllData(): String {
        val settings = dao.getSettings()
        val lessons = dao.getLessonsOnce()
        val longBreaks = dao.getLongBreaksOnce()
        val dayTypes = dao.getDayTypesOnce()
        val tasks = dao.getTasksOnce()
        val plans = dao.getPlansOnce()
        val cancelledLessons = dao.getCancelledLessonsOnce()

        val root = org.json.JSONObject()
        root.put("version", CURRENT_EXPORT_VERSION)
        root.put("exportedAt", LocalDate.now().toString())
        root.put("schema", "nittc-scheduler")

        if (settings != null) {
            root.put("settings", org.json.JSONObject().also { s ->
                s.put("termStart", settings.termStart.toString())
                s.put("termEnd", settings.termEnd.toString())
                s.put("enableLocalAi", settings.enableLocalAi)
                s.put("hfToken", settings.hfToken)
                s.put("periodsPerDay", settings.periodsPerDay)
                s.put("periodDurationMin", settings.periodDurationMin)
                s.put("breakBetweenPeriodsMin", settings.breakBetweenPeriodsMin)
                s.put("lunchBreakMin", settings.lunchBreakMin)
                s.put("lunchAfterPeriod", settings.lunchAfterPeriod)
                s.put("firstPeriodStartHour", settings.firstPeriodStartHour)
                s.put("firstPeriodStartMinute", settings.firstPeriodStartMinute)
                s.put("useKosenMode", settings.useKosenMode)
                s.put("useDrawerNavigation", settings.useDrawerNavigation)
                s.put("addTasksToCalendar", settings.addTasksToCalendar)
                s.put("showCurrentTimeMarker", settings.showCurrentTimeMarker)
                s.put("arrivalHour", settings.arrivalHour)
                s.put("arrivalMinute", settings.arrivalMinute)
                s.put("departureHour", settings.departureHour)
                s.put("departureMinute", settings.departureMinute)
                s.put("unifyTaskPlanView", settings.unifyTaskPlanView)
                s.put("enableTlsSync", settings.enableTlsSync)
            })
        }

        root.put("lessons", org.json.JSONArray().also { arr ->
            lessons.forEach { lesson ->
                arr.put(org.json.JSONObject().also { obj ->
                    obj.put("dayOfWeek", lesson.dayOfWeek)
                    obj.put("slotIndex", lesson.slotIndex)
                    obj.put("mode", lesson.mode.name)
                    obj.put("weeklySubject", lesson.weeklySubject)
                    obj.put("weeklyTeacher", lesson.weeklyTeacher)
                    if (lesson.weeklyLocation != null) obj.put("weeklyLocation", lesson.weeklyLocation)
                    obj.put("aSubject", lesson.aSubject)
                    obj.put("aTeacher", lesson.aTeacher)
                    if (lesson.aLocation != null) obj.put("aLocation", lesson.aLocation)
                    obj.put("bSubject", lesson.bSubject)
                    obj.put("bTeacher", lesson.bTeacher)
                    if (lesson.bLocation != null) obj.put("bLocation", lesson.bLocation)
                })
            }
        })

        root.put("longBreaks", org.json.JSONArray().also { arr ->
            longBreaks.forEach { lb ->
                arr.put(org.json.JSONObject().also { obj ->
                    obj.put("name", lb.name)
                    obj.put("startDate", lb.startDate.toString())
                    obj.put("endDate", lb.endDate.toString())
                })
            }
        })

        root.put("dayTypes", org.json.JSONArray().also { arr ->
            dayTypes.forEach { dt ->
                arr.put(org.json.JSONObject().also { obj ->
                    obj.put("date", dt.date.toString())
                    obj.put("dayType", dt.dayType.name)
                    if (dt.overrideLessonDayOfWeek != null) obj.put("overrideLessonDayOfWeek", dt.overrideLessonDayOfWeek)
                    if (dt.overrideLessonDayType != null) obj.put("overrideLessonDayType", dt.overrideLessonDayType.name)
                })
            }
        })

        root.put("tasks", org.json.JSONArray().also { arr ->
            tasks.forEach { task ->
                arr.put(org.json.JSONObject().also { obj ->
                    if (task.lessonId != null) obj.put("lessonId", task.lessonId)
                    obj.put("subject", task.subject)
                    if (task.teacher != null) obj.put("teacher", task.teacher)
                    obj.put("title", task.title)
                    if (task.description != null) obj.put("description", task.description)
                    obj.put("dueDate", task.dueDate.toString())
                    obj.put("dueHour", task.dueHour)
                    obj.put("dueMinute", task.dueMinute)
                    obj.put("isCompleted", task.isCompleted)
                    if (task.completedDate != null) obj.put("completedDate", task.completedDate.toString())
                    obj.put("createdDate", task.createdDate.toString())
                    obj.put("priority", task.priority)
                    obj.put("useTeacherMatching", task.useTeacherMatching)
                    if (task.calendarEventId != null) obj.put("calendarEventId", task.calendarEventId)
                })
            }
        })

        root.put("plans", org.json.JSONArray().also { arr ->
            plans.forEach { plan ->
                arr.put(org.json.JSONObject().also { obj ->
                    if (plan.lessonId != null) obj.put("lessonId", plan.lessonId)
                    obj.put("subject", plan.subject)
                    if (plan.teacher != null) obj.put("teacher", plan.teacher)
                    obj.put("title", plan.title)
                    if (plan.description != null) obj.put("description", plan.description)
                    obj.put("dueDate", plan.dueDate.toString())
                    obj.put("dueHour", plan.dueHour)
                    obj.put("dueMinute", plan.dueMinute)
                    obj.put("isCompleted", plan.isCompleted)
                    if (plan.completedDate != null) obj.put("completedDate", plan.completedDate.toString())
                    obj.put("createdDate", plan.createdDate.toString())
                    obj.put("priority", plan.priority)
                    obj.put("useTeacherMatching", plan.useTeacherMatching)
                    if (plan.calendarEventId != null) obj.put("calendarEventId", plan.calendarEventId)
                })
            }
        })

        root.put("cancelledLessons", org.json.JSONArray().also { arr ->
            cancelledLessons.forEach { cl ->
                arr.put(org.json.JSONObject().also { obj ->
                    obj.put("date", cl.date.toString())
                    obj.put("slotIndex", cl.slotIndex)
                    obj.put("createdAt", cl.createdAt)
                })
            }
        })

        return root.toString(2)
    }

    suspend fun exportSyncPayload(): org.json.JSONObject {
        val settings = dao.getSettings()
        val lessons = dao.getLessonsOnce()
        val longBreaks = dao.getLongBreaksOnce()
        val dayTypes = dao.getDayTypesOnce()
        val tasks = dao.getTasksOnce()
        val plans = dao.getPlansOnce()
        val profile = dao.getSyncProfile()
        val now = System.currentTimeMillis()
        val datasetMetaByKey = dao.getAllSyncDatasetMeta().associateBy { it.datasetKey }

        val root = org.json.JSONObject()
        root.put("device", org.json.JSONObject().also { d ->
            d.put("deviceId", profile?.deviceId ?: "")
            d.put("deviceName", profile?.deviceName ?: "")
        })

        val meta = org.json.JSONObject()
        SYNC_DATASET_KEYS.forEach { key ->
            val datasetMeta = datasetMetaByKey[key]
            meta.put(key, org.json.JSONObject().also { m ->
                m.put("updatedAt", datasetMeta?.lastUpdatedAt?.takeIf { it > 0L } ?: now)
                m.put("updatedByDeviceId", datasetMeta?.lastUpdatedByDeviceId ?: (profile?.deviceId ?: ""))
            })
        }
        root.put("metadata", meta)

        if (settings != null) {
            root.put(DATASET_SCHEDULE_SETTINGS, org.json.JSONObject().also { s ->
                s.put("termStart", settings.termStart.toString())
                s.put("termEnd", settings.termEnd.toString())
                s.put("periodsPerDay", settings.periodsPerDay)
                s.put("periodDurationMin", settings.periodDurationMin)
                s.put("breakBetweenPeriodsMin", settings.breakBetweenPeriodsMin)
                s.put("lunchBreakMin", settings.lunchBreakMin)
                s.put("lunchAfterPeriod", settings.lunchAfterPeriod)
                s.put("firstPeriodStartHour", settings.firstPeriodStartHour)
                s.put("firstPeriodStartMinute", settings.firstPeriodStartMinute)
                s.put("useKosenMode", settings.useKosenMode)
                s.put("arrivalHour", settings.arrivalHour)
                s.put("arrivalMinute", settings.arrivalMinute)
                s.put("departureHour", settings.departureHour)
                s.put("departureMinute", settings.departureMinute)
            })
        }

        root.put(DATASET_LESSONS, org.json.JSONArray().also { arr ->
            lessons.forEach { lesson ->
                arr.put(org.json.JSONObject().also { obj ->
                    obj.put("dayOfWeek", lesson.dayOfWeek)
                    obj.put("slotIndex", lesson.slotIndex)
                    obj.put("mode", lesson.mode.name)
                    obj.put("weeklySubject", lesson.weeklySubject)
                    obj.put("weeklyTeacher", lesson.weeklyTeacher)
                    if (lesson.weeklyLocation != null) obj.put("weeklyLocation", lesson.weeklyLocation)
                    obj.put("aSubject", lesson.aSubject)
                    obj.put("aTeacher", lesson.aTeacher)
                    if (lesson.aLocation != null) obj.put("aLocation", lesson.aLocation)
                    obj.put("bSubject", lesson.bSubject)
                    obj.put("bTeacher", lesson.bTeacher)
                    if (lesson.bLocation != null) obj.put("bLocation", lesson.bLocation)
                })
            }
        })

        root.put(DATASET_LONG_BREAKS, org.json.JSONArray().also { arr ->
            longBreaks.forEach { lb ->
                arr.put(org.json.JSONObject().also { obj ->
                    obj.put("name", lb.name)
                    obj.put("startDate", lb.startDate.toString())
                    obj.put("endDate", lb.endDate.toString())
                })
            }
        })

        root.put(DATASET_DAY_TYPES, org.json.JSONArray().also { arr ->
            dayTypes.forEach { dt ->
                arr.put(org.json.JSONObject().also { obj ->
                    obj.put("date", dt.date.toString())
                    obj.put("dayType", dt.dayType.name)
                    if (dt.overrideLessonDayOfWeek != null) obj.put("overrideLessonDayOfWeek", dt.overrideLessonDayOfWeek)
                    if (dt.overrideLessonDayType != null) obj.put("overrideLessonDayType", dt.overrideLessonDayType.name)
                })
            }
        })

        root.put(DATASET_TASKS, org.json.JSONArray().also { arr ->
            tasks.forEach { task ->
                arr.put(org.json.JSONObject().also { obj ->
                    if (task.lessonId != null) obj.put("lessonId", task.lessonId)
                    obj.put("subject", task.subject)
                    if (task.teacher != null) obj.put("teacher", task.teacher)
                    obj.put("title", task.title)
                    if (task.description != null) obj.put("description", task.description)
                    obj.put("dueDate", task.dueDate.toString())
                    obj.put("dueHour", task.dueHour)
                    obj.put("dueMinute", task.dueMinute)
                    obj.put("isCompleted", task.isCompleted)
                    if (task.completedDate != null) obj.put("completedDate", task.completedDate.toString())
                    obj.put("createdDate", task.createdDate.toString())
                    obj.put("priority", task.priority)
                    obj.put("useTeacherMatching", task.useTeacherMatching)
                })
            }
        })

        root.put(DATASET_PLANS, org.json.JSONArray().also { arr ->
            plans.forEach { plan ->
                arr.put(org.json.JSONObject().also { obj ->
                    if (plan.lessonId != null) obj.put("lessonId", plan.lessonId)
                    obj.put("subject", plan.subject)
                    if (plan.teacher != null) obj.put("teacher", plan.teacher)
                    obj.put("title", plan.title)
                    if (plan.description != null) obj.put("description", plan.description)
                    obj.put("dueDate", plan.dueDate.toString())
                    obj.put("dueHour", plan.dueHour)
                    obj.put("dueMinute", plan.dueMinute)
                    obj.put("isCompleted", plan.isCompleted)
                    if (plan.completedDate != null) obj.put("completedDate", plan.completedDate.toString())
                    obj.put("createdDate", plan.createdDate.toString())
                    obj.put("priority", plan.priority)
                    obj.put("useTeacherMatching", plan.useTeacherMatching)
                })
            }
        })

        val cancelledLessons = dao.getCancelledLessonsOnce()
        root.put(DATASET_CANCELLED_LESSONS, org.json.JSONArray().also { arr ->
            cancelledLessons.forEach { cl ->
                arr.put(org.json.JSONObject().also { obj ->
                    obj.put("date", cl.date.toString())
                    obj.put("slotIndex", cl.slotIndex)
                    obj.put("createdAt", cl.createdAt)
                })
            }
        })

        return root
    }

    suspend fun applySyncPayload(payload: org.json.JSONObject) {
        val settings = dao.getSettings()
        val touchedDatasets = mutableSetOf<String>()

        payload.optJSONObject(DATASET_SCHEDULE_SETTINGS)?.let { s ->
            touchedDatasets += DATASET_SCHEDULE_SETTINGS
            val base = settings ?: defaultSettings(LocalDate.now())
            dao.upsertSettings(base.copy(
                termStart = runCatching { java.time.LocalDate.parse(s.optString("termStart")) }.getOrElse { base.termStart },
                termEnd = runCatching { java.time.LocalDate.parse(s.optString("termEnd")) }.getOrElse { base.termEnd },
                periodsPerDay = if (s.has("periodsPerDay")) s.optInt("periodsPerDay", base.periodsPerDay) else base.periodsPerDay,
                periodDurationMin = if (s.has("periodDurationMin")) s.optInt("periodDurationMin", base.periodDurationMin) else base.periodDurationMin,
                breakBetweenPeriodsMin = if (s.has("breakBetweenPeriodsMin")) s.optInt("breakBetweenPeriodsMin", base.breakBetweenPeriodsMin) else base.breakBetweenPeriodsMin,
                lunchBreakMin = if (s.has("lunchBreakMin")) s.optInt("lunchBreakMin", base.lunchBreakMin) else base.lunchBreakMin,
                lunchAfterPeriod = if (s.has("lunchAfterPeriod")) s.optInt("lunchAfterPeriod", base.lunchAfterPeriod) else base.lunchAfterPeriod,
                firstPeriodStartHour = if (s.has("firstPeriodStartHour")) s.optInt("firstPeriodStartHour", base.firstPeriodStartHour) else base.firstPeriodStartHour,
                firstPeriodStartMinute = if (s.has("firstPeriodStartMinute")) s.optInt("firstPeriodStartMinute", base.firstPeriodStartMinute) else base.firstPeriodStartMinute,
                useKosenMode = if (s.has("useKosenMode")) s.optBoolean("useKosenMode", base.useKosenMode) else base.useKosenMode,
                arrivalHour = if (s.has("arrivalHour")) s.optInt("arrivalHour", base.arrivalHour) else base.arrivalHour,
                arrivalMinute = if (s.has("arrivalMinute")) s.optInt("arrivalMinute", base.arrivalMinute) else base.arrivalMinute,
                departureHour = if (s.has("departureHour")) s.optInt("departureHour", base.departureHour) else base.departureHour,
                departureMinute = if (s.has("departureMinute")) s.optInt("departureMinute", base.departureMinute) else base.departureMinute
            ))
        }

        payload.optJSONArray(DATASET_LESSONS)?.let { arr ->
            touchedDatasets += DATASET_LESSONS
            dao.deleteAllLessons()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                dao.upsertLesson(LessonEntity(
                    dayOfWeek = obj.optInt("dayOfWeek"),
                    slotIndex = obj.optInt("slotIndex"),
                    mode = runCatching { LessonMode.valueOf(obj.optString("mode", "WEEKLY")) }.getOrElse { LessonMode.WEEKLY },
                    weeklySubject = obj.optString("weeklySubject", ""),
                    weeklyTeacher = obj.optString("weeklyTeacher", ""),
                    weeklyLocation = obj.optString("weeklyLocation", null).takeIf { it?.isNotBlank() == true },
                    aSubject = obj.optString("aSubject", ""),
                    aTeacher = obj.optString("aTeacher", ""),
                    aLocation = obj.optString("aLocation", null).takeIf { it?.isNotBlank() == true },
                    bSubject = obj.optString("bSubject", ""),
                    bTeacher = obj.optString("bTeacher", ""),
                    bLocation = obj.optString("bLocation", null).takeIf { it?.isNotBlank() == true }
                ))
            }
        }

        payload.optJSONArray(DATASET_LONG_BREAKS)?.let { arr ->
            touchedDatasets += DATASET_LONG_BREAKS
            dao.deleteAllLongBreaks()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val startDate = runCatching { java.time.LocalDate.parse(obj.optString("startDate")) }.getOrNull() ?: continue
                val endDate = runCatching { java.time.LocalDate.parse(obj.optString("endDate")) }.getOrNull() ?: continue
                dao.upsertLongBreak(LongBreakEntity(name = obj.optString("name", ""), startDate = startDate, endDate = endDate))
            }
        }

        payload.optJSONArray(DATASET_DAY_TYPES)?.let { arr ->
            touchedDatasets += DATASET_DAY_TYPES
            dao.deleteAllDayTypes()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val date = runCatching { java.time.LocalDate.parse(obj.optString("date")) }.getOrNull() ?: continue
                val dayType = runCatching { DayType.valueOf(obj.optString("dayType", "A")) }.getOrElse { DayType.A }
                val overrideDow = if (obj.has("overrideLessonDayOfWeek")) obj.optInt("overrideLessonDayOfWeek") else null
                val overrideDt = if (obj.has("overrideLessonDayType")) runCatching { DayType.valueOf(obj.optString("overrideLessonDayType")) }.getOrNull() else null
                dao.upsertDayType(DayTypeEntity(date = date, dayType = dayType, overrideLessonDayOfWeek = overrideDow, overrideLessonDayType = overrideDt))
            }
        }

        payload.optJSONArray(DATASET_TASKS)?.let { arr ->
            touchedDatasets += DATASET_TASKS
            dao.deleteAllTasks()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val dueDate = runCatching { java.time.LocalDate.parse(obj.optString("dueDate")) }.getOrNull() ?: continue
                val createdDate = runCatching { java.time.LocalDate.parse(obj.optString("createdDate")) }.getOrElse { LocalDate.now() }
                val completedDate = if (obj.has("completedDate")) runCatching { java.time.LocalDate.parse(obj.optString("completedDate")) }.getOrNull() else null
                dao.upsertTask(TaskEntity(
                    lessonId = if (obj.has("lessonId")) obj.optLong("lessonId") else null,
                    subject = obj.optString("subject", ""),
                    teacher = obj.optString("teacher", null).takeIf { it?.isNotBlank() == true },
                    title = obj.optString("title", ""),
                    description = obj.optString("description", null).takeIf { it?.isNotBlank() == true },
                    dueDate = dueDate,
                    dueHour = obj.optInt("dueHour", 23),
                    dueMinute = obj.optInt("dueMinute", 59),
                    isCompleted = obj.optBoolean("isCompleted", false),
                    completedDate = completedDate,
                    createdDate = createdDate,
                    priority = obj.optInt("priority", 0),
                    useTeacherMatching = obj.optBoolean("useTeacherMatching", false)
                ))
            }
        }

        payload.optJSONArray(DATASET_PLANS)?.let { arr ->
            touchedDatasets += DATASET_PLANS
            dao.deleteAllPlans()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val dueDate = runCatching { java.time.LocalDate.parse(obj.optString("dueDate")) }.getOrNull() ?: continue
                val createdDate = runCatching { java.time.LocalDate.parse(obj.optString("createdDate")) }.getOrElse { LocalDate.now() }
                val completedDate = if (obj.has("completedDate")) runCatching { java.time.LocalDate.parse(obj.optString("completedDate")) }.getOrNull() else null
                dao.upsertPlan(PlanEntity(
                    lessonId = if (obj.has("lessonId")) obj.optLong("lessonId") else null,
                    subject = obj.optString("subject", ""),
                    teacher = obj.optString("teacher", null).takeIf { it?.isNotBlank() == true },
                    title = obj.optString("title", ""),
                    description = obj.optString("description", null).takeIf { it?.isNotBlank() == true },
                    dueDate = dueDate,
                    dueHour = obj.optInt("dueHour", 23),
                    dueMinute = obj.optInt("dueMinute", 59),
                    isCompleted = obj.optBoolean("isCompleted", false),
                    completedDate = completedDate,
                    createdDate = createdDate,
                    priority = obj.optInt("priority", 0),
                    useTeacherMatching = obj.optBoolean("useTeacherMatching", false)
                ))
            }
        }

        payload.optJSONArray(DATASET_CANCELLED_LESSONS)?.let { arr ->
            touchedDatasets += DATASET_CANCELLED_LESSONS
            dao.deleteAllCancelledLessons()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val date = runCatching { java.time.LocalDate.parse(obj.optString("date")) }.getOrNull() ?: continue
                val slotIndex = obj.optInt("slotIndex", -1)
                if (slotIndex < 0) continue
                dao.upsertCancelledLesson(CancelledLessonEntity(
                    date = date,
                    slotIndex = slotIndex,
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                ))
            }
        }

        val now = System.currentTimeMillis()
        val metadata = payload.optJSONObject("metadata")
        if (metadata != null && touchedDatasets.isNotEmpty()) {
            dao.upsertSyncDatasetMetaList(
                touchedDatasets.map { key ->
                    val entry = metadata.optJSONObject(key)
                    SyncDatasetMetaEntity(
                        datasetKey = key,
                        lastUpdatedAt = entry?.optLong("updatedAt", now) ?: now,
                        lastUpdatedByDeviceId = entry?.optString("updatedByDeviceId", "") ?: ""
                    )
                }
            )
        } else if (touchedDatasets.isNotEmpty()) {
            touchSyncDatasetMeta(*touchedDatasets.toTypedArray())
        }
    }

    suspend fun importAllData(json: String) {
        val root = org.json.JSONObject(json)

        val importVersion = if (root.has("version") && !root.isNull("version")) {
            root.optInt("version", -1)
        } else {
            1
        }

        when {
            importVersion == -1 -> throw IllegalArgumentException("versionフィールドの形式が不正です")
            importVersion < MIN_SUPPORTED_IMPORT_VERSION -> throw IllegalArgumentException("古すぎるJSON形式のためインポートできません")
            importVersion > CURRENT_EXPORT_VERSION -> throw IllegalArgumentException("このアプリでは新しすぎるJSON形式です")
        }

        val normalizedRoot = normalizeImportRoot(root, importVersion)

        val settingsEntity = normalizedRoot.optJSONObject("settings")?.let { s ->
            SettingsEntity(
                id = 1,
                termStart = LocalDate.parse(s.getString("termStart")),
                termEnd = LocalDate.parse(s.getString("termEnd")),
                enableLocalAi = s.optBoolean("enableLocalAi", false),
                hfToken = if (s.has("hfToken") && !s.isNull("hfToken")) s.getString("hfToken") else null,
                periodsPerDay = s.optInt("periodsPerDay", 4),
                periodDurationMin = s.optInt("periodDurationMin", 90),
                breakBetweenPeriodsMin = s.optInt("breakBetweenPeriodsMin", 10),
                lunchBreakMin = s.optInt("lunchBreakMin", 60),
                lunchAfterPeriod = s.optInt("lunchAfterPeriod", 2),
                firstPeriodStartHour = s.optInt("firstPeriodStartHour", 8),
                firstPeriodStartMinute = s.optInt("firstPeriodStartMinute", 40),
                useKosenMode = s.optBoolean("useKosenMode", true),
                useDrawerNavigation = s.optBoolean("useDrawerNavigation", false),
                addTasksToCalendar = s.optBoolean("addTasksToCalendar", false),
                showCurrentTimeMarker = s.optBoolean("showCurrentTimeMarker", false),
                arrivalHour = s.optInt("arrivalHour", -1),
                arrivalMinute = s.optInt("arrivalMinute", -1),
                departureHour = s.optInt("departureHour", -1),
                departureMinute = s.optInt("departureMinute", -1),
                unifyTaskPlanView = s.optBoolean("unifyTaskPlanView", false),
                enableTlsSync = s.optBoolean("enableTlsSync", false)
            )
        }

        val lessonEntities = mutableListOf<LessonEntity>()
        normalizedRoot.optJSONArray("lessons")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val dayOfWeek = obj.getInt("dayOfWeek")
                val slotIndex = obj.getInt("slotIndex")
                lessonEntities += LessonEntity(
                    id = 0,
                    dayOfWeek = dayOfWeek,
                    slotIndex = slotIndex,
                    mode = try {
                        LessonMode.valueOf(obj.optString("mode", "WEEKLY"))
                    } catch (_: Exception) { LessonMode.WEEKLY },
                    weeklySubject = obj.optString("weeklySubject", ""),
                    weeklyTeacher = obj.optString("weeklyTeacher", ""),
                    weeklyLocation = obj.optString("weeklyLocation", "").takeIf { it.isNotEmpty() },
                    aSubject = obj.optString("aSubject", ""),
                    aTeacher = obj.optString("aTeacher", ""),
                    aLocation = obj.optString("aLocation", "").takeIf { it.isNotEmpty() },
                    bSubject = obj.optString("bSubject", ""),
                    bTeacher = obj.optString("bTeacher", ""),
                    bLocation = obj.optString("bLocation", "").takeIf { it.isNotEmpty() }
                )
            }
        }

        val longBreakEntities = mutableListOf<LongBreakEntity>()
        normalizedRoot.optJSONArray("longBreaks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                longBreakEntities += LongBreakEntity(
                    id = 0,
                    name = obj.getString("name"),
                    startDate = LocalDate.parse(obj.getString("startDate")),
                    endDate = LocalDate.parse(obj.getString("endDate"))
                )
            }
        }

        val dayTypeEntities = mutableListOf<DayTypeEntity>()
        normalizedRoot.optJSONArray("dayTypes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                try {
                    dayTypeEntities += DayTypeEntity(
                        date = LocalDate.parse(obj.getString("date")),
                        dayType = DayType.valueOf(obj.getString("dayType")),
                        overrideLessonDayOfWeek = obj.optInt("overrideLessonDayOfWeek", -1).takeIf { it in 1..5 },
                        overrideLessonDayType = obj.optString("overrideLessonDayType", "")
                            .takeIf { it.isNotBlank() }
                            ?.let { DayType.valueOf(it) }
                    )
                } catch (_: Exception) { }
            }
        }

        val taskEntities = mutableListOf<TaskEntity>()
        normalizedRoot.optJSONArray("tasks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                try {
                    taskEntities += TaskEntity(
                        id = 0,
                        lessonId = if (obj.has("lessonId") && !obj.isNull("lessonId")) obj.getLong("lessonId") else null,
                        subject = obj.optString("subject", ""),
                        teacher = if (obj.has("teacher") && !obj.isNull("teacher")) obj.getString("teacher") else null,
                        title = obj.optString("title", ""),
                        description = if (obj.has("description") && !obj.isNull("description")) obj.getString("description") else null,
                        dueDate = LocalDate.parse(obj.getString("dueDate")),
                        dueHour = obj.optInt("dueHour", 23),
                        dueMinute = obj.optInt("dueMinute", 59),
                        isCompleted = obj.optBoolean("isCompleted", false),
                        completedDate = if (obj.has("completedDate") && !obj.isNull("completedDate")) LocalDate.parse(obj.getString("completedDate")) else null,
                        createdDate = LocalDate.parse(obj.getString("createdDate")),
                        priority = obj.optInt("priority", 0),
                        useTeacherMatching = obj.optBoolean("useTeacherMatching", false),
                        calendarEventId = if (obj.has("calendarEventId") && !obj.isNull("calendarEventId")) obj.getLong("calendarEventId") else null
                    )
                } catch (_: Exception) { }
            }
        }

        val planEntities = mutableListOf<PlanEntity>()
        normalizedRoot.optJSONArray("plans")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                try {
                    planEntities += PlanEntity(
                        id = 0,
                        lessonId = if (obj.has("lessonId") && !obj.isNull("lessonId")) obj.getLong("lessonId") else null,
                        subject = obj.optString("subject", ""),
                        teacher = if (obj.has("teacher") && !obj.isNull("teacher")) obj.getString("teacher") else null,
                        title = obj.optString("title", ""),
                        description = if (obj.has("description") && !obj.isNull("description")) obj.getString("description") else null,
                        dueDate = LocalDate.parse(obj.getString("dueDate")),
                        dueHour = obj.optInt("dueHour", 23),
                        dueMinute = obj.optInt("dueMinute", 59),
                        isCompleted = obj.optBoolean("isCompleted", false),
                        completedDate = if (obj.has("completedDate") && !obj.isNull("completedDate")) LocalDate.parse(obj.getString("completedDate")) else null,
                        createdDate = LocalDate.parse(obj.getString("createdDate")),
                        priority = obj.optInt("priority", 0),
                        useTeacherMatching = obj.optBoolean("useTeacherMatching", false),
                        calendarEventId = if (obj.has("calendarEventId") && !obj.isNull("calendarEventId")) obj.getLong("calendarEventId") else null
                    )
                } catch (_: Exception) { }
            }
        }

        val cancelledLessonEntities = mutableListOf<CancelledLessonEntity>()
        normalizedRoot.optJSONArray("cancelledLessons")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                try {
                    val date = LocalDate.parse(obj.getString("date"))
                    val slotIndex = obj.getInt("slotIndex")
                    if (slotIndex < 0) continue
                    cancelledLessonEntities += CancelledLessonEntity(
                        date = date,
                        slotIndex = slotIndex,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                } catch (_: Exception) { }
            }
        }

        db.withTransaction {
            settingsEntity?.let { dao.upsertSettings(it) }

            dao.deleteAllLessons()
            if (lessonEntities.isNotEmpty()) {
                lessonEntities.forEach { dao.upsertLesson(it) }
            }
            ensureLessonRows()

            dao.deleteAllLongBreaks()
            if (longBreakEntities.isNotEmpty()) {
                longBreakEntities.forEach { dao.upsertLongBreak(it) }
            }

            dao.deleteAllDayTypes()
            if (dayTypeEntities.isNotEmpty()) dao.upsertDayTypes(dayTypeEntities)

            dao.deleteAllTasks()
            if (taskEntities.isNotEmpty()) dao.upsertTasks(taskEntities)

            dao.deleteAllPlans()
            if (planEntities.isNotEmpty()) dao.upsertPlans(planEntities)

            dao.deleteAllCancelledLessons()
            if (cancelledLessonEntities.isNotEmpty()) {
                cancelledLessonEntities.forEach { dao.upsertCancelledLesson(it) }
            }

            syncDayTypes()
            touchSyncDatasetMeta(
                DATASET_SCHEDULE_SETTINGS,
                DATASET_LESSONS,
                DATASET_LONG_BREAKS,
                DATASET_DAY_TYPES,
                DATASET_TASKS,
                DATASET_PLANS,
                DATASET_CANCELLED_LESSONS
            )
        }
    }

    private fun normalizeImportRoot(root: org.json.JSONObject, importVersion: Int): org.json.JSONObject {
        val normalized = org.json.JSONObject(root.toString())
        if (importVersion == 1) {
            normalizeV1ToV2InPlace(normalized)
        }
        normalized.put("version", CURRENT_EXPORT_VERSION)
        normalized.put("schema", "nittc-scheduler")
        return normalized
    }

    private fun normalizeV1ToV2InPlace(root: org.json.JSONObject) {
        root.optJSONObject("settings")?.let { s ->
            if (!s.has("useDrawerNavigation") && s.has("useHamburgerNavigation")) {
                s.put("useDrawerNavigation", s.optBoolean("useHamburgerNavigation", false))
            }
            if (!s.has("addTasksToCalendar")) {
                s.put("addTasksToCalendar", false)
            }
            if (!s.has("showCurrentTimeMarker")) {
                s.put("showCurrentTimeMarker", false)
            }
            if (!s.has("unifyTaskPlanView")) {
                s.put("unifyTaskPlanView", false)
            }
        }

        root.optJSONArray("lessons")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (!obj.has("mode") && obj.has("alternationMode")) {
                    obj.put("mode", obj.optString("alternationMode", "WEEKLY"))
                }
            }
        }

        root.optJSONArray("tasks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue

                if (!obj.has("isCompleted") && obj.has("completed")) {
                    obj.put("isCompleted", obj.optBoolean("completed", false))
                }

                if (!obj.has("dueDate") && obj.has("deadlineDate")) {
                    obj.put("dueDate", obj.optString("deadlineDate", ""))
                }
                if (!obj.has("dueHour") && obj.has("deadlineHour")) {
                    obj.put("dueHour", obj.optInt("deadlineHour", 23))
                }
                if (!obj.has("dueMinute") && obj.has("deadlineMinute")) {
                    obj.put("dueMinute", obj.optInt("deadlineMinute", 59))
                }

                if (!obj.has("useTeacherMatching")) {
                    obj.put("useTeacherMatching", false)
                }
            }
        }

        if (!root.has("plans")) {
            root.put("plans", org.json.JSONArray())
        }
    }
}

private fun LocalDate.toDateRange(endDate: LocalDate): Sequence<LocalDate> {
    return generateSequence(this) { current ->
        current.plusDays(1).takeIf { !it.isAfter(endDate) }
    }
}
