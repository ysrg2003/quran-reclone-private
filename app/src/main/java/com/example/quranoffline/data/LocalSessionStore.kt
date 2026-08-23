package com.example.quranoffline.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Small, dependency-free local store for the first offline slice. */
data class SessionSummary(
    val id: String,
    val surah: Int,
    val startAyah: Int,
    val endAyah: Int,
    val matchedWords: Int,
    val totalWords: Int,
    val uncertainWords: Int,
    val createdAt: Long,
)

class LocalSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("offline_sessions", Context.MODE_PRIVATE)
    private val key = "sessions"

    fun list(): List<SessionSummary> {
        val array = JSONArray(preferences.getString(key, "[]"))
        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    SessionSummary(
                        id = item.getString("id"),
                        surah = item.getInt("surah"),
                        startAyah = item.getInt("startAyah"),
                        endAyah = item.getInt("endAyah"),
                        matchedWords = item.getInt("matchedWords"),
                        totalWords = item.getInt("totalWords"),
                        uncertainWords = item.getInt("uncertainWords"),
                        createdAt = item.getLong("createdAt"),
                    )
                )
            }
        }.sortedByDescending { it.createdAt }
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
        sessions.put(
            JSONObject().apply {
                put("id", summary.id)
                put("surah", summary.surah)
                put("startAyah", summary.startAyah)
                put("endAyah", summary.endAyah)
                put("matchedWords", summary.matchedWords)
                put("totalWords", summary.totalWords)
                put("uncertainWords", summary.uncertainWords)
                put("createdAt", summary.createdAt)
            }
        )
        preferences.edit().putString(key, sessions.toString()).apply()
    }

    fun clear() {
        preferences.edit().remove(key).apply()
    }
}
