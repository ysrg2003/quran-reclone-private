package com.example.quranoffline.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Per-reciter ayah-audio downloads for the Settings UI. Streaming stays the default when nothing is downloaded. */
class AudioDownloadManager private constructor(private val context: Context) {
    data class JobState(val running: Boolean = false, val done: Int = 0, val total: Int = 0, val message: String = "")

    private val prefs = context.getSharedPreferences("quran_audio_download", Context.MODE_PRIVATE)
    private val sizes = context.getSharedPreferences("quran_audio_sizes", Context.MODE_PRIVATE)
    private val pool = Executors.newFixedThreadPool(1)
    private val measurePool = Executors.newFixedThreadPool(1)
    private val cancel = ConcurrentHashMap<Int, Boolean>()
    private val measuring = ConcurrentHashMap<Int, Boolean>()
    private val _jobs = MutableStateFlow<Map<Int, JobState>>(emptyMap())
    val jobs: StateFlow<Map<Int, JobState>> = _jobs
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision
    private val _measure = MutableStateFlow<Map<Int, String>>(emptyMap())
    /** reciterId -> progress / status text for lazy size measurement. */
    val measureStatus: StateFlow<Map<Int, String>> = _measure

    fun store(reciter: Int) = AudioPackStore(context, reciter)
    fun key(reciter: Int, surah: Int) = reciter * 1000 + surah

    var wifiOnly: Boolean
        get() = prefs.getBoolean("wifi_only", true)
        set(v) { prefs.edit().putBoolean("wifi_only", v).apply() }

    fun networkAllowed(): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (!wifiOnly || !cm.isActiveNetworkMetered || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
    } catch (e: Exception) {
        // Never crash the app because the network state cannot be read; honor Wi-Fi-only conservatively.
        !wifiOnly
    }

    /** Known size in bytes of one surah for a reciter, or null when not yet measured. */
    fun surahSize(reciter: Int, surah: Int): Long? =
        if (reciter == Reciters.DEFAULT_ID) AudioPackSizes.surah(surah)
        else sizes.getLong("s_${reciter}_$surah", -1L).takeIf { it >= 0 }
    fun totalSize(reciter: Int): Long? {
        if (reciter == Reciters.DEFAULT_ID) return AudioPackSizes.total
        var t = 0L
        for (s in 1..114) t += surahSize(reciter, s) ?: return null
        return t
    }
    fun measuredSurahs(reciter: Int) = (1..114).count { surahSize(reciter, it) != null }

    /** Lazily measures sizes with HEAD requests on a background thread; never blocks the UI. */
    fun measure(reciter: Int, ayahCounts: List<Int>) {
        if (reciter == Reciters.DEFAULT_ID || measuring.putIfAbsent(reciter, true) != null) return
        measurePool.execute {
          try {
            val dl = AudioDownloader(store(reciter), reciter, ::networkAllowed)
            try {
                for (s in 1..114) {
                    if (measuring[reciter] != true) break
                    if (surahSize(reciter, s) != null) continue
                    if (!networkAllowed()) { _measure.value = _measure.value + (reciter to "الشبكة غير متاحة"); break }
                    val urls = runCatching { dl.listUrls(s) }.getOrNull() ?: continue
                    var sum = 0L; var ok = true
                    for (a in 1..ayahCounts[s - 1]) {
                        val len = urls[a]?.let { dl.contentLength(it) }
                        if (len == null) { ok = false; break }
                        sum += len
                    }
                    if (ok) sizes.edit().putLong("s_${reciter}_$s", sum).apply()
                    _measure.value = _measure.value + (reciter to "تم قياس ${measuredSurahs(reciter)}/114 سورة")
                    _revision.value++
                }
            } finally { measuring.remove(reciter); _revision.value++ }
          } catch (e: Throwable) { _measure.value = _measure.value + (reciter to "تعذر القياس: ${e.message ?: e.javaClass.simpleName}") }
        }
    }
    fun stopMeasure(reciter: Int) { measuring.remove(reciter) }

    private fun set(k: Int, st: JobState) { _jobs.value = _jobs.value + (k to st) }

    fun start(reciter: Int, surah: Int, ayahCount: Int) {
        val k = key(reciter, surah)
        if (_jobs.value[k]?.running == true) return
        val store = store(reciter); store.ensureDir()
        val need = (surahSize(reciter, surah) ?: (ayahCount * 250_000L)) - store.surahBytes(surah)
        if (context.filesDir.usableSpace < need + 50L * 1024 * 1024) { set(k, JobState(message = "المساحة غير كافية")); return }
        cancel[k] = false
        set(k, JobState(true, store.surahCount(surah), ayahCount, "جارٍ التنزيل"))
        pool.execute {
            val r = try { AudioDownloader(store, reciter, ::networkAllowed).downloadSurah(surah, ayahCount, { cancel[k] == true }) { d, t ->
                set(k, JobState(true, d, t, "جارٍ التنزيل"))
            } } catch (e: Throwable) { AudioDownloader.Result.Failed("خطأ غير متوقع: ${e.message ?: e.javaClass.simpleName}") }
            val msg = when (r) {
                is AudioDownloader.Result.Done -> "اكتمل التنزيل"
                is AudioDownloader.Result.Cancelled -> "موقوف مؤقتًا، يمكن المتابعة"
                is AudioDownloader.Result.Failed -> r.reason
            }
            set(k, JobState(false, store.surahCount(surah), ayahCount, msg))
            _revision.value++
        }
    }

