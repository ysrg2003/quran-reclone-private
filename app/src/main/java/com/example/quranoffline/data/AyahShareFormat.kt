package com.example.quranoffline.data

/** Copy/share format (user, v24.9): "(" + ayah text + ")" in plain parentheses, one group per ayah. */
object AyahShareFormat {
    fun body(texts: List<String>): String = texts.joinToString(" ") { "($it)" }
}
