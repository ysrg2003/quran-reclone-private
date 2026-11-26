package com.example.quranoffline

import com.example.quranoffline.data.PronunciationSpeechMatcher
import com.example.quranoffline.data.PronunciationWord
import com.example.quranoffline.data.QuranWord
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Audit with the standard (imlaei) spelling a speech-to-text service writes.
 * PAIRS_TSV columns: ayah, wordIndex, uthmaniText, matchText, imlaeiWord, spoken, spokenTokens(|), maddPositions(,).
 * Outputs summary counts and sample lines to AUDIT_OUT.
 */
class ImlaeiCorpusAuditTest {
    private val marks = Regex("[\\u0610-\\u061a\\u064b-\\u065f\\u06d6-\\u06ed\\u0670\\u0640]")

    private fun canon(v: String) = v.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا')
        .replace('ؤ', 'و').replace('ئ', 'ي').replace("ء", "").replace(" ", "").replace("لل", "ل")

    @Test
    fun audit() {
        val path = System.getenv("PAIRS_TSV")
        assumeTrue(path != null && File(path).exists())
        val out = StringBuilder()
        var total = 0
        val falseReject = mutableListOf<String>()
        val mutAccepted = linkedMapOf<String, MutableList<String>>()
        val mutTotal = linkedMapOf<String, Int>()
        val rows = File(path!!).readLines().map { it.split('\t') }.filter { it.size >= 8 }
        val byAyah = rows.groupBy { it[0] }
        for (c in rows) {
            val word = QuranWord(c[1].toInt(), c[2], c[3], recognizedForms = if (System.getenv("WITH_FORMS") == "1") setOf(marks.replace(c[4], "").trim()) else emptySet())
            val maddPositions = c[7].split(',').filter { it.isNotBlank() }.map { it.toInt() }.toSet()
            val tokens = c[6].split('|').filter { it.isNotBlank() }
            val ref = PronunciationWord(c[1].toInt(), c[5], tokens, maddPositions, null, null, null, null)
            val plain = marks.replace(c[4], "")
            total++
            if (!PronunciationSpeechMatcher.matches(word, ref, plain)) {
                falseReject += "${c[0]}:${c[1]}\t${c[2]}\t${c[5]}\t$plain"
            }
            // Mutations that must NOT be accepted (a wrong recitation).
            fun mutate(kind: String, observed: String) {
                if (observed.isBlank() || observed == plain) return
                // Not a real error: same word once hamza seat, spacing and tatweel are ignored.
                if (canon(observed) == canon(plain)) return
                // Legitimate: the app's own spoken form (silent final alif/waw is not pronounced).
                if (canon(observed) == canon(c[5])) return
                mutTotal[kind] = (mutTotal[kind] ?: 0) + 1
                if (PronunciationSpeechMatcher.matches(word, ref, observed)) {
                    mutAccepted.getOrPut(kind) { mutableListOf() } += "${c[0]}:${c[1]}\t${c[2]}\t$plain\t$observed"
                }
            }
            if (plain.length >= 3) {
                if (!Regex("ً\\p{M}*ا\\p{M}*$").containsMatchIn(c[2]) && !Regex("ا\\p{M}*ً\\p{M}*$").containsMatchIn(c[2])) mutate("drop-last-letter", plain.dropLast(1))
                mutate("drop-first-letter", plain.drop(1))
                mutate("drop-middle-letter", plain.removeRange(plain.length / 2, plain.length / 2 + 1))
                mutate("swap-adjacent-middle", plain.toCharArray().also { a -> val i = a.size / 2; if (i + 1 < a.size) { val t = a[i]; a[i] = a[i + 1]; a[i + 1] = t } }.concatToString())
                mutate("replace-middle-letter", plain.replaceRange(plain.length / 2, plain.length / 2 + 1, if (plain[plain.length / 2] == 'ب') "ت" else "ب"))
            }
            // Wrong neighbouring word of the same ayah must not be accepted.
            val siblings = byAyah[c[0]].orEmpty()
            val other = siblings.firstOrNull { it[1] != c[1] && marks.replace(it[4], "") != plain }
            if (other != null) mutate("neighbour-word-in-ayah", marks.replace(other[4], ""))
        }
        out.append("TOTAL words=$total falseRejects=${falseReject.size}\n")
        mutTotal.forEach { (k, n) -> out.append("MUT $k tested=$n accepted=${mutAccepted[k]?.size ?: 0}\n") }
        File(System.getenv("AUDIT_OUT") ?: "/tmp/audit.txt").writeText(out.toString())
        File((System.getenv("AUDIT_OUT") ?: "/tmp/audit.txt") + ".reject.tsv").writeText(falseReject.joinToString("\n"))
        mutAccepted.forEach { (k, v) -> File((System.getenv("AUDIT_OUT") ?: "/tmp/audit.txt") + ".mut-$k.tsv").writeText(v.joinToString("\n")) }
        println(out)
    }
}
