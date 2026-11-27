package com.example.quranoffline

import com.example.quranoffline.data.WordPackStore
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class WordPackTest {
    private fun store() = WordPackStore(Files.createTempDirectory("w").toFile()).also { it.ensureDir() }

    @Test fun localWordOnlyWhenCountAndIdentityMatch() {
        val st = store()
        st.file(112, 1, 2).writeBytes(ByteArray(10))
        st.writeIndex(112, listOf("n\t1\t4", "w\t1\t2\tاله\tallah"))
        assertNotNull(st.localWord(112, 1, 2, 4, "اله"))
        assertNotNull(st.localWord(112, 1, 2, 4, "allah"))      // uthmani form also accepted, like v11
        assertNull(st.localWord(112, 1, 2, 5, "اله"))           // word count differs
        assertNull(st.localWord(112, 1, 2, 4, "غير"))           // identity differs
        assertNull(st.localWord(112, 1, 3, 4, "اله"))           // file missing
        assertNull(st.localWord(112, 1, 2, 4, ""))
    }

    @Test fun partialFileIsNeverUsed() {
        val st = store()
        st.part(1, 1, 1).writeBytes(ByteArray(10)); st.writeIndex(1, listOf("n\t1\t1", "w\t1\t1\tx\ty"))
        assertNull(st.localWord(1, 1, 1, 1, "x"))
    }

    @Test fun countsBytesAndDeletesPerSurah() {
        val st = store()
        st.file(2, 1, 1).writeBytes(ByteArray(10)); st.file(2, 1, 2).writeBytes(ByteArray(20)); st.file(3, 1, 1).writeBytes(ByteArray(5))
        assertEquals(2, st.surahCount(2)); assertEquals(30L, st.surahBytes(2))
        st.deleteSurah(2); assertEquals(0, st.surahCount(2)); assertEquals(1, st.surahCount(3))
        st.deleteAll(); assertEquals(0L, st.usedBytes())
    }
}
