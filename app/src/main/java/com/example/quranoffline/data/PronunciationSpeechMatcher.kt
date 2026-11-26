package com.example.quranoffline.data

/** Speech-only matching against the pronunciation reference asset. */
object PronunciationSpeechMatcher {
    /**
     * Matches the raw recognizer token against the speech-only reference.
     *
     * Typed recitation may accept both the canonical Quran/provider spelling
     * and the pronunciation form. Voice matching remains explicit about the
     * spoken reference unless the typed-mode flag is enabled.
     */
    fun matches(
        word: QuranWord,
        pronunciation: PronunciationWord?,
        observed: String,
        allowCanonicalQuranSpelling: Boolean = false,
    ): Boolean {
        // Disjoined letters are judged only by their spoken letter names (never the merged spelling).
        if (Muqattaat.pronunciationFor(word) != null) return Muqattaat.matches(word, observed)
        val reference = pronunciation ?: return false
        // Typed recitation may use either the canonical Quran spelling or the
        // spoken spelling. Voice mode keeps the stricter pronunciation contract
        // unless the caller explicitly opts into canonical spelling.
        if (allowCanonicalQuranSpelling && quranWordMatchesAllowCanonicalSpelling(word, observed)) return true
        // Speech-to-text writes words in ordinary spelling (e.g. كفروا); that is correct.
        if (matchesWrittenSpelling(word, observed)) return true
        // Disjoined letters (الم, حم...): letter names, single letters and madd-cut readings.
        if (Muqattaat.matches(word, observed)) return true
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
            if (target in "اويى" && expectedIndex - 1 in maddPositions) {
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
        visualWords: List<QuranWord> = emptyList(),
        allowCanonicalQuranSpelling: Boolean = false,
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
            val actualRaw = flattenedObserved[index]
            val actual = normalizeArabicForComparison(actualRaw)
            val visualWord = visualWords.firstOrNull { it.index == word.index }
            val canonicalOrSpoken = allowCanonicalQuranSpelling && visualWord != null && word.spokenTokens.size == 1 && quranWordMatchesAllowCanonicalSpelling(visualWord, actualRaw)
            canonicalOrSpoken ||
                (visualWord != null && word.spokenTokens.size <= 1 && matchesWrittenSpelling(visualWord, actualRaw)) ||
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
            if (target in "اويى") {
                while (observedIndex < observed.length && observed[observedIndex] == target) observedIndex++
            }
        }
        return if (observedIndex == observed.length && expectedIndex == expected.length) output.toString() else observed
    }
}
