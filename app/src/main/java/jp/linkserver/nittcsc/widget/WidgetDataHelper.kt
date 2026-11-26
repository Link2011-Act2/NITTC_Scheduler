package jp.linkserver.nittcsc.widget

import jp.linkserver.nittcsc.data.getEffectiveDayTypes
import jp.linkserver.nittcsc.logic.sameSubject

import android.content.Context
import jp.linkserver.nittcsc.data.AppDatabase
import jp.linkserver.nittcsc.data.ChangedLessonEntity
import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.ExamDayScheduleEntity
import jp.linkserver.nittcsc.data.ExamLessonEntity
import jp.linkserver.nittcsc.data.hasEnteredContent
import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import jp.linkserver.nittcsc.data.lessonKey
import jp.linkserver.nittcsc.data.PlanEntity
import jp.linkserver.nittcsc.data.ResolvedLesson
import jp.linkserver.nittcsc.data.SettingsEntity
import jp.linkserver.nittcsc.data.SchedulerUsageMode
import jp.linkserver.nittcsc.data.TaskEntity
import jp.linkserver.nittcsc.logic.CLASS_SLOTS
import jp.linkserver.nittcsc.logic.forTimetable
import jp.linkserver.nittcsc.logic.ClassSlot
import jp.linkserver.nittcsc.logic.JapaneseHolidayCalculator
import jp.linkserver.nittcsc.logic.LessonKey
import jp.linkserver.nittcsc.logic.PeriodLabelStyle
import jp.linkserver.nittcsc.logic.academicYearForDate
import jp.linkserver.nittcsc.logic.applyChangedLesson
import jp.linkserver.nittcsc.logic.formatExamPeriodLabel
import jp.linkserver.nittcsc.logic.forExamTimetable
import jp.linkserver.nittcsc.logic.generateClassSlots
import jp.linkserver.nittcsc.logic.timetableTermForDate
import jp.linkserver.nittcsc.sktjf.SktjfExtraLessonResolver
import jp.linkserver.nittcsc.sktjf.SktjfTimedSchedule
import jp.linkserver.nittcsc.sktjf.SktjfTimedSlotResult
import jp.linkserver.nittcsc.sktjf.SktjfWeekSelection
import jp.linkserver.nittcsc.sktjf.SktjfCycle
import jp.linkserver.nittcsc.sktjf.resolvedLesson
import jp.linkserver.nittcsc.sktjf.SktjfElectiveSchedule
import jp.linkserver.nittcsc.sktjf.SktjfElectiveSlotResult
import jp.linkserver.nittcsc.sktjf.selectedLesson
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class WidgetData(
    val today: LocalDate,
    val settings: SettingsEntity?,
    val classSlots: List<ClassSlot>,
    val dayType: DayType,
    val dayTypeEntities: Map<LocalDate, DayTypeEntity>,
    val dayTypeMap: Map<LocalDate, DayType>,
    val cancelledLessons: Set<Pair<LocalDate, Int>>,
    val changedLessons: Map<Pair<LocalDate, Int>, ChangedLessonEntity>,
    val lessons: Map<LessonKey, LessonEntity>,
    val examDaySchedules: Map<LocalDate, ExamDayScheduleEntity>,
    val examLessons: Map<Pair<LocalDate, Int>, ExamLessonEntity>,
    val incompleteTasks: List<TaskEntity>,
    val incompletePlans: List<PlanEntity>,
    val extraResolver: SktjfExtraLessonResolver? = null,
    val timedSchedule: SktjfTimedSchedule? = null,
    val electiveSchedule: SktjfElectiveSchedule? = null
)

data class NextLessonWidgetEntry(
    val date: LocalDate,
    val slot: ClassSlot,
    val lesson: ResolvedLesson,
    val tasks: List<TaskEntity>,
    val plans: List<PlanEntity>,
    val displayTitle: String = lesson.subject
)

object WidgetDataHelper {

