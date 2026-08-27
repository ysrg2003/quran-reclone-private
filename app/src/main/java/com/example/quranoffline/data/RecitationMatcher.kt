package com.example.quranoffline.data

import java.text.Normalizer

private val arabicMarks = Regex("[\\u0610-\\u061a\\u064b-\\u065f\\u06d6-\\u06ed]")
private val smallAlif = 'ٰ'
private val silentAlifMarker = '۟'

/**
 * Removes the base letter that immediately carries the small round zero.
 * It is deliberately not an "all final alifs are silent" rule: in
 * أُو۟لَٰٓئِكَ only the marked waw is removed and the later written alif stays.
 */
private fun removeSilentMarkedLetters(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFC)
        // Word uses the small high dotless head of khah (U+06E1) for sukun;
        // the legacy audio source may use the round-zero marker (U+06DF).
        .replace(Regex("\\p{L}$silentAlifMarker"), "")
        .replace(Regex("وْ(?=ل)"), "")
        .replace(Regex("اْ$"), "")
        .replace(silentAlifMarker.toString(), "")

fun normalizeArabicForMatch(value: String): String {
    return removeSilentMarkedLetters(value)
        // Uthmani rasm writes the pronounced alif in words such as
        // ٱلصَّلَوٰةَ and ٱلزَّكَوٰةَ as a superscript alif over waw.
        // Expand that sequence before stripping marks; this is comparison-only
        // and never mutates the canonical displayed Quran text.
        .replace("و$smallAlif", "ا")
        .replace(arabicMarks, "")
        .replace(smallAlif.toString(), "")
        .replace('ٱ', 'ا')
        .replace('آ', 'ا')
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun normalizeArabicWithSmallAlif(value: String): String {
    return removeSilentMarkedLetters(value)
        .replace("و$smallAlif", "ا")
        .replace("ى$smallAlif", "ى")
        .replace(arabicMarks, "")
        .replace(smallAlif, 'ا')
        .replace('ٱ', 'ا')
        .replace('آ', 'ا')
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('ؤ', 'و')
        .replace('ئ', 'ي')
        .replace("ء", "")
        .replace(Regex("\\s+"), " ")
        .trim()
}

/** Comparison-only spelling normalization; it never changes displayed Quran text. */
fun normalizeArabicForComparison(value: String): String = normalizeArabicForMatch(value)
    .replace('أ', 'ا')
    .replace('إ', 'ا')
    .replace('ؤ', 'و')
    .replace('ئ', 'ي')
    .replace("ء", "")

/**
 * Treats a written dagger alif as a required pronounced carrier. The
 * comparison can therefore accept the spoken alif in "مالك" and "العالمين",
 * but never makes every ordinary alif optional.
 */
fun arabicWordsEquivalent(first: String, second: String): Boolean {
    val firstForms = arabicSpellingForms(first, allowSpeechTaMarbutaSubstitution = first.contains('ة'))
    val secondForms = arabicSpellingForms(second, allowSpeechTaMarbutaSubstitution = false)
    return firstForms.intersect(secondForms).isNotEmpty()
}

/**
 * Android speech providers sometimes transcribe a final ta marbuta as ه.
 * Keep the Quran/reference spelling untouched and allow that substitution only
 * from a canonical word that actually contains ة; never apply it globally.
 */
fun arabicSpellingForms(value: String, allowSpeechTaMarbutaSubstitution: Boolean): Set<String> {
    val base = normalizeArabicForMatch(value)
    val pronounced = normalizeArabicWithSmallAlif(value)
    // Voice comparison keeps the pronunciation form authoritative for drawn
    // alifs. Typed mode opts into the additional canonical Quran spelling via
    // quranWordMatchesAllowCanonicalSpelling below.
    val baseForms = if (pronounced != base) setOf(pronounced) else setOf(base, pronounced)
    return if (allowSpeechTaMarbutaSubstitution) {
        baseForms + baseForms.map { it.replace('ة', 'ه') }
    } else {
        baseForms
    }
}

fun quranWordMatchesAllowCanonicalSpelling(word: QuranWord, observed: String): Boolean {
    if (quranWordMatches(word, observed)) return true
    val canonical = normalizeArabicForMatch(word.text)
    val actual = normalizeArabicForComparison(observed)
    return canonical.isNotBlank() && canonical == actual
}

fun quranWordMatches(word: QuranWord, observed: String): Boolean {
    val markerIndex = word.text.indexOf(silentAlifMarker)
    val hasSilentMarker = markerIndex >= 0 || word.text.contains("وْل") || word.text.endsWith("اْ")
    val canonicalMatch = arabicWordsEquivalent(word.text, observed)
    // SpeechRecognizer may write the carrier that is marked silent back into
    // Arabic text. The provider spelling is never accepted for a final alif
    // carrying the round-zero marker: كفروا/لقوا/اشتروا must be spoken as
    // كفرو/لقو/اشترو. A written-and-pronounced alif remains required.
    val canonicalSpoken = normalizeArabicWithSmallAlif(word.text)
    val alternateSpelling = arabicWordsEquivalent(word.matchText, observed)
    val hasSilentFinalAlif = word.text.endsWith("ا$silentAlifMarker") || word.text.endsWith("اْ")
    // A provider spelling may preserve a silent non-alif carrier such as the
    // marked waw in Ulaika, but it may not re-introduce a final alif carrying
    // the round-zero marker. A carrier that is written and pronounced remains
    // required in the comparison layer.
    val providerSpelling = hasSilentMarker && !hasSilentFinalAlif && alternateSpelling
    val alternateKeepsPronouncedAlif = normalizeArabicWithSmallAlif(word.matchText) == canonicalSpoken
    val alternateAllowed = alternateSpelling && (canonicalSpoken == normalizeArabicForMatch(word.text) || alternateKeepsPronouncedAlif)
    return if (hasSilentMarker) {
        canonicalMatch || providerSpelling
    } else {
        canonicalMatch || alternateAllowed
    } || Muqattaat.matches(word, observed)
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
 * Converts only successfully matched speech tokens to their canonical Quran
 * spelling for the visible transcript. The raw observed token remains in the
 * evaluation/error log; this function never performs a global ة/ه rewrite.
 */
fun canonicalizeRecognizedTranscript(raw: String, evaluation: List<WordEvaluation>): String {
    val tokens = raw.trim().split(Regex("\\s+")).filter(String::isNotBlank).toMutableList()
    if (tokens.isEmpty()) return ""
    var searchFrom = 0
    evaluation.forEach { word ->
        val observed = word.observed ?: return@forEach
        if (word.status != WordStatus.MATCHED) return@forEach
        val matchIndex = tokens.indices.firstOrNull { index ->
            index >= searchFrom && arabicWordsEquivalent(word.expected, tokens[index])
        } ?: return@forEach
        tokens[matchIndex] = word.expected
        searchFrom = matchIndex + 1
    }
    return tokens.joinToString(" ")
}

/** The visible current word uses the Quran spelling once the token matched. */
fun canonicalMatchedWord(expected: QuranWord?, observed: String?): String? =
    if (expected != null && observed != null && quranWordMatches(expected, observed)) expected.text else observed

/**
 * Aligns speech with Quran words. Repeated words are resolved from the
 * current recitation cursor first, not by an unrestricted global search.
 */
object RecitationMatcher {
    private const val DELETE_COST = 1
    private const val INSERT_COST = 1
    private const val SUBSTITUTE_COST = 3

    private enum class AlignmentOp { MATCH, SUBSTITUTE, DELETE, INSERT }

    private data class AlignmentStep(
        val op: AlignmentOp,
        val expectedIndex: Int? = null,
        val observedIndex: Int? = null,
    )

    private data class Candidate(
        val cost: Int,
        val priority: Int,
        val op: AlignmentOp,
    )

    fun evaluate(
        expected: List<QuranWord>,
        observedText: String,
        hasFinalResult: Boolean = false,
        preferredExpectedIndex: Int = 0,
    ): RecitationEvaluation {
        val observed = Muqattaat.coalesceSpeechTokens(
            normalizeArabicForMatch(observedText).split(' ').filter(String::isNotBlank)
        )
        if (expected.isEmpty()) return RecitationEvaluation(emptyList(), 0, 0, 0, observed.size)

        val observedNormalized = observed.map(::normalizeArabicForComparison)
        val preferred = preferredExpectedIndex.coerceIn(0, expected.lastIndex)
        val firstObserved = observedNormalized.firstOrNull()
        val matchingCandidates = if (firstObserved == null) emptyList() else expected.indices.filter { quranWordMatches(expected[it], firstObserved) }
        val anchor = matchingCandidates.firstOrNull { it >= preferred }
            ?: matchingCandidates.firstOrNull()
            ?: preferred

        val prefixSteps = (0 until anchor).map { AlignmentStep(AlignmentOp.DELETE, expectedIndex = it) }
        val suffixSteps = alignGreedy(expected.drop(anchor), observedNormalized).map { step ->
            step.copy(expectedIndex = step.expectedIndex?.plus(anchor))
        }
        val steps = prefixSteps + suffixSteps

        val evaluations = arrayOfNulls<WordEvaluation>(expected.size)
        var matched = 0
        var uncertain = 0
        var missing = 0
        var unexpected = 0

        steps.forEachIndexed { stepIndex, step ->
            when (step.op) {
                AlignmentOp.MATCH -> {
                    val expectedIndex = requireNotNull(step.expectedIndex)
                    val observedIndex = requireNotNull(step.observedIndex)
                    evaluations[expectedIndex] = WordEvaluation(expected[expectedIndex].text, observed[observedIndex], WordStatus.MATCHED)
                    matched++
                }
                AlignmentOp.SUBSTITUTE -> {
                    val expectedIndex = requireNotNull(step.expectedIndex)
                    val observedIndex = requireNotNull(step.observedIndex)
                    evaluations[expectedIndex] = WordEvaluation(expected[expectedIndex].text, observed[observedIndex], WordStatus.EXTRA_OR_UNEXPECTED)
                    unexpected++
                }
                AlignmentOp.DELETE -> {
                    val expectedIndex = requireNotNull(step.expectedIndex)
                    val hasLaterObservedToken = steps.drop(stepIndex + 1).any { it.observedIndex != null }
                    val status = if (!hasFinalResult && !hasLaterObservedToken) WordStatus.UNCERTAIN else WordStatus.MISSING
                    evaluations[expectedIndex] = WordEvaluation(expected[expectedIndex].text, null, status)
                    if (status == WordStatus.UNCERTAIN) uncertain++ else missing++
                }
                AlignmentOp.INSERT -> unexpected++
            }
        }

        expected.forEachIndexed { index, word ->
            if (evaluations[index] == null) {
                val status = if (hasFinalResult) WordStatus.MISSING else WordStatus.UNCERTAIN
                evaluations[index] = WordEvaluation(word.text, null, status)
                if (status == WordStatus.MISSING) missing++ else uncertain++
            }
        }

        return RecitationEvaluation(evaluations.map { requireNotNull(it) }, matched, uncertain, missing, unexpected)
    }

    /** Preserve already-judged words when a recognizer returns a shorter/rebased segment. */
    fun mergePrevious(
        previous: RecitationEvaluation?,
        current: RecitationEvaluation,
        preserveBeforeIndex: Int,
    ): RecitationEvaluation {
        if (previous == null || previous.words.size != current.words.size || preserveBeforeIndex <= 0) return current
        val words = current.words.mapIndexed { index, word ->
            if (index < preserveBeforeIndex) previous.words[index] else word
        }
        return summarize(words, current.unexpected)
    }

    private fun summarize(words: List<WordEvaluation>, unexpected: Int): RecitationEvaluation {
        return RecitationEvaluation(
            words = words,
            matched = words.count { it.status == WordStatus.MATCHED },
            uncertain = words.count { it.status == WordStatus.UNCERTAIN },
            missing = words.count { it.status == WordStatus.MISSING },
            unexpected = unexpected + words.count { it.status == WordStatus.EXTRA_OR_UNEXPECTED },
        )
    }

    /**
     * Walk from the current anchor and prefer the earliest local match. A
     * global edit-distance backtrace can choose a later duplicate when two
     * repeated Quran phrases have the same cost; this local walk cannot skip
     * over an equal current word merely to reach a later duplicate.
     */
    private fun alignGreedy(expected: List<QuranWord>, observed: List<String>): List<AlignmentStep> {
        val steps = mutableListOf<AlignmentStep>()
        var i = 0
        var j = 0
        while (i < expected.size || j < observed.size) {
            when {
                i < expected.size && j < observed.size && quranWordMatches(expected[i], observed[j]) -> {
                    steps += AlignmentStep(AlignmentOp.MATCH, i, j)
                    i++
                    j++
                }
                i + 1 < expected.size && j < observed.size && quranWordMatches(expected[i + 1], observed[j]) -> {
                    steps += AlignmentStep(AlignmentOp.DELETE, expectedIndex = i)
                    i++
                }
                i < expected.size && j + 1 < observed.size && quranWordMatches(expected[i], observed[j + 1]) -> {
                    steps += AlignmentStep(AlignmentOp.INSERT, observedIndex = j)
                    j++
                }
                i < expected.size && j < observed.size -> {
                    steps += AlignmentStep(AlignmentOp.SUBSTITUTE, i, j)
                    i++
                    j++
                }
                i < expected.size -> {
                    steps += AlignmentStep(AlignmentOp.DELETE, expectedIndex = i)
                    i++
                }
                else -> {
                    steps += AlignmentStep(AlignmentOp.INSERT, observedIndex = j)
                    j++
                }
            }
        }
        return steps
    }

    private fun align(expected: List<QuranWord>, observed: List<String>): List<AlignmentStep> {
        val n = expected.size
        val m = observed.size
        val costs = Array(n + 1) { IntArray(m + 1) { Int.MAX_VALUE / 4 } }
        val back = Array(n + 1) { arrayOfNulls<AlignmentOp>(m + 1) }
        costs[0][0] = 0

        for (i in 1..n) {
            costs[i][0] = costs[i - 1][0] + DELETE_COST
            back[i][0] = AlignmentOp.DELETE
        }
        for (j in 1..m) {
            costs[0][j] = costs[0][j - 1] + INSERT_COST
            back[0][j] = AlignmentOp.INSERT
        }

        for (i in 1..n) {
            for (j in 1..m) {
                val diagonalOp = if (quranWordMatches(expected[i - 1], observed[j - 1])) AlignmentOp.MATCH else AlignmentOp.SUBSTITUTE
                val diagonalCost = costs[i - 1][j - 1] + if (diagonalOp == AlignmentOp.MATCH) 0 else SUBSTITUTE_COST
                val candidates = listOf(
                    Candidate(diagonalCost, if (diagonalOp == AlignmentOp.MATCH) 0 else 3, diagonalOp),
                    Candidate(costs[i - 1][j] + DELETE_COST, 1, AlignmentOp.DELETE),
                    Candidate(costs[i][j - 1] + INSERT_COST, 2, AlignmentOp.INSERT),
                )
                val best = candidates.minWith(compareBy<Candidate> { it.cost }.thenBy { it.priority })
                costs[i][j] = best.cost
                back[i][j] = best.op
            }
        }

        val reversed = mutableListOf<AlignmentStep>()
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            val op = back[i][j] ?: error("Alignment backtrace is incomplete")
            when (op) {
                AlignmentOp.MATCH, AlignmentOp.SUBSTITUTE -> {
                    reversed += AlignmentStep(op, i - 1, j - 1)
                    i--
                    j--
                }
                AlignmentOp.DELETE -> {
                    reversed += AlignmentStep(op, expectedIndex = i - 1)
                    i--
                }
                AlignmentOp.INSERT -> {
                    reversed += AlignmentStep(op, observedIndex = j - 1)
                    j--
                }
            }
        }
        return reversed.asReversed()
    }
}
