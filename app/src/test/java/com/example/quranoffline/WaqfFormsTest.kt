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
}
