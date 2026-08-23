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

    fun playLocalFile(filePath: String, startMs: Int, endMs: Int, onFinished: () -> Unit = {}) =
        playDataSource(filePath, startMs, endMs, onFinished)

    fun playRemoteUrl(url: String, startMs: Int, endMs: Int, onFinished: () -> Unit = {}) =
        playDataSource(url, startMs, endMs, onFinished)

    /** Play the first-error word, then the complete ayah, exactly once. */
    fun playWordThenWholeAyah(
        source: String,
        firstWord: TimingSegment,
        wholeAyah: TimingSegment,
        onWholeAyahStarted: () -> Unit = {},
        onFinished: () -> Unit = {},
    ) {
        val segments = listOf(firstWord, wholeAyah)
        var index = 0
        fun playNext() {
            if (index >= segments.size) {
                onFinished()
                return
            }
            val segment = segments[index]
            index++
            if (index == 2) onWholeAyahStarted()
            playDataSource(source, segment.startMs, segment.endMs, ::playNext)
        }
        playNext()
    }

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
        player?.let { active ->
            try { if (active.isPlaying) active.stop() } catch (_: IllegalStateException) { }
            active.release()
        }
        player = null
    }
}
