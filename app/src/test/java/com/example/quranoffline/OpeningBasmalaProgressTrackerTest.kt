package com.example.quranoffline

import com.example.quranoffline.data.CorrectionKind
import com.example.quranoffline.data.OpeningBasmalaProgressTracker
import com.example.quranoffline.data.QuranAyah
import com.example.quranoffline.data.QuranWord
import com.example.quranoffline.data.RecitationProgressInput
import com.example.quranoffline.data.WordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningBasmalaProgressTrackerTest {
    private fun tracker(): OpeningBasmalaProgressTracker = OpeningBasmalaProgressTracker(
        actualSurah = 2,
        sourceAyah = QuranAyah(
            surah = 1,
            ayah = 1,
            text = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ",
            matchText = "بسم الله الرحمان الرحيم",
            words = listOf("بسم", "الله", "الرحمان", "الرحيم").mapIndexed { index, word ->
                QuranWord(index = index, text = word, matchText = word)
            },
        ),
    )

    @Test
    fun omittedMiddleWordCreatesBarrierAndLeavesLaterWordUncertain() {
        val result = tracker().update(
            RecitationProgressInput(
                transcript = "بسم الله الرحيم",
                isFinal = true,
                segmentCompleted = true,
            ),
        )

        assertEquals(listOf(WordStatus.MATCHED, WordStatus.MATCHED, WordStatus.MISSING, WordStatus.UNCERTAIN), result.evaluation.words.map { it.status })
        assertEquals(2, result.currentWordInAyah)
        assertTrue(result.currentAyahLocked)
        assertEquals(CorrectionKind.SKIPPED_WORD, result.correction?.kind)
        assertEquals(2, result.correction?.localWordIndex)
        assertEquals("الرحيم", result.evaluation.words[3].expected)
        assertEquals(null, result.evaluation.words[3].observed)
    }

    @Test
    fun correctingMissingMiddleWordThenAllowsFollowingWord() {
        val tracker = tracker()
        tracker.update(RecitationProgressInput("بسم الله الرحيم", true, true))

        val result = tracker.update(RecitationProgressInput("الرحمان الرحيم", true, true))

        assertTrue(result.evaluation.words.all { it.status == WordStatus.MATCHED })
        assertEquals("الرحمان", result.evaluation.words[2].observed)
        assertEquals("الرحيم", result.evaluation.words[3].observed)
    }
}
