package com.example.quranoffline.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class WordReferenceAyahNumber(
    val start: Int,
    val end: Int,
    val text: String,
    val number: Int,
)

data class WordReferenceLine(
    val number: Int,
    val type: String,
    val text: String,
    val ayahKeys: List<String>,
    val ayahNumbers: List<WordReferenceAyahNumber>,
)

data class WordReferencePage(
    val page: Int,
    val lines: List<WordReferenceLine>,
)

class WordReferenceLayout(
    val schemaVersion: Int,
    val source: String,
    val fontFamily: String,
    val pages: List<WordReferencePage>,
) {
    val pageCount: Int get() = pages.size
    val lineCount: Int get() = pages.sumOf { it.lines.size }

    fun linesForPage(page: Int): List<WordReferenceLine> =
        pages.firstOrNull { it.page == page }?.lines.orEmpty()

    fun ayahKeysForPage(page: Int): List<Pair<Int, Int>> =
        linesForPage(page).flatMap { line ->
            line.ayahKeys.mapNotNull { key ->
                val parts = key.split(':')
                val surah = parts.getOrNull(0)?.toIntOrNull()
                val ayah = parts.getOrNull(1)?.toIntOrNull()
                if (surah != null && ayah != null) surah to ayah else null
            }
        }.distinct()

    fun pageForAyah(surah: Int, ayah: Int): Int? =
        pages.firstOrNull { page -> (surah to ayah) in ayahKeysForPage(page.page) }?.page

    companion object {
        fun load(context: Context): WordReferenceLayout {
            val raw = context.assets.open("quran/word_reference_layout.json").bufferedReader().use { it.readText() }
            return fromJson(raw)
        }

        fun fromJson(raw: String): WordReferenceLayout {
            val root = JSONObject(raw)
            val pagesJson = root.getJSONArray("pages")
            val pages = buildList(pagesJson.length()) {
                for (pageIndex in 0 until pagesJson.length()) {
                    val pageObject = pagesJson.getJSONObject(pageIndex)
                    val linesJson = pageObject.getJSONArray("lines")
                    val lines = buildList(linesJson.length()) {
                        for (lineIndex in 0 until linesJson.length()) {
                            val lineObject = linesJson.getJSONObject(lineIndex)
                            val keysJson = lineObject.optJSONArray("ayahKeys") ?: JSONArray()
                            val keys = buildList(keysJson.length()) {
                                for (keyIndex in 0 until keysJson.length()) add(keysJson.getString(keyIndex))
                            }
                            val numbersJson = lineObject.optJSONArray("ayahNumbers") ?: JSONArray()
                            val numbers = buildList(numbersJson.length()) {
                                for (numberIndex in 0 until numbersJson.length()) {
                                    val number = numbersJson.getJSONObject(numberIndex)
                                    add(
                                        WordReferenceAyahNumber(
                                            start = number.optInt("start"),
                                            end = number.optInt("end"),
                                            text = number.optString("text"),
                                            number = number.optInt("number"),
                                        ),
                                    )
                                }
                            }
                            add(
                                WordReferenceLine(
                                    number = lineObject.optInt("number", lineIndex + 1),
                                    type = lineObject.optString("type", "ayah"),
                                    text = lineObject.optString("text"),
                                    ayahKeys = keys,
                                    ayahNumbers = numbers,
                                ),
                            )
                        }
                    }
                    add(WordReferencePage(pageObject.optInt("page", pageIndex + 1), lines))
                }
            }
            return WordReferenceLayout(
                schemaVersion = root.optInt("schemaVersion", 1),
                source = root.optString("source"),
                fontFamily = root.optString("fontFamily"),
                pages = pages,
            )
        }
    }
}
