package com.example.quranoffline

import com.example.quranoffline.data.QuranWord
import com.example.quranoffline.data.RecitationMatcher
import com.example.quranoffline.data.WordEvaluation
import com.example.quranoffline.data.canonicalizeRecognizedTranscript
import com.example.quranoffline.data.quranWordMatchesAllowCanonicalSpelling
import com.example.quranoffline.data.WordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecitationMatcherTest {
    private val expected = WordReferenceFixtures.fatihaFirstThree

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
        val result = RecitationMatcher.evaluate(expected, "بسم الرحمان", hasFinalResult = true)
        assertEquals(2, result.matched)
        assertEquals(1, result.missing)
        assertEquals(0, result.unexpected)
        assertEquals(WordStatus.MISSING, result.words[1].status)
        assertEquals(WordStatus.MATCHED, result.words[2].status)
    }

    @Test
    fun extraObservedWordDoesNotShiftLaterWords() {
        val result = RecitationMatcher.evaluate(expected, "بسم الله الرحمان الرحيم", hasFinalResult = true)
        assertEquals(3, result.matched)
        assertEquals(1, result.unexpected)
        assertEquals(WordStatus.MATCHED, result.words[2].status)
    }

    @Test
    fun partialSkippedWordIsMissingWhenLaterWordWasHeard() {
        val result = RecitationMatcher.evaluate(expected, "بسم الرحمان", hasFinalResult = false)
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
            WordReferenceFixtures.fatihaBasmala[0],
            WordReferenceFixtures.fatihaBasmala[1],
            WordReferenceFixtures.fatihaBasmala[2],
            WordReferenceFixtures.fatihaBasmala[3],
            WordReferenceFixtures.fatihaBasmala[2].copy(index = 4),
            WordReferenceFixtures.fatihaBasmala[3].copy(index = 5),
        )
        val result = RecitationMatcher.evaluate(repeated, "الرحمان الرحيم", hasFinalResult = true)
        assertEquals(2, result.matched)
        assertEquals(WordStatus.MATCHED, result.words[2].status)
        assertEquals(WordStatus.MATCHED, result.words[3].status)
        assertEquals(WordStatus.MISSING, result.words[4].status)
    }

    @Test
    fun preferredCursorChoosesCurrentRepeatedOccurrence() {
        val repeated = listOf(
            WordReferenceFixtures.fatihaBasmala[0],
            WordReferenceFixtures.fatihaBasmala[2].copy(index = 1),
            WordReferenceFixtures.fatihaBasmala[3].copy(index = 2),
            WordReferenceFixtures.fatihaBasmala[2].copy(index = 3),
            WordReferenceFixtures.fatihaBasmala[3].copy(index = 4),
        )
        val result = RecitationMatcher.evaluate(repeated, "الرحمان الرحيم", hasFinalResult = true, preferredExpectedIndex = 3)
        assertEquals(2, result.matched)
        assertEquals(WordStatus.MATCHED, result.words[3].status)
        assertEquals(WordStatus.MATCHED, result.words[4].status)
    }

    @Test
    fun previousJudgementsArePreservedWhenNewSegmentArrives() {
        val previous = RecitationMatcher.evaluate(expected, "بسم الله", hasFinalResult = true)
        val current = RecitationMatcher.evaluate(expected, "الرحمان", hasFinalResult = false, preferredExpectedIndex = 2)
        val merged = RecitationMatcher.mergePrevious(previous, current, preserveBeforeIndex = 2)
        assertEquals(WordStatus.MATCHED, merged.words[0].status)
        assertEquals(WordStatus.MATCHED, merged.words[1].status)
        assertEquals(WordStatus.MATCHED, merged.words[2].status)
    }

    @Test
    fun diacriticsDoNotBreakBasicMatching() {
        val result = RecitationMatcher.evaluate(expected, "بِسْمِ اللَّهِ الرَّحْمَانِ", hasFinalResult = true)
        assertEquals(3, result.matched)
        assertEquals(0, result.unexpected)
    }

    @Test
    fun hamzaSeatVariantOfIyyakaIsMatched() {
        val expectedWord = listOf(WordReferenceFixtures.iyyaka)
        val result = RecitationMatcher.evaluate(expectedWord, "اياك", hasFinalResult = true)
        assertEquals(1, result.matched)
        assertEquals(WordStatus.MATCHED, result.words.single().status)
        assertEquals(0, result.unexpected)
    }

    @Test
    fun drawnAlifSpellingOfAlAlaminIsRequiredAndMatched() {
        val expectedWord = listOf(WordReferenceFixtures.alamin)
        val result = RecitationMatcher.evaluate(expectedWord, "العالمين", hasFinalResult = true)
        assertEquals(1, result.matched)
        assertEquals(WordStatus.MATCHED, result.words.single().status)
        assertEquals(0, result.unexpected)
    }

    @Test
    fun droppingDrawnAlifFromAlAlaminIsRejected() {
        val expectedWord = listOf(WordReferenceFixtures.alamin)
        val result = RecitationMatcher.evaluate(expectedWord, "العلمين", hasFinalResult = true)
        assertEquals(0, result.matched)
        assertEquals(1, result.unexpected)
        assertEquals(WordStatus.EXTRA_OR_UNEXPECTED, result.words.single().status)
    }

    @Test
    fun canonicalAlRahmanAndSpokenAlRahmanAreBothAcceptedInTypedMode() {
        val expectedWord = WordReferenceFixtures.fatihaBasmala[2]
        assertTrue(quranWordMatchesAllowCanonicalSpelling(expectedWord, "الرحمان"))
        assertTrue(quranWordMatchesAllowCanonicalSpelling(expectedWord, "الرحمن"))
    }

    @Test
    fun speechHaCanRepresentCanonicalTaMarbutaWithoutChangingDisplayedText() {
        val expectedWord = listOf(WordReferenceFixtures.prayer)
        val result = RecitationMatcher.evaluate(expectedWord, "الصلاه", hasFinalResult = true)
        assertEquals(1, result.matched)
        assertEquals(WordReferenceFixtures.prayer.text, result.words.single().expected)
        assertEquals(WordStatus.MATCHED, result.words.single().status)
    }

    @Test
    fun acceptedSpeechSpellingIsRenderedWithCanonicalTaMarbuta() {
        val rendered = canonicalizeRecognizedTranscript(
            raw = "ويقيمون الصلاه",
            evaluation = listOf(
                WordEvaluation("ويقيمون", "ويقيمون", WordStatus.MATCHED),
                WordEvaluation("الصلاة", "الصلاه", WordStatus.MATCHED),
            ),
        )
        assertEquals("ويقيمون الصلاة", rendered)
    }

    @Test
    fun silentFinalCarrierWordsRejectSpeechRecognizerWrittenAlif() {
        val expectedWords = listOf(
            WordReferenceFixtures.kafaru,
            WordReferenceFixtures.ashtraw.copy(index = 1),
            WordReferenceFixtures.laqu.copy(index = 2),
        )
        val result = RecitationMatcher.evaluate(expectedWords, "كفروا اشتروا لقوا", hasFinalResult = true)
        assertEquals(0, result.matched)
        assertEquals(3, result.unexpected)
        assertTrue(result.words.all { it.status == WordStatus.EXTRA_OR_UNEXPECTED })
    }

    @Test
    fun silentFinalCarrierWordsAcceptPronunciationWithoutWrittenCarrier() {
        val expectedWords = listOf(
            WordReferenceFixtures.kafaru,
            WordReferenceFixtures.ashtraw.copy(index = 1),
        )
        val result = RecitationMatcher.evaluate(expectedWords, "كفرو اشترو", hasFinalResult = true)
        assertEquals(2, result.matched)
        assertEquals(0, result.unexpected)
    }

    @Test
    fun daggerAlifOverWawUsesTheSpokenAlifForAllEquivalentForms() {
        val expectedWords = listOf(
            WordReferenceFixtures.prayer,
            WordReferenceFixtures.zakat.copy(index = 1),
            WordReferenceFixtures.hayat.copy(index = 2),
        )
        val result = RecitationMatcher.evaluate(expectedWords, "الصلاة الزكاة الحياة", hasFinalResult = true)
        assertEquals(3, result.matched)
        assertEquals(0, result.unexpected)
        assertEquals(listOf(WordReferenceFixtures.prayer.text, WordReferenceFixtures.zakat.text, WordReferenceFixtures.hayat.text), result.words.map { it.expected })
    }

    @Test
    fun acceptedDaggerAlifSpeechIsRenderedWithCanonicalQuranSpelling() {
        val rendered = canonicalizeRecognizedTranscript(
            raw = "أقيموا الصلاة الزكاة",
            evaluation = listOf(
                WordEvaluation("أقيموا", "أقيموا", WordStatus.MATCHED),
                WordEvaluation(WordReferenceFixtures.prayer.text, "الصلاة", WordStatus.MATCHED),
                WordEvaluation(WordReferenceFixtures.zakat.text, "الزكاة", WordStatus.MATCHED),
            ),
        )
        assertEquals("أقيموا ${WordReferenceFixtures.prayer.text} ${WordReferenceFixtures.zakat.text}", rendered)
    }

    @Test
    fun silentFinalAlifWithSmallCircleIsNotPronounced() {
        val expectedWord = listOf(WordReferenceFixtures.kafaru)
        val result = RecitationMatcher.evaluate(expectedWord, "كفرو", hasFinalResult = true)
        assertEquals(1, result.matched)
        assertEquals(0, result.unexpected)
        assertEquals(WordReferenceFixtures.kafaru.text, result.words.single().expected)
    }

    @Test
    fun speechRecognizerWrittenFinalAlifIsRejectedForSilentFinalCarrier() {
        val expectedWord = listOf(WordReferenceFixtures.kafaru)
        val result = RecitationMatcher.evaluate(expectedWord, "كفروا", hasFinalResult = true)
        assertEquals(0, result.matched)
        assertEquals(1, result.unexpected)
        assertEquals(WordStatus.EXTRA_OR_UNEXPECTED, result.words.single().status)
    }

    @Test
    fun silentWawInUlaikaDoesNotDeleteTheLaterWrittenAlif() {
        val expectedWord = listOf(WordReferenceFixtures.ulaika)
        val result = RecitationMatcher.evaluate(expectedWord, "أولئك", hasFinalResult = true)
        assertEquals(1, result.matched)
        assertEquals(WordStatus.MATCHED, result.words.single().status)
        assertEquals(WordReferenceFixtures.ulaika.text, result.words.single().expected)
    }

    @Test
    fun speechRecognizerWrittenFinalAlifIsRejectedForLaqu() {
        val expectedWord = listOf(WordReferenceFixtures.laqu)
        val silent = RecitationMatcher.evaluate(expectedWord, "لقو", hasFinalResult = true)
        val providerWritten = RecitationMatcher.evaluate(expectedWord, "لقوا", hasFinalResult = true)
        assertEquals(1, silent.matched)
        assertEquals(0, providerWritten.matched)
        assertEquals(WordStatus.EXTRA_OR_UNEXPECTED, providerWritten.words.single().status)
    }

    @Test
    fun canonicalHamzaAboveAlifRemainsInVisibleTranscript() {
        val rendered = canonicalizeRecognizedTranscript(
            raw = "وبالاخرة",
            evaluation = listOf(WordEvaluation(WordReferenceFixtures.akhira.text, "بالاخرة", WordStatus.MATCHED)),
        )
        assertEquals(WordReferenceFixtures.akhira.text, rendered)
    }

    @Test
    fun ordinaryHaIsNotAcceptedAsTaMarbuta() {
        val expectedWord = listOf(WordReferenceFixtures.prayer)
        val result = RecitationMatcher.evaluate(expectedWord, "الصلاح", hasFinalResult = true)
        assertEquals(0, result.matched)
        assertEquals(1, result.unexpected)
        assertEquals(WordStatus.EXTRA_OR_UNEXPECTED, result.words.single().status)
    }
}
