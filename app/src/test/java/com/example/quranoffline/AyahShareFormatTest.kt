package com.example.quranoffline

import com.example.quranoffline.data.AyahShareFormat as F
import org.junit.Assert.assertEquals
import org.junit.Test

class AyahShareFormatTest {
    @Test fun singleAyah() = assertEquals("{\uFD3F5\uFD3E نص} [سورة البقرة : ٥]", F.text(listOf(5), listOf("نص"), "البقرة", "البقرة", true))
    @Test fun multiAyahSelection() = assertEquals("{\uFD3F4\uFD3E أ \uFD3F5\uFD3E ب \uFD3F6\uFD3E ج \uFD3F7\uFD3E د} [سورة آل عمران : ٤-٧]",
        F.text(listOf(4, 5, 6, 7), listOf("أ", "ب", "ج", "د"), "آل عمران", "آل عمران", true))
    @Test fun acrossSurahs() = assertEquals("{\uFD3F286\uFD3E أ \uFD3F1\uFD3E ب} [سورة البقرة : ٢٨٦ - سورة آل عمران : ١]", F.text(listOf(286, 1), listOf("أ", "ب"), "البقرة", "آل عمران", false))
}
