package jp.linkserver.nittcsc.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.ChangedLessonEntity
import jp.linkserver.nittcsc.data.ExamDayScheduleEntity
import jp.linkserver.nittcsc.data.ExamLessonEntity
import jp.linkserver.nittcsc.data.HolidaySpecialLabel
import jp.linkserver.nittcsc.data.LessonNotificationExclusionEntity
import jp.linkserver.nittcsc.data.LessonDraft
import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import jp.linkserver.nittcsc.data.LessonNoteEntity
import jp.linkserver.nittcsc.data.LessonStartNotificationChipMode
import jp.linkserver.nittcsc.data.LongBreakEntity
import jp.linkserver.nittcsc.data.ResolvedLesson
import jp.linkserver.nittcsc.data.SchedulerRepository
import jp.linkserver.nittcsc.data.SettingsEntity
import jp.linkserver.nittcsc.data.PlanEntity
import jp.linkserver.nittcsc.data.SyncProfileEntity
import jp.linkserver.nittcsc.data.SyncRegisteredDeviceEntity
import jp.linkserver.nittcsc.data.TaskEntity
import jp.linkserver.nittcsc.logic.ExportRange
import jp.linkserver.nittcsc.logic.GeneratedLesson
import jp.linkserver.nittcsc.logic.JapaneseHolidayCalculator
import jp.linkserver.nittcsc.logic.PeriodLabelStyle
import jp.linkserver.nittcsc.sync.DirectConnectResult
import jp.linkserver.nittcsc.sync.DiscoveredSyncDevice
import jp.linkserver.nittcsc.sync.LocalSyncManager
import jp.linkserver.nittcsc.sync.NearbyEndpoint
import jp.linkserver.nittcsc.sync.NearbySyncManager
import jp.linkserver.nittcsc.sync.NearbyState
import jp.linkserver.nittcsc.sync.PreparedSyncSession
import jp.linkserver.nittcsc.sync.SyncChoice
import jp.linkserver.nittcsc.sync.SyncDiagnostics
import jp.linkserver.nittcsc.sync.SyncResult
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
    val dayTypeEntities: Map<LocalDate, DayTypeEntity>,
    val dayTypeMap: Map<LocalDate, DayType>,
    val longBreaks: List<LongBreakEntity>,
    val changedLessons: Map<Pair<LocalDate, Int>, ChangedLessonEntity>,
    val lessonNotificationExclusions: List<LessonNotificationExclusionEntity>,
    val lessons: Map<Pair<Int, Int>, LessonEntity>,
    val tasks: List<TaskEntity>,
    val incompleteTasks: List<TaskEntity>,
    val plans: List<PlanEntity>,
    val incompletePlans: List<PlanEntity>,
    val lessonNotes: List<LessonNoteEntity>,
    val examDaySchedules: Map<LocalDate, ExamDayScheduleEntity>,
    val examLessons: Map<Pair<LocalDate, Int>, ExamLessonEntity>
)

private data class SettingsBundle(
    val settings: SettingsEntity?,
    val dayTypes: List<DayTypeEntity>,
    val longBreaks: List<LongBreakEntity>,
    val changedLessons: List<ChangedLessonEntity>,
    val lessonNotificationExclusions: List<LessonNotificationExclusionEntity>
)

private data class ExamDataBundle(
    val daySchedules: List<ExamDayScheduleEntity>,
    val lessons: List<ExamLessonEntity>
)

data class SchedulerUiState(
    val settings: SettingsEntity? = null,
    val dayTypeEntities: Map<LocalDate, DayTypeEntity> = emptyMap(),
    val dayTypeMap: Map<LocalDate, DayType> = emptyMap(),
    val longBreaks: List<LongBreakEntity> = emptyList(),
    val changedLessons: Map<Pair<LocalDate, Int>, ChangedLessonEntity> = emptyMap(),
    val lessonNotificationExclusions: List<LessonNotificationExclusionEntity> = emptyList(),
    val lessons: Map<Pair<Int, Int>, LessonEntity> = emptyMap(),
    val tasks: List<TaskEntity> = emptyList(),
    val incompleteTasks: List<TaskEntity> = emptyList(),
    val plans: List<PlanEntity> = emptyList(),
    val incompletePlans: List<PlanEntity> = emptyList(),
    val lessonNotes: List<LessonNoteEntity> = emptyList(),
    val examDaySchedules: Map<LocalDate, ExamDayScheduleEntity> = emptyMap(),
    val examLessons: Map<Pair<LocalDate, Int>, ExamLessonEntity> = emptyMap(),
    val selectedDayOfWeek: Int = DayOfWeek.MONDAY.value,
    val selectedResultDate: LocalDate = LocalDate.now(),
    val initialized: Boolean = false,
    val syncProfile: SyncProfileEntity? = null,
    val registeredDevices: List<SyncRegisteredDeviceEntity> = emptyList(),
    val cancelledLessons: Set<Pair<LocalDate, Int>> = emptySet(),
    val syncDiagnostics: SyncDiagnostics = SyncDiagnostics()
)

