# Verification Matrix — post-fix candidate 2026-08-27

هذه المصفوفة تفصل بين تحقق المصدر، واختبارات الوحدة، وفحص الجهاز، والمراجعة القانونية. **نجاح البناء لا يساوي اختبار هاتف أو إثبات اكتمال نطقي أو clearance قانونية.** التفاصيل الهندسية في `AI_CONTEXT.md`، ومسارات المبتدئ في `README.md`.

## الحالة الحالية

| المجال | معيار القبول | الأمر/الدليل | الحالة | الملاحظة |
|---|---|---|---|---|
| Git snapshot | tag `quran-v1` موجود ولا يحتوي audio blobs في ref القابل للدفع | `git show quran-v1` وobject audit | محلياً مثبت، والرفع مؤجل | يشير إلى `1c7d2dd`؛ لا يُدفع قبل فحص pack/reflog |
| Word canonical | 6236 آية و77430 كلمة دون تغيير | `scripts/validate_word_reference_content.py` | بوابة نهائية مؤجلة | يجب تشغيلها بعد آخر تعديل |
| QUL timing | 6236 آية، 77430 نافذة canonical، raw/non-word audit | `scripts/validate_full_quran_assets.py` و`audit_husary_audio_boundaries.py` | بوابة نهائية مؤجلة | لا يُعدّل QUL JSON بصمت |
| preview | الصفحات 1–5 وعددها 36 آية مشتقة من canonical | `scripts/verify_word_reference_preview.py` | بوابة نهائية مؤجلة | preview لا يستبدل full load |
| pronunciation asset | 6236 صفاً وحقول `spoken`/`spokenTokens`/`maddPositions` | `PronunciationReference.load()` | مثبت مصدر | يقرأ `pronunciation_reference.json` المنفصل، لا provider output |
| basmala barrier | حذف `الرحمان` يجعل `الرحيم` `UNCERTAIN` | `OpeningBasmalaProgressTrackerTest` | مثبت | standalone basmala لا تدخل `allAyahs` |
| whole ayah start | queue الآية الكاملة يبدأ من offset صفر | `TimingWordMapperTest` و`QuranViewModel` | مثبت مصدر/وحدة | MediaPlayer live test مؤجل |
| word correction | window للكلمة مع gap padding <=120ms | `withCorrectionPadding` وtests | مثبت مصدر/وحدة | لا يعبر كلمة مجاورة |
| audio files | 6236 MP3 و6236 M4A، non-empty، وffprobe لكل ملف | `verify_offline_audio_bundle.py` + audit | مثبت في audit | الملفات الكبيرة ignored من Git |
| audio bounds | ترتيب cues وحدودها مقابل مدة MP3/M4A | `HUSARY_AUDIO_BOUNDARY_AUDIT_2026-08-27.json` | مثبت مع anomalies مسجلة | 228 M4A و140 MP3 boundary anomaly؛ ليست حكماً لغوياً |
| sample audio | التفريق بين نقص الملف وتأخر first cue | `HUSARY_AUDIO_SAMPLE_ASR_2026-08-27.md` | عينة محدودة مثبتة | `001001` و`002015` فقط، لا 6236 ASR |
| Online source | SpeechRecognizer يعيد الاستماع بعد pauses/transient errors ويحافظ على raw candidate | `OnlineSpeechSession.kt` + compile | مثبت مصدر | سلوك provider على الهاتف مؤجل |
| Offline source | sherpa/AudioRecord بلا fallback شبكي | Offline source/manifest | مثبت مصدر | inference على 64-bit phone مؤجل |
| Online tests | JVM tests compile/run | `:app:testOnlineDebugUnitTest` | نجح في هذه الجولة | output: `BUILD SUCCESSFUL` |
| Offline tests | JVM tests compile/run | `:app:testOfflineDebugUnitTest` | نجح في هذه الجولة | output: `BUILD SUCCESSFUL` |
| lint | لا توجد أخطاء release-blocking | `:app:lintOnlineDebug :app:lintOfflineDebug` | نجح في هذه الجولة | warning deprecated icon فقط |
| assemble | APKs للـflavorين | `:app:assembleOnlineDebug :app:assembleOfflineDebug` | نجح في هذه الجولة | الأحجام والبصمات أدناه |
| APK contents | Online بلا audio/model/sherpa؛ Offline بلا INTERNET وبأصول محلية | `docs/APK_CONTENT_AUDIT_2026-08-27.md` | نجح | فحص merged APK/manifest فعلياً |
| ADB Online | basmala omission، pause، raw candidate، whole ayah first word | phone test log | مؤجل | لا يوجد سجل جهاز في هذه الجولة |
| ADB Offline | network disabled، local inference/audio، بلا crash | phone test log | مؤجل | يتطلب جهاز 64-bit متوافق |
| rights | إذن صريح للتضمين/إعادة التوزيع/البث/cache/commercial | وثائق حقوق مؤرشفة | blocked | Word/KFGQPC/Husary/QUL-derived timing unresolved |
| GitHub Release | history نظيف، artifacts نهائية، notes وhashes | `gh auth status` ثم push/release | مؤجل | لا ترفع قبل gates والحقوق |

## Artifacts النهائية لهذه الجولة

| variant | path | size | SHA-256 |
|---|---|---:|---|
| Online | `app/build/outputs/apk/online/debug/app-online-debug.apk` | 23,407,353 bytes | `d8780e203bfada7863685af6f331ab462a97ec6c18247134c31ba82b3e6bc121` |
| Offline | `app/build/outputs/apk/offline/debug/app-offline-debug.apk` | 1,466,812,771 bytes | `89423ce087441e8a528c0eac15ad3b9e242a3f4c02fdd5c3db4186ff3dabf0ef` |

