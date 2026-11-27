package com.example.quranoffline.data

import android.content.Context
import java.io.File

/** Private, app-only storage for user-requested ayah audio (Husary via Quran.com). Never exported. */
class AudioPackStore(private val root: File) {
    constructor(context: Context) : this(File(context.filesDir, "audio_packs/husary"))

    fun file(surah: Int, ayah: Int) = File(root, "%03d%03d.mp3".format(surah, ayah))
    fun part(surah: Int, ayah: Int) = File(root, "%03d%03d.mp3.part".format(surah, ayah))
    /** A file counts only when complete; partial downloads live in .part files. */
    fun localAyah(surah: Int, ayah: Int): File? = file(surah, ayah).takeIf { it.isFile && it.length() > 0 }
    fun surahFiles(surah: Int): List<File> =
        root.listFiles { f -> f.name.endsWith(".mp3") && f.name.startsWith("%03d".format(surah)) }?.toList().orEmpty()
    fun surahBytes(surah: Int): Long = surahFiles(surah).sumOf { it.length() }
    fun surahCount(surah: Int): Int = surahFiles(surah).size
    fun usedBytes(): Long = root.listFiles()?.sumOf { it.length() } ?: 0L
    fun deleteSurah(surah: Int) {
        root.listFiles { f -> f.name.startsWith("%03d".format(surah)) }?.forEach { it.delete() }
    }
    fun deleteAll() { root.listFiles()?.forEach { it.delete() } }
    fun ensureDir() { root.mkdirs() }
}
