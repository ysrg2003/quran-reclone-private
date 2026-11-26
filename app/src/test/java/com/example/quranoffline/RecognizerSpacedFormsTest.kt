package com.example.quranoffline

import com.example.quranoffline.data.Muqattaat
import com.example.quranoffline.data.PronunciationSpeechMatcher
import com.example.quranoffline.data.PronunciationWord
import com.example.quranoffline.data.QuranWord
import com.example.quranoffline.data.RecognizerSpacedForms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognizerSpacedFormsTest {
    @Test
    fun twoTokenRecognizerSpellingIsJoinedAndMatches() {
        RecognizerSpacedForms.register(listOf("يا أيها", "يا قوم"))
        assertEquals(listOf("يا أيها", "الذين"), Muqattaat.coalesceSpeechTokens(listOf("يا", "أيها", "الذين")))
        assertEquals(listOf("قال", "يا"), Muqattaat.coalesceSpeechTokens(listOf("قال", "يا")))
        val word = QuranWord(0, "يَٰٓأَيُّهَا", "ياايها", recognizedForms = setOf("يا أيها"))
        val ref = PronunciationWord(0, "ياايها", listOf("ياايها"), setOf(1), null, null, null, null)
        assertTrue(PronunciationSpeechMatcher.matches(word, ref, "يا أيها"))
        assertFalse(PronunciationSpeechMatcher.matches(word, ref, "يا ايه"))
        RecognizerSpacedForms.register(emptyList())
    }

    @Test
    fun standardSpellingWithoutDaggerAlifIsAcceptedWhenListed() {
        val word = QuranWord(0, "ذَٰلِكَ", "ذالك", recognizedForms = setOf("ذلك"))
        val ref = PronunciationWord(0, "ذالك", listOf("ذالك"), setOf(1), null, null, null, null)
        assertTrue(PronunciationSpeechMatcher.matches(word, ref, "ذلك"))
        assertTrue(PronunciationSpeechMatcher.matches(word, ref, "ذالك"))
        assertFalse(PronunciationSpeechMatcher.matches(word, ref, "ذك"))
    }
}
