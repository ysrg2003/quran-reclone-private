package com.example.quranoffline.data

import android.content.Context
import org.json.JSONObject

/**
 * Expected speech forms derived from the exact Word/Uthmanic token and the
 * explicit pronunciation rules. This is not provider output: SpeechRecognizer
 * supplies observed text at runtime, while this class supplies expected forms.
 */
data class PronunciationWord(
    val index: Int,
    val spoken: String,
    val spokenTokens: List<String>,
    val maddPositions: Set<Int>,
    val audioStartMs: Int?,
    val audioEndMs: Int?,
    val audioWordStart: Int?,
    val audioWordEnd: Int?,
    /** Precomputed Uthmani/provider spellings accepted only for this word. */
    val recognizedForms: Set<String> = emptySet(),
    /** Precomputed pause/provider spellings tied to this Word token. */
    val waqfForms: Set<String> = emptySet(),

) {
    /** Compatibility view for older callers; new code should name the sets explicitly. */
    val spokenAlternates: Set<String> get() = recognizedForms + waqfForms
}

data class PronunciationAyah(
    val surah: Int,
    val ayah: Int,
    val spoken: String,
    val words: List<PronunciationWord>,
)

class PronunciationReference private constructor(
    private val ayahsByKey: Map<String, PronunciationAyah>,
) {
    fun ayah(surah: Int, ayah: Int): PronunciationAyah? = ayahsByKey["$surah:$ayah"]

    fun word(surah: Int, ayah: Int, localWordIndex: Int): PronunciationWord? =
        this.ayah(surah, ayah)?.words?.getOrNull(localWordIndex)

    fun isEmpty(): Boolean = ayahsByKey.isEmpty()

    fun size(): Int = ayahsByKey.size

    companion object {
        fun empty(): PronunciationReference = PronunciationReference(emptyMap())

        /** Builds a read-only reference from already-precomputed rows. */
        fun fromAyahs(ayahs: List<PronunciationAyah>): PronunciationReference =
            PronunciationReference(ayahs.associateBy { "${it.surah}:${it.ayah}" })

        /**
         * Builds expected speech directly from Word reference JSON. The JSON is
         * the extracted Word canonical content; no quran_content or audio
         * transcript is consulted, and no provider output is stored here.
         */
        fun load(context: Context): PronunciationReference {
            val raw = context.applicationContext.assets
                .open("quran/word_reference_ayahs.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            return fromWordReferenceJson(raw)
        }

        fun fromWordReferenceJson(raw: String): PronunciationReference {
            if (raw.isBlank()) return empty()
            val root = JSONObject(raw)
            val rows = root.getJSONArray("ayahs")
            val result = buildMap(rows.length()) {
                for (index in 0 until rows.length()) {
                    val row = rows.getJSONObject(index)
                    val wordsJson = row.getJSONArray("words")
                    val words = buildList(wordsJson.length()) {
                        for (wordIndex in 0 until wordsJson.length()) {
                            val word = wordsJson.getJSONObject(wordIndex)
                            val spoken = word.optString("spokenForm", word.optString("matchText", ""))
                            val spokenTokens = jsonStringList(word, "spokenTokens").ifEmpty { listOf(spoken) }
                            val recognizedForms = jsonStringSet(word, "recognizedForms")
                            val waqfForms = jsonStringSet(word, "waqfForms")
                            val maddPositions = jsonIntSet(word, "maddPositions")
                            add(
                                PronunciationWord(
                                    index = word.optInt("index", wordIndex),
                                    spoken = spoken,
                                    spokenTokens = spokenTokens,
                                    maddPositions = maddPositions,
                                    audioStartMs = null,
                                    audioEndMs = null,
                                    audioWordStart = null,
                                    audioWordEnd = null,
                                    recognizedForms = recognizedForms,
                                    waqfForms = waqfForms,
                                ),
                            )
                        }
                    }
                    val pronunciation = PronunciationAyah(
                        surah = row.getInt("surah"),
                        ayah = row.getInt("ayah"),
                        spoken = row.optString("spokenText", words.joinToString(" ") { it.spoken }),
                        words = words,
                    )
                    put("${pronunciation.surah}:${pronunciation.ayah}", pronunciation)
                }
            }
            return PronunciationReference(result)
        }

        private fun jsonStringList(word: JSONObject, key: String): List<String> {
            val array = word.optJSONArray(key) ?: return emptyList()
            return buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val value = array.optString(index).trim()
                    if (value.isNotEmpty()) add(value)
                }
            }
        }

        private fun jsonStringSet(word: JSONObject, key: String): Set<String> =
            jsonStringList(word, key).toSet()

        private fun jsonIntSet(word: JSONObject, key: String): Set<Int> {
            val array = word.optJSONArray(key) ?: return emptySet()
            return buildSet(array.length()) {
                for (index in 0 until array.length()) add(array.optInt(index))
            }
        }
    }
}
