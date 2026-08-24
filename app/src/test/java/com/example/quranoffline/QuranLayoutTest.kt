package com.example.quranoffline

import com.example.quranoffline.data.QuranLayoutWordKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuranLayoutTest {
    @Test
    fun parsesSourceWordKeyInSurahAyahWordOrder() {
        val key = QuranLayoutWordKey.parse("2:3:8")

        assertEquals(QuranLayoutWordKey(2, 3, 8), key)
        assertEquals("2:3:8", key?.value)
    }

    @Test
    fun rejectsMalformedSourceWordKey() {
        assertNull(QuranLayoutWordKey.parse("2:3"))
        assertNull(QuranLayoutWordKey.parse("2:x:8"))
    }
}
