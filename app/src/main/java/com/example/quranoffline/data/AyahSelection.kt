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
