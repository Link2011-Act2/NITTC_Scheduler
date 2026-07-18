package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode

data class LessonAutocompleteOptions(
    val subjectSuggestions: List<String>,
    val subjectTeacherCandidates: Map<String, List<String>>
)

fun buildLessonAutocompleteOptions(lessons: Collection<LessonEntity>): LessonAutocompleteOptions {
    val subjectTeacherPairs = lessons
        .flatMap { lesson ->
            when (lesson.mode) {
                LessonMode.WEEKLY -> listOf(lesson.weeklySubject to lesson.weeklyTeacher)
                LessonMode.ALTERNATING -> listOf(
                    lesson.aSubject to lesson.aTeacher,
                    lesson.bSubject to lesson.bTeacher
                )
            }
        }
        .map { (subject, teacher) -> subject.trim() to teacher.trim() }
        .filter { (subject, _) -> subject.isNotBlank() }

    return LessonAutocompleteOptions(
        subjectSuggestions = subjectTeacherPairs
            .map { it.first }
            .distinct()
            .sorted(),
        subjectTeacherCandidates = subjectTeacherPairs
            .filter { (_, teacher) -> teacher.isNotBlank() }
            .groupBy(
                keySelector = { it.first },
                valueTransform = { it.second }
            )
            .mapValues { (_, teachers) -> teachers.distinct().sorted() }
    )
}
