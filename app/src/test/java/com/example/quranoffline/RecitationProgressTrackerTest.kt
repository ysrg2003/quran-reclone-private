package com.example.quranoffline

import com.example.quranoffline.data.CorrectionKind
import com.example.quranoffline.data.CorrectionSettings
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
        assertEquals(0, result.currentWordInAyah)
    }

    @Test
    fun oneWrongWordRequestsWordOnlyCorrection() {
        val tracker = RecitationProgressTracker(listOf(ayah(1, "a", "b", "c", "d")))

        val result = tracker.update(final("x"))

        assertEquals(false, result.correction?.readWholeAyah)
    }

    @Test
    fun twoConsecutiveWrongWordsStillRequestWordOnlyCorrection() {
        val tracker = RecitationProgressTracker(listOf(ayah(1, "a", "b", "c", "d")))

        val result = tracker.update(final("x y"))

        assertEquals(false, result.correction?.readWholeAyah)
        assertEquals(WordStatus.EXTRA_OR_UNEXPECTED, result.evaluation.words[0].status)
        assertEquals(WordStatus.UNCERTAIN, result.evaluation.words[1].status)
    }

    @Test
    fun moreThanTwoConsecutiveWrongWordsRequestWholeAyahCorrection() {
        val tracker = RecitationProgressTracker(listOf(ayah(1, "a", "b", "c", "d", "e")))

        val result = tracker.update(final("x y z"))

        assertEquals(true, result.correction?.readWholeAyah)
        assertEquals(WordStatus.EXTRA_OR_UNEXPECTED, result.evaluation.words[0].status)
        assertEquals(WordStatus.UNCERTAIN, result.evaluation.words[1].status)
        assertEquals(WordStatus.UNCERTAIN, result.evaluation.words[2].status)
    }

    @Test
    fun skippedWordProducesCorrectionWithoutShiftingLaterMatch() {
        val tracker = RecitationProgressTracker(listOf(ayah(1, "a", "b", "c")))

        val result = tracker.update(final("a c"))

        assertEquals(CorrectionKind.SKIPPED_WORD, result.correction?.kind)
        assertEquals(1, result.correction?.localWordIndex)
        assertEquals(WordStatus.MISSING, result.evaluation.words[1].status)
        assertEquals(WordStatus.UNCERTAIN, result.evaluation.words[2].status)
    }

    @Test
    fun wordsAfterAnErrorReturnToDefaultEvenIfTheyWerePreviouslyMatched() {
        val tracker = RecitationProgressTracker(listOf(ayah(1, "a", "b", "c", "d"), ayah(2, "e")))
        tracker.update(final("a b c"))

        val result = tracker.update(final("a x c"))

        assertEquals(CorrectionKind.WRONG_WORD, result.correction?.kind)
        assertEquals(WordStatus.EXTRA_OR_UNEXPECTED, result.evaluation.words[1].status)
        assertEquals(WordStatus.UNCERTAIN, result.evaluation.words[2].status)
        assertEquals(WordStatus.UNCERTAIN, result.evaluation.words[3].status)
        assertEquals(0, result.currentAyahIndex)
        assertEquals(1, result.currentWordInAyah)
    }

    @Test
    fun clearJumpKeepsImmediateNextAyahAsExpectedContext() {
        val tracker = RecitationProgressTracker(
            listOf(ayah(1, "a", "b"), ayah(2, "c", "d"), ayah(3, "e", "f")),
        )

        val result = tracker.update(final("e f"))

        assertEquals(CorrectionKind.SKIPPED_WORD, result.correction?.kind)
        assertEquals(0, result.correction?.ayahIndex)
        assertEquals(0, result.correction?.localWordIndex)
        assertEquals(0, result.currentAyahIndex)
        assertEquals(0, result.evaluation.matched)
        assertEquals(WordStatus.MISSING, result.evaluation.words[0].status)
        assertEquals(WordStatus.MISSING, result.evaluation.words[1].status)
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
    fun trackerDoesNotCorrectHamzaSeatVariant() {
        val tracker = RecitationProgressTracker(
            listOf(
                QuranAyah(
                    surah = 1,
                    ayah = 5,
                    text = "إِيَّاكَ",
                    matchText = "إياك",
                    words = listOf(QuranWord(0, "إِيَّاكَ", "إياك")),
                ),
            ),
        )

        val result = tracker.update(final("اياك"))

        assertEquals(WordStatus.MATCHED, result.evaluation.words.single().status)
        assertNull(result.correction)
    }

    @Test
    fun trackerDoesNotCorrectMalikSpellingVariant() {
        val tracker = RecitationProgressTracker(
            listOf(
                QuranAyah(
                    surah = 1,
                    ayah = 4,
                    text = "مَٰلِكِ يَوْمِ الدِّينِ",
                    matchText = "ملك يوم الدين",
                    words = listOf(
                        QuranWord(0, "مَٰلِكِ", "ملك"),
                        QuranWord(1, "يَوْمِ", "يوم"),
                        QuranWord(2, "الدِّينِ", "الدين"),
                    ),
                ),
            ),
        )

        val result = tracker.update(final("مالك يوم الدين"))

        assertEquals(WordStatus.MATCHED, result.evaluation.words[0].status)
        assertEquals(WordStatus.MATCHED, result.evaluation.words[1].status)
        assertEquals(WordStatus.MATCHED, result.evaluation.words[2].status)
        assertNull(result.correction)
    }

    @Test
    fun trackerDoesNotCorrectAlAlaminSpellingVariant() {
        val tracker = RecitationProgressTracker(
            listOf(
                QuranAyah(
                    surah = 1,
                    ayah = 2,
                    text = "ٱلْعَٰلَمِينَ",
                    matchText = "العلمين",
                    words = listOf(QuranWord(0, "ٱلْعَٰلَمِينَ", "العلمين")),
                ),
            ),
        )

        val result = tracker.update(final("العالمين"))

        assertEquals(WordStatus.MATCHED, result.evaluation.words.single().status)
        assertNull(result.correction)
    }

    @Test
    fun chainedWrongWordsKeepFirstCorrectionAndMarkEachWrongPosition() {
        val tracker = RecitationProgressTracker(listOf(ayah(1, "a", "b", "c", "d")))

        val result = tracker.update(final("x y"))

        assertEquals(CorrectionKind.WRONG_WORD, result.correction?.kind)
        assertEquals(0, result.correction?.localWordIndex)
        assertEquals(WordStatus.EXTRA_OR_UNEXPECTED, result.evaluation.words[0].status)
        assertEquals(WordStatus.UNCERTAIN, result.evaluation.words[1].status)
        assertEquals(0, result.currentWordInAyah)
    }

    @Test
    fun jumpBeforeAyahCompletionMarksRemainingCurrentAyah() {
        val tracker = RecitationProgressTracker(
            listOf(ayah(1, "a", "b", "c"), ayah(2, "d", "e"), ayah(3, "f", "g")),
        )

        val result = tracker.update(final("f g"))

        assertEquals(CorrectionKind.SKIPPED_WORD, result.correction?.kind)
        assertEquals(0, result.correction?.ayahIndex)
        assertEquals(WordStatus.MISSING, result.evaluation.words[0].status)
        assertEquals(WordStatus.MISSING, result.evaluation.words[1].status)
        assertEquals(WordStatus.MISSING, result.evaluation.words[2].status)
        assertEquals(0, result.currentAyahIndex)
    }

    @Test
    fun resumeAcceptsAnyClearWordInsideCorrectionAyah() {
        val tracker = RecitationProgressTracker(listOf(ayah(1, "a", "b", "c", "d")))
        tracker.update(final("a x"))

        tracker.resumeFrom(0, 1)
        val result = tracker.update(final("b c"))

        assertNull(result.correction)
        assertEquals(WordStatus.MATCHED, result.evaluation.words[1].status)
        assertEquals(WordStatus.MATCHED, result.evaluation.words[2].status)
        assertEquals(3, result.currentWordInAyah)
    }

    @Test
    fun previousAyahCanBeSelectedAsNewSessionStart() {
        val tracker = RecitationProgressTracker(listOf(ayah(1, "a", "b"), ayah(2, "c", "d")))
        tracker.update(final("a b"))

        tracker.startFromAyah(0)
        val result = tracker.update(final("a"))

        assertEquals(0, result.currentAyahIndex)
        assertEquals(1, result.currentWordInAyah)
        assertEquals(WordStatus.MATCHED, result.evaluation.words[0].status)
        assertEquals(WordStatus.UNCERTAIN, result.evaluation.words[1].status)
        assertNull(result.correction)
    }

    @Test
    fun automaticPreviousAyahStartsWhenTheReaderRepeatsPreviousAyah() {
        val tracker = RecitationProgressTracker(
            listOf(ayah(1, "a", "b"), ayah(2, "c", "d")),
        )
        tracker.update(final("a b"))

        val result = tracker.update(final("a b a b"))

        assertNull(result.correction)
        assertEquals(1, result.currentAyahIndex)
        assertEquals(0, result.currentWordInAyah)
        assertEquals(WordStatus.MATCHED, result.evaluation.words[0].status)
        assertEquals(WordStatus.MATCHED, result.evaluation.words[1].status)

        val disabled = RecitationProgressTracker(
            listOf(ayah(1, "a", "b"), ayah(2, "c", "d")),
            correctionSettings = CorrectionSettings.DEFAULTS.copy(automaticPreviousAyah = false),
        )
        disabled.update(final("a b"))
        val disabledResult = disabled.update(final("a b a b"))

        assertEquals(CorrectionKind.WRONG_WORD, disabledResult.correction?.kind)
        assertEquals(1, disabledResult.correction?.ayahIndex)
    }

    @Test
    fun automaticPreviousAyahDoesNotTriggerWhenFirstWordMatchesCurrentAyah() {
        val tracker = RecitationProgressTracker(
            listOf(ayah(1, "a", "b"), ayah(2, "a", "c")),
        )
        tracker.update(final("a b"))

        val result = tracker.update(final("a b a b"))

        assertEquals(CorrectionKind.WRONG_WORD, result.correction?.kind)
        assertEquals(1, result.correction?.ayahIndex)
        assertEquals(WordStatus.EXTRA_OR_UNEXPECTED, result.evaluation.words[3].status)
    }

    @Test
    fun longWordPrefixDoesNotCreateCorrectionBeforeCompletedWordArrives() {
        val tracker = RecitationProgressTracker(
            listOf(
                QuranAyah(
                    surah = 1,
                    ayah = 7,
                    text = "الضالين",
                    matchText = "الضالين",
                    words = listOf(QuranWord(0, "الضالين", "الضالين")),
                ),
            ),
        )

        val partial = tracker.update(RecitationProgressInput("الضال", isFinal = false, segmentCompleted = false))
        val prefix = tracker.update(final("الضال"))
        val completed = tracker.update(final("الضالين"))

        assertNull(partial.correction)
        assertNull(prefix.correction)
        assertEquals(0, prefix.currentWordInAyah)
        assertEquals(WordStatus.UNCERTAIN, prefix.evaluation.words.single().status)
        assertNull(completed.correction)
        assertEquals(WordStatus.MATCHED, completed.evaluation.words.single().status)
    }

    @Test
    fun wholeAyahThresholdIsIndependentFromFirstWordPlaybackOrder() {
        val tracker = RecitationProgressTracker(
            listOf(ayah(1, "a", "b", "c")),
            correctionSettings = CorrectionSettings.DEFAULTS.copy(playFirstErrorBeforeWholeAyah = false),
        )

        val result = tracker.update(final("x y z"))

        assertEquals(true, result.correction?.readWholeAyah)
    }

    @Test
    fun lastWrongWordLocksCurrentAyahUntilItIsCorrected() {
        val tracker = RecitationProgressTracker(listOf(ayah(1, "a", "b"), ayah(2, "c", "d")))

        val wrong = tracker.update(final("a x"))

        assertEquals(0, wrong.currentAyahIndex)
        assertEquals(1, wrong.currentWordInAyah)
        assertEquals(WordStatus.EXTRA_OR_UNEXPECTED, wrong.evaluation.words[1].status)
        assertEquals(WordStatus.UNCERTAIN, wrong.evaluation.words[2].status)

        tracker.resumeFrom(0, 1)
        val corrected = tracker.update(final("b"))

        assertEquals(1, corrected.currentAyahIndex)
        assertEquals(0, corrected.currentWordInAyah)
        assertEquals(WordStatus.MATCHED, corrected.evaluation.words[1].status)
        assertEquals(1, corrected.correctedErrors.size)
        assertEquals("x", corrected.correctedErrors.single().observed)
        assertEquals("b", corrected.correctedErrors.single().correctedWith)
    }

    @Test
    fun futureAyahWordsAreNotEvaluatedWhenReaderJumpsBeforeCurrentAyahIsGreen() {
        val tracker = RecitationProgressTracker(
            listOf(ayah(1, "a", "b", "c"), ayah(2, "d", "e"), ayah(3, "f", "g")),
        )

        val result = tracker.update(final("f g"))

        assertEquals(0, result.currentAyahIndex)
        assertEquals(CorrectionKind.SKIPPED_WORD, result.correction?.kind)
        assertEquals(WordStatus.MISSING, result.evaluation.words[0].status)
        assertEquals(WordStatus.MISSING, result.evaluation.words[1].status)
        assertEquals(WordStatus.MISSING, result.evaluation.words[2].status)
        assertEquals(WordStatus.UNCERTAIN, result.evaluation.words[3].status)
        assertEquals(WordStatus.UNCERTAIN, result.evaluation.words[4].status)
        assertEquals(WordStatus.UNCERTAIN, result.evaluation.words[5].status)
        assertEquals(WordStatus.UNCERTAIN, result.evaluation.words[6].status)
    }

    @Test
    fun ayahLockStillStopsFutureWordsWhenAutomaticCorrectionIsDisabled() {
        val tracker = RecitationProgressTracker(
            listOf(ayah(1, "a", "b", "c"), ayah(2, "d", "e"), ayah(3, "f", "g")),
            correctionSettings = CorrectionSettings.DEFAULTS.copy(automaticCorrectionEnabled = false),
        )

        val result = tracker.update(final("f g"))

        assertNull(result.correction)
        assertEquals(WordStatus.MISSING, result.evaluation.words[0].status)
        assertEquals(WordStatus.UNCERTAIN, result.evaluation.words[3].status)
        assertEquals(WordStatus.UNCERTAIN, result.evaluation.words[5].status)
        assertEquals(0, result.currentAyahIndex)
    }

    @Test
    fun correctingMissingWordsProducesHistoryEntriesAndUnlocksNextAyah() {
        val tracker = RecitationProgressTracker(listOf(ayah(1, "a", "b"), ayah(2, "c", "d")))
        tracker.update(final("c d"))

        tracker.resumeFrom(0, 0)
        val corrected = tracker.update(final("a b"))

        assertEquals(1, corrected.currentAyahIndex)
        assertEquals(2, corrected.correctedErrors.size)
        assertTrue(corrected.correctedErrors.all { it.kind == CorrectionKind.SKIPPED_WORD })
        assertEquals(WordStatus.MATCHED, corrected.evaluation.words[0].status)
        assertEquals(WordStatus.MATCHED, corrected.evaluation.words[1].status)
    }

    @Test
    fun correctionKeyIsDeduplicatedUntilWordIsResolved() {
        val tracker = RecitationProgressTracker(listOf(ayah(1, "a", "b", "c")))

        val first = tracker.update(final("x"))
        val repeated = tracker.update(final("x"))
        val intermediateWrong = tracker.update(final("x z"))
        assertEquals(CorrectionKind.WRONG_WORD, first.correction?.kind)
        assertNull(repeated.correction)
        assertNull(intermediateWrong.correction)
        assertEquals(WordStatus.UNCERTAIN, intermediateWrong.evaluation.words[1].status)

        tracker.resumeFrom(0, 0)
        val resolved = tracker.update(final("a"))
        val nextWrong = tracker.update(final("a y"))

        assertNull(resolved.correction)
        assertEquals(CorrectionKind.WRONG_WORD, nextWrong.correction?.kind)
        assertEquals(1, nextWrong.correction?.localWordIndex)
    }
}
