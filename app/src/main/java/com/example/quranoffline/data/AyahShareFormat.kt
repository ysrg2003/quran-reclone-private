package com.example.quranoffline.data

/**
 * Copy/share format (user, v25.0): each ayah as "(" + text + ")", then one bracket with the surah name and the
 * ayah number or range in Arabic-Indic digits, e.g. (...) [البقرة: ٥] or (a) (b) [البقرة: ٥-٦]. No image, nothing else.
 */
object AyahShareFormat {
    fun digits(n: Int): String = n.toString().map { '٠' + (it - '0') }.joinToString("")

    fun body(texts: List<String>): String = texts.joinToString(" ") { "($it)" }

    fun reference(firstSurahName: String, firstAyah: Int, lastSurahName: String, lastAyah: Int, sameSurah: Boolean, count: Int): String =
        when {
            count <= 1 || (sameSurah && firstAyah == lastAyah) -> "[$firstSurahName: ${digits(firstAyah)}]"
            sameSurah -> "[$firstSurahName: ${digits(firstAyah)}-${digits(lastAyah)}]"
            else -> "[$firstSurahName: ${digits(firstAyah)} - $lastSurahName: ${digits(lastAyah)}]"
        }

    fun text(texts: List<String>, firstSurahName: String, firstAyah: Int, lastSurahName: String, lastAyah: Int, sameSurah: Boolean): String =
        body(texts) + " " + reference(firstSurahName, firstAyah, lastSurahName, lastAyah, sameSurah, texts.size)
}
