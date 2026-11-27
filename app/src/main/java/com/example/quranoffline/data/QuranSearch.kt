package com.example.quranoffline.data

data class SearchHit(val surah: Int, val ayah: Int, val matchedWords: Set<Int>, val occurrences: Int)

data class SearchOutcome(val hits: List<SearchHit>, val occurrences: Int, val usedSubstring: Boolean)

/** Pure word/phrase search over the loaded Quran text. Diacritics are ignored through [normalizeArabicForMatch]. */
object QuranSearch {
    /** Normalised word lists, built once per loaded Quran (not per keystroke). */
    fun buildIndex(ayahs: List<QuranAyah>): List<List<String>> =
        ayahs.map { a -> a.words.map { normalizeArabicForMatch(it.text) } }

    fun search(ayahs: List<QuranAyah>, index: List<List<String>>, query: String, surahScope: Int? = null): SearchOutcome {
        val q = normalizeArabicForMatch(query).split(' ').filter { it.isNotBlank() }
        if (q.isEmpty() || ayahs.size != index.size) return SearchOutcome(emptyList(), 0, false)
        fun run(substring: Boolean): SearchOutcome {
            val hits = ArrayList<SearchHit>()
            var total = 0
            for (i in ayahs.indices) {
                val a = ayahs[i]
                if (surahScope != null && a.surah != surahScope) continue
                val w = index[i]
                val matched = HashSet<Int>()
                var count = 0
                var s = 0
                while (s + q.size <= w.size) {
                    var ok = true
                    for (k in q.indices) {
                        val word = w[s + k]
                        if (!(if (substring) word.contains(q[k]) else word == q[k])) { ok = false; break }
                    }
                    if (ok) { count++; for (k in q.indices) matched.add(s + k); s += q.size } else s++
                }
                if (count > 0) { hits.add(SearchHit(a.surah, a.ayah, matched, count)); total += count }
            }
            return SearchOutcome(hits, total, substring)
        }
        val exact = run(false)
        return if (exact.hits.isNotEmpty()) exact else run(true)
    }
}

/** Resolves the "go to" dialog to a mushaf page. */
object QuickNav {
    fun pageForSurah(metadata: QuranMetadata, surah: Int): Int? = metadata.ayah(surah, 1)?.page
    fun pageForAyah(metadata: QuranMetadata, surah: Int, ayah: Int): Int? = metadata.ayah(surah, ayah)?.page
    fun pageForJuz(metadata: QuranMetadata, juz: Int): Int? =
        metadata.juzs.firstOrNull { it.index == juz }?.let { metadata.ayah(it.surah, it.ayah)?.page }
    /** Hizb n starts at quarter 4(n-1)+1. */
    fun pageForHizb(metadata: QuranMetadata, hizb: Int): Int? {
        val quarter = 4 * (hizb - 1) + 1
        return metadata.quarters.firstOrNull { it.index == quarter }?.let { metadata.ayah(it.surah, it.ayah)?.page }
    }
}
