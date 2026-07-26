package jp.linkserver.nittcsc.widget

import android.content.Context
import jp.linkserver.nittcsc.data.AppDatabase
import jp.linkserver.nittcsc.data.ChangedLessonEntity
import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.ExamDayScheduleEntity
import jp.linkserver.nittcsc.data.ExamLessonEntity
import jp.linkserver.nittcsc.data.hasEnteredContent
import jp.linkserver.nittcsc.data.HolidaySpecialLabel
import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import jp.linkserver.nittcsc.data.PlanEntity
import jp.linkserver.nittcsc.data.ResolvedLesson
import jp.linkserver.nittcsc.data.SettingsEntity
import jp.linkserver.nittcsc.data.TaskEntity
import jp.linkserver.nittcsc.logic.CLASS_SLOTS
import jp.linkserver.nittcsc.logic.ClassSlot
import jp.linkserver.nittcsc.logic.JapaneseHolidayCalculator
import jp.linkserver.nittcsc.logic.PeriodLabelStyle
import jp.linkserver.nittcsc.logic.applyChangedLesson
import jp.linkserver.nittcsc.logic.formatExamPeriodLabel
import jp.linkserver.nittcsc.logic.forExamTimetable
import jp.linkserver.nittcsc.logic.generateClassSlots
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
    val lessons: Map<Pair<Int, Int>, LessonEntity>,
    val examDaySchedules: Map<LocalDate, ExamDayScheduleEntity>,
    val examLessons: Map<Pair<LocalDate, Int>, ExamLessonEntity>,
    val incompleteTasks: List<TaskEntity>,
    val incompletePlans: List<PlanEntity>
)

data class NextLessonWidgetEntry(
    val date: LocalDate,
    val slot: ClassSlot,
    val lesson: ResolvedLesson,
    val tasks: List<TaskEntity>,
    val plans: List<PlanEntity>
)

object WidgetDataHelper {

