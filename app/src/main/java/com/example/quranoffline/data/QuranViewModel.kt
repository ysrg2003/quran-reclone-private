package com.example.quranoffline.data

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val AYAH_PAGE_SIZE = 8

enum class AssetMode { STREAM, DOWNLOAD }

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
    val correctionPlaying: Boolean = false,
    val selectedSurah: Int = 1,
    val currentPage: Int = 0,
    val totalAyahsInSurah: Int = 7,
    val mode: AssetMode = AssetMode.STREAM,
    val downloadInProgress: Boolean = false,
    val downloadedAyahs: Int = 0,
)

class QuranViewModel(application: Application) : AndroidViewModel(application) {
    private val assetManager = RemoteAssetManager(application)
    private var repository = QuranRepository(application)
    private val store = LocalSessionStore(application)
    private val goalStore = LocalGoalStore(application)
    private val timingRepository = ReferenceTimingRepository()
    private val referenceAudioPlayer = ReferenceAudioPlayer()
    private var recitationSession: OnlineSpeechSession? = null
    private var lastCorrectionWord = -1

    private val state = mutableStateOf(
        QuranUiState(
            sessions = store.list(),
            goal = goalStore.current(),
        )
    )

    val uiState: State<QuranUiState> = state

    private val targetWords: List<QuranWord>
        get() = state.value.ayahs.flatMap { it.words }

    init {
        loadAssets(AssetMode.STREAM)
    }

