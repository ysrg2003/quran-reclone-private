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
}
