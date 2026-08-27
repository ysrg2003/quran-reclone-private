package com.example.quranoffline.data

/** Maps canonical Word positions to the precomputed selected-reciter ayah windows. */
object TimingWordMapper {
    private const val SUSPICIOUS_EDGE_CUE_MS = 120
    private const val SUSPICIOUS_GAP_MS = 600

    fun forWord(ayah: QuranAyah, timings: List<TimingSegment>, localWordIndex: Int): TimingSegment? {
        if (timings.isEmpty() || localWordIndex !in ayah.words.indices) return null
        val current = timings.getOrNull(localWordIndex) ?: return null
        val previous = timings.getOrNull(localWordIndex - 1)
        val next = timings.getOrNull(localWordIndex + 1)
        return repairSuspiciousEdgeCue(current, previous, next)
    }

    /**
     * QUL occasionally emits a tiny first/last cue outside the spoken edge of
     * an ayah. Playing that raw cue produces silence or a neighbouring fragment
     * even though the canonical word index is correct. Repair only this narrow
     * edge case using the adjacent QUL boundary; ordinary cues remain unchanged.
     */
    private fun repairSuspiciousEdgeCue(
        current: TimingSegment,
        previous: TimingSegment?,
        next: TimingSegment?,
    ): TimingSegment {
        val duration = current.endMs - current.startMs
        if (duration >= SUSPICIOUS_EDGE_CUE_MS) return current

        if (previous == null && next != null) {
            val gapAfter = next.startMs - current.endMs
            if (gapAfter >= SUSPICIOUS_GAP_MS) {
                val estimatedStart = current.endMs + gapAfter / 2
                return current.copy(
                    startMs = estimatedStart.coerceAtMost(next.startMs),
                    endMs = next.startMs,
                )
            }
        }

        if (next == null && previous != null) {
            val gapBefore = current.startMs - previous.endMs
            if (gapBefore >= SUSPICIOUS_GAP_MS) {
                return current.copy(
                    startMs = previous.endMs,
                    endMs = current.endMs,
                )
            }
        }

        return current
    }

    fun wholeAyah(ayah: QuranAyah, timings: List<TimingSegment>): TimingSegment? {
        timings.firstOrNull() ?: return null
        val last = timings.lastOrNull() ?: return null
        // A whole ayah is a file-level playback request, not a concatenation
        // of word clips. QUL timestamps can begin after leading audio; start at
        // file offset zero and retain the measured final cue as stop boundary.
        return TimingSegment(0, ayah.words.size, 0, last.endMs)
    }
}
