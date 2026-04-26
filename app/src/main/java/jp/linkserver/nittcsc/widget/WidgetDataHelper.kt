package jp.linkserver.nittcsc.widget

import android.content.Context
import jp.linkserver.nittcsc.data.AppDatabase
import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import jp.linkserver.nittcsc.data.ResolvedLesson
import jp.linkserver.nittcsc.data.SettingsEntity
import jp.linkserver.nittcsc.data.TaskEntity
import jp.linkserver.nittcsc.logic.CLASS_SLOTS
import jp.linkserver.nittcsc.logic.ClassSlot
import jp.linkserver.nittcsc.logic.JapaneseHolidayCalculator
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
    val lessons: Map<Pair<Int, Int>, LessonEntity>,
    val incompleteTasks: List<TaskEntity>
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
        val lessons = dao.getLessonsOnce().associate { (it.dayOfWeek to it.slotIndex) to it }
        val incompleteTasks = dao.getIncompleteTasksOnce()

        val classSlots = if (settings != null) generateClassSlots(
            periodsPerDay = settings.periodsPerDay,
            periodDurationMin = settings.periodDurationMin,
            breakBetweenPeriodsMin = settings.breakBetweenPeriodsMin,
            lunchBreakMin = settings.lunchBreakMin,
            firstPeriodStartHour = settings.firstPeriodStartHour,
            firstPeriodStartMinute = settings.firstPeriodStartMinute,
            useKosenMode = settings.useKosenMode,
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
            lessons = lessons,
            incompleteTasks = incompleteTasks
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
        dayTypeMap: Map<LocalDate, DayType>
    ): ResolvedLesson? {
        if (date.dayOfWeek.value !in 1..5) return null
        val dayTypeEntity = dayTypeEntities[date]
        val dayType = dayTypeEntity?.dayType ?: dayTypeMap[date] ?: defaultDayType(date)
        if (dayType == DayType.HOLIDAY) return null

        val lessonDayOfWeek = dayTypeEntity?.overrideLessonDayOfWeek ?: date.dayOfWeek.value
        val lessonDayType = dayTypeEntity?.overrideLessonDayType ?: dayType
        val lesson = lessons[lessonDayOfWeek to slotIndex] ?: return null

        return when (lesson.mode) {
            LessonMode.WEEKLY -> if (lesson.weeklySubject.isBlank()) null
            else ResolvedLesson(lesson.weeklySubject, lesson.weeklyTeacher, lesson.weeklyLocation)

            LessonMode.ALTERNATING -> when (lessonDayType) {
                DayType.A -> if (lesson.aSubject.isBlank()) null
                else ResolvedLesson(lesson.aSubject, lesson.aTeacher, lesson.aLocation)
                DayType.B -> if (lesson.bSubject.isBlank()) null
                else ResolvedLesson(lesson.bSubject, lesson.bTeacher, lesson.bLocation)
                DayType.HOLIDAY -> null
            }
        }
    }

    /** 現時刻以降で最も近い授業を返す（授業中ならその授業を含む） */
    fun findNextLesson(data: WidgetData): Triple<ClassSlot, ResolvedLesson, List<TaskEntity>>? {
        val now = LocalTime.now()
        for (slot in data.classSlots) {
            val lesson = resolveLesson(data.today, slot.index, data.lessons, data.dayTypeEntities, data.dayTypeMap)
                ?: continue
            if (slot.end.isAfter(now)) {
                val tasks = tasksForSlot(data, lesson, slot)
                return Triple(slot, lesson, tasks)
            }
        }
        return null
    }

    /** 授業時間帯に提出期限がある未完了課題を返す */
    fun tasksForSlot(data: WidgetData, lesson: ResolvedLesson, slot: ClassSlot): List<TaskEntity> {
        val slotStart = slot.start.hour * 60 + slot.start.minute
        val slotEnd = slot.end.hour * 60 + slot.end.minute
        return data.incompleteTasks.filter { task ->
            task.dueDate == data.today &&
                    task.subject.trim().equals(lesson.subject.trim(), ignoreCase = true) &&
                    (task.dueHour * 60 + task.dueMinute) in slotStart..slotEnd
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

    fun formatDueDate(task: TaskEntity, today: LocalDate): String {
        val time = "${task.dueHour}:${task.dueMinute.toString().padStart(2, '0')}"
        return when (task.dueDate) {
            today -> "今日 $time"
            today.plusDays(1) -> "明日 $time"
            else -> "${task.dueDate.monthValue}/${task.dueDate.dayOfMonth} $time"
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