    suspend fun load(context: Context): WidgetData {
        val db = AppDatabase.getInstance(context)
        val dao = db.schedulerDao()
        val today = LocalDate.now()

        val settings = dao.getSettings()
        val dayTypes = dao.getDayTypesOnce()
        val dayTypeEntities = dayTypes.associateBy { it.date }
        val dayTypeMap = dayTypes.associate { it.date to it.dayType }
        val cancelledLessons = dao.getCancelledLessonsOnce().map { it.date to it.slotIndex }.toSet()
        val changedLessons = dao.getChangedLessonsOnce().associateBy { it.date to it.slotIndex }
        val lessons = dao.getLessonsOnce().associate { (it.dayOfWeek to it.slotIndex) to it }
        val examDaySchedules = dao.getExamDaySchedulesOnce().associateBy { it.date }
        val examLessons = dao.getExamLessonsOnce().associateBy { it.date to it.slotIndex }
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
            incompletePlans = incompletePlans
        )
    }

    fun defaultDayType(date: LocalDate): DayType {
        val weekend = date.dayOfWeek.value >= DayOfWeek.SATURDAY.value
        return if (weekend || JapaneseHolidayCalculator.isHoliday(date)) DayType.HOLIDAY else DayType.A
    }

    fun resolveLesson(
        date: LocalDate,
        slotIndex: Int,
        lessons: Map<Pair<Int, Int>, LessonEntity>,
        dayTypeEntities: Map<LocalDate, DayTypeEntity>,
        dayTypeMap: Map<LocalDate, DayType>,
        changedLessons: Map<Pair<LocalDate, Int>, ChangedLessonEntity> = emptyMap()
    ): ResolvedLesson? {
        if (date.dayOfWeek.value !in 1..5) return null
        val dayTypeEntity = dayTypeEntities[date]
        val dayType = dayTypeEntity?.dayType ?: dayTypeMap[date] ?: defaultDayType(date)
        if (dayType == DayType.HOLIDAY) return null

        val lessonDayOfWeek = dayTypeEntity?.overrideLessonDayOfWeek ?: date.dayOfWeek.value
        val lessonDayType = dayTypeEntity?.overrideLessonDayType ?: dayType
        val lesson = lessons[lessonDayOfWeek to slotIndex]
        val baseLesson = when (lesson?.mode) {
            LessonMode.WEEKLY -> if (lesson.weeklySubject.isBlank()) null
            else ResolvedLesson(lesson.weeklySubject, lesson.weeklyTeacher, lesson.weeklyLocation)

            LessonMode.ALTERNATING -> when (lessonDayType) {
                DayType.A -> if (lesson.aSubject.isBlank()) null
                else ResolvedLesson(lesson.aSubject, lesson.aTeacher, lesson.aLocation)
                DayType.B -> if (lesson.bSubject.isBlank()) null
                else ResolvedLesson(lesson.bSubject, lesson.bTeacher, lesson.bLocation)
                DayType.HOLIDAY -> null
            }
            null -> null
        }
        return applyChangedLesson(baseLesson, changedLessons[date to slotIndex])
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

    fun resolveLesson(data: WidgetData, date: LocalDate, slotIndex: Int): ResolvedLesson? {
        if (isExamScheduleDate(data, date)) {
            val exam = data.examLessons[date to slotIndex] ?: return null
            if (exam.subject.isBlank()) return null
            return ResolvedLesson(
                exam.subject,
                exam.teacher,
                exam.location.takeIf { it.isNotBlank() }
            )
        }
        return resolveLesson(
            date = date,
            slotIndex = slotIndex,
            lessons = data.lessons,
            dayTypeEntities = data.dayTypeEntities,
            dayTypeMap = data.dayTypeMap,
            changedLessons = data.changedLessons
        )
    }

    fun isExamScheduleDate(data: WidgetData, date: LocalDate): Boolean {
        val label = data.dayTypeEntities[date]?.holidaySpecialLabel
        return date in data.examDaySchedules &&
            data.examLessons.values.any { it.date == date && it.hasEnteredContent() } &&
            (label == HolidaySpecialLabel.MIDTERM || label == HolidaySpecialLabel.FINAL)
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
                return NextLessonWidgetEntry(
                    date = date,
                    slot = slot,
                    lesson = lesson,
                    tasks = tasksForSlot(data, date, lesson, slot),
                    plans = plansForSlot(data, date, lesson, slot)
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
                    task.subject.trim().equals(lesson.subject.trim(), ignoreCase = true) &&
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
                    plan.subject.trim().equals(lesson.subject.trim(), ignoreCase = true) &&
                    (plan.dueHour * 60 + plan.dueMinute) in slotStart..slotEnd
        }
    }

    /** 科目に一致する未完了課題が存在するか */
    fun hasTasksForLesson(data: WidgetData, lesson: ResolvedLesson?): Boolean {
        if (lesson == null) return false
        return data.incompleteTasks.any { task ->
            !task.isCompleted &&
                    task.subject.trim().equals(lesson.subject.trim(), ignoreCase = true)
        }
    }

    /** 特定日に科目が一致する未完了課題が存在するか */
    fun hasTasksForDate(data: WidgetData, date: LocalDate, lesson: ResolvedLesson?): Boolean {
        if (lesson == null) return false
        return data.incompleteTasks.any { task ->
            task.dueDate == date &&
                    task.subject.trim().equals(lesson.subject.trim(), ignoreCase = true)
        }
    }

    /** 科目に一致する未完了予定が存在するか */
    fun hasPlansForLesson(data: WidgetData, lesson: ResolvedLesson?): Boolean {
        if (lesson == null) return false
        return data.incompletePlans.any { plan ->
            !plan.isCompleted &&
                    plan.subject.trim().equals(lesson.subject.trim(), ignoreCase = true)
        }
    }

    /** 特定日に科目が一致する未完了予定が存在するか */
    fun hasPlansForDate(data: WidgetData, date: LocalDate, lesson: ResolvedLesson?): Boolean {
        if (lesson == null) return false
        return data.incompletePlans.any { plan ->
            plan.dueDate == date &&
                    plan.subject.trim().equals(lesson.subject.trim(), ignoreCase = true)
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

    fun dayTypeDisplayText(dayType: DayType, overrideLessonDayOfWeek: Int?): String {
        val base = dayTypeLabel(dayType)
        if (overrideLessonDayOfWeek == null || dayType == DayType.HOLIDAY) return base
        val dow = dayLabel(overrideLessonDayOfWeek)
        return if (dow.isBlank()) base else "$base($dow)"
    }
}
