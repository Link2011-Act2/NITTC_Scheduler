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
    val pattern = Regex("^${key.academicYear}\\.${key.term}\\.(0|[1-9][0-9]{0,127})$")
    fun parse(value: String): BigInteger? = pattern.matchEntire(value)?.groupValues?.get(1)?.let(::BigInteger)
    val revision = parse(incoming) ?: return SktjfNoticeDecision.Invalid
    val appliedNumber = applied?.let { parse(it) ?: return SktjfNoticeDecision.Invalid }
    val dismissedNumber = dismissed?.let { parse(it) ?: return SktjfNoticeDecision.Invalid }
    if ((appliedNumber != null && revision <= appliedNumber) || (dismissedNumber != null && revision <= dismissedNumber))
        return SktjfNoticeDecision.AlreadyHandled
    return SktjfNoticeDecision.Offer(if (revision == BigInteger.ZERO) SktjfUpdateKind.INITIAL else SktjfUpdateKind.REVISION)
}
