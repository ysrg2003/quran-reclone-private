package com.example.quranoffline

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordReferenceContentTest {
    private data class Row(val surah: Int, val ayah: Int, val text: String, val words: List<String>)

    private val raw: String by lazy {
        val asset = listOf(
            File("app/src/main/assets/quran/word_reference_ayahs.json"),
            File("src/main/assets/quran/word_reference_ayahs.json"),
            File("../app/src/main/assets/quran/word_reference_ayahs.json"),
        ).firstOrNull(File::exists) ?: error("Word reference asset was not found from ${System.getProperty("user.dir")}")
        asset.readText(Charsets.UTF_8)
    }

    private val rows: List<Row> by lazy {
        val rowRegex = Regex("""\{"surah":(\d+),"ayah":(\d+),"text":"(.*?)","matchText":".*?","words":\[(.*?)\]\}""")
        val wordRegex = Regex("""\{"index":(\d+),"text":"(.*?)","matchText":".*?"\}""")
        rowRegex.findAll(raw).map { match ->
            val words = wordRegex.findAll(match.groupValues[4]).map { it.groupValues[2] }.toList()
            Row(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3], words)
        }.toList()
    }

    @Test
    fun fullWordReferenceHasCompleteOrderedModel() {
        assertTrue(raw.contains("\"schemaVersion\":2"))
        assertTrue(raw.contains("\"ayahCount\":6236"))
        assertTrue(raw.contains("\"wordCount\":77430"))
        assertEquals(6236, rows.size)
        var previous = 0 to 0
        var totalWords = 0
        rows.forEach { row ->
            assertTrue(row.surah > previous.first || row.surah == previous.first && row.ayah > previous.second)
            previous = row.surah to row.ayah
            assertEquals(row.text, row.words.joinToString(" "))
            row.words.forEach { word ->
                assertFalse(word == "۞")
                assertFalse(word.isNotEmpty() && word.all { it in "٠١٢٣٤٥٦٧٨٩" })
                totalWords++
            }
        }
        assertEquals(77430, totalWords)
        assertEquals(114, rows.map { it.surah }.toSet().size)
    }

    @Test
    fun representativeAyahsRemainExactlyWordFaithful() {
        val expected = mapOf(
            "1:1" to "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ",
            "1:2" to "ٱلۡحَمۡدُ لِلَّهِ رَبِّ ٱلۡعَٰلَمِينَ",
            "1:4" to "مَٰلِكِ يَوۡمِ ٱلدِّينِ",
            "1:7" to "صِرَٰطَ ٱلَّذِينَ أَنۡعَمۡتَ عَلَيۡهِمۡ غَيۡرِ ٱلۡمَغۡضُوبِ عَلَيۡهِمۡ وَلَا ٱلضَّآلِّينَ",
            "2:1" to "الٓمٓ",
            "2:2" to "ذَٰلِكَ ٱلۡكِتَٰبُ لَا رَيۡبَۛ فِيهِۛ هُدٗى لِّلۡمُتَّقِينَ",
            "2:6" to "إِنَّ ٱلَّذِينَ كَفَرُواْ سَوَآءٌ عَلَيۡهِمۡ ءَأَنذَرۡتَهُمۡ أَمۡ لَمۡ تُنذِرۡهُمۡ لَا يُؤۡمِنُونَ",
            "2:14" to "وَإِذَا لَقُواْ ٱلَّذِينَ ءَامَنُواْ قَالُوٓاْ ءَامَنَّا وَإِذَا خَلَوۡاْ إِلَىٰ شَيَٰطِينِهِمۡ قَالُوٓاْ إِنَّا مَعَكُمۡ إِنَّمَا نَحۡنُ مُسۡتَهۡزِءُونَ",
            "2:15" to "ٱللَّهُ يَسۡتَهۡزِئُ بِهِمۡ وَيَمُدُّهُمۡ فِي طُغۡيَٰنِهِمۡ يَعۡمَهُونَ",
            "2:16" to "أُوْلَٰٓئِكَ ٱلَّذِينَ ٱشۡتَرَوُاْ ٱلضَّلَٰلَةَ بِٱلۡهُدَىٰ فَمَا رَبِحَت تِّجَٰرَتُهُمۡ وَمَا كَانُواْ مُهۡتَدِينَ",
            "13:37" to "وَكَذَٰلِكَ أَنزَلۡنَٰهُ حُكۡمًا عَرَبِيّٗاۚ وَلَئِنِ ٱتَّبَعۡتَ أَهۡوَآءَهُم بَعۡدَ مَا جَآءَكَ مِنَ ٱلۡعِلۡمِ مَا لَكَ مِنَ ٱللَّهِ مِن وَلِيّٖ وَلَا وَاقٖ",
            "15:7" to "لَّوۡمَا تَأۡتِينَا بِٱلۡمَلَٰٓئِكَةِ إِن كُنتَ مِنَ ٱلصَّٰدِقِينَ",
            "27:20" to "وَتَفَقَّدَ ٱلطَّيۡرَ فَقَالَ مَالِيَ لَآ أَرَى ٱلۡهُدۡهُدَ أَمۡ كَانَ مِنَ ٱلۡغَآئِبِينَ",
            "36:22" to "وَمَالِيَ لَآ أَعۡبُدُ ٱلَّذِي فَطَرَنِي وَإِلَيۡهِ تُرۡجَعُونَ",
            "45:37" to "وَلَهُ ٱلۡكِبۡرِيَآءُ فِي ٱلسَّمَٰوَٰتِ وَٱلۡأَرۡضِۖ وَهُوَ ٱلۡعَزِيزُ ٱلۡحَكِيمُ",
            "68:51" to "وَإِن يَكَادُ ٱلَّذِينَ كَفَرُواْ لَيُزۡلِقُونَكَ بِأَبۡصَٰرِهِمۡ لَمَّا سَمِعُواْ ٱلذِّكۡرَ وَيَقُولُونَ إِنَّهُۥ لَمَجۡنُونٞ",
            "86:1" to "وَٱلسَّمَآءِ وَٱلطَّارِقِ",
            "114:6" to "مِنَ ٱلۡجِنَّةِ وَٱلنَّاسِ",
        )
        val byKey = rows.associateBy { "${it.surah}:${it.ayah}" }
        expected.forEach { (key, text) -> assertEquals(key, text, byKey[key]?.text) }
        assertTrue(expected.values.any { it.contains('ۡ') })
    }
}
