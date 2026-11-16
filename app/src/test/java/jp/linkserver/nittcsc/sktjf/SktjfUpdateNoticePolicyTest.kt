package jp.linkserver.nittcsc.sktjf

import org.junit.Assert.assertEquals
import org.junit.Test

class SktjfUpdateNoticePolicyTest {
    private val key = SktjfUpdateKey(2026, "S", "1-1")

    @Test fun initialAndRevisionsAreComparedNumerically() {
        assertEquals(SktjfNoticeDecision.Offer(SktjfUpdateKind.INITIAL), sktjfNoticeDecision(key, "2026.S.0", null, null))
        assertEquals(SktjfNoticeDecision.Offer(SktjfUpdateKind.REVISION), sktjfNoticeDecision(key, "2026.S.10", "2026.S.9", null))
        assertEquals(SktjfNoticeDecision.AlreadyHandled, sktjfNoticeDecision(key, "2026.S.9", "2026.S.10", null))
    }

    @Test fun dismissalSuppressesSameAndOlderButNotNewerRevision() {
        assertEquals(SktjfNoticeDecision.AlreadyHandled, sktjfNoticeDecision(key, "2026.S.1", null, "2026.S.1"))
        assertEquals(SktjfNoticeDecision.AlreadyHandled, sktjfNoticeDecision(key, "2026.S.0", null, "2026.S.1"))
        assertEquals(SktjfNoticeDecision.Offer(SktjfUpdateKind.REVISION), sktjfNoticeDecision(key, "2026.S.2", null, "2026.S.1"))
    }

    @Test fun mismatchedOrMalformedVersionsDoNotProduceNotices() {
        assertEquals(SktjfNoticeDecision.Invalid, sktjfNoticeDecision(key, "2026.F.1", null, null))
        assertEquals(SktjfNoticeDecision.Invalid, sktjfNoticeDecision(key, "2026.S.01", null, null))
        assertEquals(SktjfNoticeDecision.Invalid, sktjfNoticeDecision(key, "2026.S.1", "2025.S.0", null))
        assertEquals(SktjfNoticeDecision.Invalid, sktjfNoticeDecision(key.copy(classId = "2-M"), "2026.S.0", null, null))
    }
}
