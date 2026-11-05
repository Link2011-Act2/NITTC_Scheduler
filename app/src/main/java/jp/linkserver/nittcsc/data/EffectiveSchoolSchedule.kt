package jp.linkserver.nittcsc.data

import jp.linkserver.nittcsc.logic.*
import java.time.LocalDate
import java.time.LocalTime

fun SettingsEntity.basicClassSlots(): List<ClassSlot> = generateClassSlots(
    periodsPerDay, periodDurationMin, breakBetweenPeriodsMin, lunchBreakMin,
    firstPeriodStartHour, firstPeriodStartMinute, periodLabelStyle, lunchAfterPeriod
)

fun SettingsEntity.manualArrivalTime(): LocalTime? =
    if (arrivalTimeAuto) null else validSchoolTime(arrivalHour, arrivalMinute)

fun SettingsEntity.departureTime(): LocalTime =
    schoolDepartureTime(validSchoolTime(departureHour, departureMinute), basicClassSlots())

private fun validSchoolTime(hour: Int, minute: Int): LocalTime? =
    if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null

/** 通知の実行時にも同じ実効時間割を使い、休講・振替・A/B・試験を反映する。 */
class EffectiveSchoolSchedule(
    val settings: SettingsEntity,
    private val dayTypes: Map<LocalDate, DayTypeEntity>,
    lessons: List<LessonEntity>,
    private val changed: Map<Pair<LocalDate, Int>, ChangedLessonEntity>,
    private val cancelled: Set<Pair<LocalDate, Int>>,
    private val examSchedules: Map<LocalDate, ExamDayScheduleEntity>,
    private val exams: Map<LocalDate, List<ExamLessonEntity>>
) {
    private val lessons = lessons.map { it.forTimetable(settings.enableAbTimetable) }.associateBy { it.lessonKey() }
    val basicSlots = settings.basicClassSlots()

    fun lessonsOn(date: LocalDate): List<UpcomingLessonCandidate<ResolvedLesson>> {
        if (date < settings.termStart || date > settings.termEnd) return emptyList()
        if (settings.enableExamTimetable && examSchedules.containsKey(date) &&
            dayTypes[date]?.holidaySpecialLabel?.usesExamTimetable == true &&
            exams[date].orEmpty().any { it.hasEnteredContent() }) {
            return exams[date].orEmpty().filter { it.subject.isNotBlank() }.map { exam ->
                UpcomingLessonCandidate(date, exam.slotIndex,
                    formatExamPeriodLabel(exam.slotIndex, settings.periodLabelStyle),
                    LocalTime.of(exam.startHour, exam.startMinute), LocalTime.of(exam.endHour, exam.endMinute),
                    ResolvedLesson(exam.subject, exam.teacher, exam.location.takeIf { it.isNotBlank() }))
            }
        }
        val day = dayTypes[date]
        val type = day?.dayType ?: if (date.dayOfWeek.value >= 6 || JapaneseHolidayCalculator.isHoliday(date))
            DayType.HOLIDAY else DayType.A
        if (type == DayType.HOLIDAY) return emptyList()
        val lessonDay = day?.overrideLessonDayOfWeek ?: date.dayOfWeek.value
        val lessonType = day?.overrideLessonDayType ?: type
        val term = timetableTermForDate(date, settings.enableSemesterTimetables,
            settings.secondTermStartMonth, settings.secondTermStartDay)
        return basicSlots.mapNotNull { slot ->
            if (date to slot.index in cancelled) return@mapNotNull null
            val base = lessons[LessonKey(academicYearForDate(date), term, lessonDay, slot.index)]?.let {
                when (it.mode) {
                    LessonMode.WEEKLY -> ResolvedLesson(it.weeklySubject, it.weeklyTeacher, it.weeklyLocation)
                    LessonMode.ALTERNATING -> if (lessonType == DayType.B)
                        ResolvedLesson(it.bSubject, it.bTeacher, it.bLocation)
                    else ResolvedLesson(it.aSubject, it.aTeacher, it.aLocation)
                }
            }
            val lesson = applyChangedLesson(base, changed[date to slot.index])
                ?.takeIf { it.subject.isNotBlank() } ?: return@mapNotNull null
            UpcomingLessonCandidate(date, slot.index, slot.label, slot.start, slot.end, lesson)
        }
    }

    fun arrivalTime(date: LocalDate): LocalTime = schoolArrivalTime(
        settings.manualArrivalTime() ?: examArrivalTime(date),
        lessonsOn(date).map { ClassSlot(it.slotIndex, it.slotLabel, it.start, it.end) }, basicSlots
    )

    /** 試験日に保存した登校時刻も手動指定として保持する。通常日の初期値8:30とは別設定。 */
    private fun examArrivalTime(date: LocalDate): LocalTime? {
        if (!settings.enableExamTimetable || dayTypes[date]?.holidaySpecialLabel?.usesExamTimetable != true ||
            exams[date].orEmpty().none { it.hasEnteredContent() }) return null
        val schedule = examSchedules[date] ?: return null
        return validSchoolTime(schedule.arrivalHour, schedule.arrivalMinute)
    }

    companion object {
        suspend fun load(dao: SchedulerDao): EffectiveSchoolSchedule? {
            val settings = dao.getSettings() ?: return null
            return EffectiveSchoolSchedule(settings, dao.getDayTypesOnce().associateBy { it.date },
                dao.getLessonsOnce(), dao.getChangedLessonsOnce().associateBy { it.date to it.slotIndex },
                dao.getCancelledLessonsOnce().map { it.date to it.slotIndex }.toSet(),
                dao.getExamDaySchedulesOnce().associateBy { it.date }, dao.getExamLessonsOnce().groupBy { it.date })
        }
    }
}
