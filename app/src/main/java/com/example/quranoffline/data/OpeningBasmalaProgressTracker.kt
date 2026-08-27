package com.example.quranoffline.data

/**
 * Progress state for the optional opening basmala.
 *
 * The basmala is evaluated against the Word-derived 1:1 words, but it is not
 * inserted into the Quran ayah list. A one-item RecitationProgressTracker is
 * used here so the same ordered matching, correction barrier, pause handling,
 * and madd rules apply without allowing the next Quran ayah to advance.
 */
class OpeningBasmalaProgressTracker(
    private val actualSurah: Int,
    sourceAyah: QuranAyah,
    pronunciationReference: PronunciationReference? = null,
    correctionSettings: CorrectionSettings = CorrectionSettings.DEFAULTS,
) {
    private val delegate = RecitationProgressTracker(
        ayahs = listOf(sourceAyah),
        pronunciationReference = pronunciationReference,
        correctionSettings = correctionSettings,
    )

    fun updateSettings(newSettings: CorrectionSettings) = delegate.updateSettings(newSettings)

    fun reset() = delegate.reset()

    /** Resume from the first unresolved basmala word after the correction clip. */
    fun resumeFrom(localWordIndex: Int) = delegate.resumeFrom(ayahIndex = 0, localWordIndex = localWordIndex)

    fun update(input: RecitationProgressInput): RecitationProgressResult {
        val result = delegate.update(input)
        return result.copy(
            correctedErrors = result.correctedErrors.map { error ->
                // Keep the real target surah in the history. The source words
                // come from 1:1 only because that is the canonical basmala row.
                error.copy(surah = actualSurah, ayah = 0)
            },
        )
    }
}
