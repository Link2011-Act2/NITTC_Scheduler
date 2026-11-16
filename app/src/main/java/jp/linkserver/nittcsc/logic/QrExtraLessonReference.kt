package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.QrExtraLessonReference
import jp.linkserver.nittcsc.data.SktjfExtraLessonBackup
import jp.linkserver.nittcsc.data.SktjfExtraLessonEntity
import jp.linkserver.nittcsc.sktjf.SktjfBaselineCodec

/** A QR reference identifies content and source, never a device-local lesson ID. */
internal fun qrExtraLessonReference(lesson: SktjfExtraLessonEntity): QrExtraLessonReference {
    SktjfExtraLessonBackup.validate(lesson)
    val content = SktjfBaselineCodec.decode(lesson.contentJson).lessons.lessons.getValue(lesson.id)
    fun field(value: String) = "${value.length}:$value"
    fun list(values: Collection<String>) = "${values.size}:" + values.joinToString("") { field(it) }
    val canonical = listOf(lesson.academicYear.toString(), lesson.term, lesson.classId,
        lesson.sourceLessonId ?: "", content.dayOfWeek.toString(), content.period.toString(), content.cycle.name,
        content.subject, list(content.teachers), list(content.rooms), list(content.conditions.all.sorted()),
        list(content.conditions.none.sorted()), content.kind.name, content.remote?.toString() ?: "null")
        .joinToString("") { field(it) }
    return QrExtraLessonReference(lesson.academicYear, lesson.term, lesson.classId,
        lesson.sourceLessonId, QrShareCodec.hash(canonical.toByteArray(Charsets.UTF_8)))
}

/** Ambiguous or damaged local data never becomes an implicit match. */
internal fun qrExtraLessonReferenceMap(lessons: List<SktjfExtraLessonEntity>): Map<String, QrExtraLessonReference> =
    lessons.mapNotNull { lesson -> runCatching { lesson.id to qrExtraLessonReference(lesson) }.getOrNull() }.toMap()

internal fun qrResolveExtraLessonId(reference: QrExtraLessonReference?, references: Map<String, QrExtraLessonReference>): String? =
    reference?.let { target -> references.filterValues { it == target }.keys.singleOrNull() }
