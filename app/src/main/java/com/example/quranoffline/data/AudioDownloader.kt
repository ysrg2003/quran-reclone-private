package com.example.quranoffline.data

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import org.json.JSONObject

/** Downloads one surah of ayah audio into [AudioPackStore]. Resumes partial files, honors 429/Retry-After. */
class AudioDownloader(
    private val store: AudioPackStore,
    private val networkAllowed: () -> Boolean,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val open: (String) -> HttpURLConnection = { (URI(it).toURL().openConnection() as HttpURLConnection) },
) {
    sealed class Result {
        data class Done(val files: Int, val bytes: Long) : Result()
        object Cancelled : Result()
        data class Failed(val reason: String) : Result()
    }

    /** @param cancelled polled between chunks; true means pause (the .part file is kept for resume). */
    fun downloadSurah(surah: Int, ayahCount: Int, cancelled: () -> Boolean, progress: (done: Int, total: Int) -> Unit): Result {
        require(surah in 1..114)
        store.ensureDir()
        val urls = try { listUrls(surah) } catch (e: Exception) { return Result.Failed("تعذر جلب قائمة الصوت: ${e.message}") }
        var bytes = 0L
        var done = 0
        for (a in 1..ayahCount) {
            if (cancelled()) return Result.Cancelled
            if (!networkAllowed()) return Result.Failed("الشبكة غير متاحة أو الوضع Wi-Fi فقط مفعّل")
            val url = urls[a] ?: return Result.Failed("لا يوجد رابط للآية $a")
            if (store.localAyah(surah, a) == null) {
                val r = fetch(url, store.part(surah, a), store.file(surah, a), cancelled)
                if (r is Result.Cancelled || r is Result.Failed) return r
            }
            bytes += store.file(surah, a).length(); done++; progress(done, ayahCount)
        }
        return Result.Done(done, bytes)
    }

    private fun listUrls(surah: Int): Map<Int, String> {
        val c = open("${QuranComAudio.API}/recitations/${QuranComAudio.AYAH_RECITATION_ID}/by_chapter/$surah?per_page=300")
        try {
            c.connectTimeout = 15000; c.readTimeout = 30000; c.setRequestProperty("Accept", "application/json")
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            val rows = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getJSONArray("audio_files")
            val out = HashMap<Int, String>()
            for (i in 0 until rows.length()) {
                val r = rows.getJSONObject(i)
                val key = r.getString("verse_key").split(":")
                require(key[0].toInt() == surah)
                out[key[1].toInt()] = QuranComAudio.media(r.getString("url"), QuranComAudio.API)
            }
            return out
        } finally { c.disconnect() }
    }

    internal fun fetch(url: String, part: File, dest: File, cancelled: () -> Boolean): Result {
        var attempts = 0
        while (true) {
            val have = if (part.isFile) part.length() else 0L
            val c = open(url)
            try {
                c.connectTimeout = 15000; c.readTimeout = 30000
                if (have > 0) c.setRequestProperty("Range", "bytes=$have-")
                val code = c.responseCode
                when {
                    code == 429 || code == 503 -> {
                        if (++attempts > 5) return Result.Failed("الخادم يطلب الانتظار (HTTP $code)")
                        val wait = (c.getHeaderField("Retry-After")?.trim()?.toLongOrNull() ?: (2L shl attempts)).coerceIn(1, 120)
                        sleeper(wait * 1000); continue
                    }
                    code == 416 -> { part.delete(); if (++attempts > 2) return Result.Failed("HTTP 416"); continue }
                    code != 200 && code != 206 -> return Result.Failed("HTTP $code")
                }
                val append = code == 206 && have > 0
                if (!append && part.exists()) part.delete()
                val expected = c.getHeaderField("Content-Length")?.toLongOrNull()?.let { if (append) it + have else it }
                FileOutputStream(part, append).use { out ->
                    c.inputStream.use { input ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            if (cancelled()) return Result.Cancelled
                            val n = input.read(buf); if (n < 0) break
                            out.write(buf, 0, n)
                        }
                    }
                }
                if (expected != null && part.length() != expected) {
                    if (++attempts > 3) return Result.Failed("حجم الملف غير مطابق")
                    continue
                }
                if (!part.renameTo(dest)) return Result.Failed("تعذر حفظ الملف")
                return Result.Done(1, dest.length())
            } catch (e: java.io.IOException) {
                if (++attempts > 3) return Result.Failed("انقطع الاتصال: ${e.message}")
            } finally { c.disconnect() }
        }
    }
}