    suspend fun load(context: Context): WidgetData {
        val db = AppDatabase.getInstance(context)
        val dao = db.schedulerDao()
        val today = LocalDate.now()

        val settings = dao.getSettings()
        val mode = dao.getUsageMode()?.mode?.let { value -> SchedulerUsageMode.entries.firstOrNull { it.name == value } }
        val profiles = dao.getNittcProfilesOnce()
        val extraLessons = dao.getSktjfExtraLessons()
        val groups = dao.getSktjfSelectionGroups()
        val states = dao.getSktjfSourceStates()
        val extraResolver = runCatching { SktjfExtraLessonResolver.create(mode, profiles, extraLessons, groups, states) }.getOrNull()
        val electiveSchedule = runCatching { SktjfElectiveSchedule.create(mode, profiles, groups, extraLessons, states) }.getOrNull()
        val dayTypes = dao.getEffectiveDayTypes().map { it.forTimetable(settings?.enableAbTimetable != false) }
        val dayTypeEntities = dayTypes.associateBy { it.date }
        val dayTypeMap = dayTypes.associate { it.date to it.dayType }
        val cancelledLessons = dao.getCancelledLessonsOnce().map { it.date to it.slotIndex }.toSet()
        val changedLessons = dao.getChangedLessonsOnce().associateBy { it.date to it.slotIndex }
        val storedLessons = dao.getLessonsOnce()
        val timedSchedule = SktjfTimedSchedule.create(mode, profiles, states, storedLessons, extraLessons)
        val lessons = storedLessons.map { it.forTimetable(settings?.enableAbTimetable != false) }.associateBy { it.lessonKey() }
        val examDaySchedules = dao.getExamDaySchedulesOnce().filter { settings?.enableExamTimetable != false }.associateBy { it.date }
        val examLessons = dao.getExamLessonsOnce().filter { settings?.enableExamTimetable != false }.associateBy { it.date to it.slotIndex }
        val incompleteTasks = dao.getIncompleteTasksOnce()
        val incompletePlans = dao.getIncompletePlansOnce()

        val classSlots = if (settings != null) generateClassSlots(
            periodsPerDay = settings.periodsPerDay,
            periodDurationMin = settings.periodDurationMin,
            breakBetweenPeriodsMin = settings.breakBetweenPeriodsMin,
            lunchBreakMin = settings.lunchBreakMin,
            firstPeriodStartHour = settings.firstPeriodStartHour,
            firstPeriodStartMinute = settings.firstPeriodStartMinute,
            periodLabelStyle = settings.periodLabelStyle,
            lunchAfterPeriod = settings.lunchAfterPeriod
        ) else CLASS_SLOTS

        val dayType = dayTypeMap[today] ?: defaultDayType(today)

        return WidgetData(
            today = today,
            settings = settings,
            classSlots = classSlots,
            dayType = dayType,
            dayTypeEntities = dayTypeEntities,
            dayTypeMap = dayTypeMap,
            cancelledLessons = cancelledLessons,
            changedLessons = changedLessons,
            lessons = lessons,
            examDaySchedules = examDaySchedules,
            examLessons = examLessons,
            incompleteTasks = incompleteTasks,
            incompletePlans = incompletePlans,
            extraResolver = extraResolver,
            timedSchedule = timedSchedule,
            electiveSchedule = electiveSchedule
        )
    }

    fun defaultDayType(date: LocalDate): DayType {
        val weekend = date.dayOfWeek.value >= DayOfWeek.SATURDAY.value
        return if (weekend || JapaneseHolidayCalculator.isHoliday(date)) DayType.HOLIDAY else DayType.A
    }

