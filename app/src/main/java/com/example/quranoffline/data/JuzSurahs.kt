package com.example.quranoffline.data

/** One surah's slice inside a juz. startAyah/endAyah are the ayahs that fall in the juz. */
data class JuzSurahEntry(val surah: Int, val startAyah: Int, val endAyah: Int, val partial: Boolean)

object JuzSurahs {
    /** Surahs of a juz, from the juz boundary list and surah start indexes already in the metadata. Pure. */
    fun forJuz(metadata: QuranMetadata, juz: Int): List<JuzSurahEntry> {
        val start = metadata.juzs.firstOrNull { it.index == juz }?.globalAyahIndex ?: return emptyList()
        val next = metadata.juzs.firstOrNull { it.index == juz + 1 }?.globalAyahIndex
        val last = (next ?: (metadata.suras.last().globalStart + metadata.suras.last().ayahCount)) - 1
        return metadata.suras.mapNotNull { s ->
            val sFirst = s.globalStart
            val sLast = s.globalStart + s.ayahCount - 1
            if (sLast < start || sFirst > last) null else {
                val a = maxOf(sFirst, start) - sFirst + 1
                val b = minOf(sLast, last) - sFirst + 1
                JuzSurahEntry(s.index, a, b, partial = a != 1 || b != s.ayahCount)
            }
        }
    }
}
