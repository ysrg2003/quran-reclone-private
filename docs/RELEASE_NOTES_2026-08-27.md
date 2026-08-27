# Release notes — quran-v1-writing-mode-fix — 2026-08-27

## حالة الإصدار

هذا **إصدار تصحيحي مستقل للاختبار الداخلي** بعد `quran-v1` و`quran-v1-postfix` و`quran-v1-writing-mode`. لا يستبدل أياً من الوسوم أو الإصدارات التاريخية. لا يمثل هذا الإصدار clearance للنشر العام أو التجاري؛ ما زالت حقوق إعادة توزيع ملف Word، وخط KFGQPC، وتسجيل محمود خليل الحصري، وبيانات التوقيت المشتقة تحتاج إذناً صريحاً قابلاً للأرشفة يغطي التضمين داخل Android وإعادة التوزيع والبث والتخزين المؤقت والاستخدام التجاري عند الحاجة.

## إصلاح قبول النص المكتوب

كان مسار المطابقة المعتمد على مرجع النطق يرفض أحياناً الرسم القرآني المطبّع الذي يكتبه المستخدم. أُضيف خيار `allowCanonicalQuranSpelling` إلى tracker الكتابة فقط. لذلك، عند الكتابة في الموضع الصحيح، يقبل التطبيق `الرحمن` و`الرحمان` للكلمة المرئية `ٱلرَّحۡمَٰنِ`، مع قبول النص القرآني المطبّع المقابل للكلمات الأخرى أيضاً. لا يُعاد تشكيل النص المرئي ولا يُستبدل النص الخام قبل الحكم.

يبقى مسار **التسميع الصوتي** مقابل expected المنطوق الصارم، حتى لا تتحول قاعدة تخفيف اختلاف الكتابة في حقل الكتابة إلى قبول غير مقصود للألف المنطوقة أو الصامتة في التعرف الصوتي. وتبقى قواعد الألف المرسومة المنطوقة، والألف النهائية الصامتة، والتاء المربوطة، والمد، والحروف المقطعة، والحواجز الموضعية كما هي.

اختبارات الوحدة تغطي قبول الصيغتين في tracker الكتابي و`PronunciationSpeechMatcher` مع إبقاء الصوت الصارم، وتغطي استمرار منع تخطي الكلمات أو فتح آية لاحقة قبل إصلاح الحاجز.

## إصلاح دقة مقاطع الكلمات

أثبت فحص عينة `002006.mp3` من تسجيل الحصري وجود cue أول قصير جداً من `0–70ms` مع صمت افتتاحي يقارب `1417ms`، وcue أخير قصير من `19250–19300ms` في ملف مدته نحو `19365ms`. تشغيل هذه النوافذ الخام يجعل التصحيح يبدو صمتاً أو جزءاً غير دقيق من الكلمة.

أضيف إلى `TimingWordMapper` fallback ضيق خاص بتسجيل Husary: إذا كان cue الأول أو الأخير أقصر من `120ms` وتفصله فجوة لا تقل عن `600ms` عن cue المجاور، تُقدّر نافذة الكلمة من الحد المجاور. لا تُعدّل التوقيتات العادية، ولا تُستخدم توقيتات `pronunciation_reference.json` المرتبطة بطبقة Alafasy كبديل لتسجيل Husary. تشغيل الآية الكاملة ما زال يبدأ من offset صفر، و`ReferenceAudioPlayer` يقيد النهاية بمدة MediaPlayer الفعلية.

هذا إصلاح آلي محدود لحدود شاذة، وليس إثباتاً أن كل مقطع من 6,236 آية مطابق لكل phoneme. تقرير العينة الكامل موجود في `docs/AUDIO_CLIP_INVESTIGATION_2026-08-27.md`.

## تحسين التباين والألوان

استُبدلت palette البنفسجية/المرجانية منخفضة الوضوح بدرجات أخضر داكن وذهبي أوضح لخلفيات التطبيق والبطاقات. رُفعت وضوح النصوص الثانوية، وأصبحت أزرار `OutlinedButton` داخل بطاقة التسميع الداكنة تستخدم نصاً وحدوداً بيضاء صريحة بدلاً من لون افتراضي قد يقترب من لون الخلفية. لا يغيّر ذلك تخطيط المصحف أو نص Word أو ترتيب الكلمات والأسطر.

## عقد Online وOffline

