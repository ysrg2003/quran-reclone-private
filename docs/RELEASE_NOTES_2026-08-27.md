# Release notes — 2026-08-27 — post-fix candidate

## حالة الإصدار

هذا مرشح تطوير واختبار داخلي بعد نقطة `quran V1`. لا يمثل clearance للنشر العام أو التجاري. ما زال إذن إعادة توزيع Word وخط KFGQPC وتسجيل محمود خليل الحصري وبيانات توقيت QUL المشتقة يحتاج وثيقة صريحة قابلة للأرشفة تغطي التضمين داخل Android وإعادة التوزيع والبث والتخزين والاستخدام التجاري عند الحاجة.

تم تشغيل assemble وlint النهائيين لهذه الجولة، وأُدرجت أدناه بصمات artifact الناتجة فعلياً. هذه البصمات لا تثبت حقوق إعادة التوزيع أو اختبار الهاتف.

## الإصلاحات

### البسملة الافتتاحية

أُعيد إدخال `OpeningBasmalaProgressTracker` المفقود من clone المستعاد. البسملة الافتتاحية عنصر مستقل وليست آية ضمن `allAyahs`، باستثناء أن الفاتحة نفسها تبدأ بصف Word 1:1، ولا توجد بسملة مستقلة للتوبة. يعتمد tracker على التقدم المتسلسل، لذلك فإن `بسم الله الرحيم` يجعل `الرحمان` `MISSING` ويبقي `الرحيم` `UNCERTAIN` ولا يسمح بتسريب الكلمة اللاحقة إلى الآية. يغطي ذلك `OpeningBasmalaProgressTrackerTest`.

### بداية ونهاية الصوت

كانت قراءة الآية الكاملة تُبنى من `first timing start`، وهو قد يتأخر عن بداية الملف. أصبحت queue الخاصة بالآية والسورة والقرآن والبسملة ترسل `startMs=0`. بقي قص كلمة التصحيح مرتبطاً بنافذة QUL مع padding gap-aware لا يتجاوز 120ms داخل الصمت المتاح.

بعد prepare، يقيد `ReferenceAudioPlayer` حدود التشغيل بمدة `MediaPlayer` الفعلية. هذا لا يغيّر QUL JSON؛ إنما يمنع نهاية cue integer من تجاوز مدة MP3/M4A أو جدولة stop غير صالح.

### مصدر طبقة النطق

أصبح `PronunciationReference.load()` يقرأ `quran/pronunciation_reference.json` التفصيلي الذي يحتوي `spokenTokens` و`maddPositions` وحقول ربط الصوت، بدلاً من إعادة قراءة Word JSON المبسط. يظل observed text القادم من SpeechRecognizer خاماً في `RecognitionUpdate.value/providerText` ولا يستبدل بالنص canonical قبل الحكم.

### البطء والتعليق

ظل preview للصفحات 1–5 أول واجهة. بعده تُحمّل allAyahs وmetadata وlayout وpronunciation وtiming، والنموذج Offline عند الحاجة، في مهام IO مستقلة، ثم تُجرى validation قبل نشر الحالة الكاملة. أزيل `flatMap` المتكرر لكل كلمات المصحف عبر `totalWordCount`، ونُقلت خرائط ayah/index/word-offset إلى مستوى سطح الصفحة بدلاً من بنائها لكل سطر Compose.

أُعيدت `OnlineSpeechSession` إلى contract النسخة العاملة السابقة: partial لا ينهي الجلسة، final يُراكم، وأخطاء provider العابرة مثل `ERROR_NO_MATCH` تعيد فتح segment بدلاً من إنهاء الميكروفون. لم تُجرَ تغييرات خاصة جديدة على lifecycle أو extras خارج هذا contract المستعاد.

## Online

