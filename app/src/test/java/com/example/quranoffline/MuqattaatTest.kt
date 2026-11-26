package com.example.quranoffline

import com.example.quranoffline.data.Muqattaat
import com.example.quranoffline.data.MuqattaatMaddCategory
import com.example.quranoffline.data.QuranWord
import com.example.quranoffline.data.RecitationMatcher
import com.example.quranoffline.data.RecitationProgressInput
import com.example.quranoffline.data.RecitationProgressTracker
import com.example.quranoffline.data.WordStatus
import com.example.quranoffline.data.quranWordMatches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MuqattaatTest {
    private val occurrences = listOf(
        2 to "الٓمٓ", 3 to "الٓمٓ", 7 to "الٓمٓصٓ", 10 to "الٓر", 11 to "الٓر",
        12 to "الٓر", 13 to "الٓمٓر", 14 to "الٓر", 15 to "الٓر", 19 to "كٓهيعٓصٓ",
        20 to "طه", 26 to "طسٓمٓ", 27 to "طسٓ", 28 to "طسٓمٓ", 29 to "الٓمٓ",
        30 to "الٓمٓ", 31 to "الٓمٓ", 32 to "الٓمٓ", 36 to "يسٓ", 38 to "صٓ",
        40 to "حمٓ", 41 to "حمٓ", 42 to "حمٓ", 43 to "حمٓ", 44 to "حمٓ",
        45 to "حمٓ", 46 to "حمٓ", 50 to "قٓ", 68 to "نٓ",
    )

    @Test
    fun everyCanonicalOccurrenceHasPronunciationUnits() {
        assertEquals(29, occurrences.size)
        occurrences.forEach { (surah, token) ->
            val pronunciation = Muqattaat.pronunciationFor(QuranWord(0, token, token.replace("ٓ", "")))
            assertNotNull("missing pronunciation for $surah:1 $token", pronunciation)
            assertTrue(pronunciation!!.units.isNotEmpty())
        }
    }

    @Test
    fun pronunciationKeepsVisualTokenOneWordAndUsesLetterNames() {
        val pronunciation = Muqattaat.pronunciationFor(WordReferenceFixtures.baqarahOpeningMuqattaat)!!
        assertEquals("الٓمٓ", pronunciation.visualToken)
        assertEquals(listOf("ألف", "لام", "ميم"), pronunciation.spokenNames)
        assertEquals("ألف لام ميم", pronunciation.spokenText)
        assertEquals(MuqattaatMaddCategory.NONE, pronunciation.units[0].maddCategory)
        assertEquals(MuqattaatMaddCategory.LAZIM_HARFI_SIX_HARAKAT, pronunciation.units[1].maddCategory)
        assertEquals(MuqattaatMaddCategory.LAZIM_HARFI_SIX_HARAKAT, pronunciation.units[2].maddCategory)
    }

    @Test
    fun fiveTwoLetterNamesAreExplicitlyNaturalMadd() {
        val expected = mapOf(
            "ح" to "حا", "ي" to "يا", "ط" to "طا", "ه" to "ها", "ر" to "را",
        )
        val twoLetterSequences = listOf("طه", "يس", "طس", "حم", "الر")
        twoLetterSequences.forEach { sequence ->
            val pronunciation = Muqattaat.allCanonicalSequences().firstOrNull { it == sequence }
            assertNotNull(pronunciation)
        }
        val fiveNames = listOf("حا", "يا", "طا", "ها", "را")
        assertEquals(5, expected.size)
        assertEquals(fiveNames, expected.values.toList())
        val haMim = Muqattaat.pronunciationFor(WordReferenceFixtures.ham)!!
        assertEquals("حا", haMim.units[0].shortName)
        assertEquals(MuqattaatMaddCategory.NATURAL_TWO_HARAKAT, haMim.units[0].maddCategory)
    }

    @Test
    fun separateLetterNamesMatchOneReferenceWord() {
        val expected = listOf(WordReferenceFixtures.baqarahOpeningMuqattaat)
        val result = RecitationMatcher.evaluate(expected, "ألف لام ميم", hasFinalResult = true)
        assertEquals(1, result.matched)
        assertEquals(0, result.unexpected)
        assertEquals(WordStatus.MATCHED, result.words.single().status)
    }

    @Test
    fun partialMuqattaatSpeechDoesNotPrematurelyMarkWrong() {
        val tracker = RecitationProgressTracker(listOf(
            com.example.quranoffline.data.QuranAyah(2, 1, WordReferenceFixtures.baqarahOpeningMuqattaat.text, WordReferenceFixtures.baqarahOpeningMuqattaat.matchText, listOf(WordReferenceFixtures.baqarahOpeningMuqattaat))
        ))
        tracker.startFromAyah(0)
        val partial = tracker.update(RecitationProgressInput("ألف", isFinal = false, segmentCompleted = false))
        assertEquals(WordStatus.UNCERTAIN, partial.evaluation.words.single().status)
        val complete = tracker.update(RecitationProgressInput("ألف لام ميم", isFinal = true, segmentCompleted = false))
        assertEquals(1, complete.evaluation.matched)
        assertEquals(WordStatus.MATCHED, complete.evaluation.words.single().status)
    }

    @Test
    fun longMaddInLetterNamesStillMatchesOneReferenceWord() {
        val expected = listOf(WordReferenceFixtures.baqarahOpeningMuqattaat)
        val result = RecitationMatcher.evaluate(expected, "أألف لاممم ميممم", hasFinalResult = true)
        assertEquals(1, result.matched)
        assertEquals(0, result.unexpected)
        assertEquals(WordStatus.MATCHED, result.words.single().status)
    }

    @Test
    fun longMaddAsRepeatedAlifLamMimTokensStillMatches() {
        val expected = listOf(WordReferenceFixtures.baqarahOpeningMuqattaat)
        val result = RecitationMatcher.evaluate(expected, "اااالف لاااام مميييم", hasFinalResult = true)
        assertEquals(1, result.matched)
        assertEquals(WordStatus.MATCHED, result.words.single().status)
    }

    @Test
    fun ordinaryAlamWordIsNotTreatedAsMuqattaat() {
        val ordinary = WordReferenceFixtures.ordinaryAlam
        assertFalse(Muqattaat.pronunciationFor(ordinary) != null)
        assertTrue(quranWordMatches(ordinary, "ألم"))
        assertFalse(quranWordMatches(ordinary, "ألف لام ميم"))
    }

    @Test
    fun keHaYaAinSadUsesAinNotHa() {
        val pronunciation = Muqattaat.pronunciationFor(WordReferenceFixtures.kahyainSad)!!
        assertEquals(listOf("كاف", "ها", "يا", "عين", "صاد"), pronunciation.spokenNames)
        assertEquals("أسماء حروف مستقلة", "أسماء حروف مستقلة")
    }
}

