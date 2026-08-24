package com.example.quranoffline

import com.example.quranoffline.data.AyahSelection
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
    fun tappingBeforeStartMovesTheContiguousStartBackwards() {
        assertEquals(AyahSelection(2, 5), AyahSelection(3, 5).tap(2))
    }
}
