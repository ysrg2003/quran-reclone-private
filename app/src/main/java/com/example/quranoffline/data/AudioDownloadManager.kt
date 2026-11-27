package com.example.quranoffline.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Ties the store, downloader and the Wi-Fi-only preference together for the Settings UI. */
class AudioDownloadManager private constructor(private val context: Context) {
    data class JobState(val running: Boolean = false, val done: Int = 0, val total: Int = 0, val message: String = "")

    val store = AudioPackStore(context)
    private val prefs = context.getSharedPreferences("quran_audio_download", Context.MODE_PRIVATE)
    private val pool = Executors.newFixedThreadPool(1)
    private val cancel = ConcurrentHashMap<Int, Boolean>()
    private val _jobs = MutableStateFlow<Map<Int, JobState>>(emptyMap())
    val jobs: StateFlow<Map<Int, JobState>> = _jobs
    private val _revision = MutableStateFlow(0)
    /** Bumps whenever files change so the UI recomputes sizes. */
    val revision: StateFlow<Int> = _revision

    var wifiOnly: Boolean
        get() = prefs.getBoolean("wifi_only", true)
        set(v) { prefs.edit().putBoolean("wifi_only", v).apply() }

    fun networkAllowed(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        if (!wifiOnly) return true
        return !cm.isActiveNetworkMetered || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun set(s: Int, st: JobState) { _jobs.value = _jobs.value + (s to st) }

    fun start(surah: Int, ayahCount: Int) {
        if (_jobs.value[surah]?.running == true) return
        store.ensureDir()
        val free = context.filesDir.usableSpace
        val need = AudioPackSizes.surah(surah) - store.surahBytes(surah)
        if (free < need + 50L * 1024 * 1024) { set(surah, JobState(message = "المساحة غير كافية")); return }
        cancel[surah] = false
        set(surah, JobState(true, store.surahCount(surah), ayahCount, "جارٍ التنزيل"))
        pool.execute {
            val r = AudioDownloader(store, ::networkAllowed).downloadSurah(surah, ayahCount, { cancel[surah] == true }) { d, t ->
                set(surah, JobState(true, d, t, "جارٍ التنزيل"))
            }
            val msg = when (r) {
                is AudioDownloader.Result.Done -> "اكتمل التنزيل"
                is AudioDownloader.Result.Cancelled -> "موقوف مؤقتًا، يمكن المتابعة"
                is AudioDownloader.Result.Failed -> r.reason
            }
            set(surah, JobState(false, store.surahCount(surah), ayahCount, msg))
            _revision.value++
        }
    }

    fun pause(surah: Int) { cancel[surah] = true }
    fun delete(surah: Int) { cancel[surah] = true; store.deleteSurah(surah); set(surah, JobState()); _revision.value++ }
    fun deleteAll() { _jobs.value.keys.forEach { cancel[it] = true }; store.deleteAll(); _jobs.value = emptyMap(); _revision.value++ }

    companion object {
        @Volatile private var inst: AudioDownloadManager? = null
        fun get(context: Context) = inst ?: synchronized(this) { inst ?: AudioDownloadManager(context.applicationContext).also { inst = it } }
    }
}
