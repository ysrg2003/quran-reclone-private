package com.example.quranoffline.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Entirely local microphone + sherpa-onnx session using an extracted model directory. */
class LocalRecitationSession(
    private val context: Context,
    private val modelDirectory: File,
    private val onUpdate: (RecognitionUpdate) -> Unit,
) {
    companion object { private const val SAMPLE_RATE = 16_000 }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private val cleanupInProgress = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null

    fun start() {
        if (running.get()) return
        if (cleanupInProgress.get()) {
            mainHandler.postDelayed({ start() }, 120L)
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            emit(RecognitionUpdate.Failed("يجب السماح بالميكروفون لبدء التسميع."))
            return
        }
        try {
            require(modelDirectory.isDirectory) { "مجلد النموذج المحلي غير موجود" }
            val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            require(minBuffer > 0) { "تعذر تحديد حجم مخزن الصوت." }
            val modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = File(modelDirectory, "encoder.int8.onnx").absolutePath,
                    decoder = File(modelDirectory, "decoder.int8.onnx").absolutePath,
                    joiner = File(modelDirectory, "joiner.int8.onnx").absolutePath,
                ),
                tokens = File(modelDirectory, "tokens.txt").absolutePath,
                modelType = "nemo_transducer",
                numThreads = 2,
                provider = "cpu",
            )
            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = modelConfig,
                enableEndpoint = true,
                decodingMethod = "greedy_search",
            )
            recognizer = OnlineRecognizer(null, config)
            stream = recognizer!!.createStream()
            recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer * 2)
            require(recorder!!.state == AudioRecord.STATE_INITIALIZED) { "تعذر تهيئة الميكروفون." }
            recorder!!.startRecording()
            running.set(true)
            emit(RecognitionUpdate.Started)
            worker = Thread({ captureLoop(minBuffer) }, "quran-online-local-recognizer").also { it.start() }
        } catch (error: Throwable) {
            closeResources()
            emit(RecognitionUpdate.Failed("تعذر تشغيل النموذج المحلي: ${error.message ?: "خطأ غير معروف"}"))
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        cleanupInProgress.set(true)
        try { recorder?.stop() } catch (_: IllegalStateException) { }
        val workerToJoin = worker
        worker = null
        Thread({
            try {
                workerToJoin?.join(1_000)
                stream?.let { activeStream ->
                    try {
                        activeStream.inputFinished()
                        while (recognizer?.isReady(activeStream) == true) recognizer?.decode(activeStream)
                        recognizer?.getResult(activeStream)?.let { result ->
                            if (result.text.isNotBlank()) emit(RecognitionUpdate.Text(result.text, result.timestamps, isFinal = true))
                        }
                    } catch (_: Throwable) { }
                }
                closeResources()
                emit(RecognitionUpdate.Stopped)
            } finally {
                cleanupInProgress.set(false)
            }
        }, "quran-local-recognizer-cleanup").start()
    }

    private fun captureLoop(minBuffer: Int) {
        val activeRecorder = recorder ?: return
        val activeStream = stream ?: return
        val activeRecognizer = recognizer ?: return
        val pcm = ShortArray((minBuffer / 2).coerceAtLeast(512))
        val samples = FloatArray(pcm.size)
        var lastEmittedText = ""
        var lastEmittedAt = 0L
        while (running.get()) {
            val count = activeRecorder.read(pcm, 0, pcm.size)
            if (count <= 0) continue
            for (i in 0 until count) samples[i] = pcm[i] / 32768.0f
            activeStream.acceptWaveform(samples.copyOf(count), SAMPLE_RATE)
            while (activeRecognizer.isReady(activeStream)) activeRecognizer.decode(activeStream)
            val result = activeRecognizer.getResult(activeStream)
            val text = result.text
            val now = SystemClock.uptimeMillis()
            if (text.isNotBlank() && (text != lastEmittedText || now - lastEmittedAt >= 250L)) {
                lastEmittedText = text
                lastEmittedAt = now
                emit(RecognitionUpdate.Text(text, result.timestamps))
            }
        }
    }

    private fun emit(update: RecognitionUpdate) { mainHandler.post { onUpdate(update) } }

    private fun closeResources() {
        try { recorder?.release() } catch (_: Throwable) { }
        recorder = null
        try { stream?.release() } catch (_: Throwable) { }
        stream = null
        try { recognizer?.release() } catch (_: Throwable) { }
        recognizer = null
    }
}
