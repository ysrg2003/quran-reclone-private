# Release notes — 2026-08-27 — post-fix candidate

## حالة الإصدار

هذا مرشح تطوير واختبار داخلي بعد نقطة `quran V1`. لا يمثل clearance للنشر العام أو التجاري. ما زال إذن إعادة توزيع Word وخط KFGQPC وتسجيل محمود خليل الحصري وبيانات توقيت QUL المشتقة يحتاج وثيقة صريحة قابلة للأرشفة تغطي التضمين داخل Android وإعادة التوزيع والبث والتخزين والاستخدام التجاري عند الحاجة.

أعيد تشغيل اختبارات الوحدة وassemble وlint بعد تغيير عقد Offline وإضافة وضع الكتابة. البصمات أدناه ناتجة فعلياً من هذا المرشح، ولا تثبت حقوق إعادة التوزيع أو اختبار الهاتف.

## الإصلاحات

### البسملة الافتتاحية

أُعيد إدخال `OpeningBasmalaProgressTracker` المفقود من clone المستعاد. البسملة الافتتاحية عنصر مستقل وليست آية ضمن `allAyahs`، باستثناء أن الفاتحة نفسها تبدأ بصف Word 1:1، ولا توجد بسملة مستقلة للتوبة. يعتمد tracker على التقدم المتسلسل، لذلك فإن `بسم الله الرحيم` يجعل `الرحمان` `MISSING` ويبقي `الرحيم` `UNCERTAIN` ولا يسمح بتسريب الكلمة اللاحقة إلى الآية. يغطي ذلك `OpeningBasmalaProgressTrackerTest`.

### بداية ونهاية الصوت

كانت قراءة الآية الكاملة تُبنى من `first timing start`، وهو قد يتأخر عن بداية الملف. أصبحت queue الخاصة بالآية والسورة والقرآن والبسملة ترسل `startMs=0`. بقي قص كلمة التصحيح مرتبطاً بنافذة QUL مع padding gap-aware لا يتجاوز 120ms داخل الصمت المتاح.

بعد prepare، يقيد `ReferenceAudioPlayer` حدود التشغيل بمدة `MediaPlayer` الفعلية. هذا لا يغيّر QUL JSON؛ إنما يمنع نهاية cue integer من تجاوز مدة MP3/M4A أو جدولة stop غير صالح.

### مصدر طبقة النطق

أصبح `PronunciationReference.load()` يقرأ `quran/pronunciation_reference.json` التفصيلي الذي يحتوي `spokenTokens` و`maddPositions` وحقول ربط الصوت، بدلاً من إعادة قراءة Word JSON المبسط. يظل observed text القادم من SpeechRecognizer خاماً في `RecognitionUpdate.value/providerText` ولا يستبدل بالنص canonical قبل الحكم.

### البطء والتعليق

ظل preview للصفحات 1–5 أول واجهة. بعده تُحمّل allAyahs وmetadata وlayout وpronunciation وtiming في مهام IO مستقلة، ثم تُجرى validation قبل نشر الحالة الكاملة. أزيل `flatMap` المتكرر لكل كلمات المصحف عبر `totalWordCount`، ونُقلت خرائط ayah/index/word-offset إلى مستوى سطح الصفحة بدلاً من بنائها لكل سطر Compose.

### التسميع بالكتابة وعقد الـflavors

أضيف `RecitationInputMode.TYPING` إلى تبويبي الحفظ والقراءة. زر البدء يهيئ جلسة كتابة بلا ميكروفون، وحقل عربي وزر «إرسال النص للتحقق» يمران النص النهائي إلى نفس `RecitationProgressTracker` المستخدم للصوت؛ لذلك يبقى ترتيب الكلمات وbarrier وتصحيح البسملة وسجل الأخطاء موحداً. Online وOffline يستخدمان الآن `OnlineSpeechSession`/Android `SpeechRecognizer` للتسميع الصوتي. أزيل Sherpa-ONNX وFastConformer والنموذج المحلي من Gradle وsource sets. Offline يحتفظ ببيانات القرآن والتوقيتات و6,236 ملف M4A فقط، ويحتاج الإنترنت للتعرف الصوتي عبر SpeechRecognizer.

أُعيدت `OnlineSpeechSession` إلى contract النسخة العاملة السابقة: partial لا ينهي الجلسة، final يُراكم، وأخطاء provider العابرة مثل `ERROR_NO_MATCH` تعيد فتح segment بدلاً من إنهاء الميكروفون. لم تُجرَ تغييرات خاصة جديدة على lifecycle أو extras خارج هذا contract المستعاد.

