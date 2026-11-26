package jp.linkserver.nittcsc.data

import jp.linkserver.nittcsc.logic.*
import jp.linkserver.nittcsc.sktjf.SktjfCycle
import jp.linkserver.nittcsc.sktjf.SktjfExtraLessonResolver
import jp.linkserver.nittcsc.sktjf.SktjfLessonOrigin
import jp.linkserver.nittcsc.sktjf.SktjfNotificationOrigins
import jp.linkserver.nittcsc.sktjf.shouldNotifySktjfLesson
import jp.linkserver.nittcsc.sktjf.SktjfTimedSchedule
import jp.linkserver.nittcsc.sktjf.SktjfTimedSlotResult
import jp.linkserver.nittcsc.sktjf.SktjfWeekSelection
import jp.linkserver.nittcsc.sktjf.resolvedLesson
import jp.linkserver.nittcsc.sktjf.SktjfElectiveSchedule
import jp.linkserver.nittcsc.sktjf.SktjfElectiveSlotResult
import jp.linkserver.nittcsc.sktjf.SktjfElectiveResolution
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
class EffectiveSchoolSchedule internal constructor(
    val settings: SettingsEntity,
    private val dayTypes: Map<LocalDate, DayTypeEntity>,
    lessons: List<LessonEntity>,
    private val changed: Map<Pair<LocalDate, Int>, ChangedLessonEntity>,
    private val cancelled: Set<Pair<LocalDate, Int>>,
    private val examSchedules: Map<LocalDate, ExamDayScheduleEntity>,
    private val exams: Map<LocalDate, List<ExamLessonEntity>>,
    private val notificationOrigins: SktjfNotificationOrigins? = null,
    private val usageMode: SchedulerUsageMode? = null,
    private val extraResolver: SktjfExtraLessonResolver? = null,
    private val timedSchedule: SktjfTimedSchedule? = null,
    private val electiveSchedule: SktjfElectiveSchedule? = null
) {
    private val originalLessons = lessons.associateBy { it.lessonKey() }
    private val lessons = lessons.map { it.forTimetable(settings.enableAbTimetable) }.associateBy { it.lessonKey() }
    val basicSlots = settings.basicClassSlots()

    fun lessonsOn(date: LocalDate): List<UpcomingLessonCandidate<ResolvedLesson>> {
        if (date < settings.termStart || date > settings.termEnd) return emptyList()
        if (hasExamSchedule(date)) {
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
            val ordinaryBase = lessons[LessonKey(academicYearForDate(date), term, lessonDay, slot.index)]?.let {
                when (it.mode) {
                    LessonMode.WEEKLY -> ResolvedLesson(it.weeklySubject, it.weeklyTeacher, it.weeklyLocation)
                    LessonMode.ALTERNATING -> if (lessonType == DayType.B)
                        ResolvedLesson(it.bSubject, it.bTeacher, it.bLocation)
                    else ResolvedLesson(it.aSubject, it.aTeacher, it.aLocation)
                }
            }
            val cycle = if (lessonType == DayType.B) SktjfCycle.B else SktjfCycle.A
            val timedBase = when (val timed = timedSchedule?.resolve(academicYearForDate(date), term, lessonDay, slot.index + 1, date, cycle)) {
                null, SktjfTimedSlotResult.Unmanaged -> ordinaryBase
                is SktjfTimedSlotResult.Managed -> (timed.selection as? SktjfWeekSelection.Selected)?.value?.resolvedLesson()
            }
            val base = when (val elective = electiveSchedule?.resolve(academicYearForDate(date), term, lessonDay, slot.index + 1, date, cycle)) {
                null, SktjfElectiveSlotResult.Unmanaged -> timedBase
                SktjfElectiveSlotResult.Unconfirmed -> null
                is SktjfElectiveSlotResult.Managed -> {
                    val id=(elective.resolution as? SktjfElectiveResolution.Chosen)?.lessonId
                    elective.options.singleOrNull { it.sourceLessonId==id }?.let { item ->
                        ResolvedLesson(item.content.subject,item.content.teachers.joinToString("、"),
                            item.content.rooms.joinToString("、").ifBlank { null },extraLessonId=item.id)
                    }
                }
            }
            val lesson = applyChangedLesson(base, changed[date to slot.index])
                ?.takeIf { it.subject.isNotBlank() } ?: return@mapNotNull null
            UpcomingLessonCandidate(date, slot.index, slot.label, slot.start, slot.end, lesson)
        }
    }

    private fun hasExamSchedule(date: LocalDate): Boolean = settings.enableExamTimetable && examSchedules.containsKey(date) &&
        dayTypes[date]?.holidaySpecialLabel?.usesExamTimetable == true && exams[date].orEmpty().any { it.hasEnteredContent() }

    internal fun notificationLessonsOn(date: LocalDate, allowed: (ResolvedLesson) -> Boolean = { true }): List<UpcomingLessonCandidate<ResolvedLesson>> {
        val candidates = lessonsOn(date)
        if (usageMode != SchedulerUsageMode.NITTC || hasExamSchedule(date)) return candidates.filter { allowed(it.content) }
        val day = dayTypes[date]
        val effectiveDay = day?.overrideLessonDayOfWeek ?: date.dayOfWeek.value
        val effectiveType = day?.overrideLessonDayType ?: day?.dayType ?: DayType.A
        val term = timetableTermForDate(date, settings.enableSemesterTimetables, settings.secondTermStartMonth, settings.secondTermStartDay)
        val year = academicYearForDate(date)
        val ordinary = candidates.filter { candidate ->
            val row = originalLessons[LessonKey(year, term, effectiveDay, candidate.slotIndex)]
            val cycle = when {
                row == null -> null
                row.mode == LessonMode.WEEKLY -> SktjfCycle.WEEKLY
                !settings.enableAbTimetable || effectiveType != DayType.B -> SktjfCycle.A
                else -> SktjfCycle.B
            }
            val effectiveCycle = if (effectiveType == DayType.B) SktjfCycle.B else SktjfCycle.A
            val timed = timedSchedule?.resolve(year, term, effectiveDay, candidate.slotIndex + 1, date, effectiveCycle)
            val elective = electiveSchedule?.resolve(year,term,effectiveDay,candidate.slotIndex+1,date,effectiveCycle)
            val origin = if (elective is SktjfElectiveSlotResult.Managed) {
                val selected=(elective.resolution as? SktjfElectiveResolution.Chosen)?.lessonId
                elective.options.singleOrNull { it.sourceLessonId==selected }?.content?.let { SktjfLessonOrigin(it.kind,it.subject) }
            } else if (timed is SktjfTimedSlotResult.Managed) {
                (timed.selection as? SktjfWeekSelection.Selected)?.value?.sourceLessonId?.let { id ->
                    timedSchedule.origin(year, term, effectiveDay, candidate.slotIndex + 1, effectiveCycle, id)
                }
            } else cycle?.let { notificationOrigins?.find(year, if (term == TimetableTerm.FIRST) "S" else "F",
                effectiveDay, candidate.slotIndex + 1, it) }
            shouldNotifySktjfLesson(usageMode, origin, candidate.content) && allowed(candidate.content)
        }
        if (extraResolver == null || date < settings.termStart || date > settings.termEnd) return ordinary
        val actualType = day?.dayType ?: if (date.dayOfWeek.value >= 6 || JapaneseHolidayCalculator.isHoliday(date))
            DayType.HOLIDAY else DayType.A
        val effective = (day ?: DayTypeEntity(date, actualType)).forTimetable(settings.enableAbTimetable)
        val extra = basicSlots.flatMap { slot ->
            if (date to slot.index in cancelled || slot.index !in 0..11) return@flatMap emptyList()
            val cycle = if (effectiveType == DayType.B) SktjfCycle.B else SktjfCycle.A
            if (timedSchedule?.resolve(year, term, effectiveDay, slot.index + 1, date, cycle) is SktjfTimedSlotResult.Managed)
                return@flatMap emptyList()
            extraResolver.lessonsOn(date, slot.index + 1, effective, settings.enableSemesterTimetables,
                settings.secondTermStartMonth, settings.secondTermStartDay).mapNotNull { item ->
                val content = item.content
                val lesson = ResolvedLesson(content.subject, content.teachers.joinToString("、"),
                    content.rooms.joinToString("、").ifBlank { null })
                val origin = item.sourceLessonId?.let { SktjfLessonOrigin(content.kind, content.subject) }
                if (!shouldNotifySktjfLesson(usageMode, origin, lesson) || !allowed(lesson)) null
                else UpcomingLessonCandidate(date, slot.index, slot.label, slot.start, slot.end, lesson)
            }
        }
        // One notification per time slot keeps alarm and Live Update delivery identities stable.
        return (ordinary + extra).groupBy { it.slotIndex }.toSortedMap().values.map { group ->
            if (group.size == 1) group.single() else group.first().copy(content = ResolvedLesson(
                group.joinToString("、") { it.content.subject },
                group.map { it.content.teacher }.filter { it.isNotBlank() }.joinToString("、"),
                group.mapNotNull { it.content.location?.takeIf(String::isNotBlank) }.joinToString("、").ifBlank { null }))
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
            val mode = dao.getUsageMode()?.mode?.let { value -> SchedulerUsageMode.entries.firstOrNull { it.name == value } }
            val profiles = dao.getNittcProfilesOnce()
            val states = dao.getSktjfSourceStates()
            val origins = SktjfNotificationOrigins.create(mode, profiles, states)
            val extraLessons = dao.getSktjfExtraLessons()
            val selectionGroups = dao.getSktjfSelectionGroups()
            val extraResolver = runCatching { SktjfExtraLessonResolver.create(mode, profiles, extraLessons, selectionGroups, states) }.getOrNull()
            val electiveSchedule = runCatching { SktjfElectiveSchedule.create(mode,profiles,selectionGroups,extraLessons,states) }.getOrNull()
            val lessons = dao.getLessonsOnce()
            val timedSchedule = SktjfTimedSchedule.create(mode, profiles, states, lessons, extraLessons)
            return EffectiveSchoolSchedule(settings, dao.getEffectiveDayTypes().associateBy { it.date },
                lessons, dao.getChangedLessonsOnce().associateBy { it.date to it.slotIndex },
                dao.getCancelledLessonsOnce().map { it.date to it.slotIndex }.toSet(),
                dao.getExamDaySchedulesOnce().associateBy { it.date }, dao.getExamLessonsOnce().groupBy { it.date }, origins, mode, extraResolver, timedSchedule, electiveSchedule)
        }
    }
}
