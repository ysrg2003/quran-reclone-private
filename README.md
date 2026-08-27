# Quran Memorization Online / Offline

## ما هذا المشروع؟

هذا تطبيق Android عربي لقراءة المصحف بالنص العثماني المحفوظ من ملف Word المرجعي، ولحفظ وتسميع الآيات مع تصحيح محافظ على ترتيب الكلمات. العرض لا يستخدم صور المصحف داخل APK؛ بل يعرض `line.text` من Word مع خط KFGQPC المضمّن في المشروع. توجد نسختان منفصلتان من Gradle: `online` تعتمد على Android `SpeechRecognizer`، و`offline` تعتمد على sherpa-onnx/FastConformer والصوت المحلي.

> **مهم:** المشروع مرشح **للاختبار الداخلي فقط**. لم تُثبت وثائق صريحة تسمح بإعادة توزيع Word أو خط KFGQPC أو تسجيل محمود خليل الحصري أو بيانات توقيت QUL المشتقة تجارياً أو علنياً.

## ماذا ستحقق في أول تشغيل؟

بعد بناء APK مناسب، ستظهر صفحات Word الأولى 1–5 سريعاً من preview، ثم يكتمل تحميل النص والتخطيط والتوقيتات في الخلفية. يمكنك فتح السورة، التنقل RTL، إخفاء النص للتسميع، تشغيل الآية/السورة، واستخدام البسملة الافتتاحية كجملة مستقلة. في Online يحتاج صوت القراءة وSpeechRecognizer إلى الإنترنت. في Offline تكون قاعدة البيانات والصوت والنموذج داخل APK، ولا يحتوي Offline على صلاحية `INTERNET`.

## المتطلبات

| المتطلب | Online | Offline |
|---|---|---|
| JDK | 17+؛ الاختبارات الأخيرة استُخدمت مع JDK 21 | نفسه |
| Android SDK | platform/build tools 35 | نفسه |
| جهاز Android | مطلوب للتجربة الحية، مع Internet للتعرف/الصوت | جهاز 64-bit متوافق؛ يتطلب اختبار inference فعلياً |
| صلاحية runtime | `RECORD_AUDIO`، وتطبيق Online يعلن `INTERNET` | `RECORD_AUDIO` فقط |
| حجم الأصول | صغير نسبياً لأنه لا يحزم audio/model | كبير جداً بسبب 6236 M4A وmodel |

## خريطة المشروع

| المسار | وظيفته |
|---|---|
| `app/src/main/assets/quran/word_reference_ayahs.json` | Word canonical: 6236 آية و77430 كلمة |
| `app/src/main/assets/quran/word_reference_layout.json` | تخطيط 604 صفحة و9048 سطراً |
| `app/src/main/assets/quran/word_reference_preview*.json` | preview للصفحات 1–5، وعددها 36 آية |
| `app/src/main/assets/quran/pronunciation_reference.json` | expected spoken tokens وmadd وربط الصوت |
| `app/src/main/assets/quran/audio_timing/Husary_Murattal.json` | توقيت QUL للحصري: 6236 آية و77430 نافذة canonical |
| `app/src/main/assets/quran/quran-meta-data.xml` | metadata تنظيم السور والصفحات والأجزاء |
| `app/src/main/res/font/kfgqpc_hafs_uthmanic_script.ttf` | الخط المرئي المضمّن |
| `app/src/main/java/.../data/` | repository، matcher، trackers، sessions، audio player |
| `app/src/offline/assets/quran/audio/husary_aac/` | M4A Offline محلية، تدخل APK فقط؛ MP3 المصدر خارج repository |
| `scripts/` | توليد/تحقق Word وtiming وaudio manifests والتدقيق |
| `docs/` | provenance، حقوق، التقارير، matrix، release notes |
| `AI_CONTEXT.md` | السياق الهندسي الكامل للـagent/maintainer |

## البناء

نفّذ من جذر المستودع. إذا كان المسار مختلفاً على Windows، استبدل قيمة `JAVA_HOME` و`ANDROID_SDK_ROOT` بمساري JDK وSDK لديك.