    fun pause(reciter: Int, surah: Int) { cancel[key(reciter, surah)] = true }
    fun delete(reciter: Int, surah: Int) { val k = key(reciter, surah); cancel[k] = true; store(reciter).deleteSurah(surah); set(k, JobState()); _revision.value++ }
    fun deleteReciter(reciter: Int) { for (s in 1..114) cancel[key(reciter, s)] = true; store(reciter).deleteAll(); _jobs.value = _jobs.value.filterKeys { it / 1000 != reciter }; _revision.value++ }
    fun deleteAll() { _jobs.value.keys.forEach { cancel[it] = true }; AudioPackStore.deleteEverything(context); _jobs.value = emptyMap(); _revision.value++ }
    fun usedBytes(reciter: Int) = store(reciter).usedBytes()
    fun usedBytesAll() = AudioPackStore.usedBytesAll(context)


    // ---------- word audio ----------
    val wordStore = WordPackStore(context)
    private val _wordJobs = MutableStateFlow<Map<Int, JobState>>(emptyMap())
    val wordJobs: StateFlow<Map<Int, JobState>> = _wordJobs
    private val wordCancel = ConcurrentHashMap<Int, Boolean>()
    private val wordPool = Executors.newFixedThreadPool(1)
    private val wordMeasuring = ConcurrentHashMap<Int, Boolean>()
    private fun setWord(s: Int, st: JobState) { _wordJobs.value = _wordJobs.value + (s to st) }
    private fun wordHttp() = AudioDownloader(AudioPackStore(context), Reciters.DEFAULT_ID, ::networkAllowed)
    private fun wordDl() = WordDownloader(wordStore, ::networkAllowed, wordHttp())

    /** Measured bytes of a surah's word files, or null. */
    fun wordSurahSize(s: Int): Long? = sizes.getLong("w_$s", -1L).takeIf { it >= 0 }
    fun wordMeasured() = (1..114).count { wordSurahSize(it) != null }
    fun wordMeasuredBytes() = (1..114).sumOf { wordSurahSize(it) ?: 0L }
    /** Real total when every surah is measured; otherwise an estimate (measured average, or 57 KB/word from one sample). */
    fun wordTotal(): Pair<Long, Boolean> {
        if (wordMeasured() == 114) return wordMeasuredBytes() to true
        return (TOTAL_WORDS * 57_000L) to false
    }

    fun startWords(surah: Int) {
        if (_wordJobs.value[surah]?.running == true) return
        wordStore.ensureDir()
        val need = (wordSurahSize(surah) ?: 8_000_000L) - wordStore.surahBytes(surah)
        if (context.filesDir.usableSpace < need + 50L * 1024 * 1024) { setWord(surah, JobState(message = "المساحة غير كافية")); return }
        wordCancel[surah] = false
        setWord(surah, JobState(true, wordStore.surahCount(surah), 0, "جارٍ التنزيل"))
        wordPool.execute { runWordSurah(surah); _revision.value++ }
    }

    private fun runWordSurah(surah: Int): AudioDownloader.Result {
        val r = try { wordDl().downloadSurah(surah, { wordCancel[surah] == true }) { d, t -> setWord(surah, JobState(true, d, t, "جارٍ التنزيل")) } }
        catch (e: Throwable) { AudioDownloader.Result.Failed("خطأ غير متوقع: ${e.message ?: e.javaClass.simpleName}") }
        val msg = when (r) {
            is AudioDownloader.Result.Done -> "اكتمل التنزيل"
            is AudioDownloader.Result.Cancelled -> "موقوف مؤقتًا، يمكن المتابعة"
            is AudioDownloader.Result.Failed -> r.reason
        }
        if (r is AudioDownloader.Result.Done) sizes.edit().putLong("w_$surah", wordStore.surahBytes(surah)).apply()
        setWord(surah, JobState(false, wordStore.surahCount(surah), 0, msg))
        return r
    }
    fun pauseWords(surah: Int) { wordCancel[surah] = true }
    fun deleteWords(surah: Int) { wordCancel[surah] = true; wordStore.deleteSurah(surah); sizes.edit().remove("w_$surah").apply(); setWord(surah, JobState()); _revision.value++ }
    fun deleteAllWords() { _wordJobs.value.keys.forEach { wordCancel[it] = true }; wordStore.deleteAll(); _wordJobs.value = emptyMap(); (1..114).forEach { sizes.edit().remove("w_$it").apply() }; _revision.value++ }

