package jp.linkserver.nittcsc.logic

import java.text.Normalizer
import java.util.Locale

/** Convert only Roman numeral glyphs, without changing the stored or displayed name. */
fun normalizeSubjectRomanNumerals(value: String): String = buildString {
    value.forEach { character ->
        append(if (character in '\u2160'..'\u217f') Normalizer.normalize(character.toString(), Normalizer.Form.NFKC) else character.toString())
    }
}

/** Exact identity matching; unlike general search, this does not fold unrelated characters. */
fun subjectIdentityKey(value: String): String = normalizeSubjectRomanNumerals(value).trim().lowercase(Locale.ROOT)
fun sameSubject(left: String, right: String): Boolean = subjectIdentityKey(left) == subjectIdentityKey(right)
fun subjectContains(value: String, query: String): Boolean = subjectIdentityKey(value).contains(subjectIdentityKey(query))
