package jp.linkserver.nittcsc.sktjf

import java.math.BigInteger

private val revisionPattern = Regex("([0-9]{4})\\.([SF])\\.(0|[1-9][0-9]{0,127})(\\.Beta)?")
private val undergraduateClass = Regex("1-[1-4]|[2-5][MEIB]")
internal fun isSktjfUndergraduateClass(id: String) = undergraduateClass.matches(id)

internal data class SktjfRevision(val year: Int, val term: String, val number: BigInteger, val beta: Boolean)

internal fun parseSktjfRevision(tag: String): SktjfRevision? {
    val match = revisionPattern.matchEntire(tag) ?: return null
    val (year, term, number, beta) = match.destructured
    return SktjfRevision(year.toInt(), term, number.toBigInteger(), beta.isNotEmpty())
}
