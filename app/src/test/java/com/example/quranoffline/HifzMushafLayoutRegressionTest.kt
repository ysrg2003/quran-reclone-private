package com.example.quranoffline

import com.example.quranoffline.data.WordDisplayRules as R
import com.example.quranoffline.data.WordStatus
import com.example.quranoffline.data.CorrectionSettings
import com.example.quranoffline.data.ayahHiddenInTasmee
import com.example.quranoffline.data.pageAfterHorizontalSwipe
import org.junit.Assert.*
import org.junit.Test

/** v24.8: the Hifz/Tasmee tab now draws the Reading-tab mushaf page. These pin the behaviour each function needs from it. */
class HifzMushafLayoutRegressionTest {
    @Test fun visibleModeShowsEveryWord() {
        for (s in listOf(null, WordStatus.MATCHED, WordStatus.UNCERTAIN, WordStatus.MISSING)) assertTrue(R.pageWordVisible(false, s, false))
    }

    @Test fun hideModeHidesUnsaidWords() {
        assertFalse(R.pageWordVisible(true, null, false))
        assertFalse(R.pageWordVisible(true, WordStatus.UNCERTAIN, false))
    }

    @Test fun hideModeRevealsMatchedAndWrongWordsWithTheCorrectText() {
        assertTrue(R.pageWordVisible(true, WordStatus.MATCHED, true))
        assertTrue(R.pageWordVisible(true, WordStatus.MISSING, false))
        assertTrue(R.pageWordVisible(true, WordStatus.EXTRA_OR_UNEXPECTED, true))
    }

    @Test fun hideModeOnTab0IsWholePage() {
        // tab 0's hide button hides the whole page (user decision); the selection-only rule applies in the full-screen reader
        val keys = listOf(1 to 1, 1 to 2)
        assertTrue(ayahHiddenInTasmee(true, emptySet(), keys, 1 to 1, tasmeeActive = true))
        assertFalse(ayahHiddenInTasmee(false, emptySet(), keys, 1 to 1))
    }

    @Test fun pageSwipeMatchesReadingTab() {
        assertEquals(6, pageAfterHorizontalSwipe(5, 604, 120f))
        assertEquals(4, pageAfterHorizontalSwipe(5, 604, -120f))
        assertEquals(5, pageAfterHorizontalSwipe(5, 604, 10f))
        assertEquals(1, pageAfterHorizontalSwipe(1, 604, -300f))
        assertEquals(604, pageAfterHorizontalSwipe(604, 604, 300f))
    }

    @Test fun wholeAyahOffOptionAndDefaultKept() {
        val off = CorrectionSettings.DEFAULTS.copy(wholeAyahAfterConsecutiveErrors = 0)
        assertEquals(0, off.normalized().wholeAyahAfterConsecutiveErrors)
        assertEquals(3, CorrectionSettings.DEFAULTS.wholeAyahAfterConsecutiveErrors)
    }
}
