package com.example.quranoffline

import com.example.quranoffline.data.AudioDownloader
import com.example.quranoffline.data.AudioPackStore
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AudioDownloaderTest {
    private lateinit var store: AudioPackStore
    private val payload = ByteArray(100_000) { (it % 251).toByte() }
    private var hits = 0
    private var rateLimitFirst = 0
    private val sleeps = mutableListOf<Long>()
    private val rangeSeen = mutableListOf<String?>()

    private inner class Fake : HttpURLConnection(URL("http://localhost/a.mp3")) {
        private var range: String? = null
        private var code = 0
        override fun setRequestProperty(k: String, v: String) { if (k == "Range") range = v }
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getResponseCode(): Int {
            if (code == 0) {
                hits++; rangeSeen += range
                code = if (hits <= rateLimitFirst) 429 else if (range != null) 206 else 200
            }
            return code
        }
        private fun from() = range?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
        override fun getHeaderField(name: String?): String? = when {
            name == "Retry-After" && code == 429 -> "3"
            name == "Content-Length" && code != 429 -> (payload.size - from()).toString()
            else -> null
        }
        override fun getInputStream(): InputStream = ByteArrayInputStream(payload, from(), payload.size - from())
    }

    @Before fun setUp() { store = AudioPackStore(Files.createTempDirectory("packs").toFile().also { it.mkdirs() }) }
    @After fun tearDown() { store.deleteAll() }

    private fun dl() = AudioDownloader(store, networkAllowed = { true }, sleeper = { sleeps += it }, open = { Fake() })
    private val url = "http://localhost/a.mp3"

    @Test fun downloadsCompleteFileAndOnlyThenCountsAsLocal() {
        assertNull(store.localAyah(1, 1))
        val r = dl().fetch(url, store.part(1, 1), store.file(1, 1)) { false }
        assertTrue(r is AudioDownloader.Result.Done)
        assertArrayEquals(payload, store.localAyah(1, 1)!!.readBytes())
        assertFalse(store.part(1, 1).exists())
    }

    @Test fun partialFileIsNotLocalAndResumesWithRange() {
        store.part(1, 2).writeBytes(payload.copyOf(40_000))
        assertNull(store.localAyah(1, 2))
        val r = dl().fetch(url, store.part(1, 2), store.file(1, 2)) { false }
        assertTrue(r is AudioDownloader.Result.Done)
        assertEquals("bytes=40000-", rangeSeen.first())
        assertArrayEquals(payload, store.file(1, 2).readBytes())
    }

    @Test fun honorsRetryAfterOn429() {
        rateLimitFirst = 2
        val r = dl().fetch(url, store.part(1, 3), store.file(1, 3)) { false }
        assertTrue(r is AudioDownloader.Result.Done)
        assertEquals(listOf(3000L, 3000L), sleeps)
    }

    @Test fun givesUpAfterRepeated429() {
        rateLimitFirst = 100
        val r = dl().fetch(url, store.part(1, 4), store.file(1, 4)) { false }
        assertTrue(r is AudioDownloader.Result.Failed)
        assertNull(store.localAyah(1, 4))
    }

    @Test fun cancelKeepsPartialForResume() {
        var n = 0
        val r = dl().fetch(url, store.part(1, 5), store.file(1, 5)) { ++n > 2 }
        assertTrue(r is AudioDownloader.Result.Cancelled)
        assertNull(store.localAyah(1, 5))
        assertTrue(store.part(1, 5).exists())
    }

    @Test fun deleteRemovesOnlyThatSurahAndCountsBytes() {
        store.file(2, 1).writeBytes(ByteArray(10)); store.file(2, 2).writeBytes(ByteArray(20)); store.file(3, 1).writeBytes(ByteArray(5))
        assertEquals(2, store.surahCount(2)); assertEquals(30L, store.surahBytes(2)); assertEquals(35L, store.usedBytes())
        store.deleteSurah(2)
        assertEquals(0, store.surahCount(2)); assertEquals(5L, store.usedBytes())
        store.deleteAll(); assertEquals(0L, store.usedBytes())
    }

    @Test fun manifestDeclaresNetworkStatePermission() {
        // v12/v14 crashed on Download: ConnectivityManager was read without this permission (SecurityException).
        val m = java.io.File("src/main/AndroidManifest.xml").readText()
        assertTrue(m.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertTrue(m.contains("android.permission.INTERNET"))
    }

    @Test fun downloadSurahNeverThrowsEvenIfConnectionBlowsUp() {
        val d = AudioDownloader(store, networkAllowed = { true }, sleeper = {}, open = { throw SecurityException("boom") })
        val r = d.downloadSurah(1, 7, { false }) { _, _ -> }
        assertTrue(r is AudioDownloader.Result.Failed)
    }

    @Test fun fetchTurnsUnexpectedExceptionIntoFailure() {
        val d = AudioDownloader(store, networkAllowed = { true }, sleeper = {}, open = { throw IllegalStateException("x") })
        assertTrue(d.fetch(url, store.part(1, 6), store.file(1, 6)) { false } is AudioDownloader.Result.Failed)
    }
}
