package com.example.quranoffline.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

object LocalBackup {
    fun write(
        context: Context,
        uri: Uri,
        sessions: List<SessionSummary>,
        goal: DailyGoal,
        correctionSettings: CorrectionSettings = CorrectionSettings.DEFAULTS,
    ) {
        val payload = JSONObject().apply {
            put("format", "quran-online-backup-v2")
            put("createdAt", System.currentTimeMillis())
            put("goal", JSONObject().apply {
                put("targetAyahs", goal.targetAyahs)
                put("completedAyahs", goal.completedAyahs)
                put("day", goal.day)
            })
            put("correctionSettings", JSONObject().apply {
                val settings = correctionSettings.normalized()
                put("automaticCorrectionEnabled", settings.automaticCorrectionEnabled)
                put("correctWrongWords", settings.correctWrongWords)
                put("correctSkippedWords", settings.correctSkippedWords)
                put("correctAyahJumps", settings.correctAyahJumps)
                put("wholeAyahAfterConsecutiveErrors", settings.wholeAyahAfterConsecutiveErrors)
                put("playFirstErrorBeforeWholeAyah", settings.playFirstErrorBeforeWholeAyah)
                put("wordRepeatCount", settings.wordRepeatCount)
                put("wholeAyahRepeatCount", settings.wholeAyahRepeatCount)
                put("pauseBetweenPartsMs", settings.pauseBetweenPartsMs)
                put("clipPaddingBeforeMs", settings.clipPaddingBeforeMs)
                put("clipPaddingAfterMs", settings.clipPaddingAfterMs)
                put("resumePosition", settings.resumePosition.name)
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
