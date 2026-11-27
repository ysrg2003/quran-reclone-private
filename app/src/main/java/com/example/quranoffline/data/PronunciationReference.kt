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

    /** Ayahs in their original order (used by the parsed-data cache). */
    fun allAyahs(): List<PronunciationAyah> = ayahsByKey.values.toList()

    fun size(): Int = ayahsByKey.size

    companion object {
        fun empty(): PronunciationReference = PronunciationReference(emptyMap())

        /** Builds a read-only reference from already-precomputed rows. */
        fun fromAyahs(ayahs: List<PronunciationAyah>): PronunciationReference =
            PronunciationReference(ayahs.associateBy { "${it.surah}:${it.ayah}" })

        /**
         * Loads the precomputed speech-only reference. SpeechRecognizer output
         * is still the observed runtime value; this asset supplies only the
         * expected spoken forms and the explicit madd/waqf rules.
         */
        fun load(context: Context): PronunciationReference {
            val list = ParsedCache.load(
                context, "pronunciation", 6236,
                build = {
                    loadFromJson(context).allAyahs()
                },
                write = ParsedCache::writePronunciation,
                read = ParsedCache::readPronunciation,
            )
            return fromAyahs(list)
        }

        private fun loadFromJson(context: Context): PronunciationReference {
            val raw = context.applicationContext.assets
                .open("quran/pronunciation_reference.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            return fromPronunciationJson(raw)
        }

        fun fromPronunciationJson(raw: String): PronunciationReference {
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
                            val spoken = word.optString("spoken", word.optString("spokenForm", ""))
                            add(
                                PronunciationWord(
                                    index = word.optInt("index", wordIndex),
                                    spoken = spoken,
                                    spokenTokens = jsonStringList(word, "spokenTokens").ifEmpty { listOf(spoken) },
                                    maddPositions = jsonIntSet(word, "maddPositions"),
                                    audioStartMs = word.optIntNullable("audioStartMs"),
                                    audioEndMs = word.optIntNullable("audioEndMs"),
                                    audioWordStart = word.optIntNullable("audioWordStart"),
                                    audioWordEnd = word.optIntNullable("audioWordEnd"),
                                    recognizedForms = jsonStringSet(word, "recognizedForms"),
                                    waqfForms = jsonStringSet(word, "waqfForms"),
                                ),
                            )
                        }
                    }
                    val pronunciation = PronunciationAyah(
                        surah = row.getInt("surah"),
                        ayah = row.getInt("ayah"),
                        spoken = row.optString("spoken", words.joinToString(" ") { it.spoken }),
                        words = words,
                    )
                    put("${pronunciation.surah}:${pronunciation.ayah}", pronunciation)
                }
            }
            return PronunciationReference(result)
        }

        /** Compatibility parser for the minimal Word-only fixture format. */
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

        private fun JSONObject.optIntNullable(key: String): Int? =
            if (has(key) && !isNull(key)) getInt(key) else null

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
