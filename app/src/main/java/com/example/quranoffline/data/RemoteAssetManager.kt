package com.example.quranoffline.data

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class RemoteAssetManager(context: Context) {
    private val appContext = context.applicationContext
    private val filesRoot = File(appContext.filesDir, "quran-assets")
    private val audioRoot = File(filesRoot, "audio/Alafasy_128kbps")
    private val modelRoot = File(filesRoot, "models/fastconformer-quran-ar-onnx-int8")

    companion object {
        const val BASE_URL = "https://raw.githubusercontent.com/ysrg2003/quran-memorization-assets/main"
        const val TEXT_URL = "$BASE_URL/quran/quran_content.json"
        const val TIMINGS_URL = "$BASE_URL/timings/Alafasy_128kbps.json"
        private const val TEXT_ASSET_VERSION = "2026.08.24.1"
        const val MODEL_ZIP_URL = "https://github.com/ysrg2003/quran-memorization-assets/releases/download/v0.1.0/fastconformer-quran-ar-onnx-int8.zip"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 120_000
    }

    fun audioUrl(surah: Int, ayah: Int): String =
        "$BASE_URL/audio/Alafasy_128kbps/${surah.toString().padStart(3, '0')}${ayah.toString().padStart(3, '0')}.mp3"

    fun cachedAudioFile(surah: Int, ayah: Int): File? {
        val file = File(audioRoot, "${surah.toString().padStart(3, '0')}${ayah.toString().padStart(3, '0')}.mp3")
        return file.takeIf { it.isFile && it.length() > 0L }
    }

    fun isSurahDownloaded(surah: Int, ayahCount: Int): Boolean =
        (1..ayahCount).all { cachedAudioFile(surah, it) != null }

    fun downloadedAyahCount(surah: Int, ayahCount: Int): Int =
        (1..ayahCount).count { cachedAudioFile(surah, it) != null }

    fun isModelDownloaded(): Boolean = listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt").all { File(modelRoot, it).isFile && File(modelRoot, it).length() > 0L }

    fun modelDirectory(): File = modelRoot

    fun downloadModel(): File {
        if (isModelDownloaded()) return modelRoot
        val temporaryRoot = File(filesRoot, "models/.fastconformer-download")
        temporaryRoot.deleteRecursively()
        temporaryRoot.mkdirs()
        val connection = open(MODEL_ZIP_URL)
        try {
            ZipInputStream(connection.inputStream.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val fileName = File(entry.name).name
                        if (fileName in setOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt")) {
                            File(temporaryRoot, fileName).outputStream().use { output -> zip.copyTo(output, DEFAULT_BUFFER_SIZE) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } finally {
            connection.disconnect()
        }
        val expected = listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt")
        check(expected.all { File(temporaryRoot, it).isFile && File(temporaryRoot, it).length() > 0L }) { "Downloaded model archive is incomplete" }
        modelRoot.deleteRecursively()
        modelRoot.parentFile?.mkdirs()
        check(temporaryRoot.renameTo(modelRoot)) { "Could not finalize model download" }
        return modelRoot
    }

    fun deleteSurah(surah: Int, ayahCount: Int) {
        (1..ayahCount).forEach { ayah -> cachedAudioFile(surah, ayah)?.delete() }
    }

    fun fetchText(): String = fetch(TEXT_URL)

    fun fetchTimings(): String = fetch(TIMINGS_URL)

    fun loadOrDownloadText(): String {
        val file = File(filesRoot, "quran_content.json")
        val versionFile = File(filesRoot, "quran_content.version")
        val hasUsableCache = file.isFile && file.length() > 0L
        val cacheVersion = versionFile.takeIf { it.isFile }?.readText()?.trim()
        if (hasUsableCache && cacheVersion == TEXT_ASSET_VERSION) return file.readText()
        return runCatching {
            fetchText().also {
                filesRoot.mkdirs()
                file.writeText(it)
                versionFile.writeText(TEXT_ASSET_VERSION)
            }
        }.getOrElse { error ->
            if (hasUsableCache) file.readText() else throw error
        }
    }

    fun loadOrDownloadTimings(): String {
        val file = File(filesRoot, "Alafasy_128kbps.json")
        return if (file.isFile && file.length() > 0L) file.readText() else fetchTimings().also { filesRoot.mkdirs(); file.writeText(it) }
    }

    fun downloadAyah(surah: Int, ayah: Int): File {
        audioRoot.mkdirs()
        val target = File(audioRoot, "${surah.toString().padStart(3, '0')}${ayah.toString().padStart(3, '0')}.mp3")
        if (target.isFile && target.length() > 0L) return target
        val partial = File(target.path + ".part")
        fetchToFile(audioUrl(surah, ayah), partial)
        if (!partial.renameTo(target)) error("Could not finalize ${target.name}")
        return target
    }

    private fun fetch(url: String): String {
        val connection = open(url)
        return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            .also { connection.disconnect() }
    }

    private fun fetchToFile(url: String, target: File) {
        val connection = open(url)
        try {
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "QuranMemorizationOnline/1.0")
        }
        if (connection.responseCode !in 200..299) {
            val code = connection.responseCode
            connection.disconnect()
            error("Asset request failed ($code): $url")
        }
        return connection
    }
}
