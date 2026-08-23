package com.example.quranoffline.data

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import org.json.JSONArray

/** A contiguous reference segment in milliseconds from quran-align-data. */
data class TimingSegment(
    val wordStart: Int,
    val wordEnd: Int,
    val startMs: Int,
    val endMs: Int,
)

class ReferenceTimingRepository {
    @Volatile private var timings: Map<String, List<TimingSegment>> = emptyMap()

    fun update(json: String) {
        val rows = JSONArray(json)
        timings = buildMap {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val segmentsJson = row.getJSONArray("segments")
                val segments = buildList(segmentsJson.length()) {
                    for (j in 0 until segmentsJson.length()) {
                        val segment = segmentsJson.getJSONArray(j)
                        add(TimingSegment(segment.getInt(0), segment.getInt(1), segment.getInt(2), segment.getInt(3)))
                    }
                }
                put("${row.getInt("surah")}:${row.getInt("ayah")}", segments)
            }
        }
    }

    fun forAyah(surah: Int, ayah: Int): List<TimingSegment> = timings["$surah:$ayah"].orEmpty()

    fun isReady(): Boolean = timings.isNotEmpty()
}

/** Plays a local file or streams a public URL without persisting the streamed media. */
class ReferenceAudioPlayer {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var stopRunnable: Runnable? = null
    private var nextSequenceRunnable: Runnable? = null

    fun playLocalFile(filePath: String, startMs: Int, endMs: Int, onFinished: () -> Unit = {}) =
        playDataSource(filePath, startMs, endMs, onFinished)

    fun playRemoteUrl(url: String, startMs: Int, endMs: Int, onFinished: () -> Unit = {}) =
        playDataSource(url, startMs, endMs, onFinished)

    fun playWord(source: String, startMs: Int, endMs: Int, onFinished: () -> Unit = {}) =
        playDataSource(source, startMs, endMs, onFinished)

    /**
     * Plays a correction according to the user policy. A whole-ayah request is
     * represented by the first-error word followed by the complete ayah when
     * that option is enabled; a word-only request never adds the ayah.
     */
    fun playCorrection(
        source: String,
        firstWord: TimingSegment,
        wholeAyah: TimingSegment?,
        settings: CorrectionSettings,
        onWholeAyahStarted: () -> Unit = {},
        onFinished: () -> Unit = {},
    ) {
        val normalized = settings.normalized()
        val items = buildList {
            if (wholeAyah == null) {
                repeat(normalized.wordRepeatCount) {
                    add(PlaybackItem(firstWord.withPadding(normalized), isWholeAyah = false))
                }
            } else {
                if (normalized.playFirstErrorBeforeWholeAyah) {
                    repeat(normalized.wordRepeatCount) {
                        add(PlaybackItem(firstWord.withPadding(normalized), isWholeAyah = false))
                    }
                }
                repeat(normalized.wholeAyahRepeatCount) {
                    add(PlaybackItem(wholeAyah.withPadding(normalized), isWholeAyah = true))
                }
            }
        }
        playSequence(source, items, normalized.pauseBetweenPartsMs, onWholeAyahStarted, onFinished)
    }

    /** Backward-compatible default policy used by older callers and tests. */
    fun playWordThenWholeAyah(
        source: String,
        firstWord: TimingSegment,
        wholeAyah: TimingSegment,
        onWholeAyahStarted: () -> Unit = {},
        onFinished: () -> Unit = {},
    ) = playCorrection(
        source = source,
        firstWord = firstWord,
        wholeAyah = wholeAyah,
        settings = CorrectionSettings.DEFAULTS,
        onWholeAyahStarted = onWholeAyahStarted,
        onFinished = onFinished,
    )

    private data class PlaybackItem(
        val segment: TimingSegment,
        val isWholeAyah: Boolean,
    )

    private fun playSequence(
        source: String,
        items: List<PlaybackItem>,
        pauseBetweenPartsMs: Int,
        onWholeAyahStarted: () -> Unit,
        onFinished: () -> Unit,
    ) {
        if (items.isEmpty()) {
            onFinished()
            return
        }
        var index = 0
        fun playNext() {
            if (index >= items.size) {
                onFinished()
                return
            }
            val item = items[index++]
            if (item.isWholeAyah) onWholeAyahStarted()
            playDataSource(source, item.segment.startMs, item.segment.endMs) {
                if (index < items.size && pauseBetweenPartsMs > 0) {
                    val delayedNext = Runnable {
                        nextSequenceRunnable = null
                        playNext()
                    }
                    nextSequenceRunnable = delayedNext
                    mainHandler.postDelayed(delayedNext, pauseBetweenPartsMs.toLong())
                } else {
                    playNext()
                }
            }
        }
        playNext()
    }

    private fun TimingSegment.withPadding(settings: CorrectionSettings): TimingSegment = copy(
        startMs = (startMs - settings.clipPaddingBeforeMs).coerceAtLeast(0),
        endMs = endMs + settings.clipPaddingAfterMs,
    )

    private fun playDataSource(source: String, startMs: Int, endMs: Int, onFinished: () -> Unit) {
        stop()
        val mediaPlayer = MediaPlayer()
        try {
            mediaPlayer.setDataSource(source)
            mediaPlayer.setOnPreparedListener { prepared ->
                val rawStart = startMs.coerceAtLeast(0)
                val rawEnd = endMs.coerceAtLeast(0)
                val safeStart = minOf(rawStart, rawEnd)
                val safeEnd = maxOf(rawStart, rawEnd).coerceAtLeast(safeStart + 80)
                prepared.seekTo(safeStart)
                prepared.start()
                val stop = Runnable {
                    if (player === prepared) {
                        try { prepared.stop() } catch (_: IllegalStateException) { }
                        prepared.release()
                        player = null
                        onFinished()
                    }
                }
                stopRunnable = stop
                mainHandler.postDelayed(stop, (safeEnd - safeStart).toLong() + 80L)
            }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                if (player === mediaPlayer) player = null
                mediaPlayer.release()
                onFinished()
                true
            }
            player = mediaPlayer
            mediaPlayer.prepareAsync()
        } catch (_: Throwable) {
            mediaPlayer.release()
            onFinished()
        }
    }

    fun stop() {
        stopRunnable?.let(mainHandler::removeCallbacks)
        stopRunnable = null
        nextSequenceRunnable?.let(mainHandler::removeCallbacks)
        nextSequenceRunnable = null
        player?.let { active ->
            try { if (active.isPlaying) active.stop() } catch (_: IllegalStateException) { }
            active.release()
        }
        player = null
    }
}
