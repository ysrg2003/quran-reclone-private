package com.example.quranoffline

import com.example.quranoffline.data.OpeningBasmalaSpeechFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningBasmalaTest {
    @Test
    fun completeOpeningBasmalaIsRemovedBeforeFirstAyahWord() {
        val result = OpeningBasmalaSpeechFilter.filter(
            transcript = "بسم الله الرحمن الرحيم الم",
            isFinal = true,
            segmentCompleted = true,
            alreadyConsumed = false,
        )

        assertEquals("الم", result.ayahTranscript)
        assertTrue(result.consumed)
        assertFalse(result.needsCorrection)
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
            transcript = "بسم الله الرحمن الرحيم الم",
            isFinal = true,
            segmentCompleted = true,
            alreadyConsumed = false,
        )
        val wrong = OpeningBasmalaSpeechFilter.filter(
            transcript = "بسم الله الرحيم الرحيم الم",
            isFinal = true,
            segmentCompleted = true,
            alreadyConsumed = false,
        )

        assertEquals("الم", result.ayahTranscript)
        assertEquals("الم", wrong.ayahTranscript)
        assertTrue(wrong.needsCorrection)
        assertTrue(wrong.consumed)
    }

    @Test
    fun nonOpeningFirstWordStartsAyahWithoutWaitingForBasmala() {
        val result = OpeningBasmalaSpeechFilter.filter(
            transcript = "الم",
            isFinal = true,
            segmentCompleted = true,
            alreadyConsumed = false,
        )

        assertEquals("الم", result.ayahTranscript)
        assertTrue(result.consumed)
        assertFalse(result.needsCorrection)
    }
}
