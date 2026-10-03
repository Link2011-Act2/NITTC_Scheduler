package jp.linkserver.nittcsc.logic

import kotlin.math.floor

/** 受信状態はCollectorに任せ、ここでは表示容量と退場対象だけを決める。 */
internal data class QrFragmentDotLayout(val columns: Int, val lastRowColumns: Int) {
    val capacity: Int get() = columns * 2 + lastRowColumns
}

internal fun qrFragmentDotLayout(
    width: Float, diameter: Float, spacing: Float, overflowLabelWidth: Float, total: Int
): QrFragmentDotLayout {
    require(width.isFinite() && width >= 0f && diameter.isFinite() && diameter > 0f && spacing.isFinite() && spacing >= 0f)
    require(overflowLabelWidth.isFinite() && overflowLabelWidth >= 0f && total >= 0)
    val pitch = diameter + spacing
    val columns = floor((width + spacing) / pitch).toInt().coerceAtLeast(1)
    // +Nは3行目だけに置く。最初の総枚数で領域を確保し、桁数が減っても容量を動かさない。
    val lastRowColumns = if (total > columns * 3) {
        floor((width - overflowLabelWidth) / pitch).toInt().coerceIn(0, columns)
    } else columns
    return QrFragmentDotLayout(columns, lastRowColumns)
}

internal data class QrFragmentDotWindow(val indices: List<Int>, val overflow: Int)

internal fun qrFragmentDotWindow(total: Int, capacity: Int, retired: Set<Int>): QrFragmentDotWindow {
    require(total >= 0 && capacity > 0)
    val remaining = (0 until total).filter { it !in retired }
    return QrFragmentDotWindow(remaining.take(capacity), (remaining.size - capacity).coerceAtLeast(0))
}

internal fun qrFragmentToRetire(total: Int, capacity: Int, received: Set<Int>, retired: Set<Int>, fixed: Boolean = false): Int? {
    if (fixed) return null
    val window = qrFragmentDotWindow(total, capacity, retired)
    if (window.overflow == 0) return null // 最後の穴埋めフェーズでは受信済みも固定。
    // 可視の受信済みを先に退場させる。画面外の受信済みだけでも未受信の可視性を優先できる。
    return window.indices.firstOrNull { it in received }
        ?: (0 until total).firstOrNull { it !in retired && it in received }
}
