package com.example.quranoffline.data

import android.app.Application
import android.os.Build
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val AYAH_PAGE_SIZE = 8

enum class AssetMode { STREAM, DOWNLOAD }
enum class RecitationEngine { ONLINE, OFFLINE }

data class QuranUiState(
    val ayahs: List<QuranAyah> = emptyList(),
    val metadata: QuranMetadata? = null,
    val hidden: Boolean = false,
    val listening: Boolean = false,
    val assetsReady: Boolean = false,
    val loadingAssets: Boolean = true,
    val transcript: String = "",
    val evaluation: RecitationEvaluation? = null,
    val notice: String = "جاري تحميل النص والتوقيتات من مستودع الأصول…",
    val sessions: List<SessionSummary> = emptyList(),
    val goal: DailyGoal = DailyGoal(targetAyahs = 3, completedAyahs = 0, day = ""),
    val currentWordIndex: Int = -1,
    val currentSpokenWord: String? = null,
    val currentSpokenWordIndex: Int = -1,
    val currentAyahIndex: Int = -1,
    val currentWordInAyah: Int = -1,
    val recitationCursor: Int = 0,
    val correctionPlaying: Boolean = false,
    val correctionPhase: CorrectionPhase = CorrectionPhase.LISTENING,
    val correctionMessage: String? = null,
    val correctionSettings: CorrectionSettings = CorrectionSettings.DEFAULTS,
    val correctionAyahIndex: Int = -1,
    val correctionFirstWordInAyah: Int = -1,
    val selectedSurah: Int = 1,
    val currentPage: Int = 0,
    val totalAyahsInSurah: Int = 7,
    val mode: AssetMode = AssetMode.STREAM,
    val downloadInProgress: Boolean = false,
    val downloadedAyahs: Int = 0,
    val engine: RecitationEngine = RecitationEngine.ONLINE,
    val modelReady: Boolean = false,
    val modelDownloading: Boolean = false,
    val offlineSupported: Boolean = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(),
)

private data class PreparedAssets(
    val repository: QuranRepository,
    val metadata: QuranMetadata,
    val ayahs: List<QuranAyah>,
    val selectedSurah: Int,
    val totalAyahs: Int,
    val downloadedAyahs: Int,
    val modelReady: Boolean,
)

class QuranViewModel(application: Application) : AndroidViewModel(application) {
    private val assetManager = RemoteAssetManager(application)
    private val metadataRepository = QuranMetadataRepository(application)
    private var repository = QuranRepository(application)
    private val store = LocalSessionStore(application)
    private val goalStore = LocalGoalStore(application)
    private val correctionSettingsStore = LocalCorrectionSettingsStore(application)
    private val timingRepository = ReferenceTimingRepository()
    private val referenceAudioPlayer = ReferenceAudioPlayer()
    private var onlineSession: OnlineSpeechSession? = null
    private var localSession: LocalRecitationSession? = null
    private var progressTracker: RecitationProgressTracker? = null
    private var recognitionGeneration = 0L
    private var activeCorrection: CorrectionRequest? = null
    private var correctionWasListening = false

    private val state = mutableStateOf(
        QuranUiState(
            sessions = store.list(),
            goal = goalStore.current(),
            correctionSettings = correctionSettingsStore.current(),
            offlineSupported = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(),
        ),
    )
    val uiState: State<QuranUiState> = state

    private val targetWords: List<QuranWord>
        get() = state.value.ayahs.flatMap { it.words }

    init { loadAssets(AssetMode.STREAM) }

    fun setMode(mode: AssetMode) {
        if (mode == state.value.mode && state.value.assetsReady) return
        stopListening()
        state.value = state.value.copy(mode = mode, loadingAssets = true, assetsReady = false, notice = "جاري تجهيز وضع ${if (mode == AssetMode.STREAM) "البث المباشر" else "التنزيل المحلي"}…")
        loadAssets(mode)
    }

