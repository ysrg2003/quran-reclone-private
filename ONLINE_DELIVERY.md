# Online Delivery

## الغرض وحدود التوزيع

`online` هو flavor Android مستقل للتسميع عبر `SpeechRecognizer` الموجود في Android. تبقى قاعدة النص العثماني والتخطيط وmetadata وطبقة pronunciation وتوقيتات Husary محلية داخل APK، بينما صوت الحصري عند القراءة أو التصحيح يُجلب عبر HTTPS عند الحاجة. هذا المستودع مرشح **للاختبار الداخلي فقط**؛ لا توجد clearance عامة أو تجارية للأصول الجوهرية.

## البناء من الصفر

نفّذ الأوامر التالية من جذر المستودع:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_SDK_ROOT=/home/ubuntu/tools/android-sdk
/home/ubuntu/tools/gradle-8.10.2/bin/gradle :app:assembleOnlineDebug --no-daemon
```

النجاح المتوقع هو `BUILD SUCCESSFUL` ووجود:

```text
app/build/outputs/apk/online/debug/app-online-debug.apk
```

إذا ظهر خطأ `SDK location not found`، تحقق من `ANDROID_SDK_ROOT` ومسار `platforms/android-35`. إذا ظهر خطأ Java، استخدم JDK يحتوي `javac` واضبط `JAVA_HOME` قبل إعادة الأمر.

## ما يدخل Online وما لا يدخل

| المكوّن | Online |
|---|---|
| Word canonical/layout/preview | موجود محلياً |
| `pronunciation_reference.json` | موجود محلياً |
| QUL Husary timing | موجود محلياً |
| MP3/M4A الحصري | غير محزم؛ يطلب URL عند الحاجة |
| local ASR model/runtime | غير موجود؛ يعتمد التسميع الصوتي على Android SpeechRecognizer |
| `INTERNET` | موجود |
| `RECORD_AUDIO` | موجود |

لا يحتوي Online على صوت الحصري أو أي محرك ASR محلي. وضع الكتابة متاح داخل التطبيق ويعمل محلياً باستخدام نفس `RecitationProgressTracker` المستخدم للتسميع الصوتي.

## التشغيل الأول

بعد تثبيت `app-online-debug.apk` وفتح التطبيق، تظهر واجهة preview للصفحات 1–5 أولاً، ثم يكتمل تحميل المصحف والتوقيتات في الخلفية. في وضع الصوت اختر بدء التسميع بعد منح `RECORD_AUDIO`؛ وقد يحتاج Android SpeechRecognizer إلى الإنترنت. صمت أو نفس قصير لا ينهي الجلسة: `OnlineSpeechSession` يراكم partial/final ويعيد فتح مقطع provider بعد النتائج أو الخطأ العابر. في وضع الكتابة اختر «بالكتابة»، ابدأ الجلسة، اكتب كلمة أو آية ثم اضغط «إرسال النص للتحقق» من دون ميكروفون. قد يعالج مزود Android الصوت خارج الجهاز، لذلك لا يوصف Online بأنه معالجة خاصة بالكامل.

عند قراءة آية كاملة، يبدأ المشغل من offset `0` حتى لا يقص صوتاً يسبق أول QUL cue. عند تصحيح كلمة منفردة، يستخدم المشغل نافذة الكلمة مع padding محدود داخل الصمت. البسملة الافتتاحية مستقلة ولا تُحسب من آيات السورة، ويجب تصحيح كلمة البسملة المفقودة قبل السماح بانتقال الكلمات اللاحقة.

## فحص APK

من جذر المشروع بعد البناء:

```bash
AAPT=/home/ubuntu/tools/android-sdk/build-tools/35.0.0/aapt
APK=app/build/outputs/apk/online/debug/app-online-debug.apk
$AAPT dump permissions "$APK"
unzip -l "$APK" | grep -E 'm4a|mp3|fastconformer|sherpa|libonnxruntime' || true
sha256sum "$APK"
stat -c '%s bytes' "$APK"
```

المتوقع هو وجود `android.permission.INTERNET` و`android.permission.RECORD_AUDIO`، وعدم ظهور M4A/MP3/model/sherpa native. احفظ hash والحجم في Release Notes فقط بعد اكتمال final assemble، لا تستخدم قيم artifact قديمة.

## الاختبارات

```bash
/home/ubuntu/tools/gradle-8.10.2/bin/gradle :app:testOnlineDebugUnitTest --no-daemon
python3 scripts/validate_word_reference_content.py --reference app/src/main/assets/quran/word_reference_ayahs.json
python3 scripts/validate_word_reference_layout.py
python3 scripts/validate_pronunciation_reference.py --reference app/src/main/assets/quran/pronunciation_reference.json
python3 scripts/verify_word_reference_preview.py
python3 scripts/validate_full_quran_assets.py --mp3-dir ../quran-husary-mp3-source
git diff --check
```

تثبت unit tests صحة المصدر والـstate machine، ومنها حاجز وضع الكتابة عند تخطي كلمة، لكنها لا تثبت عمل microphone provider على هاتف بعينه. يلزم اختبار حي منفصل للبِسملة، omitted `الرحمان`، توقف النفس، بداية الآية من الكلمة الأولى، و`ERROR_NO_MATCH`.

## الصوت والمصادر

مصدر التوقيتات هو QUL Husary المقفل في `app/src/main/assets/quran/audio_timing/Husary_Murattal.json`. مصدر URL المستخدم runtime هو `https://audio-cdn.tarteel.ai/quran/husary/{surah:03d}{ayah:03d}.mp3`. تدقيق الحدود الكامل في `docs/HUSARY_AUDIO_BOUNDARY_AUDIT_2026-08-27.md` يصف ما أثبتته metadata وما بقي يحتاج استماعاً أو ASR. مصدر MP3 للتدقيق/التحويل هو `../quran-husary-mp3-source/` خارج repository، بينما M4A فقط تدخل Offline APK. لا تغيّر URL أو reciter لتجاوز anomaly، ولا تحول أداة التنزيل أو وجود CDN إلى إذن إعادة توزيع.

