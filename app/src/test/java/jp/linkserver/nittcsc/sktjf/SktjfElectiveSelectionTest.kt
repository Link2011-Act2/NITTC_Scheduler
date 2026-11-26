package jp.linkserver.nittcsc.sktjf

import org.junit.Assert.assertEquals
import org.junit.Test

class SktjfElectiveSelectionTest {
    @Test fun selectedAndUnknownChoicesPreserveAllCandidates() {
        val candidates = listOf("a", "b", "c")
        assertEquals(SktjfElectiveResolution.Chosen("b"), resolveSktjfElective(candidates, candidates, "b"))
        assertEquals(SktjfElectiveResolution.Undecided(candidates), resolveSktjfElective(candidates, candidates, null))
        assertEquals(SktjfElectiveResolution.Chosen("a"), resolveSktjfElective(candidates, listOf("a"), null))
        assertEquals(SktjfElectiveResolution.Empty, resolveSktjfElective(candidates, emptyList(), null))
        assertEquals(SktjfElectiveResolution.Empty, resolveSktjfElective(candidates, listOf("a"), "b"))
    }

    @Test fun removedSelectionRequiresReviewEvenIfOnlyOneCandidateRemains() {
        assertEquals(SktjfElectiveResolution.NeedsReview("old", listOf("new")),
            resolveSktjfElective(listOf("new"), listOf("new"), "old"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateCandidateIdsAreInvalid() { resolveSktjfElective(listOf("a", "a"), listOf("a"), null) }
}
