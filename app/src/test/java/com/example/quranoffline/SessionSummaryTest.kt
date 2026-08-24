package com.example.quranoffline

import com.example.quranoffline.data.SessionSummary
import com.example.quranoffline.data.SessionSummaryRanking
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSummaryTest {
    @Test
    fun latestOpenedSessionAppearsFirst() {
        val older = SessionSummary("old", 2, 1, 1, 1, 3, 2, 100, lastOpenedAt = 100)
        val newer = SessionSummary("new", 2, 3, 3, 0, 3, 3, 200, label = "مرجع قراءة 2:3", page = 3, checkpointAyah = 3, lastOpenedAt = 200)

        val ordered = SessionSummaryRanking.latestFirst(listOf(older, newer))

        assertEquals(listOf("new", "old"), ordered.map { it.id })
        assertEquals("مرجع قراءة 2:3", ordered.first().label)
        assertEquals(3, ordered.first().checkpointAyah)
    }
}
