package com.example.quranoffline

import com.example.quranoffline.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JuzSurahsTest {
    private fun md(): QuranMetadata {
        // surah1: 7 ayahs (global 1..7), surah2: 286 (8..293), surah3: 200 (294..493)
        val suras = listOf(
            QuranSuraMetadata(1, 7, 1, "a", RevelationType.values()[0], 1, 1),
            QuranSuraMetadata(2, 286, 8, "b", RevelationType.values()[0], 2, 1),
            QuranSuraMetadata(3, 200, 294, "c", RevelationType.values()[0], 3, 1),
        )
        val juzs = listOf(QuranBoundary(1, 1, 1, 1), QuranBoundary(2, 2, 142, 149), QuranBoundary(3, 3, 1, 294))
        return QuranMetadata(suras, juzs, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
    @Test fun juz1HasSurah1FullAndSurah2Partial() {
        val e = JuzSurahs.forJuz(md(), 1)
        assertEquals(listOf(1, 2), e.map { it.surah })
        assertEquals(false, e[0].partial)
        assertEquals(1 to 141, e[1].startAyah to e[1].endAyah); assertTrue(e[1].partial)
    }
    @Test fun juz2StartsMidSurah() {
        val e = JuzSurahs.forJuz(md(), 2)
        assertEquals(listOf(2), e.map { it.surah }); assertEquals(142, e[0].startAyah); assertEquals(286, e[0].endAyah)
    }
    @Test fun lastJuzRunsToEnd() {
        val e = JuzSurahs.forJuz(md(), 3)
        assertEquals(1 to 200, e[0].startAyah to e[0].endAyah); assertEquals(false, e[0].partial)
    }
}
