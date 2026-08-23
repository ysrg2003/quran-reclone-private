package com.example.quranoffline

import com.example.quranoffline.data.CorrectionKind
import com.example.quranoffline.data.QuranAyah
import com.example.quranoffline.data.QuranWord
import com.example.quranoffline.data.RecitationProgressInput
import com.example.quranoffline.data.RecitationProgressTracker
import com.example.quranoffline.data.WordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecitationProgressTrackerTest {
    private fun ayah(number: Int, vararg words: String): QuranAyah = QuranAyah(
        surah = 1,
        ayah = number,
        text = words.joinToString(" "),
        matchText = words.joinToString(" "),
        words = words.mapIndexed { index, word -> QuranWord(index, word, word) },
    )

    private fun final(text: String) = RecitationProgressInput(text, isFinal = true, segmentCompleted = true)

    @Test
    fun wrongWordProducesOneCorrectionForExpectedPosition() {
        val tracker = RecitationProgressTracker(listOf(ayah(1, "a", "b")))

        val result = tracker.update(final("x"))

        assertEquals(CorrectionKind.WRONG_WORD, result.correction?.kind)
        assertEquals(0, result.correction?.ayahIndex)
        assertEquals(0, result.correction?.localWordIndex)
        assertEquals(WordStatus.EXTRA_OR_UNEXPECTED, result.evaluation.words[0].status)
        assertEquals(1, result.currentWordInAyah)
    }

    @Test
    fun skippedWordProducesCorrectionWithoutShiftingLaterMatch() {
        val tracker = RecitationProgressTracker(listOf(ayah(1, "a", "b", "c")))

        val result = tracker.update(final("a c"))

        assertEquals(CorrectionKind.SKIPPED_WORD, result.correction?.kind)
        assertEquals(1, result.correction?.localWordIndex)
        assertEquals(WordStatus.MISSING, result.evaluation.words[1].status)
        assertEquals(WordStatus.MATCHED, result.evaluation.words[2].status)
    }

    @Test
    fun clearJumpKeepsImmediateNextAyahAsExpectedContext() {
        val tracker = RecitationProgressTracker(
            listOf(ayah(1, "a", "b"), ayah(2, "c", "d"), ayah(3, "e", "f")),
        )

        val result = tracker.update(final("e f"))

        assertEquals(CorrectionKind.FIRST_WORD_EXPECTED_AYAH, result.correction?.kind)
        assertEquals(1, result.correction?.ayahIndex)
        assertEquals(0, result.correction?.localWordIndex)
        assertEquals(0, result.currentAyahIndex)
        assertEquals(0, result.evaluation.matched)
    }

    @Test
    fun rollbackWithinCurrentAyahIsAllowed() {
        val tracker = RecitationProgressTracker(listOf(ayah(1, "a", "b", "c", "d")))
        tracker.update(final("a b"))

        val result = tracker.update(final("a b a"))

        assertNull(result.correction)
        assertEquals(WordStatus.MATCHED, result.evaluation.words[0].status)
        assertEquals(1, result.currentWordInAyah)
    }

    @Test
    fun partialDoesNotAdvanceOrCreateCorrection() {
        val tracker = RecitationProgressTracker(listOf(ayah(1, "a", "b")))

        val result = tracker.update(RecitationProgressInput("x", isFinal = false, segmentCompleted = false))

        assertNull(result.correction)
        assertEquals(0, result.currentWordInAyah)
        assertEquals(0, result.evaluation.matched)
        assertTrue(result.transcriptChanged)
    }

    @Test
    fun repeatedAyahUsesCurrentAyahNotLaterDuplicate() {
        val tracker = RecitationProgressTracker(listOf(ayah(1, "a", "b"), ayah(2, "a", "b")))

        val first = tracker.update(final("a b"))
        val second = tracker.update(final("a b a"))

        assertEquals(1, first.currentAyahIndex)
        assertEquals(WordStatus.MATCHED, first.evaluation.words[0].status)
        assertEquals(WordStatus.MATCHED, first.evaluation.words[1].status)
        assertEquals(WordStatus.MATCHED, second.evaluation.words[2].status)
    }

    @Test
    fun correctionKeyIsDeduplicatedUntilWordIsResolved() {
        val tracker = RecitationProgressTracker(listOf(ayah(1, "a", "b", "c")))

        val first = tracker.update(final("x"))
        val repeated = tracker.update(final("x"))
        val intermediateWrong = tracker.update(final("x z"))
        val resolved = tracker.update(final("x z b"))
        val nextWrong = tracker.update(final("x z b y"))

        assertEquals(CorrectionKind.WRONG_WORD, first.correction?.kind)
        assertNull(repeated.correction)
        assertEquals(CorrectionKind.WRONG_WORD, intermediateWrong.correction?.kind)
        assertNull(resolved.correction)
        assertEquals(CorrectionKind.WRONG_WORD, nextWrong.correction?.kind)
        assertEquals(2, nextWrong.correction?.localWordIndex)
    }
}
