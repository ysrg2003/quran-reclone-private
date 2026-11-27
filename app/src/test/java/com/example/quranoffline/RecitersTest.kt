package com.example.quranoffline

import com.example.quranoffline.data.AudioPackStore
import com.example.quranoffline.data.Reciters
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class RecitersTest {
    @Test fun twelveUniqueRecitersAndDefaultIsV11Husary() {
        assertEquals(12, Reciters.all.size)
        assertEquals(12, Reciters.all.map { it.id }.toSet().size)
        assertEquals((1..12).toSet(), Reciters.all.map { it.id }.toSet())
        assertEquals(6, Reciters.DEFAULT_ID)
        assertEquals(6, Reciters.byId(999).id)
    }

    @Test fun reciterFilesAreSeparateAndDeleteIsPerReciter() {
        val root = Files.createTempDirectory("r").toFile()
        val a = AudioPackStore(java.io.File(root, "r7")); val b = AudioPackStore(java.io.File(root, "r3"))
        a.ensureDir(); b.ensureDir()
        a.file(1, 1).writeBytes(ByteArray(10)); b.file(1, 1).writeBytes(ByteArray(20))
        assertEquals(10L, a.usedBytes()); assertEquals(20L, b.usedBytes())
        a.deleteAll()
        assertNull(a.localAyah(1, 1)); assertNotNull(b.localAyah(1, 1))
    }
}
