# Third-party notices

## Quran text

The application canonical Quran text is derived from the user-supplied `UthmanicHafs_V20.doc` Word reference and preserved in `app/src/main/assets/quran/word_reference_ayahs.json`. The Word file and its KFGQPC rendering terms must remain the governing source for redistribution. Tanzil metadata is used only for structural organization; it is not the canonical text or comparison source.

- Tanzil metadata source: https://tanzil.net/
- Tanzil metadata license: Creative Commons Attribution 3.0 as declared by the source
- Word source: `reference-input/UthmanicHafs_V20.doc`
- Important: derived comparison fields must not rewrite or replace the canonical Word `text`.

The application does not use `quran_content.json` as the runtime canonical text.

## Quran metadata

The Arabic Mushaf-organization metadata in `app/src/main/assets/quran/quran-meta-data.xml` is the supplied Tanzil metadata format, version 1.0, and is distributed under Creative Commons Attribution. The app uses only the Arabic structural fields (`name`, `index`, `ayas`, `start`, `type`, `order`, `rukus`, and Arabic-coordinate boundaries); the English transliteration and translation fields are intentionally ignored in the current Arabic-focused interface.

- Source: https://tanzil.net/
- License: Creative Commons Attribution 3.0 as declared by the metadata source
- The metadata is used for organization and display only; it does not replace the canonical Quran text, audio timings, or recitation judgment.

## quran-align

- Source: https://github.com/cpfair/quran-align
- Code license: MIT
- Timing-data license in the upstream release: Creative Commons Attribution 4.0 International
- The aligner is used as a build-time preprocessing tool. Its Unix/CMU Sphinx dependencies are not part of the Android runtime.

## EveryAyah (historical build reference)

- The public downloader gist consulted during earlier build work is https://gist.github.com/abdalrohman/11da7e35b6006744275e2dfc2e918aa8 and declares its own script as MIT.
- The local `scripts/download_full_everyayah_audio.sh` is a separate project build helper and is not shipped in the APK; it must not be presented as the gist author’s code without a provenance comparison.
- EveryAyah’s public directory exposes recitation files, but the directory page inspected did not provide a general redistribution license for all recordings. The gist/script license applies to downloader code only, not to audio recordings or a particular reciter. Audio must be redistributed only when its source terms or permission allow it.

## quran.com-images

- Source: https://github.com/quran/quran.com-images
- Generator code: GPL as stated in the upstream README.
- Fonts and generated page resources: attributed by upstream to the King Fahd Quran Complex and subject to their terms. They must not be redistributed in an application until the applicable permission and attribution requirements are verified.
- In this project, the repository is used only as an external, build-time comparison reference for page/line/ayah structure. Its pages, fonts, glyph data, and generated resources are not embedded in the APK. The extracted structural report is not a substitute for a license review.

## Quran-PNG comparison reference

- Source: https://github.com/GovarJabbar/Quran-PNG
- The GitHub repository metadata inspected for the reference did not declare a license (`license: null`), and no clear LICENSE file was found in the inspected clone.
- The local clone is used only for deterministic validation of page filenames, dimensions, and limited raster signals. The `001.png`–`604.png` files are not copied into the Android project and are not redistributed by this application. No permission to redistribute them should be inferred.

## ASR runtime boundary

This release does **not** bundle Sherpa-ONNX, ONNX Runtime, FastConformer, or any other local ASR model. Both flavors use Android `SpeechRecognizer` for voice recitation; the system/provider may process recognition audio outside the application and the voice-recitation path therefore requires Internet access according to the installed provider. The writing-recitation path needs neither microphone nor network.

## Mahmoud Khalil Al-Husary audio and QUL segments

The default reference reader is Mahmoud Khalil Al-Husary, Hafs, Murattal. The source resource is:

- QUL resource: https://qul.tarteel.ai/resources/recitation/112
- QUL segment documentation: https://qul.tarteel.ai/docs/with-segments
- Build-time endpoint used to obtain the raw ayah segments: `https://qul.tarteel.ai/api/v1/audio/ayah_segments/20?surah={surah}&from=1&per_page=1000`
- Ayah audio pattern used by the app: `https://audio-cdn.tarteel.ai/quran/husary/{surah:03d}{ayah:03d}.mp3`

