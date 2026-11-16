package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.DayType
import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.NittcProfileEntity
import jp.linkserver.nittcsc.data.SchedulerUsageMode
import jp.linkserver.nittcsc.data.SktjfExtraLessonBackup
import jp.linkserver.nittcsc.data.SktjfExtraLessonEntity
import jp.linkserver.nittcsc.data.attributes
import jp.linkserver.nittcsc.data.validateProfile
import jp.linkserver.nittcsc.logic.TimetableTerm
import jp.linkserver.nittcsc.logic.academicYearForDate
import jp.linkserver.nittcsc.logic.timetableTermForDate
import java.time.LocalDate

internal data class SktjfVisibleExtra(val id: String, val sourceLessonId: String?, val content: SktjfLessonContent, val storedContentJson: String = "")
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
            it.content.cycle == SktjfCycle.WEEKLY ||
                (it.content.cycle == SktjfCycle.A && cycle == DayType.A) ||
                (it.content.cycle == SktjfCycle.B && cycle == DayType.B)
        }
    }

    companion object {
        internal fun create(mode: SchedulerUsageMode?, profiles: List<NittcProfileEntity>, lessons: List<SktjfExtraLessonEntity>): SktjfExtraLessonResolver {
            if (mode != SchedulerUsageMode.NITTC) return SktjfExtraLessonResolver(emptyMap())
            require(profiles.map { it.academicYear }.distinct().size == profiles.size)
            profiles.forEach { it.validateProfile() }
            SktjfExtraLessonBackup.validateList(lessons)
            val byYear = profiles.associate { it.academicYear to (it to it.attributes()) }
            val result = linkedMapOf<ExtraSlot, MutableList<SktjfVisibleExtra>>()
            for (lesson in lessons) {
                val (profile, attributes) = byYear[lesson.academicYear] ?: continue
                if (profile.classId != lesson.classId) continue
                val content = SktjfBaselineCodec.decode(lesson.contentJson).lessons.lessons.getValue(lesson.id)
                if (!content.conditions.matches(attributes)) continue
                val slot = ExtraSlot(lesson.academicYear, lesson.term, content.dayOfWeek, content.period)
                result.getOrPut(slot) { mutableListOf() } += SktjfVisibleExtra(lesson.id, lesson.sourceLessonId, content, lesson.contentJson)
            }
            return SktjfExtraLessonResolver(result.mapValues { (_, value) -> value.sortedBy { it.id } })
        }
    }
}
