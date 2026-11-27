package com.example.quranoffline.data

import android.content.Context

/** Colour and visibility rules for words in the tasmee/hifz views. Colours are exactly the v11 values. */
object WordDisplayRules {
    const val MATCHED = 0xFFDFF1E5L          // green
    const val ERROR = 0xFFFFDDD8L            // red: wrong, extra or missing word
    const val UNCERTAIN = 0xFFFFF0C9L        // yellow
    const val CURRENT = 0xFFDFF1E5L          // word being said now
    const val NONE = 0x00000000L             // transparent

    fun background(status: WordStatus?, isCurrent: Boolean): Long = when (status) {
        WordStatus.MATCHED -> MATCHED
        WordStatus.EXTRA_OR_UNEXPECTED, WordStatus.MISSING -> ERROR
        WordStatus.UNCERTAIN -> UNCERTAIN
        null -> if (isCurrent) CURRENT else NONE
    }

    enum class Shown { CORRECT_WORD, CURRENT_SPOKEN, DOT, HIDDEN }

    /**
     * hidden = the "hide ayahs" mode. onlySpoken (new, default on) removes the dots so only words already said
     * or judged appear. A judged word always shows the CORRECT text, so a wrong word is displayed as the right one in red.
     */
    fun shown(hidden: Boolean, onlySpoken: Boolean, status: WordStatus?, revealed: Boolean, isCurrent: Boolean): Shown = when {
        !hidden -> Shown.CORRECT_WORD
        revealed -> Shown.CORRECT_WORD
        // A word not said yet carries the status UNCERTAIN (yellow, no observed word). It must stay hidden, otherwise the whole text appears after the first word.
        onlySpoken && status != null && status != WordStatus.UNCERTAIN -> Shown.CORRECT_WORD
        isCurrent -> Shown.CURRENT_SPOKEN
        onlySpoken -> Shown.HIDDEN
        else -> Shown.DOT
    }

    fun onlySpoken(context: Context): Boolean =
        context.applicationContext.getSharedPreferences("quran_display", Context.MODE_PRIVATE).getBoolean("only_spoken_when_hidden", true)
    fun setOnlySpoken(context: Context, v: Boolean) {
        context.applicationContext.getSharedPreferences("quran_display", Context.MODE_PRIVATE).edit().putBoolean("only_spoken_when_hidden", v).apply()
    }
}
