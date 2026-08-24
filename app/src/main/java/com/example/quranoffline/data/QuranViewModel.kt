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

enum class AssetMode { STREAM, DOWNLOAD }
enum class RecitationEngine { ONLINE, OFFLINE }
enum class ReaderScope { AYAH, SURAH, JUZ, PAGES, QURAN }

data class QuranUiState(
    val ayahs: List<QuranAyah> = emptyList(),
    val allAyahs: List<QuranAyah> = emptyList(),
    val metadata: QuranMetadata? = null,
    val pageLayout: QuranLayout? = null,
    val hidden: Boolean = false,
    val listening: Boolean = false,
    val assetsReady: Boolean = false,
    val loadingAssets: Boolean = true,
    val transcript: String = "",
    val evaluation: RecitationEvaluation? = null,
    val notice: String = "جاري تحميل النص والتوقيتات من مستودع الأصول…",
    val sessions: List<SessionSummary> = emptyList(),
    val correctedErrors: List<CorrectedRecitationError> = emptyList(),
    val goal: DailyGoal = DailyGoal(targetAyahs = 3, completedAyahs = 0, day = ""),
    val checkpoint: MemorizationCheckpoint? = null,
    val currentWordIndex: Int = -1,
    val currentSpokenWord: String? = null,
    val currentSpokenWordIndex: Int = -1,
    val currentAyahIndex: Int = -1,
    val currentWordInAyah: Int = -1,
    val currentAyahLocked: Boolean = false,
    val recitationCursor: Int = 0,
    val correctionPlaying: Boolean = false,
    val openingBasmalaActive: Boolean = false,
    val correctionPhase: CorrectionPhase = CorrectionPhase.LISTENING,
    val correctionMessage: String? = null,
    val correctionSettings: CorrectionSettings = CorrectionSettings.DEFAULTS,
    val readerPlaying: Boolean = false,
    val readerScope: ReaderScope = ReaderScope.AYAH,
    val readerCurrentKey: String? = null,
    val selectedAyahIndices: List<Int> = emptyList(),
    val correctionAyahIndex: Int = -1,
    val correctionFirstWordInAyah: Int = -1,
    val selectedSurah: Int = 1,
    val currentPage: Int = 1,
    val totalPages: Int = 604,
    val pageWordOffset: Int = 0,
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
    val pageLayout: QuranLayout,
    val allAyahs: List<QuranAyah>,
    val ayahs: List<QuranAyah>,
    val pageWordOffset: Int,
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
    private val correctedErrorStore = LocalRecitationErrorStore(application)
    private val goalStore = LocalGoalStore(application)
    private val memorizationProgressStore = LocalMemorizationProgressStore(application)
    private val correctionSettingsStore = LocalCorrectionSettingsStore(application)
    private val timingRepository = ReferenceTimingRepository()
    private val referenceAudioPlayer = ReferenceAudioPlayer()
    private var onlineSession: OnlineSpeechSession? = null
    private var localSession: LocalRecitationSession? = null
    private var progressTracker: RecitationProgressTracker? = null
    private var recognitionGeneration = 0L
    private var activeCorrection: CorrectionRequest? = null
    private var correctionWasListening = false
    private var openingBasmalaConsumed = true
    private var openingBasmalaNotice: String? = null
    private var openingBasmalaDetected = false
    private var openingBasmalaNeedsCorrection = false
    private var readerGeneration = 0L

    private val state = mutableStateOf(
        QuranUiState(
            sessions = store.list(),
            correctedErrors = correctedErrorStore.list(),
            goal = goalStore.current(),
            checkpoint = memorizationProgressStore.read(),
            correctionSettings = correctionSettingsStore.current(),
            offlineSupported = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(),
        ),
    )
    val uiState: State<QuranUiState> = state

    private val targetWords: List<QuranWord>
        get() = state.value.allAyahs.flatMap { it.words }

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
                val pageLayout = withContext(Dispatchers.IO) {
                    if (mode == AssetMode.DOWNLOAD) assetManager.loadOrDownloadPageLayout() else assetManager.fetchPageLayout()
                }
                val requestedSurah = state.value.selectedSurah
                val prepared = withContext(Dispatchers.Default) {
                    val parsedRepository = QuranRepository(getApplication(), text)
                    val parsedMetadata = metadataRepository.load()
                    parsedMetadata.requireCompatible(parsedRepository.allAyahs())
                    timingRepository.update(timings)
                    val parsedPageLayout = QuranLayout.fromJson(pageLayout)
                    val allAyahs = parsedRepository.allAyahs()
                    check(parsedPageLayout.pageCount == parsedMetadata.pages.size) { "Page layout page count does not match Quran metadata" }
                    check(parsedPageLayout.wordCount == allAyahs.sumOf { it.words.size }) { "Page layout word count does not match Quran text" }
                    val requestedSurahAyahs = parsedRepository.ayahsForSurah(requestedSurah).ifEmpty { parsedRepository.ayahsForSurah(1) }
                    val selectedSurah = requestedSurahAyahs.firstOrNull()?.surah ?: 1
                    val totalAyahs = parsedRepository.ayahsForSurah(selectedSurah).size
                    val initialPage = parsedMetadata.ayah(selectedSurah, 1)?.page ?: 1
                    val visiblePage = page(allAyahs, parsedMetadata, initialPage)
                    PreparedAssets(
                        repository = parsedRepository,
                        metadata = parsedMetadata,
                        pageLayout = parsedPageLayout,
                        allAyahs = allAyahs,
                        ayahs = visiblePage,
                        pageWordOffset = globalWordOffset(allAyahs, visiblePage.firstOrNull()),
                        selectedSurah = selectedSurah,
                        totalAyahs = totalAyahs,
                        downloadedAyahs = assetManager.downloadedAyahCount(selectedSurah, totalAyahs),
                        modelReady = assetManager.isModelDownloaded(),
                    )
                }
                repository = prepared.repository
                state.value = state.value.copy(
                    ayahs = prepared.ayahs, allAyahs = prepared.allAyahs, metadata = prepared.metadata, pageLayout = prepared.pageLayout, selectedSurah = prepared.selectedSurah,
                    currentPage = prepared.metadata.ayah(prepared.selectedSurah, 1)?.page ?: 1,
                    totalPages = prepared.metadata.pages.size,
                    pageWordOffset = prepared.pageWordOffset,
                    totalAyahsInSurah = prepared.totalAyahs,
                    loadingAssets = false, assetsReady = true,
                    downloadedAyahs = prepared.downloadedAyahs,
                    modelReady = prepared.modelReady,
                    checkpoint = memorizationProgressStore.read()?.validatedAgainst(prepared.allAyahs),
                    notice = if (mode == AssetMode.STREAM) "البث المباشر فعال؛ النص والتوقيتات في الذاكرة فقط." else "وضع التنزيل فعال؛ الصوت يُحفظ فقط عندما تختار تنزيله.",
                )
            } catch (error: Throwable) {
                state.value = state.value.copy(loadingAssets = false, assetsReady = false, notice = "تعذر تحميل الأصول من المستودع العام: ${error.message ?: "خطأ شبكي"}")
            }
        }
    }

    fun selectSurah(surah: Int) {
        val surahAyahs = repository.ayahsForSurah(surah)
        val metadata = state.value.metadata ?: return
        if (surahAyahs.isEmpty()) return
        if (state.value.listening || state.value.correctionPlaying) stopListening() else stopReader()
        val pageNumber = metadata.ayah(surah, 1)?.page ?: 1
        showPage(pageNumber, notice = "تم اختيار السورة ${metadata.sura(surah)?.arabicName ?: surah} من المصحف.")
        state.value = state.value.copy(
            selectedSurah = surah,
            totalAyahsInSurah = surahAyahs.size,
            downloadedAyahs = assetManager.downloadedAyahCount(surah, surahAyahs.size),
        )
    }

    fun nextPage() {
        if (state.value.currentPage >= state.value.totalPages) return
        if (state.value.listening || state.value.correctionPlaying) stopListening() else stopReader()
        showPage(state.value.currentPage + 1)
    }

    fun previousPage() {
        if (state.value.currentPage <= 1) return
        if (state.value.listening || state.value.correctionPlaying) stopListening() else stopReader()
        showPage(state.value.currentPage - 1)
    }

    /** Called by the reader's horizontal touch pager. */
    fun goToPage(pageNumber: Int) {
        val target = pageNumber.coerceIn(1, state.value.totalPages)
        if (target == state.value.currentPage) return
        if (state.value.listening || state.value.correctionPlaying) stopListening() else stopReader()
        showPage(target)
    }

    private fun showPage(pageNumber: Int, notice: String? = null) {
        val metadata = state.value.metadata ?: return
        val actualPage = pageNumber.coerceIn(1, metadata.pages.size)
        val visible = page(state.value.allAyahs, metadata, actualPage)
        val pageSurah = visible.firstOrNull()?.surah ?: state.value.selectedSurah
        val pageSurahAyahs = state.value.allAyahs.filter { it.surah == pageSurah }
        state.value = state.value.copy(
            ayahs = visible,
            selectedSurah = pageSurah,
            totalAyahsInSurah = pageSurahAyahs.size,
            downloadedAyahs = assetManager.downloadedAyahCount(pageSurah, pageSurahAyahs.size),
            currentPage = actualPage,
            pageWordOffset = globalWordOffset(state.value.allAyahs, visible.firstOrNull()),
            hidden = false,
            transcript = "",
            evaluation = null,
            currentWordIndex = -1,
            currentSpokenWord = null,
            currentSpokenWordIndex = -1,
            currentAyahIndex = -1,
            currentWordInAyah = -1,
            recitationCursor = globalWordOffset(state.value.allAyahs, visible.firstOrNull()),
            notice = notice ?: "صفحة $actualPage من ${state.value.totalPages}.",
        )
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

    fun playAyah(surah: Int, ayah: Int) = startReader(ReaderScope.AYAH, surah, ayah)

    fun beginAyahSelection(surah: Int, ayah: Int) {
        val index = state.value.allAyahs.indexOfFirst { it.surah == surah && it.ayah == ayah }
        if (index < 0) return
        state.value = state.value.copy(selectedAyahIndices = listOf(index), notice = "تم تحديد الآية $ayah. اضغط على آية أخرى لإكمال نطاق متصل.")
    }

    fun tapAyahSelection(surah: Int, ayah: Int) {
        val index = state.value.allAyahs.indexOfFirst { it.surah == surah && it.ayah == ayah }
        if (index < 0) return
        val current = state.value.selectedAyahIndices
        val next = if (current.isEmpty()) {
            listOf(index)
        } else {
            AyahSelection(current.first(), current.last()).tap(index).indices.toList()
        }
        state.value = state.value.copy(selectedAyahIndices = next, notice = "تم تحديد ${next.size} آيات متصلة.")
    }

    fun clearAyahSelection() {
        state.value = state.value.copy(selectedAyahIndices = emptyList(), notice = "أُلغي تحديد الآيات.")
    }

    fun playSelectedAyahs() {
        val selected = state.value.selectedAyahIndices
        if (selected.isEmpty()) {
            state.value = state.value.copy(notice = "حدد آية واحدة على الأقل أولاً.")
            return
        }
        startReaderIndices(selected, "الآيات المحددة")
    }

    fun playOpeningBasmala(surah: Int) {
        if (QuranPresentation.openingBasmalaForSurah(surah) == null) {
            state.value = state.value.copy(notice = "الفاتحة آيتها الأولى هي البسملة، والتوبة لا تُفتتح ببسملة مستقلة.")
            return
        }
        if (!state.value.assetsReady) {
            state.value = state.value.copy(notice = "انتظر اكتمال تحميل النص والتوقيتات أولاً.")
            return
        }
        if (state.value.listening || state.value.correctionPlaying) stopListening()
        val timings = timingRepository.forAyah(1, 1)
        val first = timings.firstOrNull()
        val last = timings.lastOrNull() ?: first
        if (first == null || last == null) {
            state.value = state.value.copy(notice = "لا يتوفر توقيت مرجعي مستقل للبِسملة حالياً.")
            return
        }
        val local = assetManager.cachedAudioFile(1, 1)
        if (state.value.mode == AssetMode.DOWNLOAD && local == null) {
            state.value = state.value.copy(notice = "نزّل صوت الفاتحة أولاً لتشغيل البِسملة في وضع التنزيل، أو اختر وضع البث المباشر.")
            return
        }
        val generation = ++readerGeneration
        val key = "opening-basmala:$surah"
        state.value = state.value.copy(readerPlaying = true, readerScope = ReaderScope.AYAH, readerCurrentKey = key, notice = "تُسمع البِسملة الافتتاحية؛ لا تُحسب آية ولا كلمة.")
        referenceAudioPlayer.playQueue(
            items = listOf(
                ReferenceAudioPlayer.QueueItem(
                    source = local?.absolutePath ?: assetManager.audioUrl(1, 1),
                    startMs = first.startMs,
                    endMs = last.endMs,
                    key = key,
                ),
            ),
            pauseBetweenMs = 0,
            onItemStarted = { itemKey -> if (generation == readerGeneration) state.value = state.value.copy(readerCurrentKey = itemKey) },
            onFinished = { if (generation == readerGeneration) state.value = state.value.copy(readerPlaying = false, readerCurrentKey = null, notice = "اكتملت البِسملة الافتتاحية؛ ابدأ الآن بالآية الأولى.") },
        )
    }

    fun playFromHere(surah: Int, ayah: Int) = startReader(ReaderScope.QURAN, surah, ayah)

    fun playSurahFromHere(surah: Int, ayah: Int) = startReader(ReaderScope.SURAH, surah, ayah)

    fun playReaderScope(scope: ReaderScope) {
        val checkpoint = state.value.checkpoint?.validatedAgainst(state.value.allAyahs)
        val start = checkpoint ?: MemorizationCheckpoint(1, 1, 0, false, 0L)
        startReader(scope, start.surah, start.ayah)
    }

    fun stopReader() {
        readerGeneration++
        referenceAudioPlayer.stop()
        state.value = state.value.copy(readerPlaying = false, readerCurrentKey = null, notice = "أُوقف تشغيل القراءة.")
    }

    private fun startReaderIndices(selected: List<Int>, label: String) {
        if (!state.value.assetsReady) {
            state.value = state.value.copy(notice = "انتظر اكتمال تحميل النص والتوقيتات أولاً.")
            return
        }
        val items = ayahIndicesInMushafOrder(selected).mapNotNull { index ->
            val target = state.value.allAyahs.getOrNull(index) ?: return@mapNotNull null
            val timings = timingRepository.forAyah(target.surah, target.ayah)
            val first = timings.firstOrNull() ?: return@mapNotNull null
            val last = timings.lastOrNull() ?: first
            val local = assetManager.cachedAudioFile(target.surah, target.ayah)
            if (state.value.mode == AssetMode.DOWNLOAD && local == null) return@mapNotNull null
            ReferenceAudioPlayer.QueueItem(
                source = local?.absolutePath ?: assetManager.audioUrl(target.surah, target.ayah),
                startMs = first.startMs,
                endMs = last.endMs,
                key = "${target.surah}:${target.ayah}",
            )
        }
        if (items.isEmpty()) {
            state.value = state.value.copy(notice = if (state.value.mode == AssetMode.DOWNLOAD) "نزّل صوت الآيات المحددة أولاً، أو اختر وضع البث المباشر." else "لا توجد مقاطع صوتية صالحة للآيات المحددة.")
            return
        }
        val generation = ++readerGeneration
        state.value = state.value.copy(readerPlaying = true, readerScope = ReaderScope.AYAH, readerCurrentKey = items.first().key, notice = "تُقرأ $label…")
        referenceAudioPlayer.playQueue(
            items = items,
            onItemStarted = { key -> if (generation == readerGeneration) state.value = state.value.copy(readerCurrentKey = key) },
            onFinished = { if (generation == readerGeneration) state.value = state.value.copy(readerPlaying = false, readerCurrentKey = null, notice = "اكتملت قراءة $label.") },
        )
    }

    private fun startReader(scope: ReaderScope, surah: Int, ayah: Int) {
        if (!state.value.assetsReady) {
            state.value = state.value.copy(notice = "انتظر اكتمال تحميل النص والتوقيتات أولاً.")
            return
        }
        val metadata = state.value.metadata
        val startIndex = state.value.allAyahs.indexOfFirst { it.surah == surah && it.ayah == ayah }
        if (metadata == null || startIndex < 0) {
            state.value = state.value.copy(notice = "موضع القراءة غير موجود في المصحف المحمّل.")
            return
        }
        val selected = when (scope) {
            ReaderScope.AYAH -> listOf(startIndex)
            ReaderScope.SURAH -> state.value.allAyahs.indices.filter { state.value.allAyahs[it].surah == surah && it >= startIndex }
            ReaderScope.JUZ -> state.value.allAyahs.indices.filter { it >= startIndex && metadata.ayah(state.value.allAyahs[it].surah, state.value.allAyahs[it].ayah)?.juz == metadata.ayah(surah, ayah)?.juz }
            ReaderScope.PAGES -> state.value.allAyahs.indices.filter { it >= startIndex && metadata.ayah(state.value.allAyahs[it].surah, state.value.allAyahs[it].ayah)?.page == metadata.ayah(surah, ayah)?.page }
            ReaderScope.QURAN -> state.value.allAyahs.indices.filter { it >= startIndex }
        }
        val items = selected.mapNotNull { index ->
            val target = state.value.allAyahs[index]
            val timings = timingRepository.forAyah(target.surah, target.ayah)
            val first = timings.firstOrNull() ?: return@mapNotNull null
            val last = timings.lastOrNull() ?: first
            val local = assetManager.cachedAudioFile(target.surah, target.ayah)
            if (state.value.mode == AssetMode.DOWNLOAD && local == null) return@mapNotNull null
            ReferenceAudioPlayer.QueueItem(
                source = local?.absolutePath ?: assetManager.audioUrl(target.surah, target.ayah),
                startMs = first.startMs,
                endMs = last.endMs,
                key = "${target.surah}:${target.ayah}",
            )
        }
        if (items.isEmpty()) {
            state.value = state.value.copy(notice = if (state.value.mode == AssetMode.DOWNLOAD) "نزّل صوت الموضع أولاً، أو اختر وضع البث المباشر." else "لا توجد مقاطع صوتية صالحة لهذا الموضع.")
            return
        }
        val generation = ++readerGeneration
        state.value = state.value.copy(readerPlaying = true, readerScope = scope, readerCurrentKey = items.first().key, notice = "تُقرأ ${readerScopeLabel(scope)}…")
        referenceAudioPlayer.playQueue(
            items = items,
            onItemStarted = { key -> if (generation == readerGeneration) state.value = state.value.copy(readerCurrentKey = key) },
            onFinished = { if (generation == readerGeneration) state.value = state.value.copy(readerPlaying = false, readerCurrentKey = null, notice = "اكتملت قراءة ${readerScopeLabel(scope)}.") },
        )
    }

    private fun readerScopeLabel(scope: ReaderScope): String = when (scope) {
        ReaderScope.AYAH -> "الآية"
        ReaderScope.SURAH -> "السورة"
        ReaderScope.JUZ -> "الجزء"
        ReaderScope.PAGES -> "الصفحة"
        ReaderScope.QURAN -> "المصحف من الموضع المحدد"
    }

    fun startListening() {
        if (!state.value.assetsReady) { state.value = state.value.copy(notice = "انتظر اكتمال تحميل النص والتوقيتات أولاً."); return }
        if (state.value.allAyahs.isEmpty() || state.value.metadata == null) { state.value = state.value.copy(notice = "تعذر تجهيز ترتيب صفحات المصحف."); return }
        if (state.value.engine == RecitationEngine.OFFLINE && !state.value.offlineSupported) {
            state.value = state.value.copy(listening = false, notice = offlineUnavailableMessage())
            return
        }
        if (state.value.engine == RecitationEngine.OFFLINE && !state.value.modelReady) { state.value = state.value.copy(notice = "نزّل نموذج FastConformer أولاً لتفعيل التسميع Offline."); return }
        if (state.value.listening || state.value.correctionPlaying || state.value.readerPlaying) return
        openingBasmalaNotice = null
        openingBasmalaDetected = false
        openingBasmalaNeedsCorrection = false
        val metadata = state.value.metadata ?: return
        val pageStart = QuranPageLayout.firstAyahIndexOnPage(state.value.allAyahs, metadata, state.value.currentPage)
        val selectedSurahStart = state.value.allAyahs.indexOfFirst { ayah ->
            ayah.surah == state.value.selectedSurah && ayah.ayah == 1
        }
        val savedCheckpoint = state.value.checkpoint?.validatedAgainst(state.value.allAyahs)
        val savedAyahIndex = savedCheckpoint?.let { checkpoint ->
            state.value.allAyahs.indexOfFirst { it.surah == checkpoint.surah && it.ayah == checkpoint.ayah }
                .takeIf { it >= 0 }
        }
        val startAyahIndex = savedAyahIndex ?: (if (pageStart >= 0) pageStart else selectedSurahStart).coerceAtLeast(0)
        val startWordIndex = if (savedAyahIndex != null) savedCheckpoint?.wordIndex ?: 0 else 0
        val tracker = RecitationProgressTracker(state.value.allAyahs, correctionSettings = state.value.correctionSettings)
        tracker.startFromPosition(startAyahIndex, startWordIndex)
        progressTracker = tracker
        // At every eligible surah opening, listen for an optional standalone
        // basmala. A direct first-ayah word is still accepted immediately.
        openingBasmalaConsumed = !shouldListenForOpeningBasmala(startAyahIndex)
        val startPage = pageForAyahIndex(startAyahIndex)
        val startVisibleAyahs = page(state.value.allAyahs, state.value.metadata!!, startPage)
        activeCorrection = null
        correctionWasListening = false
        state.value = state.value.copy(
            ayahs = startVisibleAyahs,
            currentPage = startPage,
            pageWordOffset = globalWordOffset(state.value.allAyahs, startVisibleAyahs.firstOrNull()),
            listening = true,
            transcript = "",
            evaluation = null,
            currentWordIndex = tracker.globalWordIndex(startAyahIndex, startWordIndex) ?: -1,
            currentSpokenWord = null,
            currentSpokenWordIndex = -1,
            currentAyahIndex = startAyahIndex,
            currentWordInAyah = startWordIndex,
            currentAyahLocked = false,
            recitationCursor = tracker.globalWordIndex(startAyahIndex, startWordIndex) ?: 0,
            correctionPlaying = false,
            openingBasmalaActive = !openingBasmalaConsumed,
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
        if (currentAyah <= 0 || currentAyah !in state.value.allAyahs.indices || state.value.correctionPlaying) return
        val previousAyahIndex = currentAyah - 1
        referenceAudioPlayer.stop()
        recognitionGeneration++
        onlineSession?.stop(); onlineSession = null
        localSession?.stop(); localSession = null
        val tracker = RecitationProgressTracker(state.value.allAyahs, correctionSettings = state.value.correctionSettings)
        tracker.startFromAyah(previousAyahIndex)
        openingBasmalaConsumed = !shouldListenForOpeningBasmala(previousAyahIndex)
        openingBasmalaDetected = false
        openingBasmalaNeedsCorrection = false
        progressTracker = tracker
        activeCorrection = null
        correctionWasListening = false
        val previousPage = pageForAyahIndex(previousAyahIndex)
        val previousVisibleAyahs = page(state.value.allAyahs, state.value.metadata!!, previousPage)
        state.value = state.value.copy(
            ayahs = previousVisibleAyahs,
            currentPage = previousPage,
            pageWordOffset = globalWordOffset(state.value.allAyahs, previousVisibleAyahs.firstOrNull()),
            listening = true,
            transcript = "",
            evaluation = null,
            currentWordIndex = tracker.globalWordIndex(previousAyahIndex, 0) ?: -1,
            currentSpokenWord = null,
            currentSpokenWordIndex = -1,
            currentAyahIndex = previousAyahIndex,
            currentWordInAyah = 0,
            currentAyahLocked = false,
            recitationCursor = tracker.globalWordIndex(previousAyahIndex, 0) ?: 0,
            correctionPlaying = false,
            openingBasmalaActive = !openingBasmalaConsumed,
            correctionPhase = CorrectionPhase.LISTENING,
            correctionMessage = null,
            correctionAyahIndex = -1,
            correctionFirstWordInAyah = -1,
            notice = "بدأت جلسة جديدة من السورة ${state.value.allAyahs[previousAyahIndex].surah}، الآية ${state.value.allAyahs[previousAyahIndex].ayah}.",
        )
        startRecognitionSession()
    }

    private fun handleRecognitionUpdate(update: RecognitionUpdate) {
        if (state.value.correctionPlaying && update !is RecognitionUpdate.Started) return
        when (update) {
            RecognitionUpdate.Started -> Unit
            is RecognitionUpdate.Text -> {
                openingBasmalaNotice = null
                val previousState = state.value
                val awaitingOpeningBasmala = !openingBasmalaConsumed
                val filteredTranscript = openingBasmalaTranscript(update.value, update.isFinal, update.segmentCompleted)

                if (awaitingOpeningBasmala && !openingBasmalaConsumed) {
                    // This callback belongs to the independent basmala
                    // sentence. It is intentionally invisible to Quran word
                    // evaluation and never becomes progress.
                    state.value = previousState.copy(
                        transcript = "",
                        currentSpokenWord = null,
                        currentSpokenWordIndex = -1,
                        openingBasmalaActive = openingBasmalaDetected,
                        notice = if (openingBasmalaDetected) "جارٍ الاستماع إلى البسملة الافتتاحية وحدها؛ لن تُحسب من الآية." else previousState.notice,
                    )
                    return
                }

                if (awaitingOpeningBasmala && openingBasmalaDetected) {
                    if (openingBasmalaNeedsCorrection) {
                        pauseRecognitionForOpeningBasmala()
                        playOpeningBasmalaCorrection()
                        return
                    }
                    restartRecognitionAfterOpeningBasmala()
                    if (filteredTranscript.isBlank()) return
                    // A correct basmala may share a recognizer callback with
                    // the first ayah. The basmala is consumed independently,
                    // while the trailing ayah words continue below.
                }

                val tracker = progressTracker ?: RecitationProgressTracker(previousState.allAyahs, correctionSettings = previousState.correctionSettings).also { progressTracker = it }
                val result = tracker.update(
                    RecitationProgressInput(
                        transcript = filteredTranscript,
                        isFinal = update.isFinal,
                        segmentCompleted = update.segmentCompleted,
                    ),
                )
                val correction = result.correction
                if (result.correctedErrors.isNotEmpty()) correctedErrorStore.appendAll(result.correctedErrors)
                val correctedErrors = if (result.correctedErrors.isNotEmpty()) correctedErrorStore.list() else previousState.correctedErrors
                val currentTarget = correction?.let { tracker.globalWordIndex(it.ayahIndex, it.localWordIndex) }
                    ?: result.currentGlobalWordIndex.takeIf { it < targetWords.size }
                val canonicalSpokenWord = canonicalMatchedWord(
                    targetWords.getOrNull(result.spokenGlobalWordIndex),
                    result.spokenWord,
                )
                val resolvedAyah = previousState.allAyahs.getOrNull(result.currentAyahIndex)
                val resolvedPage = resolvedAyah?.let { previousState.metadata?.ayah(it.surah, it.ayah)?.page } ?: previousState.currentPage
                val resolvedVisibleAyahs = previousState.metadata?.let { page(previousState.allAyahs, it, resolvedPage) } ?: previousState.ayahs
                state.value = previousState.copy(
                    ayahs = resolvedVisibleAyahs,
                    currentPage = resolvedPage,
                    pageWordOffset = globalWordOffset(previousState.allAyahs, resolvedVisibleAyahs.firstOrNull()),
                    transcript = canonicalizeRecognizedTranscript(update.value, result.evaluation.words),
                    evaluation = result.evaluation,
                    correctedErrors = correctedErrors,
                    currentWordIndex = currentTarget ?: -1,
                    currentSpokenWord = canonicalSpokenWord,
                    currentSpokenWordIndex = result.spokenGlobalWordIndex,
                    currentAyahIndex = result.currentAyahIndex,
                    currentWordInAyah = result.currentWordInAyah,
                    currentAyahLocked = result.currentAyahLocked,
                    recitationCursor = result.currentGlobalWordIndex.coerceIn(0, targetWords.size),
                    openingBasmalaActive = false,
                    correctionMessage = correction?.let(::correctionMessage) ?: previousState.correctionMessage,
                    correctionAyahIndex = correction?.ayahIndex ?: previousState.correctionAyahIndex,
                    correctionFirstWordInAyah = correction?.localWordIndex ?: previousState.correctionFirstWordInAyah,
                    notice = openingBasmalaNotice ?: if (previousState.engine == RecitationEngine.ONLINE) "يتم التحقق عبر الإنترنت؛ الصوت لا يُخزن محلياً." else "يتم التحقق محلياً؛ لا يغادر الصوت جهازك.",
                )
                if (update.isFinal || update.segmentCompleted) persistCheckpoint(result.currentAyahIndex, result.currentWordInAyah)
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
                state.value = state.value.copy(listening = false, openingBasmalaActive = false, notice = update.message)
            }
            RecognitionUpdate.Stopped -> {
                if (activeCorrection != null) return
                progressTracker = null
                state.value = state.value.copy(listening = false, openingBasmalaActive = false, notice = "انتهت الجلسة. راجع المواضع غير المؤكدة قبل اعتماد النتيجة.")
            }
        }
    }

    private fun pauseRecognitionForOpeningBasmala() {
        recognitionGeneration++
        readerGeneration++
        referenceAudioPlayer.stop()
        onlineSession?.stop(); onlineSession = null
        localSession?.stop(); localSession = null
        state.value = state.value.copy(
            listening = false,
            correctionPlaying = true,
            openingBasmalaActive = true,
            correctionPhase = CorrectionPhase.PLAYING_FIRST_ERROR,
            correctionMessage = "البسملة الافتتاحية تحتاج تصحيحاً؛ ستُسمع الجملة مستقلة.",
            notice = "يُشغّل تصحيح البسملة الافتتاحية فقط؛ لا تُحسب آية ولا كلمة.",
        )
    }

    private fun playOpeningBasmalaCorrection() {
        val timings = timingRepository.forAyah(1, 1)
        val first = timings.firstOrNull()
        val last = timings.lastOrNull() ?: first
        if (first == null || last == null) {
            finishOpeningBasmalaCorrection("لا يتوفر مقطع مرجعي مستقل لتصحيح البسملة حالياً.")
            return
        }
        val local = assetManager.cachedAudioFile(1, 1)
        if (state.value.mode == AssetMode.DOWNLOAD && local == null) {
            finishOpeningBasmalaCorrection("نزّل صوت الفاتحة أولاً لتشغيل تصحيح البسملة، أو اختر وضع البث المباشر.")
            return
        }
        val generation = ++readerGeneration
        val key = "opening-basmala-correction"
        referenceAudioPlayer.playQueue(
            items = listOf(
                ReferenceAudioPlayer.QueueItem(
                    source = local?.absolutePath ?: assetManager.audioUrl(1, 1),
                    startMs = first.startMs,
                    endMs = last.endMs,
                    key = key,
                ),
            ),
            pauseBetweenMs = 0,
            onItemStarted = { itemKey ->
                if (generation == readerGeneration) state.value = state.value.copy(readerCurrentKey = itemKey)
            },
            onFinished = {
                if (generation == readerGeneration) finishOpeningBasmalaCorrection("اكتمل تصحيح البسملة؛ أعد تلاوتها أو ابدأ الآية الأولى مباشرة.")
            },
        )
    }

    private fun finishOpeningBasmalaCorrection(message: String) {
        openingBasmalaConsumed = true
        openingBasmalaDetected = false
        openingBasmalaNeedsCorrection = false
        state.value = state.value.copy(
            listening = true,
            correctionPlaying = false,
            openingBasmalaActive = false,
            correctionPhase = CorrectionPhase.WAITING_RESUME,
            correctionMessage = message,
            readerPlaying = false,
            readerCurrentKey = null,
            notice = message,
        )
        startRecognitionSession()
    }

    private fun restartRecognitionAfterOpeningBasmala() {
        openingBasmalaConsumed = true
        openingBasmalaDetected = false
        openingBasmalaNeedsCorrection = false
        recognitionGeneration++
        onlineSession?.stop(); onlineSession = null
        localSession?.stop(); localSession = null
        state.value = state.value.copy(
            listening = true,
            openingBasmalaActive = false,
            transcript = "",
            currentSpokenWord = null,
            currentSpokenWordIndex = -1,
            notice = "تمت البسملة كجملة مستقلة؛ ابدأ الآن بالآية الأولى.",
        )
        startRecognitionSession()
    }

    private fun openingBasmalaTranscript(transcript: String, isFinal: Boolean, segmentCompleted: Boolean): String {
        if (openingBasmalaConsumed) return transcript
        val current = state.value.allAyahs.getOrNull(state.value.currentAyahIndex)
        if (current == null || current.ayah != 1 || current.surah == 1 || current.surah == 9) {
            openingBasmalaConsumed = true
            return transcript
        }
        val result = OpeningBasmalaSpeechFilter.filter(
            transcript = transcript,
            isFinal = isFinal,
            segmentCompleted = segmentCompleted,
            alreadyConsumed = false,
        )
        openingBasmalaConsumed = result.consumed
        openingBasmalaDetected = result.detectedOpeningBasmala
        openingBasmalaNeedsCorrection = result.needsCorrection
        if (result.needsCorrection) {
            openingBasmalaNotice = "لم تطابق البسملة الافتتاحية تماماً؛ ستُصحح كجملة مستقلة قبل بدء الآية."
        }
        return result.ayahTranscript
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
        val ayahIndex = state.value.allAyahs.indexOfFirst { it.surah == location.first.surah && it.ayah == location.first.ayah }
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
        val ayah = state.value.allAyahs.getOrNull(request.ayahIndex) ?: return finishCorrectionPlayback()
        val timings = timingRepository.forAyah(ayah.surah, ayah.ayah)
        val firstWord = TimingWordMapper.forWord(ayah, timings, request.localWordIndex)
            ?: return finishCorrectionPlayback()
        val wholeAyah = if (request.readWholeAyah) TimingWordMapper.wholeAyah(ayah, timings) else null
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
        val ayah = state.value.allAyahs.getOrNull(request.ayahIndex)
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
        state.value.allAyahs.forEach { ayah ->
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
        persistCheckpoint(state.value.currentAyahIndex, state.value.currentWordInAyah)
        recognitionGeneration++
        readerGeneration++
        referenceAudioPlayer.stop()
        onlineSession?.stop(); onlineSession = null
        localSession?.stop(); localSession = null
        progressTracker = null
        activeCorrection = null
        correctionWasListening = false
        openingBasmalaConsumed = true
        openingBasmalaDetected = false
        openingBasmalaNeedsCorrection = false
        state.value = state.value.copy(listening = false, correctionPlaying = false, openingBasmalaActive = false, correctionPhase = CorrectionPhase.LISTENING, readerPlaying = false, readerCurrentKey = null)
    }

    fun startMemorizationFrom(surah: Int, ayah: Int) {
        val metadata = state.value.metadata ?: return
        val pageNumber = metadata.ayah(surah, ayah)?.page ?: return
        stopListening()
        setMemorizationStart(surah, ayah)
        showPage(pageNumber, notice = "تم تحديد بداية التسميع من السورة $surah، الآية $ayah.")
    }

    fun setMemorizationStart(surah: Int, ayah: Int, wordIndex: Int = 0, includeOpeningBasmala: Boolean = false) {
        val target = state.value.allAyahs.firstOrNull { it.surah == surah && it.ayah == ayah }
        val checkpoint = target?.let {
            MemorizationCheckpoint(surah, ayah, wordIndex, includeOpeningBasmala, System.currentTimeMillis())
                .validatedAgainst(state.value.allAyahs)
        }
        if (checkpoint == null) {
            state.value = state.value.copy(notice = "تعذر تعيين موضع البداية؛ الموضع غير موجود في المصحف المحمّل.")
            return
        }
        memorizationProgressStore.save(checkpoint)
        state.value = state.value.copy(checkpoint = checkpoint, notice = "تم حفظ نقطة البداية: السورة $surah، الآية $ayah، الكلمة ${wordIndex + 1}.")
    }

    fun resumeFromCheckpoint() {
        if (state.value.checkpoint?.validatedAgainst(state.value.allAyahs) == null) {
            state.value = state.value.copy(notice = "لا توجد نقطة استئناف صالحة بعد؛ اختر موضعاً من الفهرس أو القارئ.")
            return
        }
        startListening()
    }

    private fun persistCheckpoint(ayahIndex: Int, wordIndex: Int) {
        val all = state.value.allAyahs
        if (all.isEmpty()) return
        val target = all.getOrNull(ayahIndex) ?: all.last()
        val safeWordIndex = if (ayahIndex in all.indices) wordIndex.coerceIn(0, target.words.size) else target.words.size
        val previous = state.value.checkpoint
        val checkpoint = MemorizationCheckpoint(
            surah = target.surah,
            ayah = target.ayah,
            wordIndex = safeWordIndex,
            includeOpeningBasmala = previous?.includeOpeningBasmala ?: state.value.correctionSettings.listenForOpeningBasmala,
            updatedAt = System.currentTimeMillis(),
        )
        memorizationProgressStore.save(checkpoint)
        state.value = state.value.copy(checkpoint = checkpoint)
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
                checkpoint = state.value.checkpoint,
                correctedErrors = state.value.correctedErrors,
            )
            state.value = state.value.copy(notice = "تم تصدير النسخة الاحتياطية وإعدادات التصحيح إلى الملف الذي اخترته.")
        }
        catch (error: Throwable) { state.value = state.value.copy(notice = "تعذر تصدير النسخة الاحتياطية: ${error.message ?: "خطأ غير معروف"}") }
    }

    fun clearSessions() { store.clear(); state.value = state.value.copy(sessions = emptyList(), notice = "حُذف سجل الجلسات المحلي.") }

    fun clearCorrectedErrors() {
        correctedErrorStore.clear()
        state.value = state.value.copy(correctedErrors = emptyList(), notice = "حُذف سجل الأخطاء المصححة المحلي.")
    }

    override fun onCleared() { recognitionGeneration++; onlineSession?.stop(); localSession?.stop(); progressTracker = null; referenceAudioPlayer.stop(); super.onCleared() }

    private fun offlineUnavailableMessage(): String = "التسميع Offline غير متاح بأمان على هذا الهاتف؛ النظام يعمل 32-bit، وظهر انهيار native داخل محرك FastConformer. استخدم Online أو هاتفاً 64-bit."

    private fun page(ayahs: List<QuranAyah>, metadata: QuranMetadata, pageNumber: Int): List<QuranAyah> =
        QuranPageLayout.ayahsOnPage(ayahs, metadata, pageNumber)

    private fun globalWordOffset(allAyahs: List<QuranAyah>, firstVisibleAyah: QuranAyah?): Int =
        QuranPageLayout.globalWordOffset(allAyahs, firstVisibleAyah)

    private fun shouldListenForOpeningBasmala(ayahIndex: Int): Boolean {
        val ayah = state.value.allAyahs.getOrNull(ayahIndex) ?: return false
        val eligible = ayah.ayah == 1 && QuranPresentation.openingBasmalaForSurah(ayah.surah) != null
        val checkpointOverride = state.value.checkpoint?.includeOpeningBasmala == true
        return eligible && (state.value.correctionSettings.listenForOpeningBasmala || checkpointOverride)
    }

    private fun pageForAyahIndex(index: Int): Int {
        val metadata = state.value.metadata ?: return state.value.currentPage
        return QuranPageLayout.pageForAyahIndex(state.value.allAyahs, metadata, index, state.value.currentPage)
    }
}
