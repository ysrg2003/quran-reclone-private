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

    private val day = 24L * 60 * 60 * 1000

    @Test fun estimateFromPaceAndTarget() {
        val t0 = 1_000_000_000_000L
        val s0 = KhatmaLogic.record(base.copy(dailyTarget = 100), (0 until 600).toList(), "d1", t0)
        assertEquals(t0, s0.startMillis)
        // 600 ayahs in day 3 (2 full days passed) => 200/day, remaining 5636 => 29 days; target 100 => 57 days
        val e = KhatmaLogic.estimate(s0, t0 + 2 * day + 1000)
        assertEquals(5636, e.remaining); assertEquals(200.0, e.pacePerDay, 0.001)
        assertEquals(29, e.daysByPace); assertEquals(57, e.daysByTarget)
    }

    @Test fun estimateEmptyAndNoTarget() {
        val e = KhatmaLogic.estimate(base, 5L)
        assertEquals(6236, e.remaining); assertEquals(null, e.daysByPace); assertEquals(null, e.daysByTarget)
    }

    @Test fun startResetsAfterCompletion() {
        val s = KhatmaLogic.record(base, (0 until 6236).toList(), "d1", 123L)
        assertEquals(1, s.completed); assertEquals(0L, s.startMillis)
    }
}
