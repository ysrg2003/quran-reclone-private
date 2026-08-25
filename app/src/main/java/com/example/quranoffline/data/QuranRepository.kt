package com.example.quranoffline.data

import android.content.Context
import org.json.JSONObject

/**
 * Canonical Quran data loaded only from the bundled Word-derived asset.
 *
 * `text` is the supplied Uthmanic Word spelling. `matchText` is a separate
 * comparison hint and is never used to render or replace the canonical text.
 */
data class QuranWord(
    val index: Int,
    val text: String,
    val matchText: String,
)

data class QuranAyah(
    val surah: Int,
    val ayah: Int,
    val text: String,
    val matchText: String,
    val words: List<QuranWord>,
)

class QuranRepository(context: Context) {
    private val appContext = context.applicationContext
    private val ayahs: List<QuranAyah> by lazy {
        val raw = appContext.assets.open("quran/word_reference_ayahs.json")
            .bufferedReader()
            .use { it.readText() }
        parse(raw)
    }

    fun allAyahs(): List<QuranAyah> = ayahs

    fun find(surah: Int, ayah: Int): QuranAyah? =
        ayahs.firstOrNull { it.surah == surah && it.ayah == ayah }

    fun ayahsForSurah(surah: Int): List<QuranAyah> = ayahs.filter { it.surah == surah }

    fun firstAyahs(limit: Int = 3): List<QuranAyah> = ayahs.take(limit)

    fun surahNumbers(): List<Int> = ayahs.map { it.surah }.distinct()

    companion object {
        private fun parse(raw: String): List<QuranAyah> {
            require(raw.isNotBlank()) { "word_reference_ayahs.json is empty" }
            val root = JSONObject(raw)
            require(root.getInt("ayahCount") == 6236) { "Unexpected Word reference ayah count" }
            require(root.getInt("wordCount") == 77430) { "Unexpected Word reference word count" }
            val items = root.getJSONArray("ayahs")
            return buildList(items.length()) {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val wordsJson = item.getJSONArray("words")
                    val words = buildList(wordsJson.length()) {
                        for (j in 0 until wordsJson.length()) {
                            val word = wordsJson.getJSONObject(j)
                            add(
                                QuranWord(
                                    index = word.getInt("index"),
                                    text = word.getString("text"),
                                    matchText = word.optString("matchText"),
                                ),
                            )
                        }
                    }
                    require(words.map { it.index } == words.indices.toList()) {
                        "Non-contiguous Word indices at ${item.getInt("surah")}:${item.getInt("ayah")}"
                    }
                    val wordText = words.joinToString(" ") { it.text }
                    add(
                        QuranAyah(
                            surah = item.getInt("surah"),
                            ayah = item.getInt("ayah"),
                            text = wordText,
                            matchText = words.joinToString(" ") { it.matchText },
                            words = words,
                        ),
                    )
                }
            }.also { result ->
                require(result.size == 6236) { "Unexpected parsed Word reference size" }
                require(result.zipWithNext().all { (first, second) ->
                    second.surah > first.surah || (second.surah == first.surah && second.ayah > first.ayah)
                }) { "Word reference ayahs are not in Quran order" }
            }
        }
    }
}