## استكشاف الأخطاء

| العرض | التفسير المرجح | الإجراء |
|---|---|---|
| لا يظهر نص الصفحة الأولى | preview أو asset parse فشل | راجع notice ثم شغّل validators؛ full load هو المصدر النهائي |
| `ERROR_NO_MATCH` | provider أنهى segment بلا نتيجة | contract السابق يعيد الاستماع تلقائياً ما دام المستخدم لم يضغط إيقافاً |
| الصوت يبدأ بعد أول كلمة | seek استُخدم كـword window | تحقق أن مسار القراءة الكاملة يرسل `startMs=0`؛ لا تعدّل timing JSON |
| correction يعبر كلمة أخرى | padding غير gap-aware | راجع `withCorrectionPadding` وحد 120ms واختبار window |
| Download أو صوت/ASR محلي في Online | build variant أو مسار أصول خاطئ | استخدم `assembleOnlineDebug` وافحص `applicationId` ومحتوى APK |

## الحقوق والخصوصية

راجع `app/src/main/assets/third_party_licenses/NOTICE.md` و`docs/source-lock.md`. النص Word، خط KFGQPC، تسجيل الحصري، وتوقيتات QUL المشتقة هي نقاط مراجعة إلزامية قبل أي نشر عام أو تجاري. Online يطلب `INTERNET` و`RECORD_AUDIO` للتسميع الصوتي؛ صوت المستخدم يمر إلى provider Android حسب إعداد الجهاز، ولا يرفع التطبيق الصوت إلى خادم خاص به.

## المراجع

[1]: https://developer.android.com/reference/android/speech/SpeechRecognizer "Android SpeechRecognizer"
[2]: https://qul.tarteel.ai/resources/recitation/112 "QUL Husary recitation source"
[3]: https://github.com/GovarJabbar/Quran-PNG/tree/master "Quran-PNG external reference"
