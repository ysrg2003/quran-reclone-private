package com.example.quranoffline

import com.example.quranoffline.data.WordReferenceLayout
import com.example.quranoffline.data.WordReferenceLine
import com.example.quranoffline.data.WordReferencePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordReferenceLayoutTest {
    private val layout = WordReferenceLayout(
        schemaVersion = 3,
        source = "Word",
        fontFamily = "KFGQPC HAFS Uthmanic Script",
        pages = listOf(
            WordReferencePage(
                page = 3,
                lines = listOf(
                    WordReferenceLine(1, "ayah", "إِنَّ ٱلَّذِينَ كَفَرُواْ", listOf("2:6"), emptyList()),
                    WordReferenceLine(2, "ayah", "... ٱشۡتَرَوُاْ ...", listOf("2:16"), emptyList()),
                ),
            ),
        ),
    )

    @Test
    fun wordPageIndexKeepsSourceAyahKeysWithoutRewritingText() {
        assertEquals(listOf(2 to 6, 2 to 16), layout.ayahKeysForPage(3))
        assertEquals(3, layout.pageForAyah(2, 6))
        assertEquals(3, layout.pageForAyah(2, 16))
        assertTrue(layout.linesForPage(3).first().text.contains("كَفَرُواْ"))
        assertTrue(layout.linesForPage(3).last().text.contains("ٱشۡتَرَوُاْ"))
    }
}
