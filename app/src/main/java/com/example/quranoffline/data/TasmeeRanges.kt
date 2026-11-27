package com.example.quranoffline.data

object TasmeeRanges {
    /** First and last ayah index whose value (page or juz) lies in lo..hi (swapped if reversed). Null when none or out of bounds. */
    fun span(values: List<Int>, from: Int, to: Int, min: Int, max: Int): Pair<Int, Int>? {
        val lo = minOf(from, to); val hi = maxOf(from, to)
        if (lo < min || hi > max) return null
        val first = values.indexOfFirst { it in lo..hi }
        if (first < 0) return null
        val last = values.indexOfLast { it in lo..hi }
        return first to last
    }
}
