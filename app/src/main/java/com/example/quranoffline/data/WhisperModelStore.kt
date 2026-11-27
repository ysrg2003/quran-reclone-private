package com.example.quranoffline.data

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Private storage for the optional Whisper model (ggml q8_0 of tarteel-ai/whisper-base-ar-quran, Apache-2.0). */
class WhisperModelStore(private val dir: File) {
    fun model() = File(dir, FILE_NAME)
    fun part() = File(dir, "$FILE_NAME.part")
    fun ensureDir() { dir.mkdirs() }
    /** Ready only when the full file exists, has the pinned size and sha256 was verified at install time. */
    fun ready() = model().isFile && model().length() == SIZE
    fun delete() { model().delete(); part().delete() }
    fun usedBytes() = (if (model().isFile) model().length() else 0L) + (if (part().isFile) part().length() else 0L)

    /** Moves a finished .part into place only if its sha256 equals the pinned hash; otherwise deletes it. */
    fun verifyAndInstall(): Boolean {
        val p = part()
        if (!p.isFile || p.length() != SIZE || sha256(p.inputStream()) != SHA256) { p.delete(); return false }
        return p.renameTo(model())
    }

    /** Copies a user-picked file into .part (import fallback), then verifies. */
    fun importFrom(input: InputStream): Boolean {
        ensureDir(); part().delete()
        input.use { i -> part().outputStream().use { o -> i.copyTo(o) } }
        return verifyAndInstall()
    }

    companion object {
        const val FILE_NAME = "ggml-model-q8_0.bin"
        const val SIZE = 81_768_585L
        const val SHA256 = "72194195f7d280adebec57acf2c6e01e209484322ec1cec21c275a3c0b1e3d77"
        const val URL = "https://huggingface.co/sadrapp/whisper-base-ar-quran-ggml/resolve/main/ggml-model-q8_0.bin"
        fun sha256(input: InputStream): String {
            val md = MessageDigest.getInstance("SHA-256"); val buf = ByteArray(64 * 1024)
            input.use { while (true) { val n = it.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
