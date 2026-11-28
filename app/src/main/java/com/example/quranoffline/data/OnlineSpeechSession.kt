package com.example.quranoffline.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.concurrent.atomic.AtomicBoolean

/** Online recognizer with automatic recovery from normal speech pauses. */
class OnlineSpeechSession(
    context: Context,
    private val onUpdate: (RecognitionUpdate) -> Unit,
) : SpeechEngine {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val active = AtomicBoolean(false)
    private var restartScheduled = false
    private var committedText = ""
    private var currentPartialText = ""
    private var lastEmittedCombinedText = ""
    private val restartRunnable = Runnable {
        restartScheduled = false
        if (active.get()) startPlatformListening()
    }
    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(Muqattaat.biasingPhrases()))
        }
        // Quranic words can contain deliberately long madd; do not finalize a
        // segment after a short breath while the word is still being extended.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, FINAL_SILENCE_MS)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, MINIMUM_SPEECH_MS)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, POSSIBLY_COMPLETE_SILENCE_MS)
    }

    init {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onUpdate(RecognitionUpdate.Started)
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                if (!active.get()) return
                if (error in TRANSIENT_ERRORS) {
                    // ERROR_NO_MATCH and normal speech timeouts are recoverable:
                    // keep the session active and start a fresh provider segment.
                    scheduleRestart()
                } else {
                    active.set(false)
                    onUpdate(RecognitionUpdate.Failed("تعذر التعرف عبر الإنترنت، رمز الخطأ $error."))
                }
            }

            override fun onResults(results: Bundle?) {
                emit(results, isFinal = true)
                if (active.get()) scheduleRestart()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                emit(partialResults, isFinal = false)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            private fun emit(bundle: Bundle?, isFinal: Boolean) {
                val alternatives = bundle
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    .orEmpty()
                val text = chooseAlternative(alternatives)
                if (text.isBlank()) return
                if (isFinal) {
                    committedText = joinTexts(committedText, text)
                    currentPartialText = ""
                } else {
                    currentPartialText = text
                }
                val combined = joinTexts(committedText, currentPartialText)
                if (!isFinal && combined == lastEmittedCombinedText) return
                lastEmittedCombinedText = combined
                // value is the accumulated raw candidate consumed by the state
                // machine; providerText is the exact latest provider candidate
                // used by the raw display layer.
                onUpdate(
                    RecognitionUpdate.Text(
                        value = combined,
                        timestamps = FloatArray(0),
                        isFinal = isFinal,
                        segmentCompleted = isFinal,
                        providerText = text,
                        endOfUtterance = isFinal,
                    ),
                )
            }
        })
    }

    /**
     * Top result unless it looks like a truncated muqatta'at spelling (a long madd that the recognizer
     * cut short) and a lower-ranked alternative carries the complete letter-name sequence.
     */
    private fun chooseAlternative(alternatives: List<String>): String {
        val top = alternatives.firstOrNull().orEmpty()
        if (alternatives.size < 2 || !Muqattaat.looksTruncated(top)) return top
        return alternatives.drop(1).firstOrNull { Muqattaat.startsWithFullSequence(it) } ?: top
    }

    override fun start() {
        if (!active.compareAndSet(false, true)) return
        committedText = ""
        currentPartialText = ""
        lastEmittedCombinedText = ""
        onUpdate(RecognitionUpdate.Started)
        startPlatformListening()
    }

    override fun stop() {
        if (!active.getAndSet(false)) return
        restartScheduled = false
        mainHandler.removeCallbacks(restartRunnable)
        if (currentPartialText.isNotBlank()) {
            onUpdate(
                RecognitionUpdate.Text(
                    value = joinTexts(committedText, currentPartialText),
                    timestamps = FloatArray(0),
                    isFinal = true,
                    segmentCompleted = true,
                    providerText = currentPartialText,
                    endOfUtterance = true,
                ),
            )
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
