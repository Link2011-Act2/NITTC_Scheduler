package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.DayTypeEntity
import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.QrLessonReference
import jp.linkserver.nittcsc.data.QrSharePayload
import jp.linkserver.nittcsc.data.QrShareSection
import jp.linkserver.nittcsc.data.SettingsEntity
import jp.linkserver.nittcsc.data.qrScheduleTimes

internal fun qrLessonReference(lesson: LessonEntity): QrLessonReference {
    // 長さを付け、区切り文字を含む科目名・場所でも曖昧な連結にしない。
    val fields = listOf(lesson.mode.name, lesson.weeklySubject, lesson.weeklyTeacher, lesson.weeklyLocation,
        lesson.aSubject, lesson.aTeacher, lesson.aLocation, lesson.bSubject, lesson.bTeacher, lesson.bLocation)
    val content = fields.joinToString("") { field -> field.orEmpty().trim().let { "${it.length}:$it" } }
    return QrLessonReference(lesson.academicYear, lesson.timetableTerm, lesson.dayOfWeek, lesson.slotIndex,
        QrShareCodec.hash(content.toByteArray(Charsets.UTF_8)))
}

internal fun qrResolveLessonId(reference: QrLessonReference?, lessons: Map<LessonKey, LessonEntity>): Long? {
    if (reference == null) return null
    val lesson = lessons[LessonKey(reference.academicYear, reference.timetableTerm, reference.dayOfWeek, reference.slotIndex)]
    if (lesson == null || lesson.id <= 0 || qrLessonReference(lesson) != reference) {
        throw QrShareException(QrShareFailure.LESSON_REFERENCE)
    }
    return lesson.id
}

internal fun qrCheckScheduleCompatibility(data: QrSharePayload, settings: SettingsEntity) {
    if (data.sections.none { it == QrShareSection.FIRST || it == QrShareSection.SECOND }) return
    if (settings.secondTermStartMonth != data.secondTermStartMonth || settings.secondTermStartDay != data.secondTermStartDay ||
        settings.qrScheduleTimes() != data.scheduleTimes) {
        throw QrShareException(QrShareFailure.SETTINGS)
    }
}

/** 試験日ラベルだけを更新し、他の日のA/B・休日・振替は保持する。 */
internal fun qrExamDayUpdates(existing: List<DayTypeEntity>, incoming: List<DayTypeEntity>, year: Int): List<DayTypeEntity> {
    val next = incoming.associateBy { it.date }
    val cleared = existing.filter {
        academicYearForDate(it.date) == year && it.holidaySpecialLabel?.usesExamTimetable == true && it.date !in next
    }.map { it.copy(holidaySpecialLabel = null) }
    return cleared + incoming
}
