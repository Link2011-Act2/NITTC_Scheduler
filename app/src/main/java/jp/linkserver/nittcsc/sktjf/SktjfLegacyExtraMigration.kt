package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.SktjfExtraLessonBackup
import jp.linkserver.nittcsc.data.SktjfExtraLessonEntity
import jp.linkserver.nittcsc.data.SktjfSourceStateBackup
import jp.linkserver.nittcsc.data.SktjfSourceStateEntity

/** Prepare removal only when every old extra is an unedited, unreferenced distribution record. */
internal fun prepareLegacyExtraMigration(
    state: SktjfSourceStateEntity?, extras: List<SktjfExtraLessonEntity>, referencedIds: Set<String>
): SktjfSourceStateEntity? = runCatching {
    require(state != null && extras.isNotEmpty())
    SktjfSourceStateBackup.validate(state)
    SktjfExtraLessonBackup.validateList(extras)
    val baseline = SktjfBaselineCodec.decode(state.baselineJson)
    val removedSources = extras.map { extra ->
        require(extra.academicYear == state.academicYear && extra.term == state.term && extra.classId == state.classId)
        require(extra.id !in referencedIds)
        val source = requireNotNull(extra.sourceLessonId)
        val content = SktjfBaselineCodec.decode(extra.contentJson).lessons.lessons.getValue(extra.id)
        require(baseline.lessons.lessons[source] == content)
        source
    }.toSet()
    val links = SktjfSourceLinks.decode(state.linksJson)
    require(links.none { it.sourceLessonId in removedSources })
    val updated = baseline.copy(lessons = baseline.lessons.copy(lessons = baseline.lessons.lessons - removedSources))
    state.copy(baselineJson = SktjfBaselineCodec.encode(updated))
}.getOrNull()
