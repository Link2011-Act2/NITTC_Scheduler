package jp.linkserver.nittcsc.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class SktjfThreeWayMergeTest {
    @Test fun unchangedLocalAcceptsUpdatedDistribution() {
        assertEquals(SktjfMergeDecision.Apply("new"), mergeSktjfValue("old", "old", "new"))
    }

    @Test fun unchangedDistributionPreservesLocalEdit() {
        assertEquals(SktjfMergeDecision.Keep("edited"), mergeSktjfValue("old", "edited", "old"))
    }

    @Test fun bothChangedRequiresAChoice() {
        assertEquals(SktjfMergeDecision.Conflict("old", "edited", "new"), mergeSktjfValue("old", "edited", "new"))
    }

    @Test fun omittedDistributionNeverErasesLocalContent() {
        assertEquals(SktjfMergeDecision.Keep("added"), mergeSktjfValue(null, "added", null))
        assertEquals(SktjfMergeDecision.Keep("edited"), mergeSktjfValue("old", "edited", null))
        assertEquals(SktjfMergeDecision.Keep("old"), mergeSktjfValue("old", "old", null))
    }

    @Test fun firstImportProtectsExistingContent() {
        assertEquals(SktjfMergeDecision.Apply("new"), mergeSktjfValue(null, null, "new"))
        assertEquals(SktjfMergeDecision.Conflict(null, "local", "new"), mergeSktjfValue(null, "local", "new"))
        assertEquals(SktjfMergeDecision.Keep("new"), mergeSktjfValue(null, "new", "new"))
    }

    @Test fun localDeletionConflictsWithChangedDistribution() {
        assertEquals(SktjfMergeDecision.Conflict("old", null, "new"), mergeSktjfValue("old", null, "new"))
        assertEquals(SktjfMergeDecision.Keep(null), mergeSktjfValue("old", null, "old"))
    }
}
