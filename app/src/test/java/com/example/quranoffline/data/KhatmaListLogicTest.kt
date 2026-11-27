package com.example.quranoffline.data

import java.util.BitSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KhatmaListLogicTest {
    private fun record(id: String, kind: SaveMarkKind, covered: IntArray = intArrayOf()) =
        KhatmaRecord(id, "k-$id", kind, KhatmaState(kind = kind, covered = BitSet().apply { covered.forEach { set(it) } }))

    @Test fun migrateKeepsOneRecordPerKindAndCopiesState() {
        val hifz = KhatmaState(kind = SaveMarkKind.HIFZ, covered = BitSet().apply { set(0); set(5) }, dailyTarget = 4, reminderOn = true, reminderHour = 21)
        val read = KhatmaState(kind = SaveMarkKind.READ, covered = BitSet().apply { set(9) }, dailyTarget = 2, targetUnit = 1)
        val out = KhatmaListLogic.migrate(listOf(hifz, read)) { "legacy-$it" }
        assertEquals(2, out.size)
        assertEquals(KhatmaListLogic.DEFAULT_HIFZ_NAME, out[0].name)
        assertEquals(KhatmaListLogic.DEFAULT_READ_NAME, out[1].name)
        assertEquals(2, out[0].state.coveredCount)
        assertEquals(21, out[0].state.reminderHour)
        assertTrue(out[0].state.reminderOn)
        assertEquals(1, out[1].state.coveredCount)
        assertEquals(1, out[1].state.targetUnit)
        assertEquals("legacy-0", out[0].id)
    }

    @Test fun activeRecordFallsBackToFirstOfItsKind() {
        val records = listOf(record("a", SaveMarkKind.HIFZ), record("b", SaveMarkKind.HIFZ), record("c", SaveMarkKind.READ))
        assertEquals("b", KhatmaListLogic.activeFor(records, SaveMarkKind.HIFZ, "b")?.id)
        assertEquals("a", KhatmaListLogic.activeFor(records, SaveMarkKind.HIFZ, "missing")?.id)
        assertEquals("a", KhatmaListLogic.activeFor(records, SaveMarkKind.HIFZ, "c")?.id)
        assertEquals("c", KhatmaListLogic.activeFor(records, SaveMarkKind.READ, null)?.id)
    }

    @Test fun deleteRefusesTheLastRecordOfAKind() {
        val records = listOf(record("a", SaveMarkKind.HIFZ), record("b", SaveMarkKind.HIFZ), record("c", SaveMarkKind.READ))
        assertEquals(listOf("a", "c"), KhatmaListLogic.delete(records, "b")?.map { it.id })
        assertNull(KhatmaListLogic.delete(records, "c"))
        assertEquals(records, KhatmaListLogic.delete(records, "nope"))
    }

    @Test fun replaceUpdatesOrAppends() {
        val records = listOf(record("a", SaveMarkKind.HIFZ))
        val renamed = records[0].copy(name = "جديد")
        assertEquals("جديد", KhatmaListLogic.replace(records, renamed).single().name)
        assertEquals(2, KhatmaListLogic.replace(records, record("b", SaveMarkKind.READ)).size)
    }
}
