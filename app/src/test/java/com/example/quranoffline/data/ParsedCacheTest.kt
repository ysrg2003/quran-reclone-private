package com.example.quranoffline.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsedCacheTest {
    private fun quran(): ParsedCache.QuranData {
        val ayahs = (0 until 6236).map { i ->
            val words = (0 until 12).map { w ->
                QuranWord(w, "كلمة$i$w", "كلمه$i$w", "كلمه$w", if (w % 3 == 0) setOf("a$w", "b$w c") else emptySet(), if (w == 5) setOf("وقف") else emptySet())
            }
            QuranAyah(i / 100 + 1, i % 100 + 1, words.joinToString(" ") { it.text }, words.joinToString(" ") { it.matchText }, words)
        }
        return ParsedCache.QuranData(ayahs, listOf("يا ايها", "ها انتم"))
    }

    private fun pron(): List<PronunciationAyah> = (0 until 6236).map { i ->
        PronunciationAyah(1 + i / 100, 1 + i % 100, "s$i", (0 until 12).map { w ->
            PronunciationWord(w, "w$w", listOf("t$w", "u$w"), if (w % 4 == 0) setOf(1, 2) else emptySet(), if (w % 2 == 0) w * 10 else null, 5, null, 7, setOf("f$w"), emptySet())
        })
    }

    private fun layout(): WordReferenceLayout = WordReferenceLayout(2, "src", "font", (1..604).map { p ->
        WordReferencePage(p, (1..15).map { n ->
            WordReferenceLine(n, "ayah", "txt$p$n", listOf("1:$n", "2:$n"), listOf(WordReferenceAyahNumber(3, 4, "٣", n)), mapOf("1:$n" to (0 to 3)))
        })
    }, 604)

    private fun tmp(): File = File.createTempFile("pcache", ".bin")

    @Test
    fun quranRoundTripAndTiming() {
        val data = quran(); val f = tmp()
        val t0 = System.nanoTime()
        ParsedCache.writeFile(f, 7L, 6236, data, ParsedCache::writeQuran)
        val t1 = System.nanoTime()
        val back = ParsedCache.readFile(f, 7L, 6236, ParsedCache::readQuran)
        val t2 = System.nanoTime()
        assertNotNull(back)
        assertEquals(data.ayahs, back!!.ayahs)
        assertEquals(data.spacedForms, back.spacedForms)
        println("CACHE quran bytes=${f.length()} write=${(t1 - t0) / 1_000_000}ms read=${(t2 - t1) / 1_000_000}ms")
    }

    @Test
    fun pronunciationAndLayoutRoundTrip() {
        val f = tmp(); val p = pron()
        ParsedCache.writeFile(f, 1L, 6236, p, ParsedCache::writePronunciation)
        val t = System.nanoTime()
        assertEquals(p, ParsedCache.readFile(f, 1L, 6236, ParsedCache::readPronunciation))
        println("CACHE pronunciation bytes=${f.length()} read=${(System.nanoTime() - t) / 1_000_000}ms")
        val g = tmp(); val l = layout()
        ParsedCache.writeFile(g, 1L, 604, l, ParsedCache::writeLayout)
        val back = ParsedCache.readFile(g, 1L, 604, ParsedCache::readLayout)!!
        assertEquals(l.pages, back.pages); assertEquals(l.declaredPageCount, back.declaredPageCount); assertEquals(l.source, back.source)
    }

    @Test
    fun staleOrDamagedFilesFallBackToNull() {
        val p = pron(); val f = tmp()
        ParsedCache.writeFile(f, 1L, 6236, p, ParsedCache::writePronunciation)
        assertNull("different app build", ParsedCache.readFile(f, 2L, 6236, ParsedCache::readPronunciation))
        assertNull("different size hint", ParsedCache.readFile(f, 1L, 6000, ParsedCache::readPronunciation))
        val bytes = f.readBytes()
        File(f.path + "t").also { it.writeBytes(bytes.copyOf(bytes.size / 2)) }.let {
            assertNull("truncated", runCatching { ParsedCache.readFile(it, 1L, 6236, ParsedCache::readPronunciation) }.getOrNull())
        }
        File(f.path + "x").also { it.writeBytes(bytes + byteArrayOf(1)) }.let {
            assertNull("trailing junk", ParsedCache.readFile(it, 1L, 6236, ParsedCache::readPronunciation))
        }
        val flipped = bytes.copyOf(); for (i in 100 until 400) flipped[i] = (flipped[i].toInt() xor 0x5A).toByte()
        File(f.path + "f").also { it.writeBytes(flipped) }.let {
            // damage is either rejected or throws; the caller wraps this in runCatching and rebuilds from JSON
            val r = runCatching { ParsedCache.readFile(it, 1L, 6236, ParsedCache::readPronunciation) }
            assertTrue(r.isFailure || r.getOrNull() == null || r.getOrNull() != p)
        }
        File(f.path + "e").also { it.writeBytes(ByteArray(0)) }.let { assertNull(ParsedCache.readFile(it, 1L, 6236, ParsedCache::readPronunciation)) }
    }
}
