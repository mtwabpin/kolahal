/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty

/**
 * Laid-out lines kept for painting again, the most recently used first: at most [max] of them,
 * the least recently used dropped when a new one comes in. A line laid out once is painted on
 * every frame from here, so scrolling back lays out nothing. Used on the main thread only.
 */
class BlockCache<K : Any, V : Any>(val max: Int) {
    private val kept = object : LinkedHashMap<K, V>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > max
    }

    /** How many are kept now. */
    val size: Int get() = kept.size

    /** The value for [key], made with [make] and kept when there is none. */
    fun get(key: K, make: () -> V): V = kept[key] ?: make().also { kept[key] = it }

    /** The values kept, least recently used first. */
    fun values(): List<V> = kept.values.toList()

    /** Drops everything (the system is short of memory). */
    fun clear() = kept.clear()
}

/** About how many bytes a grid's cells take: two ints a cell ([CellBuffer]). */
fun CellBuffer.bytes(): Long = cols.toLong() * rows * 8
