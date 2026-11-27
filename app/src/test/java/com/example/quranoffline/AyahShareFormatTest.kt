package com.example.quranoffline

import com.example.quranoffline.data.AyahShareFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class AyahShareFormatTest {
    @Test fun singleAyahIsPlainParenthesesAroundTheText() = assertEquals("(الحمد لله رب العالمين)", AyahShareFormat.body(listOf("الحمد لله رب العالمين")))
    @Test fun eachAyahOfARangeIsWrappedSeparately() = assertEquals("(أ) (ب)", AyahShareFormat.body(listOf("أ", "ب")))
}
