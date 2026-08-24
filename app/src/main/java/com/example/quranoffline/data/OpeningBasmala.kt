package com.example.quranoffline.data

/** A presentation/recitation item, never a QuranAyah and never a progress word. */
data class OpeningBasmala(
    val surah: Int,
    val text: String = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
    val matchText: String = "بسم الله الرحمن الرحيم",
    val countAsAyah: Boolean = false,
)

data class OpeningBasmalaSpeechResult(
    val ayahTranscript: String,
    val consumed: Boolean,
    val needsCorrection: Boolean,
)

/**
 * Keeps an optional opening basmala outside QuranAyah. It is deliberately
 * conservative: a partial "ب"/"بس" remains outside the ayah, a recognized
 * four-word opening is removed, and an incomplete/wrong opening is consumed
 * without poisoning the first Quran word.
 */
object OpeningBasmalaSpeechFilter {
    private const val FIRST_WORD = "بسم"
    private val canonicalWords = listOf("بسم", "الله", "الرحمن", "الرحيم")

    fun filter(transcript: String, isFinal: Boolean, segmentCompleted: Boolean, alreadyConsumed: Boolean): OpeningBasmalaSpeechResult {
        if (alreadyConsumed) return OpeningBasmalaSpeechResult(transcript, consumed = true, needsCorrection = false)
        val tokens = normalizeArabicForComparison(transcript).split(' ').filter(String::isNotBlank)
        if (tokens.isEmpty()) return OpeningBasmalaSpeechResult("", consumed = false, needsCorrection = false)
        val first = tokens.first()
        if (FIRST_WORD.startsWith(first)) {
            if (tokens.size == 1 && first != FIRST_WORD) return OpeningBasmalaSpeechResult("", consumed = false, needsCorrection = false)
            if (tokens.size < canonicalWords.size && !(isFinal || segmentCompleted)) {
                return OpeningBasmalaSpeechResult("", consumed = false, needsCorrection = false)
            }
        } else {
            return OpeningBasmalaSpeechResult(transcript, consumed = true, needsCorrection = false)
        }
        val openingWords = tokens.take(canonicalWords.size)
        val correct = openingWords == canonicalWords
        val rest = tokens.drop(canonicalWords.size).joinToString(" ")
        val complete = tokens.size >= canonicalWords.size || isFinal || segmentCompleted
        return if (complete) {
            OpeningBasmalaSpeechResult(rest, consumed = true, needsCorrection = !correct)
        } else {
            OpeningBasmalaSpeechResult("", consumed = false, needsCorrection = false)
        }
    }
}

object QuranPresentation {
    /**
     * The opening basmala is shown before every surah except al-Fatiha (where
     * it is ayah 1:1) and al-Tawbah (which has no opening basmala).
     */
    fun openingBasmalaForSurah(surah: Int): OpeningBasmala? =
        if (surah in 2..114 && surah != 9) OpeningBasmala(surah) else null
}