### Online

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_SDK_ROOT=/home/ubuntu/tools/android-sdk
/home/ubuntu/tools/gradle-8.10.2/bin/gradle :app:assembleOnlineDebug --no-daemon
```

المخرج المتوقع:

```text
app/build/outputs/apk/online/debug/app-online-debug.apk
BUILD SUCCESSFUL
```

### Offline

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_SDK_ROOT=/home/ubuntu/tools/android-sdk
/home/ubuntu/tools/gradle-8.10.2/bin/gradle :app:assembleOfflineDebug --no-daemon
```

يتطلب Offline وجود `app/src/offline/assets/models/fastconformer-quran-ar-onnx-int8.zip` و6236 ملف M4A محلياً. MP3 المصدر للتدقيق/التحويل موجود خارج repository في `../quran-husary-mp3-source/`، ولا يدخل APK. الملفات الكبيرة لا تدخل Git history، بل تُجهز في بيئة البناء أو تُنشر كـRelease artifact عندما تسمح الحقوق.

إذا ظهر `SDK location not found`، تحقق من `ANDROID_SDK_ROOT` ووجود `platforms/android-35`. إذا ظهر خطأ `javac`، استخدم JDK كاملاً لا JRE. إذا ظهر نقص M4A/model في Offline، شغّل validators قبل إعادة البناء ولا تنشئ ملفات placeholder.

## السلوك المهم في التسميع

`RecognitionUpdate.value` هو النص التراكمي الخام القادم من SpeechRecognizer أو النموذج، و`providerText` أحدث candidate خام للعرض؛ لا يُستبدل observed بنص Word قبل الحكم. المطابقة موضعية: إذا حُذفت كلمة وسطية، تكون الكلمة المفقودة `MISSING` وتبقى الكلمات التالية `UNCERTAIN` حتى التصحيح، ولا تنتقل الجلسة إلى آية لاحقة قبل إزالة barrier. الأخطاء تحفظ محلياً في `LocalRecitationErrorStore`.

البسملة الافتتاحية مستقلة في جميع السور التي تحتاجها، ولا تُضاف إلى `allAyahs`. الفاتحة لها صفها القرآني الخاص، والتوبة لا تُفتتح ببسملة مستقلة. `OpeningBasmalaProgressTrackerTest` يثبت أن `بسم الله الرحيم` لا يجعل `الرحيم` صحيحة عند حذف `الرحمان`.

عند تشغيل آية كاملة يبدأ المشغل من offset صفر، لا من أول QUL word cue؛ هذا يمنع قص بداية الآية عندما يبدأ أول cue متأخراً. تصحيح كلمة منفردة يبقى مقيداً بنافذة QUL وبـgap-aware padding لا يتجاوز 120ms داخل الصمت. مدة `MediaPlayer` الفعلية تقيد النهاية إذا اختلفت مدة M4A/MP3 قليلاً عن cue integer.

## التحقق من المصدر والأصول

نفّذ من جذر المشروع:

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
python3 scripts/audit_husary_audio_boundaries.py \
  --canonical app/src/main/assets/quran/word_reference_ayahs.json \
  --timing app/src/main/assets/quran/audio_timing/Husary_Murattal.json \
  --mp3-dir ../quran-husary-mp3-source \
  --m4a-dir app/src/offline/assets/quran/audio/husary_aac \
  --json-output docs/HUSARY_AUDIO_BOUNDARY_AUDIT_2026-08-27.json \
  --markdown-output docs/HUSARY_AUDIO_BOUNDARY_AUDIT_2026-08-27.md
