package com.example.quranoffline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranComLayoutBuilderTest {
    private fun ayah(s: Int, a: Int, n: Int) = QuranAyah(s, a, "", "", List(n) { QuranWord(it, "w$s.$a.$it", "m") })
    private fun verse(s: Int, a: Int, vararg lines: Int) =
        QcVerse(s, a, lines.map { QcWord(it, false) } + QcWord(lines.last(), true))

    @Test fun ayahSpanningLinesSplitsWordsAndKeepsEndMarkerOnLastLine() {
        val ayahs = mapOf("2:6" to ayah(2, 6, 5))
        val layout = QuranComLayoutBuilder.build(
            listOf(QcPage(3, listOf(verse(2, 6, 1, 1, 1, 2, 2)))), ayahs, emptyMap(), null,
        )
        val lines = layout.linesForPage(3)
        assertEquals(listOf(1, 2), lines.map { it.number })
        assertEquals("w2.6.0 w2.6.1 w2.6.2", lines[0].text)
        assertEquals(0 to 3, lines[0].wordRanges["2:6"])
        assertEquals("w2.6.3 w2.6.4 \u0666", lines[1].text)
        assertEquals(3 to 5, lines[1].wordRanges["2:6"])
        assertEquals(6, lines[1].ayahNumbers.single().number)
    }

    @Test fun suraHeaderAndBismillahFillGapLines() {
        val ayahs = mapOf("2:1" to ayah(2, 1, 1))
        val layout = QuranComLayoutBuilder.build(
            listOf(QcPage(2, listOf(verse(2, 1, 3)))), ayahs, mapOf(2 to "SURA2"), "BISM",
        )
        assertEquals(listOf("sura", "bismillah", "ayah"), layout.linesForPage(2).map { it.type })
        assertEquals(listOf(1, 2, 3), layout.linesForPage(2).map { it.number })
    }

    @Test fun suraTitleAtBottomOfPreviousPageLeavesOnlyBismillahOnNextPage() {
        val ayahs = mapOf("1:1" to ayah(1, 1, 1), "4:1" to ayah(4, 1, 1))
        val p1 = QcPage(1, (1..6).map { QcVerse(1, it, listOf(QcWord(it, false), QcWord(it, true))) }.let { listOf(it.first()) })
        // page 14 lines used, next page opens sura 4
        val last = QcVerse(1, 1, (1..7).map { QcWord(it, false) } + QcWord(7, true)) // 7 lines, page 1 expects 8
        val layout = QuranComLayoutBuilder.build(
            listOf(QcPage(1, listOf(last)), QcPage(2, listOf(verse(4, 1, 2)))),
            ayahs, mapOf(4 to "SURA4"), "BISM",
        )
        assertEquals("sura", layout.linesForPage(1).last().type)
        assertEquals(listOf("bismillah", "ayah"), layout.linesForPage(2).map { it.type })
        assertTrue(p1.verses.isNotEmpty())
    }

    @Test fun mismatchedWordCountStillCoversEveryOurWordOnce() {
        val ayahs = mapOf("8:6" to ayah(8, 6, 12))
        val layout = QuranComLayoutBuilder.build(
            listOf(QcPage(180, listOf(QcVerse(8, 6, (1..11).map { QcWord(if (it <= 6) 1 else 2, false) } + QcWord(2, true))))),
            ayahs, emptyMap(), null,
        )
        val r = layout.linesForPage(180).flatMap { it.wordRanges.values }
        assertEquals(0, r.first().first)
        assertEquals(12, r.last().second)
        assertEquals(r[0].second, r[1].first)
    }
}
