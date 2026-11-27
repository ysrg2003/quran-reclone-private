package com.example.quranoffline.data

/**
 * User-controlled policy for automatic reference-audio corrections.
 *
 * The defaults deliberately preserve the current behavior: a word clip for one
 * or two consecutive issues, then the first wrong-word clip followed by the
 * whole ayah for three or more issues in the same ayah.
 */
data class CorrectionSettings(
    val automaticCorrectionEnabled: Boolean = DEFAULT_AUTOMATIC_CORRECTION_ENABLED,
    val correctWrongWords: Boolean = DEFAULT_CORRECT_WRONG_WORDS,
    val correctSkippedWords: Boolean = DEFAULT_CORRECT_SKIPPED_WORDS,
    val correctAyahJumps: Boolean = DEFAULT_CORRECT_AYAH_JUMPS,
    val automaticPreviousAyah: Boolean = DEFAULT_AUTOMATIC_PREVIOUS_AYAH,
    val listenForOpeningBasmala: Boolean = DEFAULT_LISTEN_FOR_OPENING_BASMALA,
    val wholeAyahAfterConsecutiveErrors: Int = DEFAULT_WHOLE_AYAH_AFTER_CONSECUTIVE_ERRORS,
    val playFirstErrorBeforeWholeAyah: Boolean = DEFAULT_PLAY_FIRST_ERROR_BEFORE_WHOLE_AYAH,
    val wordRepeatCount: Int = DEFAULT_WORD_REPEAT_COUNT,
    val wholeAyahRepeatCount: Int = DEFAULT_WHOLE_AYAH_REPEAT_COUNT,
    val pauseBetweenPartsMs: Int = DEFAULT_PAUSE_BETWEEN_PARTS_MS,
    val clipPaddingBeforeMs: Int = DEFAULT_CLIP_PADDING_BEFORE_MS,
    val clipPaddingAfterMs: Int = DEFAULT_CLIP_PADDING_AFTER_MS,
    val resumePosition: ResumePosition = DEFAULT_RESUME_POSITION,
    val mushafTextScale: Float = DEFAULT_MUSHAF_TEXT_SCALE,
    /** Diameter in dp of the small circle shown at the start of a selected block. */
    val selectionCircleDp: Int = DEFAULT_SELECTION_CIRCLE_DP,
) {
    fun normalized(): CorrectionSettings {
        val safePause = PAUSE_OPTIONS_MS.minByOrNull { kotlin.math.abs(it - pauseBetweenPartsMs) }
            ?: DEFAULT_PAUSE_BETWEEN_PARTS_MS
        val safeResumePosition = if (resumePosition == ResumePosition.WORD_AFTER_ERROR) {
            // The first unresolved error is a hard barrier. Keep the legacy
            // enum for persisted settings, but never resume after that word.
            ResumePosition.FIRST_ERROR
        } else resumePosition
        return copy(
            wholeAyahAfterConsecutiveErrors = wholeAyahAfterConsecutiveErrors.coerceIn(MIN_ERROR_THRESHOLD, MAX_ERROR_THRESHOLD),
            wordRepeatCount = wordRepeatCount.coerceIn(MIN_REPEAT_COUNT, MAX_REPEAT_COUNT),
            wholeAyahRepeatCount = wholeAyahRepeatCount.coerceIn(MIN_REPEAT_COUNT, MAX_REPEAT_COUNT),
            pauseBetweenPartsMs = safePause,
            clipPaddingBeforeMs = clipPaddingBeforeMs.coerceIn(MIN_PADDING_MS, MAX_PADDING_MS),
            clipPaddingAfterMs = clipPaddingAfterMs.coerceIn(MIN_PADDING_MS, MAX_PADDING_MS),
            mushafTextScale = mushafTextScale.coerceIn(MIN_MUSHAF_TEXT_SCALE, MAX_MUSHAF_TEXT_SCALE),
            selectionCircleDp = selectionCircleDp.coerceIn(MIN_SELECTION_CIRCLE_DP, MAX_SELECTION_CIRCLE_DP),
            resumePosition = safeResumePosition,
        )
    }

    companion object {
        const val DEFAULT_WHOLE_AYAH_AFTER_CONSECUTIVE_ERRORS = 3
        const val MIN_ERROR_THRESHOLD = 1
        const val MAX_ERROR_THRESHOLD = 6
        const val DEFAULT_WORD_REPEAT_COUNT = 1
        const val DEFAULT_WHOLE_AYAH_REPEAT_COUNT = 1
        const val MIN_REPEAT_COUNT = 1
        const val MAX_REPEAT_COUNT = 3
        const val DEFAULT_PAUSE_BETWEEN_PARTS_MS = 0
        const val DEFAULT_CLIP_PADDING_BEFORE_MS = 0
        const val DEFAULT_CLIP_PADDING_AFTER_MS = 0
        const val MIN_PADDING_MS = 0
        const val MAX_PADDING_MS = 300
        const val DEFAULT_MUSHAF_TEXT_SCALE = 1.0f
        const val MIN_MUSHAF_TEXT_SCALE = 0.8f
        const val MAX_MUSHAF_TEXT_SCALE = 1.5f
        const val DEFAULT_SELECTION_CIRCLE_DP = 18
        const val MIN_SELECTION_CIRCLE_DP = 10
        const val MAX_SELECTION_CIRCLE_DP = 40

        val PAUSE_OPTIONS_MS = listOf(0, 250, 500, 1_000)

        val DEFAULTS = CorrectionSettings()
    }
}

enum class ResumePosition {
    AYAH_START,
    FIRST_ERROR,
    WORD_AFTER_ERROR,
}

private const val DEFAULT_AUTOMATIC_CORRECTION_ENABLED = true
private const val DEFAULT_CORRECT_WRONG_WORDS = true
private const val DEFAULT_CORRECT_SKIPPED_WORDS = true
private const val DEFAULT_CORRECT_AYAH_JUMPS = true
private const val DEFAULT_AUTOMATIC_PREVIOUS_AYAH = true
private const val DEFAULT_LISTEN_FOR_OPENING_BASMALA = true
private const val DEFAULT_PLAY_FIRST_ERROR_BEFORE_WHOLE_AYAH = true
private val DEFAULT_RESUME_POSITION = ResumePosition.FIRST_ERROR

fun CorrectionSettings.displayResumePosition(): String = when (resumePosition) {
    ResumePosition.AYAH_START -> "من أول الآية"
    ResumePosition.FIRST_ERROR -> "من أول خطأ"
    ResumePosition.WORD_AFTER_ERROR -> "من أول خطأ"
}

fun CorrectionSettings.displayPause(): String = when (pauseBetweenPartsMs) {
    0 -> "فوري"
    else -> "${pauseBetweenPartsMs} مللي ثانية"
}
