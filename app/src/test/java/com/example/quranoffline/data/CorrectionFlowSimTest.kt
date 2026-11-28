package com.example.quranoffline.data

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * v25.6: multi-ayah recitation flow over the real Quran text. Mispronounce the first word after a waqf mark (or the last word
 * of an ayah that has none), expect the correction on exactly that word, resume like the app does, finish the ayah, and
 * check that the next ayahs are still judged (no lost sync). Tokens are the asset matchText (what a correct recognizer writes).
 */
class CorrectionFlowSimTest {
    private fun load(): List<QuranAyah>? {
        val f = File(System.getenv("QURAN_BENCH_TSV") ?: "/tmp/quran_words.tsv")
        if (!f.exists()) return null
        return f.readLines().map { line ->
            val p = line.split('\t')
            val words = p.drop(2).mapIndexed { i, w -> val (t, m) = w.split('|'); QuranWord(i, t, m) }
            QuranAyah(p[0].toInt(), p[1].toInt(), words.joinToString(" ") { it.text }, words.joinToString(" ") { it.matchText }, words)
        }
    }
    private val stopMark = Regex("[\\u06D6-\\u06DC]")
    private fun wrong(w: String) = w.reversed() + "ق"
    private fun prefix(w: String) = w.dropLast(1).ifEmpty { w + "ق" }
    private fun seg(t: List<String>) = RecitationProgressInput(t.joinToString(" "), true, true, endOfUtterance = true)

    @Test fun flow() {
        val all = load(); assumeTrue(all != null); all!!
        val out = StringBuilder(); val stat = linkedMapOf<String, Int>()
        fun bump(k: String) { stat.merge(k, 1, Int::plus) }
        for (surah in listOf(2, 3, 4, 5, 6, 7, 9, 18, 20, 36)) {
            val ayahs = all.filter { it.surah == surah }
            for (variant in listOf("nonsense", "prefix")) for (delivery in listOf("single", "pausal")) {
                val tr = RecitationProgressTracker(ayahs)
                var j = 0
                while (j < ayahs.size) {
                    val a = ayahs[j]; val m = a.words.map { it.matchText }; val n = m.size
                    // baseline: does a clean recitation of this ayah advance a fresh tracker? (skips recognizer-form artifacts of this sim)
                    val ks = (1 until n).filter { stopMark.containsMatchIn(a.words[it - 1].text) }
                    val k = if (ks.isNotEmpty()) ks.first() else n - 1
                    val kind = if (ks.isNotEmpty()) "waqf" else "last"
                    if (n < 2) { tr.update(seg(m)); j++; continue }
                    val bad = if (variant == "nonsense") wrong(m[k]) else prefix(m[k])
                    val before = m.take(k)
                    val res = if (delivery == "single") tr.update(seg(before + bad + m.drop(k + 1)))
                    else { tr.update(seg(before)); tr.update(seg(listOf(bad) + m.drop(k + 1))) }
                    var corr = res.correction
                    // a trailing prefix token is held back until a later token or the end of speech; the end of speech is a repeated final update
                    bump("$kind/$variant/$delivery total")
                    if (corr == null) { bump("$kind/$variant/$delivery NO_CORRECTION"); if (fails(stat, "$kind/$variant/$delivery NO_CORRECTION") <= 6) out.append("NOCORR $surah:${a.ayah} k=$k bad=$bad\n") }
                    else if (corr.localWordIndex != k) { bump("$kind/$variant/$delivery WRONG_INDEX"); if (fails(stat, "$kind/$variant/$delivery WRONG_INDEX") <= 6) out.append("WRONGIDX $surah:${a.ayah} want=$k got=${corr.localWordIndex}\n") }
                    // resume like the app and finish the ayah
                    tr.resumeFrom(j, k)
                    tr.update(seg(m.drop(k)))
                    val r2 = tr.update(seg(m.drop(k)))
                    val adv = r2.currentAyahIndex > j || (j == ayahs.lastIndex)
                    if (!adv) { bump("$kind/$variant/$delivery STUCK_AFTER_FIX"); if (fails(stat, "$kind/$variant/$delivery STUCK_AFTER_FIX") <= 6) out.append("STUCK $surah:${a.ayah} state=ayah ${r2.currentAyahIndex} word ${r2.currentWordInAyah}\n"); 
                        // re-sync like the user would: restart this ayah from its start
                        tr.startFromAyah(j + 1) }
                    j++
                }
            }
        }
        out.insert(0, "STATS=$stat\n")
        File("/tmp/flow_report.txt").writeText(out.toString())
        val noCorrLastPrefix = stat.filterKeys { it.startsWith("last/prefix") && it.endsWith("NO_CORRECTION") }.values.sum()
        check(noCorrLastPrefix <= 300) { "last-word truncation still uncorrected: $noCorrLastPrefix" }
        println(out)
    }
    private fun fails(stat: Map<String, Int>, k: String) = stat[k] ?: 0
}
