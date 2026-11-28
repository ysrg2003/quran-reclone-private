package com.example.quranoffline.data

import org.junit.Assert.*
import org.junit.Test

/** v25.7: An-Nisa 4:70 (user screenshot): the recognizer returned the word after وكفى without it. */
class ConjunctionDropTest {
    private val words = listOf(
        "ذَٰلِكَ" to "ذالك", "ٱلۡفَضۡلُ" to "الفضل", "مِنَ" to "من", "ٱللَّهِۚ" to "الله",
        "وَكَفَىٰ" to "وكفى", "بِٱللَّهِ" to "بالله", "عَلِيمٗا" to "عليما",
    ).mapIndexed { i, (t, m) -> QuranWord(i, t, m) }
    private val ayah = QuranAyah(4, 70, "", "", words)
    private fun run(tokens: String): RecitationProgressResult {
        val tr = RecitationProgressTracker(listOf(ayah))
        return tr.update(RecitationProgressInput(tokens, true, true, endOfUtterance = true))
    }
    @Test fun normalReadingStillPasses() { val r = run("ذالك الفضل من الله وكفى بالله عليما"); assertNull(r.correction); assertEquals(7, r.evaluation.matched) }
    @Test fun recognizerDropsConjunctionWord() { val r = run("ذالك الفضل من الله بالله عليما"); assertNull(r.correction); assertEquals(7, r.evaluation.matched) }
    @Test fun trulyWrongWordStillCorrectedOnThatWord() { val r = run("ذالك الفضل من الله خطا بالله عليما"); assertEquals(4, r.correction?.localWordIndex) }
    @Test fun skippedLongWordStillFlagged() { val r = run("ذالك من الله وكفى بالله عليما"); assertEquals(1, r.correction?.localWordIndex) }
}
