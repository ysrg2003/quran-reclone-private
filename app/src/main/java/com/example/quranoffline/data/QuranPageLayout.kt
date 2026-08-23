package com.example.quranoffline.data

/**
 * Deterministic page projection over the canonical global ayah sequence.
 * It contains no rendering or recognizer state and is therefore safe to unit test.
 */
object QuranPageLayout {
    fun ayahsOnPage(
        allAyahs: List<QuranAyah>,
        metadata: QuranMetadata,
        pageNumber: Int,
    ): List<QuranAyah> {
        val actualPage = pageNumber.coerceIn(1, metadata.pages.size)
        return allAyahs.filter { ayah -> metadata.ayah(ayah.surah, ayah.ayah)?.page == actualPage }
    }

    fun firstAyahIndexOnPage(
        allAyahs: List<QuranAyah>,
        metadata: QuranMetadata,
        pageNumber: Int,
    ): Int = allAyahs.indexOfFirst { ayah ->
        metadata.ayah(ayah.surah, ayah.ayah)?.page == pageNumber.coerceIn(1, metadata.pages.size)
    }

    fun globalWordOffset(
        allAyahs: List<QuranAyah>,
        firstVisibleAyah: QuranAyah?,
    ): Int {
        if (firstVisibleAyah == null) return 0
        val index = allAyahs.indexOfFirst { it.surah == firstVisibleAyah.surah && it.ayah == firstVisibleAyah.ayah }
        return if (index < 0) 0 else allAyahs.take(index).sumOf { it.words.size }
    }

    fun pageForAyahIndex(
        allAyahs: List<QuranAyah>,
        metadata: QuranMetadata,
        index: Int,
        fallbackPage: Int = 1,
    ): Int {
        val ayah = allAyahs.getOrNull(index) ?: return fallbackPage
        return metadata.ayah(ayah.surah, ayah.ayah)?.page ?: fallbackPage
    }
}
