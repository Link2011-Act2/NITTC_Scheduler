package jp.linkserver.nittcsc.data

import jp.linkserver.nittcsc.sktjf.SktjfBaseline
import jp.linkserver.nittcsc.sktjf.SktjfBaselineCodec
import jp.linkserver.nittcsc.sktjf.SktjfCalendarBaseline
import jp.linkserver.nittcsc.sktjf.SktjfStoredBaseline
import jp.linkserver.nittcsc.sktjf.SktjfConditions
import jp.linkserver.nittcsc.sktjf.SktjfCycle
import jp.linkserver.nittcsc.sktjf.SktjfLessonContent
import jp.linkserver.nittcsc.sktjf.SktjfLessonKind
import jp.linkserver.nittcsc.sktjf.SktjfSourceLink
import jp.linkserver.nittcsc.sktjf.SktjfSourceLinks
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class SktjfSourceStateBackupTest {
    private fun state(): SktjfSourceStateEntity {
        val lesson = SktjfLessonContent(1, 2, SktjfCycle.A, "数学", emptyList(), emptyList(),
            SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null)
        val baseline = SktjfStoredBaseline(SktjfBaseline(2026, "S", "1-1", mapOf("one" to lesson)),
            SktjfCalendarBaseline(2026, "S", "1-1", emptyMap()))
        val links = SktjfSourceLinks.encode(listOf(SktjfSourceLink("one", 1, 2, SktjfCycle.A)))
        return SktjfSourceStateEntity(2026, "S", "1-1", "2026.S.0", SktjfBaselineCodec.encode(baseline), false, links)
    }

    @Test fun restorePreservesBaselineButRequiresRelinking() {
        val source = state()
        val result = SktjfSourceStateBackup.decode(SktjfSourceStateBackup.encode(listOf(source)))
        assertEquals(source.copy(requiresRelinking = true), result.single())
        val local = source.copy(baselineJson = SktjfBaselineCodec.encode(SktjfBaselineCodec.decode(source.baselineJson).copy(localFile = true)))
        assertTrue(SktjfBaselineCodec.decode(SktjfSourceStateBackup.decode(SktjfSourceStateBackup.encode(listOf(local))).single().baselineJson).localFile)
    }

    @Test fun rejectsMismatchedOrDuplicateSourceState() {
        val source = state()
        assertTrue(runCatching { SktjfSourceStateBackup.encode(listOf(source.copy(revision = "2026.F.0"))) }.isFailure)
        assertTrue(runCatching { SktjfSourceStateBackup.encode(listOf(source.copy(classId = "1-2"))) }.isFailure)
        assertTrue(runCatching { SktjfSourceStateBackup.encode(listOf(source.copy(linksJson = SktjfSourceLinks.encode(listOf(SktjfSourceLink("missing", 1, 2, SktjfCycle.A))))) ) }.isFailure)
        val encoded = SktjfSourceStateBackup.encode(listOf(source))
        val duplicate = JSONArray().put(encoded.getJSONObject(0)).put(encoded.getJSONObject(0))
        assertTrue(runCatching { SktjfSourceStateBackup.decode(duplicate) }.isFailure)
    }
}