git diff --check
```

التدقيق الأخير فحص 6236 container وtiming rows. أثبت وجود 6236 MP3 و6236 M4A ونجاح `ffprobe` لكل ملف، لكنه وجد 228 صفاً تتجاوز فيها نهاية QUL مدة M4A و140 صفاً تتجاوز فيها مدة MP3، إضافة إلى 114 آية يبدأ أول cue فيها بعد 1000ms. هذه مخالفات metadata/حدود لا تثبت وحدها نقص النطق. فحص ASR محدود لـ`001001.mp3` و`002015.mp3` موثق في `docs/HUSARY_AUDIO_SAMPLE_ASR_2026-08-27.md`؛ لم يُستمع إلى كل الآيات.

## فحص APK والصلاحيات

```bash
AAPT=/home/ubuntu/tools/android-sdk/build-tools/35.0.0/aapt
$AAPT dump permissions app/build/outputs/apk/online/debug/app-online-debug.apk
unzip -l app/build/outputs/apk/online/debug/app-online-debug.apk | grep -E 'm4a|mp3|fastconformer|sherpa|libonnxruntime' || true
$AAPT dump permissions app/build/outputs/apk/offline/debug/app-offline-debug.apk
unzip -l app/build/outputs/apk/offline/debug/app-offline-debug.apk | grep -E 'm4a|mp3|fastconformer|sherpa|third_party_licenses'
```

Online المتوقع أن يحتوي `INTERNET` و`RECORD_AUDIO` وألا يحتوي M4A أو model أو sherpa native. Offline المتوقع ألا يحتوي `INTERNET`، وأن يحتوي `RECORD_AUDIO` وM4A وmodel وruntime المحلي. لا تسجل SHA أو الحجم في Release Notes إلا بعد final assemble ناجح.

## Git ونقطة quran V1

النقطة المحلية المطلوبة محفوظة باسم tag annotated `quran-v1` على commit `1c7d2dd` برسالة `quran V1 snapshot before audio boundary fixes`. لا تُعدّل هذا tag ولا تدفعه قبل فحص reachable objects؛ الصوت ignored ولا يجوز أن يكون في Git history.

```bash
git status --short
git log -3 --oneline --decorate
git show --stat quran-v1
git ls-tree -r --name-only quran-v1 | grep 'husary_aac/.*\.m4a' || true
git rev-list --objects --all > /tmp/quran-reachable-objects.txt
```

إذا ظهر blob صوتي ضخم في refs القابلة للدفع، أوقف الرفع وأعد بناء history نظيفاً من `origin/main`. لا تضع token أو credential في الملفات.

## الحقوق والخصوصية

راجع `third_party_notices/NOTICE.md` و`app/src/main/assets/third_party_licenses/` و`docs/source-lock.md`. وجود MIT/Apache/CC BY لمكوّن معين لا يمنح حقاً تلقائياً لإعادة توزيع Word أو الخط أو التسجيل أو timing المشتق. Online يرسل صوت المستخدم إلى provider النظام وفق إعداد الجهاز؛ Offline يعالج الصوت محلياً بعد توفر النموذج، لكن لم يُختبر inference Offline على هاتف في هذه الجولة.

## الحالة المثبتة والمؤجلة

| الحالة | الدليل |
|---|---|
| compile/unit tests للـOnline | نجحت `:app:testOnlineDebugUnitTest` |
| compile/unit tests للـOffline | نجحت `:app:testOfflineDebugUnitTest` |
| tracker يمنع تخطي كلمة وسط البسملة | `OpeningBasmalaProgressTrackerTest` |
| whole-ayah يبدأ من صفر في المصدر | `TimingWordMapperTest` وqueue في `QuranViewModel` |
| تدقيق كل containers/timing | `docs/HUSARY_AUDIO_BOUNDARY_AUDIT_2026-08-27.*` |
| ADB/اختبار ميكروفون/MediaPlayer حي | مؤجل؛ لا يُدّعى نجاحه |
| inference Offline مع الشبكة معطلة | مؤجل؛ لا يوجد جهاز مناسب في هذه الجولة |
| final APK hashes/size/permissions | مؤجل حتى final assemble/lint |
| GitHub push وRelease | مؤجل حتى object audit وfinal gates وقرار الحقوق |

## مراجع

[1]: https://qul.tarteel.ai/resources/recitation/112 "QUL Husary recitation source"
[2]: https://developer.android.com/reference/android/speech/SpeechRecognizer "Android SpeechRecognizer"
[3]: https://github.com/GovarJabbar/Quran-PNG/tree/master "Quran-PNG external reference"
[4]: https://github.com/quran/quran.com-images/tree/master "quran.com-images external reference"