| العنصر | Online | Offline |
|---|---|---|
| قواعد بيانات القرآن وWord canonical والتخطيط والتوقيتات والـpreview | داخل APK | داخل APK |
| صوت الحصري | يُجلب HTTPS عند الحاجة؛ لا MP3/M4A داخل APK | 6,236 ملف M4A AAC-LC داخل APK |
| التسميع الصوتي | Android `SpeechRecognizer` | Android `SpeechRecognizer` |
| التسميع بالكتابة | محلي عبر `RecitationProgressTracker` | محلي عبر `RecitationProgressTracker` |
| `INTERNET` | مطلوب لـSpeechRecognizer والصوت | مطلوب لـSpeechRecognizer فقط أثناء الصوت |
| `RECORD_AUDIO` | مطلوب للصوت فقط | مطلوب للصوت فقط |
| Sherpa/FastConformer/ONNX/model محلي | غير موجود | غير موجود |

وضع الكتابة لا يحتاج ميكروفوناً أو شبكة. تشغيل صوت الحصري المحلي في Offline لا يحتاج الشبكة، لكن التعرف الصوتي في Offline يحتاج Internet لأن العقد النهائي يستخدم Android SpeechRecognizer ولا يستخدم ASR محلياً.

## Artifacts النهائية

| variant | path | size | SHA-256 |
|---|---|---:|---|
| Online | `app/build/outputs/apk/online/debug/app-online-debug.apk` | 23,415,576 bytes | `d39c19f778ef6135591b4d3ee390e58f6b67e6137defc09466c11966181aab5e` |
| Offline | `app/build/outputs/apk/offline/debug/app-offline-debug.apk` | 1,300,252,702 bytes | `94aa4c3432a62f6191f3f5ac97637c1b12f9e0e8bdb9dd39c084e788e88df88c` |

فحص الـAPK أثبت وجود `INTERNET` و`RECORD_AUDIO` في الإصدارين. Online يحتوي 0 M4A و0 MP3 و0 أسماء ASR محلي، وOffline يحتوي 6,236 M4A و0 MP3 و0 أسماء FastConformer/Sherpa/ONNX/model. يحتوي كل APK على 7 ملفات legal assets. اجتاز Offline `verify_offline_apk.sh` بنتيجة `offline-apk-ok: permissions=INTERNET,RECORD_AUDIO m4a=6236 local-asr=0`.

## التحقق المنفذ

| البوابة | النتيجة |
|---|---|
| `:app:testOnlineDebugUnitTest` | نجحت؛ 125 اختباراً |
| `:app:testOfflineDebugUnitTest` | نجحت؛ 125 اختباراً |
| `:app:assembleOnlineDebug` | نجح |
| `:app:assembleOfflineDebug` | نجح بعد حذف نموذج FastConformer من مصدر Offline وإعادة البناء |
| `:app:lintOnlineDebug` | نجح؛ بقي تحذير deprecated متعلقاً بـ`Icons.Filled.MenuBook` فقط |
| `:app:lintOfflineDebug` | نجح؛ بقي التحذير نفسه فقط |
| اختبارات matcher والكتابة | نجحت؛ `الرحمن` و`الرحمان` مقبولتان في الكتابة |
| اختبارات edge timing | نجحت؛ cue الأول/الأخير الشاذان يستخدمان نافذة تقديرية آمنة |
| validators النص والتخطيط والنطق والـpreview والصوت | مثبتة سابقاً في `VERIFICATION_MATRIX.md`؛ لم تتغير أصول Word أو QUL |
| تدقيق APK والصلاحيات والمحتوى | نجح بالقيم أعلاه |
| اختبار هاتف/ADB/MediaPlayer/SpeechRecognizer حي | مؤجل؛ لم ينفذ في هذه الجولة |

## القيود والحقوق

لم يُستمع إلى جميع 6,236 ملفاً، ولم يُشغّل ASR مستقلاً على كامل الصوت، ولا يثبت تدقيق الحاويات وحده اكتمال النطق أو دقة كل كلمة. لم يجر اختبار حي على هاتف، لذلك لا يجوز ادعاء نجاح الميكروفون أو SpeechRecognizer أو تشغيل M4A على جهاز محدد. كما أن وجود مصدر عام أو إسناد مفتوح لمكوّن ما لا يثبت تلقائياً حق شحن Word أو الخط أو التسجيل أو timing المشتق. الإصدار للاختبار الداخلي فقط.

## المراجع

[1]: https://developer.android.com/reference/android/speech/SpeechRecognizer "Android SpeechRecognizer"
[2]: https://qul.tarteel.ai/resources/recitation/112 "QUL Husary recitation source"
[3]: https://github.com/GovarJabbar/Quran-PNG/tree/master "Quran-PNG external reference"
