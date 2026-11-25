package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.LessonDraft
import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode

/** Resolve only the matching side of a linked row; ambiguous A/B names are not guessed. */
fun linkedLessonSubjectRename(old: LessonEntity, draft: LessonDraft, itemSubject: String): Pair<String, String>? {
    if (old.mode != draft.mode || itemSubject.isBlank()) return null
    val pairs = when (old.mode) {
        LessonMode.WEEKLY -> listOf(old.weeklySubject to draft.weeklySubject)
        LessonMode.ALTERNATING -> listOf(old.aSubject to draft.aSubject, old.bSubject to draft.bSubject)
    }
    val matches = pairs.filter { (before, _) -> before.isNotBlank() && sameSubject(before, itemSubject) }
    val (before, after) = matches.singleOrNull() ?: return null
    return if (after.isBlank() || before == after) null else before to after.trim()
}

/** Only spelling variants and a parenthesized qualifier are considered clearly related. */
fun isClearlyRelatedSubjectRename(before: String, after: String): Boolean {
    val oldKey = subjectIdentityKey(before)
    val newKey = subjectIdentityKey(after)
    if (oldKey.isEmpty() || newKey.isEmpty()) return false
    if (oldKey == newKey) return true
    fun qualified(base: String, value: String): Boolean {
        if (!value.startsWith(base)) return false
        val suffix = value.substring(base.length).trimStart()
        return (suffix.startsWith("(") && suffix.endsWith(")")) ||
            (suffix.startsWith("（") && suffix.endsWith("）"))
    }
    return qualified(oldKey, newKey) || qualified(newKey, oldKey)
}
