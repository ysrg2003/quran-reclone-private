package com.example.quranoffline.data

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** v25.6: simulated mispronunciation right after a waqf mark and at the last word of an ayah, over the real Quran text. */
class MispronunciationBoundaryTest {
    private fun load(): List<QuranAyah>? {
        val f = File(System.getenv("QURAN_BENCH_TSV") ?: "/tmp/quran_words.tsv")
        if (!f.exists()) return null
        return f.readLines().map { line ->
            val p = line.split('\t')
            val words = p.drop(2).mapIndexed { i, w -> val (t, m) = w.split('|'); QuranWord(i, t, m) }
            QuranAyah(p[0].toInt(), p[1].toInt(), words.joinToString(" ") { it.text }, words.joinToString(" ") { it.matchText }, words)
        }
    }

    private val waqf = Regex("[\\u0615\\u06D6-\\u06DC\\u06DE\\u06E2\\u06E5-\\u06E6\\u06E8\\u06EA-\\u06ED]")
    private fun wrong(w: String) = w.reversed() + "ق"
    private fun prefix(w: String) = w.dropLast(1).ifEmpty { w + "ق" }

    /** Returns the first correction localWordIndex (or -1) after feeding the tokens, in the given delivery mode. */
    private fun run(ayahs: List<QuranAyah>, idx: Int, tokens: List<String>, mode: String): Int {
        val tr = RecitationProgressTracker(listOf(ayahs[idx]))
        when (mode) {
            "final" -> return tr.update(RecitationProgressInput(tokens.joinToString(" "), true, true, endOfUtterance = true)).correction?.localWordIndex ?: -1
            "partials" -> {
                var c: CorrectionRequest? = null
                for (n in 1..tokens.size) {
                    val last = n == tokens.size
                    val r = tr.update(RecitationProgressInput(tokens.take(n).joinToString(" "), last, last, endOfUtterance = last))
                    if (c == null) c = r.correction
                }
                return c?.localWordIndex ?: -1
            }
            else -> { // "pausal": waqf pause finalises a segment before the wrong word
                return -2
            }
        }
    }

    @Test fun boundaries() {
        val ayahs = load(); assumeTrue(ayahs != null); ayahs!!
        val out = StringBuilder()
        val fails = linkedMapOf<String, Int>(); val total = linkedMapOf<String, Int>()
        fun note(kind: String, ok: Boolean, msg: String) {
            total.merge(kind, 1, Int::plus)
            if (!ok) { fails.merge(kind, 1, Int::plus); if (fails[kind]!! <= 12) out.append("FAIL $kind $msg\n") }
        }
        for ((idx, a) in ayahs.withIndex()) {
            val m = a.words.map { it.matchText }
            val n = m.size
            if (n < 2) continue
            for (mode in listOf("final", "partials")) {
                // last word mispronounced
                for ((vname, bad) in listOf("nonsense" to wrong(m.last()), "prefix" to prefix(m.last()))) {
                    val tokens = m.dropLast(1) + bad
                    val got = run(ayahs, idx, tokens, mode)
                    note("last/$vname/$mode", got == n - 1, "${a.surah}:${a.ayah} got=$got want=${n - 1} bad=$bad")
                }
                // first word after each waqf mark mispronounced (rest recited correctly)
                for (k in 1 until n) {
                    if (!waqf.containsMatchIn(a.words[k - 1].text)) continue
                    for ((vname, bad) in listOf("nonsense" to wrong(m[k]), "prefix" to prefix(m[k]))) {
                        val tokens = m.take(k) + bad + m.drop(k + 1)
                        val got = run(ayahs, idx, tokens, mode)
                        note("afterWaqf/$vname/$mode", got == k, "${a.surah}:${a.ayah} k=$k got=$got bad=$bad")
                    }
                }
            }
        }
        out.insert(0, "TOTAL=$total FAILS=$fails\n")
        File("/tmp/boundary_report.txt").writeText(out.toString())
        println(out)
        check((fails["last/prefix/final"] ?: 0) <= 1300 && (fails["last/prefix/partials"] ?: 0) <= 1300) { "last-word prefix still uncorrected: $fails" }
    }
}
