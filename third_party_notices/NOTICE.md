# Third-party notices

## Quran text

The embedded Quran text is derived from Tanzil Quran Text Version 1.1.

- Source: https://tanzil.net/
- License: Creative Commons Attribution 3.0
- Required notice: the text must be distributed verbatim; changing the text is not allowed. The source must be clearly indicated and a link to Tanzil must be provided so users can follow updates.
- License page: https://tanzil.net/docs/text_license

The application keeps the canonical ayah text in `app/src/main/assets/quran/quran_content.json` and uses a derived matching form separately. The canonical text is not rewritten.

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

## EveryAyah downloader

- Source: https://gist.github.com/abdalrohman/11da7e35b6006744275e2dfc2e918aa8
- Script license stated by the author: MIT
- Important: this license applies to the downloader script, not automatically to audio recordings served by EveryAyah or to a particular reciter's recording. Audio must be redistributed only when its source terms or permission allow it.

## quran.com-images

- Source: https://github.com/quran/quran.com-images
- Generator code: GPL as stated in the upstream README.
- Fonts and generated page resources: attributed by upstream to the King Fahd Quran Complex and subject to their terms. They must not be redistributed in an application until the applicable permission and attribution requirements are verified.

## sherpa-onnx

- Source: https://github.com/k2-fsa/sherpa-onnx
- The project and local AAR are used as the on-device ONNX runtime/JNI layer.
- AAR source used in this build: https://huggingface.co/csukuangfj2/sherpa-onnx-libs
- The exact AAR checksum is recorded in `docs/source-lock.md`. Keep its upstream license and notices with every distribution.

## Quranic ASR model

The project is prepared for an optional local model pack. The recommended candidate is:

- Model: https://huggingface.co/mohammed/fastconformer-quran-ar-onnx-int8
- License shown by the model card: CC-BY-4.0
- Runtime files: encoder, decoder, joiner, tokens, and metadata.
- Model-card status: on-device verification is pending, so application accuracy must be benchmarked on real Android hardware before making educational accuracy claims.

No model file is silently substituted by a cloud service. If the model pack is absent or fails validation, the app must report that local recognition is unavailable rather than using Android/Google online speech recognition.
