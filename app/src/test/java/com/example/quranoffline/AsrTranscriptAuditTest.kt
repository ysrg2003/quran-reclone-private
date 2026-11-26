package com.example.quranoffline

import com.example.quranoffline.data.PronunciationAyah
import com.example.quranoffline.data.PronunciationReference
import com.example.quranoffline.data.PronunciationWord
import com.example.quranoffline.data.QuranAyah
import com.example.quranoffline.data.QuranWord
import com.example.quranoffline.data.RecitationProgressInput
import com.example.quranoffline.data.RecitationProgressTracker
import com.example.quranoffline.data.RecognizerSpacedForms
import com.example.quranoffline.data.WordStatus
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Real ASR transcripts (TSV key, transcript, secs) of ayah audio fed through the real tracker. */
class AsrTranscriptAuditTest {
    private val marks = Regex("[\\u0610-\\u061a\\u064b-\\u065f\\u06d6-\\u06ed\\u0670\\u0640]")

    @Test
    fun audit() {
        val pairs = System.getenv("PAIRS_TSV"); val asr = System.getenv("ASR_TSV")
        assumeTrue(pairs != null && asr != null && File(asr).exists())
        val rows = File(pairs!!).readLines().map { it.split('\t') }.filter { it.size >= 8 }
        RecognizerSpacedForms.register(rows.map { marks.replace(it[4], "").trim() }.filter { it.contains(' ') }.toSet())
        val bySurah = rows.groupBy { it[0].substringBefore(':').toInt() }
        val out = StringBuilder(); var words = 0; var matched = 0; var ayahs = 0; var perfect = 0
        val rej = linkedMapOf<String, Int>()
        val per = sortedMapOf<Int, IntArray>()
        for (line in File(asr).readLines()) {
            val c = line.split('\t'); if (c.size < 2) continue
            val key = c[0]; val s = key.substringBefore(':').toInt(); val a = key.substringAfter(':').toInt()
            val srows = bySurah[s] ?: continue
            val byAyah = srows.groupBy { it[0] }.toList().sortedBy { it.first.substringAfter(':').toInt() }
            val idx = byAyah.indexOfFirst { it.first == key }; if (idx < 0) continue
            val qAyahs = byAyah.map { (k, ws) -> QuranAyah(s, k.substringAfter(':').toInt(), "", "", ws.map { QuranWord(it[1].toInt(), it[2], it[3], recognizedForms = setOf(marks.replace(it[4], "").trim())) }) }
            val pAyahs = byAyah.map { (k, ws) -> PronunciationAyah(s, k.substringAfter(':').toInt(), "", ws.map { PronunciationWord(it[1].toInt(), it[5], it[6].split('|').filter(String::isNotBlank), it[7].split(',').filter(String::isNotBlank).map(String::toInt).toSet(), null, null, null, null) }) }
            val tr = RecitationProgressTracker(qAyahs, PronunciationReference.fromAyahs(pAyahs))
            tr.startFromAyah(idx)
            val text = c[1].replace(Regex("[0-9٠-٩]+[.\\-:]?"), " ").replace(Regex("[.,،؟?!:;\"'()\\[\\]«»-]"), " ").replace(Regex("\\s+"), " ").trim()
            val res = tr.update(RecitationProgressInput(text, true, true))
            val ev = res.evaluation.words
            val start = qAyahs.take(idx).sumOf { it.words.size }
            val n = qAyahs[idx].words.size
            val m = (0 until n).count { ev[start + it].status == WordStatus.MATCHED }
            val pr = per.getOrPut(s) { IntArray(4) }; pr[0]++; pr[1] += n; pr[2] += m; if (m == n) pr[3]++
            ayahs++; words += n; matched += m; if (m == n) perfect++
            if (m < n) {
                val first = (0 until n).first { ev[start + it].status != WordStatus.MATCHED }
                out.append("$key m=$m/$n firstMiss=${first} exp=${byAyah[idx].second[first][4]} obs=${res.correction?.observed}\n  ASR: $text\n")
            }
        }
        out.insert(0, per.entries.joinToString("") { (k, v) -> "SURAH $k ayahs=${v[0]} words=${v[1]} matched=${v[2]} perfect=${v[3]}\n" })
        out.insert(0, "ASR ayahs=$ayahs words=$words matched=$matched perfectAyahs=$perfect\n")
        File(System.getenv("AUDIT_OUT") + ".asr.txt").writeText(out.toString())
    }
}
