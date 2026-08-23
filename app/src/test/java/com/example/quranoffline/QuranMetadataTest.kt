package com.example.quranoffline

import com.example.quranoffline.data.QuranAyah
import com.example.quranoffline.data.QuranMetadata
import com.example.quranoffline.data.RevelationType
import com.example.quranoffline.data.SajdaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranMetadataTest {
    private fun load(): QuranMetadata =
        QuranMetadata.parse(
            javaClass.getResourceAsStream("/quran-meta-data.xml")!!.bufferedReader(Charsets.UTF_8).use { it.readText() },
        )

    @Test
    fun parsesArabicSurasAndIgnoresEnglishPresentationFields() {
        val metadata = load()

        assertEquals(114, metadata.suras.size)
        assertEquals(6236, metadata.suras.sumOf { it.ayahCount })
        assertEquals("الفاتحة", metadata.sura(1)?.arabicName)
        assertEquals(RevelationType.MECCAN, metadata.sura(1)?.revelationType)
        assertEquals("الملك", metadata.sura(67)?.arabicName)
        assertEquals(RevelationType.MECCAN, metadata.sura(67)?.revelationType)
        assertEquals(1, metadata.sura(96)?.revelationOrder)
        assertTrue(metadata.suras.all { it.arabicName.isNotBlank() })
    }

    @Test
    fun convertsMushafBoundariesToZeroBasedGlobalAyahMetadata() {
        val metadata = load()

        val first = metadata.ayah(1, 1)!!
        assertEquals(0, first.globalAyahIndex)
        assertEquals(1, first.page)
        assertEquals(1, first.juz)
        assertEquals(1, first.quarter)
        assertEquals(1, first.manzil)
        assertEquals(1, first.ruku)

        val secondJuzStart = metadata.ayah(2, 142)!!
        assertEquals(7 + 141, secondJuzStart.globalAyahIndex)
        assertEquals(2, secondJuzStart.juz)
        assertEquals(9, secondJuzStart.quarter)

        val last = metadata.ayah(114, 6)!!
        assertEquals(6235, last.globalAyahIndex)
        assertTrue(last.page <= 604)
    }

    @Test
    fun parsesSajdaTypesAndDoesNotInventSajdasForOtherAyahs() {
        val metadata = load()

        assertEquals(SajdaType.RECOMMENDED, metadata.ayah(7, 206)?.sajda)
        assertEquals(SajdaType.OBLIGATORY, metadata.ayah(32, 15)?.sajda)
        assertEquals(SajdaType.OBLIGATORY, metadata.ayah(96, 19)?.sajda)
        assertNull(metadata.ayah(1, 7)?.sajda)
        assertEquals(15, metadata.sajdas.size)
    }

    @Test
    fun metadataIsCompatibleWithCanonicalAyahSequence() {
        val metadata = load()
        val ayahs = metadata.suras.flatMap { sura ->
            (1..sura.ayahCount).map { ayah ->
                QuranAyah(
                    surah = sura.index,
                    ayah = ayah,
                    text = "",
                    matchText = "",
                    words = emptyList(),
                )
            }
        }

        metadata.requireCompatible(ayahs)
    }
}
