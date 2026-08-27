package com.example.quranoffline.data

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject

/** A contiguous reference segment in milliseconds from the selected reciter's ayah audio. */
data class TimingSegment(
    val wordStart: Int,
    val wordEnd: Int,
    val startMs: Int,
    val endMs: Int,
)

private const val AUTOMATIC_EDGE_GUARD_MS = 120

/**
 * Expands a word clip only into measured inter-word silence. It cannot cross a
 * neighbouring canonical segment, so the correction remains one word.
 */
internal fun TimingSegment.withCorrectionPadding(
    settings: CorrectionSettings,
    previousWord: TimingSegment? = null,
    nextWord: TimingSegment? = null,
): TimingSegment {
    val safeBeforeGap = previousWord?.let { (startMs - it.endMs).coerceAtLeast(0) } ?: 0
    val safeAfterGap = nextWord?.let { (it.startMs - endMs).coerceAtLeast(0) } ?: 0
    val requestedBefore = settings.clipPaddingBeforeMs + (safeBeforeGap / 2).coerceAtMost(AUTOMATIC_EDGE_GUARD_MS)
    val requestedAfter = settings.clipPaddingAfterMs + (safeAfterGap / 2).coerceAtMost(AUTOMATIC_EDGE_GUARD_MS)
    val actualBefore = if (previousWord == null) requestedBefore else requestedBefore.coerceAtMost(safeBeforeGap)
    val actualAfter = if (nextWord == null) requestedAfter else requestedAfter.coerceAtMost(safeAfterGap)
    return copy(
        startMs = (startMs - actualBefore).coerceAtLeast(0),
        endMs = endMs + actualAfter,
    )
}

class ReferenceTimingRepository {
    @Volatile private var timings: Map<String, List<TimingSegment>> = emptyMap()

