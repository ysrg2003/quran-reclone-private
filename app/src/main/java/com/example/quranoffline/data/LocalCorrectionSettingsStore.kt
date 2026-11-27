package com.example.quranoffline.data

import android.content.Context

/** Stores only correction preferences on the device; Quran text and audio are not stored here. */
class LocalCorrectionSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("quran_correction_settings", Context.MODE_PRIVATE)

    fun current(): CorrectionSettings = CorrectionSettings(
        automaticCorrectionEnabled = preferences.getBoolean("automaticCorrectionEnabled", CorrectionSettings.DEFAULTS.automaticCorrectionEnabled),
        correctWrongWords = preferences.getBoolean("correctWrongWords", CorrectionSettings.DEFAULTS.correctWrongWords),
        correctSkippedWords = preferences.getBoolean("correctSkippedWords", CorrectionSettings.DEFAULTS.correctSkippedWords),
        correctAyahJumps = preferences.getBoolean("correctAyahJumps", CorrectionSettings.DEFAULTS.correctAyahJumps),
        automaticPreviousAyah = preferences.getBoolean("automaticPreviousAyah", CorrectionSettings.DEFAULTS.automaticPreviousAyah),
        listenForOpeningBasmala = preferences.getBoolean("listenForOpeningBasmala", CorrectionSettings.DEFAULTS.listenForOpeningBasmala),
        wholeAyahAfterConsecutiveErrors = preferences.getInt(
            "wholeAyahAfterConsecutiveErrors",
            CorrectionSettings.DEFAULTS.wholeAyahAfterConsecutiveErrors,
        ),
        playFirstErrorBeforeWholeAyah = preferences.getBoolean(
            "playFirstErrorBeforeWholeAyah",
            CorrectionSettings.DEFAULTS.playFirstErrorBeforeWholeAyah,
        ),
        wordRepeatCount = preferences.getInt("wordRepeatCount", CorrectionSettings.DEFAULTS.wordRepeatCount),
        wholeAyahRepeatCount = preferences.getInt("wholeAyahRepeatCount", CorrectionSettings.DEFAULTS.wholeAyahRepeatCount),
        pauseBetweenPartsMs = preferences.getInt("pauseBetweenPartsMs", CorrectionSettings.DEFAULTS.pauseBetweenPartsMs),
        clipPaddingBeforeMs = preferences.getInt("clipPaddingBeforeMs", CorrectionSettings.DEFAULTS.clipPaddingBeforeMs),
        clipPaddingAfterMs = preferences.getInt("clipPaddingAfterMs", CorrectionSettings.DEFAULTS.clipPaddingAfterMs),
        resumePosition = runCatching {
            ResumePosition.valueOf(preferences.getString("resumePosition", CorrectionSettings.DEFAULTS.resumePosition.name).orEmpty())
        }.getOrDefault(CorrectionSettings.DEFAULTS.resumePosition),
        mushafTextScale = preferences.getFloat("mushafTextScale", CorrectionSettings.DEFAULTS.mushafTextScale),
        selectionCircleDp = preferences.getInt("actionCircleDp", CorrectionSettings.DEFAULTS.selectionCircleDp),
    ).normalized()

    fun save(settings: CorrectionSettings) {
        val normalized = settings.normalized()
        preferences.edit()
            .putBoolean("automaticCorrectionEnabled", normalized.automaticCorrectionEnabled)
            .putBoolean("correctWrongWords", normalized.correctWrongWords)
            .putBoolean("correctSkippedWords", normalized.correctSkippedWords)
            .putBoolean("correctAyahJumps", normalized.correctAyahJumps)
            .putBoolean("automaticPreviousAyah", normalized.automaticPreviousAyah)
            .putBoolean("listenForOpeningBasmala", normalized.listenForOpeningBasmala)
            .putInt("wholeAyahAfterConsecutiveErrors", normalized.wholeAyahAfterConsecutiveErrors)
            .putBoolean("playFirstErrorBeforeWholeAyah", normalized.playFirstErrorBeforeWholeAyah)
            .putInt("wordRepeatCount", normalized.wordRepeatCount)
            .putInt("wholeAyahRepeatCount", normalized.wholeAyahRepeatCount)
            .putInt("pauseBetweenPartsMs", normalized.pauseBetweenPartsMs)
            .putInt("clipPaddingBeforeMs", normalized.clipPaddingBeforeMs)
            .putInt("clipPaddingAfterMs", normalized.clipPaddingAfterMs)
            .putString("resumePosition", normalized.resumePosition.name)
            .putFloat("mushafTextScale", normalized.mushafTextScale)
            .putInt("actionCircleDp", normalized.selectionCircleDp)
            .apply()
    }

    fun reset(): CorrectionSettings {
        preferences.edit().clear().apply()
        return CorrectionSettings.DEFAULTS
    }
}
