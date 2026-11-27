package com.example.quranoffline.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/** Builds the active engine. Whisper is used only when chosen, arm64 and the verified model is present. */
object SpeechEngineFactory {
    fun create(context: Context, onUpdate: (RecognitionUpdate) -> Unit): SpeechEngine {
        val mgr = WhisperModelManager.get(context)
        if (mgr.effectiveEngine() != EngineChoice.WHISPER) return OnlineSpeechSession(context, onUpdate)
        return FallbackEngine(context, onUpdate, mgr.store.model().absolutePath)
    }

    private class FallbackEngine(val context: Context, val onUpdate: (RecognitionUpdate) -> Unit, val path: String) : SpeechEngine {
        @Volatile private var current: SpeechEngine? = null
        @Volatile private var stopped = false
        private val main = Handler(Looper.getMainLooper())
        override fun start() {
            current = WhisperSpeechSession(path, onUpdate, { why ->
                main.post {
                    if (stopped) return@post
                    Toast.makeText(context, "تعذر تشغيل Whisper ($why). تم التحويل إلى تعرف النظام عبر الإنترنت.", Toast.LENGTH_LONG).show()
                    current = OnlineSpeechSession(context, onUpdate).also { it.start() }
                }
            }).also { it.start() }
        }
        override fun stop() { stopped = true; current?.stop(); current = null }
    }
}
