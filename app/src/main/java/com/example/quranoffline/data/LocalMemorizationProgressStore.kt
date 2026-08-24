package com.example.quranoffline.data

import android.content.Context
/** Durable cursor for memorization; it deliberately does not use a global word index. */
data class MemorizationCheckpoint(
    val surah: Int,
    val ayah: Int,
    val wordIndex: Int,
    val includeOpeningBasmala: Boolean,
    val updatedAt: Long,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    fun key(): String = "$surah:$ayah:$wordIndex"

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        fun default(): MemorizationCheckpoint = MemorizationCheckpoint(1, 1, 0, false, 0L)
    }
}

class LocalMemorizationProgressStore(context: Context) {
    private val preferences = context.getSharedPreferences("memorization_progress", Context.MODE_PRIVATE)
    private val key = "checkpoint"

    fun read(): MemorizationCheckpoint? =
        preferences.getString(key, null)?.let(MemorizationCheckpointCodec::decode)

    fun save(checkpoint: MemorizationCheckpoint) {
        preferences.edit().putString(key, MemorizationCheckpointCodec.encode(checkpoint)).apply()
    }

    fun clear() { preferences.edit().remove(key).apply() }
}

object MemorizationCheckpointCodec {
    fun encode(checkpoint: MemorizationCheckpoint): String =
        "{\"schemaVersion\":${checkpoint.schemaVersion},\"surah\":${checkpoint.surah},\"ayah\":${checkpoint.ayah},\"wordIndex\":${checkpoint.wordIndex},\"includeOpeningBasmala\":${checkpoint.includeOpeningBasmala},\"updatedAt\":${checkpoint.updatedAt}}"

    fun decode(raw: String): MemorizationCheckpoint? = runCatching {
        val fields = Regex("\\\"(schemaVersion|surah|ayah|wordIndex|includeOpeningBasmala|updatedAt)\\\"\\s*:\\s*(true|false|-?\\d+)")
            .findAll(raw)
            .associate { match -> match.groupValues[1] to match.groupValues[2] }
        MemorizationCheckpoint(
            surah = fields.getValue("surah").toInt(),
            ayah = fields.getValue("ayah").toInt(),
            wordIndex = fields.getValue("wordIndex").toInt(),
            includeOpeningBasmala = fields["includeOpeningBasmala"]?.toBoolean() ?: false,
            updatedAt = fields["updatedAt"]?.toLong() ?: 0L,
            schemaVersion = fields["schemaVersion"]?.toInt() ?: 0,
        ).takeIf { it.schemaVersion == MemorizationCheckpoint.CURRENT_SCHEMA_VERSION }
    }.getOrNull()
}

fun MemorizationCheckpoint.validatedAgainst(ayahs: List<QuranAyah>): MemorizationCheckpoint? {
    if (schemaVersion != MemorizationCheckpoint.CURRENT_SCHEMA_VERSION) return null
    val record = ayahs.firstOrNull { it.surah == surah && it.ayah == this.ayah } ?: return null
    if (wordIndex !in 0..record.words.size) return null
    return copy(wordIndex = wordIndex.coerceIn(0, record.words.size))
}
