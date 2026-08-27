# Release notes — 2026-08-27

## حالة الإصدار

هذا إصدار تطوير واختبار داخلي لفرعين Android flavor مستقلين. لا يمثل clearance للنشر العام أو التجاري، لأن إذن إعادة توزيع Word وخط KFGQPC وتسجيل الحصري وبيانات QUL المشتقة لم يُثبت بعد بوثيقة قابلة للأرشفة.

## Online

يستخدم Online `Android SpeechRecognizer` فقط، ويبث MP3 آية الحصري عند الحاجة عبر HTTPS. النص العثماني، تخطيط Word، preview، metadata، حقول المقارنة، توقيتات Husary QUL، وإشعارات الحقوق داخل APK. لا يضم MP3 أو M4A أو FastConformer أو sherpa native.

```text
Path: app/build/outputs/apk/online/debug/app-online-debug.apk
Size: 24,208,929 bytes
SHA-256: 66f15285644fa51f1b18e9264e2e70700b450a2be8abb6bfefcf0b3b49291002
```

يتطلب Online `RECORD_AUDIO` و`INTERNET`. قد يعالج مزود Android الصوت خارج التطبيق. لم يُثبت هذا APK على هاتف في هذه الجولة.

## Offline

يستخدم Offline `sherpa-onnx` مع FastConformer المضمن، ويضم 6236 ملف M4A AAC-LC 64kbps مشتقة من ملفات MP3 نفسها التي يستخدمها Online. يحذف manifest Offline صلاحية `INTERNET`، ولا يعرض fallback شبكياً. يضم النص والتخطيط والتوقيتات والـmanifest والإشعارات.

```text
Path: app/build/outputs/apk/offline/debug/app-offline-debug.apk
Size: 1,467,618,203 bytes
SHA-256: f13075df246d222edff333591020f45821bfae3644e3e179b98aab0fcbeca528
```

يتطلب Offline `RECORD_AUDIO` فقط. حجم M4A المضمّن 1,274,873,643 bytes، وتوجد SHA-256 للمصدر والمشتق في `husary_aac_manifest.json`. لم يُثبت inference أو تشغيل MediaPlayer من هذا APK على هاتف 64-bit في هذه الجولة.

## تحسينات مشتركة

يعرض التطبيق preview للصفحات 1–5 قبل تحليل الأصول الكاملة في الخلفية. تحافظ المقارنة على `providerText` الخام القادم من recognizer، ويحافظ العرض على Word canonical. يستعمل تصحيح الكلمة نافذة QUL مع هامش من الصمت لا يتجاوز 120ms لكل طرف. أضيفت إشعارات قانونية وفهارس التراخيص والبحث إلى شاشة داخل التطبيق.

## التحقق

نجحت المدققات التالية:

```text
python3 scripts/verify_word_text_unchanged.py
python3 scripts/verify_word_audio_timings.py
python3 scripts/verify_word_reference_preview.py
python3 scripts/verify_offline_audio_bundle.py
gradle :app:testOnlineDebugUnitTest :app:testOfflineDebugUnitTest :app:assembleOnlineDebug :app:assembleOfflineDebug :app:lintOnlineDebug :app:lintOfflineDebug --offline --no-daemon --max-workers=1
git diff --check
```

نجح فحص APK في Online بعدم وجود MP3/M4A أو model أو sherpa، ونجح فحص Offline بوجود 6236 M4A والنموذج وsherpa وعدم وجود `android.permission.INTERNET`. لا تشمل هذه النتائج اختبار الهاتف الحي أو إثبات حقوق النشر.

## التراجع

يمكن الرجوع إلى commit الإصدار السابق في سجل Git. لا تحذف `husary_aac_manifest.json` أو ملفات Word أو إشعارات الحقوق أثناء التراجع؛ فهي جزء من provenance والتحقق. عند تغير صوت المصدر يجب إعادة بناء M4A وmanifest والتوقيتات وفقاً لذلك وعدم خلط تسجيلات قارئين.