الدليل التفصيلي للصلاحيات وعدّ entries هو `docs/APK_CONTENT_AUDIT_2026-08-27.md`. هذه artifacts داخلية وليست clearance قانونية.

## نتائج تدقيق الصوت

في 2026-08-27 فحصت الأداة `6236` صفاً، و`6236` MP3، و`6236` M4A، ونجح `ffprobe` لكل الملفات. وسيط `firstTimingStartMs` و`leadingGapMs` يساوي `0`، لكن `114` آية يبدأ أول cue فيها بعد `1000ms`، والحد الأقصى `3010ms`. ظهرت `228` حالة تتجاوز فيها نهاية QUL مدة M4A و`140` حالة تتجاوز فيها مدة MP3؛ أُبقيت هذه الحالات في التقرير ولم تُعدّل بيانات QUL.

فحص ASR المحدود أعاد من `001001.mp3` النص «بسم الله الرحمن الرحيم» من بداية الملف تقريباً، وأعاد من `002015.mp3` أول كلمة مع بداية صوتية قبل cue الأول عند `1330ms`. يدعم ذلك بدء القراءة الكاملة من `0`، لكنه لا يثبت linguistic completeness لكل الملفات.

## الإصلاحات المرتبطة بالمصفوفة

| العيب | السبب المثبت | الإصلاح |
|---|---|---|
| تخطي كلمة وسط البسملة | tracker المفقود/المقارنة الدفعة لم تفرض barrier كلمة بكلمة | `OpeningBasmalaProgressTracker` مستقل مع regression test؛ اللاحق `UNCERTAIN` |
| قص بداية الآية عند cue متأخر | queue الكاملة تستخدم first timing start | queue البسملة/الآية/السورة/القرآن ترسل `startMs=0` |
| نهاية cue بعد مدة الحاوية | فروق QUL integer وMP3/AAC duration | `ReferenceAudioPlayer` يقيد الحدود بمدة `MediaPlayer` بعد prepare |
| تعطل/بطء التحميل | full assets وعمليات lookup متكررة | preview مبكر، تحميل IO متوازٍ، `totalWordCount`، وفهارس الصفحة المشتركة |
| فقدان ميكروفون/رمز 7 في نسخة مستعادة | OnlineSpeechSession الحالية كانت minimal وتُنهي عند end/error | استُعيد contract النسخة العاملة السابقة: partial/final accumulation وrestart للأخطاء العابرة؛ extras محفوظة |

## أوامر البوابة النهائية

نفذ من جذر المستودع:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_SDK_ROOT=/home/ubuntu/tools/android-sdk
/home/ubuntu/tools/gradle-8.10.2/bin/gradle :app:testOnlineDebugUnitTest :app:testOfflineDebugUnitTest --no-daemon
python3 scripts/validate_word_reference_content.py --reference app/src/main/assets/quran/word_reference_ayahs.json
python3 scripts/validate_word_reference_layout.py
python3 scripts/validate_pronunciation_reference.py --reference app/src/main/assets/quran/pronunciation_reference.json
python3 scripts/verify_word_reference_preview.py
python3 scripts/validate_full_quran_assets.py --mp3-dir ../quran-husary-mp3-source
python3 scripts/verify_offline_audio_bundle.py
/home/ubuntu/tools/gradle-8.10.2/bin/gradle :app:assembleOnlineDebug :app:assembleOfflineDebug --no-daemon
/home/ubuntu/tools/gradle-8.10.2/bin/gradle :app:lintOnlineDebug :app:lintOfflineDebug --no-daemon
AAPT=/home/ubuntu/tools/android-sdk/build-tools/35.0.0/aapt
$AAPT dump permissions app/build/outputs/apk/online/debug/app-online-debug.apk
$AAPT dump permissions app/build/outputs/apk/offline/debug/app-offline-debug.apk
unzip -l app/build/outputs/apk/online/debug/app-online-debug.apk | grep -E 'm4a|mp3|fastconformer|sherpa|libonnxruntime' || true
unzip -l app/build/outputs/apk/offline/debug/app-offline-debug.apk | grep -E 'm4a|mp3|fastconformer|sherpa|third_party_licenses'
sha256sum app/build/outputs/apk/online/debug/app-online-debug.apk app/build/outputs/apk/offline/debug/app-offline-debug.apk
```

عند اختلاف أي نتيجة، أوقف release وسجل الأمر ومخرجاته في ملف evidence جديد بدلاً من تحويل `deferred` إلى `passed` يدوياً.

## ملاحظات تاريخية لا تُعاد كحقائق حالية

كانت المصفوفة القديمة تحتوي hashes وأحجام APK ونتائج هاتف وعبارات تخص إصدارات سابقة، كما كانت تصف أحياناً حقول pronunciation داخل Word canonical أو مسارات Alafasy. هذه المعلومات لا تُستخدم كدليل للمرشح الحالي؛ الدليل الحالي هو الملفات والاختبارات والتقارير المذكورة أعلاه.

## المراجع

[1]: https://developer.android.com/reference/android/speech/SpeechRecognizer "Android SpeechRecognizer"
[2]: https://qul.tarteel.ai/resources/recitation/112 "QUL Husary recitation source"
[3]: https://github.com/GovarJabbar/Quran-PNG/tree/master "Quran-PNG external reference"
