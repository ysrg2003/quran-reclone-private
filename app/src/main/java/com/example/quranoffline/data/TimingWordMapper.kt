package com.example.quranoffline.data

/** Maps app/reference words to imperfect quran-align segment positions without changing Quran text. */
object TimingWordMapper {
    fun forWord(ayah: QuranAyah, timings: List<TimingSegment>, localWordIndex: Int): TimingSegment? {
        if (timings.isEmpty() || localWordIndex !in ayah.words.indices) return null
        // quran-align wordStart/wordEnd refer to its own tokenization. When
        // tokens were grouped (especially muqattaat), those numbers can skip
        // over app word indexes, so the stable mapping is ordinal within the
        // ayah, with the final segment as a safe tail fallback.
        return timings.getOrNull(localWordIndex) ?: timings.lastOrNull()
    }

    fun wholeAyah(ayah: QuranAyah, timings: List<TimingSegment>): TimingSegment? {
        val first = timings.firstOrNull() ?: return null
        val last = timings.lastOrNull() ?: first
        return TimingSegment(0, ayah.words.size, first.startMs, last.endMs)
    }
}
