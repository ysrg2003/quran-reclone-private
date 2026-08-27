# Source lock — Online وOffline

هذه الوثيقة تسجل المدخلات المقفلة فعلياً للبناء. النص الحاكم هو ناتج ملف Word المقدم من المستخدم، والتوقيتات مرتبطة بمسار Husary QUL. توثيق المصدر لا يساوي إثبات حق إعادة التوزيع.

## الأصول المقفلة

| المكوّن | المدخل/المصدر | الدور | الإصدار/الحالة |
|---|---|---|---|
| النص العثماني | `reference-input/UthmanicHafs_V20.doc` ثم `app/src/main/assets/quran/word_reference_ayahs.json` | 6236 آية و77430 كلمة مع Word token الحرفي و`matchText` | SHA-256 canonical: `8c69e9b9112f1915ed784248a6318ed51d7d152df8f6d3a6de00ffae4698775a`; حق إعادة التوزيع غير مثبت |
| تخطيط Word | `app/src/main/assets/quran/word_reference_layout.json` | 604 صفحة و9048 سطراً للعرض RTL | `line.text` يعرض دون إعادة كتابة |
| preview | `word_reference_preview.json` و`word_reference_preview_layout.json` | الصفحات 1–5، وعددها 36 آية | مشتق آلياً من canonical وليس مصدراً ثانياً |
| طبقة النطق | `app/src/main/assets/quran/pronunciation_reference.json` | 6236 expected ayah، و`spoken` و`spokenTokens` و`maddPositions` وربط segment | يقرأها `PronunciationReference.load()`؛ لا تحتوي provider output runtime |
| metadata | `app/src/main/assets/quran/quran-meta-data.xml` | السور والآيات والصفحات والأجزاء والأحزاب والعلامات التنظيمية | Tanzil metadata CC BY بحسب الإشعار؛ لا تستبدل Word canonical |
| خط KFGQPC | `app/src/main/res/font/kfgqpc_hafs_uthmanic_script.ttf` | الرسم المرئي العثماني | SHA-256 `27a0727ef2f644da13b5b254befb85a7f3539d095a94caaceea2079cf5d7be5a`; EULA/إذن التضمين التجاري غير مثبت |
| توقيتات QUL | `app/src/main/assets/quran/audio_timing/Husary_Murattal.json` | نوافذ الكلمات والآيات للتصحيح والقراءة | 6236 آية، 77430 نافذة canonical؛ SHA-256 `ea24ab0a10c7d6f595946bbda777e968746684560f738088a83016a464b346d9` |
| صوت الحصري Online | `https://audio-cdn.tarteel.ai/quran/husary/{surah:03d}{ayah:03d}.mp3` | قراءة وتصحيح Online | استُحضر 2026-08-27؛ CDN لا يثبت وحده ترخيص إعادة التوزيع |
| صوت الحصري Offline | `app/src/offline/assets/quran/audio/husary_aac/*.m4a` | AAC-LC/M4A مشتقة من MP3، داخل Offline APK عند البناء | 6236 ملفاً، `1,274,873,643` bytes بحسب manifest الحالي؛ خارج Git history |
| manifests | `husary_manifest.json` و`husary_aac_manifest.json` | hash/size للمصدر والمشتق | يعاد توليدها بالـscripts ولا تُعدّل يدوياً |
| نموذج Offline | `app/src/offline/assets/models/fastconformer-quran-ar-onnx-int8.zip` | FastConformer local ASR | SHA-256 سابق موثق في NOTICE/source-lock؛ يتطلب بوابة APK النهائية |
| sherpa AAR | `app/libs/sherpa-onnx-1.13.6.aar` | runtime Offline فقط | Apache-2.0 للمكتبة؛ لا يدخل Online variant |
| مراجع الصور | Quran-PNG وquran.com-images | مقارنة بنيوية خارجية للصفحات | لا تُشحن الصور أو أصول الخط منها |

## فصل الإصدارين

