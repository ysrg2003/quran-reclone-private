package com.example.quranoffline.data

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Resolves streamed Online audio and bundled Offline audio. */
class RemoteAssetManager(context: Context) {
    private val appContext = context.applicationContext
    private val audioRoot = File(appContext.filesDir, "quran-assets/audio/Husary_Murattal")
    private val bundledAudioAssetRoot = "quran/audio/husary_aac"

    companion object {
        /** QUL ayah-by-ayah Murattal Hafs recitation by Mahmoud Khalil Al-Husary. */
        const val AYAH_AUDIO_BASE_URL = "https://audio-cdn.tarteel.ai/quran/husary"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 120_000

        fun audioFileName(surah: Int, ayah: Int): String {
            require(surah in 1..114) { "invalid surah: $surah" }
            require(ayah > 0) { "invalid ayah: $ayah" }
            return "${surah.toString().padStart(3, '0')}${ayah.toString().padStart(3, '0')}.mp3"
        }

        fun audioUrlFor(surah: Int, ayah: Int): String =
            "$AYAH_AUDIO_BASE_URL/${audioFileName(surah, ayah)}"
    }

    fun audioUrl(surah: Int, ayah: Int): String = audioUrlFor(surah, ayah)

    fun cachedAudioFile(surah: Int, ayah: Int): File? {
        val file = File(audioRoot, audioFileName(surah, ayah))
        return file.takeIf { it.isFile && it.length() > 0L }
    }

    fun isSurahDownloaded(surah: Int, ayahCount: Int): Boolean =
        (1..ayahCount).all { cachedAudioFile(surah, it) != null }

    fun downloadedAyahCount(surah: Int, ayahCount: Int): Int =
        (1..ayahCount).count { cachedAudioFile(surah, it) != null }

    /** Copies one bundled Offline AAC/M4A file to private storage so MediaPlayer can seek it. */
    fun materializeBundledAyah(surah: Int, ayah: Int): File? {
        val target = File(audioRoot, bundledAudioFileName(surah, ayah))
        if (target.isFile && target.length() > 0L) return target
        val assetPath = bundledAudioAssetPath(surah, ayah)
        return runCatching {
            audioRoot.mkdirs()
            val partial = File(target.path + ".part")
            appContext.assets.open(assetPath).use { input ->
                partial.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
            }
            check(partial.renameTo(target)) { "Could not finalize bundled ${target.name}" }
            target
        }.getOrNull()
    }

    private fun bundledAudioFileName(surah: Int, ayah: Int): String =
        audioFileName(surah, ayah).removeSuffix(".mp3") + ".m4a"

    fun bundledAudioAssetPath(surah: Int, ayah: Int): String =
        "$bundledAudioAssetRoot/${bundledAudioFileName(surah, ayah)}"

    fun isBundledOfflineAudioAvailable(surah: Int, ayah: Int): Boolean = runCatching {
        appContext.assets.open(bundledAudioAssetPath(surah, ayah)).use { }
    }.isSuccess

    fun deleteSurah(surah: Int, ayahCount: Int) {
        (1..ayahCount).forEach { ayah -> cachedAudioFile(surah, ayah)?.delete() }
    }

    fun downloadAyah(surah: Int, ayah: Int): File {
        audioRoot.mkdirs()
        val target = File(audioRoot, audioFileName(surah, ayah))
        if (target.isFile && target.length() > 0L) return target
        val partial = File(target.path + ".part")
        fetchToFile(audioUrl(surah, ayah), partial)
        if (!partial.renameTo(target)) error("Could not finalize ${target.name}")
        return target
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
            setRequestProperty("User-Agent", "QuranMemorizationHusary/1.0")
        }
        if (connection.responseCode !in 200..299) {
            val code = connection.responseCode
            connection.disconnect()
            error("Asset request failed ($code): $url")
        }
        return connection
    }
}
