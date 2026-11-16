package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.ResolvedLesson
import jp.linkserver.nittcsc.data.SchedulerUsageMode

internal data class SktjfLessonOrigin(val kind: SktjfLessonKind, val originalSubject: String)

/** Apply to the effective lesson after date-specific edits; a manual lesson has no origin. */
internal fun shouldNotifySktjfLesson(
    mode: SchedulerUsageMode?, origin: SktjfLessonOrigin?, effectiveLesson: ResolvedLesson
): Boolean {
    if (mode != SchedulerUsageMode.NITTC || origin == null) return true
    val specialSubject = when (origin.kind) {
        SktjfLessonKind.MAKEUP -> "補講"
        SktjfLessonKind.EXTENSION -> "延長枠"
        SktjfLessonKind.NORMAL -> return true
    }
    return origin.originalSubject.trim() != specialSubject || effectiveLesson.subject.trim() != specialSubject
}
