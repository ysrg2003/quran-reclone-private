package com.example.quranoffline.data

/** A presentation/recitation item, never a QuranAyah and never a progress word. */
data class OpeningBasmala(
    val surah: Int,
    val text: String = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ",
    val matchText: String = "بسم الله الرحمان الرحيم",
    val countAsAyah: Boolean = false,
)

data class OpeningBasmalaSpeechResult(
    val ayahTranscript: String,
    val consumed: Boolean,
    val needsCorrection: Boolean,
    val detectedOpeningBasmala: Boolean = false,
)

/**
 * Recognizes one optional opening-basmala sentence independently from the
 * Quran ayah stream. The input to this filter is the recognizer's current
 * sentence only; the Quran first ayah is sent in a later recognizer segment.
 * A direct first-ayah start is explicitly allowed and is never treated as a
 * malformed basmala.
 */
object OpeningBasmalaSpeechFilter {
    private const val FIRST_WORD = "بسم"
    private val canonicalWords = listOf("بسم", "الله", "الرحمان", "الرحيم")

    fun filter(transcript: String, isFinal: Boolean, segmentCompleted: Boolean, alreadyConsumed: Boolean): OpeningBasmalaSpeechResult {
        if (alreadyConsumed) return OpeningBasmalaSpeechResult(transcript, consumed = true, needsCorrection = false)
        val tokens = normalizeArabicForComparison(transcript).split(' ').filter(String::isNotBlank)
        if (tokens.isEmpty()) return OpeningBasmalaSpeechResult("", consumed = false, needsCorrection = false)
        val first = tokens.first()
        if (!FIRST_WORD.startsWith(first)) {
            // The reader started directly with the actual first Quran ayah.
            return OpeningBasmalaSpeechResult(transcript, consumed = true, needsCorrection = false)
        }
        if (tokens.size == 1 && first != FIRST_WORD) {
            // Keep a partial "ب"/"بس" outside the ayah until the sentence is
            // either completed or finalized as an incorrect basmala.
            return OpeningBasmalaSpeechResult("", consumed = false, needsCorrection = false, detectedOpeningBasmala = true)
        }
        if (!(isFinal || segmentCompleted)) {
            // Never start the Quran stream from a partial basmala callback;
            // wait for the recognizer to close this standalone sentence.
            return OpeningBasmalaSpeechResult("", consumed = false, needsCorrection = false, detectedOpeningBasmala = true)
        }

        // The basmala remains an independent progress item. If the provider
        // appends first-ayah words in the same callback, preserve only those
        // trailing words for the Quran tracker after the basmala decision.
        val openingWords = tokens.take(canonicalWords.size)
        val correct = openingWords == canonicalWords
        val complete = isFinal || segmentCompleted
        return if (complete) {
            // The basmala is closed as a separate item; any trailing words
            // remain available for the Quran tracker in this same callback.
            OpeningBasmalaSpeechResult(
                ayahTranscript = tokens.drop(canonicalWords.size).joinToString(" "),
                consumed = true,
                needsCorrection = !correct,
                detectedOpeningBasmala = true,
            )
        } else {
            OpeningBasmalaSpeechResult("", consumed = false, needsCorrection = false, detectedOpeningBasmala = true)
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