    fun resolveLesson(
        date: LocalDate,
        slotIndex: Int,
        lessons: Map<LessonKey, LessonEntity>,
        dayTypeEntities: Map<LocalDate, DayTypeEntity>,
        dayTypeMap: Map<LocalDate, DayType>,
        changedLessons: Map<Pair<LocalDate, Int>, ChangedLessonEntity> = emptyMap(),
        semesterTimetablesEnabled: Boolean = false,
        secondTermStartMonth: Int = 10,
        secondTermStartDay: Int = 1,
        timedSchedule: SktjfTimedSchedule? = null,
        electiveSchedule: SktjfElectiveSchedule? = null
    ): ResolvedLesson? {
        val dayTypeEntity = dayTypeEntities[date]
        val dayType = dayTypeEntity?.dayType ?: dayTypeMap[date] ?: defaultDayType(date)
        if (dayType == DayType.HOLIDAY) return null

        val lessonDayOfWeek = dayTypeEntity?.overrideLessonDayOfWeek ?: date.dayOfWeek.value
        val lessonDayType = dayTypeEntity?.overrideLessonDayType ?: dayType
        val timetableTerm = timetableTermForDate(
            date, semesterTimetablesEnabled, secondTermStartMonth, secondTermStartDay
        )
        val lesson = lessons[
            LessonKey(
                academicYear = academicYearForDate(date),
                timetableTerm = timetableTerm,
                dayOfWeek = lessonDayOfWeek,
                slotIndex = slotIndex
            )
        ]
        val baseLesson = when (lesson?.mode) {
            LessonMode.WEEKLY -> if (lesson.weeklySubject.isBlank()) null
            else ResolvedLesson(lesson.weeklySubject, lesson.weeklyTeacher, lesson.weeklyLocation, lessonId = lesson.id.takeIf { it > 0 })

            LessonMode.ALTERNATING -> when (lessonDayType) {
                DayType.A -> if (lesson.aSubject.isBlank()) null
                else ResolvedLesson(lesson.aSubject, lesson.aTeacher, lesson.aLocation, lessonId = lesson.id.takeIf { it > 0 })
                DayType.B -> if (lesson.bSubject.isBlank()) null
                else ResolvedLesson(lesson.bSubject, lesson.bTeacher, lesson.bLocation, lessonId = lesson.id.takeIf { it > 0 })
                DayType.HOLIDAY -> null
            }
            null -> null
        }
        val cycle = if (lessonDayType == DayType.B) SktjfCycle.B else SktjfCycle.A
        val timedSelected = when (val timed = timedSchedule?.resolve(academicYearForDate(date), timetableTerm, lessonDayOfWeek, slotIndex + 1, date, cycle)) {
            null, SktjfTimedSlotResult.Unmanaged -> baseLesson
            is SktjfTimedSlotResult.Managed -> (timed.selection as? SktjfWeekSelection.Selected)?.value?.resolvedLesson()
        }
        val selected = when(val elective=electiveSchedule?.resolve(academicYearForDate(date),timetableTerm,lessonDayOfWeek,slotIndex+1,date,cycle)) {
            null, SktjfElectiveSlotResult.Unmanaged -> timedSelected
            SktjfElectiveSlotResult.Unconfirmed -> null
            is SktjfElectiveSlotResult.Managed -> elective.selectedLesson()
        }
        return applyChangedLesson(selected, changedLessons[date to slotIndex])
    }

    fun classSlotsForDate(data: WidgetData, date: LocalDate): List<ClassSlot> {
        if (!isExamScheduleDate(data, date)) {
            return data.classSlots
        }
        val examSlots = data.examLessons.values
            .filter { it.date == date }
            .sortedBy { it.slotIndex }
            .map { exam ->
                ClassSlot(
                    index = exam.slotIndex,
                    label = formatExamPeriodLabel(
                        exam.slotIndex,
                        data.settings?.periodLabelStyle ?: PeriodLabelStyle.PAIR_KOSHI
                    ),
                    start = LocalTime.of(exam.startHour, exam.startMinute),
                    end = LocalTime.of(exam.endHour, exam.endMinute)
                )
            }
        val settings = data.settings ?: return data.classSlots
        return examSlots.ifEmpty {
            generateClassSlots(
                periodsPerDay = settings.examPeriodsPerDay,
                periodDurationMin = settings.examPeriodDurationMin,
                breakBetweenPeriodsMin = settings.examBreakBetweenPeriodsMin,
                lunchBreakMin = settings.examLunchBreakMin,
                firstPeriodStartHour = settings.examFirstPeriodStartHour,
                firstPeriodStartMinute = settings.examFirstPeriodStartMinute,
                periodLabelStyle = settings.periodLabelStyle.forExamTimetable(),
                lunchAfterPeriod = settings.examLunchAfterPeriod
            )
        }
    }

    fun resolveLessons(data: WidgetData, date: LocalDate, slotIndex: Int): List<ResolvedLesson> {
        if (isExamScheduleDate(data, date)) {
            val exam = data.examLessons[date to slotIndex] ?: return emptyList()
            if (exam.subject.isBlank()) return emptyList()
            return listOf(ResolvedLesson(
                exam.subject,
                exam.teacher,
                exam.location.takeIf { it.isNotBlank() }
            ))
        }
        val base = resolveLesson(
            date = date,
            slotIndex = slotIndex,
            lessons = data.lessons,
            dayTypeEntities = data.dayTypeEntities,
            dayTypeMap = data.dayTypeMap,
            changedLessons = data.changedLessons,
            semesterTimetablesEnabled = data.settings?.enableSemesterTimetables == true,
            secondTermStartMonth = data.settings?.secondTermStartMonth ?: 10,
            secondTermStartDay = data.settings?.secondTermStartDay ?: 1,
            timedSchedule = data.timedSchedule,
            electiveSchedule = data.electiveSchedule
        )
        if (slotIndex !in 0..11) return listOfNotNull(base)
        val day = data.dayTypeEntities[date] ?: DayTypeEntity(date, data.dayTypeMap[date] ?: defaultDayType(date))
        val cycle = if ((day.overrideLessonDayType ?: day.dayType) == DayType.B) SktjfCycle.B else SktjfCycle.A
        val term = timetableTermForDate(date, data.settings?.enableSemesterTimetables == true,
            data.settings?.secondTermStartMonth ?: 10, data.settings?.secondTermStartDay ?: 1)
        if (data.timedSchedule?.resolve(academicYearForDate(date), term, day.overrideLessonDayOfWeek ?: date.dayOfWeek.value,
                slotIndex + 1, date, cycle) is SktjfTimedSlotResult.Managed) return listOfNotNull(base)
        val extra = data.extraResolver?.lessonsOn(date, slotIndex + 1, day,
            data.settings?.enableSemesterTimetables == true, data.settings?.secondTermStartMonth ?: 10,
            data.settings?.secondTermStartDay ?: 1).orEmpty().map {
            ResolvedLesson(it.content.subject, it.content.teachers.joinToString("、"),
                it.content.rooms.joinToString("、").ifBlank { null }, extraLessonId = it.id)
        }
        return listOfNotNull(base) + extra
    }

