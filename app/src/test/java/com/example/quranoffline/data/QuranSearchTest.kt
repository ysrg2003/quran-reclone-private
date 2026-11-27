package com.example.quranoffline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranSearchTest {
    private fun ayah(s: Int, a: Int, vararg w: String) =
        QuranAyah(s, a, w.joinToString(" "), w.joinToString(" "), w.mapIndexed { i, t -> QuranWord(i, t, t) })

    private val data = listOf(
        ayah(1, 1, "بِسْمِ", "ٱللَّهِ", "ٱلرَّحْمَٰنِ"),
        ayah(2, 1, "كُنتُم", "تُكَذِّبُونَ", "بِهَا", "تُكَذِّبُونَ"),
        ayah(2, 2, "ٱللَّهِ", "أَكْبَرُ"),
    )
    private val idx = QuranSearch.buildIndex(data)

    @Test fun wordCountsOccurrencesAndAyahs() {
        val r = QuranSearch.search(data, idx, "تكذبون")
        assertEquals(1, r.hits.size); assertEquals(2, r.occurrences); assertEquals(setOf(1, 3), r.hits[0].matchedWords)
    }

    @Test fun phraseAndScope() {
        assertEquals(2, QuranSearch.search(data, idx, "الله").hits.size)
        assertEquals(1, QuranSearch.search(data, idx, "الله", surahScope = 2).hits.size)
        assertEquals(1, QuranSearch.search(data, idx, "كنتم تكذبون").hits.size)
    }

    @Test fun substringFallback() {
        val r = QuranSearch.search(data, idx, "كذب")
        assertTrue(r.usedSubstring); assertEquals(1, r.hits.size)
    }

    @Test fun emptyQuery() { assertEquals(0, QuranSearch.search(data, idx, "  ").hits.size) }
}
