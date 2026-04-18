package jp.linkserver.nittcsc.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.LessonDraft
import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import jp.linkserver.nittcsc.data.LongBreakEntity
import jp.linkserver.nittcsc.data.ResolvedLesson
import jp.linkserver.nittcsc.data.SchedulerRepository
import jp.linkserver.nittcsc.data.SettingsEntity
import jp.linkserver.nittcsc.data.TaskEntity
import jp.linkserver.nittcsc.logic.ExportRange
import jp.linkserver.nittcsc.logic.GeneratedLesson
import jp.linkserver.nittcsc.logic.JapaneseHolidayCalculator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

private data class CoreDataState(
    val settings: SettingsEntity?,
    val dayTypeMap: Map<LocalDate, DayType>,
    val longBreaks: List<LongBreakEntity>,
    val lessons: Map<Pair<Int, Int>, LessonEntity>,
    val tasks: List<TaskEntity>,
    val incompleteTasks: List<TaskEntity>
)

data class SchedulerUiState(
    val settings: SettingsEntity? = null,
    val dayTypeMap: Map<LocalDate, DayType> = emptyMap(),
    val longBreaks: List<LongBreakEntity> = emptyList(),
    val lessons: Map<Pair<Int, Int>, LessonEntity> = emptyMap(),
    val tasks: List<TaskEntity> = emptyList(),
    val incompleteTasks: List<TaskEntity> = emptyList(),
    val selectedDayOfWeek: Int = DayOfWeek.MONDAY.value,
    val selectedResultDate: LocalDate = LocalDate.now(),
    val initialized: Boolean = false
)