    fun setMode(mode: AssetMode) {
        if (mode == state.value.mode && state.value.assetsReady) return
        stopListening()
        state.value = state.value.copy(mode = mode, loadingAssets = true, assetsReady = false, notice = "جاري تجهيز وضع ${if (mode == AssetMode.STREAM) "البث المباشر" else "التنزيل المحلي"}…")
        loadAssets(mode)
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
                repository = QuranRepository(getApplication(), text)
                timingRepository.update(timings)
                val firstSurah = repository.ayahsForSurah(state.value.selectedSurah).ifEmpty { repository.ayahsForSurah(1) }
                val selectedSurah = firstSurah.firstOrNull()?.surah ?: 1
                state.value = state.value.copy(
                    ayahs = page(firstSurah, 0),
                    selectedSurah = selectedSurah,
                    currentPage = 0,
                    totalAyahsInSurah = repository.ayahsForSurah(selectedSurah).size,
                    loadingAssets = false,
                    assetsReady = true,
                    downloadedAyahs = assetManager.downloadedAyahCount(selectedSurah, repository.ayahsForSurah(selectedSurah).size),
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
        state.value = state.value.copy(
            ayahs = page(surahAyahs, 0),
            selectedSurah = surah,
            currentPage = 0,
            totalAyahsInSurah = surahAyahs.size,
            downloadedAyahs = assetManager.downloadedAyahCount(surah, surahAyahs.size),
            hidden = false,
            transcript = "",
            evaluation = null,
            currentWordIndex = -1,
            notice = "تم اختيار السورة $surah من المستودع العام.",
        )
    }

    fun nextPage() {
        val next = state.value.currentPage + 1
        if (next * AYAH_PAGE_SIZE >= state.value.totalAyahsInSurah) return
        state.value = state.value.copy(ayahs = page(repository.ayahsForSurah(state.value.selectedSurah), next), currentPage = next, hidden = false, transcript = "", evaluation = null, currentWordIndex = -1)
    }

    fun previousPage() {
        val previous = (state.value.currentPage - 1).coerceAtLeast(0)
        state.value = state.value.copy(ayahs = page(repository.ayahsForSurah(state.value.selectedSurah), previous), currentPage = previous, hidden = false, transcript = "", evaluation = null, currentWordIndex = -1)
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
                    withContext(Dispatchers.Main) {
                        state.value = state.value.copy(downloadedAyahs = assetManager.downloadedAyahCount(surah, ayahCount))
                    }
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
        if (!state.value.assetsReady) {
            state.value = state.value.copy(notice = "انتظر اكتمال تحميل النص والتوقيتات أولاً.")
            return
        }
        if (state.value.listening) return
        state.value = state.value.copy(listening = true, transcript = "", evaluation = null, notice = "جارٍ التعرف عبر الإنترنت؛ لا يتم حفظ صوتك في التطبيق.")
        recitationSession = OnlineSpeechSession(getApplication()) { update ->
            when (update) {
                RecognitionUpdate.Started -> Unit
                is RecognitionUpdate.Text -> {
                    val evaluation = RecitationMatcher.evaluate(targetWords, update.value)
                    val firstProblem = evaluation.words.indexOfFirst { it.status != WordStatus.MATCHED }
                    state.value = state.value.copy(transcript = update.value, evaluation = evaluation, currentWordIndex = firstProblem, notice = "يتم التحقق عبر الإنترنت؛ الصوت لا يُخزن محلياً.")
                    if (firstProblem >= 0 && evaluation.words[firstProblem].status == WordStatus.EXTRA_OR_UNEXPECTED && lastCorrectionWord != firstProblem) {
                        lastCorrectionWord = firstProblem
                        playCorrectionForCurrentWord()
                    }
                }
                is RecognitionUpdate.Failed -> state.value = state.value.copy(listening = false, notice = update.message)
                RecognitionUpdate.Stopped -> state.value = state.value.copy(listening = false, notice = "انتهت الجلسة. راجع المواضع غير المؤكدة قبل اعتماد النتيجة.")
            }
        }
        recitationSession?.start()
    }

    fun playCorrectionForCurrentWord() {
        val wordIndex = state.value.currentWordIndex
        if (wordIndex < 0) return
        val location = locateWord(wordIndex) ?: return
        val (ayah, localWordIndex) = location
        val segment = timingRepository.forAyah(ayah.surah, ayah.ayah).firstOrNull { localWordIndex in it.wordStart until it.wordEnd } ?: return
        val localFile = assetManager.cachedAudioFile(ayah.surah, ayah.ayah)
        if (state.value.mode == AssetMode.DOWNLOAD && localFile == null) {
            state.value = state.value.copy(notice = "نزّل صوت السورة الحالية أولاً، أو اختر وضع البث المباشر للتشغيل دون حفظ.")
            return
        }
        state.value = state.value.copy(correctionPlaying = true, notice = "يُشغّل المقطع الصحيح ${if (localFile != null) "من التخزين المحلي" else "بثاً دون حفظ"}…")
        val finished = { state.value = state.value.copy(correctionPlaying = false) }
        if (localFile != null) referenceAudioPlayer.playLocalFile(localFile.absolutePath, segment.startMs, segment.endMs, finished)
        else referenceAudioPlayer.playRemoteUrl(assetManager.audioUrl(ayah.surah, ayah.ayah), segment.startMs, segment.endMs, finished)
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

    fun stopListening() { recitationSession?.stop(); recitationSession = null; state.value = state.value.copy(listening = false) }

    fun saveCurrentSession() {
        val evaluation = state.value.evaluation ?: return
        val summary = SessionSummary(java.util.UUID.randomUUID().toString(), state.value.ayahs.firstOrNull()?.surah ?: 1, state.value.ayahs.firstOrNull()?.ayah ?: 1, state.value.ayahs.lastOrNull()?.ayah ?: 1, evaluation.matched, evaluation.total, evaluation.uncertain, System.currentTimeMillis())
        store.append(summary)
        goalStore.addCompleted(1)
        state.value = state.value.copy(sessions = store.list(), goal = goalStore.current(), notice = "حُفظت الجلسة محلياً على الجهاز.")
    }

    fun setDailyGoal(targetAyahs: Int) { goalStore.setTarget(targetAyahs); state.value = state.value.copy(goal = goalStore.current(), notice = "تم تحديث الهدف اليومي محلياً.") }

    fun exportBackup(uri: android.net.Uri) {
        try { LocalBackup.write(getApplication(), uri, store.list(), goalStore.current()); state.value = state.value.copy(notice = "تم تصدير النسخة الاحتياطية إلى الملف الذي اخترته.") }
        catch (error: Throwable) { state.value = state.value.copy(notice = "تعذر تصدير النسخة الاحتياطية: ${error.message ?: "خطأ غير معروف"}") }
    }

    fun clearSessions() { store.clear(); state.value = state.value.copy(sessions = emptyList(), notice = "حُذف سجل الجلسات المحلي.") }

    override fun onCleared() { recitationSession?.stop(); referenceAudioPlayer.stop(); super.onCleared() }

    private fun page(ayahs: List<QuranAyah>, page: Int): List<QuranAyah> = ayahs.drop(page * AYAH_PAGE_SIZE).take(AYAH_PAGE_SIZE)
}
