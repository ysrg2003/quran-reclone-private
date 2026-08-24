package com.example.quranoffline.data

import org.json.JSONObject

/** A source word position used by the text-only mushaf page layout. */
data class QuranLayoutWordKey(
    val surah: Int,
    val ayah: Int,
    val word: Int,
) {
    val value: String get() = "$surah:$ayah:$word"

    companion object {
        fun parse(raw: String): QuranLayoutWordKey? {
            val parts = raw.split(':')
            if (parts.size != 3) return null
            return runCatching { QuranLayoutWordKey(parts[0].toInt(), parts[1].toInt(), parts[2].toInt()) }.getOrNull()
        }
    }
}

data class QuranLayoutMarker(
    val position: Int,
    val type: String,
    val symbol: String? = null,
    val glyphId: Int? = null,
)

data class QuranLayoutLine(
    val number: Int,
    val type: String,
    val wordKeys: List<QuranLayoutWordKey>,
    val markers: List<QuranLayoutMarker> = emptyList(),
)

data class QuranLayoutPage(
    val page: Int,
    val lines: List<QuranLayoutLine>,
)

data class QuranLayout(
    val schemaVersion: Int,
    val source: String,
    val pageCount: Int,
    val wordCount: Int,
    val pages: Map<Int, QuranLayoutPage>,
) {
    fun page(number: Int): QuranLayoutPage? = pages[number]
    fun linesForPage(number: Int): List<QuranLayoutLine> = pages[number]?.lines.orEmpty()

    companion object {
        fun fromJson(raw: String): QuranLayout {
            val root = JSONObject(raw)
            val pageArray = root.getJSONArray("pages")
            val pages = buildMap {
                for (pageIndex in 0 until pageArray.length()) {
                    val pageObject = pageArray.getJSONObject(pageIndex)
                    val linesArray = pageObject.getJSONArray("lines")
                    val lines = buildList {
                        for (lineIndex in 0 until linesArray.length()) {
                            val lineObject = linesArray.getJSONObject(lineIndex)
                            val wordsArray = lineObject.optJSONArray("wordKeys")
                            val keys = buildList {
                                if (wordsArray != null) {
                                    for (wordIndex in 0 until wordsArray.length()) {
                                        QuranLayoutWordKey.parse(wordsArray.optString(wordIndex))?.let(::add)
                                    }
                                }
                            }
                            val markers = buildList {
                                val markersArray = lineObject.optJSONArray("markers")
                                if (markersArray != null) {
                                    for (markerIndex in 0 until markersArray.length()) {
                                        val markerObject = markersArray.optJSONObject(markerIndex) ?: continue
                                        add(
                                            QuranLayoutMarker(
                                                position = markerObject.optInt("position", markerIndex),
                                                type = markerObject.optString("type", "unknown"),
                                                symbol = markerObject.optString("symbol").takeIf { it.isNotBlank() },
                                                glyphId = markerObject.optInt("glyphId", 0).takeIf { it > 0 },
                                            ),
                                        )
                                    }
                                }
                            }
                            add(
                                QuranLayoutLine(
                                    number = lineObject.getInt("number"),
                                    type = lineObject.getString("type"),
                                    wordKeys = keys,
                                    markers = markers.sortedBy { it.position },
                                ),
                            )
                        }
                    }
                    val page = pageObject.getInt("page")
                    put(page, QuranLayoutPage(page, lines))
                }
            }
            return QuranLayout(
                schemaVersion = root.optInt("schemaVersion", 1),
                source = root.optString("source"),
                pageCount = root.optInt("pageCount", pages.size),
                wordCount = root.optInt("wordCount", pages.values.sumOf { page -> page.lines.sumOf { it.wordKeys.size } }),
                pages = pages,
            )
        }
    }
}

/** Deterministic page projection over the canonical global ayah sequence. */
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
