# AI_CONTEXT — Quran Memorization Online / Offline

## 1. الهوية والحدود

هذا مستودع تطبيق Android عربي لحفظ وتسميع القرآن وقراءة صفحات المصحف بالنص العثماني المشتق حرفياً من ملف Word المرجعي. الحزمة هي `com.example.quranoffline`، والواجهة Kotlin/Jetpack Compose، والحد الأدنى Android 26، و`compileSdk/targetSdk` يساويان 35. يدعم المشروع variantين مستقلين: `online` للتعرف عبر Android `SpeechRecognizer`، و`offline` للتعرف المحلي عبر sherpa-onnx/FastConformer والصوت المضمّن.

> **القاعدة الذهبية:** `line.text` والنصوص والرموز من Word canonical هي مصدر العرض. لا تُعدّل طبقة العرض النص الحاكم لتناسب نتيجة التعرف، ولا تُدخل صور Quran-PNG أو quran.com-images في APK.

هذه النسخة ليست تصريحاً تجارياً أو عاماً بإعادة التوزيع. حقوق تضمين Word، وخط KFGQPC، وتسجيل محمود خليل الحصري، وبيانات التوقيت المشتقة، ما زالت تحتاج إذناً صريحاً قابلاً للإثبات. حالة التوزيع الحالية هي **internal testing only**.

## 2. النتيجة الناجحة الأولى

