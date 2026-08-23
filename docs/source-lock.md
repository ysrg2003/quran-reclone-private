# Source lock

This file records the inputs used by the offline build. Update it whenever an upstream source changes, and regenerate derived assets instead of editing generated JSON by hand.

| Component | Locked input | Role | Runtime |
|---|---|---|---|
| Tanzil Quran text | Downloaded Quran Text Version 1.1, Uthmani, numbered text | Canonical ayah text | Bundled local asset |
| quran-align code | Local Git checkout at build time | Reference word timing preprocessing | Build machine only |
| quran-align data | `quran-align-data-2016-11-24.zip` release | Reference word timing data | Optional local pack |
| EveryAyah downloader | Local Git checkout of the supplied Gist | Build-time audio acquisition | Build machine only |
| quran.com-images | Local Git checkout of `quran/quran.com-images` | Optional page-image generation | Build machine only until rights verified |
| sherpa-onnx | AAR `1.13.6` from `csukuangfj2/sherpa-onnx-libs` | Local ONNX/JNI inference runtime | Android runtime |
| ASR model | `mohammed/fastconformer-quran-ar-onnx-int8` | Candidate local Quranic ASR | Optional model pack |

## Integrity commands

Run these commands after downloading or regenerating inputs:

```text
sha256sum app/libs/sherpa-onnx-1.13.6.aar
sha256sum build/quran-align-data/quran-align-data-2016-11-24.zip
sha256sum build-tanzil-uthmani-numbered.txt
sha256sum app/src/main/assets/quran/quran_content.json
```

## Offline guarantee

The Android module contains no `INTERNET` permission. Runtime inputs must resolve to `assets/`, `filesDir`, or a user-selected local document URI. The ASR engine must fail closed when a model file is missing; it must never fall back to Android SpeechRecognizer or another online provider.


## Runtime verification notes

The inspected `sherpa-onnx` source defines a separate `OnlineRecognizerTransducerNeMoImpl` path. Its runtime selection checks the decoder graph output count and selects the NeMo implementation when the decoder has more than one output; the checked model decoder exposes multiple outputs. The Android AAR contains `libsherpa-onnx-jni.so` for arm64-v8a, armeabi-v7a, x86, and x86_64, and the project compiles against its Kotlin API.

The FastConformer model metadata reports `vocab_size=1024`, `sample_rate=16000`, `feature_dim=80`, causal streaming attention, and the cache dimensions required by the NeMo streaming path. ONNX Runtime validation passed for all three graphs; actual device latency and recognition quality still require a physical Android device because the sandbox has no `/dev/kvm` for the emulator.
