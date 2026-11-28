package com.example.quranoffline.data

import java.util.UUID

/** Stable input from either the online recognizer or a local recognizer. */
data class RecitationProgressInput(
    val transcript: String,
    val isFinal: Boolean,
    val segmentCompleted: Boolean,
    /** The recognizer ended an utterance: a trailing token that is only the start of the expected word is judged now (v25.6). */
    val endOfUtterance: Boolean = false,
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
    val correctedErrors: List<CorrectedRecitationError> = emptyList(),
    val currentAyahLocked: Boolean = false,
    val transcriptChanged: Boolean,
)

/**
 * Keeps recitation progress inside an ayah instead of searching the whole surah.
 * It commits only final/segment-complete recognizer output; unstable partials are
 * exposed as the current spoken word without advancing the expected context.
 */
class RecitationProgressTracker(
    private val ayahs: List<QuranAyah>,
    /** Null is reserved for synthetic unit tests; production passes the loaded reference explicitly. */
    private val pronunciationReference: PronunciationReference? = null,
    /** Text mode accepts canonical Quran spelling alongside spoken comparison forms. */
    private val allowCanonicalQuranSpelling: Boolean = false,
    private val rollbackWindow: Int = 3,
    private val jumpConfirmationWords: Int = 2,
    correctionSettings: CorrectionSettings = CorrectionSettings.DEFAULTS,
) {
    private var settings = correctionSettings.normalized()
    private val cache = sharedFor(ayahs)
    private val words = cache.words
    private val ayahStarts = IntArray(ayahs.size).also { starts ->
        var offset = 0
        ayahs.forEachIndexed { index, ayah ->
            starts[index] = offset
            offset += ayah.words.size
        }
    }
    private val committedTokens = mutableListOf<String>()
    // Copying the shared pristine list is a cheap array copy; building 77k
    // WordEvaluation objects on the main thread at every start caused stutter.
    private val evaluationWords = ArrayList(cache.pristine)
    private val openErrorKinds = mutableMapOf<Int, CorrectionKind>()
    private val correctedErrors = mutableListOf<CorrectedRecitationError>()
    private var matchedCount = 0
    private var uncertainCount = evaluationWords.size
    private var missingCount = 0
    private var unexpectedCount = 0
    private var currentAyahIndex = 0
    private var nextWordInAyah = 0
    private var activeCorrectionKey: String? = null
    private var activeCorrectionGlobalIndex: Int = -1
    private var awaitingResume = false
    private var resumeAnchorLocalIndex = 0
    private var lastTranscriptTokens: List<String> = emptyList()
    private var pendingWordPrefix: String? = null
    private var muqattaatLeftover: List<MuqattaatLetter> = emptyList()
    private var stopProcessingSegment = false
    // Consecutive issues counted across recognizer segments, not only inside one segment (v24.6).
    private var consecutiveIssueAyah = -1
    private var consecutiveIssues = 0
    private var cachedEvaluation = RecitationEvaluation(
        words = evaluationWords.toList(),
        matched = 0,
        uncertain = evaluationWords.size,
        missing = 0,
        unexpected = 0,
    )

    fun updateSettings(newSettings: CorrectionSettings) {
        settings = newSettings.normalized()
    }

    fun reset() {
        committedTokens.clear()
        evaluationWords.clear()
        evaluationWords.addAll(cache.pristine)
        openErrorKinds.clear()
        correctedErrors.clear()
        matchedCount = 0
        uncertainCount = evaluationWords.size
        missingCount = 0
        unexpectedCount = 0
        currentAyahIndex = 0
        nextWordInAyah = 0
        activeCorrectionKey = null
        activeCorrectionGlobalIndex = -1
        awaitingResume = false
        resumeAnchorLocalIndex = 0
        lastTranscriptTokens = emptyList()
        pendingWordPrefix = null
        stopProcessingSegment = false
        cachedEvaluation = RecitationEvaluation(
            words = evaluationWords.toList(),
            matched = 0,
            uncertain = evaluationWords.size,
            missing = 0,
            unexpected = 0,
        )
    }

    /** Start a new recitation session from a chosen ayah and discard the prior session judgement. */
    fun startFromAyah(ayahIndex: Int) {
        reset()
        currentAyahIndex = ayahIndex.coerceIn(0, ayahs.lastIndex.coerceAtLeast(0))
    }

    /** Start a fresh session from an exact Quran word position. */
    fun startFromPosition(ayahIndex: Int, localWordIndex: Int) {
        reset()
        currentAyahIndex = ayahIndex.coerceIn(0, ayahs.lastIndex.coerceAtLeast(0))
        nextWordInAyah = localWordIndex.coerceIn(0, ayahs.getOrNull(currentAyahIndex)?.words?.size ?: 0)
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
        pendingWordPrefix = null
        stopProcessingSegment = false
    }

    fun update(input: RecitationProgressInput): RecitationProgressResult {
        val tokens = tokenize(input.transcript)
        val changed = tokens != lastTranscriptTokens
        lastTranscriptTokens = tokens
        val stable = input.isFinal || input.segmentCompleted
        var correction: CorrectionRequest? = null
        var spokenGlobalWordIndex = -1

        if (stable) {
            stopProcessingSegment = false
            val newTokens = suffixAfterCommitted(tokens)
            val contextWordIndex = currentGlobalWordIndex()
            val contextMatched = matchCompleteAyahFromReference(newTokens)
            if (contextMatched) spokenGlobalWordIndex = contextWordIndex
            var index = 0
            if (contextMatched) {
                index = newTokens.size
            }
            while (index < newTokens.size) {
                var token = newTokens[index]
                if (muqattaatLeftover.isNotEmpty()) {
                    val next = muqattaatLeftover.first()
                    muqattaatLeftover = muqattaatLeftover.drop(1)
                    if (Muqattaat.unitMatchesToken(next, token)) { index++; continue }
                    muqattaatLeftover = emptyList()
                }
                val expectedCurrent = ayahs.getOrNull(currentAyahIndex)?.words?.getOrNull(nextWordInAyah)
                if (expectedCurrent != null && pendingWordPrefix == null) {
                    val used = Muqattaat.matchedTokenCount(expectedCurrent, newTokens, index)
                    val units = Muqattaat.pronunciationFor(expectedCurrent)?.units?.size ?: 0
                    if (used in 1 until units) {
                        token = newTokens.subList(index, index + used).joinToString(" ")
                        muqattaatLeftover = Muqattaat.missingUnitsAfter(expectedCurrent, used)
                        index += used - 1
                    }
                }
                val muqattaatPhrase = newTokens.drop(index).joinToString(" ")
                if (expectedCurrent != null && Muqattaat.isPotentialPrefix(expectedCurrent, muqattaatPhrase)) {
                    pendingWordPrefix = muqattaatPhrase
                    spokenGlobalWordIndex = currentGlobalWordIndex()
                    break
                }
                if (pendingWordPrefix != null) {
                    val pending = pendingWordPrefix.orEmpty()
                    pendingWordPrefix = null
                    when {
                        matchesCurrentExpected(token) -> Unit
                        matchesCurrentExpected(pending + token) -> token = pending + token
                        else -> {
                            val beforePending = currentGlobalWordIndex()
                            val pendingEvent = acceptStableToken(pending, listOf(pending), 0)
                            if (pendingEvent != null && correction == null) correction = pendingEvent
                            if (pendingEvent?.kind == CorrectionKind.FIRST_WORD_EXPECTED_AYAH || pendingEvent?.stopProcessingSegment == true || stopProcessingSegment) {
                                spokenGlobalWordIndex = beforePending
                                break
                            }
                        }
                    }
                }
                if (index < newTokens.lastIndex) {
                    val joined = newTokens[index] + newTokens[index + 1]
                    if (matchesCurrentExpected(joined)) {
                        token = joined
                        index++
                    }
                }
                if (!input.endOfUtterance && isPotentialWordPrefix(token) && index == newTokens.lastIndex) {
                    pendingWordPrefix = token
                    spokenGlobalWordIndex = currentGlobalWordIndex()
                    break
                }
                val before = currentGlobalWordIndex()
                val event = acceptStableToken(token, newTokens, index)
                if (event != null && correction == null) correction = event
                if (event?.kind == CorrectionKind.FIRST_WORD_EXPECTED_AYAH || event?.stopProcessingSegment == true || stopProcessingSegment) {
                    spokenGlobalWordIndex = before
                    break
                }
                spokenGlobalWordIndex = acceptedGlobalWordIndex(token, before)
                index++
            }
            committedTokens.clear()
            committedTokens.addAll(tokens)
            cachedEvaluation = buildEvaluation()
        }

        val spokenWord = rawLastToken(input.transcript)
        val currentGlobal = currentGlobalWordIndex()
        val resolvedErrors = correctedErrors.toList()
        correctedErrors.clear()
        return RecitationProgressResult(
            evaluation = cachedEvaluation,
            currentAyahIndex = currentAyahIndex,
            currentWordInAyah = nextWordInAyah,
            currentGlobalWordIndex = currentGlobal,
            spokenWord = spokenWord,
            spokenGlobalWordIndex = spokenGlobalWordIndex,
            correction = correction,
            correctedErrors = resolvedErrors,
            currentAyahLocked = currentAyahHasUnresolvedError(),
            transcriptChanged = changed,
        )
    }

    fun globalWordIndex(ayahIndex: Int, localWordIndex: Int): Int? {
        if (ayahIndex !in ayahs.indices) return null
        if (localWordIndex !in 0..ayahs[ayahIndex].words.size) return null
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
            // While the word at the anchor is still wrong or missing, only that word may be
            // recited: later or earlier words are not accepted before it is pronounced correctly.
            val anchorOpen = globalWordIndex(currentAyahIndex, resumeAnchorLocalIndex)?.let { openErrorKinds.containsKey(it) } == true
            val resumeIndex = expected.indices
                .filter { !anchorOpen || it <= resumeAnchorLocalIndex }
                .filter { matchesWord(currentAyahIndex, it, token) }
                .minByOrNull { kotlin.math.abs(it - resumeAnchorLocalIndex) }
            if (resumeIndex != null) {
                awaitingResume = false
                nextWordInAyah = resumeIndex + 1
                // Restarting from an earlier word discards the progress after it.
                if (resumeIndex < resumeAnchorLocalIndex) resetFollowingWordsToUncertain(resumeIndex)
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

        // Opening letters are never blocking: if the reader goes straight on to the word that follows
        // them (self-recited, or too long a madd for the recognizer), the opening counts as done.
        run {
            val at = nextWordInAyah.coerceIn(0, expectedWords.lastIndex)
            val openingWord = expectedWords[at]
            if (Muqattaat.pronunciationFor(openingWord) != null && !Muqattaat.matches(openingWord, token)) {
                val followsInAyah = at + 1 < expectedWords.size && matchesWord(currentAyahIndex, at + 1, token)
                val followsInNextAyah = at + 1 >= expectedWords.size && currentAyahIndex + 1 < ayahs.size &&
                    ayahs[currentAyahIndex + 1].surah == expectedAyah.surah && matchesWord(currentAyahIndex + 1, 0, token)
                if (followsInAyah || followsInNextAyah) {
                    markMatched(at, "")
                    nextWordInAyah = at + 1
                    if (nextWordInAyah >= expected.size) advanceAyah()
                    return acceptStableToken(token, segment, segmentIndex)
                }
            }
        }

        if (settings.automaticPreviousAyah && isPreviousAyahStart(segment, segmentIndex)) {
            val previousAyahIndex = currentAyahIndex - 1
            currentAyahIndex = previousAyahIndex
            nextWordInAyah = 0
            val previousWords = ayahs[previousAyahIndex].words
            markMatched(0, token)
            nextWordInAyah = 1
            if (nextWordInAyah >= previousWords.size) advanceAyah()
            return null
        }

        val current = nextWordInAyah.coerceIn(0, expected.lastIndex)
        val exactCurrent = matchesWord(currentAyahIndex, current, token)
        // The reader may restart from any earlier word of the same ayah (nearest match); progress after
        // that word is cleared. Later words are still never accepted before the current word is right.
        val rollbackMatch = (0 until current).lastOrNull { matchesWord(currentAyahIndex, it, token) }
        val laterMatch = ((current + 1)..expected.lastIndex).firstOrNull { matchesWord(currentAyahIndex, it, token) }

        return when {
            exactCurrent -> {
                markMatched(current, token)
                noteMatch()
                nextWordInAyah = current + 1
                clearCorrectionIfResolved(globalWordIndex(currentAyahIndex, current))
                if (nextWordInAyah >= expected.size) advanceAyah()
                null
            }
            rollbackMatch != null -> {
                // A reader may go back within the same ayah to repair or restart.
                resetFollowingWordsToUncertain(rollbackMatch)
                markMatched(rollbackMatch, token)
                noteMatch()
                nextWordInAyah = rollbackMatch + 1
                clearCorrectionIfResolved(globalWordIndex(currentAyahIndex, rollbackMatch))
                null
            }
            laterMatch != null -> {
                // A later token never validates the words after the first gap.
                // Keep this ayah locked and return all following words to the
                // default state until the missing word is repaired.
                resetFollowingWordsToUncertain(current)
                markMissing(current)
                stopProcessingSegment = true
                correctionForIfEnabled(
                    kind = CorrectionKind.SKIPPED_WORD,
                    ayahIndex = currentAyahIndex,
                    localWordIndex = current,
                    observed = token,
                    readWholeAyah = shouldReadWholeAyah(maxOf(noteIssue(), consecutiveIssueCount(expectedWords, current, segment, segmentIndex))),
                    stopProcessingSegment = true,
                )
            }
            isClearJump(segment, segmentIndex) -> {
                stopProcessingSegment = true
                // A future ayah is never evaluated while this ayah has an
                // unresolved word. Mark only the current ayah and stop this
                // recognizer segment; the next segment must repair it first.
                resetFollowingWordsToUncertain(current - 1)
                for (missing in current until expected.size) markMissing(missing)
                correctionForIfEnabled(
                    kind = CorrectionKind.SKIPPED_WORD,
                    ayahIndex = currentAyahIndex,
                    localWordIndex = current,
                    observed = token,
                    readWholeAyah = shouldReadWholeAyah(expected.size - current),
                    stopProcessingSegment = true,
                )
            }
            else -> {
                resetFollowingWordsToUncertain(current)
                markWrong(current, token)
                stopProcessingSegment = true
                val request = correctionForIfEnabled(
                    kind = CorrectionKind.WRONG_WORD,
                    ayahIndex = currentAyahIndex,
                    localWordIndex = current,
                    observed = token,
                    readWholeAyah = shouldReadWholeAyah(maxOf(noteIssue(), consecutiveIssueCount(expectedWords, current, segment, segmentIndex))),
                    stopProcessingSegment = true,
                )
                nextWordInAyah = current
                request
            }
        }
    }

    private fun isPreviousAyahStart(segment: List<String>, segmentIndex: Int): Boolean {
        if (currentAyahIndex <= 0 || currentAyahIndex !in ayahs.indices || nextWordInAyah != 0) return false
        val currentWords = ayahs[currentAyahIndex].words
        val previousWords = ayahs[currentAyahIndex - 1].words
        val confirmationCount = minOf(jumpConfirmationWords, previousWords.size)
        if (confirmationCount <= 0 || segmentIndex + confirmationCount > segment.size) return false
        if (matchesWord(currentAyahIndex, 0, segment[segmentIndex])) return false
        return (0 until confirmationCount).all { offset ->
            matchesWord(currentAyahIndex - 1, offset, segment[segmentIndex + offset])
        }
    }

    private fun matchesCurrentExpected(token: String): Boolean {
        if (currentAyahIndex !in ayahs.indices) return false
        val expectedWords = ayahs[currentAyahIndex].words
        val candidateIndices = if (awaitingResume) {
            expectedWords.indices
        } else {
            listOf(nextWordInAyah.coerceIn(0, expectedWords.lastIndex))
        }
        return candidateIndices.any { matchesWord(currentAyahIndex, it, token) }
    }

    private fun isPotentialWordPrefix(token: String): Boolean {
        if (currentAyahIndex !in ayahs.indices) return false
        val expectedWords = ayahs[currentAyahIndex].words
        val targetIndex = if (awaitingResume) {
            resumeAnchorLocalIndex.coerceIn(0, expectedWords.lastIndex)
        } else {
            nextWordInAyah.coerceIn(0, expectedWords.lastIndex)
        }
        if (Muqattaat.isPotentialPrefix(expectedWords[targetIndex], token)) return true
        val observed = normalizeArabicForComparison(token)
        if (observed.length < 2) return false
        val expectedForms = arabicSpellingForms(expectedWords[targetIndex].text, allowSpeechTaMarbutaSubstitution = true) +
            arabicSpellingForms(expectedWords[targetIndex].matchText, allowSpeechTaMarbutaSubstitution = false)
        return expectedForms.any { it.startsWith(observed) && it.length > observed.length }
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
            if (matchesWord(currentAyahIndex, expectedIndex, token)) {
                maximum = maxOf(maximum, issueCount)
                issueCount = 0
                expectedIndex++
                continue
            }
            val later = ((expectedIndex + 1)..expected.lastIndex).firstOrNull { matchesWord(currentAyahIndex, it, token) }
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

    /**
     * Ayahs grouped by everything that decides whether their FIRST word matches an observed token (the word and its
     * pronunciation reference). Built once; lets isClearJump test each distinct first word once instead of scanning all
     * later ayahs on every mismatching token (v24.5).
     */
    private val firstWordGroups: List<IntArray> by lazy {
        val byKey = LinkedHashMap<Any?, MutableList<Int>>()
        ayahs.forEachIndexed { i, a ->
            val w = a.words.firstOrNull() ?: return@forEachIndexed
            val ref = pronunciationReference?.word(a.surah, a.ayah, 0)
            val refKey = ref?.let { listOf(it.spoken, it.spokenTokens, it.maddPositions, it.recognizedForms, it.waqfForms) }
            byKey.getOrPut(w to refKey) { mutableListOf() }.add(i)
        }
        byKey.values.map { it.toIntArray() }
    }

    private fun isClearJump(segment: List<String>, segmentIndex: Int): Boolean {
        if (segmentIndex + jumpConfirmationWords > segment.size) return false
        if (currentAyahIndex + 1 !in ayahs.indices) return false
        val phrase = segment.subList(segmentIndex, segmentIndex + jumpConfirmationWords)
        val after = currentAyahIndex + 1
        for (group in firstWordGroups) {
            if (group[group.size - 1] < after) continue
            // Members share the first word and its reference, so one test decides the first word for the whole group.
            if (!matchesWord(group[0], 0, phrase[0])) continue
            for (candidate in group) {
                if (candidate < after) continue
                val start = ayahs[candidate].words.take(jumpConfirmationWords)
                if (start.size >= jumpConfirmationWords && start.indices.all { matchesWord(candidate, it, phrase[it]) }) return true
            }
        }
        return false
    }

    private fun acceptedGlobalWordIndex(token: String, fallback: Int): Int {
        if (currentAyahIndex !in ayahs.indices) return fallback
        val start = ayahStarts[currentAyahIndex]
        val candidates = ayahs[currentAyahIndex].words.indices.filter { local ->
            matchesWord(currentAyahIndex, local, token)
        }
        return candidates.minByOrNull { local -> kotlin.math.abs(start + local - fallback) }?.let(start::plus) ?: fallback
    }

    private fun markMatched(localIndex: Int, observed: String) {
        val global = globalWordIndex(currentAyahIndex, localIndex) ?: return
        val previous = evaluationWords.getOrNull(global)
        if (previous != null && (previous.status == WordStatus.MISSING || previous.status == WordStatus.EXTRA_OR_UNEXPECTED)) {
            correctedErrors += CorrectedRecitationError(
                id = UUID.randomUUID().toString(),
                surah = ayahs[currentAyahIndex].surah,
                ayah = ayahs[currentAyahIndex].ayah,
                localWordIndex = localIndex,
                expected = previous.expected,
                observed = previous.observed,
                correctedWith = observed,
                kind = openErrorKinds[global] ?: CorrectionKind.WRONG_WORD,
                correctedAt = System.currentTimeMillis(),
            )
        }
        openErrorKinds.remove(global)
        setEvaluation(global, WordEvaluation(ayahs[currentAyahIndex].words[localIndex].text, observed, WordStatus.MATCHED))
    }

    private fun markMissing(localIndex: Int) {
        val global = globalWordIndex(currentAyahIndex, localIndex) ?: return
        openErrorKinds.putIfAbsent(global, CorrectionKind.SKIPPED_WORD)
        setEvaluation(global, WordEvaluation(ayahs[currentAyahIndex].words[localIndex].text, null, WordStatus.MISSING))
    }

    private fun resetFollowingWordsToUncertain(localIndex: Int) {
        if (currentAyahIndex !in ayahs.indices) return
        val start = ayahStarts[currentAyahIndex]
        val endExclusive = start + ayahs[currentAyahIndex].words.size
        for (global in (start + localIndex + 1).coerceAtLeast(start) until endExclusive) {
            openErrorKinds.remove(global)
            val word = words[global]
            setEvaluation(global, WordEvaluation(word.text, null, WordStatus.UNCERTAIN))
        }
    }

    private fun markWrong(localIndex: Int, observed: String) {
        val global = globalWordIndex(currentAyahIndex, localIndex) ?: return
        openErrorKinds.putIfAbsent(global, CorrectionKind.WRONG_WORD)
        setEvaluation(global, WordEvaluation(ayahs[currentAyahIndex].words[localIndex].text, observed, WordStatus.EXTRA_OR_UNEXPECTED))
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

    /** One issue event, counted across segments so the whole-ayah rule can actually reach its threshold. */
    private fun noteIssue(): Int {
        if (consecutiveIssueAyah != currentAyahIndex) { consecutiveIssueAyah = currentAyahIndex; consecutiveIssues = 0 }
        consecutiveIssues++
        return consecutiveIssues
    }

    private fun noteMatch() { consecutiveIssueAyah = currentAyahIndex; consecutiveIssues = 0 }

    private fun shouldReadWholeAyah(issueCount: Int): Boolean =
        settings.wholeAyahAfterConsecutiveErrors > 0 && issueCount >= settings.wholeAyahAfterConsecutiveErrors

    private fun clearCorrectionIfResolved(globalIndex: Int?) {
        if (globalIndex != null && globalIndex == activeCorrectionGlobalIndex) {
            activeCorrectionKey = null
            activeCorrectionGlobalIndex = -1
        }
    }

    private fun currentAyahHasUnresolvedError(): Boolean {
        if (currentAyahIndex !in ayahs.indices) return false
        val start = ayahStarts[currentAyahIndex]
        return ayahs[currentAyahIndex].words.indices.any { local ->
            evaluationWords.getOrNull(start + local)?.status in setOf(WordStatus.MISSING, WordStatus.EXTRA_OR_UNEXPECTED)
        }
    }

    private fun advanceAyah() {
        if (currentAyahIndex !in ayahs.indices) return
        val start = ayahStarts[currentAyahIndex]
        val size = ayahs[currentAyahIndex].words.size
        val unresolved = (0 until size).firstOrNull { evaluationWords.getOrNull(start + it)?.status != WordStatus.MATCHED }
        if (unresolved != null) {
            // Do not unlock the next ayah until every word in this ayah is green.
            nextWordInAyah = unresolved
            return
        }
        currentAyahIndex++
        nextWordInAyah = 0
        consecutiveIssueAyah = -1
        consecutiveIssues = 0
    }

    private fun currentGlobalWordIndex(): Int {
        if (currentAyahIndex !in ayahs.indices) return words.size
        return ayahStarts[currentAyahIndex] + nextWordInAyah.coerceAtMost(ayahs[currentAyahIndex].words.size)
    }

    private fun setEvaluation(globalIndex: Int, evaluation: WordEvaluation) {
        if (globalIndex !in evaluationWords.indices) return
        when (evaluationWords[globalIndex].status) {
            WordStatus.MATCHED -> matchedCount--
            WordStatus.UNCERTAIN -> uncertainCount--
            WordStatus.MISSING -> missingCount--
            WordStatus.EXTRA_OR_UNEXPECTED -> unexpectedCount--
        }
        evaluationWords[globalIndex] = evaluation
        when (evaluation.status) {
            WordStatus.MATCHED -> matchedCount++
            WordStatus.UNCERTAIN -> uncertainCount++
            WordStatus.MISSING -> missingCount++
            WordStatus.EXTRA_OR_UNEXPECTED -> unexpectedCount++
        }
    }

    private fun buildEvaluation(): RecitationEvaluation = RecitationEvaluation(
        words = evaluationWords.toList(),
        matched = matchedCount,
        uncertain = uncertainCount,
        missing = missingCount,
        unexpected = unexpectedCount,
    )

    /**
     * Uses the complete Sheikh-derived ayah phrase as a second-level check.
     * It runs only at the beginning of an ayah and only on a stable complete
     * phrase, so it cannot skip an unresolved word or unlock the next ayah.
     */
    private fun matchCompleteAyahFromReference(tokens: List<String>): Boolean {
        if (tokens.isEmpty() || nextWordInAyah != 0 || awaitingResume || committedTokens.isNotEmpty()) return false
        val ayah = ayahs.getOrNull(currentAyahIndex) ?: return false
        val reference = pronunciationReference?.ayah(ayah.surah, ayah.ayah) ?: return false
        if (!PronunciationSpeechMatcher.matchesAyahPhrase(
                reference,
                tokens,
                visualWords = ayah.words,
                allowCanonicalQuranSpelling = allowCanonicalQuranSpelling,
            )) return false
        val observedUnits = tokens.flatMap { it.split(' ').filter(String::isNotBlank) }
        var observedIndex = 0
        ayah.words.indices.forEach { localIndex ->
            val referenceWord = reference.words.getOrNull(localIndex) ?: return@forEach
            val unitCount = referenceWord.spokenTokens.ifEmpty { listOf(referenceWord.spoken) }.size
            val observed = observedUnits.drop(observedIndex).take(unitCount).joinToString(" ")
            markMatched(localIndex, observed)
            observedIndex += unitCount
        }
        nextWordInAyah = ayah.words.size
        advanceAyah()
        return true
    }

    private fun matchesWord(ayahIndex: Int, localWordIndex: Int, observed: String): Boolean {
        val ayah = ayahs.getOrNull(ayahIndex) ?: return false
        val word = ayah.words.getOrNull(localWordIndex) ?: return false
        val referenceWord = pronunciationReference?.word(ayah.surah, ayah.ayah, localWordIndex)
        return if (pronunciationReference == null) {
            // This branch is only for synthetic tracker tests that omit the
            // speech asset. The app always injects the complete local asset.
            quranWordMatches(word, observed)
        } else {
            PronunciationSpeechMatcher.matches(
                word = word,
                pronunciation = referenceWord,
                observed = observed,
                allowCanonicalQuranSpelling = allowCanonicalQuranSpelling,
            )
        }
    }

    /**
     * Preserve the recognizer token as observed data. Arabic normalization is
     * performed only inside the speech matcher; it must not turn the observed
     * result into Quran display spelling before judgement.
     */
    private fun tokenize(text: String): List<String> = Muqattaat.coalesceSpeechTokens(
        text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    )

    private fun rawLastToken(text: String): String? =
        text.trim().split(Regex("\\s+")).lastOrNull { it.isNotBlank() }

    private fun longestCommonPrefix(first: List<String>, second: List<String>): Int {
        val limit = minOf(first.size, second.size)
        var index = 0
        while (index < limit && first[index] == second[index]) index++
        return index
    }

    private class Shared(val words: List<QuranWord>, val pristine: List<WordEvaluation>)

    companion object {
        @Volatile private var shared: Pair<List<QuranAyah>, Shared>? = null

        private fun sharedFor(ayahs: List<QuranAyah>): Shared {
            shared?.let { if (it.first === ayahs) return it.second }
            synchronized(this) {
                shared?.let { if (it.first === ayahs) return it.second }
                val words = ayahs.flatMap { it.words }
                val built = Shared(words, words.map { WordEvaluation(it.text, null, WordStatus.UNCERTAIN) })
                shared = ayahs to built
                return built
            }
        }

        /** Build the shared word tables off the main thread before the first recitation starts. */
        fun prewarm(ayahs: List<QuranAyah>) { sharedFor(ayahs) }
    }
}