من جذر المستودع يمكن بناء واختبار نسخة Online محلياً بعد ضبط JDK 21 وAndroid SDK 35:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_SDK_ROOT=/home/ubuntu/tools/android-sdk
/home/ubuntu/tools/gradle-8.10.2/bin/gradle :app:testOnlineDebugUnitTest --no-daemon
```

النتيجة المثبتة في هذه الجولة هي `BUILD SUCCESSFUL` لاختبارات Online وOffline. هذا لا يثبت تشغيل الميكروفون على هاتف، ولا يثبت سلامة مزود SpeechRecognizer أو inference المحلي على كل الأجهزة.

## 3. خريطة المستودع والطبقات

| المسار | المسؤولية الفعلية | العقد أو الاختبار المرتبط |
|---|---|---|
| `app/build.gradle.kts` | SDK، Compose، flavor dimensions، اعتماد sherpa في Offline فقط | assemble/test variantين |
| `app/src/main/AndroidManifest.xml` | `INTERNET` و`RECORD_AUDIO` للـmain source set | فحص aapt |
| `app/src/offline/AndroidManifest.xml` | إزالة `INTERNET` بواسطة `tools:node="remove"` | فحص Offline APK |
| `app/src/main/assets/quran/word_reference_ayahs.json` | النص الحاكم والكلمات و`matchText` | `verify_word_text_unchanged.py` |
| `app/src/main/assets/quran/pronunciation_reference.json` | expected spoken tokens وmadd وبيانات ربط الصوت | اختبارات matcher/حجم 6236 |
| `app/src/main/assets/quran/word_reference_layout.json` | 604 صفحة ومواضع/أسطر Word | `verify_word_reference_preview.py` |
| `app/src/main/assets/quran/audio_timing/Husary_Murattal.json` | توقيت QUL للكلمات والآيات | `verify_word_audio_timings.py` وتدقيق الحدود |
| `app/src/main/assets/quran/quran-meta-data.xml` | metadata التنظيمية للسور والآيات والصفحات والأجزاء | `QuranMetadataRepository` |
| `app/src/main/java/.../data/QuranRepository.kt` | قراءة Word canonical إلى `QuranAyah/QuranWord` | word text tests |
| `.../PronunciationReference.kt` | قراءة asset pronunciation التفصيلي، لا قراءة provider output | pronunciation tests |
| `.../RecitationMatcher.kt` | مطابقة كلمة متوقعة مع observed forms وقواعد الرسم/الوقف | matcher tests |
| `.../RecitationProgressTracker.kt` | state machine للتسميع العام، barrier، correction، pauses | tracker tests |
| `.../OpeningBasmala.kt` | كشف البسملة الافتتاحية وعزلها عن ayah list | `OpeningBasmalaTest` |
| `.../OpeningBasmalaProgressTracker.kt` | tracker مستقل للبِسملة باستخدام كلمات 1:1 دون إدخالها في allAyahs | omitted-middle regression |
| `.../OnlineSpeechSession.kt` | جلسة Android SpeechRecognizer وتراكم partial/final وإعادة التشغيل | hardware only for provider behavior |
| `app/src/offline/.../LocalRecitationSession.kt` | AudioRecord + sherpa local، لا شبكة | Offline device gate |
| `.../ReferenceAudioPlayer.kt` | MediaPlayer، AssetFileDescriptor، queue، word padding، clamp duration | audio boundary review |
| `.../TimingWordMapper.kt` | تحويل timing إلى word/whole-ayah windows | `TimingWordMapperTest` |
| `app/src/main/java/.../WordReferenceLineView.kt` | عرض `line.text` وتلوين spans فقط | canonical text invariant |
| `app/src/main/java/.../MainActivity.kt` | سطح صفحة المصحف والـCompose interactions | visual/device verification |
| `scripts/` | توليد timing/audio manifests وتدقيق Word والصوت | release gates |
| `docs/` | provenance، حقوق، تدقيقات، matrix، release notes | cross-document validation |

## 4. البناء والفصل بين الإصدارين

| الخاصية | Online | Offline |
|---|---|---|
| Gradle flavor | `online` | `offline` |
| application id suffix | `.online` | `.offline` |
| `OFFLINE_DISTRIBUTION` | `false` | `true` |
| ASR | `OnlineSpeechSession` / Android provider | `LocalRecitationSession` / sherpa-onnx |
| sherpa AAR | غير مضاف إلى variant | `offlineImplementation` |
| النص والتوقيتات | assets محلية | assets محلية |
| صوت الحصري | HTTPS عند الحاجة أو cache بحسب mode | M4A داخل APK عبر `AssetFileDescriptor` |
| model | غير موجود | ZIP داخل APK، يُستخرج إلى `filesDir` |
| INTERNET | موجود من main manifest | محذوف في Offline manifest |
| RECORD_AUDIO | موجود | موجود |

`online` هنا ليس مجرد APK فارغ: قاعدة النص/التخطيط/timing/pronunciation محلية، بينما صوت القراءة وprovider ASR هما الحدّ الخارجي. `offline` يعيد توزيع صوت M4A والنموذج فعلياً داخل APK، ولذلك وضع الحقوق فيه أشد حساسية.

## 5. العقود والحالة والبيانات

### 5.1 النص والتخطيط

`word_reference_ayahs.json` يحتوي 6236 آية و77430 كلمة. كل Word يحافظ على `text` العثماني و`matchText` المخصص للمقارنة؛ asset الحالي لا يُفترض أن يحتوي حقول pronunciation التفصيلية. `word_reference_layout.json` يحفظ خطوط الصفحة. `word_reference_preview.json` و`word_reference_preview_layout.json` مشتقان للصفحات 1–5 وعددها 36 آية لتقديم واجهة مبكرة.

`QuranViewModel` لا يضع `assetsReady=true` إلا بعد تحميل النسخة الكاملة والتحقق من compatibility. preview يعرض صفحة أولى فقط عندما لا توجد القائمة الكاملة، ثم يستبدلها بالمصدر الكامل. القراءة لا تستخدم صور Quran-PNG كأصل runtime.

### 5.2 طبقة النطق

`PronunciationReference.load()` يقرأ `quran/pronunciation_reference.json`، وليس Word JSON. هذا asset التفصيلي يحتوي 6236 صفاً وحقولاً مثل `spoken`, `spokenTokens`, `maddPositions`, وحقول audio-word linkage. هو expected reference مشتق build-time، وليس ناتج SpeechRecognizer. النتيجة الخام من provider تبقى في `RecognitionUpdate.Text.providerText`/`value` ولا تُستبدل بهذه الطبقة قبل الحكم.

### 5.3 توقيتات وصوت الحصري

`Husary_Murattal.json` ناتج build-time من QUL recitation 20/recitation 112 بحسب script ومثبت على 6236 آية، و77430 نافذة canonical، و77897 segment خام، منها 366 segment غير كلمة محفوظة للتدقيق. لا يعيد runtime تقسيم segment أو تخمينه.

مجلد MP3 المصدر الخارجي الذي لا يدخل Git ولا APK هو:

```text
../quran-husary-mp3-source/*.mp3
```

أما M4A المشتقة التي تدخل Offline APK عند البناء فهي:

```text
app/src/offline/assets/quran/audio/husary_aac/*.m4a
```

تدقيق 2026-08-27 أثبت وجود 6236 MP3 و6236 M4A، وإمكانية `ffprobe` لكل ملف. سجل التدقيق الكامل هو `docs/HUSARY_AUDIO_BOUNDARY_AUDIT_2026-08-27.json`. ظهرت مخالفات metadata في 228 صفاً عند مقارنة آخر QUL cue بمدة M4A، وفي 140 صفاً بمدة MP3، كما ظهرت 114 آية يبدأ أول cue فيها بعد 1000ms. هذه ليست دليلاً وحدها على نقص النطق؛ لذلك لم تُعدّل التوقيتات بصمت.

فحص ASR محدود موثق في `docs/HUSARY_AUDIO_SAMPLE_ASR_2026-08-27.md`: `001001.mp3` أعاد «بسم الله الرحمن الرحيم» من بداية الملف تقريباً، و`002015.mp3` أعاد أول كلمة مع بداية صوتية قبل cue الأول البالغ 1330ms. هذه العينة تدعم إصلاح offset، لكنها لا تثبت اكتمالاً لغوياً لكل 6236 آية.

## 6. دورة التسميع والحكم

`OnlineSpeechSession` يعيد contract النسخة العاملة السابقة: partial لا ينهي الجلسة، وfinal يُضاف إلى `committedText`، وبعد النتائج أو أخطاء provider العابرة يعاد فتح segment تلقائياً. `value` هو النص التراكمي الخام الذي يمر إلى state machine، و`providerText` هو أحدث candidate خام للعرض؛ لا تُضاف canonical spelling إلى observed text. لم تُضف extras جديدة خارج extras النسخة العاملة السابقة؛ يلزم اختبار الهاتف لإثبات سلوك provider الحقيقي.

`RecitationProgressTracker` يطابق الكلمة في موضعها لا بمجرد وجودها في الجملة. عند missing أو skipped word يثبت correction barrier، وتبقى الكلمات اللاحقة `UNCERTAIN` حتى تُصحح الكلمة الأولى الخاطئة. pause أو partial غير النهائي لا ينهي جلسة الحكم. سجل الأخطاء يحفظ عبر `LocalRecitationErrorStore`.

البِسملة الافتتاحية عنصر مستقل لكل سورة ما عدا الفاتحة والتوبة وفق `QuranPresentation`. لا تُضاف إلى `allAyahs`. عند الاشتباه ببداية البسملة، تمر النتيجة إلى `OpeningBasmalaProgressTracker` الذي يستخدم صف 1:1 كمصدر كلمات فقط ويعيد ترميز سجل الخطأ على السورة الفعلية. اختبار `omittedMiddleWordCreatesBarrierAndLeavesLaterWordUncertain` يثبت أن `بسم الله الرحيم` ينتج `الرحمان=MISSING` و`الرحيم=UNCERTAIN` ولا يمرر الرحيم إلى الآية.

## 7. دورة تشغيل الصوت

`TimingWordMapper.wholeAyah()` يعيد window كاملة تبدأ عند `0`، مع نهاية آخر cue المتاح. `QuranViewModel` يرسل `startMs=0` في queue الخاصة بقراءة الآية/السورة/القرآن والبِسملة؛ لا يُستخدم first word cue لقص القراءة الكاملة. تصحيح كلمة منفردة لا يزال يستخدم cue الخاص بالكلمة وpadding gap-aware بحد أقصى 120ms داخل الصمت ولا يعبر كلمة مجاورة.

`ReferenceAudioPlayer` يقرأ مدة `MediaPlayer` بعد prepare ويقيد start/end إليها. هذا يعالج فروق حاوية AAC وQUL integer cues مثل نهاية 1:1 التي تتجاوز مدة الملف ببضعة milliseconds، من دون تعديل JSON. Offline للبِسملة يمرر `assetPath` إلى M4A المضمّن، بينما Online يستخدم cache/URL.

## 8. إصلاحات الأداء والـrace

تمت معالجة المصادر المحددة من الكود كما يلي:

| المشكلة | الإجراء | حدود الإثبات |
|---|---|---|
| إعادة parsing أو انتظار كل الأصول قبل ظهور UI | preview pages 1–5 أولاً على `Dispatchers.IO`، ثم full load | يلزم قياس هاتف فعلي |
| قراءة full assets بالتتابع | allAyahs، metadata، layout، pronunciation، timing، وmodel tasks مستقلة داخل `coroutineScope`/`async(Dispatchers.IO)` ثم validation قبل UI | الاختبارات تثبت compile فقط |
| `flatMap` لكل 77430 كلمة في كل callback | `QuranUiState.totalWordCount` يحسب أثناء التحميل ويستخدم في cursor bounds | لا يوجد benchmark device بعد |
| بحث `allAyahs.firstOrNull` لكل Word line | خرائط `ayahsByKey` وindex وoffset تُبنى مرة في `MushafPageSurface` وتُمرر إلى السطور | يحافظ على line.text |
| إنشاء خرائط كبيرة داخل كل line | أزيل من `WordReferenceLineView`؛ الصفحة هي مالكة cache | يلزم profile Compose |
| race بين preview وfull load | `loadAssets` ينتظر `previewLoadJob` ويضع full state بعد validation؛ jobs السابقة تُلغى عند تغيير mode | يلزم اختبار lifecycle على الهاتف |

لا يصح وصف الأداء بأنه مُثبت 100% على جهاز المستخدم: لا توجد في هذه الجولة نتيجة ADB أو benchmark startup أو profile frame-time. المثبت هو إزالة عمليات O(n) الواضحة ونقل القراءة الثقيلة خارج Main حسب المصدر والاختبارات.

## 9. Git ونقطة quran V1

النقطة المرجعية المحلية المطلوبة محفوظة باسم tag annotated `quran-v1` وتشير إلى commit `1c7d2dd` برسالة `quran V1 snapshot before audio boundary fixes`. لا ينبغي دفعها قبل إعادة فحص reachable objects لأن محاولة سابقة أنشأت pack صوتياً ضخماً ثم أُعيد reset؛ MP3 المصدر خارج repository وM4A ignored. العمل الحالي غير ملتزم بعد ويحتوي إصلاحات ما بعد V1 وتقارير التدقيق.

قبل أي push يجب تشغيل:

```bash
git status --short
git log -3 --oneline --decorate
git show --stat quran-v1
git ls-tree -r --name-only quran-v1 | grep 'husary_aac/.*\.m4a' || true
git rev-list --objects --all > /tmp/quran-reachable-objects.txt
# ثم افحص أحجام objects، ولا تدفع إذا ظهرت blobs صوتية ضخمة في refs القابلة للدفع.
```

لا تُخزن MP3/M4A أو APK داخل Git history. التوزيع الكبير، إن سمحت الحقوق، يكون Release asset خارجي لا commit.

## 10. الاختبارات والبوابات

الأوامر التالية هي بوابات المصدر الحالية، وتُشغّل من جذر المستودع مع `JAVA_HOME` و`ANDROID_SDK_ROOT` أعلاه:

```bash
python3 scripts/validate_word_reference_content.py --reference app/src/main/assets/quran/word_reference_ayahs.json
python3 scripts/validate_word_reference_layout.py
python3 scripts/validate_pronunciation_reference.py --reference app/src/main/assets/quran/pronunciation_reference.json
python3 scripts/verify_word_reference_preview.py
python3 scripts/validate_full_quran_assets.py --mp3-dir ../quran-husary-mp3-source
python3 scripts/verify_offline_audio_bundle.py
python3 scripts/audit_husary_audio_boundaries.py \
  --canonical app/src/main/assets/quran/word_reference_ayahs.json \
  --timing app/src/main/assets/quran/audio_timing/Husary_Murattal.json \
  --mp3-dir ../quran-husary-mp3-source \
  --m4a-dir app/src/offline/assets/quran/audio/husary_aac \
  --json-output docs/HUSARY_AUDIO_BOUNDARY_AUDIT_2026-08-27.json \
  --markdown-output docs/HUSARY_AUDIO_BOUNDARY_AUDIT_2026-08-27.md
/home/ubuntu/tools/gradle-8.10.2/bin/gradle :app:testOnlineDebugUnitTest :app:testOfflineDebugUnitTest --no-daemon
git diff --check
```

بعد البناء يجب استعمال `aapt dump permissions` و`unzip -l` للتحقق من: Online يحتوي `INTERNET` و`RECORD_AUDIO` ولا يحتوي M4A/model/sherpa native؛ Offline يحتوي `RECORD_AUDIO` ولا يحتوي `INTERNET` ويحتوي M4A/model/sherpa. يجب حفظ SHA-256 وحجم APK بعد البناء النهائي فقط.

## 11. ما هو مثبت وما هو مؤجل

| الحالة | النطاق | الدليل |
|---|---|---|
| مثبت | source compile/unit tests Online | `:app:testOnlineDebugUnitTest` نجح في هذه الجولة |
| مثبت | source compile/unit tests Offline | `:app:testOfflineDebugUnitTest` نجح في هذه الجولة |
| مثبت | 6236 audio containers present/probed | `HUSARY_AUDIO_BOUNDARY_AUDIT_2026-08-27.*` |
| مثبت | full-file reader starts at zero in source | `QuranViewModel` و`TimingWordMapperTest` |
| مثبت | omitted middle basmala word barrier | `OpeningBasmalaProgressTrackerTest` |
| مثبت | legal warning/internal-only wording | `third_party_notices/NOTICE.md` و`docs/source-lock.md` |
| deferred | ADB/hardware Online basmala and full-ayah test | لا يوجد جهاز ظاهر في هذه الجولة |
| deferred | Offline on compatible 64-bit phone with network disabled | لا يوجد device/inference run |
| deferred | full linguistic listening/ASR audit of all 6236 files | metadata audit + two controlled samples فقط |
| مثبت | final APK permissions/content/size/SHA | `docs/APK_CONTENT_AUDIT_2026-08-27.md` |
| مثبت محلياً | Git object audit | `docs/GIT_OBJECT_AUDIT_2026-08-27.md`؛ push/Release ما زال مؤجلاً للحقوق والمراجعة النهائية |

## 12. بروتوكول agent قبل التعديل

ابدأ بقراءة هذا الملف و`README.md` و`docs/source-lock.md` و`docs/VERIFICATION_MATRIX.md`. ابحث عن الاستدعاءات والاختبارات قبل تعديل contract. لا تغيّر `line.text` أو Word canonical. لا تخلط standalone basmala مع ayah list. لا تستبدل الحصري أو QUL لتجميل نتيجة. لا تغيّر SpeechRecognizer extras/lifecycle إلا مع دليل regression معزول. كل تعديل يجب أن يضيف أو يربط اختباراً، ثم يشغل variantين، ثم يحدّث مصفوفة verified/deferred. أي ادعاء بسلامة جهاز أو صوت حي أو ADB يجب أن يستند إلى سجل فعلي لا إلى build ناجح.

## المراجع

[1]: https://github.com/ysrg2003/quran-memorization-online "مستودع التطبيق الخاص"
[2]: https://qul.tarteel.ai/resources/recitation/112 "Quranic Universal Library — recitation source"
[3]: https://github.com/GovarJabbar/Quran-PNG/tree/master "Quran-PNG external visual reference"
[4]: https://github.com/quran/quran.com-images/tree/master "quran.com-images external reference"
[5]: https://developer.android.com/reference/android/speech/SpeechRecognizer "Android SpeechRecognizer documentation"
