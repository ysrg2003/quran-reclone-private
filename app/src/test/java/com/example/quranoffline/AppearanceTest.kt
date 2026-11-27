package com.example.quranoffline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceTest {
    @Test fun badTabOrderFallsBackToDefault() {
        assertEquals(AppearanceSpec.DEFAULT_TAB_ORDER, AppearanceStore.normalizeOrder(listOf(1, 1, 2)))
        assertEquals(AppearanceSpec.DEFAULT_TAB_ORDER, AppearanceStore.normalizeOrder(listOf(0, 1, 2, 3, 4, 5)))
        assertEquals(AppearanceSpec.DEFAULT_TAB_ORDER, AppearanceStore.normalizeOrder(emptyList()))
    }
    @Test fun validReorderKept() {
        val o = listOf(6, 4, 3, 2, 1, 0)
        assertEquals(o, AppearanceStore.normalizeOrder(o))
    }
    @Test fun settingsTabNeverInRow() { assertTrue(5 !in AppearanceSpec.DEFAULT_TAB_ORDER) }
    @Test fun presetIdsUnique() { assertEquals(APPEARANCE_PRESETS.size, APPEARANCE_PRESETS.map { it.id }.toSet().size) }
}
