package com.example.quranoffline

import com.example.quranoffline.data.PronunciationAyah
import com.example.quranoffline.data.PronunciationSpeechMatcher
import com.example.quranoffline.data.PronunciationWord
import com.example.quranoffline.data.QuranWord
import com.example.quranoffline.data.quranWordMatches
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Corpus audit: every Quran word, spoken correctly, must be accepted when the
 * recognizer writes it in ordinary spelling. WORDS_TSV is generated from the
 * app's own Quran asset (columns: ayah, text, matchText, rasmForm, imlaeiForm).
 */
class RecognizerSpellingCorpusTest {
    @Test
    fun everyWordAcceptedInSomePlainRecognizerSpelling() {
        val path = System.getenv("WORDS_TSV")
        assumeTrue(path != null && File(path).exists())
        val failures = mutableListOf<String>()
        var total = 0
        File(path!!).forEachLine { line ->
            val c = line.split('\t')
            if (c.size < 5) return@forEachLine
            total++
            val word = QuranWord(0, c[1], c[2])
            val reference = PronunciationWord(0, c[2], listOf(c[2]), emptySet(), null, null, null, null)
            // The app's voice path: PronunciationSpeechMatcher with the spoken reference.
            val letters = com.example.quranoffline.data.Muqattaat.pronunciationFor(word)
            // Disjoined letters are judged by their spoken letter names, not ordinary spelling.
            val ok = if (letters != null) PronunciationSpeechMatcher.matches(word, reference, letters.spokenText) && !PronunciationSpeechMatcher.matches(word, reference, c[3]) else PronunciationSpeechMatcher.matches(word, reference, c[3]) ||
                PronunciationSpeechMatcher.matches(word, reference, c[4])
            if (!ok) failures += "${c[0]}\t${c[1]}\t${c[3]}\t${c[4]}"
        }
        File(System.getenv("WORDS_OUT") ?: "/tmp/corpus-failures.tsv").writeText(failures.joinToString("\n"))
        println("CORPUS total=$total failures=${failures.size}")
        assertEquals(0, failures.size)
    }

    @Test
    fun writtenSilentAlifSpellingIsAccepted() {
        val kafaru = QuranWord(0, "كَفَرُواْ", "كفرو")
        assertTrue(quranWordMatches(kafaru, "كفروا"))
        assertTrue(quranWordMatches(kafaru, "كفرو"))
        val reference = PronunciationWord(0, "كفرو", listOf("كفرو"), setOf(3), null, null, null, null)
        assertTrue(PronunciationSpeechMatcher.matches(kafaru, reference, "كفروا"))
        assertTrue(PronunciationSpeechMatcher.matches(kafaru, reference, "كفرو"))
        assertTrue(PronunciationSpeechMatcher.matchesAyahPhrase(
            PronunciationAyah(1, 1, "كفرو", listOf(reference)), listOf("كفروا"), listOf(kafaru)))
    }
}