    fun setEngine(engine: RecitationEngine) {
        if (state.value.engine == engine) return
        stopListening()
        val notice = when {
            engine == RecitationEngine.ONLINE -> "سيستخدم التسميع SpeechRecognizer عبر الإنترنت."
            !state.value.offlineSupported -> offlineUnavailableMessage()
            else -> "سيستخدم التسميع نموذج FastConformer المحلي؛ نزّله مرة واحدة أولاً."
        }
        state.value = state.value.copy(engine = engine, notice = notice, correctionMessage = null)
    }

    fun downloadOfflineModel() {
        if (state.value.modelReady || state.value.modelDownloading) return
        state.value = state.value.copy(modelDownloading = true, notice = "جاري تنزيل نموذج التسميع Offline إلى التخزين المحلي…")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                assetManager.downloadModel()
                withContext(Dispatchers.Main) { state.value = state.value.copy(modelReady = true, modelDownloading = false, notice = "اكتمل تنزيل النموذج. يمكنك التسميع Offline دون إنترنت.") }
            } catch (error: Throwable) {
                withContext(Dispatchers.Main) { state.value = state.value.copy(modelDownloading = false, notice = "تعذر تنزيل النموذج المحلي: ${error.message ?: "خطأ شبكي"}") }
            }
        }
    }

    private fun loadAssets(mode: AssetMode) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    if (mode == AssetMode.DOWNLOAD) assetManager.loadOrDownloadText() else assetManager.fetchText()
                }
                val timings = withContext(Dispatchers.IO) {
                    if (mode == AssetMode.DOWNLOAD) assetManager.loadOrDownloadTimings() else assetManager.fetchTimings()
                }
                val requestedSurah = state.value.selectedSurah
                val prepared = withContext(Dispatchers.Default) {
                    val parsedRepository = QuranRepository(getApplication(), text)
                    val parsedMetadata = metadataRepository.load()
                    parsedMetadata.requireCompatible(parsedRepository.allAyahs())
                    timingRepository.update(timings)
                    val requested = parsedRepository.ayahsForSurah(requestedSurah).ifEmpty { parsedRepository.ayahsForSurah(1) }
                    val selectedSurah = requested.firstOrNull()?.surah ?: 1
                    val totalAyahs = parsedRepository.ayahsForSurah(selectedSurah).size
                    PreparedAssets(
                        repository = parsedRepository,
                        metadata = parsedMetadata,
                        ayahs = requested,
                        selectedSurah = selectedSurah,
                        totalAyahs = totalAyahs,
                        downloadedAyahs = assetManager.downloadedAyahCount(selectedSurah, totalAyahs),
                        modelReady = assetManager.isModelDownloaded(),
                    )
                }
                repository = prepared.repository
                state.value = state.value.copy(
                    ayahs = prepared.ayahs, metadata = prepared.metadata, selectedSurah = prepared.selectedSurah, currentPage = 0,
                    totalAyahsInSurah = prepared.totalAyahs,
                    loadingAssets = false, assetsReady = true,
                    downloadedAyahs = prepared.downloadedAyahs,
                    modelReady = prepared.modelReady,
                    notice = if (mode == AssetMode.STREAM) "البث المباشر فعال؛ النص والتوقيتات في الذاكرة فقط." else "وضع التنزيل فعال؛ الصوت يُحفظ فقط عندما تختار تنزيله.",
                )
            } catch (error: Throwable) {
                state.value = state.value.copy(loadingAssets = false, assetsReady = false, notice = "تعذر تحميل الأصول من المستودع العام: ${error.message ?: "خطأ شبكي"}")
            }
        }
    }

    fun selectSurah(surah: Int) {
        val surahAyahs = repository.ayahsForSurah(surah)
        if (surahAyahs.isEmpty()) return
        state.value = state.value.copy(ayahs = surahAyahs, selectedSurah = surah, currentPage = 0, totalAyahsInSurah = surahAyahs.size, downloadedAyahs = assetManager.downloadedAyahCount(surah, surahAyahs.size), hidden = false, transcript = "", evaluation = null, currentWordIndex = -1, currentSpokenWord = null, currentSpokenWordIndex = -1, recitationCursor = 0, notice = "تم اختيار السورة $surah من المستودع العام.")
    }

    fun nextPage() {
        val next = state.value.currentPage + 1
        if (next * AYAH_PAGE_SIZE >= state.value.totalAyahsInSurah) return
        state.value = state.value.copy(ayahs = page(repository.ayahsForSurah(state.value.selectedSurah), next), currentPage = next, hidden = false, transcript = "", evaluation = null, currentWordIndex = -1, currentSpokenWord = null, currentSpokenWordIndex = -1, recitationCursor = 0)
    }

    fun previousPage() {
        val previous = (state.value.currentPage - 1).coerceAtLeast(0)
        state.value = state.value.copy(ayahs = page(repository.ayahsForSurah(state.value.selectedSurah), previous), currentPage = previous, hidden = false, transcript = "", evaluation = null, currentWordIndex = -1, currentSpokenWord = null, currentSpokenWordIndex = -1, recitationCursor = 0)
    }

    fun downloadCurrentSurah() {
        if (state.value.mode != AssetMode.DOWNLOAD || state.value.downloadInProgress) return
        val surah = state.value.selectedSurah
        val ayahCount = state.value.totalAyahsInSurah
        state.value = state.value.copy(downloadInProgress = true, notice = "جاري تنزيل صوت السورة $surah إلى التخزين المحلي…")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                (1..ayahCount).forEach { ayah ->
                    assetManager.downloadAyah(surah, ayah)
                    withContext(Dispatchers.Main) { state.value = state.value.copy(downloadedAyahs = assetManager.downloadedAyahCount(surah, ayahCount)) }
                }
                withContext(Dispatchers.Main) { state.value = state.value.copy(downloadInProgress = false, notice = "اكتمل تنزيل السورة $surah. يمكن تشغيلها لاحقاً دون شبكة.") }
            } catch (error: Throwable) {
                withContext(Dispatchers.Main) { state.value = state.value.copy(downloadInProgress = false, notice = "توقف التنزيل، ويمكن استئنافه: ${error.message ?: "خطأ شبكي"}") }
            }
        }
    }

    fun deleteCurrentSurah() {
        val surah = state.value.selectedSurah
        assetManager.deleteSurah(surah, state.value.totalAyahsInSurah)
        state.value = state.value.copy(downloadedAyahs = 0, notice = "حُذفت ملفات صوت السورة $surah من التخزين المحلي.")
    }

    fun toggleHidden() { state.value = state.value.copy(hidden = !state.value.hidden) }

    fun startListening() {
        if (!state.value.assetsReady) { state.value = state.value.copy(notice = "انتظر اكتمال تحميل النص والتوقيتات أولاً."); return }
        if (state.value.engine == RecitationEngine.OFFLINE && !state.value.offlineSupported) {
            state.value = state.value.copy(listening = false, notice = offlineUnavailableMessage())
            return
        }
        if (state.value.engine == RecitationEngine.OFFLINE && !state.value.modelReady) { state.value = state.value.copy(notice = "نزّل نموذج FastConformer أولاً لتفعيل التسميع Offline."); return }
        if (state.value.listening || state.value.correctionPlaying) return
        progressTracker = RecitationProgressTracker(state.value.ayahs, correctionSettings = state.value.correctionSettings)
        activeCorrection = null
        correctionWasListening = false
        state.value = state.value.copy(
            listening = true,
            transcript = "",
            evaluation = null,
            currentWordIndex = -1,
            currentSpokenWord = null,
            currentSpokenWordIndex = -1,
            currentAyahIndex = 0,
            currentWordInAyah = 0,
            recitationCursor = 0,
            correctionPlaying = false,
            correctionPhase = CorrectionPhase.LISTENING,
            correctionMessage = null,
            correctionAyahIndex = -1,
            correctionFirstWordInAyah = -1,
            notice = if (state.value.engine == RecitationEngine.ONLINE) "جارٍ التعرف عبر الإنترنت؛ لا يُحفظ صوتك في التطبيق." else "جارٍ التعرف محلياً؛ لا يغادر الصوت جهازك.",
        )
        startRecognitionSession()
    }

    private fun startRecognitionSession() {
        val generation = ++recognitionGeneration
        val guardedUpdate: (RecognitionUpdate) -> Unit = { update ->
            if (generation == recognitionGeneration) handleRecognitionUpdate(update)
        }
        if (state.value.engine == RecitationEngine.ONLINE) {
            onlineSession = OnlineSpeechSession(getApplication(), guardedUpdate)
            onlineSession?.start()
        } else {
            localSession = LocalRecitationSession(getApplication(), assetManager.modelDirectory(), guardedUpdate)
            localSession?.start()
        }
    }

    fun startFromPreviousAyah() {
        val currentAyah = state.value.currentAyahIndex
        if (!state.value.assetsReady) {
            state.value = state.value.copy(notice = "انتظر اكتمال تحميل النص والتوقيتات أولاً.")
            return
        }
        if (currentAyah <= 0 || currentAyah !in state.value.ayahs.indices || state.value.correctionPlaying) return
        val previousAyahIndex = currentAyah - 1
        referenceAudioPlayer.stop()
        recognitionGeneration++
        onlineSession?.stop(); onlineSession = null
        localSession?.stop(); localSession = null
        val tracker = RecitationProgressTracker(state.value.ayahs, correctionSettings = state.value.correctionSettings)
        tracker.startFromAyah(previousAyahIndex)
        progressTracker = tracker
        activeCorrection = null
        correctionWasListening = false
        state.value = state.value.copy(
            listening = true,
            transcript = "",
            evaluation = null,
            currentWordIndex = tracker.globalWordIndex(previousAyahIndex, 0) ?: -1,
            currentSpokenWord = null,
            currentSpokenWordIndex = -1,
            currentAyahIndex = previousAyahIndex,
            currentWordInAyah = 0,
            recitationCursor = tracker.globalWordIndex(previousAyahIndex, 0) ?: 0,
            correctionPlaying = false,
            correctionPhase = CorrectionPhase.LISTENING,
            correctionMessage = null,
            correctionAyahIndex = -1,
            correctionFirstWordInAyah = -1,
            notice = "بدأت جلسة جديدة من الآية ${state.value.ayahs[previousAyahIndex].ayah}.",
        )
        startRecognitionSession()
    }

    private fun handleRecognitionUpdate(update: RecognitionUpdate) {
        if (state.value.correctionPlaying && update !is RecognitionUpdate.Started) return
        when (update) {
            RecognitionUpdate.Started -> Unit
            is RecognitionUpdate.Text -> {
                val previousState = state.value
                val tracker = progressTracker ?: RecitationProgressTracker(previousState.ayahs, correctionSettings = previousState.correctionSettings).also { progressTracker = it }
                val result = tracker.update(
                    RecitationProgressInput(
                        transcript = update.value,
                        isFinal = update.isFinal,
                        segmentCompleted = update.segmentCompleted,
                    ),
                )
                val correction = result.correction
                val currentTarget = correction?.let { tracker.globalWordIndex(it.ayahIndex, it.localWordIndex) }
                    ?: result.currentGlobalWordIndex.takeIf { it < targetWords.size }
                state.value = previousState.copy(
                    transcript = update.value,
                    evaluation = result.evaluation,
                    currentWordIndex = currentTarget ?: -1,
                    currentSpokenWord = result.spokenWord,
                    currentSpokenWordIndex = result.spokenGlobalWordIndex,
                    currentAyahIndex = result.currentAyahIndex,
                    currentWordInAyah = result.currentWordInAyah,
                    recitationCursor = result.currentGlobalWordIndex.coerceIn(0, targetWords.size),
                    correctionMessage = correction?.let(::correctionMessage) ?: previousState.correctionMessage,
                    correctionAyahIndex = correction?.ayahIndex ?: previousState.correctionAyahIndex,
                    correctionFirstWordInAyah = correction?.localWordIndex ?: previousState.correctionFirstWordInAyah,
                    notice = if (previousState.engine == RecitationEngine.ONLINE) "يتم التحقق عبر الإنترنت؛ الصوت لا يُخزن محلياً." else "يتم التحقق محلياً؛ لا يغادر الصوت جهازك.",
                )
                if (correction != null && (update.isFinal || update.segmentCompleted) && activeCorrection == null) {
                    activeCorrection = correction
                    correctionWasListening = previousState.listening
                    pauseRecognitionForCorrection(correction)
                    playCorrection(correction)
                }
            }
            is RecognitionUpdate.Failed -> {
                if (activeCorrection != null) return
                progressTracker = null
                state.value = state.value.copy(listening = false, notice = update.message)
            }
            RecognitionUpdate.Stopped -> {
                if (activeCorrection != null) return
                progressTracker = null
                state.value = state.value.copy(listening = false, notice = "انتهت الجلسة. راجع المواضع غير المؤكدة قبل اعتماد النتيجة.")
            }
        }
    }

    private fun pauseRecognitionForCorrection(request: CorrectionRequest) {
        recognitionGeneration++
        state.value = state.value.copy(
            listening = false,
            correctionPlaying = true,
            correctionPhase = CorrectionPhase.PLAYING_FIRST_ERROR,
            correctionMessage = correctionMessage(request),
            notice = when {
                request.readWholeAyah && state.value.correctionSettings.playFirstErrorBeforeWholeAyah -> "سيُقرأ مقطع أول خطأ ثم الآية كاملة…"
                request.readWholeAyah -> "ستُقرأ الآية كاملة للتصحيح…"
                else -> "سيُقرأ مقطع الكلمة فقط…"
            },
        )
        onlineSession?.stop(); onlineSession = null
        localSession?.stop(); localSession = null
    }

    fun playCorrectionForCurrentWord() {
        val globalWordIndex = state.value.currentWordIndex
        if (globalWordIndex < 0 || activeCorrection != null) return
        val location = locateWord(globalWordIndex) ?: return
        val ayahIndex = state.value.ayahs.indexOfFirst { it.surah == location.first.surah && it.ayah == location.first.ayah }
        if (ayahIndex < 0) return
        val request = CorrectionRequest(
            CorrectionKind.WRONG_WORD,
            ayahIndex,
            location.second,
            null,
            "manual:$globalWordIndex",
            readWholeAyah = false,
        )
        activeCorrection = request
        correctionWasListening = state.value.listening
        if (correctionWasListening) pauseRecognitionForCorrection(request)
        playCorrection(request)
    }

    private fun playCorrection(request: CorrectionRequest) {
        val ayah = state.value.ayahs.getOrNull(request.ayahIndex) ?: return finishCorrectionPlayback()
        val timings = timingRepository.forAyah(ayah.surah, ayah.ayah)
        val firstWord = timings.firstOrNull { request.localWordIndex in it.wordStart until it.wordEnd }
            ?: return finishCorrectionPlayback()
        val wholeAyah = if (request.readWholeAyah) {
            timings.firstOrNull()?.let { start ->
                timings.lastOrNull()?.let { end -> TimingSegment(0, ayah.words.size, start.startMs, end.endMs) }
            }
        } else null
        val localFile = assetManager.cachedAudioFile(ayah.surah, ayah.ayah)
        if (state.value.mode == AssetMode.DOWNLOAD && localFile == null) {
            finishCorrectionPlayback()
            state.value = state.value.copy(notice = "نزّل صوت السورة الحالية أولاً، أو اختر وضع البث المباشر للتشغيل دون حفظ.")
            return
        }
        state.value = state.value.copy(
            correctionPlaying = true,
            correctionPhase = CorrectionPhase.PLAYING_FIRST_ERROR,
            correctionMessage = correctionMessage(request),
            notice = when {
                request.readWholeAyah && state.value.correctionSettings.playFirstErrorBeforeWholeAyah -> "يُشغّل مقطع أول خطأ ثم الآية كاملة ${if (localFile != null) "من التخزين المحلي" else "بثاً دون حفظ"}…"
                request.readWholeAyah -> "تُشغّل الآية كاملة ${if (localFile != null) "من التخزين المحلي" else "بثاً دون حفظ"}…"
                else -> "يُشغّل مقطع الكلمة ${if (localFile != null) "من التخزين المحلي" else "بثاً دون حفظ"}…"
            },
        )
        val finished = { finishCorrectionPlayback() }
        val source = localFile?.absolutePath ?: assetManager.audioUrl(ayah.surah, ayah.ayah)
        val wholeStarted = { state.value = state.value.copy(correctionPhase = CorrectionPhase.PLAYING_WHOLE_AYAH, notice = "تُقرأ الآية كاملة للتصحيح…") }
        referenceAudioPlayer.playCorrection(
            source = source,
            firstWord = firstWord,
            wholeAyah = wholeAyah,
            settings = state.value.correctionSettings,
            onWholeAyahStarted = wholeStarted,
            onFinished = finished,
        )
    }

    private fun finishCorrectionPlayback() {
        val request = activeCorrection
        activeCorrection = null
        val shouldResume = correctionWasListening
        correctionWasListening = false
        if (shouldResume && request != null) {
            progressTracker?.resumeFrom(request.ayahIndex, resumeAnchorFor(request))
            state.value = state.value.copy(
                listening = true,
                correctionPlaying = false,
                correctionPhase = CorrectionPhase.WAITING_RESUME,
                currentAyahIndex = request.ayahIndex,
                currentWordInAyah = request.localWordIndex,
                currentWordIndex = trackerGlobalIndex(request),
                notice = "انتهى التصحيح؛ ابدأ من أول الآية أو من أي كلمة داخلها.",
            )
            startRecognitionSession()
        } else {
            state.value = state.value.copy(listening = false, correctionPlaying = false, correctionPhase = CorrectionPhase.LISTENING)
        }
    }

    private fun trackerGlobalIndex(request: CorrectionRequest): Int =
        progressTracker?.globalWordIndex(request.ayahIndex, resumeAnchorFor(request)) ?: -1

    private fun resumeAnchorFor(request: CorrectionRequest): Int = when (state.value.correctionSettings.resumePosition) {
        ResumePosition.AYAH_START -> 0
        ResumePosition.FIRST_ERROR -> request.localWordIndex
        ResumePosition.WORD_AFTER_ERROR -> request.localWordIndex + 1
    }

    private fun correctionMessage(request: CorrectionRequest): String {
        val ayah = state.value.ayahs.getOrNull(request.ayahIndex)
        val word = ayah?.words?.getOrNull(request.localWordIndex)?.text.orEmpty()
        val settings = state.value.correctionSettings
        return when (request.kind) {
            CorrectionKind.WRONG_WORD -> correctionText("الكلمة تحتاج تصحيحاً", word, request.readWholeAyah, settings)
            CorrectionKind.SKIPPED_WORD -> correctionText("الكلمة المتخطاة", word, request.readWholeAyah, settings)
            CorrectionKind.FIRST_WORD_EXPECTED_AYAH -> correctionText("الآية المتوقعة تبدأ من", word, true, settings)
        }
    }

    private fun correctionText(prefix: String, word: String, wholeAyah: Boolean, settings: CorrectionSettings): String {
        val suffix = when {
            !wholeAyah -> "سيُقرأ مقطع الكلمة فقط."
            settings.playFirstErrorBeforeWholeAyah -> "سيُقرأ مقطع أول خطأ ثم الآية كاملة."
            else -> "ستُقرأ الآية كاملة."
        }
        return "$prefix: $word؛ $suffix"
    }

    private fun locateWord(globalWordIndex: Int): Pair<QuranAyah, Int>? {
        var remaining = globalWordIndex
        state.value.ayahs.forEach { ayah ->
            if (remaining < ayah.words.size) return ayah to remaining
            remaining -= ayah.words.size
        }
        return null
    }

    fun stopCorrection() {
        if (!state.value.correctionPlaying) return
        referenceAudioPlayer.stop()
        finishCorrectionPlayback()
    }

    fun stopListening() {
        recognitionGeneration++
        referenceAudioPlayer.stop()
        onlineSession?.stop(); onlineSession = null
        localSession?.stop(); localSession = null
        progressTracker = null
        activeCorrection = null
        correctionWasListening = false
        state.value = state.value.copy(listening = false, correctionPlaying = false, correctionPhase = CorrectionPhase.LISTENING)
    }

    fun saveCurrentSession() {
        val evaluation = state.value.evaluation ?: return
        val summary = SessionSummary(java.util.UUID.randomUUID().toString(), state.value.ayahs.firstOrNull()?.surah ?: 1, state.value.ayahs.firstOrNull()?.ayah ?: 1, state.value.ayahs.lastOrNull()?.ayah ?: 1, evaluation.matched, evaluation.total, evaluation.uncertain, System.currentTimeMillis())
        store.append(summary); goalStore.addCompleted(1)
        state.value = state.value.copy(sessions = store.list(), goal = goalStore.current(), notice = "حُفظت الجلسة محلياً على الجهاز.")
    }

    fun setDailyGoal(targetAyahs: Int) { goalStore.setTarget(targetAyahs); state.value = state.value.copy(goal = goalStore.current(), notice = "تم تحديث الهدف اليومي محلياً.") }

    fun updateCorrectionSettings(transform: (CorrectionSettings) -> CorrectionSettings) {
        val updated = transform(state.value.correctionSettings).normalized()
        correctionSettingsStore.save(updated)
        progressTracker?.updateSettings(updated)
        state.value = state.value.copy(correctionSettings = updated, notice = "تم حفظ إعدادات التصحيح على هذا الجهاز.")
    }

    fun resetCorrectionSettings() {
        val defaults = correctionSettingsStore.reset()
        progressTracker?.updateSettings(defaults)
        state.value = state.value.copy(correctionSettings = defaults, notice = "أعيدت إعدادات التصحيح إلى الوضع الافتراضي الحالي.")
    }

    fun exportBackup(uri: android.net.Uri) {
        try {
            LocalBackup.write(
                getApplication(),
                uri,
                store.list(),
                goalStore.current(),
                state.value.correctionSettings,
            )
            state.value = state.value.copy(notice = "تم تصدير النسخة الاحتياطية وإعدادات التصحيح إلى الملف الذي اخترته.")
        }
        catch (error: Throwable) { state.value = state.value.copy(notice = "تعذر تصدير النسخة الاحتياطية: ${error.message ?: "خطأ غير معروف"}") }
    }

    fun clearSessions() { store.clear(); state.value = state.value.copy(sessions = emptyList(), notice = "حُذف سجل الجلسات المحلي.") }

    override fun onCleared() { recognitionGeneration++; onlineSession?.stop(); localSession?.stop(); progressTracker = null; referenceAudioPlayer.stop(); super.onCleared() }

    private fun offlineUnavailableMessage(): String = "التسميع Offline غير متاح بأمان على هذا الهاتف؛ النظام يعمل 32-bit، وظهر انهيار native داخل محرك FastConformer. استخدم Online أو هاتفاً 64-bit."

    private fun page(ayahs: List<QuranAyah>, page: Int): List<QuranAyah> = ayahs.drop(page * AYAH_PAGE_SIZE).take(AYAH_PAGE_SIZE)
}
