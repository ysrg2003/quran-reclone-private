package com.example.quranoffline

import com.example.quranoffline.data.PronunciationAyah
import com.example.quranoffline.data.PronunciationSpeechMatcher
import com.example.quranoffline.data.PronunciationWord
import com.example.quranoffline.data.QuranWord
import com.example.quranoffline.data.quranWordMatches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PronunciationSpeechMatcherTest {
    @Test
    fun validAlifMaddMayBeRepeated() {
        val word = WordReferenceFixtures.qala
        val reference = PronunciationWord(0, "قال", listOf("قال"), setOf(1), null, null, null, null)
        assertTrue(PronunciationSpeechMatcher.matches(word, reference, "قااال"))
    }

    @Test
    fun repeatedConsonantWithoutMaddIsNotCollapsed() {
        val word = WordReferenceFixtures.kataba
        val reference = PronunciationWord(0, "كتبت", listOf("كتبت"), emptySet(), null, null, null, null)
        assertFalse(PronunciationSpeechMatcher.matches(word, reference, "كتتبت"))
    }

    @Test
    fun muqattaatLetterNamesAcceptLongMaddOnlyInNames() {
        val word = WordReferenceFixtures.kahyainSad
        assertTrue(quranWordMatches(word, "كاااف ها يا عييين صاااد"))
        assertFalse(quranWordMatches(word, "كككاف ها يا عين صاد"))
    }

    @Test
    fun wholeAyahReferenceMatchesInOrder() {
        val reference = PronunciationAyah(
            2,
            6,
            "ان الذين كفرو",
            listOf(
                PronunciationWord(0, "ان", listOf("ان"), emptySet(), null, null, null, null),
                PronunciationWord(1, "الذين", listOf("الذين"), emptySet(), null, null, null, null),
                PronunciationWord(2, "كفرو", listOf("كفرو"), setOf(3), null, null, null, null),
            ),
        )
        assertTrue(PronunciationSpeechMatcher.matchesAyahPhrase(reference, listOf("إن", "الذين", "كفرو")))
        assertFalse(PronunciationSpeechMatcher.matchesAyahPhrase(reference, listOf("إن", "كفرو", "الذين")))
    }

    @Test
    fun constrainedMaddCollapseReturnsCanonicalSpelling() {
        assertEquals("قال", PronunciationSpeechMatcher.collapseValidMaddRepeats("قااال", "قال", setOf(1)))
        assertEquals("كتتبت", PronunciationSpeechMatcher.collapseValidMaddRepeats("كتتبت", "كتبت", setOf(1)))
    }

    @Test
    fun pronouncedDaggerAlifIsRequiredInPronunciationReference() {
        val word = WordReferenceFixtures.alamin
        val reference = PronunciationWord(0, "العالمين", listOf("العالمين"), setOf(3, 6), null, null, null, null)
        assertTrue(PronunciationSpeechMatcher.matches(word, reference, "العالمين"))
        assertFalse(PronunciationSpeechMatcher.matches(word, reference, "العلمين"))
    }

    @Test
    fun ordinaryWordDoesNotCollapseRepeatedNonMaddAlifInWholeAyah() {
        val reference = PronunciationAyah(
            1,
            1,
            "اكل",
            listOf(PronunciationWord(0, "اكل", listOf("اكل"), emptySet(), null, null, null, null)),
        )
        assertFalse(PronunciationSpeechMatcher.matchesAyahPhrase(reference, listOf("اااكل")))
    }
}
