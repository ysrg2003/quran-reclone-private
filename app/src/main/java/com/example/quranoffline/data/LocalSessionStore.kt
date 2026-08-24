package com.example.quranoffline.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Local, dependency-free store for reopenable reading and memorization sessions. */
data class SessionSummary(
    val id: String,
    val surah: Int,
    val startAyah: Int,
    val endAyah: Int,
    val matchedWords: Int,
    val totalWords: Int,
    val uncertainWords: Int,
    val createdAt: Long,
    val label: String = "جلسة تسميع",
    val page: Int = 0,
    val wordIndex: Int = 0,
    val scope: String = "AYAH",
    val checkpointSurah: Int? = null,
    val checkpointAyah: Int? = null,
    val checkpointWordIndex: Int? = null,
    val includeOpeningBasmala: Boolean = false,
    val lastOpenedAt: Long = createdAt,
)

object SessionSummaryRanking {
    fun latestFirst(sessions: List<SessionSummary>): List<SessionSummary> = sessions.sortedByDescending { it.lastOpenedAt }
}

object SessionSummaryCodec {
    fun encode(summary: SessionSummary): String = summary.toJson().toString()

    fun decode(raw: String): SessionSummary? = runCatching {
        val item = JSONObject(raw)
        val createdAt = item.optLong("createdAt", 0L)
        SessionSummary(
            id = item.getString("id"),
            surah = item.getInt("surah"),
            startAyah = item.getInt("startAyah"),
            endAyah = item.getInt("endAyah"),
            matchedWords = item.optInt("matchedWords", 0),
            totalWords = item.optInt("totalWords", 0),
            uncertainWords = item.optInt("uncertainWords", 0),
            createdAt = createdAt,
            label = item.optString("label", "جلسة تسميع"),
            page = item.optInt("page", 0),
            wordIndex = item.optInt("wordIndex", 0),
            scope = item.optString("scope", "AYAH"),
            checkpointSurah = item.optNullableInt("checkpointSurah"),
            checkpointAyah = item.optNullableInt("checkpointAyah"),
            checkpointWordIndex = item.optNullableInt("checkpointWordIndex"),
            includeOpeningBasmala = item.optBoolean("includeOpeningBasmala", false),
            lastOpenedAt = item.optLong("lastOpenedAt", createdAt),
        )
    }.getOrNull()

    private fun SessionSummary.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("surah", surah)
        put("startAyah", startAyah)
        put("endAyah", endAyah)
        put("matchedWords", matchedWords)
        put("totalWords", totalWords)
        put("uncertainWords", uncertainWords)
        put("createdAt", createdAt)
        put("label", label)
        put("page", page)
        put("wordIndex", wordIndex)
        put("scope", scope)
        checkpointSurah?.let { put("checkpointSurah", it) }
        checkpointAyah?.let { put("checkpointAyah", it) }
        checkpointWordIndex?.let { put("checkpointWordIndex", it) }
        put("includeOpeningBasmala", includeOpeningBasmala)
        put("lastOpenedAt", lastOpenedAt)
    }
}

class LocalSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("offline_sessions", Context.MODE_PRIVATE)
    private val key = "sessions"

    fun list(): List<SessionSummary> {
        val array = JSONArray(preferences.getString(key, "[]"))
        val sessions = buildList(array.length()) {
            for (i in 0 until array.length()) {
                SessionSummaryCodec.decode(array.getJSONObject(i).toString())?.let(::add)
            }
        }
        return SessionSummaryRanking.latestFirst(sessions)
    }

    fun append(summary: SessionSummary = SessionSummary(
        id = UUID.randomUUID().toString(),
        surah = 1,
        startAyah = 1,
        endAyah = 1,
        matchedWords = 0,
        totalWords = 0,
        uncertainWords = 0,
        createdAt = System.currentTimeMillis(),
    )) {
        val sessions = JSONArray(preferences.getString(key, "[]"))
        sessions.put(JSONObject(SessionSummaryCodec.encode(summary)))
        preferences.edit().putString(key, sessions.toString()).apply()
    }

    fun touch(id: String, openedAt: Long = System.currentTimeMillis()) {
        val sessions = JSONArray(preferences.getString(key, "[]"))
        for (i in 0 until sessions.length()) {
            val item = sessions.getJSONObject(i)
            if (item.optString("id") == id) item.put("lastOpenedAt", openedAt)
        }
        preferences.edit().putString(key, sessions.toString()).apply()
    }

    fun clear() {
        preferences.edit().remove(key).apply()
    }
}

private fun JSONObject.optNullableInt(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null
