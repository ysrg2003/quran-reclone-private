package com.example.quranoffline.data

import android.app.Application
import android.os.Build
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quranoffline.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AssetMode { STREAM, DOWNLOAD }
enum class RecitationEngine { ONLINE }
enum class RecitationInputMode { VOICE, TYPING }
enum class ReaderScope { AYAH, SURAH, JUZ, HIZB, PAGES, QURAN }

data class QuranUiState(
    val ayahs: List<QuranAyah> = emptyList(),
    val allAyahs: List<QuranAyah> = emptyList(),
    /** Cached global word count; avoids flattening all 77k words on callbacks. */
    val totalWordCount: Int = 0,
    val metadata: QuranMetadata? = null,
    val pageLayout: QuranLayout? = null,
    val wordReferenceLayout: WordReferenceLayout? = null,
    val hidden: Boolean = false,
    val listening: Boolean = false,
    val assetsReady: Boolean = false,
    val loadingAssets: Boolean = true,
    /** Exact text returned by SpeechRecognizer; never canonicalized for judgement or display. */
    val rawTranscript: String = "",
    /** Optional Quranized display derived only after judgement. */
    val transcript: String = "",
    val evaluation: RecitationEvaluation? = null,
    /** Standalone basmala evaluation; never included in Quran ayah totals. */
    val openingBasmalaEvaluation: RecitationEvaluation? = null,
    val openingBasmalaWordIndex: Int = -1,
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
    val inputMode: RecitationInputMode = RecitationInputMode.VOICE,
    val typedInput: String = "",
)

private data class LoadedAssetInputs(
    val allAyahs: List<QuranAyah>,
    val metadata: QuranMetadata,
    val wordReferenceLayout: WordReferenceLayout,
    val pronunciationReference: PronunciationReference,
)

