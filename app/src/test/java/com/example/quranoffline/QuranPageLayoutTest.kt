package com.example.quranoffline

import com.example.quranoffline.data.QuranLayoutItem
import com.example.quranoffline.data.QuranLayoutLine
import com.example.quranoffline.data.QuranLayoutMarker
import com.example.quranoffline.data.QuranLayoutWordKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class QuranPageLayoutTest {
    @Test
    fun schemaV3ModelKeepsSourceItemsAndGlyphIdentity() {
        val wordKey = QuranLayoutWordKey(2, 14, 2)
        val line = QuranLayoutLine(
            number = 1,
            type = "ayah",
            wordKeys = listOf(wordKey),
            items = listOf(
                QuranLayoutItem(2, "word", wordKey, 429, 64476, "QCF_P003.TTF"),
                QuranLayoutItem(1, "ayah", null, 430, 64477, "QCF_P003.TTF"),
            ).sortedByDescending { it.position },
            markers = listOf(QuranLayoutMarker(1, "ayah", glyphId = 430, glyphCode = 64477, fontFile = "QCF_P003.TTF")),
        )
        assertEquals("2:14:2", line.wordKeys.single().value)
        assertEquals(2, line.items.first().position)
        assertEquals(64476, line.items.first().glyphCode)
        assertEquals("QCF_P003.TTF", line.items.first().fontFile)
        assertNotNull(line.markers.single().glyphCode)
    }
}
