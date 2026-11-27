package com.example.quranoffline.data

import android.content.Context
import java.io.File

/** Private, app-only storage for user-requested ayah audio (Husary via Quran.com). Never exported. */
class AudioPackStore(private val root: File) {
    constructor(context: Context, reciterId: Int = Reciters.DEFAULT_ID) : this(dirFor(context, reciterId))

    fun file(surah: Int, ayah: Int) = File(root, "%03d%03d.mp3".format(surah, ayah))
    fun part(surah: Int, ayah: Int) = File(root, "%03d%03d.mp3.part".format(surah, ayah))
    /** A file counts only when complete; partial downloads live in .part files. */
    fun localAyah(surah: Int, ayah: Int): File? = file(surah, ayah).takeIf { it.isFile && it.length() > 0 }
    fun surahFiles(surah: Int): List<File> =
        root.listFiles { f -> f.name.endsWith(".mp3") && f.name.startsWith("%03d".format(surah)) }?.toList().orEmpty()
    fun surahBytes(surah: Int): Long = surahFiles(surah).sumOf { it.length() }
    fun surahCount(surah: Int): Int = surahFiles(surah).size
    /** One directory pass for all 114 surahs (index 1..114). Replaces 114 separate directory scans. */
    fun countsBySurah(): IntArray {
        val out = IntArray(115)
        root.list()?.forEach { n -> if (n.endsWith(".mp3") && n.length >= 3) n.substring(0, 3).toIntOrNull()?.let { if (it in 1..114) out[it]++ } }
        return out
    }
    fun usedBytes(): Long = root.listFiles()?.sumOf { it.length() } ?: 0L
    fun deleteSurah(surah: Int) {
        root.listFiles { f -> f.name.startsWith("%03d".format(surah)) }?.forEach { it.delete() }
    }
    fun deleteAll() { root.listFiles()?.forEach { it.delete() } }
    fun ensureDir() { root.mkdirs() }

    companion object {
        /** Reciter 6 keeps the v12 folder name so earlier downloads stay valid. */
        fun dirFor(context: Context, reciterId: Int) = File(context.filesDir, if (reciterId == Reciters.DEFAULT_ID) "audio_packs/husary" else "audio_packs/r$reciterId")
        fun rootAll(context: Context) = File(context.filesDir, "audio_packs")
        fun usedBytesAll(context: Context): Long = rootAll(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }
        fun deleteEverything(context: Context) { rootAll(context).deleteRecursively() }
    }
}