The `online` flavor embeds the generated timing index and streams the MP3 at runtime. The `offline` flavor embeds 6,236 AAC-LC/M4A derivatives at 64 kbps, generated from the same source MP3s; the source MP3s are retained outside the APK and recorded in `husary_manifest.json`, while `husary_aac_manifest.json` records source and derived SHA-256 values. The generator rejects merged segments and missing canonical words rather than silently splitting them or assigning a neighbouring word. QUL segments are machine-generated alignment data and must not be described as human-reviewed phoneme boundaries for every millisecond. The QUL FAQ and download guide say that resource-specific licensing must be checked; no clear license for this recording was displayed on the resource page inspected. Embedding M4A derivatives in Offline is redistribution, not merely streaming. Review and obtain permission for the recording, derived timing data, CDN use, transcoding, and Offline embedding before public or commercial redistribution.

Independent public search results do not cure this gap. QuranHub publishes a separate Husary audio repository under a GPL-3.0 repository license, but its raw LICENSE contains no recording-rights chain or audio-specific grant. QuranCentral, Internet Archive, Assabile, and Quran.com expose Husary listening/download pages, but the pages inspected do not grant this application permission to embed, cache, rehost, or commercially redistribute the exact recording. The research record is in `docs/LEGAL_INDEPENDENT_SEARCH_ADDENDUM_2026-08-27.md` and `docs/LEGAL_INDEPENDENT_SEARCH_NOTES_2026-08-27.md`.

## Runtime libraries and build dependencies

The application uses AndroidX Core KTX 1.15.0, Activity Compose 1.10.0, Lifecycle Runtime KTX 2.8.7, Compose BOM 2024.12.01 with Compose UI 1.7.6 and Material3, DataStore Preferences 1.1.1, Kotlin 2.0.21, and JUnit 4.13.2 for tests. AndroidX, Kotlin, and Compose components are Apache-2.0; JUnit 4.13.2 is distributed under the Eclipse Public License 1.0. The Android SDK and Android system APIs are build/runtime platform components and are not redistributed as application assets.

## Licensing status and release gate

This project is cleared for internal development and testing only. It is **not cleared for public or commercial release** until the project owner has a written, archivable permission or applicable EULA for the supplied KFGQPC font and Word reference, and resource-specific redistribution terms or permission for the Mahmoud Khalil Al-Husary audio and the derived Husary QUL timing index. A QUL repository MIT license, a QUL FAQ statement that commercial use may be possible subject to resource-specific licenses, or a working CDN URL is not by itself a grant to redistribute a recording.

Quran-PNG and quran.com-images remain comparison references only. Their images, fonts, pages, glyph bounds, and generated resources are not bundled in the APK. The Quran.com-images README separately states that its code is GPL while the fonts and pages belong to King Fahd Quran Complex; the GPL statement must not be treated as a license for those assets.

## Privacy and permission disclosure

Both `online` and `offline` flavors require `INTERNET` for Android SpeechRecognizer and require `RECORD_AUDIO` only for voice recitation. The `offline` flavor keeps the Quran databases, QUL timing index, and 6,236 Husary M4A files inside the APK, so Quran display and reference-audio playback do not need the network. The writing-recitation mode needs neither permission. Android SpeechRecognizer is a system/provider service and may process audio outside the application; the app must disclose this and must not claim that either flavor is fully offline or fully private. The application does not request location, contacts, camera, phone, storage, notification, or background-service permissions in its manifest.

`android:allowBackup="true"` permits Android backup behavior for application data according to the device/OS backup policy. Before public release, decide whether checkpoints, session history, and correction history may be included in backup, and document or configure this choice.

## Attribution and license files

The repository keeps the detailed research record in `docs/LEGAL_LICENSE_RESEARCH_2026-08-27.md`, plus the independent-search addendum and notes named above. Before distributing an APK, include readable copies of the applicable MIT, Apache-2.0, CC BY 3.0, CC BY 4.0, EPL-1.0, and source-specific notices in the distribution or in an accessible in-app legal-notices screen. Links alone do not replace a license condition that requires retaining the license text.

The startup path now reads a small Word-derived preview for pages 1–5, shows the first page, and performs full canonical-text, layout, pronunciation, and timing validation in a background coroutine. The preview is an exact subset of the canonical Word asset and is not a second source. Word correction clips use a gap-aware guard of at most 120 ms per side, clipped to measured silence so the automatic or user-selected padding cannot cross a neighbouring word segment.
