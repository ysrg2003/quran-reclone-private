#!/usr/bin/env bash
set -euo pipefail

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
if [[ ! -f "$APK" ]]; then
  echo "APK not found: $APK" >&2
  exit 1
fi

permissions="$(aapt dump permissions "$APK")"
if grep -q "android.permission.INTERNET" <<<"$permissions"; then
  echo "FAIL: INTERNET permission found" >&2
  exit 1
fi
grep -q "android.permission.RECORD_AUDIO" <<<"$permissions"
unzip -tq "$APK" >/dev/null
listing="$(mktemp)"
trap 'rm -f "$listing"' EXIT
unzip -l "$APK" >"$listing"
for asset in \
  assets/quran/quran_content.json \
  assets/models/fastconformer-quran-ar-onnx-int8/encoder.int8.onnx \
  assets/models/fastconformer-quran-ar-onnx-int8/decoder.int8.onnx \
  assets/models/fastconformer-quran-ar-onnx-int8/joiner.int8.onnx \
  assets/models/fastconformer-quran-ar-onnx-int8/tokens.txt \
  assets/timings/Alafasy_128kbps.json \
  assets/audio/Alafasy_128kbps/001001.mp3 \
  assets/audio/Alafasy_128kbps/001002.mp3 \
  assets/audio/Alafasy_128kbps/001003.mp3; do
  grep -q "$asset" "$listing"
done

echo "offline-apk-ok"
