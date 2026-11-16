package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.ChangedLessonEntity
import jp.linkserver.nittcsc.data.ResolvedLesson

private val NO_LESSON_STYLE_SUBJECTS = setOf("補講", "延長枠")

fun applyChangedLesson(
    baseLesson: ResolvedLesson?,
    changedLesson: ChangedLessonEntity?
): ResolvedLesson? {
    return changedLesson?.let {
        ResolvedLesson(
            subject = it.subject,
            teacher = it.teacher,
            location = it.location
        )
    } ?: baseLesson
}

fun ResolvedLesson?.usesNoLessonAppearance(): Boolean {
    return this == null || subject.trim() in NO_LESSON_STYLE_SUBJECTS
}
