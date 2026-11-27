package com.example.quranoffline.data

import android.content.Context
import java.io.File
import org.json.JSONObject

/** Private storage for per-word audio (Quran.com wbw files). One index file per surah guards against misaligned words. */
class WordPackStore(private val root: File) {
    constructor(context: Context) : this(File(context.filesDir, "audio_packs/words"))

    fun file(s: Int, a: Int, w: Int) = File(root, "%03d_%03d_%03d.mp3".format(s, a, w))
    fun part(s: Int, a: Int, w: Int) = File(root, "%03d_%03d_%03d.mp3.part".format(s, a, w))
    fun index(s: Int) = File(root, "%03d.idx".format(s))
    fun ensureDir() { root.mkdirs() }
    private fun surahFiles(s: Int) = root.listFiles { f -> f.name.endsWith(".mp3") && f.name.startsWith("%03d_".format(s)) }?.toList().orEmpty()
    fun surahCount(s: Int) = surahFiles(s).size
    fun surahBytes(s: Int) = surahFiles(s).sumOf { it.length() }
    /** One directory pass for all 114 surahs (index 1..114). */
    fun countsBySurah(): IntArray {
        val out = IntArray(115)
        root.list()?.forEach { n -> if (n.endsWith(".mp3") && n.length >= 4 && n[3] == '_') n.substring(0, 3).toIntOrNull()?.let { if (it in 1..114) out[it]++ } }
        return out
    }
    fun usedBytes() = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    fun deleteSurah(s: Int) { root.listFiles { f -> f.name.startsWith("%03d".format(s)) }?.forEach { it.delete() } }
    fun deleteAll() { root.deleteRecursively() }

    /** Index lines: "n<TAB>ayah<TAB>wordCount" and "w<TAB>ayah<TAB>pos<TAB>imlaei<TAB>uthmani" (normalized). */
    fun writeIndex(s: Int, lines: List<String>) { ensureDir(); index(s).writeText(lines.joinToString("\n")) }

    private companion object { val indexCache = HashMap<String, ParsedIndex>() }

    private class ParsedIndex(val stamp: Long, val counts: Map<Int, Int>, val words: Map<Long, Pair<String, String>>)

    /** The per-surah index is parsed once and reused while the file is unchanged (it used to be re-read for every word played, on the main thread). */
    private fun indexFor(s: Int): ParsedIndex? {
        val f = index(s)
        val stamp = f.lastModified() * 31 + f.length()
        synchronized(indexCache) {
            indexCache["${root.path}#$s"]?.takeIf { it.stamp == stamp }?.let { return it }
        }
        val rows = runCatching { f.readLines().map { it.split('\t') } }.getOrNull() ?: return null
        val counts = HashMap<Int, Int>()
        val words = HashMap<Long, Pair<String, String>>()
        rows.forEach { r ->
            if (r.size == 3 && r[0] == "n") { val a = r[1].toIntOrNull(); val n = r[2].toIntOrNull(); if (a != null && n != null) counts.putIfAbsent(a, n) }
            else if (r.size == 5 && r[0] == "w") { val a = r[1].toIntOrNull(); val w = r[2].toIntOrNull(); if (a != null && w != null) words.putIfAbsent(a * 1000 + w + 0L, r[3] to r[4]) }
        }
        return ParsedIndex(stamp, counts, words).also { synchronized(indexCache) { indexCache["${root.path}#$s"] = it } }
    }

    /** Local file for a word only if complete and the API's word identity and count match what the app asks for. */
    fun localWord(s: Int, a: Int, w: Int, count: Int, normalizedText: String): File? {
        val f = file(s, a, w)
        if (!f.isFile || f.length() == 0L) return null
        val idx = indexFor(s) ?: return null
        if (idx.counts[a] != count) return null
        val row = idx.words[a * 1000 + w + 0L] ?: return null
        if (normalizedText.isEmpty() || (normalizedText != row.first && normalizedText != row.second)) return null
        return f
    }
}

class WordDownloader(
    private val store: WordPackStore,
    private val networkAllowed: () -> Boolean,
    private val http: AudioDownloader,
    private val openJson: (String) -> java.net.HttpURLConnection = { (java.net.URI(it).toURL().openConnection() as java.net.HttpURLConnection) },
) {
    data class Entry(val ayah: Int, val pos: Int, val url: String)

    /** One request per surah: all words with audio url and imlaei text. Also writes the identity index. */
    fun listWords(surah: Int): List<Entry> {
        val c = openJson("${QuranComAudio.API}/verses/by_chapter/$surah?words=true&word_fields=audio_url,text_uthmani,text_imlaei&per_page=300")
        try {
            c.connectTimeout = 15000; c.readTimeout = 40000; c.setRequestProperty("Accept", "application/json")
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            val verses = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getJSONArray("verses")
            val out = ArrayList<Entry>(); val idx = ArrayList<String>()
            for (i in 0 until verses.length()) {
                val v = verses.getJSONObject(i)
                val key = v.getString("verse_key").split(":"); require(key[0].toInt() == surah)
                val a = key[1].toInt(); val words = v.getJSONArray("words"); var n = 0
                for (j in 0 until words.length()) {
                    val w = words.getJSONObject(j)
                    if (w.getString("char_type_name") != "word") continue
                    n++
                    val pos = w.getInt("position")
                    out += Entry(a, pos, QuranComAudio.media(w.getString("audio_url"), "https://audio.qurancdn.com/"))
                    idx += "w\t$a\t$pos\t${QuranComAudio.normalized(w.optString("text_imlaei"))}\t${QuranComAudio.normalized(w.optString("text_uthmani"))}"
                }
                idx += "n\t$a\t$n"
            }
            store.writeIndex(surah, idx)
            return out
        } finally { c.disconnect() }
    }

    fun downloadSurah(surah: Int, cancelled: () -> Boolean, progress: (done: Int, total: Int) -> Unit): AudioDownloader.Result {
        store.ensureDir()
        val words = try { listWords(surah) } catch (e: Throwable) { return AudioDownloader.Result.Failed("تعذر جلب قائمة الكلمات: ${e.message ?: e.javaClass.simpleName}") }
        var done = 0
        for (w in words) {
            if (cancelled()) return AudioDownloader.Result.Cancelled
            if (!networkAllowed()) return AudioDownloader.Result.Failed("الشبكة غير متاحة أو الوضع Wi-Fi فقط مفعّل")
            if (!store.file(surah, w.ayah, w.pos).let { it.isFile && it.length() > 0 }) {
                val r = try { http.fetch(w.url, store.part(surah, w.ayah, w.pos), store.file(surah, w.ayah, w.pos), cancelled) } catch (e: Throwable) { AudioDownloader.Result.Failed("خطأ: ${e.message ?: e.javaClass.simpleName}") }
                if (r is AudioDownloader.Result.Cancelled || r is AudioDownloader.Result.Failed) return r
            }
            done++; if (done % 5 == 0 || done == words.size) progress(done, words.size)
        }
        return AudioDownloader.Result.Done(done, store.surahBytes(surah))
    }

    fun wordUrls(surah: Int) = listWords(surah)
}
