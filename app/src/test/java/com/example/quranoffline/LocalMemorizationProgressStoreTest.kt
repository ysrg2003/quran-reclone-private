package com.example.quranoffline

import com.example.quranoffline.data.MemorizationCheckpoint
import com.example.quranoffline.data.MemorizationCheckpointCodec
import com.example.quranoffline.data.QuranAyah
import com.example.quranoffline.data.validatedAgainst
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMemorizationProgressStoreTest {
    private val ayahs = listOf(
        QuranAyah(
            1,
            1,
            WordReferenceFixtures.fatihaBasmala[0].text,
            WordReferenceFixtures.fatihaBasmala[0].matchText,
            listOf(WordReferenceFixtures.fatihaBasmala[0]),
        ),
        QuranAyah(
            2,
            1,
            WordReferenceFixtures.baqarahOpeningMuqattaat.text,
            WordReferenceFixtures.baqarahOpeningMuqattaat.matchText,
            listOf(WordReferenceFixtures.baqarahOpeningMuqattaat),
        ),
        QuranAyah(
            2,
            2,
            "${WordReferenceFixtures.dhalika.text} ${WordReferenceFixtures.kitab.text}",
            "ذلك الكتاب",
            listOf(WordReferenceFixtures.dhalika, WordReferenceFixtures.kitab),
        ),
    )

    @Test
    fun codecRoundTripPreservesStableLocationAndOpeningOption() {
        val original = MemorizationCheckpoint(2, 2, 1, true, 1234L)
        val decoded = MemorizationCheckpointCodec.decode(MemorizationCheckpointCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun malformedOrOldSchemaIsRejected() {
        assertNull(MemorizationCheckpointCodec.decode("not-json"))
        assertNull(MemorizationCheckpointCodec.decode("{\"schemaVersion\":0,\"surah\":2,\"ayah\":1,\"wordIndex\":0}"))
    }

    @Test
    fun validationAcceptsWordEndAndRejectsUnknownOrOutOfRangeLocation() {
        assertEquals(2, MemorizationCheckpoint(2, 2, 2, false, 1L).validatedAgainst(ayahs)!!.wordIndex)
        assertNull(MemorizationCheckpoint(2, 2, 3, false, 1L).validatedAgainst(ayahs))
        assertNull(MemorizationCheckpoint(2, 99, 0, false, 1L).validatedAgainst(ayahs))
        assertNull(MemorizationCheckpoint(999, 1, 0, false, 1L).validatedAgainst(ayahs))
    }

    @Test
    fun globalIndexIsDerivedFromAyahWordsAfterBasmalaSeparation() {
        val tracker = com.example.quranoffline.data.RecitationProgressTracker(ayahs)
        tracker.startFromPosition(1, 1)
        assertEquals(2, tracker.globalWordIndex(1, 1))
        assertEquals(1, ayahs[1].words.size)
    }

    @Test
    fun openingBasmalaOptionDoesNotChangeAyahWordBoundaries() {
        val withBasmala = MemorizationCheckpoint(2, 1, 0, true, 1L).validatedAgainst(ayahs)
        assertTrue(withBasmala!!.includeOpeningBasmala)
        assertEquals(1, ayahs[1].words.size)
        assertFalse(ayahs[1].words.any { it.text == WordReferenceFixtures.fatihaBasmala[0].text })
    }
}