class SchedulerViewModel(
    private val repository: SchedulerRepository,
    private val syncManager: LocalSyncManager? = null,
    val nearbySyncManager: NearbySyncManager? = null
) : ViewModel() {

    private val selectedDayOfWeek = MutableStateFlow(DayOfWeek.MONDAY.value)
    private val selectedResultDate = MutableStateFlow(LocalDate.now())
    private val initialized = MutableStateFlow(false)
    private val _snackbarMessages = MutableSharedFlow<String>()
    private val syncDiagnosticsFlow = syncManager?.diagnostics ?: kotlinx.coroutines.flow.MutableStateFlow(SyncDiagnostics())

    val snackbarMessages = _snackbarMessages.asSharedFlow()

    private fun launchRepositoryUpdate(
        successMessage: String? = null,
        action: suspend () -> Unit
    ) {
        viewModelScope.launch {
            action()
            successMessage?.let { _snackbarMessages.emit(it) }
        }
    }

    private val examDataFlow = combine(
        repository.examDaySchedulesFlow,
        repository.examLessonsFlow
    ) { schedules, lessons ->
        ExamDataBundle(schedules, lessons)
    }

    private val coreDataFlow: kotlinx.coroutines.flow.Flow<CoreDataState> = combine(
        combine(
            combine(
                repository.settingsFlow,
                repository.dayTypesFlow,
                repository.longBreaksFlow,
                repository.changedLessonsFlow,
                repository.lessonNotificationExclusionsFlow
            ) { settings: SettingsEntity?, dayTypes: List<DayTypeEntity>, longBreaks: List<LongBreakEntity>, changedLessons: List<ChangedLessonEntity>, exclusions: List<LessonNotificationExclusionEntity> ->
                SettingsBundle(settings, dayTypes, longBreaks, changedLessons, exclusions)
            },
            repository.lessonsFlow,
            repository.tasksFlow,
            repository.incompleteTasksFlow
        ) { baseData, lessons: List<LessonEntity>, tasks: List<TaskEntity>, incompleteTasks: List<TaskEntity> ->
            Pair(baseData, Triple(lessons, tasks, incompleteTasks))
        },
        repository.plansFlow,
        repository.incompletePlansFlow,
        repository.lessonNotesFlow,
        examDataFlow
    ) { base, plans: List<PlanEntity>, incompletePlans: List<PlanEntity>, lessonNotes: List<LessonNoteEntity>, examData: ExamDataBundle ->
        val (baseData, taskPart) = base
        val (lessons, tasks, incompleteTasks) = taskPart
        CoreDataState(
            settings = baseData.settings,
            dayTypeEntities = baseData.dayTypes.associateBy { it.date },
            dayTypeMap = baseData.dayTypes.associate { it.date to it.dayType },
            longBreaks = baseData.longBreaks,
            changedLessons = baseData.changedLessons.associateBy { it.date to it.slotIndex },
            lessonNotificationExclusions = baseData.lessonNotificationExclusions,
            lessons = lessons.associateBy { it.dayOfWeek to it.slotIndex },
            tasks = tasks,
            incompleteTasks = incompleteTasks,
            plans = plans,
            incompletePlans = incompletePlans,
            lessonNotes = lessonNotes,
            examDaySchedules = examData.daySchedules.associateBy { it.date },
            examLessons = examData.lessons.associateBy { it.date to it.slotIndex }
        )
    }

    val uiState: StateFlow<SchedulerUiState> = combine(
        combine(
            coreDataFlow,
            selectedDayOfWeek,
            selectedResultDate,
            initialized
        ) { core, dayOfWeek, resultDate, isInitialized ->
            SchedulerUiState(
                settings = core.settings,
                dayTypeEntities = core.dayTypeEntities,
                dayTypeMap = core.dayTypeMap,
                longBreaks = core.longBreaks,
                changedLessons = core.changedLessons,
                lessonNotificationExclusions = core.lessonNotificationExclusions,
                lessons = core.lessons,
                tasks = core.tasks,
                incompleteTasks = core.incompleteTasks,
                plans = core.plans,
                incompletePlans = core.incompletePlans,
                lessonNotes = core.lessonNotes,
                examDaySchedules = core.examDaySchedules,
                examLessons = core.examLessons,
                selectedDayOfWeek = dayOfWeek,
                selectedResultDate = resultDate,
                initialized = isInitialized
            )
        },
        repository.syncProfileFlow,
        repository.syncRegisteredDevicesFlow,
        repository.cancelledLessonsFlow,
        syncDiagnosticsFlow
    ) { base, syncProfile, registeredDevices, cancelledLessons, syncDiagnostics ->
        base.copy(
            syncProfile = syncProfile,
            registeredDevices = registeredDevices,
            cancelledLessons = cancelledLessons.map { it.date to it.slotIndex }.toSet(),
            syncDiagnostics = syncDiagnostics
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SchedulerUiState()
    )

    val nearbyState: kotlinx.coroutines.flow.StateFlow<NearbyState> =
        nearbySyncManager?.state ?: kotlinx.coroutines.flow.MutableStateFlow(NearbyState())

    init {
        viewModelScope.launch {
            repository.initialize()
            syncManager?.initialize()
            initialized.value = true

            // アプリ起動後、Nearbyアドバタイズをスタンバイ開始（相手から見つけられるようにする）
            val manager = nearbySyncManager ?: return@launch
            val profile = syncManager?.getProfile()
            manager.setLocalName(profile?.deviceName ?: android.os.Build.MODEL)
            manager.startStandbyAdvertising()
        }
        // Wi-Fi同期受信時にSnackbarで通知
        syncManager?.let { mgr ->
            viewModelScope.launch {
                mgr.incomingSyncEvents.collect { deviceName ->
                    val msg = if (deviceName.isNotBlank()) "✓ ${deviceName}と同期されました" else "✓ 同期されました"
                    _snackbarMessages.emit(msg)
                }
            }
        }
    }

    fun startNearbySearch() {
        val manager = nearbySyncManager ?: return
        viewModelScope.launch {
            val profile = syncManager?.getProfile()
            manager.setLocalName(profile?.deviceName ?: android.os.Build.MODEL)
            manager.conflictAutoNewerFirst = profile?.conflictAutoNewerFirst ?: false
            manager.startSearching()
        }
    }

    fun applyNearbyConflictResolutions(resolutions: Map<String, SyncChoice>) {
        nearbySyncManager?.applyConflictResolutions(resolutions)
    }

    fun connectToNearbyEndpoint(endpoint: NearbyEndpoint) {
        nearbySyncManager?.requestConnectionTo(endpoint)
    }

    fun acceptNearbyConnection() {
        nearbySyncManager?.acceptConnection()
    }

    fun rejectNearbyConnection() {
        nearbySyncManager?.rejectConnection()
    }

    fun stopNearbySync() {
        nearbySyncManager?.stopAll()
        // 画面を閉じた後もスタンバイ広告を再開して引き続き見つけられるようにする
        viewModelScope.launch {
            val profile = syncManager?.getProfile()
            nearbySyncManager?.setLocalName(profile?.deviceName ?: android.os.Build.MODEL)
            nearbySyncManager?.startStandbyAdvertising()
        }
    }

    /** パーミッション付与後などにスタンバイ広告を（再）開始する */
    fun retryStandbyAdvertising() {
        val manager = nearbySyncManager ?: return
        viewModelScope.launch {
            val profile = syncManager?.getProfile()
            manager.setLocalName(profile?.deviceName ?: android.os.Build.MODEL)
            manager.startStandbyAdvertising()
        }
    }

    fun runAutoSync() {
        val manager = syncManager ?: return
        viewModelScope.launch {
            manager.runAutoSync()
        }
    }

    fun saveSyncProfile(userNickname: String, deviceName: String, password: String, autoSyncEnabled: Boolean, conflictAutoNewerFirst: Boolean = false) {
        val manager = syncManager ?: return
        viewModelScope.launch {
            manager.updateProfile(userNickname, deviceName, password, autoSyncEnabled, conflictAutoNewerFirst)
        }
    }

    suspend fun discoverSyncDevices(): List<DiscoveredSyncDevice> {
        val manager = syncManager ?: return emptyList()
        return manager.discoverDevices()
    }

    suspend fun connectToSyncHost(host: String, port: Int): DirectConnectResult {
        val manager = syncManager ?: return DirectConnectResult(false, "同期機能が初期化されていません。")
        return manager.connectToHost(host, port)
    }

    suspend fun runSyncSelfConnectivityTest(): DirectConnectResult {
        val manager = syncManager ?: return DirectConnectResult(false, "同期機能が初期化されていません。")
        return manager.runSelfConnectivityTest()
    }

    suspend fun preparePasswordSync(device: DiscoveredSyncDevice, password: String): SyncResult {
        val manager = syncManager ?: return SyncResult(false, "同期機能が初期化されていません。")
        return manager.preparePasswordSync(device, password)
    }

    suspend fun prepareTrustedSync(deviceId: String): SyncResult {
        val manager = syncManager ?: return SyncResult(false, "同期機能が初期化されていません。")
        return manager.prepareTrustedSync(deviceId)
    }

    suspend fun applyPreparedSync(session: PreparedSyncSession, resolutions: Map<String, SyncChoice>): SyncResult {
        val manager = syncManager ?: return SyncResult(false, "同期機能が初期化されていません。")
        return manager.applyPreparedSync(session, resolutions)
    }

    suspend fun registerTrustedSyncDevice(device: DiscoveredSyncDevice, password: String): SyncResult {
        val manager = syncManager ?: return SyncResult(false, "同期機能が初期化されていません。")
        return manager.registerTrustedDevice(device, password)
    }

    suspend fun removeTrustedSyncDevice(deviceId: String): SyncResult {
        val manager = syncManager ?: return SyncResult(false, "同期機能が初期化されていません。")
        return manager.removeTrustedDevice(deviceId)
    }

    suspend fun getSyncListeningPort(): Int {
        val manager = syncManager ?: return 0
        return manager.getListeningPort()
    }

    fun formatSyncTimestamp(value: Long): String {
        val manager = syncManager ?: return if (value <= 0L) "未同期" else value.toString()
        return manager.formatTimestamp(value)
    }

    fun setLessonCancelled(date: LocalDate, slotIndex: Int, cancelled: Boolean) {
        viewModelScope.launch {
            repository.setLessonCancelled(date, slotIndex, cancelled)
        }
    }

    fun saveChangedLesson(date: LocalDate, slotIndex: Int, subject: String, teacher: String, location: String?) {
        viewModelScope.launch {
            repository.upsertChangedLesson(date, slotIndex, subject, teacher, location)
            _snackbarMessages.emit("授業変更を保存しました。")
        }
    }

    suspend fun saveChangedLessonDirect(date: LocalDate, slotIndex: Int, subject: String, teacher: String, location: String?) {
        repository.upsertChangedLesson(date, slotIndex, subject, teacher, location)
    }

    fun clearChangedLesson(date: LocalDate, slotIndex: Int) {
        viewModelScope.launch {
            repository.deleteChangedLesson(date, slotIndex)
            _snackbarMessages.emit("授業変更を解除しました。")
        }
    }

    suspend fun clearChangedLessonDirect(date: LocalDate, slotIndex: Int) {
        repository.deleteChangedLesson(date, slotIndex)
    }

    fun saveLessonNote(date: LocalDate, slotIndex: Int, text: String) {
        viewModelScope.launch {
            repository.upsertLessonNote(date, slotIndex, text)
            _snackbarMessages.emit("授業メモを保存しました。")
        }
    }

    fun deleteLessonNote(date: LocalDate, slotIndex: Int) {
        viewModelScope.launch {
            repository.deleteLessonNote(date, slotIndex)
            _snackbarMessages.emit("授業メモを削除しました。")
        }
    }

    fun isLessonCancelled(date: LocalDate, slotIndex: Int, cancelledLessons: Set<Pair<LocalDate, Int>>): Boolean {
        return cancelledLessons.contains(date to slotIndex)
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

    fun saveDayTypes(dates: List<LocalDate>, dayType: DayType) {
        viewModelScope.launch {
            repository.upsertDayTypes(dates, dayType)
        }
    }

    fun saveLessonOverride(date: LocalDate, dayOfWeek: Int, dayType: DayType) {
        viewModelScope.launch {
            repository.upsertLessonOverride(date, dayOfWeek, dayType)
        }
    }

    fun clearLessonOverride(date: LocalDate) {
        viewModelScope.launch {
            repository.clearLessonOverride(date)
        }
    }

    fun updateHolidaySpecialLabel(date: LocalDate, label: HolidaySpecialLabel?) {
        viewModelScope.launch {
            repository.updateHolidaySpecialLabel(date, label)
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
        launchRepositoryUpdate { repository.toggleLocalAi(enabled) }
    }

    fun toggleNaturalLanguageTaskAdd(enabled: Boolean) {
        launchRepositoryUpdate { repository.toggleNaturalLanguageTaskAdd(enabled) }
    }

    fun updateExamTimetableSettings(
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
        viewModelScope.launch {
            repository.updateExamTimetableSettings(
                periodsPerDay = periodsPerDay,
                periodDurationMin = periodDurationMin,
                breakBetweenPeriodsMin = breakBetweenPeriodsMin,
                lunchBreakMin = lunchBreakMin,
                lunchAfterPeriod = lunchAfterPeriod,
                firstPeriodStartHour = firstPeriodStartHour,
                firstPeriodStartMinute = firstPeriodStartMinute,
                arrivalHour = arrivalHour,
                arrivalMinute = arrivalMinute
            )
        }
    }

    fun saveExamDaySchedule(
        schedule: ExamDayScheduleEntity,
        lessons: List<ExamLessonEntity>
    ) {
        viewModelScope.launch {
            repository.saveExamDaySchedule(schedule, lessons)
            _snackbarMessages.emit("テスト時間割を保存しました。")
        }
    }

    fun saveExamPeriodSchedules(
        schedules: List<ExamDayScheduleEntity>,
        lessons: List<ExamLessonEntity>
    ) {
        viewModelScope.launch {
            repository.saveExamPeriodSchedules(schedules, lessons)
            _snackbarMessages.emit("テスト時間割を保存しました。")
        }
    }

    fun saveExamLessonMemo(date: LocalDate, slotIndex: Int, text: String) {
        viewModelScope.launch {
            repository.updateExamLessonMemo(date, slotIndex, text)
        }
    }

    fun deleteExamDaySchedule(date: LocalDate) {
        viewModelScope.launch {
            repository.deleteExamDaySchedule(date)
            _snackbarMessages.emit("テスト時間割を削除しました。")
        }
    }

    fun toggleDrawerNavigation(enabled: Boolean) {
        launchRepositoryUpdate { repository.toggleDrawerNavigation(enabled) }
    }

    fun toggleAddTasksToCalendar(enabled: Boolean) {
        launchRepositoryUpdate { repository.toggleAddTasksToCalendar(enabled) }
    }

    fun toggleSyncLessonsToCalendar(enabled: Boolean) {
        launchRepositoryUpdate { repository.toggleSyncLessonsToCalendar(enabled) }
    }

    fun enableSyncLessonsToCalendar(start: LocalDate, end: LocalDate) {
        launchRepositoryUpdate { repository.enableSyncLessonsToCalendar(start, end) }
    }

    fun updateLessonCalendarSyncRange(start: LocalDate, end: LocalDate) {
        launchRepositoryUpdate { repository.updateLessonCalendarSyncRange(start, end) }
    }

    fun toggleCurrentTimeMarker(enabled: Boolean) {
        launchRepositoryUpdate { repository.toggleCurrentTimeMarker(enabled) }
    }

    fun toggleUnifyTaskPlanView(enabled: Boolean) {
        launchRepositoryUpdate { repository.toggleUnifyTaskPlanView(enabled) }
    }

    fun toggleShowWeekdayOnDates(enabled: Boolean) {
        launchRepositoryUpdate { repository.toggleShowWeekdayOnDates(enabled) }
    }

    fun toggleTlsSync(enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleTlsSync(enabled)
            syncManager?.onTlsSettingChanged(enabled)
        }
    }

    fun toggleAdvancedTimeSettingsUi(enabled: Boolean) {
        launchRepositoryUpdate { repository.toggleAdvancedTimeSettingsUi(enabled) }
    }

    fun toggleLessonStartNotifications(enabled: Boolean) {
        launchRepositoryUpdate { repository.toggleLessonStartNotifications(enabled) }
    }

    fun updateLessonStartNotificationMinutesBefore(minutesBefore: Int) {
        launchRepositoryUpdate { repository.updateLessonStartNotificationMinutesBefore(minutesBefore) }
    }

    fun toggleLessonStartNotificationLiveUpdates(enabled: Boolean) {
        launchRepositoryUpdate { repository.toggleLessonStartNotificationLiveUpdates(enabled) }
    }

    fun toggleLessonStartNotificationProgressCountsDown(enabled: Boolean) {
        launchRepositoryUpdate { repository.toggleLessonStartNotificationProgressCountsDown(enabled) }
    }

    fun updateLessonStartNotificationLiveUpdateEarlyMinutes(minutes: Int) {
        launchRepositoryUpdate { repository.updateLessonStartNotificationLiveUpdateEarlyMinutes(minutes) }
    }

    fun updateLessonStartNotificationChipMode(mode: LessonStartNotificationChipMode) {
        launchRepositoryUpdate { repository.updateLessonStartNotificationChipMode(mode) }
    }

    fun addLessonNotificationExclusion(subject: String, teacher: String?, matchTeacher: Boolean) {
        viewModelScope.launch {
            repository.upsertLessonNotificationExclusion(subject, teacher, matchTeacher)
            _snackbarMessages.emit("授業前通知の例外を追加しました。")
        }
    }

    fun deleteLessonNotificationExclusion(exclusion: LessonNotificationExclusionEntity) {
        viewModelScope.launch {
            repository.deleteLessonNotificationExclusion(exclusion.id)
            _snackbarMessages.emit("授業前通知の例外を削除しました。")
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
        periodLabelStyle: PeriodLabelStyle,
        arrivalHour: Int,
        arrivalMinute: Int,
        departureHour: Int,
        departureMinute: Int
    ) {
        viewModelScope.launch {
            repository.updateScheduleSettings(
                periodsPerDay, periodDurationMin, breakBetweenPeriodsMin,
                lunchBreakMin, lunchAfterPeriod, firstPeriodStartHour, firstPeriodStartMinute, periodLabelStyle,
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
        periodLabelStyle: PeriodLabelStyle,
        arrivalHour: Int,
        arrivalMinute: Int,
        departureHour: Int,
        departureMinute: Int
    ) {
        viewModelScope.launch {
            repository.updateScheduleSettings(
                periodsPerDay, periodDurationMin, breakBetweenPeriodsMin,
                lunchBreakMin, lunchAfterPeriod, firstPeriodStartHour, firstPeriodStartMinute, periodLabelStyle,
                arrivalHour, arrivalMinute, departureHour, departureMinute
            )
        }
    }

    fun updateHfToken(token: String?) {
        launchRepositoryUpdate { repository.updateHfToken(token) }
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
        dayTypeMap: Map<LocalDate, DayType>,
        dayTypeEntities: Map<LocalDate, DayTypeEntity> = emptyMap(),
        changedLessons: Map<Pair<LocalDate, Int>, ChangedLessonEntity> = emptyMap()
    ): ResolvedLesson? {
        val baseLesson = resolveBaseLessonForDate(date, slotIndex, lessons, dayTypeMap, dayTypeEntities) ?: return null
        val changedLesson = changedLessons[date to slotIndex] ?: return baseLesson
        return ResolvedLesson(changedLesson.subject, changedLesson.teacher, changedLesson.location)
    }

    fun resolveBaseLessonForDate(
        date: LocalDate,
        slotIndex: Int,
        lessons: Map<Pair<Int, Int>, LessonEntity>,
        dayTypeMap: Map<LocalDate, DayType>,
        dayTypeEntities: Map<LocalDate, DayTypeEntity> = emptyMap()
    ): ResolvedLesson? {
        if (date.dayOfWeek.value !in 1..5) return null

        val dayTypeEntity = dayTypeEntities[date]
        val dayType = dayTypeEntity?.dayType ?: dayTypeMap[date] ?: defaultDayType(date)
        if (dayType == DayType.HOLIDAY) return null

        val lessonDayOfWeek = dayTypeEntity?.overrideLessonDayOfWeek ?: date.dayOfWeek.value
        val lessonDayType = dayTypeEntity?.overrideLessonDayType ?: dayType
        val lesson = lessons[lessonDayOfWeek to slotIndex] ?: return null

        return when (lesson.mode) {
            LessonMode.WEEKLY -> {
                if (lesson.weeklySubject.isBlank()) null
                else ResolvedLesson(lesson.weeklySubject, lesson.weeklyTeacher, lesson.weeklyLocation)
            }

            LessonMode.ALTERNATING -> {
                when (lessonDayType) {
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
        launchRepositoryUpdate("課題を保存しました。") { repository.upsertTask(task) }
    }

    suspend fun saveTaskDirect(task: TaskEntity): TaskEntity {
        return repository.upsertTask(task)
    }

    fun saveTasksSilently(tasks: List<TaskEntity>) {
        launchRepositoryUpdate { repository.upsertTasks(tasks) }
    }

    fun deleteTask(task: TaskEntity) {
        launchRepositoryUpdate("課題を削除しました。") { repository.deleteTask(task.id) }
    }

    fun markTaskAsComplete(task: TaskEntity) {
        launchRepositoryUpdate("課題を完了しました。") { repository.markTaskAsComplete(task.id) }
    }

    fun markTaskAsIncomplete(task: TaskEntity) {
        launchRepositoryUpdate("課題を未完了に戻しました。") { repository.markTaskAsIncomplete(task.id) }
    }

    suspend fun getTasksForDate(date: LocalDate): List<TaskEntity> {
        return repository.getTasksByDate(date)
    }

    suspend fun getTasksInRange(fromDate: LocalDate, toDate: LocalDate): List<TaskEntity> {
        return repository.getTasksInRange(fromDate, toDate)
    }

    fun savePlan(plan: PlanEntity) {
        launchRepositoryUpdate("予定を保存しました。") { repository.upsertPlan(plan) }
    }

    suspend fun savePlanDirect(plan: PlanEntity): PlanEntity {
        return repository.upsertPlan(plan)
    }

    fun savePlansSilently(plans: List<PlanEntity>) {
        launchRepositoryUpdate { repository.upsertPlans(plans) }
    }

    fun deletePlan(plan: PlanEntity) {
        launchRepositoryUpdate("予定を削除しました。") { repository.deletePlan(plan.id) }
    }

    fun markPlanAsComplete(plan: PlanEntity) {
        launchRepositoryUpdate("予定を完了しました。") { repository.markPlanAsComplete(plan.id) }
    }

    fun markPlanAsIncomplete(plan: PlanEntity) {
        launchRepositoryUpdate("予定を未完了に戻しました。") { repository.markPlanAsIncomplete(plan.id) }
    }

    suspend fun getPlansForDate(date: LocalDate): List<PlanEntity> {
        return repository.getPlansByDate(date)
    }

    suspend fun getPlansInRange(fromDate: LocalDate, toDate: LocalDate): List<PlanEntity> {
        return repository.getPlansInRange(fromDate, toDate)
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
        fromDate: LocalDate = LocalDate.now(),
        fromTime: LocalTime = LocalTime.now()
    ): Pair<LocalDate, LocalTime>? {
        return repository.calculateNextLessonDateTime(subject, teacher, useTeacherMatching, fromDate, fromTime)
    }

    suspend fun calculatePreviousLessonDateTime(
        subject: String,
        teacher: String?,
        useTeacherMatching: Boolean,
        fromDate: LocalDate = LocalDate.now(),
        currentTime: LocalTime = LocalTime.now()
    ): Pair<LocalDate, LocalTime>? {
        return repository.calculatePreviousLessonDateTime(subject, teacher, useTeacherMatching, fromDate, currentTime)
    }

    suspend fun calculateNextLessonDateTimeSkipCurrent(
        subject: String,
        teacher: String?,
        useTeacherMatching: Boolean,
        fromDate: LocalDate = LocalDate.now(),
        currentTime: LocalTime = LocalTime.now()
    ): Pair<LocalDate, LocalTime>? {
        return repository.calculateNextLessonDateTimeSkipCurrent(subject, teacher, useTeacherMatching, fromDate, currentTime)
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
    private val repository: SchedulerRepository,
    private val syncManager: LocalSyncManager? = null,
    private val nearbySyncManager: NearbySyncManager? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SchedulerViewModel::class.java)) {
            return SchedulerViewModel(repository, syncManager, nearbySyncManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
