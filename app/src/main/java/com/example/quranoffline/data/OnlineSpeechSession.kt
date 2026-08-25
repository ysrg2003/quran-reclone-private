package com.example.quranoffline.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/** Online recognizer with automatic recovery from normal speech pauses. */
class OnlineSpeechSession(
    context: Context,
    private val onUpdate: (RecognitionUpdate) -> Unit,
) {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val active = AtomicBoolean(false)
    private var restartScheduled = false
    private var committedText = ""
    private var currentPartialText = ""
    private var lastEmittedCombinedText = ""
    private var lastRmsLogAt = 0L
    private val restartRunnable = Runnable {
        restartScheduled = false
        if (active.get()) startPlatformListening()
    }
    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        // Use the generic Arabic locale: some installed Google recognizers
        // advertise ar-SA but return ERROR_NO_MATCH for ordinary Arabic input.
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar")
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // Quranic words can contain deliberately long madd; do not finalize a
        // segment after a short breath while the word is still being extended.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, FINAL_SILENCE_MS)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, MINIMUM_SPEECH_MS)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, POSSIBLY_COMPLETE_SILENCE_MS)
    }

    init {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "SpeechRecognizer ready")
                onUpdate(RecognitionUpdate.Started)
            }
            override fun onBeginningOfSpeech() {
                Log.d(TAG, "SpeechRecognizer beginning")
            }
            override fun onRmsChanged(rmsdB: Float) {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastRmsLogAt >= 1_000L) {
                    lastRmsLogAt = now
                    Log.d(TAG, "SpeechRecognizer rms=$rmsdB")
                }
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                Log.d(TAG, "SpeechRecognizer end of speech")
            }

            override fun onError(error: Int) {
                if (!active.get()) return
                Log.w(TAG, "SpeechRecognizer error code=$error")
                if (error in TRANSIENT_ERRORS) {
                    runCatching { recognizer.cancel() }
                    val message = if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                        "لم تفهم خدمة التعرف هذا المقطع (رمز 7)؛ الميكروفون مفتوح، جارٍ إعادة الاستماع…"
                    } else {
                        "تعذر التقاط الصوت مؤقتاً (رمز $error)؛ جارٍ إعادة الاستماع…"
                    }
                    onUpdate(RecognitionUpdate.Retrying(message))
                    scheduleRestart()
                } else {
                    active.set(false)
                    onUpdate(RecognitionUpdate.Failed("تعذر التعرف عبر الإنترنت، رمز الخطأ $error."))
                }
            }

            override fun onResults(results: Bundle?) {
                Log.d(TAG, "SpeechRecognizer final callback")
                emit(results, isFinal = true)
                if (active.get()) scheduleRestart()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                emit(partialResults, isFinal = false)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            private fun emit(bundle: Bundle?, isFinal: Boolean) {
                val text = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isBlank()) {
                    Log.d(TAG, "SpeechRecognizer callback without text final=$isFinal")
                    return
                }
                Log.d(TAG, "SpeechRecognizer text callback final=$isFinal chars=${text.length}")
                if (isFinal) {
                    committedText = joinTexts(committedText, text)
                    currentPartialText = ""
                } else {
                    currentPartialText = text
                }
                val combined = joinTexts(committedText, currentPartialText)
                if (!isFinal && combined == lastEmittedCombinedText) return
                lastEmittedCombinedText = combined
                onUpdate(RecognitionUpdate.Text(combined, FloatArray(0), isFinal = isFinal, segmentCompleted = isFinal))
            }
        })
    }

    fun start() {
        if (!active.compareAndSet(false, true)) return
        committedText = ""
        currentPartialText = ""
        lastEmittedCombinedText = ""
        onUpdate(RecognitionUpdate.Started)
        startPlatformListening()
    }

    fun stop() {
        if (!active.getAndSet(false)) return
        restartScheduled = false
        mainHandler.removeCallbacks(restartRunnable)
        if (currentPartialText.isNotBlank()) {
            onUpdate(RecognitionUpdate.Text(joinTexts(committedText, currentPartialText), FloatArray(0), isFinal = true, segmentCompleted = true))
        }
        runCatching { recognizer.stopListening() }
        runCatching { recognizer.cancel() }
        runCatching { recognizer.destroy() }
        onUpdate(RecognitionUpdate.Stopped)
    }

    private fun startPlatformListening() {
        if (!active.get()) return
        runCatching { recognizer.startListening(intent) }
            .onFailure {
                active.set(false)
                onUpdate(RecognitionUpdate.Failed("تعذر تشغيل التعرف عبر الإنترنت: ${it.message ?: "خطأ غير معروف"}"))
            }
    }

    private fun scheduleRestart() {
        if (!active.get() || restartScheduled) return
        restartScheduled = true
        mainHandler.postDelayed(restartRunnable, RESTART_DELAY_MS)
    }

    private fun joinTexts(first: String, second: String): String = listOf(first.trim(), second.trim())
        .filter(String::isNotBlank)
        .joinToString(" ")

    companion object {
        private const val TAG = "QuranSpeechSession"
        private const val RESTART_DELAY_MS = 280L
        private const val MINIMUM_SPEECH_MS = 350L
        private const val POSSIBLY_COMPLETE_SILENCE_MS = 2_000L
        private const val FINAL_SILENCE_MS = 2_400L
        private val TRANSIENT_ERRORS = setOf(
            SpeechRecognizer.ERROR_AUDIO,
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
        )
    }
}
