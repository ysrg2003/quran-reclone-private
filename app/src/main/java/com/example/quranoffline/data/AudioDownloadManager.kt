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

    companion object {
        @Volatile private var inst: AudioDownloadManager? = null
        fun get(context: Context) = inst ?: synchronized(this) { inst ?: AudioDownloadManager(context.applicationContext).also { inst = it } }
    }
}
