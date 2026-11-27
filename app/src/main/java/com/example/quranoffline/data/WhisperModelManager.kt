package com.example.quranoffline.data

import android.content.Context
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Download / pause / delete / import of the Whisper model plus the engine preference. */
class WhisperModelManager private constructor(private val context: Context) {
    data class State(val running: Boolean = false, val message: String = "")
    val store = WhisperModelStore(java.io.File(context.filesDir, "whisper"))
    private val audio = AudioDownloadManager.get(context)
    private val prefs = context.getSharedPreferences("quran_engine", Context.MODE_PRIVATE)
    private val pool = Executors.newSingleThreadExecutor()
    @Volatile private var cancel = false
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision

    var choice: EngineChoice
        get() = runCatching { EngineChoice.valueOf(prefs.getString("engine", "SYSTEM")!!) }.getOrDefault(EngineChoice.SYSTEM)
        set(v) { prefs.edit().putString("engine", v.name).apply() }

    val arm64: Boolean get() = android.os.Build.SUPPORTED_ABIS.contains("arm64-v8a") && WhisperNative.available

    fun effectiveEngine() = EngineSelector.select(choice, arm64, store.ready())

    fun start() {
        if (_state.value.running) return
        store.ensureDir()
        if (context.filesDir.usableSpace < WhisperModelStore.SIZE + 50L * 1024 * 1024) { _state.value = State(false, "المساحة غير كافية"); return }
        cancel = false; _state.value = State(true, "جارٍ التنزيل")
        pool.execute {
          try {
            val done = java.io.File(store.part().path + ".done")
            val r = AudioDownloader(store = AudioPackStore(java.io.File(context.filesDir, "whisper_unused")), networkAllowed = audio::networkAllowed)
                .let { if (!audio.networkAllowed()) AudioDownloader.Result.Failed("الشبكة غير متاحة أو الوضع Wi-Fi فقط مفعّل") else it.fetch(WhisperModelStore.URL, store.part(), done) { cancel } }
            val msg = when (r) {
                is AudioDownloader.Result.Cancelled -> "موقوف مؤقتًا، يمكن المتابعة"
                is AudioDownloader.Result.Failed -> r.reason
                is AudioDownloader.Result.Done -> if (done.renameTo(store.part()) && store.verifyAndInstall()) "اكتمل التنزيل وتم التحقق من البصمة" else "فشل التحقق من البصمة (sha256)، حُذف الملف"
            }
            _state.value = State(false, msg); _revision.value++
          } catch (e: Throwable) { _state.value = State(false, "خطأ غير متوقع: ${e.message ?: e.javaClass.simpleName}") }
        }
    }
    fun pause() { cancel = true }
    fun delete() { cancel = true; store.delete(); if (choice == EngineChoice.WHISPER) choice = EngineChoice.SYSTEM; _state.value = State(); _revision.value++ }
    fun importUri(uri: android.net.Uri) {
        pool.execute {
            val ok = runCatching { context.contentResolver.openInputStream(uri)?.let { store.importFrom(it) } == true }.getOrDefault(false)
            _state.value = State(false, if (ok) "تم استيراد النموذج والتحقق منه" else "الملف غير مطابق للنموذج المطلوب"); _revision.value++
        }
    }
    companion object {
        @Volatile private var inst: WhisperModelManager? = null
        fun get(c: Context) = inst ?: synchronized(this) { inst ?: WhisperModelManager(c.applicationContext).also { inst = it } }
    }
}
