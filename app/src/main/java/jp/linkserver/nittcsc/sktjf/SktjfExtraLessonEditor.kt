package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.SktjfExtraLessonBackup
import jp.linkserver.nittcsc.data.SktjfExtraLessonEntity

internal data class SktjfExtraLessonDraft(val subject: String, val teachers: String, val rooms: String)

/** Modify only the named lesson, retaining its identity, source and applicability. */
internal fun editSktjfExtraLesson(original: SktjfExtraLessonEntity, draft: SktjfExtraLessonDraft): SktjfExtraLessonEntity {
    SktjfExtraLessonBackup.validate(original)
    val stored = SktjfBaselineCodec.decode(original.contentJson)
    val before = stored.lessons.lessons.getValue(original.id)
    val subject = draft.subject.trim(); require(subject.isNotEmpty() && subject.length <= 1000)
    fun lines(value: String) = value.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        .also { require(it.size <= 64 && it.all { line -> line.length <= 1000 }) }
    val after = before.copy(subject = subject, teachers = lines(draft.teachers), rooms = lines(draft.rooms),
        kind = if (subject == before.subject) before.kind else SktjfLessonKind.NORMAL)
    val updated = original.copy(contentJson = SktjfBaselineCodec.encode(stored.copy(lessons = stored.lessons.copy(lessons = mapOf(original.id to after)))))
    SktjfExtraLessonBackup.validate(updated)
    return updated
}
