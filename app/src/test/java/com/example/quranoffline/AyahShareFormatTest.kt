package com.example.quranoffline

import com.example.quranoffline.data.AyahShareFormat as F
import org.junit.Assert.assertEquals
import org.junit.Test

class AyahShareFormatTest {
    @Test fun singleAyah() = assertEquals("(نص) [البقرة: ٥]", F.text(listOf("نص"), "البقرة", 5, "البقرة", 5, true))
    @Test fun twoAyahs() = assertEquals("(أ) (ب) [البقرة: ٥-٦]", F.text(listOf("أ", "ب"), "البقرة", 5, "البقرة", 6, true))
    @Test fun acrossSurahs() = assertEquals("(أ) (ب) [البقرة: ٢٨٦ - آل عمران: ١]", F.text(listOf("أ", "ب"), "البقرة", 286, "آل عمران", 1, false))
    @Test fun multiDigit() = assertEquals("[الفاتحة: ١٢٣]", F.reference("الفاتحة", 123, "الفاتحة", 123, true, 1))
}
