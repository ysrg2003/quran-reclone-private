package com.example.quranoffline.data

/** A presentation/recitation item, never a QuranAyah and never a progress word. */
data class OpeningBasmala(
    val surah: Int,
    val text: String = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ",
    val matchText: String = "بسم الله الرحمان الرحيم",
    val countAsAyah: Boolean = false,
)

data class OpeningBasmalaSpeechResult(
    /** Only text that is safe to pass to the Quran ayah tracker. */
    val ayahTranscript: String,
    val consumed: Boolean,
    val needsCorrection: Boolean,
    val detectedOpeningBasmala: Boolean = false,
    /** Raw recognizer candidate reserved for the standalone basmala tracker. */
    val basmalaTranscript: String = "",
)

/**
 * Routes one recognizer candidate between the standalone opening basmala and
 * the actual Quran ayah stream. Routing is deliberately conservative: until
 * the four opening words are recognized as a complete opening prefix, no
 * trailing token is allowed to enter the ayah tracker.
 *
 * This object only routes observed recognizer text. It does not canonicalize
 * the observed value for display or use Quran display text as a speech result;
 * word judgement belongs to OpeningBasmalaProgressTracker.
 */
object OpeningBasmalaSpeechFilter {
    private const val FIRST_WORD = "بسم"
    private val canonicalWords = listOf("بسم", "الله", "الرحمان", "الرحيم")
    private val acceptedRahmanRoutingForms = setOf("الرحمان", "الرحمن")

    fun filter(
        transcript: String,
        isFinal: Boolean,
        segmentCompleted: Boolean,
        alreadyConsumed: Boolean,
        continuation: Boolean = false,
        directFirstAyah: Boolean = false,
    ): OpeningBasmalaSpeechResult {
        val raw = transcript.trim()
        if (alreadyConsumed || directFirstAyah) {
            return OpeningBasmalaSpeechResult(
                ayahTranscript = raw,
                consumed = true,
                needsCorrection = false,
            )
        }

        val rawTokens = raw.split(Regex("\\s+")).filter(String::isNotBlank)
        val tokens = normalizeArabicForComparison(raw).split(' ').filter(String::isNotBlank)
        if (tokens.isEmpty()) {
            return OpeningBasmalaSpeechResult("", consumed = false, needsCorrection = false)
        }

        // After a word correction, the new recognizer segment belongs wholly
        // to the standalone basmala. Never reinterpret its tail as an ayah.
        if (continuation) {
            return OpeningBasmalaSpeechResult(
                ayahTranscript = "",
                consumed = false,
                needsCorrection = false,
                detectedOpeningBasmala = true,
                basmalaTranscript = raw,
            )
        }

        val first = tokens.first()
        if (!FIRST_WORD.startsWith(first)) {
            // The reader started directly with the actual first Quran ayah.
            return OpeningBasmalaSpeechResult(
                ayahTranscript = raw,
                consumed = true,
                needsCorrection = false,
            )
        }

        val stable = isFinal || segmentCompleted
        if (!stable) {
            return OpeningBasmalaSpeechResult(
                ayahTranscript = "",
                consumed = false,
                needsCorrection = false,
                detectedOpeningBasmala = true,
                basmalaTranscript = raw,
            )
        }

        // A valid split is granted only for a complete, recognizable four-word
        // prefix. A candidate such as "بسم الله الرحيم الرحيم" therefore
        // remains entirely in the basmala tracker; the missing الرحمن cannot
        // be hidden by passing the final الرحيم to the ayah tracker.
        val completeOpeningPrefix = tokens.size >= canonicalWords.size &&
            tokens[0] == canonicalWords[0] &&
            tokens[1] == canonicalWords[1] &&
            tokens[2] in acceptedRahmanRoutingForms &&
            tokens[3] == canonicalWords[3]

        return if (completeOpeningPrefix) {
            OpeningBasmalaSpeechResult(
                ayahTranscript = rawTokens.drop(canonicalWords.size).joinToString(" "),
                consumed = true,
                needsCorrection = false,
                detectedOpeningBasmala = true,
                basmalaTranscript = rawTokens.take(canonicalWords.size).joinToString(" "),
            )
        } else {
            OpeningBasmalaSpeechResult(
                ayahTranscript = "",
                consumed = false,
                needsCorrection = tokens.size >= canonicalWords.size,
                detectedOpeningBasmala = true,
                basmalaTranscript = raw,
            )
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
