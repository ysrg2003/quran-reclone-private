package com.example.quranoffline.data

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * One-time binary cache of the parsed bundled data (v22). The big JSON assets
 * (about 25 MB) were re-parsed at every launch; the first launch still parses
 * them and then stores the parsed result in the app's private files folder.
 * Later launches read the compact binary file instead.
 *
 * Safety: any problem (missing, older app build, wrong header, truncated,
 * corrupt, exception) returns null and the caller rebuilds from the JSON
 * assets, exactly like before. A bad file is deleted. Writing is atomic
 * (temp file, then rename) and never blocks or crashes the caller.
 * The cache only holds data that is already bundled in the APK; it holds no
 * user data and is rebuilt whenever the app is updated.
 */
object ParsedCache {
    private const val MAGIC = 0x51434143 // "QCAC"
    private const val FORMAT = 1
    private const val END = 0x454E4421

    private fun dir(context: Context): File = File(context.applicationContext.filesDir, "parsed_cache").also { it.mkdirs() }

    /** Changes when the APK is updated or reinstalled, so stale data is never reused. */
    fun signature(context: Context): Long = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime xor (info.versionName?.hashCode()?.toLong() ?: 0L)
    }.getOrDefault(0L)

    /** Returns the cached value, or builds it with [build] and stores it. Never throws because of the cache. */
    fun <T> load(
        context: Context,
        name: String,
        sizeHint: Int,
        build: () -> T,
        write: (DataOutputStream, T) -> Unit,
        read: (DataInputStream) -> T,
    ): T {
        val file = File(dir(context), "$name.bin")
        val sig = signature(context)
        val cached = runCatching { readFile(file, sig, sizeHint, read) }.getOrNull()
        if (cached != null) return cached
        runCatching { file.delete() }
        val built = build()
        runCatching { writeFile(file, sig, sizeHint, built, write) }
        return built
    }

    fun <T> readFile(file: File, sig: Long, sizeHint: Int, read: (DataInputStream) -> T): T? {
        if (!file.isFile || file.length() < 24) return null
        DataInputStream(BufferedInputStream(file.inputStream(), 1 shl 16)).use { input ->
            if (input.readInt() != MAGIC || input.readInt() != FORMAT || input.readLong() != sig || input.readInt() != sizeHint) return null
            val value = read(input)
            if (input.readInt() != END || input.read() != -1) return null
            return value
        }
    }

    fun <T> writeFile(file: File, sig: Long, sizeHint: Int, value: T, write: (DataOutputStream, T) -> Unit) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            DataOutputStream(BufferedOutputStream(tmp.outputStream(), 1 shl 16)).use { out ->
                out.writeInt(MAGIC); out.writeInt(FORMAT); out.writeLong(sig); out.writeInt(sizeHint)
                write(out, value)
                out.writeInt(END)
            }
            if (!tmp.renameTo(file)) { file.delete(); if (!tmp.renameTo(file)) tmp.delete() }
        } catch (t: Throwable) {
            runCatching { tmp.delete() }
            runCatching { file.delete() }
        }
    }

    // ---- string helpers (writeUTF is limited to 64 KB, so use our own) ----
    private fun DataOutputStream.str(s: String) { val b = s.toByteArray(Charsets.UTF_8); writeInt(b.size); write(b) }
    private fun DataInputStream.str(): String {
        val n = readInt(); require(n in 0..4_000_000)
        val b = ByteArray(n); readFully(b); return String(b, Charsets.UTF_8)
    }
    private fun DataOutputStream.nullableInt(v: Int?) { if (v == null) writeBoolean(false) else { writeBoolean(true); writeInt(v) } }
    private fun DataInputStream.nullableInt(): Int? = if (readBoolean()) readInt() else null
    private fun DataOutputStream.strs(c: Collection<String>) { writeInt(c.size); c.forEach { str(it) } }
    private fun DataInputStream.strList(): List<String> { val n = readInt(); require(n in 0..1_000_000); return ArrayList<String>(n).also { l -> repeat(n) { l.add(str()) } } }
    private fun DataInputStream.strSet(): Set<String> { val n = readInt(); require(n in 0..1_000_000); return if (n == 0) emptySet() else LinkedHashSet<String>(n * 2).also { s -> repeat(n) { s.add(str()) } } }

    // ---- Quran text (with recognizer forms already merged) ----
    class QuranData(val ayahs: List<QuranAyah>, val spacedForms: List<String>)

    fun writeQuran(out: DataOutputStream, data: QuranData) {
        out.strs(data.spacedForms)
        out.writeInt(data.ayahs.size)
        data.ayahs.forEach { a ->
            out.writeInt(a.surah); out.writeInt(a.ayah); out.str(a.text); out.str(a.matchText)
            out.writeInt(a.words.size)
            a.words.forEach { w ->
                out.writeInt(w.index); out.str(w.text); out.str(w.matchText); out.str(w.spokenForm)
                out.strs(w.recognizedForms); out.strs(w.waqfForms)
            }
        }
    }

    fun readQuran(input: DataInputStream): QuranData {
        val spaced = input.strList()
        val n = input.readInt(); require(n in 0..7000)
        val list = ArrayList<QuranAyah>(n)
        repeat(n) {
            val surah = input.readInt(); val ayah = input.readInt(); val text = input.str(); val match = input.str()
            val wc = input.readInt(); require(wc in 0..2000)
            val words = ArrayList<QuranWord>(wc)
            repeat(wc) {
                words.add(QuranWord(input.readInt(), input.str(), input.str(), input.str(), input.strSet(), input.strSet()))
            }
            list.add(QuranAyah(surah, ayah, text, match, words))
        }
        return QuranData(list, spaced)
    }

    // ---- Pronunciation reference ----
    fun writePronunciation(out: DataOutputStream, list: List<PronunciationAyah>) {
        out.writeInt(list.size)
        list.forEach { a ->
            out.writeInt(a.surah); out.writeInt(a.ayah); out.str(a.spoken); out.writeInt(a.words.size)
            a.words.forEach { w ->
                out.writeInt(w.index); out.str(w.spoken); out.strs(w.spokenTokens)
                out.writeInt(w.maddPositions.size); w.maddPositions.forEach { out.writeInt(it) }
                out.nullableInt(w.audioStartMs); out.nullableInt(w.audioEndMs)
                out.nullableInt(w.audioWordStart); out.nullableInt(w.audioWordEnd)
                out.strs(w.recognizedForms); out.strs(w.waqfForms)
            }
        }
    }

    fun readPronunciation(input: DataInputStream): List<PronunciationAyah> {
        val n = input.readInt(); require(n in 0..7000)
        val list = ArrayList<PronunciationAyah>(n)
        repeat(n) {
            val surah = input.readInt(); val ayah = input.readInt(); val spoken = input.str(); val wc = input.readInt(); require(wc in 0..2000)
            val words = ArrayList<PronunciationWord>(wc)
            repeat(wc) {
                val index = input.readInt(); val sp = input.str(); val tokens = input.strList()
                val mc = input.readInt(); require(mc in 0..1000)
                val madd = if (mc == 0) emptySet() else LinkedHashSet<Int>().also { s -> repeat(mc) { s.add(input.readInt()) } }
                words.add(PronunciationWord(index, sp, tokens, madd, input.nullableInt(), input.nullableInt(), input.nullableInt(), input.nullableInt(), input.strSet(), input.strSet()))
            }
            list.add(PronunciationAyah(surah, ayah, spoken, words))
        }
        return list
    }

    // ---- Page layout ----
    fun writeLayout(out: DataOutputStream, l: WordReferenceLayout) {
        out.writeInt(l.schemaVersion); out.str(l.source); out.str(l.fontFamily); out.writeInt(l.declaredPageCount)
        out.writeInt(l.pages.size)
        l.pages.forEach { p ->
            out.writeInt(p.page); out.writeInt(p.lines.size)
            p.lines.forEach { line ->
                out.writeInt(line.number); out.str(line.type); out.str(line.text); out.strs(line.ayahKeys)
                out.writeInt(line.ayahNumbers.size)
                line.ayahNumbers.forEach { n -> out.writeInt(n.start); out.writeInt(n.end); out.str(n.text); out.writeInt(n.number) }
                out.writeInt(line.wordRanges.size)
                line.wordRanges.forEach { (k, r) -> out.str(k); out.writeInt(r.first); out.writeInt(r.second) }
            }
        }
    }

    fun readLayout(input: DataInputStream): WordReferenceLayout {
        val schema = input.readInt(); val source = input.str(); val font = input.str(); val declared = input.readInt()
        val pc = input.readInt(); require(pc in 0..2000)
        val pages = ArrayList<WordReferencePage>(pc)
        repeat(pc) {
            val page = input.readInt(); val lc = input.readInt(); require(lc in 0..200)
            val lines = ArrayList<WordReferenceLine>(lc)
            repeat(lc) {
                val number = input.readInt(); val type = input.str(); val text = input.str(); val keys = input.strList()
                val nc = input.readInt(); require(nc in 0..200)
                val nums = ArrayList<WordReferenceAyahNumber>(nc)
                repeat(nc) { nums.add(WordReferenceAyahNumber(input.readInt(), input.readInt(), input.str(), input.readInt())) }
                val rc = input.readInt(); require(rc in 0..200)
                val ranges = if (rc == 0) emptyMap() else LinkedHashMap<String, Pair<Int, Int>>().also { m -> repeat(rc) { m[input.str()] = input.readInt() to input.readInt() } }
                lines.add(WordReferenceLine(number, type, text, keys, nums, ranges))
            }
            pages.add(WordReferencePage(page, lines))
        }
        return WordReferenceLayout(schema, source, font, pages, declared)
    }
}
