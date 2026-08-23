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
    val correctionMessage: String? = null,
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
    val ayahs: List<QuranAyah>,
    val selectedSurah: Int,
    val totalAyahs: Int,
    val downloadedAyahs: Int,
    val modelReady: Boolean,
)

class QuranViewModel(application: Application) : AndroidViewModel(application) {
    private val assetManager = RemoteAssetManager(application)
    private var repository = QuranRepository(application)
    private val store = LocalSessionStore(application)
    private val goalStore = LocalGoalStore(application)
    private val timingRepository = ReferenceTimingRepository()
    private val referenceAudioPlayer = ReferenceAudioPlayer()
    private var onlineSession: OnlineSpeechSession? = null
    private var localSession: LocalRecitationSession? = null
    private var progressTracker: RecitationProgressTracker? = null

    private val state = mutableStateOf(
        QuranUiState(
            sessions = store.list(),
            goal = goalStore.current(),
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
                    timingRepository.update(timings)
                    val requested = parsedRepository.ayahsForSurah(requestedSurah).ifEmpty { parsedRepository.ayahsForSurah(1) }
                    val selectedSurah = requested.firstOrNull()?.surah ?: 1
                    val totalAyahs = parsedRepository.ayahsForSurah(selectedSurah).size
                    PreparedAssets(
                        repository = parsedRepository,
                        ayahs = requested,
                        selectedSurah = selectedSurah,
                        totalAyahs = totalAyahs,
                        downloadedAyahs = assetManager.downloadedAyahCount(selectedSurah, totalAyahs),
                        modelReady = assetManager.isModelDownloaded(),
                    )
                }
                repository = prepared.repository
                state.value = state.value.copy(
                    ayahs = prepared.ayahs, selectedSurah = prepared.selectedSurah, currentPage = 0,
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
        if (state.value.listening) return
        progressTracker = RecitationProgressTracker(state.value.ayahs)
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
            correctionMessage = null,
            notice = if (state.value.engine == RecitationEngine.ONLINE) "جارٍ التعرف عبر الإنترنت؛ لا يُحفظ صوتك في التطبيق." else "جارٍ التعرف محلياً؛ لا يغادر الصوت جهازك.",
        )
        if (state.value.engine == RecitationEngine.ONLINE) {
            onlineSession = OnlineSpeechSession(getApplication(), ::handleRecognitionUpdate)
            onlineSession?.start()
        } else {
            localSession = LocalRecitationSession(getApplication(), assetManager.modelDirectory(), ::handleRecognitionUpdate)
            localSession?.start()
        }
    }

