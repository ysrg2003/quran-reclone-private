package com.example.quranoffline.data

/** Speech-only matching against the pronunciation reference asset. */
object PronunciationSpeechMatcher {
    /**
     * Matches one recognized token against the reference pronunciation. The
     * canonical Quran matcher remains the first fallback; the audio-derived
     * spelling is an additional comparison layer only.
     */
    fun matches(word: QuranWord, pronunciation: PronunciationWord?, observed: String): Boolean {
        if (quranWordMatches(word, observed)) return true
        val reference = pronunciation ?: return false
        val expected = normalizeArabicForComparison(reference.spoken)
        val actual = normalizeArabicForComparison(observed)
        if (expected == actual) return true
        if (reference.spokenTokens.size > 1 && expected.split(' ').filter(String::isNotBlank) == actual.split(' ').filter(String::isNotBlank)) return true
        return reference.maddPositions.isNotEmpty() && collapseValidMaddRepeats(actual, expected, reference.maddPositions) == expected
    }

    /**
     * Collapse only repeated alif/waw/ya at positions known to be long-vowel
     * carriers in the reference word. Repeated consonants are never collapsed.
     */
    fun collapseValidMaddRepeats(observed: String, expected: String, maddPositions: Set<Int>): String {
        if (observed.isBlank() || expected.isBlank() || maddPositions.isEmpty()) return observed
        val output = StringBuilder()
        var expectedIndex = 0
        var observedIndex = 0
        while (observedIndex < observed.length && expectedIndex < expected.length) {
            val actual = observed[observedIndex]
            val target = expected[expectedIndex]
            if (actual != target) return observed
            output.append(target)
            observedIndex++
            expectedIndex++
            if (target in "اوي" && expectedIndex - 1 in maddPositions) {
                while (observedIndex < observed.length && observed[observedIndex] == target) observedIndex++
            }
        }
        return if (observedIndex == observed.length && expectedIndex == expected.length) output.toString() else observed
    }

    /**
     * Scores a complete recognized phrase against the reference ayah in order.
     * This is used as context evidence when a provider emits ambiguous word
     * spellings; it does not silently advance a locked ayah by itself.
     */
    fun matchesAyahPhrase(
        reference: PronunciationAyah,
        observedTokens: List<String>,
    ): Boolean {
        val expectedWords = reference.words
        if (expectedWords.isEmpty() || observedTokens.isEmpty()) return false
        val flattenedObserved = observedTokens.flatMap { it.split(' ').filter(String::isNotBlank) }
        val expectedUnits = expectedWords.flatMap { word ->
            word.spokenTokens.ifEmpty { listOf(word.spoken) }.map { unit -> word to unit }
        }
        if (flattenedObserved.size != expectedUnits.size) return false
        return expectedUnits.indices.all { index ->
            val (word, expectedUnit) = expectedUnits[index]
            val expected = normalizeArabicForComparison(expectedUnit)
            val actual = normalizeArabicForComparison(flattenedObserved[index])
            expected == actual ||
                (word.spokenTokens.size > 1 && collapseLongVowelRepeats(actual, expected) == expected) ||
                (word.maddPositions.isNotEmpty() && collapseValidMaddRepeats(actual, expected, word.maddPositions) == expected)
        }
    }

    private fun collapseLongVowelRepeats(observed: String, expected: String): String {
        if (observed.isBlank() || expected.isBlank()) return observed
        val output = StringBuilder()
        var expectedIndex = 0
        var observedIndex = 0
        while (observedIndex < observed.length && expectedIndex < expected.length) {
            val actual = observed[observedIndex]
            val target = expected[expectedIndex]
            if (actual != target) return observed
            output.append(target)
            observedIndex++
            expectedIndex++
            if (target in "اوي") {
                while (observedIndex < observed.length && observed[observedIndex] == target) observedIndex++
            }
        }
        return if (observedIndex == observed.length && expectedIndex == expected.length) output.toString() else observed
    }
}
