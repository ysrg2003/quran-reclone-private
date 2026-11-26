package com.example.quranoffline

import com.example.quranoffline.data.QuranWord
import com.example.quranoffline.data.matchesWrittenSpelling
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hafs waqf changes spelling only for ta marbuta (ة -> ه); vowels/tanween vanish without changing letters. */
class WaqfFormsTest {
    @Test
    fun taMarbutaMayBecomeHaOnStopping() {
        val w = QuranWord(0, "ٱلۡجَنَّةَ", "الجنة")
        assertTrue(matchesWrittenSpelling(w, "الجنه"))
        assertTrue(matchesWrittenSpelling(w, "الجنة"))
    }

    @Test
    fun arbitraryFinalLetterDropOrHaSubstitutionIsRejected() {
        val w = QuranWord(0, "ٱلۡجَنَّةَ", "الجنة")
        assertFalse(matchesWrittenSpelling(w, "الجن"))
        val r = QuranWord(1, "رَبِّ", "رب")
        assertFalse(matchesWrittenSpelling(r, "ره"))
        assertFalse(matchesWrittenSpelling(r, "ر"))
        val k = QuranWord(2, "كِتَٰبٞ", "كتاب")
        assertFalse(matchesWrittenSpelling(k, "كتا"))
    }
}

class TanweenAlifTest {
    @Test
    fun tanweenFathAlifMayBeOmittedWhenReadingOn() {
        val w = QuranWord(0, "عَبۡدًا", "عبدا")
        assertTrue(matchesWrittenSpelling(w, "عبد"))
        assertTrue(matchesWrittenSpelling(w, "عبدا"))
        assertFalse(matchesWrittenSpelling(QuranWord(1, "ٱلۡكِتَٰبَ", "الكتاب"), "الكتب"))
        assertFalse(matchesWrittenSpelling(QuranWord(2, "قَالُواْ", "قالوا"), "قالا"))
    }

    @Test
    fun alifMaqsuraAndYaAreInterchangeable() {
        val w = QuranWord(0, "عَلَىٰ", "علي", recognizedForms = setOf("على"))
        assertTrue(matchesWrittenSpelling(w, "علي"))
        assertTrue(matchesWrittenSpelling(w, "على"))
    }

    @Test
    fun realAssetTanweenFathCodepointAcceptsBothStopAndContinue() {
        // U+0657 is how the app's Quran text writes the open tanween fath.
        listOf("كَثِيرٗا" to "كثير", "عَذَابٗا" to "عذاب", "يَوۡمٗا" to "يوم", "رَغَدٗا" to "رغد").forEach { (text, noAlif) ->
            val w = QuranWord(0, text, noAlif + "ا")
            assertTrue(text, matchesWrittenSpelling(w, noAlif))
            assertTrue(text, matchesWrittenSpelling(w, noAlif + "ا"))
        }
        // A different word is still rejected.
        assertFalse(matchesWrittenSpelling(QuranWord(0, "كَثِيرٗا", "كثيرا"), "كبير"))
    }

    @Test
    fun finalAlifWithRoundZeroAcceptsBothWaqfAndWaslForms() {
        val ana = QuranWord(0, "أَنَا۠", "انا")
        assertTrue(matchesWrittenSpelling(ana, "انا"))
        assertTrue(matchesWrittenSpelling(ana, "أنا"))
        assertTrue(matchesWrittenSpelling(ana, "ان"))
        val zunun = QuranWord(0, "ٱلظُّنُونَا۠", "الظنونا")
        assertTrue(matchesWrittenSpelling(zunun, "الظنونا"))
        assertTrue(matchesWrittenSpelling(zunun, "الظنون"))
        assertFalse(matchesWrittenSpelling(zunun, "الظنو"))
    }

    @Test
    fun taMarbutaBothFormsWithTanweenAndRoundTrip() {
        val w = QuranWord(0, "بَقَرَةٞ", "بقرة")
        assertTrue(matchesWrittenSpelling(w, "بقرة"))
        assertTrue(matchesWrittenSpelling(w, "بقره"))
        val t = QuranWord(0, "أُمَّةٗ", "امة")
        assertTrue(matchesWrittenSpelling(t, "امة"))
        assertTrue(matchesWrittenSpelling(t, "امه"))
        assertFalse(matchesWrittenSpelling(t, "امت"))
    }

    @Test
    fun definiteDroppedYaAndMergedAnLaAreAccepted() {
        val hadi = QuranWord(0, "بِهَٰدِي", "بهادي")
        assertTrue(matchesWrittenSpelling(hadi, "بهادي"))
        assertTrue(matchesWrittenSpelling(hadi, "بهاد"))
        assertFalse(matchesWrittenSpelling(hadi, "بهاج"))
        val alla = QuranWord(0, "أَلَّا", "الا")
        assertTrue(matchesWrittenSpelling(alla, "الا"))
        assertTrue(matchesWrittenSpelling(alla, "انلا"))
        assertFalse(matchesWrittenSpelling(QuranWord(0, "أَلَا", "الا"), "انلا"))
        val wa = QuranWord(0, "وَأَلَّوِ", "وألو")
        assertTrue(matchesWrittenSpelling(wa, "وانلو"))
    }
}
