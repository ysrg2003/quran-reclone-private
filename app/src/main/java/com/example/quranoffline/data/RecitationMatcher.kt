package com.example.quranoffline.data

import java.text.Normalizer

private val arabicMarks = Regex("[\\u0610-\\u061a\\u064b-\\u065f\\u0670\\u06d6-\\u06ed]")

fun normalizeArabicForMatch(value: String): String {
    return Normalizer.normalize(value, Normalizer.Form.NFC)
        .replace(arabicMarks, "")
        .replace('ٱ', 'ا')
        .replace('ٰ', 'ا')
        .replace(Regex("\\s+"), " ")
        .trim()
}

enum class WordStatus { MATCHED, MISSING, EXTRA_OR_UNEXPECTED, UNCERTAIN }

data class WordEvaluation(
    val expected: String,
    val observed: String?,
    val status: WordStatus,
)

data class RecitationEvaluation(
    val words: List<WordEvaluation>,
    val matched: Int,
    val uncertain: Int,
    val missing: Int,
    val unexpected: Int,
) {
    val total: Int get() = words.size
}

/**
 * Conservative closed-text matcher. It never invents a missing word when the
 * recognizer has not emitted enough tokens, so a partial/low-confidence stream
 * is shown as uncertain by the UI.
 */
object RecitationMatcher {
    fun evaluate(expected: List<QuranWord>, observedText: String, hasFinalResult: Boolean = false): RecitationEvaluation {
        val observed = normalizeArabicForMatch(observedText)
            .split(' ')
            .filter(String::isNotBlank)
        val evaluations = ArrayList<WordEvaluation>(expected.size)
        var matched = 0
        var uncertain = 0
        var missing = 0
        var unexpected = 0

        expected.forEachIndexed { index, word ->
            val observedWord = observed.getOrNull(index)
            val expectedNormalized = normalizeArabicForMatch(word.matchText)
            val observedNormalized = observedWord?.let(::normalizeArabicForMatch)
            when {
                observedWord == null && !hasFinalResult -> {
                    evaluations += WordEvaluation(word.text, null, WordStatus.UNCERTAIN)
                    uncertain++
                }
                observedWord == null -> {
                    evaluations += WordEvaluation(word.text, null, WordStatus.MISSING)
                    missing++
                }
                observedNormalized == expectedNormalized -> {
                    evaluations += WordEvaluation(word.text, observedWord, WordStatus.MATCHED)
                    matched++
                }
                else -> {
                    evaluations += WordEvaluation(word.text, observedWord, WordStatus.EXTRA_OR_UNEXPECTED)
                    unexpected++
                }
            }
        }

        if (observed.size > expected.size) unexpected += observed.size - expected.size
        return RecitationEvaluation(evaluations, matched, uncertain, missing, unexpected)
    }
}
