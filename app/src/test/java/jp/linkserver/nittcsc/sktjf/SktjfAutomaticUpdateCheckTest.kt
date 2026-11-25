package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.NittcProfileEntity
import jp.linkserver.nittcsc.data.SchedulerUsageMode
import jp.linkserver.nittcsc.data.SktjfSourceStateEntity
import jp.linkserver.nittcsc.data.NittcYearOutcome
import jp.linkserver.nittcsc.data.NittcYearTransitionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SktjfAutomaticUpdateCheckTest {
    private val profile = NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)
    private val base = SktjfUpdateSnapshot(SchedulerUsageMode.NITTC, listOf(profile), emptyList())
    private fun document(term: String = "S", revision: String = "2026.S.0") = SktjfDocument(2026, term,
        LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 6), "1-1", revision,
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    private fun success(doc: SktjfDocument) = SktjfFetchResult.Success(
        SktjfManifest(2026, doc.term, doc.revision, Instant.parse("2026-04-01T00:00:00Z"), emptyList()), doc)

    @Test fun genericModeNeverContactsDistribution() = runBlocking {
        var calls = 0
        val result = checkSktjfUpdates(2026, "S", { base.copy(mode = SchedulerUsageMode.GENERIC) }, { null },
            { _, _ -> calls++; SktjfDiscoveryResult.NotPublished }, { _, _ -> error("unexpected") })
        assertEquals(SktjfAutomaticCheckResult.NoUpdate, result)
        assertEquals(0, calls)
    }

    @Test fun timedCandidateRemainsAttachedToAutomaticOffer() = runBlocking {
        val doc = document()
        val candidate = SktjfV2Candidate(doc, emptyMap(), emptyMap())
        val result = checkSktjfUpdates(2026, "S", { base }, { null },
            { _, _ -> SktjfDiscoveryResult.Found("2026.S.0") },
            { _, _ -> success(doc).copy(timed = candidate) })
        assertEquals(candidate, (result as SktjfAutomaticCheckResult.Available).offer.timed)
    }

    @Test fun offersOnlyVerifiedUnacknowledgedNewerRevision() = runBlocking {
        val applied = SktjfSourceStateEntity(2026, "S", "1-1", "2026.S.1", "{}", false)
        val state = base.copy(sourceStates = listOf(applied))
        val result = checkSktjfUpdates(2026, "S", { state }, { "2026.S.1" },
            { year, term -> if (year == 2026 && term == "S") SktjfDiscoveryResult.Found("2026.S.2") else SktjfDiscoveryResult.NotPublished },
            { _, _ -> success(document(revision = "2026.S.2")) })
        val offer = (result as SktjfAutomaticCheckResult.Available).offer
        assertEquals(SktjfUpdateKind.REVISION, offer.kind)
        assertEquals("2026.S.2", offer.document.revision)
        val handled = checkSktjfUpdates(2026, "S", { state }, { "2026.S.2" },
            { year, term -> if (year == 2026 && term == "S") SktjfDiscoveryResult.Found("2026.S.2") else SktjfDiscoveryResult.NotPublished },
            { _, _ -> error("acknowledged version must not be downloaded") })
        assertEquals(SktjfAutomaticCheckResult.NoUpdate, handled)
    }

    @Test fun localFileDoesNotHideAnOfficialReleaseWithSameOrLowerRevision() = runBlocking {
        val local = SktjfStoredBaseline(SktjfBaseline(2026, "S", "1-1", emptyMap()),
            SktjfCalendarBaseline(2026, "S", "1-1", emptyMap()), localFile = true)
        val state = base.copy(sourceStates = listOf(SktjfSourceStateEntity(2026, "S", "1-1", "2026.S.5",
            SktjfBaselineCodec.encode(local), false)))
        val result = checkSktjfUpdates(2026, "S", { state }, { null },
            { year, term -> if (year == 2026 && term == "S") SktjfDiscoveryResult.Found("2026.S.0") else SktjfDiscoveryResult.NotPublished },
            { _, _ -> success(document()) })
        assertEquals("2026.S.0", (result as SktjfAutomaticCheckResult.Available).offer.document.revision)
    }

    @Test fun failedFetchAndChangedProfileCannotProduceAnOffer() = runBlocking {
        val failed = checkSktjfUpdates(2026, "S", { base }, { null },
            { _, _ -> SktjfDiscoveryResult.Found("2026.S.0") }, { _, _ -> SktjfFetchResult.InvalidData })
        assertEquals(SktjfAutomaticCheckResult.Failed, failed)
        var reads = 0
        val stale = checkSktjfUpdates(2026, "S", { if (reads++ == 0) base else base.copy(profiles = listOf(profile.copy(classId = "1-2"))) },
            { null }, { _, _ -> SktjfDiscoveryResult.Found("2026.S.0") }, { _, _ -> success(document()) })
        assertEquals(SktjfAutomaticCheckResult.Stale, stale)
    }

    @Test fun wrongDocumentIdentityIsRejectedAndStaleProvenanceDoesNotBlockDiscovery() = runBlocking {
        val wrong = checkSktjfUpdates(2026, "S", { base }, { null },
            { _, _ -> SktjfDiscoveryResult.Found("2026.S.0") }, { _, _ -> success(document().copy(classId = "1-2")) })
        assertEquals(SktjfAutomaticCheckResult.Failed, wrong)
        val source = SktjfSourceStateEntity(2026, "S", "1-1", "2026.S.0", "{}", true)
        var sCalls = 0
        val skipped = checkSktjfUpdates(2026, "S", { base.copy(sourceStates = listOf(source)) }, { null },
            { year, term -> if (year == 2026 && term == "S") sCalls++; SktjfDiscoveryResult.NotPublished }, { _, _ -> error("unexpected") })
        assertEquals(SktjfAutomaticCheckResult.NoUpdate, skipped)
        assertEquals(1, sCalls)
    }

    @Test fun newerReleaseIsDetectedEvenWhenThePreviousSourceNeedsReview() = runBlocking {
        val source = SktjfSourceStateEntity(2026, "S", "1-1", "2026.S.0", "{}", true)
        val result = checkSktjfUpdates(2026, "S", { base.copy(sourceStates = listOf(source)) }, { null },
            { year, term -> if (year == 2026 && term == "S") SktjfDiscoveryResult.Found("2026.S.1") else SktjfDiscoveryResult.NotPublished },
            { _, _ -> success(document(revision = "2026.S.1")) })
        assertEquals("2026.S.1", (result as SktjfAutomaticCheckResult.Available).offer.document.revision)
    }

    @Test fun nextAcademicYearStartsWithFirstTermEvenDuringCurrentSecondTerm() = runBlocking {
        val next = profile.copy(academicYear = 2027, grade = 2, classId = "2M")
        val calls = mutableListOf<Pair<Int, String>>()
        val result = checkSktjfUpdates(2026, "F", { base.copy(profiles = listOf(next)) }, { null },
            { year, term -> calls += year to term; SktjfDiscoveryResult.NotPublished }, { _, _ -> error("unexpected") })
        assertEquals(SktjfAutomaticCheckResult.NoUpdate, result)
        assertEquals(listOf(2027 to "S", 2027 to "F"), calls)
    }

    @Test fun nextYearWithoutProfileRequiresVerifiedManifest() = runBlocking {
        val nextManifest = SktjfManifest(2027, "S", "2027.S.0", Instant.parse("2027-02-01T00:00:00Z"), emptyList())
        suspend fun check(manifest: SktjfManifest?) = checkSktjfUpdates(2026, "S", { base }, { null },
            { year, _ -> if (year == 2027) SktjfDiscoveryResult.Found("2027.S.0") else SktjfDiscoveryResult.NotPublished },
            { _, _ -> error("class must not be guessed") }, { manifest })
        assertEquals(SktjfAutomaticCheckResult.NeedsAffiliation(2027, "2027.S.0"), check(nextManifest))
        assertEquals(SktjfAutomaticCheckResult.Failed, check(null))
        assertEquals(SktjfAutomaticCheckResult.Failed, check(nextManifest.copy(academicYear = 2026)))
    }

    @Test fun graduationDoesNotRequestFutureYearDistribution() = runBlocking {
        val graduated = base.copy(yearTransitions = listOf(NittcYearTransitionEntity(2027, NittcYearOutcome.GRADUATED.name)))
        val years = mutableListOf<Int>()
        val result = checkSktjfUpdates(2026, "S", { graduated }, { null },
            { year, _ -> years += year; SktjfDiscoveryResult.NotPublished }, { _, _ -> error("unexpected") })
        assertEquals(SktjfAutomaticCheckResult.NoUpdate, result)
        assertEquals(listOf(2026, 2026), years)
    }

    @Test fun restoredDeferralDoesNotRepeatNewYearPrompt() = runBlocking {
        val deferred = base.copy(yearTransitions = listOf(NittcYearTransitionEntity(2027, NittcYearOutcome.DEFERRED.name)))
        val years = mutableListOf<Int>()
        val result = checkSktjfUpdates(2026, "S", { deferred }, { null },
            { year, _ -> years += year; SktjfDiscoveryResult.NotPublished }, { _, _ -> error("unexpected") })
        assertEquals(SktjfAutomaticCheckResult.NoUpdate, result)
        assertEquals(listOf(2026, 2026), years)
    }
}
