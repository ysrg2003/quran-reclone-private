package com.example.quranoffline.data

import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

/** v25.9: per-word timings of the chosen reciter's own ayah file, from the Quran.Foundation (api.quran.com v4) verse audio segments. In-memory cache. */
object WordTimingRepository {
    /** wordPos (1-based), startMs, endMs, relative to the start of that reciter's ayah file. */
    data class Seg(val wordPos: Int, val startMs: Int, val endMs: Int)
    private val cache = ConcurrentHashMap<String, List<Seg>>()

    /** Blocking network call: run off the main thread. Empty list = the API gave no word timings for this reciter/ayah. */
    fun get(reciterId: Int, surah: Int, ayah: Int): List<Seg> {
        val k = "$reciterId:$surah:$ayah"
        cache[k]?.let { return it }
        val out: List<Seg> = try {
            val c = URI("https://api.quran.com/api/v4/verses/by_key/$surah:$ayah?audio=$reciterId").toURL().openConnection() as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 15000; c.setRequestProperty("Accept", "application/json")
            if (c.responseCode != 200) return emptyList()
            val raw = c.inputStream.bufferedReader().use { it.readText() }
            val seg = JSONObject(raw).getJSONObject("verse").optJSONObject("audio")?.optJSONArray("segments")
            val list = ArrayList<Seg>()
            if (seg != null) for (i in 0 until seg.length()) { val a = seg.getJSONArray(i); if (a.length() >= 4) list.add(Seg(a.getInt(1), a.getInt(2), a.getInt(3))) }
            list
        } catch (e: Exception) { return emptyList() } // network failure is not cached
        cache[k] = out
        return out
    }

    /** Word being recited at [posMs]: the last word that has started (it stays lit through short gaps); null before the first word. */
    fun wordAt(segs: List<Seg>, posMs: Int): Int? = segs.lastOrNull { it.startMs <= posMs }?.wordPos
}
