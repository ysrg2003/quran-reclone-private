package com.example.quranoffline

import com.example.quranoffline.data.AyahSelection
import com.example.quranoffline.data.ayahIndicesInMushafOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class AyahSelectionTest {
    @Test
    fun longPressStartsOneAyah() {
        assertEquals(AyahSelection(1, 1), AyahSelection.fromLongPress(1))
    }

    @Test
    fun tappingLaterAyahExtendsTheRangeWithoutGaps() {
        val selection = AyahSelection.fromLongPress(1).tap(4)

        assertEquals(1, selection.startIndex)
        assertEquals(4, selection.endIndex)
        assertEquals(1..4, selection.indices)
    }

    @Test
    fun tappingEarlierAyahTruncatesEverythingAfterIt() {
        val selection = AyahSelection(1, 5).tap(3)

        assertEquals(AyahSelection(1, 3), selection)
        assertEquals(3, selection.count)
    }

    @Test
    fun selectedAyahsPlayFromTheEarliestMushafIndex() {
        assertEquals(listOf(2, 3, 5, 7), ayahIndicesInMushafOrder(listOf(7, 3, 2, 5, 3)))
    }

    @Test
    fun tappingBeforeStartMovesTheContiguousStartBackwards() {
        assertEquals(AyahSelection(2, 5), AyahSelection(3, 5).tap(2))
    }
}

class SelectionRangeTest {
    @org.junit.Test fun sidesMoveIndependently() {
        val t = com.example.quranoffline.data.SelectionRange
        org.junit.Assert.assertEquals(10 to 15, t.tap(10, 12, 10, 15))   // extend next
        org.junit.Assert.assertEquals(7 to 12, t.tap(10, 12, 10, 7))     // extend previous
        org.junit.Assert.assertEquals(8 to 14, t.tap(7, 14, 10, 8))      // shrink previous side
        org.junit.Assert.assertEquals(7 to 11, t.tap(7, 14, 10, 11))     // shrink next side
        org.junit.Assert.assertEquals(10 to 10, t.tap(7, 14, 10, 10))
    }
}

class AyahSegmentsTest {
    private fun line(text: String, keys: List<String>, nums: List<Triple<Int, Int, Int>>) =
        com.example.quranoffline.data.WordReferenceLine(1, "ayah", text, keys,
            nums.map { com.example.quranoffline.data.WordReferenceAyahNumber(it.first, it.second, "x", it.third) })
    private fun seg(l: com.example.quranoffline.data.WordReferenceLine) = com.example.quranoffline.data.ayahSegments(l)

    // Real bundled data, Al-Baqarah page 2: line 4 = end of ayah 2 + start of ayah 3, yet ayahKeys lists only 2:2.
    @org.junit.Test fun ayahStartingMidLineIsRecovered() {
        val l = line("x".repeat(76), listOf("2:2"), listOf(Triple(16, 17, 2)))
        org.junit.Assert.assertEquals(listOf(Triple("2:2", 0, 17), Triple("2:3", 17, 76)), seg(l))
    }
    @org.junit.Test fun lineWithTwoMarkersAndNoTail() {
        val l = line("x".repeat(51), listOf("1:3", "1:4"), listOf(Triple(24, 25, 3), Triple(50, 51, 4)))
        org.junit.Assert.assertEquals(listOf(Triple("1:3", 0, 25), Triple("1:4", 25, 51)), seg(l))
    }
    @org.junit.Test fun continuingAyahWithoutMarkerTakesWholeLine() {
        org.junit.Assert.assertEquals(listOf(Triple("2:5", 0, 57)), seg(line("x".repeat(57), listOf("2:5"), emptyList())))
    }
    @org.junit.Test fun hitTest() {
        val s = seg(line("x".repeat(76), listOf("2:2"), listOf(Triple(16, 17, 2))))
        org.junit.Assert.assertEquals("2:2", com.example.quranoffline.data.ayahKeyAtOffset(s, 3))
        org.junit.Assert.assertEquals("2:3", com.example.quranoffline.data.ayahKeyAtOffset(s, 40))
    }
}

class HideAyahsRuleTest {
    private val page = listOf(2 to 1, 2 to 2, 2 to 3, 2 to 4)
    private fun hid(mode: Boolean, sel: Set<Pair<Int, Int>>, key: Pair<Int, Int>) = com.example.quranoffline.data.ayahHiddenInTasmee(mode, sel, page, key)

    @org.junit.Test fun offMeansNothingHidden() = page.forEach { org.junit.Assert.assertFalse(hid(false, setOf(2 to 2), it)) }
    @org.junit.Test fun noSelectionHidesWholePage() = page.forEach { org.junit.Assert.assertTrue(hid(true, emptySet(), it)) }
    // Al-Baqarah page 2: ayah 1 is the single word alif-lam-mim. Selecting only it used to hide just that ayah (and the basmala box).
    @org.junit.Test fun selectionOfFirstAyahStillHidesTheWholePage() {
        page.forEach { org.junit.Assert.assertTrue(hid(true, setOf(2 to 1), it)) }
    }
    @org.junit.Test fun selectionAnywhereHidesWholePage() {
        page.forEach { org.junit.Assert.assertTrue(hid(true, setOf(2 to 40, 2 to 41), it)) }
        page.forEach { org.junit.Assert.assertTrue(hid(true, setOf(2 to 3, 2 to 4, 2 to 5), it)) }
    }
}

class AyahSegmentsPropertyTest {
    @org.junit.Test fun segmentsAlwaysCoverTheWholeLineWithoutGapsOrOverlap() {
        val cases = listOf(
            "x".repeat(76) to listOf(Triple(16, 17, 2)),
            "x".repeat(51) to listOf(Triple(24, 25, 3), Triple(50, 51, 4)),
            "x".repeat(60) to listOf(Triple(10, 11, 5), Triple(30, 31, 6), Triple(45, 46, 7)),
            "x".repeat(30) to emptyList(),
            "x".repeat(20) to listOf(Triple(19, 20, 9)),
        )
        cases.forEach { (text, nums) ->
            val l = com.example.quranoffline.data.WordReferenceLine(1, "ayah", text, listOf("2:" + (nums.firstOrNull()?.third ?: 9)),
                nums.map { com.example.quranoffline.data.WordReferenceAyahNumber(it.first, it.second, "x", it.third) })
            val s = com.example.quranoffline.data.ayahSegments(l)
            org.junit.Assert.assertEquals(0, s.first().second)
            org.junit.Assert.assertEquals(text.length, s.last().third)
            s.zipWithNext().forEach { (a, b) -> org.junit.Assert.assertEquals(a.third, b.second) }
            (0 until text.length).forEach { off -> org.junit.Assert.assertNotNull(com.example.quranoffline.data.ayahKeyAtOffset(s, off)) }
        }
    }
    @org.junit.Test fun emptyLineAndNonAyahLineGiveNoSegments() {
        val l = com.example.quranoffline.data.WordReferenceLine(1, "surah_name", "x", emptyList(), emptyList())
        org.junit.Assert.assertTrue(com.example.quranoffline.data.ayahSegments(l).isEmpty())
    }
}
