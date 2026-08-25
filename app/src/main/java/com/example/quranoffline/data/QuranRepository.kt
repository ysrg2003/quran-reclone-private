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
        private val basmalaText = listOf("بِسۡمِ", "ٱللَّهِ", "ٱلرَّحۡمَٰنِ", "ٱلرَّحۡيمِ")
        private val basmalaMatchText = listOf("بسم", "الله", "الرحمان", "الرحيم")

        private fun parse(raw: String): List<QuranAyah> {
            if (raw.isBlank()) return emptyList()
            val root = JSONObject(raw)
            val items = root.getJSONArray("ayahs")
            return buildList(items.length()) {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val surah = item.getInt("surah")
                    val ayah = item.getInt("ayah")
                    val wordsJson = item.getJSONArray("words")
                    val rawWords = buildList(wordsJson.length()) {
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
                    val wordsToKeep = if (isLegacyOpeningBasmala(surah, ayah, rawWords)) rawWords.drop(4) else rawWords
                    val words = wordsToKeep.mapIndexed { index, word -> word.copy(index = index) }
                    add(
                        QuranAyah(
                            surah = surah,
                            ayah = ayah,
                            text = words.joinToString(" ") { it.text },
                            matchText = words.joinToString(" ") { it.matchText },
                            words = words,
                        )
                    )
                }
            }
        }

        /** Keeps a network-fallback cache on the same contract as the migrated asset. */
        private fun isLegacyOpeningBasmala(surah: Int, ayah: Int, words: List<QuranWord>): Boolean {
            if (surah !in 2..114 || surah == 9 || ayah != 1 || words.size < 5) return false
            val firstText = words.take(4).map { normalizeArabicForComparison(it.text) }
            val firstMatch = words.take(4).map { normalizeArabicForComparison(it.matchText) }
            return firstText == basmalaText.map(::normalizeArabicForComparison) &&
                firstMatch == basmalaMatchText.map(::normalizeArabicForComparison)
        }
    }
}
