package com.example.quranoffline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordReferenceFixturesTest {
    @Test
    fun canonicalFixturesPreserveWordOnlyMarksAndCarriers() {
        assertEquals('ۡ', WordReferenceFixtures.UTHMANIC_SMALL_HIGH_HEAD_OF_KHAH)
        assertEquals("بِسۡمِ", WordReferenceFixtures.fatihaBasmala[0].text)
        assertTrue(WordReferenceFixtures.alamin.text.contains(WordReferenceFixtures.UTHMANIC_SMALL_HIGH_HEAD_OF_KHAH))
        assertTrue(WordReferenceFixtures.ordinaryAlam.text.endsWith(WordReferenceFixtures.UTHMANIC_SMALL_HIGH_HEAD_OF_KHAH))
        assertEquals("ٱلصَّلَوٰةَ", WordReferenceFixtures.prayer.text)
        assertEquals("كَفَرُواْ", WordReferenceFixtures.kafaru.text)
        assertTrue(WordReferenceFixtures.kafaru.text.endsWith("اْ"))
    }
}
