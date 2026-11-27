package com.example.quranoffline.data

/** JNI bridge to whisper.cpp (MIT), built for arm64-v8a only. Decode is greedy, language ar. */
object WhisperNative {
    val available: Boolean = try { System.loadLibrary("whisper_jni"); true } catch (_: Throwable) { false }
    external fun nativeInit(path: String): Long
    external fun nativeFree(handle: Long)
    external fun nativeTranscribe(handle: Long, pcm: FloatArray, threads: Int): String
}
