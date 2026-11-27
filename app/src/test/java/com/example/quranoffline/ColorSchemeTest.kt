package com.example.quranoffline

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.graphics.luminance
import org.junit.Test

class ColorSchemeTest {
    @Test fun selectedChipFollowsAccentNotDefaultLavender() {
        for (accent in listOf(0xFF276456L, 0xFF5B3A8C, 0xFF9A3412)) {
            val a = AppearanceSpec(accent = accent).resolve()
            val cs = a.colorScheme()
            assertEquals(a.container, cs.secondaryContainer)
            assertEquals(a.container, cs.primaryContainer)
            assertNotEquals(Color(0xFFE8DEF8), cs.secondaryContainer)
            assertEquals(a.primary, cs.primary)
            assertEquals(a.primary, cs.surfaceTint)
        }
    }
    @Test fun containerIsLightEnoughForDarkText() {
        val a = AppearanceSpec().resolve()
        assertTrue(a.container.luminance() > 0.6f)
        assertTrue(a.deep.luminance() < 0.15f)
    }
}

class TabOrderDefaultTest {
    @Test fun juzIsFirstByDefault() {
        assertEquals(2, AppearanceSpec.DEFAULT_TAB_ORDER.first())
        assertEquals(AppearanceSpec.DEFAULT_TAB_ORDER, AppearanceStore.normalizeOrder(AppearanceSpec.DEFAULT_TAB_ORDER))
    }
    @Test fun customOrderStillValid() {
        val custom = listOf(6, 2, 0, 1, 3, 4)
        assertEquals(custom, AppearanceStore.normalizeOrder(custom))
    }
}
