package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.NittcProfileEntity
import jp.linkserver.nittcsc.data.SchedulerUsageMode
import jp.linkserver.nittcsc.data.SktjfSourceStateEntity
import jp.linkserver.nittcsc.data.NittcYearTransitionEntity
import jp.linkserver.nittcsc.data.NittcYearOutcome
import jp.linkserver.nittcsc.logic.nittcGraduatedForYear

internal data class SktjfUpdateSnapshot(val mode: SchedulerUsageMode?, val profiles: List<NittcProfileEntity>,
    val sourceStates: List<SktjfSourceStateEntity>, val yearTransitions: List<NittcYearTransitionEntity> = emptyList())
internal data class SktjfAutomaticOffer(val key: SktjfUpdateKey, val kind: SktjfUpdateKind, val document: SktjfDocument,
    val timed: SktjfV2Candidate? = null, val selections: SktjfV3Candidate? = null)
internal fun SktjfSourceStateEntity.isLocalSktjfSource(): Boolean =
    runCatching { SktjfBaselineCodec.decode(baselineJson).localFile }.getOrDefault(false)
internal fun SktjfSourceStateEntity?.officialSktjfRevision(): String? =
    this?.takeUnless { it.isLocalSktjfSource() }?.revision
internal sealed interface SktjfAutomaticCheckResult {
    data class Available(val offer: SktjfAutomaticOffer) : SktjfAutomaticCheckResult
    data class NeedsAffiliation(val year: Int, val revision: String) : SktjfAutomaticCheckResult
    data object NoUpdate : SktjfAutomaticCheckResult
    data object Failed : SktjfAutomaticCheckResult
    data object Stale : SktjfAutomaticCheckResult
}

/** Never write a timetable or acknowledge an offer while checking the network. */
internal suspend fun checkSktjfUpdates(
    year: Int, preferredTerm: String,
    snapshot: suspend () -> SktjfUpdateSnapshot,
    acknowledged: (SktjfUpdateKey) -> String?,
    discover: suspend (Int, String) -> SktjfDiscoveryResult,
    fetch: suspend (String, String) -> SktjfFetchResult,
    manifest: suspend (String) -> SktjfManifest? = { null }
): SktjfAutomaticCheckResult {
    require(year in 1900..9997 && preferredTerm in setOf("S", "F"))
    val before = snapshot()
    if (before.mode != SchedulerUsageMode.NITTC) return SktjfAutomaticCheckResult.NoUpdate
    for (targetYear in listOf(year, year + 1)) {
        if (nittcGraduatedForYear(targetYear, before.profiles, before.yearTransitions)) continue
        val profile = before.profiles.singleOrNull { it.academicYear == targetYear }
        if (profile == null) {
            if (before.yearTransitions.any { it.academicYear == targetYear && it.outcome == NittcYearOutcome.DEFERRED.name }) continue
            if (targetYear == year + 1 && before.profiles.any { it.academicYear == year }) {
                when (val found = discover(targetYear, "S")) {
                    is SktjfDiscoveryResult.Found -> {
                        val value = manifest(found.revision) ?: return SktjfAutomaticCheckResult.Failed
                        if (value.academicYear != targetYear || value.term != "S" || value.revision != found.revision)
                            return SktjfAutomaticCheckResult.Failed
                        if (snapshot() != before) return SktjfAutomaticCheckResult.Stale
                        return SktjfAutomaticCheckResult.NeedsAffiliation(targetYear, found.revision)
                    }
                    SktjfDiscoveryResult.NotPublished -> Unit
                    else -> return SktjfAutomaticCheckResult.Failed
                }
            }
            continue
        }
        if (!isSktjfUndergraduateClass(profile.classId)) continue
        val terms = if (targetYear > year || preferredTerm == "S") listOf("S", "F") else listOf("F", "S")
        for (term in terms) {
            val key = SktjfUpdateKey(targetYear, term, profile.classId)
            val source = before.sourceStates.singleOrNull { it.academicYear == targetYear && it.term == term && it.classId == profile.classId }
            val discovery = discover(targetYear, term)
            if (discovery == SktjfDiscoveryResult.NotPublished) continue
            val revision = (discovery as? SktjfDiscoveryResult.Found)?.revision ?: return SktjfAutomaticCheckResult.Failed
            val decision = sktjfNoticeDecision(key, revision, source.officialSktjfRevision(), acknowledged(key))
            if (decision == SktjfNoticeDecision.Invalid) return SktjfAutomaticCheckResult.Failed
            if (decision !is SktjfNoticeDecision.Offer) continue
            val result = fetch(revision, profile.classId)
            if (result == SktjfFetchResult.ClassNotFound) continue
            val success = result as? SktjfFetchResult.Success ?: return SktjfAutomaticCheckResult.Failed
            val doc = success.document
            if (doc.academicYear != key.academicYear || doc.term != key.term || doc.classId != key.classId || doc.revision != revision)
                return SktjfAutomaticCheckResult.Failed
            if (snapshot() != before) return SktjfAutomaticCheckResult.Stale
            return SktjfAutomaticCheckResult.Available(SktjfAutomaticOffer(key, decision.kind, doc, success.timed, success.selections))
        }
    }
    return if (snapshot() == before) SktjfAutomaticCheckResult.NoUpdate else SktjfAutomaticCheckResult.Stale
}
