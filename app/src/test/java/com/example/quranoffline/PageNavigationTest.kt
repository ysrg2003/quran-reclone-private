package com.example.quranoffline

import com.example.quranoffline.data.pageAfterHorizontalSwipe
import org.junit.Assert.assertEquals
import org.junit.Test

class PageNavigationTest {
    @Test
    fun leftSwipeGoesToPreviousPage() {
        assertEquals(2, pageAfterHorizontalSwipe(currentPage = 3, totalPages = 604, dragAmount = -120f))
    }

    @Test
    fun rightSwipeGoesToNextPage() {
        assertEquals(4, pageAfterHorizontalSwipe(currentPage = 3, totalPages = 604, dragAmount = 120f))
    }

    @Test
    fun swipeDoesNotLeavePageBounds() {
        assertEquals(1, pageAfterHorizontalSwipe(currentPage = 1, totalPages = 604, dragAmount = -120f))
        assertEquals(604, pageAfterHorizontalSwipe(currentPage = 604, totalPages = 604, dragAmount = 120f))
        assertEquals(3, pageAfterHorizontalSwipe(currentPage = 3, totalPages = 604, dragAmount = 10f))
    }
}