private data class PreparedAssets(
    val repository: QuranRepository,
    val metadata: QuranMetadata,
    val wordReferenceLayout: WordReferenceLayout,
    val pronunciationReference: PronunciationReference,
    val allAyahs: List<QuranAyah>,
    val ayahs: List<QuranAyah>,
    val pageWordOffset: Int,
    val selectedSurah: Int,
    val totalAyahs: Int,
    val downloadedAyahs: Int,
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
    private val referenceAudioPlayer = ReferenceAudioPlayer(application)
    private var pronunciationReference = PronunciationReference.empty()
    private var onlineSession: OnlineSpeechSession? = null
    private var writingModeActive = false
    private var progressTracker: RecitationProgressTracker? = null
    private var openingBasmalaTracker: OpeningBasmalaProgressTracker? = null
    private var recognitionGeneration = 0L
    private var activeCorrection: CorrectionRequest? = null
    private var correctionWasListening = false
    private var openingBasmalaConsumed = true
    private var openingBasmalaNotice: String? = null
    private var openingBasmalaDetected = false
    private var openingBasmalaNeedsCorrection = false
    private var activeBasmalaCorrection: CorrectionRequest? = null
    private var openingBasmalaCommittedRaw = ""
    private var openingBasmalaContinuation = false
    private var openingBasmalaRequired = false
    private var readerGeneration = 0L
    private var assetLoadJob: Job? = null
    private var previewLoadJob: Job? = null

    private val state = mutableStateOf(
        QuranUiState(
            sessions = store.list(),
            correctedErrors = correctedErrorStore.list(),
            goal = goalStore.current(),
            checkpoint = memorizationProgressStore.read(),
            correctionSettings = correctionSettingsStore.current(),
            mode = if (BuildConfig.OFFLINE_DISTRIBUTION) AssetMode.DOWNLOAD else AssetMode.STREAM,
            engine = RecitationEngine.ONLINE,
            inputMode = RecitationInputMode.VOICE,
        ),
    )
    val uiState: State<QuranUiState> = state

    init {
        previewLoadJob = loadPreview()
        loadAssets(if (BuildConfig.OFFLINE_DISTRIBUTION) AssetMode.DOWNLOAD else AssetMode.STREAM)
    }

    fun setMode(mode: AssetMode) {
        if (BuildConfig.OFFLINE_DISTRIBUTION && mode != AssetMode.DOWNLOAD) return
        if (!BuildConfig.OFFLINE_DISTRIBUTION && mode != AssetMode.STREAM) return
        if (mode == state.value.mode && state.value.assetsReady) return
        stopListening()
        state.value = state.value.copy(mode = mode, loadingAssets = true, assetsReady = false, notice = "جاري تجهيز وضع ${if (mode == AssetMode.STREAM) "البث المباشر" else "التنزيل المحلي"}…")
        loadAssets(mode)
    }

    fun setEngine(@Suppress("UNUSED_PARAMETER") engine: RecitationEngine) {
        state.value = state.value.copy(engine = RecitationEngine.ONLINE, notice = "سيستخدم التسميع Android SpeechRecognizer عبر الإنترنت.")
    }

    fun setRecitationInputMode(mode: RecitationInputMode) {
        if (state.value.inputMode == mode && !state.value.listening) return
        stopListening()
        writingModeActive = false
        state.value = state.value.copy(
            inputMode = mode,
            typedInput = "",
            rawTranscript = "",
            transcript = "",
            evaluation = null,
            notice = if (mode == RecitationInputMode.TYPING) "وضع الكتابة جاهز؛ اكتب كلمة أو آية ثم اضغط إرسال." else "وضع الصوت جاهز؛ سيستخدم Android SpeechRecognizer.",
        )
    }

    private fun readBundledAsset(path: String): String =
        getApplication<Application>().assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun loadPreview(): Job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val previewRepository = QuranRepository(context, "quran/word_reference_preview.json")
                val previewMetadata = metadataRepository.load()
                val previewLayout = WordReferenceLayout.loadPreview(context)
                val previewAyahs = previewRepository.allAyahs()
                withContext(Dispatchers.Main) {
                    if (state.value.allAyahs.isNotEmpty()) return@withContext
                    val firstPage = previewLayout.ayahKeysForPage(1)
                        .mapNotNull { (surah, ayah) -> previewAyahs.firstOrNull { it.surah == surah && it.ayah == ayah } }
                    state.value = state.value.copy(
                        ayahs = firstPage,
                        allAyahs = previewAyahs,
                        totalWordCount = previewAyahs.sumOf { it.words.size },
                        metadata = previewMetadata,
                        wordReferenceLayout = previewLayout,
                        currentPage = 1,
                        totalPages = previewMetadata.pages.size,
                        pageWordOffset = globalWordOffset(previewAyahs, firstPage.firstOrNull()),
                        loadingAssets = true,
                        assetsReady = false,
                        notice = "ظهرت الصفحات الأولى؛ جارٍ تجهيز بقية النص والتوقيتات في الخلفية…",
                    )
                }
            } catch (_: CancellationException) {
                throw CancellationException("Preview loading cancelled")
            } catch (_: Throwable) {
                // The full, validated load below remains the source of truth.
            }
        }

    private fun loadAssets(mode: AssetMode) {
        assetLoadJob?.cancel()
        assetLoadJob = viewModelScope.launch {
            try {
                previewLoadJob?.join()
                val context = getApplication<Application>()
                val requestedSurah = state.value.selectedSurah
                val requestedPage = state.value.currentPage
                // Heavy assets are deliberately loaded after the preview has been
                // published. quran_page_layout.json is not used by the active
                // renderer and is therefore not parsed during startup.
                // These independent reads run concurrently on IO dispatchers;
                // the UI is updated only after all validated results are ready.
                val parsedRepository = withContext(Dispatchers.IO) { QuranRepository(context) }
                val loaded = coroutineScope {
                    val allAyahsDeferred = async(Dispatchers.IO) { parsedRepository.allAyahs() }
                    val metadataDeferred = async(Dispatchers.IO) { metadataRepository.load() }
                    val layoutDeferred = async(Dispatchers.IO) { WordReferenceLayout.load(context) }
                    val pronunciationDeferred = async(Dispatchers.IO) { PronunciationReference.load(context) }
                    val timingDeferred = async(Dispatchers.IO) { timingRepository.loadBundled(context) }
                    timingDeferred.await()
                    LoadedAssetInputs(
                        allAyahs = allAyahsDeferred.await(),
                        metadata = metadataDeferred.await(),
                        wordReferenceLayout = layoutDeferred.await(),
                        pronunciationReference = pronunciationDeferred.await(),
                    )
                }
                val allAyahs = loaded.allAyahs
                val parsedMetadata = loaded.metadata
                val wordReferenceLayout = loaded.wordReferenceLayout
                val pronunciationReference = loaded.pronunciationReference
                val prepared = withContext(Dispatchers.Default) {
                    parsedMetadata.requireCompatible(allAyahs)
                    check(wordReferenceLayout.pageCount == parsedMetadata.pages.size) { "Word reference page count does not match Quran metadata" }
                    check(pronunciationReference.size() == allAyahs.size) { "Word-derived pronunciation ayah count does not match Word Quran text" }
                    // Build the 77k-word tracker tables here, off the main thread.
                    RecitationProgressTracker.prewarm(allAyahs)
                    val requestedSurahAyahs = parsedRepository.ayahsForSurah(requestedSurah).ifEmpty { parsedRepository.ayahsForSurah(1) }
                    val selectedSurah = requestedSurahAyahs.firstOrNull()?.surah ?: 1
                    val totalAyahs = parsedRepository.ayahsForSurah(selectedSurah).size
                    val actualPage = requestedPage.coerceIn(1, parsedMetadata.pages.size)
                    val visiblePage = wordReferenceLayout.ayahKeysForPage(actualPage)
                        .mapNotNull { (surah, ayah) -> allAyahs.firstOrNull { it.surah == surah && it.ayah == ayah } }
                        .takeIf { it.isNotEmpty() }
                        ?: page(allAyahs, parsedMetadata, actualPage)
                    PreparedAssets(
                        repository = parsedRepository,
                        metadata = parsedMetadata,
                        wordReferenceLayout = wordReferenceLayout,
                        pronunciationReference = pronunciationReference,
                        allAyahs = allAyahs,
                        ayahs = visiblePage,
                        pageWordOffset = globalWordOffset(allAyahs, visiblePage.firstOrNull()),
                        selectedSurah = visiblePage.firstOrNull()?.surah ?: selectedSurah,
                        totalAyahs = parsedRepository.ayahsForSurah(visiblePage.firstOrNull()?.surah ?: selectedSurah).size,
                        downloadedAyahs = assetManager.downloadedAyahCount(visiblePage.firstOrNull()?.surah ?: selectedSurah, totalAyahs),
                    )
                }
                repository = prepared.repository
                this@QuranViewModel.pronunciationReference = prepared.pronunciationReference
                val finalPage = state.value.currentPage.coerceIn(1, prepared.metadata.pages.size)
                val finalVisible = prepared.wordReferenceLayout.ayahKeysForPage(finalPage)
                    .mapNotNull { (surah, ayah) -> prepared.allAyahs.firstOrNull { it.surah == surah && it.ayah == ayah } }
                    .takeIf { it.isNotEmpty() }
                    ?: page(prepared.allAyahs, prepared.metadata, finalPage)
                val finalSurah = finalVisible.firstOrNull()?.surah ?: state.value.selectedSurah
                val finalSurahAyahs = prepared.repository.ayahsForSurah(finalSurah)
                state.value = state.value.copy(
                    ayahs = finalVisible,
                    allAyahs = prepared.allAyahs,
                    totalWordCount = prepared.allAyahs.sumOf { it.words.size },
                    metadata = prepared.metadata,
                    pageLayout = null,
                    wordReferenceLayout = prepared.wordReferenceLayout,
                    selectedSurah = finalSurah,
                    currentPage = finalPage,
                    totalPages = prepared.metadata.pages.size,
                    pageWordOffset = globalWordOffset(prepared.allAyahs, finalVisible.firstOrNull()),
                    totalAyahsInSurah = finalSurahAyahs.size,
                    loadingAssets = false,
                    assetsReady = true,
                    downloadedAyahs = assetManager.downloadedAyahCount(finalSurah, finalSurahAyahs.size),
                    checkpoint = memorizationProgressStore.read()?.validatedAgainst(prepared.allAyahs),
                    notice = if (mode == AssetMode.STREAM) "نص Word العثماني والتوقيتات محليان؛ صوت الحصري يُجلب عند الحاجة." else "نص Word العثماني والتوقيتات وصوت الحصري مضمّنة داخل إصدار Offline؛ يحتاج التسميع الصوتي للإنترنت فقط.",
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state.value = state.value.copy(
                    loadingAssets = false,
                    assetsReady = false,
                    notice = "تعذر تحميل التوقيتات أو تخطيط الصفحة: ${error.message ?: "خطأ شبكي"}",
                )
            }
        }
    }

    fun selectSurah(surah: Int) {
        if (!state.value.assetsReady) {
            state.value = state.value.copy(notice = "الصفحة الأولى جاهزة للعرض؛ انتظر اكتمال تجهيز المصحف قبل اختيار سورة أخرى.")
            return
        }
        val surahAyahs = repository.ayahsForSurah(surah)
        val metadata = state.value.metadata ?: return
        if (surahAyahs.isEmpty()) return
        if (state.value.listening || state.value.correctionPlaying) stopListening() else stopReader()
        val pageNumber = state.value.wordReferenceLayout?.pageForAyah(surah, 1) ?: metadata.ayah(surah, 1)?.page ?: 1
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
        val layout = state.value.wordReferenceLayout
        if (!state.value.assetsReady && layout != null && !layout.hasPage(actualPage)) {
            state.value = state.value.copy(notice = "الصفحات القريبة جاهزة فقط؛ جارٍ تجهيز بقية المصحف في الخلفية…")
            return
        }
        val visible = layout?.ayahKeysForPage(actualPage)
            ?.mapNotNull { (surah, ayah) -> state.value.allAyahs.firstOrNull { it.surah == surah && it.ayah == ayah } }
            ?.takeIf { it.isNotEmpty() }
            ?: page(state.value.allAyahs, metadata, actualPage)
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
            rawTranscript = "",
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
        if (!BuildConfig.OFFLINE_DISTRIBUTION || state.value.mode != AssetMode.DOWNLOAD || state.value.downloadInProgress) return
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
        if (!BuildConfig.OFFLINE_DISTRIBUTION) return
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
        val bundledAssetPath = if (BuildConfig.OFFLINE_DISTRIBUTION) {
            assetManager.bundledAudioAssetPath(1, 1)
        } else null
        if (!BuildConfig.OFFLINE_DISTRIBUTION && state.value.mode == AssetMode.DOWNLOAD && local == null) {
            state.value = state.value.copy(notice = "نزّل صوت الفاتحة أولاً لتشغيل البِسملة في وضع التنزيل، أو اختر وضع البث المباشر.")
            return
        }
        val generation = ++readerGeneration
        val key = "opening-basmala:$surah"
        state.value = state.value.copy(readerPlaying = true, readerScope = ReaderScope.AYAH, readerCurrentKey = key, notice = "تُسمع البِسملة الافتتاحية؛ لا تُحسب آية ولا كلمة.")
        referenceAudioPlayer.playQueue(
            items = listOf(
                ReferenceAudioPlayer.QueueItem(
                    source = if (BuildConfig.OFFLINE_DISTRIBUTION) "" else local?.absolutePath ?: assetManager.audioUrl(1, 1),
                    // Full-file playback must include any audio before the
                    // first QUL word cue; word clips remain timing-bounded.
                    startMs = 0,
                    endMs = last.endMs,
                    key = key,
                    assetPath = bundledAssetPath,
                ),
            ),
            pauseBetweenMs = 0,
            onItemStarted = { itemKey -> if (generation == readerGeneration) state.value = state.value.copy(readerCurrentKey = itemKey) },
            onFinished = { if (generation == readerGeneration) state.value = state.value.copy(readerPlaying = false, readerCurrentKey = null, notice = "اكتملت البِسملة الافتتاحية؛ ابدأ الآن بالآية الأولى.") },
        )
    }

    fun playFromHere(surah: Int, ayah: Int) = startReader(ReaderScope.QURAN, surah, ayah)

    fun playSurahFromHere(surah: Int, ayah: Int) = startReader(ReaderScope.SURAH, surah, ayah)

    fun playReaderScopeFromHere(scope: ReaderScope, surah: Int, ayah: Int) = startReader(scope, surah, ayah)

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
            queueItemForAyah(target, first, last)
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
            ReaderScope.HIZB -> state.value.allAyahs.indices.filter { it >= startIndex && metadata.ayah(state.value.allAyahs[it].surah, state.value.allAyahs[it].ayah)?.quarter == metadata.ayah(surah, ayah)?.quarter }
            ReaderScope.PAGES -> state.value.allAyahs.indices.filter { it >= startIndex && metadata.ayah(state.value.allAyahs[it].surah, state.value.allAyahs[it].ayah)?.page == metadata.ayah(surah, ayah)?.page }
            ReaderScope.QURAN -> state.value.allAyahs.indices.filter { it >= startIndex }
        }
        val items = selected.mapNotNull { index ->
            val target = state.value.allAyahs[index]
            val timings = timingRepository.forAyah(target.surah, target.ayah)
            val first = timings.firstOrNull() ?: return@mapNotNull null
            val last = timings.lastOrNull() ?: first
            queueItemForAyah(target, first, last)
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

    private fun queueItemForAyah(target: QuranAyah, first: TimingSegment, last: TimingSegment): ReferenceAudioPlayer.QueueItem? {
        if (BuildConfig.OFFLINE_DISTRIBUTION) {
            return ReferenceAudioPlayer.QueueItem(
                source = "",
                assetPath = assetManager.bundledAudioAssetPath(target.surah, target.ayah),
                // This QueueItem represents the complete ayah file, not a
                // word clip. Start at file offset zero so a late first cue
                // cannot remove the first spoken word.
                startMs = 0,
                endMs = last.endMs,
                key = "${target.surah}:${target.ayah}",
            )
        }
        val local = assetManager.cachedAudioFile(target.surah, target.ayah)
        if (state.value.mode == AssetMode.DOWNLOAD && local == null) return null
        return ReferenceAudioPlayer.QueueItem(
            source = local?.absolutePath ?: assetManager.audioUrl(target.surah, target.ayah),
            // This QueueItem represents the complete ayah file, not a word
            // clip. A late first QUL cue must not clip its opening audio.
            startMs = 0,
            endMs = last.endMs,
            key = "${target.surah}:${target.ayah}",
        )
    }

    private fun readerScopeLabel(scope: ReaderScope): String = when (scope) {
        ReaderScope.AYAH -> "الآية"
        ReaderScope.SURAH -> "السورة"
        ReaderScope.JUZ -> "الجزء"
        ReaderScope.HIZB -> "الحزب والربع"
        ReaderScope.PAGES -> "الصفحة"
        ReaderScope.QURAN -> "المصحف من الموضع المحدد"
    }

    fun startListening() {
        if (!state.value.assetsReady) { state.value = state.value.copy(notice = "انتظر اكتمال تحميل النص والتوقيتات أولاً."); return }
        if (state.value.allAyahs.isEmpty() || state.value.metadata == null) { state.value = state.value.copy(notice = "تعذر تجهيز ترتيب صفحات المصحف."); return }
        if (state.value.listening || state.value.correctionPlaying || state.value.readerPlaying) return
        openingBasmalaNotice = null
        openingBasmalaDetected = false
        openingBasmalaNeedsCorrection = false
        openingBasmalaTracker = null
        activeBasmalaCorrection = null
        openingBasmalaCommittedRaw = ""
        openingBasmalaContinuation = false
        openingBasmalaRequired = false
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
        // A saved checkpoint identifies the ayah, but resuming a session must
        // always replay that ayah from its first word. The persisted word index
        // remains useful for history/display and is intentionally not a resume
        // cursor.
        val startWordIndex = 0
        val tracker = RecitationProgressTracker(
            state.value.allAyahs,
            pronunciationReference = pronunciationReference,
            allowCanonicalQuranSpelling = state.value.inputMode == RecitationInputMode.TYPING,
            correctionSettings = state.value.correctionSettings,
        )
        tracker.startFromPosition(startAyahIndex, startWordIndex)
        progressTracker = tracker
        // At every eligible surah opening, arm an optional standalone basmala
        // tracker. A direct first-ayah word is still accepted immediately.
        val listenForBasmala = startWordIndex == 0 && shouldListenForOpeningBasmala(startAyahIndex)
        openingBasmalaConsumed = !listenForBasmala
        openingBasmalaTracker = if (listenForBasmala) createOpeningBasmalaTracker(startAyahIndex) else null
        if (openingBasmalaTracker == null) openingBasmalaConsumed = true
        val startPage = pageForAyahIndex(startAyahIndex)
        val startVisibleAyahs = page(state.value.allAyahs, state.value.metadata!!, startPage)
        activeCorrection = null
        correctionWasListening = false
        state.value = state.value.copy(
            ayahs = startVisibleAyahs,
            currentPage = startPage,
            pageWordOffset = globalWordOffset(state.value.allAyahs, startVisibleAyahs.firstOrNull()),
            listening = true,
            rawTranscript = "",
            transcript = "",
            evaluation = null,
            openingBasmalaEvaluation = null,
            openingBasmalaWordIndex = -1,
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
            notice = if (state.value.inputMode == RecitationInputMode.TYPING) "وضع الكتابة جاهز؛ أدخل النص ثم اضغط إرسال." else "جارٍ التعرف عبر الإنترنت؛ لا يُحفظ صوتك في التطبيق.",
        )
        if (!writingModeActive) startRecognitionSession()
    }

    fun startTypedRecitation() {
        if (state.value.inputMode != RecitationInputMode.TYPING || state.value.listening || state.value.correctionPlaying) return
        writingModeActive = true
        startListening()
    }

    fun updateTypedInput(text: String) {
        if (state.value.inputMode == RecitationInputMode.TYPING) state.value = state.value.copy(typedInput = text)
    }

    fun submitTypedRecitation() {
        if (state.value.inputMode != RecitationInputMode.TYPING || state.value.correctionPlaying) return
        if (!state.value.listening) startTypedRecitation()
        if (!state.value.listening) return
        val text = state.value.typedInput.trim()
        if (text.isBlank()) {
            state.value = state.value.copy(notice = "اكتب كلمة أو جزءاً من الآية ثم اضغط إرسال.")
            return
        }
        state.value = state.value.copy(typedInput = "")
        handleRecognitionUpdate(
            RecognitionUpdate.Text(
                value = text,
                timestamps = FloatArray(0),
                isFinal = true,
                segmentCompleted = true,
                providerText = text,
            ),
        )
    }

    private fun startRecognitionSession() {
        val generation = ++recognitionGeneration
        val guardedUpdate: (RecognitionUpdate) -> Unit = { update ->
            if (generation == recognitionGeneration) handleRecognitionUpdate(update)
        }
        onlineSession = OnlineSpeechSession(getApplication(), guardedUpdate)
        onlineSession?.start()
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
        val tracker = RecitationProgressTracker(
            state.value.allAyahs,
            pronunciationReference = pronunciationReference,
            allowCanonicalQuranSpelling = state.value.inputMode == RecitationInputMode.TYPING,
            correctionSettings = state.value.correctionSettings,
        )
        tracker.startFromAyah(previousAyahIndex)
        val listenForBasmala = shouldListenForOpeningBasmala(previousAyahIndex)
        openingBasmalaConsumed = !listenForBasmala
        openingBasmalaTracker = if (listenForBasmala) createOpeningBasmalaTracker(previousAyahIndex) else null
        if (openingBasmalaTracker == null) openingBasmalaConsumed = true
        openingBasmalaCommittedRaw = ""
        openingBasmalaContinuation = false
        openingBasmalaRequired = false
        openingBasmalaDetected = false
        openingBasmalaNeedsCorrection = false
        activeBasmalaCorrection = null
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
            rawTranscript = "",
            transcript = "",
            evaluation = null,
            openingBasmalaEvaluation = null,
            openingBasmalaWordIndex = -1,
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
        if (!writingModeActive) startRecognitionSession()
    }

    private fun handleRecognitionUpdate(update: RecognitionUpdate) {
        if (state.value.correctionPlaying && update !is RecognitionUpdate.Started) return
        when (update) {
            RecognitionUpdate.Started -> Unit
            is RecognitionUpdate.Text -> {
                openingBasmalaNotice = null
                val previousState = state.value
                // The comparison source is the recognizer callback candidate,
                // never the Quran display transcript or its canonicalized form.
                val recognizerTranscript = recognizerTextForComparison(update)
                val basmalaResult = openingBasmalaSpeechResult(
                    recognizerTranscript,
                    update.isFinal,
                    update.segmentCompleted,
                )
                val filteredTranscript = basmalaResult.ayahTranscript

                if (!openingBasmalaConsumed && basmalaResult.detectedOpeningBasmala) {
                    val tracker = openingBasmalaTracker
                    val basmalaTranscript = basmalaResult.basmalaTranscript
                    if (tracker == null || basmalaTranscript.isBlank()) {
                        state.value = previousState.copy(
                            rawTranscript = rawSpeechTextForDisplay(update),
                            transcript = "",
                            openingBasmalaActive = true,
                            notice = "جارٍ الاستماع إلى البسملة الافتتاحية كلمة بكلمة؛ لن تُحسب من الآية.",
                        )
                        return
                    }
                    val basmalaProgress = tracker.update(
                        RecitationProgressInput(
                            transcript = basmalaTranscript,
                            isFinal = update.isFinal,
                            segmentCompleted = update.segmentCompleted,
                        ),
                    )
                    val basmalaCorrection = basmalaProgress.correction
                    val basmalaCompleted = basmalaProgress.evaluation.words.isNotEmpty() &&
                        basmalaProgress.evaluation.words.all { it.status == WordStatus.MATCHED }
                    if (basmalaProgress.correctedErrors.isNotEmpty()) correctedErrorStore.appendAll(basmalaProgress.correctedErrors)
                    val correctedErrors = if (basmalaProgress.correctedErrors.isNotEmpty()) correctedErrorStore.list() else previousState.correctedErrors
                    state.value = previousState.copy(
                        rawTranscript = rawSpeechTextForDisplay(update),
                        transcript = "",
                        openingBasmalaEvaluation = basmalaProgress.evaluation,
                        openingBasmalaWordIndex = basmalaProgress.currentWordInAyah,
                        currentSpokenWord = basmalaProgress.spokenWord,
                        currentSpokenWordIndex = -1,
                        openingBasmalaActive = true,
                        correctedErrors = correctedErrors,
                        correctionMessage = basmalaCorrection?.let(::openingBasmalaCorrectionMessage) ?: previousState.correctionMessage,
                        notice = "جارٍ التحقق من البسملة كلمة بكلمة؛ لا تُحسب آية ولا كلمة من السورة.",
                    )
                    if (basmalaCorrection != null && (update.isFinal || update.segmentCompleted) && activeBasmalaCorrection == null) {
                        activeBasmalaCorrection = basmalaCorrection
                        correctionWasListening = previousState.listening
                        pauseRecognitionForBasmalaCorrection(basmalaCorrection)
                        playOpeningBasmalaCorrection(basmalaCorrection)
                        return
                    }
                    if (!basmalaCompleted) return

                    // The standalone basmala is complete. Only now may any
                    // trailing raw provider words continue into Quran tracking.
                    openingBasmalaConsumed = true
                    openingBasmalaDetected = false
                    openingBasmalaNeedsCorrection = false
                    openingBasmalaCommittedRaw = ""
                    openingBasmalaTracker = null
                    openingBasmalaContinuation = false
                    restartRecognitionAfterOpeningBasmala()
                    if (filteredTranscript.isBlank()) return
                }

                val tracker = progressTracker ?: RecitationProgressTracker(
                    previousState.allAyahs,
                    pronunciationReference = pronunciationReference,
                    allowCanonicalQuranSpelling = previousState.inputMode == RecitationInputMode.TYPING,
                    correctionSettings = previousState.correctionSettings,
                ).also { progressTracker = it }
                val result = tracker.update(
                    RecitationProgressInput(
                        transcript = filteredTranscript,
                        isFinal = update.isFinal,
                        segmentCompleted = update.segmentCompleted,
                    ),
                )
                val crossedToNewSurah = crossedIntoRequiredBasmala(previousState.currentAyahIndex, result.currentAyahIndex)
                if (crossedToNewSurah) {
                    val nextAyah = previousState.allAyahs[result.currentAyahIndex]
                    val nextPage = previousState.metadata?.ayah(nextAyah.surah, nextAyah.ayah)?.page ?: previousState.currentPage
                    val nextVisibleAyahs = previousState.metadata?.let { page(previousState.allAyahs, it, nextPage) } ?: previousState.ayahs
                    openingBasmalaRequired = true
                    openingBasmalaConsumed = false
                    openingBasmalaDetected = false
                    openingBasmalaNeedsCorrection = false
                    openingBasmalaCommittedRaw = ""
                    openingBasmalaContinuation = false
                    openingBasmalaTracker = createOpeningBasmalaTracker(result.currentAyahIndex)
                    state.value = previousState.copy(
                        ayahs = nextVisibleAyahs,
                        currentPage = nextPage,
                        pageWordOffset = globalWordOffset(previousState.allAyahs, nextVisibleAyahs.firstOrNull()),
                        rawTranscript = rawSpeechTextForDisplay(update),
                        transcript = canonicalizeRecognizedTranscript(recognizerTranscript, result.evaluation.words),
                        evaluation = result.evaluation,
                        correctedErrors = previousState.correctedErrors,
                        currentWordIndex = tracker.globalWordIndex(result.currentAyahIndex, 0) ?: -1,
                        currentSpokenWord = rawSpeechWordForDisplay(update),
                        currentSpokenWordIndex = -1,
                        currentAyahIndex = result.currentAyahIndex,
                        currentWordInAyah = 0,
                        currentAyahLocked = false,
                        recitationCursor = tracker.globalWordIndex(result.currentAyahIndex, 0) ?: result.currentGlobalWordIndex,
                        openingBasmalaActive = true,
                        correctionMessage = null,
                        correctionAyahIndex = -1,
                        correctionFirstWordInAyah = -1,
                        notice = "انتهت السورة السابقة. اقرأ البسملة قبل بدء سورة ${nextAyah.surah}؛ لا تُحسب البسملة آية.",
                    )
                    if (update.isFinal || update.segmentCompleted) persistCheckpoint(result.currentAyahIndex, 0)
                    return
                }
                val correction = result.correction
                if (result.correctedErrors.isNotEmpty()) correctedErrorStore.appendAll(result.correctedErrors)
                val correctedErrors = if (result.correctedErrors.isNotEmpty()) correctedErrorStore.list() else previousState.correctedErrors
                                    val currentTarget = correction?.let { tracker.globalWordIndex(it.ayahIndex, it.localWordIndex) }
                        ?: result.currentGlobalWordIndex.takeIf { it < previousState.totalWordCount }

                // Keep the word shown as "current" exactly as observed by the
                // recognizer. Canonical Quran text belongs only to the expected
                // display/evaluation layers, never to raw speech presentation.
                val observedSpokenWord = rawSpeechWordForDisplay(update)
                val resolvedAyah = previousState.allAyahs.getOrNull(result.currentAyahIndex)
                val resolvedPage = resolvedAyah?.let { previousState.metadata?.ayah(it.surah, it.ayah)?.page } ?: previousState.currentPage
                val resolvedVisibleAyahs = previousState.metadata?.let { page(previousState.allAyahs, it, resolvedPage) } ?: previousState.ayahs
                state.value = previousState.copy(
                    ayahs = resolvedVisibleAyahs,
                    currentPage = resolvedPage,
                    pageWordOffset = globalWordOffset(previousState.allAyahs, resolvedVisibleAyahs.firstOrNull()),
                    rawTranscript = rawSpeechTextForDisplay(update),
                    transcript = canonicalizeRecognizedTranscript(recognizerTranscript, result.evaluation.words),
                    evaluation = result.evaluation,
                    correctedErrors = correctedErrors,
                    currentWordIndex = currentTarget ?: -1,
                    currentSpokenWord = observedSpokenWord,
                    currentSpokenWordIndex = result.spokenGlobalWordIndex,
                    currentAyahIndex = result.currentAyahIndex,
                    currentWordInAyah = result.currentWordInAyah,
                    currentAyahLocked = result.currentAyahLocked,
                    recitationCursor = result.currentGlobalWordIndex.coerceIn(0, previousState.totalWordCount),
                    openingBasmalaActive = false,
                    correctionMessage = correction?.let(::correctionMessage) ?: previousState.correctionMessage,
                    correctionAyahIndex = correction?.ayahIndex ?: previousState.correctionAyahIndex,
                    correctionFirstWordInAyah = correction?.localWordIndex ?: previousState.correctionFirstWordInAyah,
                    notice = openingBasmalaNotice ?: "يتم التحقق عبر Android SpeechRecognizer عبر الإنترنت؛ لا يُحفظ صوتك في التطبيق.",
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
                if (activeCorrection != null || activeBasmalaCorrection != null) return
                progressTracker = null
                openingBasmalaTracker = null
                state.value = state.value.copy(listening = false, openingBasmalaActive = false, notice = "انتهت الجلسة. راجع المواضع غير المؤكدة قبل اعتماد النتيجة.")
            }
        }
    }

    private fun pauseRecognitionForBasmalaCorrection(request: CorrectionRequest) {
        recognitionGeneration++
        readerGeneration++
        referenceAudioPlayer.stop()
        onlineSession?.stop(); onlineSession = null
        state.value = state.value.copy(
            listening = false,
            correctionPlaying = true,
            openingBasmalaActive = true,
            correctionPhase = CorrectionPhase.PLAYING_FIRST_ERROR,
            correctionMessage = openingBasmalaCorrectionMessage(request),
            notice = "يُشغّل مقطع كلمة البسملة من آية الحصري فقط؛ لا تُحسب البسملة آية ولا تُقرأ كاملة للتصحيح.",
        )
    }

    private fun playOpeningBasmalaCorrection(request: CorrectionRequest) {
        val sourceAyah = basmalaSourceAyah()
        val timings = timingRepository.forAyah(1, 1)
        val firstWord = sourceAyah?.let { TimingWordMapper.forWord(it, timings, request.localWordIndex) }
        if (firstWord == null) {
            finishOpeningBasmalaCorrection("لا يتوفر مقطع كلمة مستقل لتصحيح البسملة حالياً.")
            return
        }
        val localAyah = assetManager.cachedAudioFile(1, 1)
        val bundledAssetPath = if (BuildConfig.OFFLINE_DISTRIBUTION) assetManager.bundledAudioAssetPath(1, 1) else null
        val ayahSource = when {
            BuildConfig.OFFLINE_DISTRIBUTION -> ""
            localAyah != null -> localAyah.absolutePath
            state.value.mode == AssetMode.STREAM -> assetManager.audioUrl(1, 1)
            else -> null
        }
        if (ayahSource == null) {
            state.value = state.value.copy(correctionPlaying = true, notice = "جاري تنزيل آية الحصري لتشغيل مقطع كلمة البسملة الدقيق…")
            viewModelScope.launch(Dispatchers.IO) {
                val downloaded = runCatching { assetManager.downloadAyah(1, 1) }.getOrNull()
                withContext(Dispatchers.Main) {
                    if (activeBasmalaCorrection != request) return@withContext
                    if (downloaded == null) {
                        finishOpeningBasmalaCorrection("تعذر تنزيل آية الحصري؛ تحقق من الاتصال وحاول مرة أخرى.")
                    } else {
                        playOpeningBasmalaCorrectionSource(request, firstWord, downloaded.absolutePath, null)
                    }
                }
            }
            return
        }
        playOpeningBasmalaCorrectionSource(request, firstWord, ayahSource, bundledAssetPath)
    }

    private fun playOpeningBasmalaCorrectionSource(request: CorrectionRequest, firstWord: TimingSegment, ayahSource: String, bundledAssetPath: String? = null) {
        val generation = ++readerGeneration
        referenceAudioPlayer.playCorrection(
            source = ayahSource,
            firstWord = firstWord,
            wholeAyah = null,
            settings = state.value.correctionSettings,
            firstWordAssetPath = null,
            firstWordSource = QuranComAudio.word(1,1,request.localWordIndex,requireNotNull(basmalaSourceAyah()).words[request.localWordIndex].text,requireNotNull(basmalaSourceAyah()).words.size),
            onWholeAyahStarted = {},
            onFinished = {
                if (generation == readerGeneration) finishOpeningBasmalaCorrection("اكتمل تصحيح كلمة البسملة؛ أعد الكلمة المظللة ثم تابع.")
            },
        )
    }

    private fun finishOpeningBasmalaCorrection(message: String) {
        val request = activeBasmalaCorrection
        activeBasmalaCorrection = null
        val shouldResume = correctionWasListening
        correctionWasListening = false
        if (shouldResume && request != null) {
            openingBasmalaTracker?.resumeFrom(request.localWordIndex)
            openingBasmalaCommittedRaw = ""
            openingBasmalaContinuation = true
            state.value = state.value.copy(
                listening = true,
                correctionPlaying = false,
                openingBasmalaActive = true,
                correctionPhase = CorrectionPhase.WAITING_RESUME,
                correctionMessage = message,
                openingBasmalaWordIndex = request.localWordIndex,
                currentSpokenWord = null,
                currentSpokenWordIndex = -1,
                readerPlaying = false,
                readerCurrentKey = null,
                notice = message,
            )
            if (!writingModeActive) startRecognitionSession()
        } else {
            state.value = state.value.copy(
                listening = false,
                correctionPlaying = false,
                openingBasmalaActive = true,
                correctionPhase = CorrectionPhase.LISTENING,
                correctionMessage = message,
                readerPlaying = false,
                readerCurrentKey = null,
            )
        }
    }

    private fun restartRecognitionAfterOpeningBasmala() {
        openingBasmalaConsumed = true
        openingBasmalaDetected = false
        openingBasmalaNeedsCorrection = false
        recognitionGeneration++
        onlineSession?.stop(); onlineSession = null
        state.value = state.value.copy(
            listening = true,
            openingBasmalaActive = false,
            rawTranscript = "",
            transcript = "",
            currentSpokenWord = null,
            currentSpokenWordIndex = -1,
            notice = "تمت البسملة كجملة مستقلة؛ ابدأ الآن بالآية الأولى.",
        )
        if (!writingModeActive) startRecognitionSession()
    }

    /**
     * Keep the recognizer's primary candidate as observed data. In particular,
     * do not replace it with canonical Word spelling before judgement or display.
     */
    private fun recognizerTextForComparison(update: RecognitionUpdate.Text): String =
        update.providerText.ifBlank { update.value }

    private fun rawSpeechTextForDisplay(update: RecognitionUpdate.Text): String =
        recognizerTextForComparison(update)

    private fun rawSpeechWordForDisplay(update: RecognitionUpdate.Text): String? =
        rawSpeechTextForDisplay(update)
            .trim()
            .split(Regex("\\s+"))
            .lastOrNull(String::isNotBlank)

    private fun openingBasmalaSpeechResult(transcript: String, isFinal: Boolean, segmentCompleted: Boolean): OpeningBasmalaSpeechResult {
        if (openingBasmalaConsumed) {
            return OpeningBasmalaSpeechResult(transcript, consumed = true, needsCorrection = false)
        }
        val current = state.value.allAyahs.getOrNull(state.value.currentAyahIndex)
        if (current == null || current.ayah != 1 || current.surah == 1 || current.surah == 9) {
            openingBasmalaConsumed = true
            openingBasmalaTracker = null
            openingBasmalaCommittedRaw = ""
            return OpeningBasmalaSpeechResult(transcript, consumed = true, needsCorrection = false)
        }
        val candidate = listOf(openingBasmalaCommittedRaw, transcript.trim())
            .filter(String::isNotBlank)
            .joinToString(" ")
        val result = OpeningBasmalaSpeechFilter.filter(
            transcript = candidate,
            isFinal = isFinal,
            segmentCompleted = segmentCompleted,
            alreadyConsumed = false,
            continuation = openingBasmalaContinuation,
            directFirstAyah = !openingBasmalaContinuation && isDirectFirstAyah(candidate),
        )
        openingBasmalaDetected = result.detectedOpeningBasmala
        openingBasmalaNeedsCorrection = false
        if (result.detectedOpeningBasmala && (isFinal || segmentCompleted)) {
            openingBasmalaCommittedRaw = candidate
        }
        return result
    }

    private fun crossedIntoRequiredBasmala(previousAyahIndex: Int, currentAyahIndex: Int): Boolean {
        if (previousAyahIndex !in state.value.allAyahs.indices || currentAyahIndex !in state.value.allAyahs.indices) return false
        val previous = state.value.allAyahs[previousAyahIndex]
        val current = state.value.allAyahs[currentAyahIndex]
        return currentAyahIndex > previousAyahIndex &&
            previous.surah != current.surah &&
            current.ayah == 1 &&
            current.surah != 9
    }

    private fun basmalaSourceAyah(): QuranAyah? =
        state.value.allAyahs.firstOrNull { it.surah == 1 && it.ayah == 1 }

    private fun isDirectFirstAyah(transcript: String): Boolean {
        val ayah = state.value.allAyahs.getOrNull(state.value.currentAyahIndex) ?: return false
        val word = ayah.words.firstOrNull() ?: return false
        val firstToken = transcript.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
        if (firstToken.isBlank()) return false
        val pronunciation = pronunciationReference.word(ayah.surah, ayah.ayah, 0)
        val actual = normalizeArabicForComparison(firstToken)
        val expected = buildSet {
            pronunciation?.let {
                add(it.spoken)
                addAll(it.recognizedForms)
                addAll(it.waqfForms)
            }
            add(word.spokenForm)
            addAll(word.recognizedForms)
            addAll(word.waqfForms)
            add(word.matchText)
        }.map(::normalizeArabicForComparison).filter(String::isNotBlank)
        return expected.any { it == actual || (actual.length > 1 && it.startsWith(actual)) } ||
            matchesWrittenSpelling(word, firstToken) ||
            Muqattaat.isPotentialPrefix(word, firstToken)
    }

    private fun createOpeningBasmalaTracker(ayahIndex: Int): OpeningBasmalaProgressTracker? {
        val ayah = state.value.allAyahs.getOrNull(ayahIndex) ?: return null
        return OpeningBasmalaProgressTracker(
            actualSurah = ayah.surah,
            sourceAyah = basmalaSourceAyah() ?: return null,
            pronunciationReference = pronunciationReference,
            correctionSettings = state.value.correctionSettings,
            allowCanonicalQuranSpelling = state.value.inputMode == RecitationInputMode.TYPING,
        )
    }

    private fun openingBasmalaCorrectionMessage(request: CorrectionRequest): String {
        val word = basmalaSourceAyah()?.words?.getOrNull(request.localWordIndex)?.text.orEmpty()
        return "كلمة البسملة تحتاج تصحيحاً: $word؛ سيُقرأ مقطع الكلمة فقط."
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
        val previousWord = timings.getOrNull(request.localWordIndex - 1)
        val nextWord = timings.getOrNull(request.localWordIndex + 1)
        val wholeAyah = if (request.readWholeAyah) TimingWordMapper.wholeAyah(ayah, timings) else null
        val localAyah = assetManager.cachedAudioFile(ayah.surah, ayah.ayah)
        val bundledAssetPath = if (BuildConfig.OFFLINE_DISTRIBUTION) assetManager.bundledAudioAssetPath(ayah.surah, ayah.ayah) else null
        val ayahSource = when {
            BuildConfig.OFFLINE_DISTRIBUTION -> ""
            localAyah != null -> localAyah.absolutePath
            state.value.mode == AssetMode.STREAM -> assetManager.audioUrl(ayah.surah, ayah.ayah)
            else -> null
        }
        if (ayahSource == null) {
            state.value = state.value.copy(notice = "جاري تنزيل ملف آية الحصري لتشغيل مقطع الكلمة الدقيق…")
            viewModelScope.launch(Dispatchers.IO) {
                val downloaded = runCatching { assetManager.downloadAyah(ayah.surah, ayah.ayah) }.getOrNull()
                withContext(Dispatchers.Main) {
                    if (activeCorrection != request) return@withContext
                    if (downloaded == null) {
                        finishCorrectionPlayback()
                        state.value = state.value.copy(notice = "تعذر تنزيل ملف آية الحصري؛ تحقق من الاتصال وحاول مرة أخرى.")
                    } else {
                        playCorrectionSource(request, firstWord, previousWord, nextWord, wholeAyah, QuranComAudio.word(ayah.surah,ayah.ayah,request.localWordIndex,ayah.words[request.localWordIndex].text,ayah.words.size), downloaded.absolutePath, null)
                    }
                }
            }
            return
        }
        val wordSource = QuranComAudio.word(ayah.surah,ayah.ayah,request.localWordIndex,ayah.words[request.localWordIndex].text,ayah.words.size)
        val wholeAyahSource = ayahSource
        state.value = state.value.copy(
            correctionPlaying = true,
            correctionPhase = CorrectionPhase.PLAYING_FIRST_ERROR,
            correctionMessage = correctionMessage(request),
            notice = when {
                request.readWholeAyah && state.value.correctionSettings.playFirstErrorBeforeWholeAyah -> "يُشغّل مقطع كلمة الحصري الدقيق ثم الآية كاملة ${if (localAyah != null) "من التخزين المحلي" else "بثاً دون حفظ"}…"
                request.readWholeAyah -> "تُشغّل آية الحصري كاملة ${if (localAyah != null) "من التخزين المحلي" else "بثاً دون حفظ"}…"
                else -> "يُشغّل مقطع كلمة الحصري فقط من ملف الآية…"
            },
        )
        playCorrectionSource(request, firstWord, previousWord, nextWord, wholeAyah, wordSource, wholeAyahSource, bundledAssetPath)
    }

    private fun playCorrectionSource(
        request: CorrectionRequest,
        firstWord: TimingSegment,
        previousWord: TimingSegment?,
        nextWord: TimingSegment?,
        wholeAyah: TimingSegment?,
        wordSource: String,
        wholeAyahSource: String?,
        bundledAssetPath: String? = null,
    ) {
        val finished = { finishCorrectionPlayback() }
        val source = wholeAyahSource ?: wordSource
        val wholeStarted = { state.value = state.value.copy(correctionPhase = CorrectionPhase.PLAYING_WHOLE_AYAH, notice = "تُقرأ الآية كاملة للتصحيح…") }
        referenceAudioPlayer.playCorrection(
            source = source,
            firstWord = firstWord,
            previousWord = previousWord,
            nextWord = nextWord,
            wholeAyah = wholeAyah,
            settings = state.value.correctionSettings,
            firstWordAssetPath = null,
            wholeAyahAssetPath = bundledAssetPath,
            firstWordSource = wordSource,
            wholeAyahSource = wholeAyahSource ?: source,
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
            if (!writingModeActive) startRecognitionSession()
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
        progressTracker = null
        openingBasmalaTracker = null
        activeCorrection = null
        activeBasmalaCorrection = null
        correctionWasListening = false
        openingBasmalaConsumed = true
        openingBasmalaDetected = false
        openingBasmalaNeedsCorrection = false
        openingBasmalaCommittedRaw = ""
        openingBasmalaContinuation = false
        writingModeActive = false
        state.value = state.value.copy(
            listening = false,
            correctionPlaying = false,
            openingBasmalaActive = false,
            openingBasmalaEvaluation = null,
            openingBasmalaWordIndex = -1,
            correctionPhase = CorrectionPhase.LISTENING,
            readerPlaying = false,
            readerCurrentKey = null,
        )
    }

    /** Start after the disjoined opening letters; the word index is within [ayah]. */
    fun startMemorizationAfterOpening(surah: Int, ayah: Int, wordIndex: Int) {
        val metadata = state.value.metadata ?: return
        val pageNumber = metadata.ayah(surah, ayah)?.page ?: return
        stopListening()
        setMemorizationStart(surah, ayah, wordIndex)
        showPage(pageNumber, notice = "تم تحديد بداية التسميع بعد الحروف المقطعة: السورة $surah، الآية $ayah.")
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
        val first = state.value.ayahs.firstOrNull() ?: return
        val last = state.value.ayahs.lastOrNull() ?: first
        val checkpoint = state.value.checkpoint
        val now = System.currentTimeMillis()
        store.append(
            SessionSummary(
                id = java.util.UUID.randomUUID().toString(),
                surah = first.surah,
                startAyah = first.ayah,
                endAyah = last.ayah,
                matchedWords = evaluation.matched,
                totalWords = evaluation.total,
                uncertainWords = evaluation.uncertain,
                createdAt = now,
                label = "جلسة تسميع ${first.surah}:${first.ayah}",
                page = state.value.currentPage,
                wordIndex = state.value.currentWordInAyah.coerceAtLeast(0),
                scope = ReaderScope.AYAH.name,
                checkpointSurah = checkpoint?.surah ?: first.surah,
                checkpointAyah = checkpoint?.ayah ?: first.ayah,
                checkpointWordIndex = checkpoint?.wordIndex ?: state.value.currentWordInAyah.coerceAtLeast(0),
                includeOpeningBasmala = checkpoint?.includeOpeningBasmala == true,
                lastOpenedAt = now,
            ),
        )
        goalStore.addCompleted(1)
        state.value = state.value.copy(sessions = store.list(), goal = goalStore.current(), notice = "حُفظت الجلسة محلياً على الجهاز، وستظهر أولاً عند فتح السجل.")
    }

    fun saveBookmark(surah: Int, ayah: Int) {
        val target = state.value.allAyahs.firstOrNull { it.surah == surah && it.ayah == ayah } ?: return
        val now = System.currentTimeMillis()
        store.append(
            SessionSummary(
                id = java.util.UUID.randomUUID().toString(),
                surah = target.surah,
                startAyah = target.ayah,
                endAyah = target.ayah,
                matchedWords = 0,
                totalWords = target.words.size,
                uncertainWords = target.words.size,
                createdAt = now,
                label = "مرجع قراءة ${target.surah}:${target.ayah}",
                page = state.value.metadata?.ayah(target.surah, target.ayah)?.page ?: state.value.currentPage,
                wordIndex = 0,
                scope = ReaderScope.AYAH.name,
                checkpointSurah = target.surah,
                checkpointAyah = target.ayah,
                checkpointWordIndex = 0,
                includeOpeningBasmala = target.ayah == 1 && QuranPresentation.openingBasmalaForSurah(target.surah) != null,
                lastOpenedAt = now,
            ),
        )
        state.value = state.value.copy(sessions = store.list(), notice = "حُفظ هذا الموضع كمرجع محلي.")
    }

    fun openSession(id: String) {
        val session = store.list().firstOrNull { it.id == id } ?: return
        val targetSurah = session.checkpointSurah ?: session.surah
        val targetAyah = session.checkpointAyah ?: session.startAyah
        val targetIndex = state.value.allAyahs.indexOfFirst { it.surah == targetSurah && it.ayah == targetAyah }
        if (targetIndex < 0) {
            state.value = state.value.copy(notice = "تعذر فتح المرجع؛ الموضع غير موجود في النص المحمّل.")
            return
        }
        val checkpoint = MemorizationCheckpoint(
            targetSurah,
            targetAyah,
            (session.checkpointWordIndex ?: session.wordIndex).coerceAtLeast(0),
            session.includeOpeningBasmala,
            System.currentTimeMillis(),
        ).validatedAgainst(state.value.allAyahs)
        if (checkpoint == null) {
            state.value = state.value.copy(notice = "تعذر فتح المرجع؛ نقطة الاستئناف غير صالحة.")
            return
        }
        memorizationProgressStore.save(checkpoint)
        store.touch(session.id)
        val pageNumber = session.page.takeIf { it > 0 } ?: pageForAyahIndex(targetIndex)
        showPage(pageNumber, notice = "فُتح ${session.label}، ويمكنك الاستماع أو الاستئناف من موضعه.")
        state.value = state.value.copy(checkpoint = checkpoint, sessions = store.list())
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

    override fun onCleared() { recognitionGeneration++; onlineSession?.stop(); progressTracker = null; referenceAudioPlayer.stop(); super.onCleared() }

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