    fun resolveLesson(data: WidgetData, date: LocalDate, slotIndex: Int): ResolvedLesson? {
        val lessons = resolveLessons(data, date, slotIndex)
        return when (lessons.size) {
            0 -> null
            1 -> lessons.single()
            else -> ResolvedLesson(lessons.joinToString("、") { it.subject },
                lessons.map { it.teacher }.filter { it.isNotBlank() }.joinToString("、"),
                lessons.mapNotNull { it.location?.takeIf(String::isNotBlank) }.joinToString("、").ifBlank { null })
        }
    }

    fun lessonTitle(lessons: List<ResolvedLesson>): String? = lessons.firstOrNull()?.let {
        if (lessons.size == 1) it.subject else "${it.subject} +${lessons.size - 1}"
    }

    fun isExamScheduleDate(data: WidgetData, date: LocalDate): Boolean {
        val label = data.dayTypeEntities[date]?.holidaySpecialLabel
        return data.settings?.enableExamTimetable != false && date in data.examDaySchedules &&
            data.examLessons.values.any { it.date == date && it.hasEnteredContent() } &&
            label?.usesExamTimetable == true
    }

    /** 今日から先で最も近い授業を返す（授業中ならその授業を含む） */
    fun findNextLesson(data: WidgetData): NextLessonWidgetEntry? {
        val now = LocalTime.now()
        val nowMinuteOfDay = now.hour * 60 + now.minute
        val todayEndMinute = effectiveSchoolEndMinuteOfDay(data)
        for (dayOffset in 0..14) {
            val date = data.today.plusDays(dayOffset.toLong())
            if (date == data.today && nowMinuteOfDay >= todayEndMinute) continue
            val isExamDate = isExamScheduleDate(data, date)
            for (slot in classSlotsForDate(data, date)) {
                if (!isExamDate && data.cancelledLessons.contains(date to slot.index)) continue
                if (date == data.today && !slot.end.isAfter(now)) continue
                val lesson = resolveLesson(data, date, slot.index) ?: continue
                val individualLessons = resolveLessons(data, date, slot.index)
                return NextLessonWidgetEntry(
                    date = date,
                    slot = slot,
                    lesson = lesson,
                    tasks = individualLessons.flatMap { tasksForSlot(data, date, it, slot) }.distinctBy { it.id },
                    plans = individualLessons.flatMap { plansForSlot(data, date, it, slot) }.distinctBy { it.id },
                    displayTitle = lessonTitle(individualLessons) ?: lesson.subject
                )
            }
        }
        return null
    }

    private fun effectiveSchoolEndMinuteOfDay(data: WidgetData): Int {
        val settings = data.settings
        if (isExamScheduleDate(data, data.today)) {
            val examEnd = classSlotsForDate(data, data.today).lastOrNull()?.end ?: return 24 * 60
            return examEnd.hour * 60 + examEnd.minute
        }
        if (settings != null && settings.departureHour >= 0 && settings.departureMinute >= 0) {
            return settings.departureHour.coerceIn(0, 23) * 60 +
                settings.departureMinute.coerceIn(0, 59)
        }
        val lastEnd = classSlotsForDate(data, data.today).lastOrNull()?.end ?: return 24 * 60
        val roundedEndHour = lastEnd.hour + if (lastEnd.minute > 0) 1 else 0
        return roundedEndHour * 60
    }

    private fun matchesWidgetLesson(subject: String, lessonId: Long?, extraLessonId: String?, lesson: ResolvedLesson): Boolean =
        if (extraLessonId != null) extraLessonId == lesson.extraLessonId
        else (lessonId == null || lesson.lessonId == null || lessonId == lesson.lessonId) && sameSubject(subject, lesson.subject)

