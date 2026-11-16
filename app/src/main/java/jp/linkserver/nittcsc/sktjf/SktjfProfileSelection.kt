package jp.linkserver.nittcsc.sktjf

/** Resolve distribution conditions before mapping into the ordinary editable timetable. */
internal fun selectSktjfLessons(document: SktjfDocument, attributes: Set<String>): SktjfDocument? {
    val selected = document.lessonsFor(attributes).map { lesson ->
        lesson.copy(
            teachers = lesson.teachers.joinToString("、").let { if (it.isEmpty()) emptyList() else listOf(it) },
            rooms = lesson.rooms.joinToString("、").let { if (it.isEmpty()) emptyList() else listOf(it) },
            conditions = SktjfConditions(emptySet(), emptySet())
        )
    }
    for (slot in selected.groupBy { it.dayOfWeek to it.period }.values) {
        if (slot.size > 2 || slot.count { it.cycle == SktjfCycle.WEEKLY } > 1 ||
            slot.count { it.cycle == SktjfCycle.A } > 1 || slot.count { it.cycle == SktjfCycle.B } > 1 ||
            (slot.size > 1 && slot.any { it.cycle == SktjfCycle.WEEKLY })) return null
    }
    return document.copy(lessons = selected)
}
