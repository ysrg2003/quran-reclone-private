package com.example.quranoffline

import com.example.quranoffline.data.WordDisplayRules as R
import com.example.quranoffline.data.WordStatus
import org.junit.Assert.*
import org.junit.Test

class WordDisplayRulesTest {
    @Test fun colourRulesMatchV11() {
        assertEquals(0xFFDFF1E5L, R.background(WordStatus.MATCHED, false))
        assertEquals(0xFFFFDDD8L, R.background(WordStatus.EXTRA_OR_UNEXPECTED, false))
        assertEquals(0xFFFFDDD8L, R.background(WordStatus.MISSING, false))
        assertEquals(0xFFFFF0C9L, R.background(WordStatus.UNCERTAIN, false))
        assertEquals(0xFFDFF1E5L, R.background(null, true))
        assertEquals(0x00000000L, R.background(null, false))
        // a judged word keeps its status colour even when it is also the current word
        assertEquals(0xFFFFDDD8L, R.background(WordStatus.MISSING, true))
    }

    @Test fun visibleModeAlwaysShowsCorrectText() {
        for (only in listOf(true, false)) assertEquals(R.Shown.CORRECT_WORD, R.shown(false, only, null, false, false))
    }

    @Test fun hiddenModeWithDotsKeepsV11Behaviour() {
        assertEquals(R.Shown.DOT, R.shown(true, false, null, false, false))
        assertEquals(R.Shown.CURRENT_SPOKEN, R.shown(true, false, null, false, true))
        assertEquals(R.Shown.CORRECT_WORD, R.shown(true, false, WordStatus.MATCHED, true, false))
    }

    @Test fun onlySpokenHidesUnsaidWordsAndShowsCorrectWordForErrors() {
        assertEquals(R.Shown.HIDDEN, R.shown(true, true, null, false, false))
        assertEquals(R.Shown.CURRENT_SPOKEN, R.shown(true, true, null, false, true))
        assertEquals(R.Shown.CORRECT_WORD, R.shown(true, true, WordStatus.EXTRA_OR_UNEXPECTED, true, false))
        assertEquals(R.Shown.CORRECT_WORD, R.shown(true, true, WordStatus.MISSING, false, false))
        assertEquals(R.Shown.CORRECT_WORD, R.shown(true, true, WordStatus.MATCHED, true, false))
    }
}

class WordRevealOneByOneTest {
    @Test fun wordsNotSaidYetStayHiddenEvenWhenTheyCarryTheUncertainStatus() {
        // v16 bug: every unsaid word has status UNCERTAIN, so the whole text appeared after the first word.
        assertEquals(R.Shown.HIDDEN, R.shown(true, true, WordStatus.UNCERTAIN, false, false))
        assertEquals(R.Shown.CURRENT_SPOKEN, R.shown(true, true, WordStatus.UNCERTAIN, false, true))
        assertEquals(R.Shown.CORRECT_WORD, R.shown(true, true, WordStatus.MATCHED, true, false))
        assertEquals(R.Shown.CORRECT_WORD, R.shown(true, true, WordStatus.EXTRA_OR_UNEXPECTED, true, false))
        assertEquals(R.Shown.CORRECT_WORD, R.shown(true, true, WordStatus.MISSING, false, false))
    }
    @Test fun dotsModeStillShowsDotsForUnsaidWords() {
        assertEquals(R.Shown.DOT, R.shown(true, false, WordStatus.UNCERTAIN, false, false))
    }
}
