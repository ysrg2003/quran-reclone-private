package com.example.quranoffline.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

object LocalBackup {
    fun write(context: Context, uri: Uri, sessions: List<SessionSummary>, goal: DailyGoal) {
        val payload = JSONObject().apply {
            put("format", "quran-offline-backup-v1")
            put("createdAt", System.currentTimeMillis())
            put("goal", JSONObject().apply {
                put("targetAyahs", goal.targetAyahs)
                put("completedAyahs", goal.completedAyahs)
                put("day", goal.day)
            })
            put("sessions", JSONArray().apply {
                sessions.forEach { session ->
                    put(JSONObject().apply {
                        put("id", session.id)
                        put("surah", session.surah)
                        put("startAyah", session.startAyah)
                        put("endAyah", session.endAyah)
                        put("matchedWords", session.matchedWords)
                        put("totalWords", session.totalWords)
                        put("uncertainWords", session.uncertainWords)
                        put("createdAt", session.createdAt)
                    })
                }
            })
        }
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(payload.toString(2).toByteArray(Charsets.UTF_8))
        } ?: error("تعذر فتح ملف النسخ الاحتياطي")
    }
}
