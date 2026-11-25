package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.logic.normalizeSubjectRomanNumerals

internal data class SktjfProfileSlotOptions(val day: Int, val period: Int, val lessons: List<SktjfLesson>)

private fun List<SktjfLesson>.hasSlotCollision(): Boolean =
    size > 2 || count { it.cycle == SktjfCycle.WEEKLY } > 1 ||
        count { it.cycle == SktjfCycle.A } > 1 || count { it.cycle == SktjfCycle.B } > 1 ||
        (size > 1 && any { it.cycle == SktjfCycle.WEEKLY })

/** Profile-filtered alternatives that need a person's choice for this timetable slot. */
internal fun sktjfProfileSlotOptions(document: SktjfDocument, attributes: Set<String>): List<SktjfProfileSlotOptions> =
    document.lessonsFor(attributes).groupBy { it.dayOfWeek to it.period }.mapNotNull { (slot, lessons) ->
        if (lessons.hasSlotCollision()) SktjfProfileSlotOptions(slot.first, slot.second, lessons) else null
    }.sortedWith(compareBy({ it.day }, { it.period }))

/** Resolve distribution conditions before mapping into the ordinary editable timetable. */
internal fun selectSktjfLessons(document: SktjfDocument, attributes: Set<String>,
    chosenAlternatives: Set<String>? = null): SktjfDocument? {
    val options = sktjfProfileSlotOptions(document, attributes)
    val optionIds = options.flatMap { it.lessons }.map { it.id }
    if (optionIds.distinct().size != optionIds.size) return null
    if (options.isNotEmpty() && chosenAlternatives == null) return null
    val chosen = chosenAlternatives.orEmpty()
    if (chosen.any { it !in optionIds }) return null
    for (slot in options) {
        val selected = slot.lessons.filter { it.id in chosen }
        if (selected.isEmpty() || selected.hasSlotCollision()) return null
        // An unambiguous A or B alternative must not be dropped as a side effect.
        for (cycle in listOf(SktjfCycle.A, SktjfCycle.B)) {
            if (slot.lessons.any { it.cycle == cycle } && selected.none { it.cycle == cycle || it.cycle == SktjfCycle.WEEKLY }) return null
        }
    }
    val ambiguousIds = optionIds.toSet()
    val selected = document.lessonsFor(attributes).map { lesson ->
        lesson.copy(
            subject = normalizeSubjectRomanNumerals(lesson.subject),
            teachers = lesson.teachers.joinToString("、").let { if (it.isEmpty()) emptyList() else listOf(it) },
            rooms = lesson.rooms.joinToString("、").let { if (it.isEmpty()) emptyList() else listOf(it) },
            conditions = SktjfConditions(emptySet(), emptySet())
        )
    }.filter { it.id !in ambiguousIds || it.id in chosen }
    for (slot in selected.groupBy { it.dayOfWeek to it.period }.values) {
        if (slot.hasSlotCollision()) return null
    }
    return document.copy(lessons = selected)
}
