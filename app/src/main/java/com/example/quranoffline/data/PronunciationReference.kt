package com.example.quranoffline.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Speech-only pronunciation metadata. It is deliberately separate from the
 * Word reference renderer and never changes QuranAyah.text or QuranWord.text.
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
    val legacyWordIndices: List<Int> = emptyList(),
)

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
        fun load(context: Context): PronunciationReference {
            val raw = context.applicationContext.assets
                .open("quran/pronunciation_reference.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            return fromJson(raw)
        }

        fun fromJson(raw: String): PronunciationReference {
            if (raw.isBlank()) return PronunciationReference(emptyMap())
            val root = JSONObject(raw)
            val rows = root.getJSONArray("ayahs")
            val result = buildMap(rows.length()) {
                for (index in 0 until rows.length()) {
                    val row = rows.getJSONObject(index)
                    val wordsJson = row.getJSONArray("words")
                    val words = buildList(wordsJson.length()) {
                        for (wordIndex in 0 until wordsJson.length()) {
                            val word = wordsJson.getJSONObject(wordIndex)
                            val spoken = word.getString("spoken")
                            val spokenTokens = word.getJSONArray("spokenTokens").toStringList()
                            add(
                                PronunciationWord(
                                    index = word.getInt("index"),
                                    spoken = spoken,
                                    spokenTokens = spokenTokens,
                                    maddPositions = word.getJSONArray("maddPositions").toIntSet(),
                                    audioStartMs = word.optionalInt("audioStartMs"),
                                    audioEndMs = word.optionalInt("audioEndMs"),
                                    audioWordStart = word.optionalInt("audioWordStart"),
                                    audioWordEnd = word.optionalInt("audioWordEnd"),
                                    legacyWordIndices = word.optJSONArray("legacyWordIndices")?.toIntList().orEmpty(),
                                ),
                            )
                        }
                    }
                    val pronunciation = PronunciationAyah(
                        surah = row.getInt("surah"),
                        ayah = row.getInt("ayah"),
                        spoken = row.getString("spoken"),
                        words = words,
                    )
                    put("${pronunciation.surah}:${pronunciation.ayah}", pronunciation)
                }
            }
            return PronunciationReference(result)
        }

        private fun JSONArray.toStringList(): List<String> =
            buildList(length()) { for (index in 0 until length()) add(getString(index)) }

        private fun JSONArray.toIntSet(): Set<Int> =
            buildSet(length()) { for (index in 0 until length()) add(getInt(index)) }

        private fun JSONArray.toIntList(): List<Int> =
            buildList(length()) { for (index in 0 until length()) add(getInt(index)) }

        private fun JSONObject.optionalInt(key: String): Int? =
            runCatching { getInt(key) }.getOrNull()
    }
}
