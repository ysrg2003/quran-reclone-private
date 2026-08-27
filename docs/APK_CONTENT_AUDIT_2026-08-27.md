# APK Content Audit — quran-v1-writing-mode-fix — 2026-08-27

هذا التقرير يخص الـartifacts المبنية بعد إصلاح قبول الرسم القرآني في وضع الكتابة، وتحسين تباين بطاقة التسميع، وإصلاح cue الأول/الأخير الشاذ في مقاطع التصحيح. لا يثبت اختبار هاتف حي أو صلاحية إعادة التوزيع.

## Artifacts

| variant | application id | path | size | SHA-256 |
|---|---|---|---:|---|
| Online | `com.example.quranoffline.online` | `app/build/outputs/apk/online/debug/app-online-debug.apk` | `23,415,576` bytes | `d39c19f778ef6135591b4d3ee390e58f6b67e6137defc09466c11966181aab5e` |
| Offline | `com.example.quranoffline.offline` | `app/build/outputs/apk/offline/debug/app-offline-debug.apk` | `1,300,252,702` bytes | `94aa4c3432a62f6191f3f5ac97637c1b12f9e0e8bdb9dd39c084e788e88df88c` |

## Permissions

استُخدم `aapt` من Android Build Tools 35.0.0. كلا الـAPKين يعلنان:

- `android.permission.INTERNET` — مطلوب لـAndroid `SpeechRecognizer`، ولجلب صوت الحصري عند الحاجة في Online.
- `android.permission.RECORD_AUDIO` — مطلوب للتسميع الصوتي فقط.
- dynamic receiver permission المولّد من AndroidX.

وضع التسميع بالكتابة لا يحتاج أياً من `INTERNET` أو `RECORD_AUDIO` أثناء التشغيل.

## Asset boundary

| الفحص | Online | Offline |
|---|---:|---:|
| `.m4a` entries | 0 | 6,236 |
| `.mp3` entries | 0 | 0 |
| `fastconformer`/`sherpa`/`onnxruntime`/`onnx`/model names | 0 | 0 |
| `assets/third_party_licenses/` entries | 7 | 7 |

النتيجة تطابق العقد المطلوب: Online يضمّن قواعد البيانات والـpreview والتوقيتات دون صوت؛ Offline يضمّن قواعد البيانات والـpreview والتوقيتات و6,236 ملف M4A لصوت الحصري. لا يضمّن أي flavor Sherpa أو FastConformer أو ONNX Runtime أو نموذج ASR محلي.

## Commands

```bash
AAPT=/home/ubuntu/tools/android-sdk/build-tools/35.0.0/aapt
$AAPT dump permissions app/build/outputs/apk/online/debug/app-online-debug.apk
$AAPT dump permissions app/build/outputs/apk/offline/debug/app-offline-debug.apk
unzip -Z1 app/build/outputs/apk/online/debug/app-online-debug.apk | grep -Ei 'm4a|mp3|fastconformer|sherpa|onnx|model' || true
unzip -Z1 app/build/outputs/apk/offline/debug/app-offline-debug.apk | grep -Ei 'm4a|mp3|fastconformer|sherpa|onnx|model' || true
./scripts/verify_offline_apk.sh app/build/outputs/apk/offline/debug/app-offline-debug.apk
sha256sum app/build/outputs/apk/online/debug/app-online-debug.apk app/build/outputs/apk/offline/debug/app-offline-debug.apk
```

## Result

مرّ APK Offline عبر `verify_offline_apk.sh` بالنتيجة:

```text
offline-apk-ok: permissions=INTERNET,RECORD_AUDIO m4a=6236 local-asr=0
```

ويُظهر فحص `aapt` أن الصلاحيتين `INTERNET` و`RECORD_AUDIO` موجودتان في Online وOffline. أظهر فحص `unzip` أن Online بلا M4A أو MP3 أو ASR محلي، وأن Offline يحوي 6,236 M4A بلا MP3 أو أسماء FastConformer/Sherpa/ONNX/model.

## Limitations

هذا فحص محتوى وصلاحيات للـAPK فقط. لم يُنفّذ ADB أو اختبار SpeechRecognizer على هاتف، ولم يُختبر تشغيل M4A على جهاز المستخدم في هذه الجولة. كما أن حقوق Word وخط KFGQPC وتسجيل الحصري وتوقيتات QUL المشتقة ما زالت غير مثبتة؛ الحالة `internal testing only`.

## Source state

تم البناء بعد إضافة سياسة `allowCanonicalQuranSpelling` إلى وضع الكتابة فقط، ورفع تباين أزرار `OutlinedButton` في بطاقة التسميع، وإضافة edge-timing repair محدود إلى `TimingWordMapper`. حُذف نموذج `app/src/offline/assets/models/fastconformer-quran-ar-onnx-int8.zip` قبل إعادة بناء Offline من الحالة النظيفة. يجب تحديث هذا التقرير إذا تغيرت الشجرة أو أُعيد البناء من commit مختلف.