## Online وOffline في هذا المرشح

| العنصر | Online | Offline |
|---|---|---|
| قواعد البيانات وWord والـpreview | داخل APK | داخل APK |
| صوت الحصري | يُجلب HTTPS عند الحاجة؛ لا MP3/M4A داخل APK | 6,236 ملف M4A AAC-LC داخل APK |
| ASR الصوتي | Android `SpeechRecognizer` | Android `SpeechRecognizer` |
| التسميع بالكتابة | محلي عبر `RecitationProgressTracker` | محلي عبر `RecitationProgressTracker` |
| INTERNET | مطلوب لـSpeechRecognizer والصوت | مطلوب لـSpeechRecognizer فقط أثناء التسميع الصوتي |
| RECORD_AUDIO | مطلوب للصوت فقط | مطلوب للصوت فقط |
| model/Sherpa | غير موجود | غير موجود |
| artifact | 23,413,744 bytes؛ SHA-256 `00da3ab68e5a5a8e0453a11da665a3eb6ecf72a8562a94318f2de5c82821573f` | 1,300,250,950 bytes؛ SHA-256 `400e45219d9292e387dc575a9cd93c52994d780c84657cd7142d686fe70d4a2d` |

صوت القراءة المحلي في Offline لا يحتاج الشبكة، لكن هذا لا يجعل التسميع الصوتي Offline بالكامل؛ التعرف نفسه يمر عبر Android SpeechRecognizer. وضع الكتابة لا يحتاج Internet ولا ميكروفون.

حجم أصول M4A الحالي `1,274,873,643` bytes، وmanifest hash/size هو `app/src/offline/assets/quran/audio/husary_aac_manifest.json`. وجود الملفات محلياً لا يعني أن حق إعادة توزيع التسجيل مثبت.

## التحقق المنفذ حتى الآن

| البوابة | النتيجة |
|---|---|
| `:app:testOnlineDebugUnitTest` | نجحت بعد إضافة وضع الكتابة والعقد الجديد |
| `:app:testOfflineDebugUnitTest` | نجحت بعد إضافة وضع الكتابة والعقد الجديد |
| تدقيق container/timing لـ6236 آية | أُنجز؛ التقرير الكامل في `docs/HUSARY_AUDIO_BOUNDARY_AUDIT_2026-08-27.*` |
| ASR عينة 1:1 و2:15 | أُنجز؛ الدليل في `docs/HUSARY_AUDIO_SAMPLE_ASR_2026-08-27.md` |
| AI_CONTEXT validator | نجح بلا أخطاء |
| اختبار الهاتف/ADB/MediaPlayer حي | **deferred**؛ لم ينفذ في هذه الجولة |
| assemble/lint للعقد الجديد | نجحا؛ `BUILD SUCCESSFUL` للـflavorين مع warning deprecated icon فقط |
| فحص APK والصلاحيات/المحتوى للعقد الجديد | نجح؛ Online بلا audio، Offline بـ6,236 M4A، وكلاهما بـ`INTERNET` و`RECORD_AUDIO`، بلا model/Sherpa |

## القيود الصريحة

تدقيق metadata لا يثبت وحده أن النطق اللغوي لكل آية أو كل segment كامل. لم يُستمع إلى جميع 6236 ملفاً ولم يُشغّل ASR مستقلاً على كل ملف. كذلك لا يثبت build نجاح SpeechRecognizer على جهاز محدد أو عمله في Offline عند انقطاع الشبكة، ولا يثبت اختبار واجهة الكتابة على هاتف. لا يجوز وصف هذا المرشح بأنه تجاري أو عام أو خالٍ من قيود الحقوق.

## التراجع

تبقى نقطة `quran-v1` tag محلية محفوظة ولا تُستبدل. عند التراجع لا تُحذف ملفات Word أو pronunciation أو timing أو manifests أو NOTICE. إذا تغير مصدر الصوت، يجب إعادة بناء M4A وmanifest وإعادة تدقيق الحدود، ولا يجوز خلط تسجيلات قارئين.

## المراجع

[1]: https://qul.tarteel.ai/resources/recitation/112 "QUL Husary recitation source"
[2]: https://developer.android.com/reference/android/speech/SpeechRecognizer "Android SpeechRecognizer"
[3]: https://github.com/GovarJabbar/Quran-PNG/tree/master "Quran-PNG external reference"
