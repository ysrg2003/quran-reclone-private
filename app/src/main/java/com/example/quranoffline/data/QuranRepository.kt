package com.example.quranoffline.data

import android.content.Context
import org.json.JSONObject

/**
 * Quran text repository whose only runtime source is the supplied Word-derived
 * canonical asset. Speech comparison metadata is read from precomputed fields;
 * no external text participates in this repository.
 */
data class QuranWord(
    val index: Int,
    val text: String,
    val matchText: String,
    /** Precomputed spoken form copied from the comparison asset. */
    val spokenForm: String = matchText,
    /** Precomputed Uthmani/provider forms tied to this exact word. */
    val recognizedForms: Set<String> = emptySet(),
    /** Precomputed pause/provider forms tied to this exact word. */
    val waqfForms: Set<String> = emptySet(),
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
    private val assetPath: String = "quran/word_reference_ayahs.json",
) {
    private val appContext = context.applicationContext
    private val ayahs: List<QuranAyah> by lazy {
        val raw = appContext.assets
            .open(assetPath)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        withRecognizerForms(parseWordReference(raw))
    }

    /**
     * Adds the standard spelling a speech-to-text service writes for each word.
     * Comparison only; displayed Quran text is never changed. Missing asset or
     * ayahs with a different word count keep the previous behavior.
     */
    private fun withRecognizerForms(base: List<QuranAyah>): List<QuranAyah> {
        val forms = runCatching {
            val raw = appContext.assets.open("quran/recognizer_forms.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(raw).getJSONObject("ayahs")
            HashMap<String, List<String>>(root.length()).also { map ->
                root.keys().forEach { key ->
                    val array = root.getJSONArray(key)
                    map[key] = List(array.length()) { array.getString(it) }
                }
            }
        }.getOrNull() ?: return base
        RecognizerSpacedForms.register(forms.values.flatten().filter { it.contains(' ') })
        return base.map { ayah ->
            val list = forms["${ayah.surah}:${ayah.ayah}"]
            if (list == null || list.size != ayah.words.size) ayah
            else ayah.copy(words = ayah.words.mapIndexed { i, w -> w.copy(recognizedForms = w.recognizedForms + list[i]) })
        }
    }

    fun allAyahs(): List<QuranAyah> = ayahs

    fun find(surah: Int, ayah: Int): QuranAyah? =
        ayahs.firstOrNull { it.surah == surah && it.ayah == ayah }

    fun ayahsForSurah(surah: Int): List<QuranAyah> = ayahs.filter { it.surah == surah }

    fun firstAyahs(limit: Int = 3): List<QuranAyah> = ayahs.take(limit)

    fun isPreview(): Boolean = assetPath != "quran/word_reference_ayahs.json"

    fun surahNumbers(): List<Int> = ayahs.map { it.surah }.distinct()

    companion object {
        private fun parseWordReference(raw: String): List<QuranAyah> {
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
                            val visual = word.getString("text")
                            val matchText = word.optString("matchText", "")
                            add(
                                QuranWord(
                                    index = word.optInt("index", j),
                                    text = visual,
                                    matchText = matchText,
                                    spokenForm = word.optString("spokenForm", matchText),
                                    recognizedForms = jsonStringSet(word, "recognizedForms"),
                                    waqfForms = jsonStringSet(word, "waqfForms"),
                                ),
                            )
                        }
                    }
                    add(
                        QuranAyah(
                            surah = item.getInt("surah"),
                            ayah = item.getInt("ayah"),
                            text = words.joinToString(" ") { it.text },
                            matchText = words.joinToString(" ") { it.matchText },
                            words = words,
                        ),
                    )
                }
            }
        }

        private fun jsonStringSet(word: JSONObject, key: String): Set<String> {
            val array = word.optJSONArray(key) ?: return emptySet()
            return buildSet(array.length()) {
                for (index in 0 until array.length()) {
                    val value = array.optString(index).trim()
                    if (value.isNotEmpty()) add(value)
                }
            }
        }
    }
}
