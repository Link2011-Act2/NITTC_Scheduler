package jp.linkserver.nittcsc.logic

/** A missing incoming value means that the distribution makes no claim about this key. */
internal sealed interface SktjfMergeDecision<out T> {
    data class Keep<T>(val value: T?) : SktjfMergeDecision<T>
    data class Apply<T>(val value: T) : SktjfMergeDecision<T>
    data class Conflict<T>(val base: T?, val local: T?, val incoming: T) : SktjfMergeDecision<T>
}

/**
 * Compare content without database identifiers. An absent distribution value never deletes
 * local content, including lessons entered in cells excluded from the distribution.
 * A null base represents a first import or a key absent from the previous distribution.
 */
internal fun <T> mergeSktjfValue(base: T?, local: T?, incoming: T?): SktjfMergeDecision<T> {
    if (incoming == null || incoming == base || incoming == local) {
        return SktjfMergeDecision.Keep(local)
    }
    if (local == base) return SktjfMergeDecision.Apply(incoming)
    return SktjfMergeDecision.Conflict(base, local, incoming)
}
