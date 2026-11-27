package com.example.quranoffline.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AyahMarkerDigitsTest {
    private fun digits(n: Int) = n.toString().map { '\u0660' + (it - '0') }.joinToString("")

    @Test fun displayReversesOnlyMultiDigitNumbersAndKeepsOffsets() {
        for (n in 1..6236) {
            val d = digits(n)
            val text = "\u0646 $d \u0648"
            val num = WordReferenceAyahNumber(2, 2 + d.length, d, n)
            val line = WordReferenceLine(1, "ayah", text, listOf("2:1"), listOf(num))
            val shown = line.displayText()
            assertEquals(text.length, shown.length)
            assertEquals("outside the marker unchanged", "\u0646 ", shown.substring(0, 2))
            assertEquals(" \u0648", shown.substring(2 + d.length))
            assertEquals("n=$n", d.reversed(), shown.substring(2, 2 + d.length))
            if (d.length == 1) assertEquals(text, shown)
        }
    }

    @Test fun bundledLayoutNumbersAreStoredInCorrectOrderForAll6236() {
        val f = listOf("src/main/assets/quran/word_reference_layout.json", "app/src/main/assets/quran/word_reference_layout.json").map(::File).first { it.isFile }
        val re = Regex("\"text\": ?\"([\u0660-\u0669]+)\", ?\"number\": ?(\\d+)")
        var count = 0
        re.findAll(f.readText()).forEach { m ->
            count++
            assertEquals(digits(m.groupValues[2].toInt()), m.groupValues[1])
        }
        assertEquals(6236, count)
        assertTrue(count > 0)
    }

    @Test fun leadingMarkerLineAssignsTextToTheNextAyah() {
        // Al-Fatiha page model line 4: "2 <ayah 3 words> 3 <ayah 4 words>" with the marker of ayah 2 first on the line.
        val text = "\u0662 AAA BBB \u0663 CCC DDD"
        val nums = listOf(WordReferenceAyahNumber(0, 1, "\u0662", 2), WordReferenceAyahNumber(9, 10, "\u0663", 3))
        val line = WordReferenceLine(4, "ayah", text, listOf("1:2", "1:3", "1:4"), nums)
        val seg = ayahSegments(line)
        assertEquals(listOf("1:2", "1:3", "1:4"), seg.map { it.first })
        assertEquals("1:3", ayahKeyAtOffset(seg, text.indexOf("BBB")))
        assertEquals("1:4", ayahKeyAtOffset(seg, text.indexOf("DDD")))
        assertEquals("1:2", ayahKeyAtOffset(seg, 0))
    }

    @Test fun modelPagesOverrideDownloadedPagesOneAndTwoOnly() {
        fun page(n: Int, tag: String) = WordReferencePage(n, listOf(WordReferenceLine(1, "ayah", tag, emptyList(), emptyList())))
        val down = WordReferenceLayout(3, "quran.com", "f", (1..604).map { page(it, "d$it") }, 604)
        val bundled = WordReferenceLayout(3, "word", "f", (1..604).map { page(it, "b$it") }, 604)
        val merged = WordReferenceLayout.withModelPages(down, bundled)
        assertEquals("b1", merged.linesForPage(1).single().text)
        assertEquals("b2", merged.linesForPage(2).single().text)
        assertEquals("d3", merged.linesForPage(3).single().text)
        assertEquals(604, merged.pages.size)
    }
}
