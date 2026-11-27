package com.example.quranoffline.data

/** One shared set of lookup tables per loaded Quran, built once and reused by every screen and page turn. */
class AyahLookup private constructor(val source: List<QuranAyah>) {
    private val indexByKey: HashMap<Long, Int> = HashMap<Long, Int>(source.size * 2).also { m ->
        source.forEachIndexed { i, a -> m[key(a.surah, a.ayah)] = i }
    }

    /** wordOffsets[i] = words before ayah i; last entry is the total. */
    val wordOffsets: IntArray = IntArray(source.size + 1).also { o ->
        var sum = 0
        source.forEachIndexed { i, a -> o[i] = sum; sum += a.words.size }
        o[source.size] = sum
    }

    val byStringKey: Map<String, QuranAyah> by lazy(LazyThreadSafetyMode.PUBLICATION) { source.associateBy { it.selectionKey() } }
    val indexByStringKey: Map<String, Int> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        HashMap<String, Int>(source.size * 2).also { m -> source.forEachIndexed { i, a -> m[a.selectionKey()] = i } }
    }
    val wordOffsetsList: List<Int> by lazy(LazyThreadSafetyMode.PUBLICATION) { wordOffsets.asList() }

    fun indexOf(surah: Int, ayah: Int): Int = indexByKey[key(surah, ayah)] ?: -1

    companion object {
        private fun key(surah: Int, ayah: Int) = surah.toLong() * 1000L + ayah
        @Volatile private var cached: AyahLookup? = null

        /** Rebuilt only when a different list instance is loaded (identity check, O(1)). */
        fun of(source: List<QuranAyah>): AyahLookup {
            cached?.takeIf { it.source === source }?.let { return it }
            return synchronized(this) {
                cached?.takeIf { it.source === source } ?: AyahLookup(source).also { cached = it }
            }
        }
    }
}
