package com.example.quranoffline.data

import android.content.Context
import java.util.UUID

/** One mistake that was eventually corrected during a recitation session. */
data class CorrectedRecitationError(
    val id: String,
    val surah: Int,
    val ayah: Int,
    val localWordIndex: Int,
    val expected: String,
    val observed: String?,
    val correctedWith: String?,
    val kind: CorrectionKind,
    val correctedAt: Long,
)

/** Small, deterministic line codec so JVM tests do not depend on Android JSON stubs. */
object CorrectedRecitationErrorCodec {
    const val VERSION = 2

    fun encode(errors: List<CorrectedRecitationError>): String = buildString {
        append("v").append(VERSION).append('\n')
        errors.forEach { error ->
            append(error.id).append('\t')
                .append(error.kind.name).append('\t')
                .append(error.surah).append('\t')
                .append(error.ayah).append('\t')
                .append(error.localWordIndex).append('\t')
                .append(error.correctedAt).append('\t')
                .append(hexEncode(error.expected)).append('\t')
                .append(error.observed?.let(::hexEncode) ?: "-").append('\t')
                .append(error.correctedWith?.let(::hexEncode) ?: "-")
                .append('\n')
        }
    }

    fun decode(raw: String?): List<CorrectedRecitationError> {
        if (raw.isNullOrBlank()) return emptyList()
        val lines = raw.lineSequence().toList()
        if (lines.firstOrNull() != "v$VERSION") return emptyList()
        return lines.drop(1).mapNotNull { line ->
            val fields = line.split('\t')
            if (fields.size != 9) return@mapNotNull null
            runCatching {
                val expected = hexDecode(fields[6]) ?: return@runCatching null
                val observed = if (fields[7] == "-") null else hexDecode(fields[7]) ?: return@runCatching null
                val correctedWith = if (fields[8] == "-") null else hexDecode(fields[8]) ?: return@runCatching null
                CorrectedRecitationError(
                    id = fields[0],
                    kind = CorrectionKind.valueOf(fields[1]),
                    surah = fields[2].toInt(),
                    ayah = fields[3].toInt(),
                    localWordIndex = fields[4].toInt(),
                    correctedAt = fields[5].toLong(),
                    expected = expected,
                    observed = observed,
                    correctedWith = correctedWith,
                )
            }.getOrNull()
        }
    }

    private fun hexEncode(value: String): String = value.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    private fun hexDecode(value: String): String? {
        if (value.length % 2 != 0) return null
        val bytes = ByteArray(value.length / 2)
        for (index in bytes.indices) {
            val high = value[index * 2].digitToIntOrNull(16) ?: return null
            val low = value[index * 2 + 1].digitToIntOrNull(16) ?: return null
            bytes[index] = ((high shl 4) or low).toByte()
        }
        return bytes.toString(Charsets.UTF_8)
    }
}

class LocalRecitationErrorStore(context: Context) {
    private val preferences = context.getSharedPreferences("recitation_error_history", Context.MODE_PRIVATE)
    private val key = "corrected_errors"
    private val maxEntries = 500

    fun list(): List<CorrectedRecitationError> = CorrectedRecitationErrorCodec.decode(preferences.getString(key, null))
        .sortedByDescending { it.correctedAt }

    fun append(error: CorrectedRecitationError) = appendAll(listOf(error))

    fun appendAll(errors: List<CorrectedRecitationError>) {
        if (errors.isEmpty()) return
        val merged = (list() + errors)
            .distinctBy { it.id }
            .sortedByDescending { it.correctedAt }
            .take(maxEntries)
        preferences.edit().putString(key, CorrectedRecitationErrorCodec.encode(merged)).apply()
    }

    fun clear() {
        preferences.edit().remove(key).apply()
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
