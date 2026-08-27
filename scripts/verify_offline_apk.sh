#!/usr/bin/env bash
set -euo pipefail

APK="${1:-app/build/outputs/apk/offline/debug/app-offline-debug.apk}"
AAPT="${AAPT:-/home/ubuntu/tools/android-sdk/build-tools/35.0.0/aapt}"

if [[ ! -f "$APK" ]]; then
  echo "APK not found: $APK" >&2
  exit 1
fi
if [[ ! -x "$AAPT" ]]; then
  echo "aapt not found or not executable: $AAPT" >&2
  exit 1
fi

permissions="$($AAPT dump permissions "$APK")"
grep -q "android.permission.INTERNET" <<<"$permissions"
grep -q "android.permission.RECORD_AUDIO" <<<"$permissions"

unzip -tq "$APK" >/dev/null
listing="$(mktemp)"
trap 'rm -f "$listing"' EXIT
unzip -Z1 "$APK" >"$listing"

for asset in \
  assets/quran/word_reference_ayahs.json \
  assets/quran/word_reference_layout.json \
  assets/quran/word_reference_preview.json \
  assets/quran/word_reference_preview_layout.json \
  assets/quran/pronunciation_reference.json \
  assets/quran/quran-meta-data.xml \
  assets/quran/audio_timing/Husary_Murattal.json \
  assets/quran/audio/husary_manifest.json \
  assets/quran/audio/husary_aac_manifest.json \
  assets/quran/audio/husary_aac/001001.m4a \
  assets/quran/audio/husary_aac/114006.m4a; do
  grep -Fxq "$asset" "$listing"
done

m4a_count="$(grep -Ec '^assets/quran/audio/husary_aac/[0-9]{6}\.m4a$' "$listing")"
[[ "$m4a_count" -eq 6236 ]]

if grep -Eiq '\.(mp3|onnx|aar)$|fastconformer|sherpa|onnxruntime|assets/models/' "$listing"; then
  echo "FAIL: excluded audio/model/local-ASR content found" >&2
  exit 1
fi

echo "offline-apk-ok: permissions=INTERNET,RECORD_AUDIO m4a=$m4a_count local-asr=0"
