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

/**
 * Character span of each ayah inside one mushaf line: key, start, endExclusive.
 * The bundled line data lists only SOME of the ayahs on a line (an ayah that begins in the middle of a line is missing
 * from ayahKeys), so the end markers decide: the text up to marker N is ayah N, and the text after the last marker is ayah N+1.
 */
fun ayahSegments(line: WordReferenceLine): List<Triple<String, Int, Int>> {
    if (line.ayahKeys.isEmpty()) return emptyList()
    val len = line.text.length
    val markers = line.ayahNumbers.sortedBy { it.start }
    val firstKey = line.ayahKeys.first()
    val lastKey = line.ayahKeys.last()
    fun surahFor(ayahNo: Int, fallback: String): String =
        (line.ayahKeys.firstOrNull { it.substringAfter(':').toIntOrNull() == ayahNo } ?: fallback).substringBefore(':')
    if (markers.isEmpty()) return listOf(Triple(lastKey, 0, len))
    val out = ArrayList<Triple<String, Int, Int>>(markers.size + 1)
    var from = 0
    markers.forEach { m ->
        val end = m.end.coerceIn(from, len)
        out += Triple("${surahFor(m.number, firstKey)}:${m.number}", from, end)
        from = end
    }
    if (from < len) {
        val next = markers.last().number + 1
        out += Triple("${surahFor(next, lastKey)}:$next", from, len)
    }
    return out
}

fun ayahKeyAtOffset(segments: List<Triple<String, Int, Int>>, offset: Int): String? =
    segments.firstOrNull { offset >= it.second && offset < it.third }?.first ?: segments.lastOrNull()?.first

/**
 * v22.1 (user decision, Telegram 25402046): "hide ayahs" in the full-screen reader hides ONLY the selected ayahs, exactly
 * their words and, for ayah 1, its opening basmala box. Nothing else is hidden. With no selection (or a selection on
 * another page) nothing on the page is hidden. Set to false to hide the whole shown page instead (the v22 behaviour).
 */
const val HIDE_ONLY_SELECTED = true

/** Hide-ayahs rule for the full-screen reader (see [HIDE_ONLY_SELECTED]). pageKeys is kept for the whole-page mode. */
fun ayahHiddenInTasmee(hiddenMode: Boolean, selectedKeys: Set<Pair<Int, Int>>, pageKeys: List<Pair<Int, Int>>, key: Pair<Int, Int>): Boolean {
    if (!hiddenMode) return false
    if (!HIDE_ONLY_SELECTED) return true
    return key in selectedKeys
}