| الخاصية | Online | Offline |
|---|---|---|
| flavor flag | `BuildConfig.OFFLINE_DISTRIBUTION=false` | `BuildConfig.OFFLINE_DISTRIBUTION=true` |
| ASR | `OnlineSpeechSession` / Android provider | `LocalRecitationSession` من Offline source set |
| sherpa AAR | غير مضاف إلى variant | `offlineImplementation` فقط |
| audio | HTTPS أو cache بحسب mode | `AssetFileDescriptor` من M4A المضمّنة |
| model | غير موجود | ZIP داخل APK، يُستخرج إلى `filesDir` |
| `INTERNET` | موجود | محذوف بواسطة `app/src/offline/AndroidManifest.xml` |
| fallback | لا fallback إلى Offline | لا fallback شبكي |

## سياسة runtime

يقرأ `QuranRepository` Word canonical، ويقرأ `PronunciationReference` asset النطق التفصيلي المنفصل. لا ينشئ runtime expected spelling جديدة ولا يستخدم `quran_content.json` مصدراً للنص. `RecognitionUpdate.Text.value` هو النص التراكمي الخام للمقارنة، و`providerText` هو أحدث candidate خام للعرض؛ لا يُحوّل observed إلى Word canonical قبل الحكم.

يقرأ `QuranViewModel` preview أولاً في الخلفية لعرض الصفحة الأولى، ثم يقرأ النص الكامل والتخطيط والتوقيتات وطبقة النطق في مهام IO مستقلة، ويضع `assetsReady` بعد التحقق. لا تُجلب بيانات النص أو metadata من الشبكة.

`ReferenceAudioPlayer` يشغل URL/path في Online، ويفتح `AssetFileDescriptor` في Offline. تصحيح كلمة واحدة يستعمل window QUL للكلمة مع padding gap-aware بحد أقصى 120ms داخل نصف الصمت المتاح، ولا يمدد إلى كلمة مجاورة. قراءة الآية الكاملة ترسل `startMs=0` وتُقيد نهايتها بمدة `MediaPlayer` الفعلية حتى لا تقص first cue المتأخر ولا تتجاوز AAC duration.

## أذونات وحقوق

Online يحتاج `RECORD_AUDIO` و`INTERNET`. Offline يحتاج `RECORD_AUDIO` فقط. كلاهما يخزّن sessions وcheckpoints وerrors وsettings محلياً، و`allowBackup=true` يحتاج قرار منتج قبل أي توزيع عام.

التراخيص MIT وApache-2.0 وCC BY الموثقة تخص مكونات معينة. لا توجد وثيقة كافية تثبت حق إعادة توزيع Word أو خط KFGQPC أو تسجيل الحصري أو segments المشتقة. لذلك حالة الإصدارين **internal testing only** إلى حين إذن صريح يغطي Android embedding وإعادة التوزيع والبث والتخزين والاستخدام التجاري والتوقيتات المشتقة.

## أوامر التحقق

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

## بوابة APK

```bash
aapt dump permissions app/build/outputs/apk/online/debug/app-online-debug.apk
unzip -l app/build/outputs/apk/online/debug/app-online-debug.apk | grep -E 'm4a|mp3|fastconformer|sherpa|libonnxruntime' || true
aapt dump permissions app/build/outputs/apk/offline/debug/app-offline-debug.apk
unzip -l app/build/outputs/apk/offline/debug/app-offline-debug.apk | grep -E 'm4a|mp3|fastconformer|sherpa|third_party_licenses'
```

Online يجب أن يحتوي `INTERNET` و`RECORD_AUDIO` ولا يحتوي M4A أو model أو sherpa native. Offline يجب ألا يحتوي `android.permission.INTERNET`، ويجب أن يحتوي `RECORD_AUDIO` وM4A والنموذج وruntime المحلي.

## حالة النشر

نجاح unit test أو assemble يثبت artifact/source فقط. اختبار الهاتف وADB وMediaPlayer وinference Offline وSpeechRecognizer وغياب الشبكة يجب أن يسجل منفصلاً. لا تُرفع `quran-v1` قبل فحص reachable Git objects؛ ولا يُرفع Release عام أو تجاري قبل حل حقوق Word/KFGQPC/Husary/QUL المشتقة.

## مراجع

[1]: https://qul.tarteel.ai/resources/recitation/112 "QUL Husary recitation source"
[2]: https://github.com/GovarJabbar/Quran-PNG/tree/master "Quran-PNG external reference"
[3]: https://developer.android.com/reference/android/speech/SpeechRecognizer "Android SpeechRecognizer"
