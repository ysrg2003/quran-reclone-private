package com.example.quranoffline.data

/** Stable input from either the online recognizer or a local recognizer. */
data class RecitationProgressInput(
    val transcript: String,
    val isFinal: Boolean,
    val segmentCompleted: Boolean,
)

enum class CorrectionKind {
    WRONG_WORD,
    SKIPPED_WORD,
    FIRST_WORD_EXPECTED_AYAH,
}

enum class CorrectionPhase {
    LISTENING,
    PLAYING_FIRST_ERROR,
    PLAYING_WHOLE_AYAH,
    WAITING_RESUME,
}

data class CorrectionRequest(
    val kind: CorrectionKind,
    val ayahIndex: Int,
    val localWordIndex: Int,
    val observed: String?,
    val dedupeKey: String,
    val readWholeAyah: Boolean = true,
    val rangeEndLocalWordIndex: Int = localWordIndex,
    val stopProcessingSegment: Boolean = false,
)

data class RecitationProgressResult(
    val evaluation: RecitationEvaluation,
    val currentAyahIndex: Int,
    val currentWordInAyah: Int,
    val currentGlobalWordIndex: Int,
    val spokenWord: String?,
    val spokenGlobalWordIndex: Int,
    val correction: CorrectionRequest?,
    val transcriptChanged: Boolean,
)

/**
 * Keeps recitation progress inside an ayah instead of searching the whole surah.
 * It commits only final/segment-complete recognizer output; unstable partials are
 * exposed as the current spoken word without advancing the expected context.
 */
