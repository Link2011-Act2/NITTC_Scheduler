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
import java.time.LocalDate
import jp.linkserver.nittcsc.sktjf.SktjfV2Week
import jp.linkserver.nittcsc.sktjf.SktjfReference
import jp.linkserver.nittcsc.sktjf.SktjfV2WeekSeries

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
        assertEquals(source, SktjfSourceStateBackup.decode(SktjfSourceStateBackup.encode(listOf(source)), preserveLinkage = true).single())
        val stale = source.copy(requiresRelinking = true)
        assertEquals(stale, SktjfSourceStateBackup.decode(SktjfSourceStateBackup.encode(listOf(stale)), preserveLinkage = true).single())
        val older = SktjfSourceStateBackup.encode(listOf(source)).also { it.getJSONObject(0).remove("requiresRelinking") }
        assertTrue(SktjfSourceStateBackup.decode(older, preserveLinkage = true).single().requiresRelinking)
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

    @Test fun sourceSnapshotBackupKeepsConfirmedWeekAndReference() {
        val original=state()
        val baseline=SktjfBaselineCodec.decode(original.baselineJson).copy(
            weeks=mapOf(LocalDate.of(2026,4,6) to SktjfV2Week(9,listOf(SktjfReference("src",1,null)))))
        val updated=original.copy(baselineJson=SktjfBaselineCodec.encode(baseline))
        val restored=SktjfSourceStateBackup.decode(SktjfSourceStateBackup.encode(listOf(updated))).single()
        assertEquals(baseline,SktjfBaselineCodec.decode(restored.baselineJson))
        assertTrue(restored.requiresRelinking)
    }

    @Test fun sourceSnapshotBackupKeepsOpportunitySeries() {
        val original = state()
        val baseline = SktjfBaselineCodec.decode(original.baselineJson).copy(series = listOf(
            SktjfV2WeekSeries("shared", 1, setOf(2), setOf("one"), SktjfCycle.A, listOf(SktjfReference("src", 1, null)))))
        val updated = original.copy(baselineJson = SktjfBaselineCodec.encode(baseline))
        val restored = SktjfSourceStateBackup.decode(SktjfSourceStateBackup.encode(listOf(updated))).single()
        assertEquals(baseline, SktjfBaselineCodec.decode(restored.baselineJson))
        assertTrue(restored.requiresRelinking)
    }
}
