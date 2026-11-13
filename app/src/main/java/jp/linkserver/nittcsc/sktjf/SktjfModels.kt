package jp.linkserver.nittcsc.sktjf

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

internal data class SktjfAsset(val classId: String, val name: String, val url: String, val sha256: String)
internal data class SktjfManifest(
    val academicYear: Int, val term: String, val revision: String, val publishedAt: Instant,
    val assets: List<SktjfAsset>
)
internal data class SktjfReference(val sourceId: String, val page: Int, val note: String?)
internal data class SktjfSource(val id: String, val name: String, val sha256: String, val url: String?)
internal data class SktjfPeriod(val number: Int, val start: LocalTime, val end: LocalTime)
internal enum class SktjfSlotState { PRESENT, EMPTY, EXCLUDED }
internal data class SktjfSlot(
    val dayOfWeek: Int, val period: Int, val state: SktjfSlotState,
    val containsExcluded: Boolean, val sources: List<SktjfReference>
)
internal enum class SktjfCycle { WEEKLY, A, B }
internal enum class SktjfLessonKind { NORMAL, MAKEUP, EXTENSION }
internal data class SktjfConditions(val all: Set<String>, val none: Set<String>) {
    fun matches(attributes: Set<String>): Boolean = attributes.containsAll(all) && none.none(attributes::contains)
}
internal data class SktjfLesson(
    val id: String, val dayOfWeek: Int, val period: Int, val cycle: SktjfCycle,
    val subject: String, val teachers: List<String>, val rooms: List<String>,
    val conditions: SktjfConditions, val kind: SktjfLessonKind, val remote: Boolean?,
    val sources: List<SktjfReference>
)
internal enum class SktjfDayType { A, B, HOLIDAY }
internal enum class SktjfEventKind { MIDTERM, FINAL, EXCURSION, EVENT, EXAM_RETURN, OTHER }
internal data class SktjfEvent(val name: String, val kind: SktjfEventKind?)
internal data class SktjfCalendarDay(
    val date: LocalDate, val dayType: SktjfDayType?, val overrideDayOfWeek: Int?,
    val overrideDayType: SktjfDayType?, val event: SktjfEvent?, val sources: List<SktjfReference>
)
internal data class SktjfDocument(
    val academicYear: Int, val term: String, val start: LocalDate, val end: LocalDate,
    val classId: String, val revision: String, val sources: List<SktjfSource>,
    val periods: List<SktjfPeriod>, val slotCoverage: List<SktjfSlot>,
    val lessons: List<SktjfLesson>, val calendar: List<SktjfCalendarDay>
) {
    fun lessonsFor(attributes: Set<String>): List<SktjfLesson> = lessons.filter { it.conditions.matches(attributes) }
}