| العنصر | القيمة |
|---|---|
| flavor | `online` |
| ASR | Android `SpeechRecognizer` |
| الصوت | HTTPS عند الحاجة، ولا يُحزم MP3/M4A |
| model/sherpa | غير موجودين في Online variant |
| permissions المتوقعة | `INTERNET` و`RECORD_AUDIO` |
| artifact | `app/build/outputs/apk/online/debug/app-online-debug.apk` — 23,407,353 bytes — SHA-256 `d8780e203bfada7863685af6f331ab462a97ec6c18247134c31ba82b3e6bc121` |

## Offline

| العنصر | القيمة |
|---|---|
| flavor | `offline` |
| ASR | `LocalRecitationSession` عبر sherpa/FastConformer |
| الصوت | 6236 M4A AAC-LC مشتقة محلياً من MP3 المصدر، خارج Git history |
| model | داخل APK ويُستخرج إلى `filesDir` |
| permissions المتوقعة | `RECORD_AUDIO` فقط؛ Offline manifest يزيل `INTERNET` |
| artifact | `app/build/outputs/apk/offline/debug/app-offline-debug.apk` — 1,466,812,771 bytes — SHA-256 `89423ce087441e8a528c0eac15ad3b9e242a3f4c02fdd5c3db4186ff3dabf0ef` |

حجم أصول M4A الحالي `1,274,873,643` bytes وmanifest hash/size هو `app/src/offline/assets/quran/audio/husary_aac_manifest.json`. حجم ZIP نموذج FastConformer هو `125,393,105` bytes، وبصمته `d87fa3c28bbd52f2ca0890a42ba7a39d242ef98977d5b9dd4ddd80ed005f7b1b`. وجود الملفات محلياً لا يعني أن حق إعادة توزيع التسجيل مثبت.

## التحقق المنفذ حتى الآن

| البوابة | النتيجة |
|---|---|
| `:app:testOnlineDebugUnitTest` | نجحت بعد إصلاح البسملة والصوت والأداء |
| `:app:testOfflineDebugUnitTest` | نجحت بعد الإصلاحات نفسها |
| تدقيق container/timing لـ6236 آية | أُنجز؛ التقرير الكامل في `docs/HUSARY_AUDIO_BOUNDARY_AUDIT_2026-08-27.*` |
| ASR عينة 1:1 و2:15 | أُنجز؛ الدليل في `docs/HUSARY_AUDIO_SAMPLE_ASR_2026-08-27.md` |
| AI_CONTEXT validator | نجح بلا أخطاء |
| اختبار الهاتف/ADB/MediaPlayer حي | **deferred**؛ لم ينفذ في هذه الجولة |
| assemble/lint النهائيان | نجحا؛ Online وOffline `BUILD SUCCESSFUL` |
| فحص APK والصلاحيات/المحتوى | نجح؛ Online بلا MP3/M4A/model/sherpa، وOffline بـ6236 M4A وmodel ZIP وnative runtime وبلا INTERNET |

## القيود الصريحة

تدقيق metadata لا يثبت وحده أن النطق اللغوي لكل آية أو كل segment كامل. لم يُستمع إلى جميع 6236 ملفاً ولم يُشغّل ASR مستقلاً على كل ملف. كذلك لا يثبت build نجاح SpeechRecognizer على جهاز محدد أو inference Offline على كل ABI. لا يجوز وصف هذا المرشح بأنه تجاري أو عام أو خالٍ من قيود الحقوق.

## التراجع

تبقى نقطة `quran-v1` tag محلية محفوظة ولا تُستبدل. عند التراجع لا تُحذف ملفات Word أو pronunciation أو timing أو manifests أو NOTICE. إذا تغير مصدر الصوت، يجب إعادة بناء M4A وmanifest وإعادة تدقيق الحدود، ولا يجوز خلط تسجيلات قارئين.

## المراجع

[1]: https://qul.tarteel.ai/resources/recitation/112 "QUL Husary recitation source"
[2]: https://developer.android.com/reference/android/speech/SpeechRecognizer "Android SpeechRecognizer"
[3]: https://github.com/GovarJabbar/Quran-PNG/tree/master "Quran-PNG external reference"
