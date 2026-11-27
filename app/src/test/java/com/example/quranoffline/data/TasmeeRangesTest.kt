package com.example.quranoffline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TasmeeRangesTest {
    private val pages = listOf(1, 1, 2, 2, 2, 3, 3, 4)
    @Test fun spanOfPages() { assertEquals(2 to 6, TasmeeRanges.span(pages, 2, 3, 1, 604)) }
    @Test fun reversedIsSwapped() { assertEquals(2 to 6, TasmeeRanges.span(pages, 3, 2, 1, 604)) }
    @Test fun singlePage() { assertEquals(7 to 7, TasmeeRanges.span(pages, 4, 4, 1, 604)) }
    @Test fun outOfBounds() { assertNull(TasmeeRanges.span(pages, 0, 2, 1, 604)); assertNull(TasmeeRanges.span(pages, 1, 605, 1, 604)) }
    @Test fun missingValue() { assertNull(TasmeeRanges.span(pages, 9, 9, 1, 604)) }
}
