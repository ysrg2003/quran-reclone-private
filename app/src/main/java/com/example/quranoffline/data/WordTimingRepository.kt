package com.example.quranoffline.data

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

/**
 * v25.11: per-word timings of the chosen reciter's own ayah files (Quran.Foundation verse audio segments).
 * One request per surah. They are saved next to the downloaded audio (AudioPackStore, NNN.timings.json), written at download time
 * and also the first time a surah is played online, so the highlight works with no internet once audio is downloaded.
 */
object WordTimingRepository {
    /** wordPos (1-based), startMs, endMs, relative to the start of that reciter's ayah file. */
    data class Seg(val wordPos: Int, val startMs: Int, val endMs: Int)
    private val cache = ConcurrentHashMap<String, Map<Int, List<Seg>>>()

    /** Blocking (file/network): run off the main thread. Empty list = no word timings for this reciter/ayah. */
    fun get(store: AudioPackStore, reciterId: Int, surah: Int, ayah: Int): List<Seg> {
        val k = "$reciterId:$surah"
        cache[k]?.let { return it[ayah].orEmpty() }
        val map = readFile(store, surah) ?: fetchSurah(reciterId, surah)?.also { saveIfDownloaded(store, surah, it) } ?: return emptyList()
        cache[k] = map
        return map[ayah].orEmpty()
    }

    /** Called by the downloader after a surah's audio is complete. Best effort; returns true when timings are stored. */
    fun saveForDownload(store: AudioPackStore, reciterId: Int, surah: Int): Boolean {
        if (store.timingsFile(surah).isFile) return true
        val m = fetchSurah(reciterId, surah) ?: return false
        saveIfDownloaded(store, surah, m)
        cache["$reciterId:$surah"] = m
        return store.timingsFile(surah).isFile
    }

    private fun saveIfDownloaded(store: AudioPackStore, surah: Int, m: Map<Int, List<Seg>>) {
        if (store.surahCount(surah) == 0) return
        try {
            val o = JSONObject()
            m.forEach { (a, segs) -> o.put(a.toString(), JSONArray().also { arr -> segs.forEach { arr.put(JSONArray().put(it.wordPos).put(it.startMs).put(it.endMs)) } }) }
            val f = store.timingsFile(surah); val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(o.toString()); tmp.renameTo(f)
        } catch (e: Exception) { }
    }

    private fun readFile(store: AudioPackStore, surah: Int): Map<Int, List<Seg>>? = try {
        val f = store.timingsFile(surah)
        if (!f.isFile) null else {
            val o = JSONObject(f.readText()); val out = HashMap<Int, List<Seg>>()
            o.keys().forEach { key -> val arr = o.getJSONArray(key); val l = ArrayList<Seg>(); for (i in 0 until arr.length()) { val a = arr.getJSONArray(i); l.add(Seg(a.getInt(0), a.getInt(1), a.getInt(2))) }; out[key.toInt()] = l }
            out
        }
    } catch (e: Exception) { null }

    private fun fetchSurah(reciterId: Int, surah: Int): Map<Int, List<Seg>>? = try {
        val c = URI("https://api.quran.com/api/v4/verses/by_chapter/$surah?audio=$reciterId&per_page=300&fields=verse_key").toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000; c.setRequestProperty("Accept", "application/json")
        if (c.responseCode != 200) null else {
            val verses = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getJSONArray("verses")
            val out = HashMap<Int, List<Seg>>()
            for (i in 0 until verses.length()) {
                val v = verses.getJSONObject(i); val seg = v.optJSONObject("audio")?.optJSONArray("segments") ?: continue
                val l = ArrayList<Seg>()
                for (j in 0 until seg.length()) { val a = seg.getJSONArray(j); if (a.length() >= 4) l.add(Seg(a.getInt(1), a.getInt(2), a.getInt(3))) }
                out[v.getInt("verse_number")] = l
            }
            if (out.isEmpty()) null else out
        }
    } catch (e: Exception) { null } // network failure is not cached

    /** Word being recited at [posMs]: the last word that has started (it stays lit through short gaps); null before the first word. */
    fun wordAt(segs: List<Seg>, posMs: Int): Int? = segs.lastOrNull { it.startMs <= posMs }?.wordPos
}
