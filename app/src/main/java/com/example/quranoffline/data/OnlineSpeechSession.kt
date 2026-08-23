package com.example.quranoffline.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Online-only recognizer. It streams recognition to the platform provider and stores no audio. */
class OnlineSpeechSession(
    context: Context,
    private val onUpdate: (RecognitionUpdate) -> Unit,
) {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    init {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onUpdate(RecognitionUpdate.Started)
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = onUpdate(RecognitionUpdate.Stopped)
            override fun onError(error: Int) = onUpdate(RecognitionUpdate.Failed("تعذر التعرف عبر الإنترنت، رمز الخطأ $error."))
            override fun onResults(results: Bundle?) = emit(results)
            override fun onPartialResults(partialResults: Bundle?) = emit(partialResults)
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            private fun emit(bundle: Bundle?) {
                val text = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) onUpdate(RecognitionUpdate.Text(text, FloatArray(0)))
            }
        })
    }

    fun start() {
        onUpdate(RecognitionUpdate.Started)
        recognizer.startListening(intent)
    }

    fun stop() {
        recognizer.stopListening()
        recognizer.destroy()
        onUpdate(RecognitionUpdate.Stopped)
    }
}
