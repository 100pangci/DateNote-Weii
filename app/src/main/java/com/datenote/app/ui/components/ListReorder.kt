package com.datenote.app.ui.components

/**
 * Returns a copy of the list with the item at [fromIndex] moved to [toIndex].
 *
 * Used by the step editors together with [ReorderableColumn]; it never mutates the
 * original list so the surrounding form state stays a plain value.
 */
fun <T> List<T>.moveItem(fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex == toIndex) return this
    if (fromIndex !in indices || toIndex !in indices) return this
    val reordered = toMutableList()
    val moved = reordered.removeAt(fromIndex)
    reordered.add(toIndex, moved)
    return reordered
}