    /** 授業時間帯に提出期限がある未完了課題を返す */
    fun tasksForSlot(
        data: WidgetData,
        date: LocalDate,
        lesson: ResolvedLesson,
        slot: ClassSlot
    ): List<TaskEntity> {
        val slotStart = slot.start.hour * 60 + slot.start.minute
        val slotEnd = slot.end.hour * 60 + slot.end.minute
        return data.incompleteTasks.filter { task ->
            task.dueDate == date &&
                    matchesWidgetLesson(task.subject, task.lessonId, task.extraLessonId, lesson) &&
                    (task.dueHour * 60 + task.dueMinute) in slotStart..slotEnd
        }
    }

    /** 授業時間帯に時刻がある未完了予定を返す */
    fun plansForSlot(
        data: WidgetData,
        date: LocalDate,
        lesson: ResolvedLesson,
        slot: ClassSlot
    ): List<PlanEntity> {
        val slotStart = slot.start.hour * 60 + slot.start.minute
        val slotEnd = slot.end.hour * 60 + slot.end.minute
        return data.incompletePlans.filter { plan ->
            plan.dueDate == date &&
                    matchesWidgetLesson(plan.subject, plan.lessonId, plan.extraLessonId, lesson) &&
                    (plan.dueHour * 60 + plan.dueMinute) in slotStart..slotEnd
        }
    }

    /** 科目に一致する未完了課題が存在するか */
    fun hasTasksForLesson(data: WidgetData, lesson: ResolvedLesson?): Boolean {
        if (lesson == null) return false
        return data.incompleteTasks.any { task ->
            !task.isCompleted &&
                    matchesWidgetLesson(task.subject, task.lessonId, task.extraLessonId, lesson)
        }
    }

    /** 特定日に科目が一致する未完了課題が存在するか */
    fun hasTasksForDate(data: WidgetData, date: LocalDate, lesson: ResolvedLesson?): Boolean {
        if (lesson == null) return false
        return data.incompleteTasks.any { task ->
            task.dueDate == date &&
                    matchesWidgetLesson(task.subject, task.lessonId, task.extraLessonId, lesson)
        }
    }

    /** 科目に一致する未完了予定が存在するか */
    fun hasPlansForLesson(data: WidgetData, lesson: ResolvedLesson?): Boolean {
        if (lesson == null) return false
        return data.incompletePlans.any { plan ->
            !plan.isCompleted &&
                    matchesWidgetLesson(plan.subject, plan.lessonId, plan.extraLessonId, lesson)
        }
    }

    /** 特定日に科目が一致する未完了予定が存在するか */
    fun hasPlansForDate(data: WidgetData, date: LocalDate, lesson: ResolvedLesson?): Boolean {
        if (lesson == null) return false
        return data.incompletePlans.any { plan ->
            plan.dueDate == date &&
                    matchesWidgetLesson(plan.subject, plan.lessonId, plan.extraLessonId, lesson)
        }
    }

    fun formatDueDate(task: TaskEntity, today: LocalDate): String {
        val time = "${task.dueHour}:${task.dueMinute.toString().padStart(2, '0')}"
        return when (task.dueDate) {
            today -> "今日 $time"
            today.plusDays(1) -> "明日 $time"
            else -> "${task.dueDate.monthValue}/${task.dueDate.dayOfMonth} $time"
        }
    }

    fun formatNextLessonDate(date: LocalDate, today: LocalDate): String {
        return when (date) {
            today -> "今日"
            today.plusDays(1) -> "明日"
            else -> "${date.monthValue}/${date.dayOfMonth}(${dayLabel(date.dayOfWeek.value)})"
        }
    }

    fun dayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "月"; 2 -> "火"; 3 -> "水"; 4 -> "木"; 5 -> "金"; else -> ""
    }

    fun dayTypeLabel(dayType: DayType): String = when (dayType) {
        DayType.A -> "A"
        DayType.B -> "B"
        DayType.HOLIDAY -> "休"
    }

    fun dayTypeDisplayText(dayType: DayType, overrideLessonDayOfWeek: Int?, regularDayLabel: String? = null): String {
        val base = if (dayType != DayType.HOLIDAY && regularDayLabel != null) regularDayLabel else dayTypeLabel(dayType)
        if (overrideLessonDayOfWeek == null || dayType == DayType.HOLIDAY) return base
        val dow = dayLabel(overrideLessonDayOfWeek)
        return if (dow.isBlank()) base else "$base($dow)"
    }
}
