package com.example.quranoffline.data

/** A presentation/recitation item, never a QuranAyah and never a progress word. */
data class OpeningBasmala(
    val surah: Int,
    val text: String = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
    val matchText: String = "بسم الله الرحمن الرحيم",
    val countAsAyah: Boolean = false,
)

object QuranPresentation {
    /**
     * The opening basmala is shown before every surah except al-Fatiha (where
     * it is ayah 1:1) and al-Tawbah (which has no opening basmala).
     */
    fun openingBasmalaForSurah(surah: Int): OpeningBasmala? =
        if (surah in 2..114 && surah != 9) OpeningBasmala(surah) else null
}
