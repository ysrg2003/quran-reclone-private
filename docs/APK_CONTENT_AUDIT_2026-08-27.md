# APK content audit — 2026-08-27

## Artifacts

| variant | path | size | SHA-256 |
|---|---|---:|---|
| Online | `app/build/outputs/apk/online/debug/app-online-debug.apk` | 23,407,353 bytes | `d8780e203bfada7863685af6f331ab462a97ec6c18247134c31ba82b3e6bc121` |
| Offline | `app/build/outputs/apk/offline/debug/app-offline-debug.apk` | 1,466,812,771 bytes | `89423ce087441e8a528c0eac15ad3b9e242a3f4c02fdd5c3db4186ff3dabf0ef` |

## Permissions from merged APK manifests

```text
Online:  android.permission.INTERNET
         android.permission.RECORD_AUDIO
Offline: android.permission.RECORD_AUDIO
         no android.permission.INTERNET
```

Both APKs also contain the normal generated dynamic receiver permission for their application id. The Offline manifest removes `INTERNET` with `tools:node="remove"`.

## Payload scan

The scan used `unzip -l` and counted entries by path/suffix:

| check | Online | Offline | acceptance |
|---|---:|---:|---|
| `.m4a` entries | 0 | 6236 | Online must be 0; Offline must be 6236 |
| `.mp3` entries | 0 | 0 | both pass; MP3 source stays external |
| model path entries | 0 | 1 | Online 0; Offline contains model ZIP |
| sherpa/onnxruntime matching entries | 0 | 4 matching runtime entries among native payload | Online 0; Offline expected |

The Offline APK size is approximately 1.467 GB, consistent with 1,274,873,643 bytes of M4A, 125,393,105 bytes of model ZIP, and native/runtime plus application payload. An earlier 3.8 GB intermediate APK was rejected because it contained both 6236 MP3 and 6236 M4A entries; MP3 was moved to `../quran-husary-mp3-source/`, the build was cleaned, and this final artifact contains no MP3.

## Reproduction

```bash
AAPT=/home/ubuntu/tools/android-sdk/build-tools/35.0.0/aapt
ONLINE=app/build/outputs/apk/online/debug/app-online-debug.apk
OFFLINE=app/build/outputs/apk/offline/debug/app-offline-debug.apk
$AAPT dump permissions "$ONLINE"
$AAPT dump permissions "$OFFLINE"
unzip -l "$ONLINE" | grep -Ei 'm4a|mp3|fastconformer|sherpa|onnxruntime' || true
unzip -l "$OFFLINE" | grep -Ei 'm4a|mp3|fastconformer|sherpa|onnxruntime'
sha256sum "$ONLINE" "$OFFLINE"
```

## حدود الدليل

هذا يثبت محتوى APK والـmerged permissions وقت البناء فقط. لم يثبت تشغيل MediaPlayer، أو inference، أو SpeechRecognizer، أو غياب الشبكة على هاتف فعلي. حقوق إعادة توزيع Word/KFGQPC/Husary/QUL-derived timing ما زالت غير مثبتة؛ لذلك تبقى artifacts للاختبار الداخلي فقط.
