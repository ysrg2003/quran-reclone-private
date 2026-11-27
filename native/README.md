# libwhisper_jni.so recipe (arm64-v8a only)

What it is for: the app's optional offline speech engine (Whisper). `WhisperNative.kt` loads `libwhisper_jni.so` and calls three functions (init from a model file, free, transcribe a float PCM array, greedy decode, language `ar`, no GPU). Without the library the app falls back to Android SpeechRecognizer. The model file is NOT in the app: it is downloaded on request, pinned by size and sha256 in `WhisperModelStore.kt` (`ggml-model-q8_0.bin` from the Hugging Face repo `sadrapp/whisper-base-ar-quran-ggml`, a third-party ggml conversion of `tarteel-ai/whisper-base-ar-quran`).

## Files
- `whisper_jni.cpp`: the JNI wrapper, recovered verbatim from the build log of 2026-11-27 00:48.
- `CMakeLists.txt`: recovered verbatim (static whisper.cpp, shared `whisper_jni`, `-Wl,--gc-sections`, 16 KB page size).
- `build.sh`: rebuilds it. Recipe: NDK r26d, ABI arm64-v8a, android-26, Release, `GGML_OPENMP=OFF`, whisper.cpp from `https://github.com/ggml-org/whisper.cpp`, then `llvm-strip`.

## What is proven and what is reconstructed
- Proven from the logs: NDK r26d, flags above, JNI source, CMakeLists, strip step. The committed binary is the stripped output of that build (taken from the v20 APK).
- Reconstructed: the exact whisper.cpp commit was not logged (shallow clone of master on 2026-11-26 evening). The latest master commit before that build is `79b11911d9d38c4be2fc7dc8b4efeff3324c090f` (2026-11-26 11:14 +0100), so that is what `build.sh` is pinned to in CI.
- Rebuild check done on 2026-11-27 with that commit: same file size (3,020,584 bytes), same 3,817 dynamic symbols (identical list), same dependencies (liblog, libm, libdl, libc). NOT byte-identical (about 14.7 KB of 3.0 MB differ, so a different build id). So the rebuild is equivalent in interface and size but unproven bit-for-bit. The committed binary was therefore not replaced.
