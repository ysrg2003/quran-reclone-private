package com.example.quranoffline

import com.example.quranoffline.data.QuranAyah
import com.example.quranoffline.data.QuranWord
import com.example.quranoffline.data.PronunciationWord
import com.example.quranoffline.data.TimingSegment
import com.example.quranoffline.data.TimingWordMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TimingWordMapperTest {
    @Test
    fun ordinalFallbackMapsWordsWhenAlignLeavesGaps() {
        // Synthetic words: this test covers timing-index fallback, not Quran spelling.
        val ayah = QuranAyah(10, 1, "word0 word1 word2 word3 word4", "word0 word1 word2 word3 word4", (0..4).map { QuranWord(it, "word$it", "word$it") })
        val timings = listOf(
            TimingSegment(0, 1, 400, 7200),
            TimingSegment(2, 3, 7210, 7480),
            TimingSegment(4, 5, 7490, 8700),
            TimingSegment(5, 6, 8710, 11670),
        )
        assertEquals(400, TimingWordMapper.forWord(ayah, timings, 0)!!.startMs)
        assertEquals(7210, TimingWordMapper.forWord(ayah, timings, 1)!!.startMs)
        assertEquals(8710, TimingWordMapper.forWord(ayah, timings, 4)!!.startMs)
    }

    @Test
    fun explicitWordMappingSpansAllLegacySegmentsForMergedWord() {
        val ayah = QuranAyah(36, 22, "merged", "merged", listOf(QuranWord(0, "merged", "merged")))
        val timings = listOf(
            TimingSegment(0, 1, 100, 200),
            TimingSegment(1, 2, 210, 300),
            TimingSegment(2, 3, 310, 400),
        )
        val pronunciation = PronunciationWord(
            index = 0,
            spoken = "merged",
            spokenTokens = listOf("merged"),
            maddPositions = emptySet(),
            audioStartMs = 210,
            audioEndMs = 400,
            audioWordStart = 1,
            audioWordEnd = 3,
            legacyWordIndices = listOf(1, 2),
        )
        val mapped = TimingWordMapper.forWord(ayah, timings, 0, pronunciation)
        assertNotNull(mapped)
        assertEquals(1, mapped!!.wordStart)
        assertEquals(3, mapped.wordEnd)
        assertEquals(210, mapped.startMs)
        assertEquals(400, mapped.endMs)
    }

    @Test
    fun wholeAyahUsesFirstAndLastAudioBoundsEvenWhenIndexesDiffer() {
        // Synthetic token: whole-ayah audio bounds are independent of Quran text.
        val ayah = QuranAyah(13, 1, "synthetic-token", "synthetic-token", listOf(QuranWord(0, "synthetic-token", "synthetic-token")))
        val timings = listOf(TimingSegment(0, 1, 70, 10880), TimingSegment(15, 16, 27500, 30380))
        val whole = TimingWordMapper.wholeAyah(ayah, timings)
        assertNotNull(whole)
        assertEquals(0, whole!!.wordStart)
        assertEquals(1, whole.wordEnd)
        assertEquals(70, whole.startMs)
        assertEquals(30380, whole.endMs)
    }
}
