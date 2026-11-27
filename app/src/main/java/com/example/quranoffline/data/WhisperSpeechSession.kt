package com.example.quranoffline.data

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Offline recognizer: records 16 kHz mono, re-decodes the growing buffer with whisper.cpp and emits only words
 * that two consecutive decodes agree on (the final decode is emitted on stop). Any failure calls [onFailure]
 * so the caller can fall back to the system recognizer.
 */
class WhisperSpeechSession(
    private val modelPath: String,
    onUpdateMain: (RecognitionUpdate) -> Unit,
    private val onFailure: (String) -> Unit,
    private val threads: Int = 2,
) : SpeechEngine {
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val onUpdate: (RecognitionUpdate) -> Unit = { u -> main.post { onUpdateMain(u) } }
    private val active = AtomicBoolean(false)
    private var worker: Thread? = null
    @Volatile private var stopRequested = false

    override fun start() {
        if (!active.compareAndSet(false, true)) return
        onUpdate(RecognitionUpdate.Started)
        val previousWorker = lastWorker
        worker = Thread({ runCatching { previousWorker?.join(10000) }; run() }, "whisper-session").also { lastWorker = it; it.start() }
    }

    override fun stop() {
        if (!active.getAndSet(false)) return
        // Never block the main thread: the worker finishes its last decode, posts it, then posts Stopped.
        stopRequested = true
    }

    @SuppressLint("MissingPermission")
    private fun run() {
        var handle = 0L
        var rec: AudioRecord? = null
        try {
            handle = WhisperNative.nativeInit(modelPath)
            if (handle == 0L) { fail("تعذر تحميل نموذج Whisper"); return }
            val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, RATE))
            if (rec.state != AudioRecord.STATE_INITIALIZED) { fail("تعذر فتح الميكروفون"); return }
            rec.startRecording()
            val chunk = ShortArray(RATE / 10)
            var buffer = FloatArray(0)
            var size = 0
            var committed = ""
            var previous = emptyList<String>()
            var lastDecodeAt = 0
            var lastText = ""
            while (active.get()) {
                val n = rec.read(chunk, 0, chunk.size)
                if (n <= 0) continue
                if (size + n > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + n, RATE * 4))
                for (i in 0 until n) buffer[size + i] = chunk[i] / 32768f
                size += n
                if (size - lastDecodeAt < RATE * 3 / 2 || size < RATE) continue
                lastDecodeAt = size
                val words = StablePrefix.dropLoop(StablePrefix.words(WhisperNative.nativeTranscribe(handle, buffer.copyOf(size), threads)))
                val stable = StablePrefix.common(previous, words)
                previous = words
                val nearCut = size >= RATE * MAX_SECONDS || (size >= RATE * SOFT_SECONDS && quietTail(buffer, size))
                val emitWords = if (nearCut) words else stable
                val text = listOf(committed, emitWords.joinToString(" ")).filter { it.isNotBlank() }.joinToString(" ")
                if (text.isNotBlank() && text != lastText) {
                    lastText = text
                    onUpdate(RecognitionUpdate.Text(value = text, timestamps = FloatArray(0), isFinal = nearCut, segmentCompleted = nearCut, providerText = emitWords.joinToString(" ")))
                }
                if (nearCut) { committed = text; size = 0; lastDecodeAt = 0; previous = emptyList() }
            }
            // Final decode of whatever is left so the last words are not lost.
            if (size >= RATE / 2) {
                val words = StablePrefix.dropLoop(StablePrefix.words(WhisperNative.nativeTranscribe(handle, buffer.copyOf(size), threads)))
                val text = listOf(committed, words.joinToString(" ")).filter { it.isNotBlank() }.joinToString(" ")
                if (text.isNotBlank() && text != lastText) onUpdate(RecognitionUpdate.Text(text, FloatArray(0), true, true, words.joinToString(" ")))
            }
        } catch (t: Throwable) {
            fail("خطأ في Whisper: ${t.message ?: t.javaClass.simpleName}")
        } finally {
            runCatching { rec?.stop() }; runCatching { rec?.release() }
            if (handle != 0L) runCatching { WhisperNative.nativeFree(handle) }
            if (stopRequested) onUpdate(RecognitionUpdate.Stopped)
        }
    }

    private fun fail(message: String) {
        if (active.getAndSet(false)) onFailure(message)
    }

    private fun quietTail(buf: FloatArray, size: Int): Boolean {
        val from = size - RATE / 2
        var sum = 0.0
        for (i in from until size) sum += buf[i] * buf[i]
        return Math.sqrt(sum / (RATE / 2)) < 0.01
    }

    companion object {
        @Volatile private var lastWorker: Thread? = null
        const val RATE = 16_000
        const val SOFT_SECONDS = 8
        const val MAX_SECONDS = 14
    }
}
