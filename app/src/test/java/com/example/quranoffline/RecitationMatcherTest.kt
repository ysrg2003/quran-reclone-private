package com.example.quranoffline

import com.example.quranoffline.data.QuranWord
import com.example.quranoffline.data.RecitationMatcher
import com.example.quranoffline.data.WordEvaluation
import com.example.quranoffline.data.canonicalizeRecognizedTranscript
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

    @Test
    fun hamzaSeatVariantOfIyyakaIsMatched() {
        val expectedWord = listOf(QuranWord(0, "إِيَّاكَ", "إياك"))
        val result = RecitationMatcher.evaluate(expectedWord, "اياك", hasFinalResult = true)
        assertEquals(1, result.matched)
        assertEquals(WordStatus.MATCHED, result.words.single().status)
        assertEquals(0, result.unexpected)
    }

    @Test
    fun alifSpellingVariantOfAlAlaminIsMatched() {
        val expectedWord = listOf(QuranWord(0, "ٱلْعَٰلَمِينَ", "العلمين"))
        val result = RecitationMatcher.evaluate(expectedWord, "العالمين", hasFinalResult = true)
        assertEquals(1, result.matched)
        assertEquals(WordStatus.MATCHED, result.words.single().status)
        assertEquals(0, result.unexpected)
    }

    @Test
    fun speechHaCanRepresentCanonicalTaMarbutaWithoutChangingDisplayedText() {
        val expectedWord = listOf(QuranWord(0, "الصلاة", "الصلاة"))
        val result = RecitationMatcher.evaluate(expectedWord, "الصلاه", hasFinalResult = true)
        assertEquals(1, result.matched)
        assertEquals("الصلاة", result.words.single().expected)
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
    fun daggerAlifOverWawUsesTheSpokenAlifForAllEquivalentForms() {
        val expectedWords = listOf(
            QuranWord(0, "ٱلصَّلَوٰةَ", "الصلوة"),
            QuranWord(1, "ٱلزَّكَوٰةَ", "الزكوة"),
            QuranWord(2, "ٱلْحَيَوٰةِ", "الحيوة"),
        )
        val result = RecitationMatcher.evaluate(expectedWords, "الصلاة الزكاة الحياة", hasFinalResult = true)
        assertEquals(3, result.matched)
        assertEquals(0, result.unexpected)
        assertEquals(listOf("ٱلصَّلَوٰةَ", "ٱلزَّكَوٰةَ", "ٱلْحَيَوٰةِ"), result.words.map { it.expected })
    }

    @Test
    fun acceptedDaggerAlifSpeechIsRenderedWithCanonicalQuranSpelling() {
        val rendered = canonicalizeRecognizedTranscript(
            raw = "أقيموا الصلاة الزكاة",
            evaluation = listOf(
                WordEvaluation("أقيموا", "أقيموا", WordStatus.MATCHED),
                WordEvaluation("ٱلصَّلَوٰةَ", "الصلاة", WordStatus.MATCHED),
                WordEvaluation("ٱلزَّكَوٰةَ", "الزكاة", WordStatus.MATCHED),
            ),
        )
        assertEquals("أقيموا ٱلصَّلَوٰةَ ٱلزَّكَوٰةَ", rendered)
    }

    @Test
    fun silentFinalAlifWithSmallCircleIsNotRequiredInSpeech() {
        val expectedWord = listOf(QuranWord(0, "كَفَرُوا۟", "كفروا"))
        val result = RecitationMatcher.evaluate(expectedWord, "كفرو", hasFinalResult = true)
        assertEquals(1, result.matched)
        assertEquals(0, result.unexpected)
        assertEquals("كَفَرُوا۟", result.words.single().expected)
    }

    @Test
    fun silentFinalAlifIsStillRejectedWhenActuallyPronounced() {
        val expectedWord = listOf(QuranWord(0, "كَفَرُوا۟", "كفروا"))
        val result = RecitationMatcher.evaluate(expectedWord, "كفروا", hasFinalResult = true)
        assertEquals(0, result.matched)
        assertEquals(1, result.unexpected)
        assertEquals(WordStatus.EXTRA_OR_UNEXPECTED, result.words.single().status)
    }

    @Test
    fun silentWawInUlaikaDoesNotDeleteTheLaterWrittenAlif() {
        val expectedWord = listOf(QuranWord(0, "أُو۟لَٰٓئِكَ", "أولئك"))
        val result = RecitationMatcher.evaluate(expectedWord, "أولئك", hasFinalResult = true)
        assertEquals(1, result.matched)
        assertEquals(WordStatus.MATCHED, result.words.single().status)
        assertEquals("أُو۟لَٰٓئِكَ", result.words.single().expected)
    }

    @Test
    fun silentMarkedAlifInLaquRejectsPronouncedAlif() {
        val expectedWord = listOf(QuranWord(0, "لَقُوا۟", "لقوا"))
        val silent = RecitationMatcher.evaluate(expectedWord, "لقو", hasFinalResult = true)
        val pronounced = RecitationMatcher.evaluate(expectedWord, "لقوا", hasFinalResult = true)
        assertEquals(1, silent.matched)
        assertEquals(0, pronounced.matched)
        assertEquals(WordStatus.EXTRA_OR_UNEXPECTED, pronounced.words.single().status)
    }

    @Test
    fun canonicalHamzaAboveAlifRemainsInVisibleTranscript() {
        val rendered = canonicalizeRecognizedTranscript(
            raw = "بالاخرة",
            evaluation = listOf(WordEvaluation("بِٱلْءَاخِرَةِ", "بالاخرة", WordStatus.MATCHED)),
        )
        assertEquals("بِٱلْءَاخِرَةِ", rendered)
    }

    @Test
    fun ordinaryHaIsNotAcceptedAsTaMarbuta() {
        val expectedWord = listOf(QuranWord(0, "الصلاة", "الصلاة"))
        val result = RecitationMatcher.evaluate(expectedWord, "الصلاح", hasFinalResult = true)
        assertEquals(0, result.matched)
        assertEquals(1, result.unexpected)
        assertEquals(WordStatus.EXTRA_OR_UNEXPECTED, result.words.single().status)
    }
}
