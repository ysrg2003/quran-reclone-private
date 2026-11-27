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
