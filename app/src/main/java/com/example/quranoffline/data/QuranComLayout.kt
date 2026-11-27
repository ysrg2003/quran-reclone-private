package com.example.quranoffline.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** One word position from the quran.com API: which mushaf line it sits on, or the ayah-end marker. */
data class QcWord(val line: Int, val isEnd: Boolean)
data class QcVerse(val surah: Int, val ayah: Int, val words: List<QcWord>)
data class QcPage(val page: Int, val verses: List<QcVerse>)

/**
 * Builds a mushaf page/line layout that follows quran.com's page breaks and line breaks.
 * Only the grouping comes from the API. The Arabic text of every line is the app's own
 * bundled Word text, joined verbatim, so tasmee matching and glyphs stay unchanged.
 */
object QuranComLayoutBuilder {
    fun arabicDigits(n: Int): String = n.toString().map { '\u0660' + (it - '0') }.joinToString("")

    fun build(
        pages: List<QcPage>,
        ayahs: Map<String, QuranAyah>,
        suraTexts: Map<Int, String>,
        bismillahText: String?,
    ): WordReferenceLayout {
        val sorted = pages.sortedBy { it.page }
        var pendingHeader: Int? = null // sura title already printed at the bottom of the previous page
        val out = sorted.mapIndexed { pageIdx, page ->
            // line -> ordered segments
            class Seg(val key: String, val from: Int, val to: Int, val end: Boolean)
            val byLine = sortedMapOf<Int, MutableList<Seg>>()
            page.verses.forEach { v ->
                val key = "${v.surah}:${v.ayah}"
                val ours = ayahs[key]?.words?.map { it.text } ?: emptyList()
                val real = v.words.filter { !it.isEnd }
                val c = real.size
                val n = ours.size
                var i = 0
                while (i < c) {
                    var j = i
                    while (j + 1 < c && real[j + 1].line == real[i].line) j++
                    val from = if (c == n) i else i * n / c
                    val to = if (c == n) j + 1 else (j + 1) * n / c
                    byLine.getOrPut(real[i].line) { mutableListOf() }.add(Seg(key, from, to, false))
                    i = j + 1
                }
                val endLine = v.words.firstOrNull { it.isEnd }?.line ?: real.lastOrNull()?.line
                if (endLine != null) {
                    val list = byLine.getOrPut(endLine) { mutableListOf() }
                    val last = list.lastOrNull()
                    if (last != null && last.key == key) { list[list.size - 1] = Seg(key, last.from, last.to, true) }
                    else list.add(Seg(key, n, n, true))
                }
            }
            val lines = mutableListOf<WordReferenceLine>()
            val maxLine = byLine.keys.maxOrNull() ?: 0
            var line = 1
            while (line <= maxLine) {
                val segs = byLine[line]
                if (segs == null) {
                    // Gap: sura header (and bismillah) lines that precede the next ayah line.
                    val nextLine = byLine.keys.firstOrNull { it > line }
                    var run = 0
                    while (line + run < (nextLine ?: (maxLine + 1))) run++
                    val first = nextLine?.let { byLine[it]?.firstOrNull() }
                    val startsSura = first != null && first.key.endsWith(":1") && first.from == 0
                    if (startsSura) {
                        val surah = first!!.key.substringBefore(':').toInt()
                        val wantsBismillah = bismillahText != null && surah != 1 && surah != 9
                        if (pendingHeader == surah) {
                            if (wantsBismillah) lines += WordReferenceLine(line, "bismillah", bismillahText!!, emptyList(), emptyList())
                        } else {
                            suraTexts[surah]?.let { lines += WordReferenceLine(line, "sura", it, emptyList(), emptyList()) }
                            if (run >= 2 && wantsBismillah) lines += WordReferenceLine(line + 1, "bismillah", bismillahText!!, emptyList(), emptyList())
                        }
                        pendingHeader = null
                    }
                    line += run
                    continue
                }
                val sb = StringBuilder()
                val keys = mutableListOf<String>()
                val ranges = LinkedHashMap<String, Pair<Int, Int>>()
                val numbers = mutableListOf<WordReferenceAyahNumber>()
                segs.forEach { s ->
                    val words = ayahs[s.key]?.words.orEmpty()
                    val part = words.subList(s.from.coerceIn(0, words.size), s.to.coerceIn(0, words.size)).joinToString(" ") { it.text }
                    if (part.isNotEmpty()) { if (sb.isNotEmpty()) sb.append(' '); sb.append(part) }
                    if (s.key !in keys) keys += s.key
                    if (s.to > s.from) ranges[s.key] = s.from to s.to
                    if (s.end) {
                        val num = s.key.substringAfter(':').toInt()
                        val digits = arabicDigits(num)
                        if (sb.isNotEmpty()) sb.append(' ')
                        val start = sb.length
                        sb.append(digits)
                        numbers += WordReferenceAyahNumber(start, sb.length, digits, num)
                    }
                }
                lines += WordReferenceLine(line, "ayah", sb.toString(), keys, numbers, ranges)
                line++
            }
            pendingHeader = null
            val expected = if (page.page <= 2) 8 else 15
            if (maxLine == expected - 1) {
                // quran.com's mushaf prints a sura title on the last line when the sura's first ayah opens the next page.
                val nextVerse = sorted.getOrNull(pageIdx + 1)?.verses?.firstOrNull()
                if (nextVerse != null && nextVerse.ayah == 1) {
                    suraTexts[nextVerse.surah]?.let { lines += WordReferenceLine(expected, "sura", it, emptyList(), emptyList()) }
                    pendingHeader = nextVerse.surah
                }
            }
            WordReferencePage(page.page, lines)
        }
        return WordReferenceLayout(
            schemaVersion = 4,
            source = "quran.com API v4 page/line grouping; text from bundled Word Quran text",
            fontFamily = "KFGQPC HAFS Uthmanic Script",
            pages = out,
            declaredPageCount = out.size,
        )
    }

