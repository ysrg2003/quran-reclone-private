package com.example.quranoffline

import com.example.quranoffline.data.QuranWord
import com.example.quranoffline.data.RecitationMatcher
import com.example.quranoffline.data.WordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecitationMatcherTest {
    private val expected = listOf(
        QuranWord(0, "بِسْمِ", "بسم"),
        QuranWord(1, "اللَّهِ", "الله"),
        QuranWord(2, "الرَّحْمَٰنِ", "الرحمن"),
    )

    @Test
    fun partialStreamIsUncertainNotMissing() {
        val result = RecitationMatcher.evaluate(expected, "بسم الله", hasFinalResult = false)
        assertEquals(2, result.matched)
        assertEquals(1, result.uncertain)
        assertTrue(result.words.last().status == WordStatus.UNCERTAIN)
    }

    @Test
    fun finalStreamMarksMissingWord() {
        val result = RecitationMatcher.evaluate(expected, "بسم الله", hasFinalResult = true)
        assertEquals(2, result.matched)
        assertEquals(1, result.missing)
        assertTrue(result.words.last().status == WordStatus.MISSING)
    }

    @Test
    fun skippedExpectedWordDoesNotShiftLaterWord() {
        val result = RecitationMatcher.evaluate(expected, "بسم الرحمن", hasFinalResult = true)
        assertEquals(2, result.matched)
        assertEquals(1, result.missing)
        assertEquals(0, result.unexpected)
        assertEquals(WordStatus.MISSING, result.words[1].status)
        assertEquals(WordStatus.MATCHED, result.words[2].status)
    }

    @Test
    fun extraObservedWordDoesNotShiftLaterWords() {
        val result = RecitationMatcher.evaluate(expected, "بسم الله خطأ الرحمن", hasFinalResult = true)
        assertEquals(3, result.matched)
        assertEquals(1, result.unexpected)
        assertEquals(WordStatus.MATCHED, result.words[2].status)
    }

    @Test
    fun partialSkippedWordIsMissingWhenLaterWordWasHeard() {
        val result = RecitationMatcher.evaluate(expected, "بسم الرحمن", hasFinalResult = false)
        assertEquals(2, result.matched)
        assertEquals(1, result.missing)
        assertEquals(0, result.uncertain)
        assertEquals(WordStatus.MISSING, result.words[1].status)
    }

    @Test
    fun trailingPartialWordsRemainUncertain() {
        val result = RecitationMatcher.evaluate(expected, "بسم", hasFinalResult = false)
        assertEquals(1, result.matched)
        assertEquals(2, result.uncertain)
        assertEquals(WordStatus.UNCERTAIN, result.words[2].status)
    }

    @Test
    fun repeatedPhraseUsesCurrentEarliestOccurrence() {
        val repeated = listOf(
            QuranWord(0, "بِسْمِ", "بسم"),
            QuranWord(1, "اللَّهِ", "الله"),
            QuranWord(2, "الرَّحْمَٰنِ", "الرحمن"),
            QuranWord(3, "الرَّحِيمِ", "الرحيم"),
            QuranWord(4, "الرَّحْمَٰنِ", "الرحمن"),
            QuranWord(5, "الرَّحِيمِ", "الرحيم"),
        )
        val result = RecitationMatcher.evaluate(repeated, "الرحمن الرحيم", hasFinalResult = true)
        assertEquals(2, result.matched)
        assertEquals(WordStatus.MATCHED, result.words[2].status)
        assertEquals(WordStatus.MATCHED, result.words[3].status)
        assertEquals(WordStatus.MISSING, result.words[4].status)
    }

    @Test
    fun preferredCursorChoosesCurrentRepeatedOccurrence() {
        val repeated = listOf(
            QuranWord(0, "بِسْمِ", "بسم"),
            QuranWord(1, "الرَّحْمَٰنِ", "الرحمن"),
            QuranWord(2, "الرَّحِيمِ", "الرحيم"),
            QuranWord(3, "الرَّحْمَٰنِ", "الرحمن"),
            QuranWord(4, "الرَّحِيمِ", "الرحيم"),
        )
        val result = RecitationMatcher.evaluate(repeated, "الرحمن الرحيم", hasFinalResult = true, preferredExpectedIndex = 3)
        assertEquals(2, result.matched)
        assertEquals(WordStatus.MATCHED, result.words[3].status)
        assertEquals(WordStatus.MATCHED, result.words[4].status)
    }

    @Test
    fun previousJudgementsArePreservedWhenNewSegmentArrives() {
        val previous = RecitationMatcher.evaluate(expected, "بسم الله", hasFinalResult = true)
        val current = RecitationMatcher.evaluate(expected, "الرحمن", hasFinalResult = false, preferredExpectedIndex = 2)
        val merged = RecitationMatcher.mergePrevious(previous, current, preserveBeforeIndex = 2)
        assertEquals(WordStatus.MATCHED, merged.words[0].status)
        assertEquals(WordStatus.MATCHED, merged.words[1].status)
        assertEquals(WordStatus.MATCHED, merged.words[2].status)
    }

    @Test
    fun diacriticsDoNotBreakBasicMatching() {
        val result = RecitationMatcher.evaluate(expected, "بِسْمِ اللَّهِ الرَّحْمَٰنِ", hasFinalResult = true)
        assertEquals(3, result.matched)
        assertEquals(0, result.unexpected)
    }
}
