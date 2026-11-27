package com.example.quranoffline.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SaveMarksTest {
    private fun up(m: List<SaveMark>, k: SaveMarkKind, ayah: Int, now: Long, id: String) =
        SaveMarkLogic.upsert(m, k, 2, ayah, 3, 0, now, id)

    @Test fun sameSessionUpdatesInPlace() {
        var m = up(emptyList(), SaveMarkKind.HIFZ, 1, 1000, "a")
        m = up(m, SaveMarkKind.HIFZ, 5, 2000, "b")
        assertEquals(1, m.size); assertEquals(5, m[0].ayah); assertEquals("a", m[0].id); assertEquals(1000, m[0].startedAt)
    }

    @Test fun keepsLastThreePerKindSeparately() {
        var m = emptyList<SaveMark>()
        for (i in 0 until 5) m = up(m, SaveMarkKind.HIFZ, i + 1, i * 3_600_000L, "h$i")
        m = up(m, SaveMarkKind.READ, 9, 10_000_000L, "r0")
        assertEquals(3, SaveMarkLogic.forKind(m, SaveMarkKind.HIFZ).size)
        assertEquals(listOf(5, 4, 3), SaveMarkLogic.forKind(m, SaveMarkKind.HIFZ).map { it.ayah })
        assertEquals(1, SaveMarkLogic.forKind(m, SaveMarkKind.READ).size)
    }

    @Test fun codecRoundTrip() {
        val m = up(up(emptyList(), SaveMarkKind.HIFZ, 1, 1, "a"), SaveMarkKind.READ, 2, 2, "b")
        assertEquals(m, SaveMarkLogic.decode(SaveMarkLogic.encode(m)))
    }
}
