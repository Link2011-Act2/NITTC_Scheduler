package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.ChangedLessonEntity
import jp.linkserver.nittcsc.data.ResolvedLesson

private const val SUPPLEMENTARY_LESSON_SUBJECT = "補講"

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
    return this == null || subject.trim() == SUPPLEMENTARY_LESSON_SUBJECT
}
