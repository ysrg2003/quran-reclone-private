package com.example.quranoffline

import com.example.quranoffline.data.QuranTextPresentation
import com.example.quranoffline.data.QuranTextSpanKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranTextPresentationTest {
    @Test
    fun overlaySpansPreserveCanonicalTextAndMarkRaisedHamza() {
        val canonical = "فِى ٱلْءَاخِرَةِ ۖ"

        val spans = QuranTextPresentation.spans(canonical)

        assertEquals(canonical, QuranTextPresentation.annotated(canonical).text)
        assertTrue(spans.any { it.kind == QuranTextSpanKind.HAMZA_OVERLAY && canonical.substring(it.start, it.end) == "ء" })
        assertTrue(spans.any { it.kind == QuranTextSpanKind.QURAN_MARK_OVERLAY && canonical.substring(it.start, it.end) == "ۖ" })
    }

    @Test
    fun markerLegendUsesTheAttachedReferenceMeaningsForKnownMarks() {
        val legend = QuranTextPresentation.markerLegend().toMap()

        assertEquals("الوصل أولى", legend['ۖ'])
        assertEquals("الوقف أولى", legend['ۗ'])
        assertEquals("حرف زائد لا ينطق", legend['۟'])
        assertEquals("نهاية آية ورقمها", legend['۝'])
        assertEquals("بداية ربع حزب", legend['۞'])
    }
}
