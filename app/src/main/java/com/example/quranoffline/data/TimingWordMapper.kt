package com.example.quranoffline.data

/** Maps canonical Word positions to the precomputed selected-reciter ayah windows. */
object TimingWordMapper {
    fun forWord(ayah: QuranAyah, timings: List<TimingSegment>, localWordIndex: Int): TimingSegment? {
        if (timings.isEmpty() || localWordIndex !in ayah.words.indices) return null
        // The bundled Husary file exposes one canonical segment per Word in
        // ordinal order. Never fall back to another word: missing timing data
        // must fail closed instead of playing a neighbouring word.
        return timings.getOrNull(localWordIndex)
    }

    fun wholeAyah(ayah: QuranAyah, timings: List<TimingSegment>): TimingSegment? {
        val first = timings.firstOrNull() ?: return null
        val last = timings.lastOrNull() ?: first
        // A whole ayah is a file-level playback request, not a concatenation
        // of word clips. QUL word timings can begin after leading recitation
        // audio (and may be imperfect at a boundary), so seeking to the first
        // word would clip the beginning of the ayah. Keep the measured final
        // cue as the safe stop boundary while always starting at file offset 0.
        return TimingSegment(0, ayah.words.size, 0, last.endMs)
    }
}
