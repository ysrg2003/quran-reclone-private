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

    @org.junit.Test fun segmentsFollowEndMarkers() {
        // "aaaa(1)bbbb(2)cccc" : ayah 1 ends at its marker, ayah 2 at its marker, ayah 3 continues to line end
        val l = line("aaaa1bbbb2cccc", listOf("2:1", "2:2", "2:3"), listOf(Triple(4, 5, 1), Triple(9, 10, 2)))
        val seg = com.example.quranoffline.data.ayahSegments(l)
        org.junit.Assert.assertEquals(listOf(Triple("2:1", 0, 5), Triple("2:2", 5, 10), Triple("2:3", 10, 14)), seg)
        org.junit.Assert.assertEquals("2:1", com.example.quranoffline.data.ayahKeyAtOffset(seg, 2))
        org.junit.Assert.assertEquals("2:2", com.example.quranoffline.data.ayahKeyAtOffset(seg, 5))
        org.junit.Assert.assertEquals("2:3", com.example.quranoffline.data.ayahKeyAtOffset(seg, 13))
    }

    @org.junit.Test fun continuingAyahWithoutMarkerTakesWholeLine() {
        val l = line("aaaaaa", listOf("2:5"), emptyList())
        org.junit.Assert.assertEquals(listOf(Triple("2:5", 0, 6)), com.example.quranoffline.data.ayahSegments(l))
    }
}
