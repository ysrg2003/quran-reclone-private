package com.example.quranoffline

import com.example.quranoffline.data.QuranAyah
import com.example.quranoffline.data.QuranWord
import com.example.quranoffline.data.TimingSegment
import com.example.quranoffline.data.TimingWordMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TimingWordMapperTest {
    @Test
    fun ordinalMappingKeepsWordClipsBoundToTheirOwnTiming() {
        val ayah = QuranAyah(
            10,
            1,
            "word0 word1 word2 word3",
            "word0 word1 word2 word3",
            (0..3).map { QuranWord(it, "word$it", "word$it") },
        )
        val timings = listOf(
            TimingSegment(0, 1, 400, 7200),
            TimingSegment(2, 3, 7210, 7480),
            TimingSegment(4, 5, 7490, 8700),
            TimingSegment(5, 6, 8710, 11670),
        )

        assertEquals(400, TimingWordMapper.forWord(ayah, timings, 0)!!.startMs)
        assertEquals(7210, TimingWordMapper.forWord(ayah, timings, 1)!!.startMs)
        assertEquals(8710, TimingWordMapper.forWord(ayah, timings, 3)!!.startMs)
        assertNull(TimingWordMapper.forWord(ayah, timings, 4))
    }

    @Test
    fun wholeAyahStartsAtFileOffsetZeroEvenWhenFirstWordCueStartsLater() {
        val ayah = QuranAyah(
            13,
            1,
            "synthetic-token",
            "synthetic-token",
            listOf(QuranWord(0, "synthetic-token", "synthetic-token")),
        )
        val timings = listOf(
            TimingSegment(0, 1, 70, 10880),
            TimingSegment(15, 16, 27500, 30380),
        )

        val whole = TimingWordMapper.wholeAyah(ayah, timings)

        assertNotNull(whole)
        assertEquals(0, whole!!.wordStart)
        assertEquals(1, whole.wordEnd)
        assertEquals(0, whole.startMs)
        assertEquals(30380, whole.endMs)
    }

    @Test
    fun emptyTimingsCannotProduceAWholeAyahOrWordClip() {
        val ayah = QuranAyah(1, 1, "word", "word", listOf(QuranWord(0, "word", "word")))

        assertNull(TimingWordMapper.wholeAyah(ayah, emptyList()))
        assertNull(TimingWordMapper.forWord(ayah, emptyList(), 0))
    }
}
