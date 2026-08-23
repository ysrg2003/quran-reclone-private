package com.example.quranoffline.data

import android.content.Context
import org.json.JSONObject

/** Canonical Quran data loaded from bundled or remotely fetched Tanzil-derived JSON. */
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

class QuranRepository(
    context: Context,
    private val remoteText: String? = null,
) {
    private val appContext = context.applicationContext
    private val ayahs: List<QuranAyah> by lazy {
        val raw = remoteText ?: runCatching {
            appContext.assets.open("quran/quran_content.json").bufferedReader().use { it.readText() }
        }.getOrDefault("")
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
            if (raw.isBlank()) return emptyList()
            val root = JSONObject(raw)
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
                                    matchText = word.getString("matchText"),
                                )
                            )
                        }
                    }
                    add(
                        QuranAyah(
                            surah = item.getInt("surah"),
                            ayah = item.getInt("ayah"),
                            text = item.getString("text"),
                            matchText = item.getString("matchText"),
                            words = words,
                        )
                    )
                }
            }
        }
    }
}