class RecitationProgressTracker(
    private val ayahs: List<QuranAyah>,
    private val rollbackWindow: Int = 3,
    private val jumpConfirmationWords: Int = 2,
    correctionSettings: CorrectionSettings = CorrectionSettings.DEFAULTS,
) {
    private var settings = correctionSettings.normalized()
    private val words = ayahs.flatMap { it.words }
    private val ayahStarts = IntArray(ayahs.size).also { starts ->
        var offset = 0
        ayahs.forEachIndexed { index, ayah ->
            starts[index] = offset
            offset += ayah.words.size
        }
    }
    private val committedTokens = mutableListOf<String>()
    private val judged = linkedMapOf<Int, WordEvaluation>()
    private var currentAyahIndex = 0
    private var nextWordInAyah = 0
    private var activeCorrectionKey: String? = null
    private var activeCorrectionGlobalIndex: Int = -1
    private var awaitingResume = false
    private var resumeAnchorLocalIndex = 0
    private var lastTranscriptTokens: List<String> = emptyList()

    fun updateSettings(newSettings: CorrectionSettings) {
        settings = newSettings.normalized()
    }

    fun reset() {
        committedTokens.clear()
        judged.clear()
        currentAyahIndex = 0
        nextWordInAyah = 0
        activeCorrectionKey = null
        activeCorrectionGlobalIndex = -1
        awaitingResume = false
        resumeAnchorLocalIndex = 0
        lastTranscriptTokens = emptyList()
    }

    /** Prepare a fresh recognizer segment while preserving the current ayah and evaluations. */
    fun resumeFrom(ayahIndex: Int, localWordIndex: Int) {
        currentAyahIndex = ayahIndex.coerceIn(0, ayahs.lastIndex.coerceAtLeast(0))
        nextWordInAyah = 0
        resumeAnchorLocalIndex = localWordIndex.coerceAtLeast(0)
        awaitingResume = true
        activeCorrectionKey = null
        activeCorrectionGlobalIndex = -1
        committedTokens.clear()
        lastTranscriptTokens = emptyList()
    }

    fun update(input: RecitationProgressInput): RecitationProgressResult {
        val tokens = tokenize(input.transcript)
        val changed = tokens != lastTranscriptTokens
        lastTranscriptTokens = tokens
        val stable = input.isFinal || input.segmentCompleted
        var correction: CorrectionRequest? = null
        var spokenGlobalWordIndex = -1

        if (stable) {
            val newTokens = suffixAfterCommitted(tokens)
            for (index in newTokens.indices) {
                val token = newTokens[index]
                val before = currentGlobalWordIndex()
                val event = acceptStableToken(token, newTokens, index)
                if (event != null && correction == null) correction = event
                if (event?.kind == CorrectionKind.FIRST_WORD_EXPECTED_AYAH || event?.stopProcessingSegment == true) {
                    spokenGlobalWordIndex = before
                    break
                }
                spokenGlobalWordIndex = acceptedGlobalWordIndex(token, before)
            }
            committedTokens.clear()
            committedTokens.addAll(tokens)
        }

        val spokenWord = tokens.lastOrNull()
        val currentGlobal = currentGlobalWordIndex()
        return RecitationProgressResult(
            evaluation = buildEvaluation(),
            currentAyahIndex = currentAyahIndex,
            currentWordInAyah = nextWordInAyah,
            currentGlobalWordIndex = currentGlobal,
            spokenWord = spokenWord,
            spokenGlobalWordIndex = spokenGlobalWordIndex,
            correction = correction,
            transcriptChanged = changed,
        )
    }

    fun globalWordIndex(ayahIndex: Int, localWordIndex: Int): Int? {
        if (ayahIndex !in ayahs.indices) return null
        if (localWordIndex !in ayahs[ayahIndex].words.indices) return null
        return ayahStarts[ayahIndex] + localWordIndex
    }

    private fun suffixAfterCommitted(tokens: List<String>): List<String> {
        val prefix = longestCommonPrefix(tokens, committedTokens)
        return if (prefix == committedTokens.size) {
            tokens.drop(prefix)
        } else {
            // A recognizer can rebase a result after a restart. Do not replay the
            // entire old transcript; keep the newest result as one stable segment.
            tokens
        }
    }

    private fun acceptStableToken(
        token: String,
        segment: List<String>,
        segmentIndex: Int,
    ): CorrectionRequest? {
        if (currentAyahIndex !in ayahs.indices) return null
        val expectedAyah = ayahs[currentAyahIndex]
        val expectedWords = expectedAyah.words
        val expected = expectedWords.map { it.matchText }
        if (expected.isEmpty()) {
            advanceAyah()
            return acceptStableToken(token, segment, segmentIndex)
        }

        if (awaitingResume) {
            val resumeIndex = expected.indices
                .filter { quranWordMatches(expectedWords[it], token) }
                .minByOrNull { kotlin.math.abs(it - resumeAnchorLocalIndex) }
            if (resumeIndex != null) {
                awaitingResume = false
                nextWordInAyah = resumeIndex + 1
                markMatched(resumeIndex, token)
                clearCorrectionIfResolved(globalWordIndex(currentAyahIndex, resumeIndex))
                if (nextWordInAyah >= expected.size) advanceAyah()
                return null
            }
            // A wrong first word after playback is judged near the saved error,
            // not at word zero; the reader may still choose another valid word.
            awaitingResume = false
            nextWordInAyah = resumeAnchorLocalIndex.coerceIn(0, expected.lastIndex)
        }

        val current = nextWordInAyah.coerceIn(0, expected.lastIndex)
        val exactCurrent = quranWordMatches(expectedWords[current], token)
        val rollbackStart = (current - rollbackWindow).coerceAtLeast(0)
        val rollbackMatch = (rollbackStart until current).firstOrNull { quranWordMatches(expectedWords[it], token) }
        val laterMatch = ((current + 1)..expected.lastIndex).firstOrNull { quranWordMatches(expectedWords[it], token) }

        return when {
            exactCurrent -> {
                markMatched(current, token)
                nextWordInAyah = current + 1
                clearCorrectionIfResolved(globalWordIndex(currentAyahIndex, current))
                if (nextWordInAyah >= expected.size) advanceAyah()
                null
            }
            rollbackMatch != null -> {
                // A reader may go back within the same ayah to repair or restart.
                markMatched(rollbackMatch, token)
                nextWordInAyah = rollbackMatch + 1
                clearCorrectionIfResolved(globalWordIndex(currentAyahIndex, rollbackMatch))
                null
            }
            laterMatch != null -> {
                for (missing in current until laterMatch) markMissing(missing)
                val request = correctionForIfEnabled(
                    kind = CorrectionKind.SKIPPED_WORD,
                    ayahIndex = currentAyahIndex,
                    localWordIndex = current,
                    observed = token,
                    readWholeAyah = shouldReadWholeAyah(consecutiveIssueCount(expectedWords, current, segment, segmentIndex)),
                )
                markMatched(laterMatch, token)
                nextWordInAyah = laterMatch + 1
                clearCorrectionIfResolved(globalWordIndex(currentAyahIndex, laterMatch))
                if (nextWordInAyah >= expected.size) advanceAyah()
                request
            }
            isClearJump(segment, segmentIndex) -> {
                if (nextWordInAyah < expected.size) {
                    // The current ayah was not complete: mark its remaining words
                    // as wrong/missing and replay this same ayah from its first gap.
                    for (missing in current until expected.size) markMissing(missing)
                    correctionForIfEnabled(
                        kind = CorrectionKind.SKIPPED_WORD,
                        ayahIndex = currentAyahIndex,
                        localWordIndex = current,
                        observed = token,
                        readWholeAyah = shouldReadWholeAyah(expected.size - current),
                        stopProcessingSegment = true,
                    )
                } else {
                    // The current ayah was complete: keep context on the immediate
                    // next ayah, never on the ayah the reader jumped to.
                    correctionForIfEnabled(
                        kind = CorrectionKind.FIRST_WORD_EXPECTED_AYAH,
                        ayahIndex = currentAyahIndex,
                        localWordIndex = 0,
                        observed = token,
                        readWholeAyah = true,
                    )
                }
            }
            else -> {
                markWrong(current, token)
                val request = correctionForIfEnabled(
                    kind = CorrectionKind.WRONG_WORD,
                    ayahIndex = currentAyahIndex,
                    localWordIndex = current,
                    observed = token,
                    readWholeAyah = shouldReadWholeAyah(consecutiveIssueCount(expectedWords, current, segment, segmentIndex)),
                )
                nextWordInAyah = current + 1
                if (nextWordInAyah >= expected.size) advanceAyah()
                request
            }
        }
    }

    private fun consecutiveIssueCount(
        expected: List<QuranWord>,
        startIndex: Int,
        segment: List<String>,
        segmentIndex: Int,
    ): Int {
        var expectedIndex = startIndex
        var issueCount = 0
        var maximum = 0
        for (token in segment.drop(segmentIndex)) {
            if (expectedIndex >= expected.size) break
            if (quranWordMatches(expected[expectedIndex], token)) {
                maximum = maxOf(maximum, issueCount)
                issueCount = 0
                expectedIndex++
                continue
            }
            val later = ((expectedIndex + 1)..expected.lastIndex).firstOrNull { quranWordMatches(expected[it], token) }
            if (later != null) {
                issueCount += later - expectedIndex
                expectedIndex = later + 1
            } else {
                issueCount++
                expectedIndex++
            }
        }
        return maxOf(maximum, issueCount)
    }

    private fun isClearJump(segment: List<String>, segmentIndex: Int): Boolean {
        if (segmentIndex + jumpConfirmationWords > segment.size) return false
        if (currentAyahIndex + 1 !in ayahs.indices) return false
        val phrase = segment.subList(segmentIndex, segmentIndex + jumpConfirmationWords)
        return ayahs.drop(currentAyahIndex + 1).any { ayah ->
            val start = ayah.words.take(jumpConfirmationWords)
            start.size >= jumpConfirmationWords && start.indices.all { quranWordMatches(start[it], phrase[it]) }
        }
    }

    private fun acceptedGlobalWordIndex(token: String, fallback: Int): Int {
        val candidates = words.indices.filter { quranWordMatches(words[it], token) }
        return candidates.minByOrNull { kotlin.math.abs(it - fallback) } ?: fallback
    }

    private fun markMatched(localIndex: Int, observed: String) {
        val global = globalWordIndex(currentAyahIndex, localIndex) ?: return
        judged[global] = WordEvaluation(ayahs[currentAyahIndex].words[localIndex].text, observed, WordStatus.MATCHED)
    }

    private fun markMissing(localIndex: Int) {
        val global = globalWordIndex(currentAyahIndex, localIndex) ?: return
        judged[global] = WordEvaluation(ayahs[currentAyahIndex].words[localIndex].text, null, WordStatus.MISSING)
    }

    private fun markWrong(localIndex: Int, observed: String) {
        val global = globalWordIndex(currentAyahIndex, localIndex) ?: return
        judged[global] = WordEvaluation(ayahs[currentAyahIndex].words[localIndex].text, observed, WordStatus.EXTRA_OR_UNEXPECTED)
    }

    private fun correctionForIfEnabled(
        kind: CorrectionKind,
        ayahIndex: Int,
        localWordIndex: Int,
        observed: String?,
        readWholeAyah: Boolean = false,
        stopProcessingSegment: Boolean = false,
    ): CorrectionRequest? {
        if (!settings.automaticCorrectionEnabled) return null
        val enabled = when (kind) {
            CorrectionKind.WRONG_WORD -> settings.correctWrongWords
            CorrectionKind.SKIPPED_WORD -> settings.correctSkippedWords
            CorrectionKind.FIRST_WORD_EXPECTED_AYAH -> settings.correctAyahJumps
        }
        if (!enabled) return null
        return correctionFor(kind, ayahIndex, localWordIndex, observed, readWholeAyah, stopProcessingSegment)
    }

    private fun correctionFor(
        kind: CorrectionKind,
        ayahIndex: Int,
        localWordIndex: Int,
        observed: String?,
        readWholeAyah: Boolean = false,
        stopProcessingSegment: Boolean = false,
    ): CorrectionRequest? {
        val key = "$kind:$ayahIndex:$localWordIndex:${normalizeArabicForComparison(observed.orEmpty())}"
        if (activeCorrectionKey != null) return null
        activeCorrectionKey = key
        activeCorrectionGlobalIndex = globalWordIndex(ayahIndex, localWordIndex) ?: -1
        val end = if (kind == CorrectionKind.SKIPPED_WORD) {
            (ayahs.getOrNull(ayahIndex)?.words?.lastIndex ?: localWordIndex)
        } else localWordIndex
        return CorrectionRequest(kind, ayahIndex, localWordIndex, observed, key, readWholeAyah = readWholeAyah, rangeEndLocalWordIndex = end, stopProcessingSegment = stopProcessingSegment)
    }

    private fun shouldReadWholeAyah(issueCount: Int): Boolean =
        settings.playFirstErrorBeforeWholeAyah && issueCount >= settings.wholeAyahAfterConsecutiveErrors

    private fun clearCorrectionIfResolved(globalIndex: Int?) {
        if (globalIndex != null && globalIndex == activeCorrectionGlobalIndex) {
            activeCorrectionKey = null
            activeCorrectionGlobalIndex = -1
        }
    }

    private fun advanceAyah() {
        currentAyahIndex++
        nextWordInAyah = 0
    }

    private fun currentGlobalWordIndex(): Int {
        if (currentAyahIndex !in ayahs.indices) return words.size
        return ayahStarts[currentAyahIndex] + nextWordInAyah.coerceAtMost(ayahs[currentAyahIndex].words.size)
    }

    private fun buildEvaluation(): RecitationEvaluation {
        val evaluations = words.mapIndexed { index, word ->
            judged[index] ?: WordEvaluation(word.text, null, WordStatus.UNCERTAIN)
        }
        return RecitationEvaluation(
            words = evaluations,
            matched = evaluations.count { it.status == WordStatus.MATCHED },
            uncertain = evaluations.count { it.status == WordStatus.UNCERTAIN },
            missing = evaluations.count { it.status == WordStatus.MISSING },
            unexpected = evaluations.count { it.status == WordStatus.EXTRA_OR_UNEXPECTED },
        )
    }

    private fun tokenize(text: String): List<String> = normalizeArabicForComparison(text)
        .split(' ')
        .filter(String::isNotBlank)
        .map(::normalizeArabicForComparison)

    private fun longestCommonPrefix(first: List<String>, second: List<String>): Int {
        val limit = minOf(first.size, second.size)
        var index = 0
        while (index < limit && first[index] == second[index]) index++
        return index
    }
}
