package com.example.quranoffline

import com.example.quranoffline.data.EngineChoice
import com.example.quranoffline.data.EngineSelector
import com.example.quranoffline.data.StablePrefix
import com.example.quranoffline.data.WhisperModelStore
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class WhisperEngineTest {
    @Test fun systemRecognizerIsDefaultAndFallbackInEveryOddCase() {
        assertEquals(EngineChoice.SYSTEM, EngineSelector.select(EngineChoice.SYSTEM, true, true))
        assertEquals(EngineChoice.SYSTEM, EngineSelector.select(EngineChoice.WHISPER, false, true))
        assertEquals(EngineChoice.SYSTEM, EngineSelector.select(EngineChoice.WHISPER, true, false))
        assertEquals(EngineChoice.WHISPER, EngineSelector.select(EngineChoice.WHISPER, true, true))
    }

    @Test fun stablePrefixAcceptsOnlyAgreedWords() {
        val a = StablePrefix.words("بسم الله الرحمن")
        val b = StablePrefix.words("بسم الله الرحيم الرحمن")
        assertEquals(listOf("بسم", "الله"), StablePrefix.common(a, b))
        assertEquals(emptyList<String>(), StablePrefix.common(emptyList(), b))
    }

    @Test fun repeatedOpeningPhraseIsDropped() {
        val w = listOf("a", "b", "c", "a", "b", "c")
        assertEquals(listOf("a", "b", "c"), StablePrefix.dropLoop(w))
        assertEquals(listOf("a", "b", "c"), StablePrefix.dropLoop(listOf("a", "b", "c")))
    }

    @Test fun modelWithWrongHashIsRejectedAndDeleted() {
        val store = WhisperModelStore(Files.createTempDirectory("w").toFile())
        store.ensureDir()
        assertFalse(store.importFrom(ByteArray(1000).inputStream()))
        assertFalse(store.ready()); assertFalse(store.part().exists())
    }

    @Test fun sha256IsCorrect() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", WhisperModelStore.sha256("abc".byteInputStream()))
    }

    @Test fun deleteRemovesModelAndPartial() {
        val store = WhisperModelStore(Files.createTempDirectory("w").toFile())
        store.ensureDir(); store.part().writeBytes(ByteArray(5)); store.model().writeBytes(ByteArray(7))
        assertEquals(12L, store.usedBytes()); store.delete(); assertEquals(0L, store.usedBytes())
    }
}