    private fun handleRecognitionUpdate(update: RecognitionUpdate) {
        when (update) {
            RecognitionUpdate.Started -> Unit
            is RecognitionUpdate.Text -> {
                val previousState = state.value
                val tracker = progressTracker ?: RecitationProgressTracker(previousState.ayahs).also { progressTracker = it }
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
                    correctionMessage = correction?.let(::correctionMessage),
                    notice = if (previousState.engine == RecitationEngine.ONLINE) "يتم التحقق عبر الإنترنت؛ الصوت لا يُخزن محلياً." else "يتم التحقق محلياً؛ لا يغادر الصوت جهازك.",
                )
                if (correction != null && (update.isFinal || update.segmentCompleted)) {
                    playCorrection(correction)
                }
            }
            is RecognitionUpdate.Failed -> {
                progressTracker = null
                state.value = state.value.copy(listening = false, notice = update.message)
            }
            RecognitionUpdate.Stopped -> {
                progressTracker = null
                state.value = state.value.copy(listening = false, notice = "انتهت الجلسة. راجع المواضع غير المؤكدة قبل اعتماد النتيجة.")
            }
        }
    }

    fun playCorrectionForCurrentWord() {
        val globalWordIndex = state.value.currentWordIndex
        if (globalWordIndex < 0) return
        val location = locateWord(globalWordIndex) ?: return
        val ayahIndex = state.value.ayahs.indexOfFirst { it.surah == location.first.surah && it.ayah == location.first.ayah }
        if (ayahIndex < 0) return
        playCorrection(CorrectionRequest(CorrectionKind.WRONG_WORD, ayahIndex, location.second, null, "manual:$globalWordIndex"))
    }

    private fun playCorrection(request: CorrectionRequest) {
        val ayah = state.value.ayahs.getOrNull(request.ayahIndex) ?: return
        val localWordIndex = request.localWordIndex
        val segment = timingRepository.forAyah(ayah.surah, ayah.ayah).firstOrNull { localWordIndex in it.wordStart until it.wordEnd } ?: return
        val localFile = assetManager.cachedAudioFile(ayah.surah, ayah.ayah)
        if (state.value.mode == AssetMode.DOWNLOAD && localFile == null) {
            state.value = state.value.copy(notice = "نزّل صوت السورة الحالية أولاً، أو اختر وضع البث المباشر للتشغيل دون حفظ.")
            return
        }
        state.value = state.value.copy(
            correctionPlaying = true,
            correctionMessage = correctionMessage(request),
            notice = "يُشغّل المقطع الصحيح ${if (localFile != null) "من التخزين المحلي" else "بثاً دون حفظ"}…",
        )
        val finished = { state.value = state.value.copy(correctionPlaying = false) }
        if (localFile != null) referenceAudioPlayer.playLocalFile(localFile.absolutePath, segment.startMs, segment.endMs, finished)
        else referenceAudioPlayer.playRemoteUrl(assetManager.audioUrl(ayah.surah, ayah.ayah), segment.startMs, segment.endMs, finished)
    }

    private fun correctionMessage(request: CorrectionRequest): String {
        val ayah = state.value.ayahs.getOrNull(request.ayahIndex)
        val word = ayah?.words?.getOrNull(request.localWordIndex)?.text.orEmpty()
        return when (request.kind) {
            CorrectionKind.WRONG_WORD -> "الكلمة تحتاج تصحيحاً: $word"
            CorrectionKind.SKIPPED_WORD -> "تخطيت كلمة؛ أعد: $word"
            CorrectionKind.FIRST_WORD_EXPECTED_AYAH -> "انتقلت إلى آية أخرى؛ ابدأ بالآية التالية من: $word"
        }
    }

    private fun locateWord(globalWordIndex: Int): Pair<QuranAyah, Int>? {
        var remaining = globalWordIndex
        state.value.ayahs.forEach { ayah ->
            if (remaining < ayah.words.size) return ayah to remaining
            remaining -= ayah.words.size
        }
        return null
    }

    fun stopCorrection() { referenceAudioPlayer.stop(); state.value = state.value.copy(correctionPlaying = false) }

    fun stopListening() {
        onlineSession?.stop(); onlineSession = null
        localSession?.stop(); localSession = null
        progressTracker = null
        state.value = state.value.copy(listening = false)
    }

    fun saveCurrentSession() {
        val evaluation = state.value.evaluation ?: return
        val summary = SessionSummary(java.util.UUID.randomUUID().toString(), state.value.ayahs.firstOrNull()?.surah ?: 1, state.value.ayahs.firstOrNull()?.ayah ?: 1, state.value.ayahs.lastOrNull()?.ayah ?: 1, evaluation.matched, evaluation.total, evaluation.uncertain, System.currentTimeMillis())
        store.append(summary); goalStore.addCompleted(1)
        state.value = state.value.copy(sessions = store.list(), goal = goalStore.current(), notice = "حُفظت الجلسة محلياً على الجهاز.")
    }

    fun setDailyGoal(targetAyahs: Int) { goalStore.setTarget(targetAyahs); state.value = state.value.copy(goal = goalStore.current(), notice = "تم تحديث الهدف اليومي محلياً.") }

    fun exportBackup(uri: android.net.Uri) {
        try { LocalBackup.write(getApplication(), uri, store.list(), goalStore.current()); state.value = state.value.copy(notice = "تم تصدير النسخة الاحتياطية إلى الملف الذي اخترته.") }
        catch (error: Throwable) { state.value = state.value.copy(notice = "تعذر تصدير النسخة الاحتياطية: ${error.message ?: "خطأ غير معروف"}") }
    }

    fun clearSessions() { store.clear(); state.value = state.value.copy(sessions = emptyList(), notice = "حُذف سجل الجلسات المحلي.") }

    override fun onCleared() { onlineSession?.stop(); localSession?.stop(); progressTracker = null; referenceAudioPlayer.stop(); super.onCleared() }

    private fun offlineUnavailableMessage(): String = "التسميع Offline غير متاح بأمان على هذا الهاتف؛ النظام يعمل 32-bit، وظهر انهيار native داخل محرك FastConformer. استخدم Online أو هاتفاً 64-bit."

    private fun page(ayahs: List<QuranAyah>, page: Int): List<QuranAyah> = ayahs.drop(page * AYAH_PAGE_SIZE).take(AYAH_PAGE_SIZE)
}
