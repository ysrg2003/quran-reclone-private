package com.example.quranoffline.data

/** Maps app/reference words to imperfect quran-align segment positions without changing Quran text. */
object TimingWordMapper {
    fun forWord(
        ayah: QuranAyah,
        timings: List<TimingSegment>,
        localWordIndex: Int,
        pronunciation: PronunciationWord? = null,
    ): TimingSegment? {
        if (timings.isEmpty() || localWordIndex !in ayah.words.indices) return null
        val mapped = pronunciation?.let { item ->
            val start = item.audioStartMs
            val end = item.audioEndMs
            if (start != null && end != null) {
                TimingSegment(
                    wordStart = item.audioWordStart ?: item.legacyWordIndices.minOrNull() ?: localWordIndex,
                    wordEnd = item.audioWordEnd ?: item.legacyWordIndices.maxOrNull()?.plus(1) ?: localWordIndex + 1,
                    startMs = start,
                    endMs = end,
                )
            } else null
        }
        if (mapped != null) return mapped
        // quran-align wordStart/wordEnd refer to its own tokenization. When
        // tokens were grouped (especially muqattaat), the explicit Word-to-audio
        // mapping above is authoritative; ordinal fallback is only for legacy
        // assets that do not carry that metadata.
        return timings.getOrNull(localWordIndex) ?: timings.lastOrNull()
    }

    fun wholeAyah(ayah: QuranAyah, timings: List<TimingSegment>): TimingSegment? {
        val first = timings.firstOrNull() ?: return null
        val last = timings.lastOrNull() ?: first
        return TimingSegment(0, ayah.words.size, first.startMs, last.endMs)
    }
}
