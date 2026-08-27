package com.example.quranoffline

import com.example.quranoffline.data.CorrectionSettings
import com.example.quranoffline.data.TimingSegment
import com.example.quranoffline.data.withCorrectionPadding
import org.junit.Assert.assertEquals
import org.junit.Test

class ReferenceAudioWindowTest {
    @Test
    fun usesHalfOfMeasuredGapsToPreserveWordOnsetAndRelease() {
        val previous = TimingSegment(0, 1, 0, 1000)
        val current = TimingSegment(1, 2, 1200, 2000)
        val next = TimingSegment(2, 3, 2080, 3160)

        val result = current.withCorrectionPadding(CorrectionSettings.DEFAULTS, previous, next)

        assertEquals(1100, result.startMs)
        assertEquals(2040, result.endMs)
    }

    @Test
    fun manualPaddingCannotCrossNeighbouringCanonicalSegment() {
        val previous = TimingSegment(0, 1, 0, 1990)
        val current = TimingSegment(1, 2, 2000, 2100)
        val next = TimingSegment(2, 3, 2110, 3000)
        val settings = CorrectionSettings.DEFAULTS.copy(clipPaddingBeforeMs = 300, clipPaddingAfterMs = 300)

        val result = current.withCorrectionPadding(settings, previous, next)

        assertEquals(1990, result.startMs)
        assertEquals(2110, result.endMs)
    }

    @Test
    fun firstAndLastWordNeverBecomeNegativeOrUseUnknownNeighbourAudio() {
        val first = TimingSegment(0, 1, 0, 1000)
        val last = TimingSegment(3, 4, 9000, 10000)
        val settings = CorrectionSettings.DEFAULTS.copy(clipPaddingBeforeMs = 120, clipPaddingAfterMs = 120)

        val firstResult = first.withCorrectionPadding(settings, previousWord = null, nextWord = null)
        val lastResult = last.withCorrectionPadding(settings, previousWord = null, nextWord = null)

        assertEquals(0, firstResult.startMs)
        assertEquals(1120, firstResult.endMs)
        assertEquals(8880, lastResult.startMs)
        assertEquals(10120, lastResult.endMs)
    }
}
