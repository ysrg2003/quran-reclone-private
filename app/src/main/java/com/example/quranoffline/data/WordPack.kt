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
    fun usedBytes() = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    fun deleteSurah(s: Int) { root.listFiles { f -> f.name.startsWith("%03d".format(s)) }?.forEach { it.delete() } }
    fun deleteAll() { root.deleteRecursively() }

    /** Index lines: "n<TAB>ayah<TAB>wordCount" and "w<TAB>ayah<TAB>pos<TAB>imlaei<TAB>uthmani" (normalized). */
    fun writeIndex(s: Int, lines: List<String>) { ensureDir(); index(s).writeText(lines.joinToString("\n")) }

    /** Local file for a word only if complete and the API's word identity and count match what the app asks for. */
    fun localWord(s: Int, a: Int, w: Int, count: Int, normalizedText: String): File? {
        val f = file(s, a, w)
        if (!f.isFile || f.length() == 0L) return null
        val rows = runCatching { index(s).readLines().map { it.split('\t') } }.getOrNull() ?: return null
        val n = rows.firstOrNull { it.size == 3 && it[0] == "n" && it[1] == a.toString() }?.get(2)?.toIntOrNull()
        if (n != count) return null
        val row = rows.firstOrNull { it.size == 5 && it[0] == "w" && it[1] == a.toString() && it[2] == w.toString() } ?: return null
        if (normalizedText.isEmpty() || (normalizedText != row[3] && normalizedText != row[4])) return null
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