    fun update(json: String) {
        val rows = if (json.trimStart().startsWith("[")) {
            JSONArray(json)
        } else {
            JSONObject(json).getJSONArray("ayahs")
        }
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

    fun loadBundled(context: Context, reciter: String = "Husary_Murattal") {
        val json = context.assets.open("quran/audio_timing/$reciter.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        update(json)
    }

    fun isReady(): Boolean = timings.isNotEmpty()
}

/** Plays a local file or streams a public URL without persisting the streamed media. */
class ReferenceAudioPlayer(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var assetDescriptor: AssetFileDescriptor? = null
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
     * that option is enabled; a word-only request seeks only the first-word
     * window in the ayah source. If firstWordSource is a physical one-word
     * file, it may be different from source and is played without seeking.
     */
    fun playCorrection(
        source: String,
        firstWord: TimingSegment,
        previousWord: TimingSegment? = null,
        nextWord: TimingSegment? = null,
        wholeAyah: TimingSegment?,
        settings: CorrectionSettings,
        firstWordSource: String = source,
        wholeAyahSource: String = source,
        firstWordAssetPath: String? = null,
        wholeAyahAssetPath: String? = null,
        onWholeAyahStarted: () -> Unit = {},
        onFinished: () -> Unit = {},
    ) {
        val normalized = settings.normalized()
        val items = buildList {
            if (wholeAyah == null) {
                repeat(normalized.wordRepeatCount) {
                    val segment = firstWord.takeIf { firstWordSource == source }?.withCorrectionPadding(normalized, previousWord, nextWord)
                    add(PlaybackItem(firstWordSource, segment, isWholeAyah = false, assetPath = firstWordAssetPath))
                }
            } else {
                if (normalized.playFirstErrorBeforeWholeAyah) {
                    repeat(normalized.wordRepeatCount) {
                        add(
                            PlaybackItem(
                                firstWordSource,
                                firstWord.takeIf { firstWordSource == source }?.withCorrectionPadding(normalized, previousWord, nextWord),
                                isWholeAyah = false,
                                assetPath = firstWordAssetPath,
                            ),
                        )
                    }
                }
                repeat(normalized.wholeAyahRepeatCount) {
                    add(PlaybackItem(wholeAyahSource, wholeAyah.withCorrectionPadding(normalized), isWholeAyah = true, assetPath = wholeAyahAssetPath))
                }
            }
        }
        playSequence(items, normalized.pauseBetweenPartsMs, onWholeAyahStarted, onFinished)
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

    data class QueueItem(
        val source: String,
        val startMs: Int,
        val endMs: Int,
        val key: String,
        val assetPath: String? = null,
    )

    /** Plays a cancellable sequence; it owns no background service and stops on stop(). */
    fun playQueue(
        items: List<QueueItem>,
        pauseBetweenMs: Int = 180,
        onItemStarted: (String) -> Unit = {},
        onFinished: () -> Unit = {},
    ) {
        stop()
        if (items.isEmpty()) { onFinished(); return }
        var index = 0
        fun next() {
            if (index >= items.size) { onFinished(); return }
            val item = items[index++]
            onItemStarted(item.key)
            playDataSource(item.source, item.assetPath, TimingSegment(0, 0, item.startMs, item.endMs)) {
                if (index < items.size && pauseBetweenMs > 0) {
                    val nextRun = Runnable { nextSequenceRunnable = null; next() }
                    nextSequenceRunnable = nextRun
                    mainHandler.postDelayed(nextRun, pauseBetweenMs.toLong())
                } else next()
            }
        }
        next()
    }

    private data class PlaybackItem(
        val source: String,
        val segment: TimingSegment?,
        val isWholeAyah: Boolean,
        val assetPath: String? = null,
    )

    private fun playSequence(
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
            playDataSource(item.source, item.assetPath, item.segment) {
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

    private fun playDataSource(source: String, startMs: Int, endMs: Int, onFinished: () -> Unit) =
        playDataSource(source, null, TimingSegment(0, 0, startMs, endMs), onFinished)

    private fun playDataSource(
        source: String,
        assetPath: String?,
        segment: TimingSegment?,
        onFinished: () -> Unit,
    ) {
        stop()
        val mediaPlayer = MediaPlayer()
        try {
            val openedDescriptor = assetPath?.let { context.assets.openFd(it) }
            assetDescriptor = openedDescriptor
            val closeDescriptor = {
                openedDescriptor?.close()
                if (assetDescriptor === openedDescriptor) assetDescriptor = null
            }
            if (openedDescriptor != null) {
                val descriptor = openedDescriptor
                mediaPlayer.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            } else {
                mediaPlayer.setDataSource(source)
            }
            mediaPlayer.setOnPreparedListener { prepared ->
                if (player !== prepared) {
                    runCatching { prepared.release() }
                    closeDescriptor()
                    return@setOnPreparedListener
                }
                if (segment == null) {
                    prepared.setOnCompletionListener {
                        if (player === prepared) {
                            player = null
                            stopRunnable = null
                            prepared.release()
                            closeDescriptor()
                            onFinished()
                        }
                    }
                    prepared.start()
                    return@setOnPreparedListener
                }
                val durationMs = prepared.duration.coerceAtLeast(1)
                val rawStart = segment.startMs.coerceAtLeast(0)
                val rawEnd = segment.endMs.coerceAtLeast(0)
                // QUL timestamps are integer millisecond cues and a transcoded
                // AAC container can be a few milliseconds shorter than the MP3
                // source. Clamp both bounds to the actual prepared file so a
                // stale end cue cannot schedule a late stop or an invalid seek.
                val safeStart = minOf(rawStart, rawEnd, (durationMs - 1).coerceAtLeast(0))
                val safeEnd = minOf(
                    durationMs,
                    maxOf(rawStart, rawEnd, safeStart + 80),
                )
                if (safeEnd <= safeStart) {
                    prepared.release()
                    closeDescriptor()
                    player = null
                    onFinished()
                    return@setOnPreparedListener
                }
                // API 26+ supports closest-frame seeking. The old one-argument
                // seek may choose a previous MP3 sync frame and play adjacent
                // words before the requested window.
                prepared.seekTo(safeStart.toLong(), MediaPlayer.SEEK_CLOSEST)
                prepared.start()
                val stop = Runnable {
                    if (player === prepared) {
                        try { prepared.stop() } catch (_: IllegalStateException) { }
                        prepared.release()
                        closeDescriptor()
                        player = null
                        stopRunnable = null
                        onFinished()
                    }
                }
                stopRunnable = stop
                mainHandler.postDelayed(stop, (safeEnd - safeStart).toLong() + 80L)
            }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                if (player === mediaPlayer) player = null
                stopRunnable = null
                mediaPlayer.release()
                closeDescriptor()
                onFinished()
                true
            }
            player = mediaPlayer
            mediaPlayer.prepareAsync()
        } catch (_: Throwable) {
            mediaPlayer.release()
            assetDescriptor?.close()
            assetDescriptor = null
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
        assetDescriptor?.close()
        assetDescriptor = null
        player = null
    }
}
