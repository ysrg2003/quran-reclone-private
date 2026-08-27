# Verification Matrix — writing-mode correction 2026-08-27

تفصل هذه المصفوفة بين تحقق المصدر، واختبارات الوحدة، وتدقيق الـAPK، وفحص الجهاز، والمراجعة القانونية. **نجاح البناء لا يساوي اختبار هاتف أو إثبات اكتمال نطقي أو clearance قانونية.** التفاصيل الهندسية في `AI_CONTEXT.md`، ومسارات الاستخدام في `README.md`.

## الحالة الحالية

| المجال | معيار القبول | الأمر/الدليل | الحالة | الملاحظة |
|---|---|---|---|---|
| Git snapshot | بقاء `quran-v1` و`quran-v1-postfix` دون استبدال | `git show` وobject audit | مثبت | الإصلاح الحالي سيصدر بوسم مستقل |
| Word canonical | 6236 آية و77430 كلمة دون تغيير | `scripts/validate_word_reference_content.py` | مثبت | `word-reference-content=PASS`; 6236 آية و77430 كلمة |
| QUL timing | 6236 آية، 77430 نافذة canonical، raw/non-word audit | `scripts/validate_full_quran_assets.py` و`audit_husary_audio_boundaries.py` | مثبت | `quran-ayahs-ok=6236`; anomalies موثقة ولا تُحوّل إلى إثبات لغوي |
| preview | الصفحات 1–5 وعددها 36 آية مشتقة حرفياً | `scripts/verify_word_reference_preview.py` | مثبت | `word_reference_preview_exact_subset True` |
| pronunciation asset | 6236 صفاً و77430 كلمة مع `spoken` و`spokenTokens` و`maddPositions` | `scripts/validate_pronunciation_reference.py` و`PronunciationReference.load()` | مثبت | `pronunciation-reference=PASS`؛ expected منفصل عن observed |
| قبول الرسم في الكتابة | `الرحمن` و`الرحمان` صحيحان للكلمة `ٱلرَّحۡمَٰنِ` | `allowCanonicalQuranSpelling` واختبارات matcher/tracker | مثبت unit | مطبق في الكتابة فقط؛ لا يغير النص المرئي |
| صوت SpeechRecognizer | observed الخام يقارن بمرجع النطق الصارم | `PronunciationSpeechMatcher` | مثبت مصدر/unit | لا يستبدل provider output بنص Word قبل الحكم |
| basmala barrier | حذف `الرحمان` يجعل `الرحيم` `UNCERTAIN` | `OpeningBasmalaProgressTrackerTest` | مثبت | ينطبق على الكتابة والصوت |
| whole ayah start | queue الآية الكاملة تبدأ من offset صفر | `TimingWordMapperTest` و`QuranViewModel` | مثبت مصدر/unit | MediaPlayer live test مؤجل |
| word correction | لا تعبر النافذة كلمة مجاورة؛ padding gap-aware | `ReferenceAudioWindowTest` | مثبت unit | إعدادات padding محدودة |
| edge timing repair | cue أول/أخير قصير جداً مع فجوة كبيرة يستخدم نافذة تقديرية من حدود الجار | `TimingWordMapperTest` و`AUDIO_CLIP_INVESTIGATION_2026-08-27.md` | مثبت unit/عينة | إصلاح محدود لا يثبت دقة كل كلمة |
| audio files | 6236 MP3 مصدر و6236 M4A Offline، non-empty، وffprobe لكل ملف | `verify_offline_audio_bundle.py` + audit | مثبت | `PASS offline audio`; 6236/6236 probe؛ MP3 خارج Git وAPK |
| audio bounds | ترتيب cues وحدودها مقابل مدة MP3/M4A | `HUSARY_AUDIO_BOUNDARY_AUDIT_2026-08-27.json` | مثبت مع anomalies مسجلة | 228 M4A و140 MP3 boundary anomaly؛ ليست حكماً لغوياً |
| Online source | Android SpeechRecognizer مع حفظ raw candidate وإعادة الاستماع بعد أخطاء عابرة | `OnlineSpeechSession.kt` + compile | مثبت مصدر | سلوك provider على الهاتف مؤجل |
| Offline source | Android SpeechRecognizer للصوت، وM4A الحصري محلي، بلا ASR محلي | `OnlineSpeechSession` + APK audit | مثبت مصدر/APK | SpeechRecognizer على هاتف مؤجل |
| Online tests | JVM tests compile/run | `:app:testOnlineDebugUnitTest` | مثبت | نجح بعد إصلاح المطابقة والتوقيت |
| Offline tests | JVM tests compile/run | `:app:testOfflineDebugUnitTest` | مثبت | نجح بعد إصلاح المطابقة والتوقيت |
| lint | لا توجد أخطاء release-blocking | `:app:lintOnlineDebug` و`:app:lintOfflineDebug` | مثبت | `BUILD SUCCESSFUL`؛ بقي warning deprecated icon فقط |
| assemble | APKs للـflavorين | `:app:assembleOnlineDebug` و`:app:assembleOfflineDebug` | مثبت | القيم النهائية أدناه |
| APK contents | Online بلا audio/model؛ Offline بـ6236 M4A وبلا MP3/model/Sherpa/ONNX | `APK_CONTENT_AUDIT` و`verify_offline_apk.sh` | مثبت | كلاهما يعلن `INTERNET` و`RECORD_AUDIO` |
| ADB Online | basmala، pause، raw candidate، الكتابة، whole ayah first word | phone test log | مؤجل | لا يوجد سجل جهاز في هذه الجولة |
| ADB Offline | SpeechRecognizer مع الشبكة، تشغيل M4A محلي، الكتابة، بلا crash | phone test log | مؤجل | لا يختبر ASR محلياً؛ يتطلب provider متاحاً |
| rights | إذن صريح للتضمين/إعادة التوزيع/البث/cache/commercial | وثائق الحقوق المؤرشفة | blocked | Word/KFGQPC/Husary/QUL-derived timing unresolved |
| GitHub Release | tag مستقل وassets متطابقة مع هذه القيم | [quran-v1-writing-mode-fix](https://github.com/ysrg2003/quran-memorization-online/releases/tag/quran-v1-writing-mode-fix) | مثبت | prerelease منشور؛ digest وحجم كل asset يطابقان APK المحلي؛ لا يستبدل الإصدارات القديمة |

## Artifacts النهائية بعد الإصلاح

| variant | path | size | SHA-256 |
|---|---|---:|---|
| Online | `app/build/outputs/apk/online/debug/app-online-debug.apk` | 23,415,576 bytes | `d39c19f778ef6135591b4d3ee390e58f6b67e6137defc09466c11966181aab5e` |
| Offline | `app/build/outputs/apk/offline/debug/app-offline-debug.apk` | 1,300,252,702 bytes | `94aa4c3432a62f6191f3f5ac97637c1b12f9e0e8bdb9dd39c084e788e88df88c` |

## تدقيق محتوى الـAPK

| الفحص | Online | Offline |
|---|---:|---:|
| `INTERNET` | موجود | موجود |
| `RECORD_AUDIO` | موجود | موجود |
| ملفات M4A | 0 | 6236 |
| ملفات MP3 | 0 | 0 |
| أسماء FastConformer/Sherpa/ONNX/model | 0 | 0 |
| ملفات `assets/third_party_licenses/` | 7 | 7 |

يمر Offline أيضاً عبر `scripts/verify_offline_apk.sh` بنتيجة `offline-apk-ok: permissions=INTERNET,RECORD_AUDIO m4a=6236 local-asr=0`. وضع الكتابة لا يحتاج الشبكة أو الميكروفون. الصوت في Offline محلي، لكن التعرف الصوتي ما زال يحتاج Internet عبر Android SpeechRecognizer.

## نتائج فحص الصوت

العينة الموثقة في `docs/AUDIO_CLIP_INVESTIGATION_2026-08-27.md` استخدمت `002006.mp3`. أظهر الملف صمتاً افتتاحياً يقارب 1417ms، بينما يضع QUL cue الأول عند `0–70ms` وآخر cue عند `19250–19300ms` لملف مدته نحو 19365ms. لذلك أضيف إصلاح محدود: cue أول أو أخير أصغر من 120ms مع فجوة لا تقل عن 600ms يُستبدل بنافذة تقديرية مستندة إلى الحد المجاور. لا يُستخدم timing قارئ آخر كبديل؛ توقيتات `pronunciation_reference.json` المرتبطة بطبقة Alafasy لا تُخلط بتسجيل Husary.

يبقى هذا تدقيقاً آلياً وعينة تفريغ تقريبية، وليس استماعاً بشرياً لكل 6236 ملفاً أو إثباتاً أن كل cue يطابق كل phoneme. يلزم اختبار الهاتف قبل تحويل هذه النتيجة إلى ادعاء تجربة استخدام.

## الإصلاحات المرتبطة بالمصفوفة

| العيب | السبب المثبت | الإصلاح |
|---|---|---|
| رفض `الرحمن` في وضع الكتابة | المسار كان يطابق expected المنطوق فقط | `allowCanonicalQuranSpelling` يقبل الرسم القرآني المطبّع والصيغة المنطوقة معاً في الكتابة |
| إضعاف دقة cue الأول/الأخير | بعض QUL boundaries قصيرة جداً مع فجوات افتتاح/tail كبيرة | `TimingWordMapper.repairSuspiciousEdgeCue` يقدّر edge window الضيق فقط |
| تباين أزرار البطاقة الداكنة | أزرار `OutlinedButton` كانت ترث لوناً قد لا يظهر على الخلفية الداكنة | نص وحدود بيضاء واضحة، مع palette أخضر/ذهبي أعلى وضوحاً |
| تخطي كلمة وسط البسملة | tracker مستقل يفرض barrier كلمة بكلمة | `OpeningBasmalaProgressTracker`؛ اللاحق `UNCERTAIN` |
| قص بداية الآية | queue الكاملة تستخدم first timing start | queue تبدأ من `startMs=0` |
| نهاية cue بعد مدة الحاوية | فروق QUL integer وMP3/AAC duration | `ReferenceAudioPlayer` يقيد الحدود بعد prepare |

## أوامر البوابة النهائية

نفذ من جذر المستودع، مع تشغيل بناء Online وOffline منفصلين عند ضغط الذاكرة:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_SDK_ROOT=/home/ubuntu/tools/android-sdk
/home/ubuntu/tools/gradle-8.10.2/bin/gradle --offline --no-daemon --max-workers=1 :app:testOnlineDebugUnitTest
/home/ubuntu/tools/gradle-8.10.2/bin/gradle --offline --no-daemon --max-workers=1 :app:testOfflineDebugUnitTest
/home/ubuntu/tools/gradle-8.10.2/bin/gradle --offline --no-daemon --max-workers=1 :app:assembleOnlineDebug :app:lintOnlineDebug
/home/ubuntu/tools/gradle-8.10.2/bin/gradle --offline --no-daemon --max-workers=1 :app:assembleOfflineDebug :app:lintOfflineDebug
python3 scripts/validate_word_reference_content.py --reference app/src/main/assets/quran/word_reference_ayahs.json
python3 scripts/validate_word_reference_layout.py
python3 scripts/validate_pronunciation_reference.py --reference app/src/main/assets/quran/pronunciation_reference.json
python3 scripts/verify_word_reference_preview.py
python3 scripts/validate_full_quran_assets.py --mp3-dir ../quran-husary-mp3-source
python3 scripts/verify_offline_audio_bundle.py
./scripts/verify_offline_apk.sh app/build/outputs/apk/offline/debug/app-offline-debug.apk
```

عند اختلاف أي نتيجة، أوقف release وسجل الأمر ومخرجاته في ملف evidence جديد بدلاً من تحويل `deferred` إلى `passed` يدوياً.

## المراجع

[1]: https://developer.android.com/reference/android/speech/SpeechRecognizer "Android SpeechRecognizer"
[2]: https://qul.tarteel.ai/resources/recitation/112 "QUL Husary recitation source"
[3]: https://github.com/GovarJabbar/Quran-PNG/tree/master "Quran-PNG external reference"
