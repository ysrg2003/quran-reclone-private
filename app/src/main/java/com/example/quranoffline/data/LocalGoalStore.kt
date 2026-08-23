package com.example.quranoffline.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DailyGoal(
    val targetAyahs: Int,
    val completedAyahs: Int,
    val day: String,
) {
    val progress: Float
        get() = if (targetAyahs <= 0) 0f else (completedAyahs.toFloat() / targetAyahs).coerceIn(0f, 1f)
}

class LocalGoalStore(context: Context) {
    private val preferences = context.getSharedPreferences("offline_goal", Context.MODE_PRIVATE)
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun current(): DailyGoal {
        val today = dayFormat.format(Date())
        val savedDay = preferences.getString("day", today) ?: today
        if (savedDay != today) {
            preferences.edit().putString("day", today).putInt("completed", 0).apply()
        }
        return DailyGoal(
            targetAyahs = preferences.getInt("target", 3),
            completedAyahs = preferences.getInt("completed", 0),
            day = today,
        )
    }

    fun setTarget(targetAyahs: Int) {
        preferences.edit().putInt("target", targetAyahs.coerceIn(1, 604)).apply()
    }

    fun addCompleted(ayahs: Int) {
        val current = current()
        preferences.edit().putInt("completed", current.completedAyahs + ayahs.coerceAtLeast(0)).apply()
    }
}
