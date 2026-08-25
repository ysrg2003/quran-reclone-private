package com.example.quranoffline

import com.example.quranoffline.data.QuranWord

/**
 * Generated from the supplied Word-derived layout. Do not hand-edit the
 * canonical text literals: U+06E1 (small high dotless head of khah) and all
 * other Uthmanic marks are part of the fixture contract.
 */
object WordReferenceFixtures {
    val fatihaBasmala: List<QuranWord> = listOf(
        QuranWord(0, "بِسۡمِ", "بسم"),
        QuranWord(1, "ٱللَّهِ", "الله"),
        QuranWord(2, "ٱلرَّحۡمَٰنِ", "الرحمان"),
        QuranWord(3, "ٱلرَّحِيمِ", "الرحيم"),
    )
    val fatihaFirstThree: List<QuranWord> = fatihaBasmala.take(3)

    val alamin: QuranWord = QuranWord(0, "ٱلۡعَٰلَمِينَ", "العالمين")
    val malik: QuranWord = QuranWord(0, "مَٰلِكِ", "مالك")
    val baqarahOpeningMuqattaat: QuranWord = QuranWord(0, "الٓمٓ", "الم")
    val kahyainSad: QuranWord = QuranWord(0, "كٓهيعٓصٓ", "كهيعص")
    val ham: QuranWord = QuranWord(0, "حمٓ", "حم")
    val ordinaryAlam: QuranWord = QuranWord(0, "أَلَمۡ", "ألم")
    val iyyaka: QuranWord = QuranWord(0, "إِيَّاكَ", "إياك")
    val qala: QuranWord = QuranWord(0, "قَالَ", "قال")
    val kataba: QuranWord = QuranWord(0, "كَتَبَتۡ", "كتبت")
    val yawm: QuranWord = QuranWord(0, "يَوۡمِ", "يوم")
    val deen: QuranWord = QuranWord(0, "ٱلدِّينِ", "الدين")
    val dalleen: QuranWord = QuranWord(0, "ٱلضَّآلِّينَ", "الضالين")
    val aqimu: QuranWord = QuranWord(0, "وَأَقِيمُواْ", "وأقيموا")
    val dhalika: QuranWord = QuranWord(0, "ذَٰلِكَ", "ذلك")
    val kitab: QuranWord = QuranWord(1, "ٱلۡكِتَٰبُ", "الكتاب")
    val prayer: QuranWord = QuranWord(0, "ٱلصَّلَوٰةَ", "الصلاة")
    val zakat: QuranWord = QuranWord(0, "ٱلزَّكَوٰةَ", "الزكاة")
    val hayat: QuranWord = QuranWord(0, "ٱلۡحَيَوٰةِ", "الحياة")
    val ulaika: QuranWord = QuranWord(0, "أُوْلَٰٓئِكَ", "أولئك")
    val akhira: QuranWord = QuranWord(0, "وَبِٱلۡأٓخِرَةِ", "وبالاخرة")
    val kafaru: QuranWord = QuranWord(0, "كَفَرُواْ", "كفرو")
    val laqu: QuranWord = QuranWord(0, "لَقُواْ", "لقو")
    val ashtraw: QuranWord = QuranWord(0, "ٱشۡتَرَوُاْ", "اشترو")
    const val UTHMANIC_SMALL_HIGH_HEAD_OF_KHAH: Char = '\u06E1'
}