    /** Lazy HEAD measurement of one surah's word files. */
    fun measureWords(surahs: List<Int>) {
        for (s in surahs) {
            if (wordSurahSize(s) != null || wordMeasuring.putIfAbsent(s, true) != null) continue
            measurePool.execute {
                try {
                    val dl = wordDl(); val http = wordHttp()
                    var sum = 0L; var ok = true
                    for (e in dl.wordUrls(s)) { val l = http.contentLength(e.url); if (l == null) { ok = false; break }; sum += l }
                    if (ok) sizes.edit().putLong("w_$s", sum).apply()
                } catch (e: Throwable) { } finally { wordMeasuring.remove(s); _revision.value++ }
            }
        }
    }

    // ---------- download the entire Quran ----------
    enum class BulkKind { AYAH, WORDS }
    data class BulkState(val kind: BulkKind? = null, val running: Boolean = false, val surah: Int = 0, val doneSurahs: Int = 0, val message: String = "")
    private val _bulk = MutableStateFlow(BulkState())
    val bulk: StateFlow<BulkState> = _bulk
    @Volatile private var bulkCancel = false
    private val bulkPool = Executors.newFixedThreadPool(1)

    fun freeBytes() = context.filesDir.usableSpace

    fun startBulk(kind: BulkKind, reciter: Int, ayahCounts: List<Int>) {
        if (_bulk.value.running) return
        bulkCancel = false
        _bulk.value = BulkState(kind, true, 0, 0, "بدأ التنزيل")
        bulkPool.execute {
            try {
                var done = 0
                for (s in 1..114) {
                    if (bulkCancel) { _bulk.value = BulkState(kind, false, s, done, "موقوف مؤقتًا، يمكن المتابعة"); return@execute }
                    val have = if (kind == BulkKind.AYAH) store(reciter).surahCount(s) == ayahCounts[s - 1] else wordSurahSize(s) != null
                    if (have) { done++; continue }
                    val need = if (kind == BulkKind.AYAH) (surahSize(reciter, s) ?: ayahCounts[s - 1] * 250_000L) else (wordSurahSize(s) ?: 8_000_000L)
                    if (freeBytes() < need + 100L * 1024 * 1024) { _bulk.value = BulkState(kind, false, s, done, "توقف: المساحة غير كافية عند السورة $s"); return@execute }
                    _bulk.value = BulkState(kind, true, s, done, "جارٍ تنزيل السورة $s")
                    val r = if (kind == BulkKind.AYAH) {
                        val k = key(reciter, s); cancel[k] = false
                        set(k, JobState(true, 0, ayahCounts[s - 1], "جارٍ التنزيل"))
                        val res = try { AudioDownloader(store(reciter), reciter, ::networkAllowed).downloadSurah(s, ayahCounts[s - 1], { bulkCancel || cancel[k] == true }) { d, t -> set(k, JobState(true, d, t, "جارٍ التنزيل")) } }
                            catch (e: Throwable) { AudioDownloader.Result.Failed("خطأ: ${e.message ?: e.javaClass.simpleName}") }
                        set(k, JobState(false, store(reciter).surahCount(s), ayahCounts[s - 1], if (res is AudioDownloader.Result.Done) "اكتمل التنزيل" else "")); res
                    } else { wordCancel[s] = false; val rr = runWordSurahBulk(s); rr }
                    when (r) {
                        is AudioDownloader.Result.Done -> done++
                        is AudioDownloader.Result.Cancelled -> { _bulk.value = BulkState(kind, false, s, done, "موقوف مؤقتًا، يمكن المتابعة"); _revision.value++; return@execute }
                        is AudioDownloader.Result.Failed -> { _bulk.value = BulkState(kind, false, s, done, "توقف عند السورة $s: ${r.reason}"); _revision.value++; return@execute }
                    }
                    _revision.value++
                }
                _bulk.value = BulkState(kind, false, 114, done, "اكتمل تنزيل القرآن كاملاً")
            } catch (e: Throwable) {
                _bulk.value = BulkState(kind, false, 0, 0, "خطأ غير متوقع: ${e.message ?: e.javaClass.simpleName}")
            } finally { _revision.value++ }
        }
    }
    private fun runWordSurahBulk(s: Int): AudioDownloader.Result {
        val r = try { wordDl().downloadSurah(s, { bulkCancel || wordCancel[s] == true }) { d, t -> setWord(s, JobState(true, d, t, "جارٍ التنزيل")) } }
        catch (e: Throwable) { AudioDownloader.Result.Failed("خطأ: ${e.message ?: e.javaClass.simpleName}") }
        if (r is AudioDownloader.Result.Done) sizes.edit().putLong("w_$s", wordStore.surahBytes(s)).apply()
        setWord(s, JobState(false, wordStore.surahCount(s), 0, if (r is AudioDownloader.Result.Done) "اكتمل التنزيل" else ""))
        return r
    }
    fun pauseBulk() { bulkCancel = true }

    companion object {
        const val TOTAL_WORDS = 77_430L
        @Volatile private var inst: AudioDownloadManager? = null
        fun get(context: Context) = inst ?: synchronized(this) { inst ?: AudioDownloadManager(context.applicationContext).also { inst = it } }
    }
}
