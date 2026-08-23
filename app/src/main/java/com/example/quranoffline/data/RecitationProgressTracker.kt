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
) {
    private val words = ayahs.flatMap { it.words }
    private val normalizedWords = words.map { normalizeArabicForComparison(it.matchText) }
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
        val expected = expectedAyah.words.map { normalizeArabicForComparison(it.matchText) }
        if (expected.isEmpty()) {
            advanceAyah()
            return acceptStableToken(token, segment, segmentIndex)
        }

        if (awaitingResume) {
            val resumeIndex = expected.indices
                .filter { expected[it] == token }
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
        val exactCurrent = token == expected[current]
        val rollbackStart = (current - rollbackWindow).coerceAtLeast(0)
        val rollbackMatch = (rollbackStart until current).firstOrNull { expected[it] == token }
        val laterMatch = ((current + 1)..expected.lastIndex).firstOrNull { expected[it] == token }

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
                val request = correctionFor(
                    kind = CorrectionKind.SKIPPED_WORD,
                    ayahIndex = currentAyahIndex,
                    localWordIndex = current,
                    observed = token,
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
                    correctionFor(
                        kind = CorrectionKind.SKIPPED_WORD,
                        ayahIndex = currentAyahIndex,
                        localWordIndex = current,
                        observed = token,
                        stopProcessingSegment = true,
                    )
                } else {
                    // The current ayah was complete: keep context on the immediate
                    // next ayah, never on the ayah the reader jumped to.
                    correctionFor(
                        kind = CorrectionKind.FIRST_WORD_EXPECTED_AYAH,
                        ayahIndex = currentAyahIndex,
                        localWordIndex = 0,
                        observed = token,
                    )
                }
            }
            else -> {
                markWrong(current, token)
                val request = correctionFor(
                    kind = CorrectionKind.WRONG_WORD,
                    ayahIndex = currentAyahIndex,
                    localWordIndex = current,
                    observed = token,
                )
                nextWordInAyah = current + 1
                if (nextWordInAyah >= expected.size) advanceAyah()
                request
            }
        }
    }

    private fun isClearJump(segment: List<String>, segmentIndex: Int): Boolean {
        if (segmentIndex + jumpConfirmationWords > segment.size) return false
        if (currentAyahIndex + 1 !in ayahs.indices) return false
        val phrase = segment.subList(segmentIndex, segmentIndex + jumpConfirmationWords)
        return ayahs.drop(currentAyahIndex + 1).any { ayah ->
            val start = ayah.words.map { normalizeArabicForComparison(it.matchText) }
            start.size >= jumpConfirmationWords && start.take(jumpConfirmationWords) == phrase
        }
    }

    private fun acceptedGlobalWordIndex(token: String, fallback: Int): Int {
        val candidates = words.indices.filter { normalizedWords[it] == token }
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

    private fun correctionFor(
        kind: CorrectionKind,
        ayahIndex: Int,
        localWordIndex: Int,
        observed: String?,
        stopProcessingSegment: Boolean = false,
    ): CorrectionRequest? {
        val key = "$kind:$ayahIndex:$localWordIndex:${normalizeArabicForComparison(observed.orEmpty())}"
        if (activeCorrectionKey != null) return null
        activeCorrectionKey = key
        activeCorrectionGlobalIndex = globalWordIndex(ayahIndex, localWordIndex) ?: -1
        val end = if (kind == CorrectionKind.SKIPPED_WORD) {
            (ayahs.getOrNull(ayahIndex)?.words?.lastIndex ?: localWordIndex)
        } else localWordIndex
        return CorrectionRequest(kind, ayahIndex, localWordIndex, observed, key, readWholeAyah = true, rangeEndLocalWordIndex = end, stopProcessingSegment = stopProcessingSegment)
    }

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
