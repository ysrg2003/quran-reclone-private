# APK Content Audit — writing-mode contract — 2026-08-27

هذا التقرير يخص الـartifacts المبنية بعد إضافة وضع التسميع بالكتابة وتوحيد Android `SpeechRecognizer` في كلا الـflavors. لا يثبت اختبار هاتف حي أو صلاحية إعادة التوزيع.

## Artifacts

| variant | application id | path | size | SHA-256 |
|---|---|---|---:|---|
| Online | `com.example.quranoffline.online` | `app/build/outputs/apk/online/debug/app-online-debug.apk` | `23,413,744` bytes | `00da3ab68e5a5a8e0453a11da665a3eb6ecf72a8562a94318f2de5c82821573f` |
| Offline | `com.example.quranoffline.offline` | `app/build/outputs/apk/offline/debug/app-offline-debug.apk` | `1,300,250,950` bytes | `400e45219d9292e387dc575a9cd93c52994d780c84657cd7142d686fe70d4a2d` |

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
| `fastconformer`/`sherpa`/`onnxruntime` names | 0 | 0 |
| `third_party_licenses` entries | 7 | 7 |

النتيجة تطابق العقد المطلوب: Online يضمّن قواعد البيانات والـpreview والتوقيتات دون صوت؛ Offline يضمّن قواعد البيانات والـpreview والتوقيتات و6,236 ملف M4A لصوت الحصري. لا يضمّن أي flavor Sherpa أو FastConformer أو نموذج ASR محلي.

## Commands

```bash
AAPT=/home/ubuntu/tools/android-sdk/build-tools/35.0.0/aapt
$AAPT dump permissions app/build/outputs/apk/online/debug/app-online-debug.apk
$AAPT dump permissions app/build/outputs/apk/offline/debug/app-offline-debug.apk
unzip -Z1 app/build/outputs/apk/online/debug/app-online-debug.apk | grep -Ei 'm4a|mp3|fastconformer|sherpa|onnxruntime' || true
unzip -Z1 app/build/outputs/apk/offline/debug/app-offline-debug.apk | grep -Ei 'm4a|mp3|fastconformer|sherpa|onnxruntime' | awk 'BEGIN{m4a=mp3=local=0} /\.m4a$/{m4a++} /\.mp3$/{mp3++} /fastconformer|sherpa|onnxruntime/{local++} END{print m4a, mp3, local}'
sha256sum app/build/outputs/apk/online/debug/app-online-debug.apk app/build/outputs/apk/offline/debug/app-offline-debug.apk
```

## Limitations

هذا فحص محتوى وصلاحيات للـAPK فقط. لم يُنفّذ ADB أو اختبار SpeechRecognizer على هاتف، ولم يُختبر تشغيل M4A على جهاز المستخدم في هذه الجولة. كما أن حقوق Word وخط KFGQPC وتسجيل الحصري وتوقيتات QUL المشتقة ما زالت غير مثبتة؛ الحالة `internal testing only`.

## Source state

تم البناء بعد إضافة `RecitationInputMode.TYPING` و`submitTypedRecitation()`، وبعد إزالة اعتماد Sherpa وملفات النموذج من Gradle وsource sets. يجب تحديث هذا التقرير إذا تغيرت الشجرة أو أُعيد البناء من commit مختلف.
