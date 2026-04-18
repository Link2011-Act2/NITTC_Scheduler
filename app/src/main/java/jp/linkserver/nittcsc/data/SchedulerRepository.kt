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
        private const val CURRENT_EXPORT_VERSION = 2
        private const val MIN_SUPPORTED_IMPORT_VERSION = 1
    }

    val settingsFlow: Flow<SettingsEntity?> = dao.observeSettings()
    val dayTypesFlow: Flow<List<DayTypeEntity>> = dao.observeDayTypes()
    val longBreaksFlow: Flow<List<LongBreakEntity>> = dao.observeLongBreaks()
    val lessonsFlow: Flow<List<LessonEntity>> = dao.observeLessons()
    val tasksFlow: Flow<List<TaskEntity>> = dao.observeTasks()
    val incompleteTasksFlow: Flow<List<TaskEntity>> = dao.observeIncompleteTasks()

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

    suspend fun updateHfToken(token: String?) {
        val current = dao.getSettings() ?: return
        dao.upsertSettings(current.copy(hfToken = token))
    }

    suspend fun toggleDayType(date: LocalDate) {
        val current = dao.getDayType(date)?.dayType ?: DayType.A
        val next = when (current) {
            DayType.A -> DayType.B
            DayType.B -> DayType.HOLIDAY
            DayType.HOLIDAY -> DayType.A
        }
        dao.upsertDayType(DayTypeEntity(date = date, dayType = next))
    }

    suspend fun upsertDayType(date: LocalDate, dayType: DayType) {
        dao.upsertDayType(DayTypeEntity(date = date, dayType = dayType))
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
    }

    suspend fun deleteLongBreak(longBreak: LongBreakEntity) {
        dao.deleteLongBreak(longBreak)
        syncDayTypes()
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
    }

    suspend fun syncDayTypes() {
        val settings = dao.getSettings() ?: return
        val longBreaks = dao.getLongBreaksOnce()
        val breakRanges = longBreaks.map { it.startDate..it.endDate }
        val existing = dao.getDayTypesOnce().associateBy { it.date }

        val rebuilt = mutableListOf<DayTypeEntity>()
        for (date in settings.termStart.toDateRange(settings.termEnd)) {
            val autoHoliday = isAutoHoliday(date, breakRanges)
            val manual = existing[date]?.dayType
            val resolved = when {
                autoHoliday -> DayType.HOLIDAY
                manual != null -> manual
                else -> DayType.A
            }
            rebuilt += DayTypeEntity(date = date, dayType = resolved)
        }

        dao.upsertDayTypes(rebuilt)
        dao.deleteDayTypesOutsideRange(settings.termStart, settings.termEnd)
    }

    suspend fun generateLessons(range: ExportRange, today: LocalDate = LocalDate.now()): List<GeneratedLesson> {
        syncDayTypes()

        val settings = dao.getSettings() ?: return emptyList()
        val dayTypeMap = dao.getDayTypesOnce().associate { it.date to it.dayType }
        val lessons = dao.getLessonsOnce().associateBy { it.dayOfWeek to it.slotIndex }

        val dateBounds = when (range) {
            ExportRange.ALL_TERM -> settings.termStart..settings.termEnd
            ExportRange.THIS_WEEK -> {
                val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
                maxOf(weekStart, settings.termStart)..minOf(weekEnd, settings.termEnd)
            }
        }

        if (dateBounds.start > dateBounds.endInclusive) return emptyList()

        return buildList {
            for (date in dateBounds.start.toDateRange(dateBounds.endInclusive)) {
                if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) continue

                val dayType = dayTypeMap[date] ?: DayType.A
                if (dayType == DayType.HOLIDAY) continue

                val dayKey = date.dayOfWeek.value
                val slots = generateClassSlots(
                    settings.periodsPerDay, settings.periodDurationMin, settings.breakBetweenPeriodsMin,
                    settings.lunchBreakMin, settings.firstPeriodStartHour, settings.firstPeriodStartMinute,
                    settings.useKosenMode, settings.lunchAfterPeriod
                )
                for (slot in slots) {
                    val lesson = lessons[dayKey to slot.index] ?: continue
                    val resolved = resolveLesson(dayType, lesson) ?: continue
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

    // Task管理メソッド

    suspend fun upsertTask(task: TaskEntity) {
        dao.upsertTask(task)
    }

    suspend fun upsertTasks(tasks: List<TaskEntity>) {
        if (tasks.isEmpty()) return
        dao.upsertTasks(tasks)
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
    }

    suspend fun deleteTasksByLessonId(lessonId: Long) {
        dao.deleteTasksByLessonId(lessonId)
    }

    suspend fun markTaskAsComplete(taskId: Long, completedDate: LocalDate = LocalDate.now()) {
        val task = dao.getTaskById(taskId) ?: return
        dao.upsertTask(task.copy(isCompleted = true, completedDate = completedDate))
    }

    suspend fun markTaskAsIncomplete(taskId: Long) {
        val task = dao.getTaskById(taskId) ?: return
        dao.upsertTask(task.copy(isCompleted = false, completedDate = null))
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
        fromDate: LocalDate = LocalDate.now()
    ): Pair<LocalDate, LocalTime>? {
        val settings = dao.getSettings() ?: return null

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
        
        for (date in fromDate.toDateRange(settings.termEnd)) {
            // 平日かどうかチェック
            if (date.dayOfWeek.value !in 1..5) continue
            
            // 休日かどうかチェック
            val dayType = dao.getDayType(date)?.dayType ?: DayType.A
            if (dayType == DayType.HOLIDAY) continue
            
            // その日のすべてのスロットを検索
            for (slot in slots) {
                val slotIndex = slot.index
                val lesson = dao.getLesson(date.dayOfWeek.value, slotIndex) ?: continue
                val resolved = resolveLesson(dayType, lesson) ?: continue
                
                // useTeacherMatching フラグに基づいてマッチング
                val matches = if (useTeacherMatching) {
                    resolved.subject == subject && resolved.teacher == (teacher ?: "")
                } else {
                    resolved.subject == subject
                }
                
                if (matches) {
                    return date to slot.start
                }
            }
        }
        
        return null
    }

    suspend fun exportAllData(): String {
        val settings = dao.getSettings()
        val lessons = dao.getLessonsOnce()
        val longBreaks = dao.getLongBreaksOnce()
        val dayTypes = dao.getDayTypesOnce()
        val tasks = dao.getTasksOnce()

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
                s.put("arrivalHour", settings.arrivalHour)
                s.put("arrivalMinute", settings.arrivalMinute)
                s.put("departureHour", settings.departureHour)
                s.put("departureMinute", settings.departureMinute)
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

        return root.toString(2)
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
                lunchBreakMin = s.optInt("lunchBreakMin", 50),
                lunchAfterPeriod = s.optInt("lunchAfterPeriod", 2),
                firstPeriodStartHour = s.optInt("firstPeriodStartHour", 8),
                firstPeriodStartMinute = s.optInt("firstPeriodStartMinute", 40),
                useKosenMode = s.optBoolean("useKosenMode", true),
                useDrawerNavigation = s.optBoolean("useDrawerNavigation", false),
                addTasksToCalendar = s.optBoolean("addTasksToCalendar", false),
                arrivalHour = s.optInt("arrivalHour", -1),
                arrivalMinute = s.optInt("arrivalMinute", -1),
                departureHour = s.optInt("departureHour", -1),
                departureMinute = s.optInt("departureMinute", -1)
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
                        dayType = DayType.valueOf(obj.getString("dayType"))
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

            syncDayTypes()
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
    }
}

private fun LocalDate.toDateRange(endDate: LocalDate): Sequence<LocalDate> {
    return generateSequence(this) { current ->
        current.plusDays(1).takeIf { !it.isAfter(endDate) }
    }
}
