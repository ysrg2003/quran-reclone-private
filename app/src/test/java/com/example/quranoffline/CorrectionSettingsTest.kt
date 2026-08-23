package com.example.quranoffline

import com.example.quranoffline.data.CorrectionSettings
import com.example.quranoffline.data.ResumePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionSettingsTest {
    @Test
    fun defaultsPreserveCurrentPolicy() {
        val defaults = CorrectionSettings.DEFAULTS

        assertTrue(defaults.automaticCorrectionEnabled)
        assertEquals(3, defaults.wholeAyahAfterConsecutiveErrors)
        assertTrue(defaults.playFirstErrorBeforeWholeAyah)
        assertEquals(1, defaults.wordRepeatCount)
        assertEquals(1, defaults.wholeAyahRepeatCount)
        assertEquals(0, defaults.pauseBetweenPartsMs)
        assertEquals(ResumePosition.FIRST_ERROR, defaults.resumePosition)
    }

    @Test
    fun normalizedClampsUnsafeValues() {
        val normalized = CorrectionSettings(
            wholeAyahAfterConsecutiveErrors = 99,
            wordRepeatCount = -4,
            wholeAyahRepeatCount = 99,
            pauseBetweenPartsMs = 999,
            clipPaddingBeforeMs = -1,
            clipPaddingAfterMs = 999,
        ).normalized()

        assertEquals(CorrectionSettings.MAX_ERROR_THRESHOLD, normalized.wholeAyahAfterConsecutiveErrors)
        assertEquals(CorrectionSettings.MIN_REPEAT_COUNT, normalized.wordRepeatCount)
        assertEquals(CorrectionSettings.MAX_REPEAT_COUNT, normalized.wholeAyahRepeatCount)
        assertEquals(CorrectionSettings.PAUSE_OPTIONS_MS.last(), normalized.pauseBetweenPartsMs)
        assertEquals(CorrectionSettings.MIN_PADDING_MS, normalized.clipPaddingBeforeMs)
        assertEquals(CorrectionSettings.MAX_PADDING_MS, normalized.clipPaddingAfterMs)
    }

    @Test
    fun disabledWrongWordCorrectionStillEvaluatesButDoesNotRequestAudio() {
        val tracker = com.example.quranoffline.data.RecitationProgressTracker(
            listOf(ayah("a", "b")),
            correctionSettings = CorrectionSettings.DEFAULTS.copy(correctWrongWords = false),
        )

        val result = tracker.update(com.example.quranoffline.data.RecitationProgressInput("x", true, true))

        assertNull(result.correction)
        assertEquals(com.example.quranoffline.data.WordStatus.EXTRA_OR_UNEXPECTED, result.evaluation.words.first().status)
    }

    @Test
    fun customThresholdUsesWholeAyahOnlyAtConfiguredCount() {
        val tracker = com.example.quranoffline.data.RecitationProgressTracker(
            listOf(ayah("a", "b", "c", "d")),
            correctionSettings = CorrectionSettings.DEFAULTS.copy(wholeAyahAfterConsecutiveErrors = 2),
        )

        val result = tracker.update(com.example.quranoffline.data.RecitationProgressInput("x y", true, true))

        assertEquals(true, result.correction?.readWholeAyah)
    }

    private fun ayah(vararg words: String): com.example.quranoffline.data.QuranAyah =
        com.example.quranoffline.data.QuranAyah(
            surah = 1,
            ayah = 1,
            text = words.joinToString(" "),
            matchText = words.joinToString(" "),
            words = words.mapIndexed { index, word -> com.example.quranoffline.data.QuranWord(index, word, word) },
        )
}
