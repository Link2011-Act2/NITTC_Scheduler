package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.SktjfExtraLessonEntity
import jp.linkserver.nittcsc.data.SktjfSourceStateEntity
import org.junit.Assert.*
import org.junit.Test

class SktjfLegacyExtraMigrationTest {
    private val content = SktjfLessonContent(1, 1, SktjfCycle.B, "保体Ⅰ", listOf("先生"), emptyList(),
        SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null)
    private fun baseline(lessons: Map<String, SktjfLessonContent>) = SktjfStoredBaseline(
        SktjfBaseline(2026, "F", "1-4", lessons), SktjfCalendarBaseline(2026, "F", "1-4", emptyMap()))
    private fun state() = SktjfSourceStateEntity(2026, "F", "1-4", "2026.F.0",
        SktjfBaselineCodec.encode(baseline(mapOf("source" to content))), false, "[]")
    private fun extra(value: SktjfLessonContent = content, source: String? = "source") = SktjfExtraLessonEntity(
        "extra", 2026, "F", "1-4", source, SktjfBaselineCodec.encode(baseline(mapOf("extra" to value))))

    @Test fun uneditedUnreferencedDistributionRecordCanBeReconciled() {
        val result = prepareLegacyExtraMigration(state(), listOf(extra()), emptySet())!!
        assertTrue(SktjfBaselineCodec.decode(result.baselineJson).lessons.lessons.isEmpty())
    }

    @Test fun neverRemovesEditedReferencedOrManualRecords() {
        assertNull(prepareLegacyExtraMigration(state(), listOf(extra()), setOf("extra")))
        assertNull(prepareLegacyExtraMigration(state(), listOf(extra(content.copy(subject = "手動編集"))), emptySet()))
        assertNull(prepareLegacyExtraMigration(state(), listOf(extra(source = null)), emptySet()))
        assertNull(prepareLegacyExtraMigration(null, listOf(extra()), emptySet()))
    }
}
