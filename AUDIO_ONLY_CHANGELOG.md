# Private original-app audio swap

Baseline: 9c32b7b36524c8a4f18ca400c8601bdf730f5ebf, original repository main, checked November25,2026.

The original screens, text, matching, correction policy and default Android speech session remain unchanged. Only audio sourcing/playback changed. The test uses a separate application ID so it installs beside the original, without importing its saved progress.

- Ayah metadata is requested from Quran.com v4 recitation6, catalog name Mahmoud Khalil Al-Husary.
- Word metadata comes from the exact verse's words. Position, word count and normalized word text must match the original word before a separate API word recording is played. Different segmentation stops rather than guessing.
- New API files play in full. Old recording timestamps are not used to cut the new ayah recording or its independent word file.
- Downloaded ayah audio uses a separate private directory, so old Tarteel recordings are not mistaken for API recordings.
- No gateway/client secrets, private backend, on-device-only recognizer gate, new Quran storage/index, new UI, or new tasmee system is included.

Limits: network required for API metadata and streaming; speech uses the original Android default provider and may send speech to that provider. Starting a request does not prove Arabic recognition accuracy on a physical phone. Word audio may use a different voice from Husary ayah audio. Original matching and non-audio behavior are retained, including any pre-existing limits. This is a private test, not publication certification.

Verification: 128 local JVM tests pass, including the125 original tests and3 audio URL/reference tests. Device playback/start run is tracked separately. No Quran recording is supplied to recognition for evaluation.
