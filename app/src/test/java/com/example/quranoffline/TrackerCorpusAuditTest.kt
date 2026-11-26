package com.example.quranoffline

import com.example.quranoffline.data.PronunciationAyah
import com.example.quranoffline.data.PronunciationReference
import com.example.quranoffline.data.PronunciationWord
import com.example.quranoffline.data.QuranAyah
import com.example.quranoffline.data.QuranWord
import com.example.quranoffline.data.RecitationProgressInput
import com.example.quranoffline.data.RecitationProgressTracker
import com.example.quranoffline.data.RecognizerSpacedForms
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Feeds correct standard-spelling recitation of every surah through the real tracker. */
class TrackerCorpusAuditTest {
    private val marks = Regex("[\\u0610-\\u061a\\u064b-\\u065f\\u06d6-\\u06ed\\u0670\\u0640]")

    @Test
    fun audit() {
        val path = System.getenv("PAIRS_TSV")
        assumeTrue(path != null && File(path).exists())
        val rows = File(path!!).readLines().map { it.split('\t') }.filter { it.size >= 8 }
        RecognizerSpacedForms.register(rows.map { marks.replace(it[4], "").trim() }.filter { it.contains(' ') }.toSet())
        val bySurah = rows.groupBy { it[0].substringBefore(':').toInt() }
        val out = StringBuilder()
        var badAyahsFull = 0; var badAyahsIncr = 0; var ayahsTotal = 0; var corrections = 0
        for ((surah, srows) in bySurah.toSortedMap()) {
            val byAyah = srows.groupBy { it[0] }.toList().sortedBy { it.first.substringAfter(':').toInt() }
            val qAyahs = byAyah.map { (key, ws) ->
                QuranAyah(surah, key.substringAfter(':').toInt(), "", "", ws.map {
                    QuranWord(it[1].toInt(), it[2], it[3], recognizedForms = setOf(marks.replace(it[4], "").trim()))
                })
            }
            val pAyahs = byAyah.map { (key, ws) ->
                PronunciationAyah(surah, key.substringAfter(':').toInt(), "", ws.map {
                    PronunciationWord(it[1].toInt(), it[5], it[6].split('|').filter(String::isNotBlank),
                        it[7].split(',').filter(String::isNotBlank).map(String::toInt).toSet(), null, null, null, null)
                })
            }
            val ref = PronunciationReference.fromAyahs(pAyahs)
            for (mode in listOf("full", "incr")) {
                val tr = RecitationProgressTracker(qAyahs, ref)
                for ((i, kv) in byAyah.withIndex()) {
                    val words = kv.second.flatMap { marks.replace(it[4], "").trim().split(' ') }.filter { it.isNotBlank() }
                    ayahsTotal += if (mode == "full") 1 else 0
                    var res = tr.update(RecitationProgressInput(words.joinToString(" "), true, true))
                    if (mode == "incr") {
                        // Fresh tracker state is not reusable; emulate word-by-word by re-running cumulative stable segments.
                    }
                    if (res.correction != null) corrections++
                    val advanced = res.currentAyahIndex > i || (i == byAyah.lastIndex && res.currentAyahIndex >= i)
                    if (!advanced && mode == "full") {
                        badAyahsFull++
                        if (badAyahsFull <= 300) out.append("FULL $surah:${kv.first} stuck at word ${res.currentWordInAyah} corr=${res.correction?.kind}/${res.correction?.localWordIndex} obs=${res.correction?.observed} exp=${kv.second.getOrNull(res.currentWordInAyah)?.let { it[2] + " spoken=" + it[5] + " imlaei=" + it[4] }}\n")
                        break
                    }
                }
                if (mode == "full") continue
            }
        }
        out.insert(0, "AYAHS=$ayahsTotal badFull=$badAyahsFull corrections=$corrections\n")
        File(System.getenv("AUDIT_OUT") + ".tracker.txt").writeText(out.toString())
    }
}