    /** Checks that every ayah appears exactly once across pages in Quran order. */
    fun isConsistent(layout: WordReferenceLayout, expectedAyahs: List<QuranAyah>): Boolean {
        if (layout.pageCount != 604 || layout.pages.size != 604) return false
        val seen = layout.pages.flatMap { p -> p.lines.flatMap { it.ayahKeys } }.distinct()
        val expected = expectedAyahs.map { "${it.surah}:${it.ayah}" }
        return seen == expected
    }

    fun toJson(layout: WordReferenceLayout): String {
        val root = JSONObject()
        root.put("schemaVersion", layout.schemaVersion)
        root.put("source", layout.source)
        root.put("fontFamily", layout.fontFamily)
        root.put("pageCount", layout.pageCount)
        val pages = JSONArray()
        layout.pages.forEach { p ->
            val lines = JSONArray()
            p.lines.forEach { l ->
                val o = JSONObject()
                o.put("number", l.number); o.put("type", l.type); o.put("text", l.text)
                o.put("ayahKeys", JSONArray(l.ayahKeys))
                val nums = JSONArray()
                l.ayahNumbers.forEach { n -> nums.put(JSONObject().put("start", n.start).put("end", n.end).put("text", n.text).put("number", n.number)) }
                o.put("ayahNumbers", nums)
                if (l.wordRanges.isNotEmpty()) {
                    val wr = JSONObject()
                    l.wordRanges.forEach { (k, r) -> wr.put(k, JSONArray(listOf(r.first, r.second))) }
                    o.put("wordRanges", wr)
                }
                lines.put(o)
            }
            pages.put(JSONObject().put("page", p.page).put("lines", lines))
        }
        root.put("pages", pages)
        return root.toString()
    }
}

/** Optional quran.com layout, downloaded on the user's request into private app storage. */
object QuranComLayoutStore {
    private const val FILE_NAME = "quran_com_layout_v1.json"
    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)

    fun isInstalled(context: Context): Boolean = file(context).isFile
    fun remove(context: Context) { file(context).delete() }

    /** Returns the cached layout JSON, or null. Caller validates before use. */
    fun readCached(context: Context): String? = runCatching { file(context).takeIf { it.isFile }?.readText() }.getOrNull()

    /** Downloads 604 pages (a few MB total), builds, validates, then writes atomically. Blocking: call off the main thread. */
    fun downloadAndInstall(context: Context, onProgress: (Int) -> Unit): Result<Unit> = runCatching {
        val appContext = context.applicationContext
        val repo = QuranRepository(appContext)
        val allAyahs = repo.allAyahs()
        val ayahMap = allAyahs.associateBy { "${it.surah}:${it.ayah}" }
        val bundled = WordReferenceLayout.loadBundled(appContext)
        val suraTexts = HashMap<Int, String>()
        var bismillah: String? = null
        bundled.pages.forEach { p ->
            val lines = p.lines
            lines.forEachIndexed { idx, l ->
                if (l.type == "bismillah" && bismillah == null) bismillah = l.text
                if (l.type == "sura") {
                    val next = lines.drop(idx + 1).firstOrNull { it.ayahKeys.isNotEmpty() }?.ayahKeys?.firstOrNull()
                    next?.substringBefore(':')?.toIntOrNull()?.let { suraTexts.putIfAbsent(it, l.text) }
                }
            }
        }
        val done = AtomicInteger(0)
        val results = arrayOfNulls<QcPage>(605)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val futures = (1..604).map { page ->
                pool.submit {
                    var last: Throwable? = null
                    repeat(3) { attempt ->
                        try { results[page] = fetchPage(page); done.incrementAndGet().also(onProgress); return@submit } catch (t: Throwable) { last = t; Thread.sleep(400L * (attempt + 1)) }
                    }
                    throw last ?: IllegalStateException("page $page failed")
                }
            }
            futures.forEach { it.get() }
        } finally { pool.shutdownNow() }
        val pages = (1..604).map { requireNotNull(results[it]) { "missing page $it" } }
        val layout = QuranComLayoutBuilder.build(pages, ayahMap, suraTexts, bismillah)
        check(QuranComLayoutBuilder.isConsistent(layout, allAyahs)) { "quran.com layout did not match the Quran text; not installed" }
        val target = file(appContext)
        val tmp = File(target.path + ".part")
        tmp.writeText(QuranComLayoutBuilder.toJson(layout))
        check(tmp.renameTo(target)) { "could not save layout" }
    }

    private fun fetchPage(page: Int): QcPage {
        val url = URL("https://api.quran.com/api/v4/verses/by_page/$page?words=true&word_fields=line_number&per_page=50&fields=verse_key")
        val c = url.openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15000; c.readTimeout = 30000
            c.setRequestProperty("Accept", "application/json")
            check(c.responseCode == 200) { "quran.com HTTP ${c.responseCode}" }
            val verses = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getJSONArray("verses")
            val list = (0 until verses.length()).map { i ->
                val v = verses.getJSONObject(i)
                val key = v.getString("verse_key")
                val words = v.getJSONArray("words")
                QcVerse(
                    key.substringBefore(':').toInt(), key.substringAfter(':').toInt(),
                    (0 until words.length()).map { w ->
                        val o = words.getJSONObject(w)
                        QcWord(o.getInt("line_number"), o.optString("char_type_name") == "end")
                    },
                )
            }
            return QcPage(page, list)
        } finally { c.disconnect() }
    }
}