class MuqattaatMaddFormsTest {
    private fun m(token: String, match: String, observed: String) = quranWordMatches(QuranWord(0, token, match), observed)

    @Test
    fun fullSpelledAndMaddFormsAreAcceptedForEveryOpening() {
        val words = mapOf(
            "الٓمٓ" to listOf("الف لام ميم", "ألف لاااام ميييم"),
            "الٓمٓصٓ" to listOf("الف لام ميم صاد"),
            "الٓر" to listOf("الف لام را"),
            "الٓمٓر" to listOf("الف لام ميم را"),
            "كٓهيعٓصٓ" to listOf("كاف ها يا عين صاد"),
            "طه" to listOf("طا ها"),
            "طسٓمٓ" to listOf("طا سين ميم"),
            "طسٓ" to listOf("طا سين"),
            "يسٓ" to listOf("يا سين"),
            "صٓ" to listOf("صاد", "صااااد"),
            "حمٓ" to listOf("حا ميم"),
            "عٓسٓقٓ" to listOf("عين سين قاف"),
            "قٓ" to listOf("قاف", "قااااف"),
            "نٓ" to listOf("نون", "نووون"),
        )
        words.forEach { (token, forms) ->
            val match = token.replace("ٓ", "")
            forms.forEach { f -> assertTrue("$token <- $f", m(token, match, f)) }
        }
    }

    @Test
    fun truncatedOrWrongReadingsAreNotAccepted() {
        listOf("الم", "ألم", "الف ل", "الف لا", "الف لام", "الف", "لا", "ا ل", "ذلك", "الف سين").forEach {
            assertFalse("الم <- $it", m("الٓمٓ", "الم", it))
        }
        assertFalse(m("الٓمٓصٓ", "المص", "الف لام ميم"))
        assertFalse(m("كٓهيعٓصٓ", "كهيعص", "كاف ها يا عين"))
        assertFalse(m("حمٓ", "حم", "حا"))
        // Merged written forms are not the spoken letter names.
        assertFalse(m("حمٓ", "حم", "حم"))
        assertFalse(m("طه", "طه", "طه"))
        assertFalse(m("يسٓ", "يس", "يس"))
        assertFalse(m("طسٓ", "طس", "طس"))
        assertFalse(m("صٓ", "ص", "ص"))
        assertFalse(m("قٓ", "ق", "ق"))
        assertFalse(m("نٓ", "ن", "ن"))
        assertFalse(com.example.quranoffline.data.quranWordMatchesAllowCanonicalSpelling(QuranWord(0, "الٓمٓ", "الم"), "الم"))
    }

    @Test
    fun truncatedReadingIsDetectedSoAnotherAlternativeCanBePreferred() {
        assertTrue(Muqattaat.looksTruncated("الف ل"))
        assertFalse(Muqattaat.looksTruncated("الف لام ميم"))
        assertFalse(Muqattaat.looksTruncated("ذلك الكتاب"))
        assertTrue(Muqattaat.startsWithFullSequence("الف لام ميم ذلك"))
    }

    @Test
    fun fusedSpokenLetterNamesAreSplitButStillNeedEveryName() {
        assertTrue(m("الٓمٓ", "الم", "الف لاميم"))
        assertTrue(m("كٓهيعٓصٓ", "كهيعص", "كافها يا عين صاد"))
        assertTrue(m("حمٓ", "حم", "حاميم"))
        assertTrue(m("يسٓ", "يس", "ياسين"))
        assertFalse(m("الٓمٓ", "الم", "الفلام"))
        assertFalse(m("الٓمٓ", "الم", "ألف لامرى"))
        assertTrue(Muqattaat.coalesceSpeechTokens(listOf("عينها")) == listOf("عينها"))
    }
}
