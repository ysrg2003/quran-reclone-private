package com.example.quranoffline.data

/**
 * Maps physical horizontal movement to mushaf pages.
 * The user's requested reading direction is literal: left/north swipe = previous,
 * right swipe = next, while page numbers still increase from 1 to 604.
 */
fun pageAfterHorizontalSwipe(currentPage: Int, totalPages: Int, dragAmount: Float, threshold: Float = 80f): Int {
    val current = currentPage.coerceIn(1, totalPages.coerceAtLeast(1))
    return when {
        dragAmount <= -threshold -> (current - 1).coerceAtLeast(1)
        dragAmount >= threshold -> (current + 1).coerceAtMost(totalPages.coerceAtLeast(1))
        else -> current
    }
}
