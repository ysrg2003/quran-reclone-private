package com.example.quranoffline.data

/** A live speech-to-text source that reports through RecognitionUpdate. */
interface SpeechEngine {
    fun start()
    fun stop()
}

enum class EngineChoice { SYSTEM, WHISPER }

/** Pure engine decision so it can be unit tested. Anything unusual means the system recognizer (v11 behavior). */
object EngineSelector {
    fun select(choice: EngineChoice, arm64: Boolean, modelReady: Boolean): EngineChoice =
        if (choice == EngineChoice.WHISPER && arm64 && modelReady) EngineChoice.WHISPER else EngineChoice.SYSTEM
}

/** Words accepted only when two consecutive decodes agree, so a wrong guess never reaches the strict matcher. */
object StablePrefix {
    fun words(text: String) = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    fun common(previous: List<String>, current: List<String>): List<String> {
        val n = minOf(previous.size, current.size)
        var i = 0
        while (i < n && previous[i] == current[i]) i++
        return current.subList(0, i)
    }
    /** Collapses an immediately repeated run (the model loops its opening phrase on long audio). */
    fun dropLoop(words: List<String>): List<String> {
        for (len in 2..minOf(6, words.size / 2)) {
            val tail = words.takeLast(len); val before = words.dropLast(len).takeLast(len)
            if (tail == before) return words.dropLast(len)
        }
        return words
    }
}
