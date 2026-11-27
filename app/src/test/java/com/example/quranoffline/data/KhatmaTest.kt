package com.example.quranoffline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KhatmaTest {
    private val base = KhatmaState(SaveMarkKind.HIFZ)

    @Test fun recordsCoverageAndToday() {
        val s = KhatmaLogic.record(base, listOf(0, 1, 2, 2), "d1")
        assertEquals(3, s.coveredCount); assertEquals(3, s.todayCount); assertEquals(2, s.lastIndex)
    }

    @Test fun todayResetsOnNewDayButCoverageStays() {
        val s = KhatmaLogic.record(KhatmaLogic.record(base, listOf(0, 1), "d1"), listOf(5), "d2")
        assertEquals(3, s.coveredCount); assertEquals(1, s.todayCount)
    }

    @Test fun completingResetsAndCounts() {
        val s = KhatmaLogic.record(base, (0 until KhatmaLogic.TOTAL).toList(), "d1")
        assertEquals(1, s.completed); assertEquals(0, s.coveredCount)
    }

    @Test fun outOfRangeIgnoredAndCodecRoundTrip() {
        val s = KhatmaLogic.record(base, listOf(-1, 7000, 10, 6235), "d1")
        assertEquals(2, s.coveredCount)
        assertTrue(KhatmaLogic.decode(KhatmaLogic.encode(s.covered)) == s.covered)
    }
}