class SchedulerViewModel(
    private val repository: SchedulerRepository
) : ViewModel() {

    private val selectedDayOfWeek = MutableStateFlow(DayOfWeek.MONDAY.value)
    private val selectedResultDate = MutableStateFlow(LocalDate.now())
    private val initialized = MutableStateFlow(false)
    private val _snackbarMessages = MutableSharedFlow<String>()

    val snackbarMessages = _snackbarMessages.asSharedFlow()

    private val coreDataFlow: kotlinx.coroutines.flow.Flow<CoreDataState> = combine(
        combine(
            repository.settingsFlow,
            repository.dayTypesFlow,
            repository.longBreaksFlow
        ) { settings: SettingsEntity?, dayTypes: List<DayTypeEntity>, longBreaks: List<LongBreakEntity> ->
            Triple(settings, dayTypes, longBreaks)
        },
        repository.lessonsFlow,
        repository.tasksFlow,
        repository.incompleteTasksFlow
    ) { triple: Triple<SettingsEntity?, List<DayTypeEntity>, List<LongBreakEntity>>, lessons: List<LessonEntity>, tasks: List<TaskEntity>, incompleteTasks: List<TaskEntity> ->
        val (settings, dayTypes, longBreaks) = triple
        CoreDataState(
            settings = settings,
            dayTypeMap = dayTypes.associate { it.date to it.dayType },
            longBreaks = longBreaks,
            lessons = lessons.associateBy { it.dayOfWeek to it.slotIndex },
            tasks = tasks,
            incompleteTasks = incompleteTasks
        )
    }

    val uiState: StateFlow<SchedulerUiState> = combine(
        coreDataFlow,
        selectedDayOfWeek,
        selectedResultDate,
        initialized
    ) { core, dayOfWeek, resultDate, isInitialized ->
        SchedulerUiState(
            settings = core.settings,
            dayTypeMap = core.dayTypeMap,
            longBreaks = core.longBreaks,
            lessons = core.lessons,
            tasks = core.tasks,
            incompleteTasks = core.incompleteTasks,
            selectedDayOfWeek = dayOfWeek,
            selectedResultDate = resultDate,
            initialized = isInitialized
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SchedulerUiState()
    )

    init {
        viewModelScope.launch {
            repository.initialize()
            initialized.value = true
        }
    }

    fun selectDayOfWeek(dayOfWeek: Int) {
        selectedDayOfWeek.value = dayOfWeek
    }

    fun shiftResultDate(days: Long) {
        selectedResultDate.value = selectedResultDate.value.plusDays(days)
    }

    fun setResultDate(date: LocalDate) {
        selectedResultDate.value = date
    }

    fun toggleDayType(date: LocalDate) {
        viewModelScope.launch {
            repository.toggleDayType(date)
        }
    }

    fun saveDayType(date: LocalDate, dayType: DayType) {
        viewModelScope.launch {
            repository.upsertDayType(date, dayType)
        }
    }

    fun resetFiscalYear() {
        viewModelScope.launch {
            repository.resetToCurrentFiscalYear()
            _snackbarMessages.emit("期間を今年度にリセットしました。")
        }
    }

    fun updateTerm(startDate: LocalDate, endDate: LocalDate) {
        viewModelScope.launch {
            repository.updateTerm(startDate, endDate)
            _snackbarMessages.emit("期間を更新しました。")
        }
    }

    fun toggleLocalAi(enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleLocalAi(enabled)
        }
    }

    fun toggleDrawerNavigation(enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleDrawerNavigation(enabled)
        }
    }

    fun toggleAddTasksToCalendar(enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleAddTasksToCalendar(enabled)
        }
    }

    fun updateScheduleSettings(
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
        viewModelScope.launch {
            repository.updateScheduleSettings(
                periodsPerDay, periodDurationMin, breakBetweenPeriodsMin,
                lunchBreakMin, lunchAfterPeriod, firstPeriodStartHour, firstPeriodStartMinute, useKosenMode,
                arrivalHour, arrivalMinute, departureHour, departureMinute
            )
            _snackbarMessages.emit("時間割設定を保存しました。")
        }
    }

    fun updateScheduleSettingsSilently(
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
        viewModelScope.launch {
            repository.updateScheduleSettings(
                periodsPerDay, periodDurationMin, breakBetweenPeriodsMin,
                lunchBreakMin, lunchAfterPeriod, firstPeriodStartHour, firstPeriodStartMinute, useKosenMode,
                arrivalHour, arrivalMinute, departureHour, departureMinute
            )
        }
    }

    fun updateHfToken(token: String?) {
        viewModelScope.launch {
            repository.updateHfToken(token)
        }
    }

    fun saveLongBreak(id: Long?, name: String, startDate: LocalDate, endDate: LocalDate) {
        viewModelScope.launch {
            if (name.isBlank()) {
                _snackbarMessages.emit("長期休みの名前を入力してください。")
                return@launch
            }
            repository.upsertLongBreak(id, name, startDate, endDate)
            _snackbarMessages.emit("長期休みを保存しました。")
        }
    }

    fun deleteLongBreak(longBreak: LongBreakEntity) {
        viewModelScope.launch {
            repository.deleteLongBreak(longBreak)
            _snackbarMessages.emit("長期休みを削除しました。")
        }
    }

    fun saveLesson(dayOfWeek: Int, slotIndex: Int, draft: LessonDraft) {
        viewModelScope.launch {
            repository.upsertLesson(dayOfWeek, slotIndex, draft)
            _snackbarMessages.emit("${dayLabel(dayOfWeek)} ${slotIndex + 1}枠目を保存しました。")
        }
    }

    fun saveLessonWithoutNotification(dayOfWeek: Int, slotIndex: Int, draft: LessonDraft) {
        viewModelScope.launch {
            repository.upsertLesson(dayOfWeek, slotIndex, draft)
        }
    }

    suspend fun generateLessons(range: ExportRange): List<GeneratedLesson> {
        return repository.generateLessons(range)
    }

    fun resolveLessonForDate(
        date: LocalDate,
        slotIndex: Int,
        lessons: Map<Pair<Int, Int>, LessonEntity>,
        dayTypeMap: Map<LocalDate, DayType>
    ): ResolvedLesson? {
        if (date.dayOfWeek.value !in 1..5) return null

        val dayType = dayTypeMap[date] ?: defaultDayType(date)
        if (dayType == DayType.HOLIDAY) return null

        val lesson = lessons[date.dayOfWeek.value to slotIndex] ?: return null

        return when (lesson.mode) {
            LessonMode.WEEKLY -> {
                if (lesson.weeklySubject.isBlank()) null
                else ResolvedLesson(lesson.weeklySubject, lesson.weeklyTeacher, lesson.weeklyLocation)
            }

            LessonMode.ALTERNATING -> {
                when (dayType) {
                    DayType.A -> if (lesson.aSubject.isBlank()) null else ResolvedLesson(lesson.aSubject, lesson.aTeacher, lesson.aLocation)
                    DayType.B -> if (lesson.bSubject.isBlank()) null else ResolvedLesson(lesson.bSubject, lesson.bTeacher, lesson.bLocation)
                    DayType.HOLIDAY -> null
                }
            }
        }
    }

    fun dayTypeForDate(date: LocalDate, map: Map<LocalDate, DayType>): DayType {
        return map[date] ?: defaultDayType(date)
    }

    // Task 管理メソッド

    fun saveTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.upsertTask(task)
            _snackbarMessages.emit("課題を保存しました。")
        }
    }

    fun saveTasksSilently(tasks: List<TaskEntity>) {
        viewModelScope.launch {
            repository.upsertTasks(tasks)
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.deleteTask(task.id)
            _snackbarMessages.emit("課題を削除しました。")
        }
    }

    fun markTaskAsComplete(task: TaskEntity) {
        viewModelScope.launch {
            repository.markTaskAsComplete(task.id)
            _snackbarMessages.emit("課題を完了しました。")
        }
    }

    fun markTaskAsIncomplete(task: TaskEntity) {
        viewModelScope.launch {
            repository.markTaskAsIncomplete(task.id)
            _snackbarMessages.emit("課題を未完了に戻しました。")
        }
    }

    suspend fun getTasksForDate(date: LocalDate): List<TaskEntity> {
        return repository.getTasksByDate(date)
    }

    suspend fun getTasksInRange(fromDate: LocalDate, toDate: LocalDate): List<TaskEntity> {
        return repository.getTasksInRange(fromDate, toDate)
    }

    suspend fun exportAllData(): String = repository.exportAllData()

    fun importAllData(json: String) {
        viewModelScope.launch {
            try {
                repository.importAllData(json)
                _snackbarMessages.emit("インポートが完了しました。")
            } catch (e: IllegalArgumentException) {
                _snackbarMessages.emit(e.message ?: "インポートに失敗しました。ファイルを確認してください。")
            } catch (e: Exception) {
                _snackbarMessages.emit("インポートに失敗しました。ファイルを確認してください。")
            }
        }
    }

    suspend fun calculateNextLessonDate(
        subject: String,
        teacher: String?,
        useTeacherMatching: Boolean,
        fromDate: LocalDate = LocalDate.now()
    ): LocalDate? {
        return repository.calculateNextLessonDate(subject, teacher, useTeacherMatching, fromDate)
    }

    suspend fun calculateNextLessonDateTime(
        subject: String,
        teacher: String?,
        useTeacherMatching: Boolean,
        fromDate: LocalDate = LocalDate.now()
    ): Pair<LocalDate, LocalTime>? {
        return repository.calculateNextLessonDateTime(subject, teacher, useTeacherMatching, fromDate)
    }

    private fun defaultDayType(date: LocalDate): DayType {
        val weekend = date.dayOfWeek.value >= DayOfWeek.SATURDAY.value
        return if (weekend || JapaneseHolidayCalculator.isHoliday(date)) DayType.HOLIDAY else DayType.A
    }

    private fun dayLabel(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            1 -> "月"
            2 -> "火"
            3 -> "水"
            4 -> "木"
            5 -> "金"
            else -> ""
        }
    }
}

class SchedulerViewModelFactory(
    private val repository: SchedulerRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SchedulerViewModel::class.java)) {
            return SchedulerViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
