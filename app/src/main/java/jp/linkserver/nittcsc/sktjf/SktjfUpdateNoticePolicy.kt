package jp.linkserver.nittcsc.sktjf

import java.math.BigInteger

internal data class SktjfUpdateKey(val academicYear: Int, val term: String, val classId: String)
internal enum class SktjfUpdateKind { INITIAL, REVISION }
internal sealed interface SktjfNoticeDecision {
    data class Offer(val kind: SktjfUpdateKind) : SktjfNoticeDecision
    data object AlreadyHandled : SktjfNoticeDecision
    data object Invalid : SktjfNoticeDecision
}

/** Only compare revisions from the same year and term; never notify again for a dismissed or applied revision. */
internal fun sktjfNoticeDecision(key: SktjfUpdateKey, incoming: String, applied: String?, dismissed: String?): SktjfNoticeDecision {
    if (key.academicYear !in 1900..9998 || key.term !in setOf("S", "F") ||
        !Regex("^(1-[1-4]|[2-5][MEIB]|AC-[A-Z0-9]+)$").matches(key.classId)) return SktjfNoticeDecision.Invalid
    val incomingTag = parseSktjfRevision(incoming)
        ?.takeIf { it.year == key.academicYear && it.term == key.term } ?: return SktjfNoticeDecision.Invalid
    fun parse(value: String) = parseSktjfRevision(value)
        ?.takeIf { it.year == key.academicYear && it.term == key.term }
    val revision = incomingTag.number
    val appliedTag = applied?.let { parse(it) ?: return SktjfNoticeDecision.Invalid }
    val dismissedTag = dismissed?.let { parse(it) ?: return SktjfNoticeDecision.Invalid }
    val appliedNumber = appliedTag?.takeIf { it.beta == incomingTag.beta }?.number
    val dismissedNumber = dismissedTag?.takeIf { it.beta == incomingTag.beta }?.number
    if ((appliedNumber != null && revision <= appliedNumber) || (dismissedNumber != null && revision <= dismissedNumber))
        return SktjfNoticeDecision.AlreadyHandled
    return SktjfNoticeDecision.Offer(if (revision == BigInteger.ZERO) SktjfUpdateKind.INITIAL else SktjfUpdateKind.REVISION)
}
