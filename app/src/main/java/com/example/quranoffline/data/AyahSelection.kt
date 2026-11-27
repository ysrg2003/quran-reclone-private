package com.example.quranoffline.data

/** A contiguous, inclusive range of Quran ayah indexes in Mushaf order. */
data class AyahSelection(
    val startIndex: Int,
    val endIndex: Int,
) {
    init {
        require(startIndex >= 0) { "startIndex must be non-negative" }
        require(endIndex >= startIndex) { "endIndex must not precede startIndex" }
    }

    val indices: IntRange get() = startIndex..endIndex
    val count: Int get() = endIndex - startIndex + 1

    /** A normal tap extends forward or truncates the range; it never creates a gap. */
    fun tap(index: Int): AyahSelection = if (index < startIndex) {
        copy(startIndex = index)
    } else {
        copy(endIndex = index)
    }

    companion object {
        fun fromLongPress(index: Int): AyahSelection = AyahSelection(index, index)
    }
}

fun QuranAyah.selectionKey(): String = "$surah:$ayah"

/**
 * Playback order is always the canonical Mushaf order, independent of touch order
 * or the order in which a UI state happened to store the selected indices.
 */
fun ayahIndicesInMushafOrder(indices: Iterable<Int>): List<Int> = indices.distinct().sorted()

/** Pure selection arithmetic. The base ayah never leaves the range; each side moves on its own. */
object SelectionRange {
    /** Returns (start, end). Tapping before the base moves the start (grow or shrink), after the base moves the end, on the base collapses to it. */
    fun tap(start: Int, end: Int, base: Int, tapped: Int): Pair<Int, Int> = when {
        tapped < base -> tapped to maxOf(end, base)
        tapped > base -> minOf(start, base) to tapped
        else -> base to base
    }
}

/** Character span of each ayah inside one mushaf line: key, start, endExclusive. An ayah ends at its own end marker. */
fun ayahSegments(line: WordReferenceLine): List<Triple<String, Int, Int>> {
    if (line.ayahKeys.isEmpty()) return emptyList()
    val out = ArrayList<Triple<String, Int, Int>>(line.ayahKeys.size)
    var from = 0
    line.ayahKeys.forEachIndexed { i, key ->
        val num = key.substringAfter(':').toIntOrNull()
        val marker = line.ayahNumbers.firstOrNull { it.number == num }
        val end = if (i == line.ayahKeys.lastIndex) line.text.length else (marker?.end ?: line.text.length).coerceIn(from, line.text.length)
        out += Triple(key, from, end)
        from = end
    }
    return out
}

fun ayahKeyAtOffset(segments: List<Triple<String, Int, Int>>, offset: Int): String? =
    segments.firstOrNull { offset >= it.second && offset < it.third }?.first ?: segments.lastOrNull()?.first
