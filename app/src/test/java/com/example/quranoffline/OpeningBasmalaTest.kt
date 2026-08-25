package com.example.quranoffline

import com.example.quranoffline.data.OpeningBasmalaSpeechFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningBasmalaTest {
    @Test
    fun completeOpeningBasmalaIsAnIndependentSentence() {
        val result = OpeningBasmalaSpeechFilter.filter(
            transcript = "بسم الله الرحمان الرحيم",
            isFinal = true,
            segmentCompleted = true,
            alreadyConsumed = false,
        )

        assertEquals("", result.ayahTranscript)
        assertTrue(result.consumed)
        assertFalse(result.needsCorrection)
        assertTrue(result.detectedOpeningBasmala)
    }

    @Test
    fun partialOpeningBasmalaStaysOutsideAyah() {
        val result = OpeningBasmalaSpeechFilter.filter(
            transcript = "بسم الله",
            isFinal = false,
            segmentCompleted = false,
            alreadyConsumed = false,
        )

        assertEquals("", result.ayahTranscript)
        assertFalse(result.consumed)
        assertFalse(result.needsCorrection)
    }

    @Test
    fun wrongOpeningBasmalaIsNotPassedAsAnAyahWord() {
        val result = OpeningBasmalaSpeechFilter.filter(
            transcript = "بسم الله الرحمان الرحيم",
            isFinal = true,
            segmentCompleted = true,
            alreadyConsumed = false,
        )
        val wrong = OpeningBasmalaSpeechFilter.filter(
            transcript = "بسم الله الرحيم الرحيم",
            isFinal = true,
            segmentCompleted = true,
            alreadyConsumed = false,
        )

        assertEquals("", result.ayahTranscript)
        assertEquals("", wrong.ayahTranscript)
        assertTrue(wrong.needsCorrection)
        assertTrue(wrong.consumed)
    }

    @Test
    fun correctBasmalaAndFirstAyahCanArriveInOneRecognizerResult() {
        val result = OpeningBasmalaSpeechFilter.filter(
            transcript = "بسم الله الرحمان الرحيم الم ذلك",
            isFinal = true,
            segmentCompleted = true,
            alreadyConsumed = false,
        )

        assertEquals("الم ذلك", result.ayahTranscript)
        assertTrue(result.consumed)
        assertFalse(result.needsCorrection)
        assertTrue(result.detectedOpeningBasmala)
    }

    @Test
    fun firstAyahIsPassedInItsOwnSentenceAfterBasmala() {
        val result = OpeningBasmalaSpeechFilter.filter(
            transcript = "الم",
            isFinal = true,
            segmentCompleted = true,
            alreadyConsumed = false,
        )

        assertEquals("الم", result.ayahTranscript)
        assertTrue(result.consumed)
        assertFalse(result.needsCorrection)
        assertFalse(result.detectedOpeningBasmala)
    }
}
