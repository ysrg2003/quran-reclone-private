package com.example.quranoffline.data

/**
 * Copy/share format (user, v25.5): ONE block in curly braces, each ayah preceded by its number marker exactly as the
 * reading screen draws it (﴿n﴾ text), then the reference [سورة اسم : n] or [سورة اسم : a-b]. Nothing else.
 */
object AyahShareFormat {
    fun digits(n: Int): String = n.toString().map { '٠' + (it - '0') }.joinToString("")

    /** items = (surah, ayah, text) */
    fun body(numbers: List<Int>, texts: List<String>): String =
        "{" + texts.indices.joinToString(" ") { "\uFD3F${numbers[it]}\uFD3E ${texts[it]}" } + "}"

    fun reference(firstSurahName: String, firstAyah: Int, lastSurahName: String, lastAyah: Int, sameSurah: Boolean, count: Int): String =
        when {
            count <= 1 || (sameSurah && firstAyah == lastAyah) -> "[سورة $firstSurahName : ${digits(firstAyah)}]"
            sameSurah -> "[سورة $firstSurahName : ${digits(firstAyah)}-${digits(lastAyah)}]"
            else -> "[سورة $firstSurahName : ${digits(firstAyah)} - سورة $lastSurahName : ${digits(lastAyah)}]"
        }

    fun text(numbers: List<Int>, texts: List<String>, firstSurahName: String, lastSurahName: String, sameSurah: Boolean): String =
        body(numbers, texts) + " " + reference(firstSurahName, numbers.first(), lastSurahName, numbers.last(), sameSurah, texts.size)
}
