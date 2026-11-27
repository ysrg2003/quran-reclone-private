package com.example.quranoffline.data

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** v24.2: JVM timing of the per-recognizer-update path on the real Quran text (skipped when the TSV is absent). Prints ms per update. */
class TasmeeBenchmarkTest {
    private fun load(): List<QuranAyah>? {
        val f = File(System.getenv("QURAN_BENCH_TSV") ?: "/tmp/quran_words.tsv")
        if (!f.exists()) return null
        return f.readLines().map { line ->
            val p = line.split('\t')
            val words = p.drop(2).mapIndexed { i, w -> val (t, m) = w.split('|'); QuranWord(i, t, m) }
            QuranAyah(p[0].toInt(), p[1].toInt(), words.joinToString(" ") { it.text }, words.joinToString(" ") { it.matchText }, words)
        }
    }

    @Test fun perUpdateCost() {
        val ayahs = load()
        assumeTrue(ayahs != null)
        ayahs!!
        val tracker = RecitationProgressTracker(ayahs)
        val main = Thread.currentThread()
        val samples = java.util.concurrent.ConcurrentHashMap<String, Int>()
        val flag = java.util.concurrent.atomic.AtomicBoolean(true)
        val sampler = Thread {
            while (flag.get()) {
                val st = main.stackTrace
                val frames = st.filter { it.className.contains("quranoffline") }.take(3).joinToString(" < ") { it.className.substringAfterLast('.') + "." + it.methodName + ":" + it.lineNumber }
                if (frames.isNotEmpty()) samples.merge(frames, 1, Int::plus)
                Thread.sleep(1)
            }
        }.also { it.isDaemon = true; it.start() }
        val spoken = ayahs.flatMap { it.words }.take(1500).map { it.matchText }
        val segment = ArrayList<String>()
        var stableNs = 0L; var stableN = 0; var partialNs = 0L; var partialN = 0; var trackerNs = 0L; var canonNs = 0L; var n = 0
        val sb = StringBuilder()
        var cumulativeTranscript = ""
        for ((i, w) in spoken.withIndex()) {
            segment += w
            cumulativeTranscript = segment.joinToString(" ")
            val stable = segment.size >= 8
            val t0 = System.nanoTime()
            val r = tracker.update(RecitationProgressInput(cumulativeTranscript, isFinal = stable, segmentCompleted = stable))
            val t1 = System.nanoTime()
            canonicalizeRecognizedTranscript(cumulativeTranscript, r.evaluation.words)
            val t2 = System.nanoTime()
            if (stable) { stableNs += t1 - t0; stableN++ } else { partialNs += t1 - t0; partialN++ }
            trackerNs += t1 - t0; canonNs += t2 - t1; n++
            if (stable) segment.clear()
            if ((i + 1) % 250 == 0) {
                sb.append("after ${i + 1} words: tracker ${trackerNs / n / 1000} us/update, canonicalize ${canonNs / n / 1000} us/update (avg since last print)\n")
                trackerNs = 0; canonNs = 0; n = 0
            }
        }
        flag.set(false)
        sb.append("stable update avg ${stableNs / maxOf(stableN,1) / 1000} us (n=$stableN), partial avg ${partialNs / maxOf(partialN,1) / 1000} us (n=$partialN) [only last window]\n")
        samples.entries.sortedByDescending { it.value }.take(12).forEach { sb.append("${it.value} ${it.key}\n") }
        println("BENCH\n$sb")
    }
}

class TasmeeMismatchBenchmarkTest {
    private fun load(): List<QuranAyah>? {
        val f = File(System.getenv("QURAN_BENCH_TSV") ?: "/tmp/quran_words.tsv")
        if (!f.exists()) return null
        return f.readLines().map { line ->
            val p = line.split('\t')
            val words = p.drop(2).mapIndexed { i, w -> val (t, m) = w.split('|'); QuranWord(i, t, m) }
            QuranAyah(p[0].toInt(), p[1].toInt(), words.joinToString(" ") { it.text }, words.joinToString(" ") { it.matchText }, words)
        }
    }

    /** Recites correctly but injects wrong words (noisy recognizer): every wrong stable token triggers the later-ayah jump scan. */
    @Test fun mismatchingTokensCost() {
        val ayahs = load(); assumeTrue(ayahs != null); ayahs!!
        val tracker = RecitationProgressTracker(ayahs)
        val spoken = ayahs.flatMap { it.words }.drop(200).take(600).map { it.matchText }.toMutableList()
        val wrong = listOf("سلام", "كتاب", "بيت", "قمر")
        val times = ArrayList<Long>()
        var seg = ArrayList<String>()
        var k = 0
        for (w in spoken) {
            seg += if (k++ % 5 == 3) wrong[k % wrong.size] else w
            if (seg.size >= 6) {
                val t0 = System.nanoTime()
                tracker.update(RecitationProgressInput(seg.joinToString(" "), true, true))
                times += System.nanoTime() - t0
                seg = ArrayList()
            }
        }
        val sorted = times.sorted()
        println("MISMATCH stable updates n=${times.size} median ${sorted[sorted.size / 2] / 1000} us, p90 ${sorted[sorted.size * 9 / 10] / 1000} us, max ${sorted.last() / 1000} us")
    }
}
