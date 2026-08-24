package com.example.quranoffline

import com.example.quranoffline.data.QuranAyah
import com.example.quranoffline.data.QuranMetadata
import com.example.quranoffline.data.QuranPageLayout
import com.example.quranoffline.data.QuranWord
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
    fun rejectsDtdAndEntityDeclarationsBeforeParsing() {
        val xml = javaClass.getResourceAsStream("/quran-meta-data.xml")!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val malicious = xml.replaceFirst("<quran", "<!DOCTYPE quran [<!ENTITY external SYSTEM \"file:///tmp/secret\">]><quran")
        var rejected = false
        try {
            QuranMetadata.parse(malicious)
        } catch (error: IllegalArgumentException) {
            rejected = error.message?.contains("DTD") == true
        }
        assertTrue(rejected)
    }

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
    fun metadataPagesExposeExpectedCanonicalBoundaries() {
        val metadata = load()
        val ayahs = canonicalAyahs(metadata)

        assertEquals(listOf("1:1", "1:2", "1:3", "1:4", "1:5", "1:6", "1:7"), keys(QuranPageLayout.ayahsOnPage(ayahs, metadata, 1)))
        assertEquals((1..5).map { "2:$it" }, keys(QuranPageLayout.ayahsOnPage(ayahs, metadata, 2)))
        assertEquals((6..16).map { "2:$it" }, keys(QuranPageLayout.ayahsOnPage(ayahs, metadata, 3)))
        assertEquals((54..61).map { "18:$it" }, keys(QuranPageLayout.ayahsOnPage(ayahs, metadata, 300)))

        val lastPage = QuranPageLayout.ayahsOnPage(ayahs, metadata, 604)
        assertEquals(3, lastPage.map { it.surah }.distinct().size)
        assertEquals(
            listOf("112:1", "112:2", "112:3", "112:4", "113:1", "113:2", "113:3", "113:4", "113:5", "114:1", "114:2", "114:3", "114:4", "114:5", "114:6"),
            keys(lastPage),
        )
        assertEquals("112:1", "${lastPage.first().surah}:${lastPage.first().ayah}")
        assertEquals("114:6", "${lastPage.last().surah}:${lastPage.last().ayah}")
    }

    @Test
    fun pageWordOffsetsUseTheCanonicalGlobalWordSequence() {
        val metadata = load()
        val ayahs = canonicalAyahs(metadata).mapIndexed { index, ayah ->
            ayah.copy(words = List((index % 5) + 1) { wordIndex ->
                QuranWord(index = wordIndex, text = "كلمة", matchText = "كلمة")
            })
        }

        listOf(1, 2, 300, 604).forEach { pageNumber ->
            val pageAyahs = QuranPageLayout.ayahsOnPage(ayahs, metadata, pageNumber)
            val expectedOffset = ayahs.takeWhile { it !== pageAyahs.first() }.sumOf { it.words.size }
            assertEquals(expectedOffset, QuranPageLayout.globalWordOffset(ayahs, pageAyahs.first()))
        }
        assertEquals(604, QuranPageLayout.pageForAyahIndex(ayahs, metadata, ayahs.lastIndex))
        assertEquals(0, QuranPageLayout.firstAyahIndexOnPage(ayahs, metadata, 1))
        assertEquals(7, QuranPageLayout.firstAyahIndexOnPage(ayahs, metadata, 2))
        assertEquals(ayahs.indexOfFirst { it.surah == 2 && it.ayah == 6 }, QuranPageLayout.firstAyahIndexOnPage(ayahs, metadata, 3))
        assertEquals(ayahs.indexOfFirst { it.surah == 18 && it.ayah == 54 }, QuranPageLayout.firstAyahIndexOnPage(ayahs, metadata, 300))
        assertEquals(ayahs.indexOfFirst { it.surah == 112 && it.ayah == 1 }, QuranPageLayout.firstAyahIndexOnPage(ayahs, metadata, 604))
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

    private fun canonicalAyahs(metadata: QuranMetadata): List<QuranAyah> = metadata.suras.flatMap { sura ->
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

    private fun keys(ayahs: List<QuranAyah>): List<String> = ayahs.map { "${it.surah}:${it.ayah}" }
}
