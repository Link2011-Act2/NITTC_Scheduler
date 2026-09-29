package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.LessonEntity

/** Blank lesson rows are created in advance, so only entered subjects count as configured. */
fun unconfiguredSecondTermYears(lessons: Collection<LessonEntity>): Set<Int> {
    val configured = lessons.asSequence()
        .filter { lesson ->
            lesson.weeklySubject.isNotBlank() || lesson.aSubject.isNotBlank() ||
                lesson.bSubject.isNotBlank()
        }
        .groupBy { it.academicYear to it.timetableTerm }
    return configured.keys.asSequence()
        .filter { it.second == TimetableTerm.FIRST }
        .map { it.first }
        .filter { year -> (year to TimetableTerm.SECOND) !in configured }
        .toSet()
}
