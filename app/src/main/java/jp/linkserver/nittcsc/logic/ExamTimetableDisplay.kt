package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.ExamLessonEntity
import jp.linkserver.nittcsc.data.HolidaySpecialLabel
import jp.linkserver.nittcsc.data.hasEnteredContent

internal enum class ExamTimetableDisplay {
    NOT_EXAM, UNSET, CONFIGURED
}

/** 試験日の指定と、その日の入力状況は別々に判定する。空きコマは日全体の未設定を意味しない。 */
internal fun examTimetableDisplay(
    enabled: Boolean,
    label: HolidaySpecialLabel?,
    lessons: Collection<ExamLessonEntity>
): ExamTimetableDisplay = when {
    !enabled || label?.usesExamTimetable != true -> ExamTimetableDisplay.NOT_EXAM
    lessons.any { it.hasEnteredContent() } -> ExamTimetableDisplay.CONFIGURED
    else -> ExamTimetableDisplay.UNSET
}
