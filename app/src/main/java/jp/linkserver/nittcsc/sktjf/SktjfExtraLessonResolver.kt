package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.NittcProfileEntity
import jp.linkserver.nittcsc.data.SchedulerUsageMode
import jp.linkserver.nittcsc.data.SktjfExtraLessonBackup
import jp.linkserver.nittcsc.data.SktjfExtraLessonEntity
import jp.linkserver.nittcsc.data.SktjfSelectionGroupEntity
import jp.linkserver.nittcsc.data.SktjfSelectionGroupBackup
import jp.linkserver.nittcsc.data.SktjfSourceStateEntity
import jp.linkserver.nittcsc.data.decodeReplacedTimedSlots
import jp.linkserver.nittcsc.data.attributes
import jp.linkserver.nittcsc.data.validateProfile
import jp.linkserver.nittcsc.logic.TimetableTerm
import jp.linkserver.nittcsc.logic.academicYearForDate
import jp.linkserver.nittcsc.logic.timetableTermForDate
import jp.linkserver.nittcsc.logic.lessonWeekApplies
import java.time.LocalDate

internal data class SktjfVisibleExtra(val id: String, val sourceLessonId: String?, val content: SktjfLessonContent, val storedContentJson: String = "",
    val confirmedWeeks: Map<LocalDate, Int> = emptyMap())
private data class ExtraSlot(val year: Int, val term: String, val day: Int, val period: Int)

/** Decode and group outside the UI render path, preserving every applicable lesson. */
class SktjfExtraLessonResolver private constructor(private val bySlot: Map<ExtraSlot, List<SktjfVisibleExtra>>) {
    internal fun lessonsOn(date: LocalDate, period: Int, effectiveDay: DayTypeEntity,
        semesters: Boolean, secondTermMonth: Int, secondTermDay: Int): List<SktjfVisibleExtra> {
        require(effectiveDay.date == date && period in 1..12)
        if (effectiveDay.dayType == DayType.HOLIDAY) return emptyList()
        val day = effectiveDay.overrideLessonDayOfWeek ?: date.dayOfWeek.value
        val cycle = effectiveDay.overrideLessonDayType ?: effectiveDay.dayType
        if (cycle == DayType.HOLIDAY) return emptyList()
        val term = if (timetableTermForDate(date, semesters, secondTermMonth, secondTermDay) == TimetableTerm.FIRST) "S" else "F"
        return bySlot[ExtraSlot(academicYearForDate(date), term, day, period)].orEmpty().filter {
            val matchingCycle = it.content.cycle == SktjfCycle.WEEKLY ||
                (it.content.cycle == SktjfCycle.A && cycle == DayType.A) ||
                (it.content.cycle == SktjfCycle.B && cycle == DayType.B)
            val range = it.content.weekRange?.range
            matchingCycle && (range == null || lessonWeekApplies(range, date, it.confirmedWeeks) == true)
        }
    }

    companion object {
        internal fun create(mode: SchedulerUsageMode?, profiles: List<NittcProfileEntity>, lessons: List<SktjfExtraLessonEntity>,
            groups: List<SktjfSelectionGroupEntity> = emptyList(), states: List<SktjfSourceStateEntity> = emptyList()): SktjfExtraLessonResolver {
            if (mode != SchedulerUsageMode.NITTC) return SktjfExtraLessonResolver(emptyMap())
            require(profiles.map { it.academicYear }.distinct().size == profiles.size)
            profiles.forEach { it.validateProfile() }
            SktjfExtraLessonBackup.validateList(lessons)
            SktjfSelectionGroupBackup.validateList(groups)
            val grouped=groups.flatMap { group ->
                listOf(group.lessonIdsJson, group.retiredLessonIdsJson).flatMap { json ->
                    val ids=org.json.JSONArray(json)
                    (0 until ids.length()).map { listOf(group.academicYear.toString(),group.term,group.classId,ids.getString(it)) }
                }
            }.toSet()
            val replaced = states.flatMap { state ->
                val slots = decodeReplacedTimedSlots(state.replacedTimedSlotsJson)
                if (slots.isEmpty()) return@flatMap emptyList()
                val source = SktjfBaselineCodec.decode(state.baselineJson)
                source.lessons.lessons.filter { (_, content) -> (content.dayOfWeek to content.period) in slots }
                    .keys.map { id -> listOf(state.academicYear.toString(), state.term, state.classId, id) }
            }.toSet()
            val byYear = profiles.associate { it.academicYear to (it to it.attributes()) }
            val installed = installedSktjfSources(states)
            val result = linkedMapOf<ExtraSlot, MutableList<SktjfVisibleExtra>>()
            for (lesson in lessons) {
                if (listOf(lesson.academicYear.toString(),lesson.term,lesson.classId,lesson.sourceLessonId) in grouped ||
                    listOf(lesson.academicYear.toString(),lesson.term,lesson.classId,lesson.sourceLessonId) in replaced) continue
                val (profile, attributes) = byYear[lesson.academicYear] ?: continue
                val activeClass = installed[lesson.academicYear to lesson.term]?.classId ?: profile.classId
                if (activeClass != lesson.classId) continue
                val baseline = SktjfBaselineCodec.decode(lesson.contentJson)
                val content = baseline.lessons.lessons.getValue(lesson.id)
                if (!content.conditions.matches(attributes)) continue
                val slot = ExtraSlot(lesson.academicYear, lesson.term, content.dayOfWeek, content.period)
                result.getOrPut(slot) { mutableListOf() } += SktjfVisibleExtra(lesson.id, lesson.sourceLessonId, content, lesson.contentJson,
                    baseline.weeks.mapValues { it.value.number })
            }
            return SktjfExtraLessonResolver(result.mapValues { (_, value) -> value.sortedBy { it.id } })
        }
    }
}
