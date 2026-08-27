# Source lock — Online وOffline

هذه الوثيقة تسجل المدخلات المقفلة فعلياً للبناء. النص الحاكم هو ناتج ملف Word المقدم من المستخدم، والتوقيتات مرتبطة بمسار Husary QUL المستخدم في Online. لا يساوي توثيق المصدر إثبات حق إعادة التوزيع.

## الأصول المقفلة

| المكوّن | المدخل/المصدر | الدور | الإصدار/الحالة |
| --- | --- | --- | --- |
| النص العثماني | `reference-input/UthmanicHafs_V20.doc` → `app/src/main/assets/quran/word_reference_ayahs.json` | 6236 آية و77430 كلمة مع Word token الحرفي وحقول المقارنة | SHA-256 canonical: `8c69e9b9112f1915ed784248a6318ed51d7d152df8f6d3a6de00ffae4698775a`; حق إعادة التوزيع غير مثبت |
| تخطيط Word | `app/src/main/assets/quran/word_reference_layout.json` | 604 صفحة و9048 سطراً للعرض RTL | مصدر Word؛ يعرض `line.text` دون إعادة كتابة |
| preview | `word_reference_preview.json` و`word_reference_preview_layout.json` | الصفحات 1–5، 36 آية، لعرض سريع قبل full load | مشتق آلياً من canonical وليس مصدراً ثانياً |
| حقول النطق | `spokenForm`, `spokenTokens`, `recognizedForms`, `waqfForms`, `maddPositions`, `isMuqattaat` داخل canonical | expected وصيغ ASR والوقف والمد والحروف المقطعة | read-only في runtime؛ build-time بواسطة `rebuild_word_match_text.py` |
| metadata | `app/src/main/assets/quran/quran-meta-data.xml` | السور والآيات والصفحات والأجزاء والأحزاب والعلامات التنظيمية | Tanzil CC BY metadata؛ لا تستبدل Word canonical |
| خط KFGQPC | `app/src/main/res/font/kfgqpc_hafs_uthmanic_script.ttf` | الرسم المرئي العثماني | SHA-256 `27a0727ef2f644da13b5b254befb85a7f3539d095a94caaceea2079cf5d7be5a`; EULA/إذن التضمين التجاري غير مثبت |
| توقيتات QUL | `app/src/main/assets/quran/audio_timing/Husary_Murattal.json` | نوافذ الآيات والكلمات للتصحيح والقراءة | 6236 آية، 77430 نافذة canonical؛ SHA-256 `ea24ab0a10c7d6f595946bbda777e968746684560f738088a83016a464b346d9`; build-time من QUL |
| صوت الحصري Online | `https://audio-cdn.tarteel.ai/quran/husary/{surah:03d}{ayah:03d}.mp3` | قراءة وتصحيح Online | retrieval 2026-08-27؛ لا يثبت CDN وحده ترخيص إعادة التوزيع |
| صوت الحصري Offline | `app/src/offline/assets/quran/audio/husary_aac/*.m4a` | AAC-LC/M4A مشتقة من نفس MP3 المسار المستخدم في Online، داخل APK Offline | 6236 ملفاً، `1,274,873,643` bytes، manifest المصدر/المشتق في `husary_manifest.json` و`husary_aac_manifest.json` |
| مولد الصوت | `scripts/download_husary_audio.py` و`scripts/download_husary_audio_parallel.sh` و`scripts/transcode_husary_audio_offline.py` | تنزيل MP3 المصدر، استئناف، تحويل AAC وحساب manifests | M4A مشتقة 64kbps؛ MP3 المصدر خارج APK |
| نموذج Offline | `app/src/offline/assets/models/fastconformer-quran-ar-onnx-int8.zip` | FastConformer local ASR | SHA-256 `d87fa3c28bbd52f2ca0890a42ba7a39d242ef98977d5b9dd4ddd80ed005f7b1b`; أصل release v0.1.0 |
| sherpa AAR | `app/libs/sherpa-onnx-1.13.6.aar` | runtime Offline فقط | SHA-256 `01e87037afca2ed49085062aace5c012e60321e8e23e3a72b6d9ac02c843f66c`; الحجم 37,751,439؛ Apache-2.0 للمكتبة |
| ONNX Runtime | `libonnxruntime.so` داخل AAR | native dependency | ONNX Runtime 1.27.1 المكتشف سابقاً؛ MIT؛ إشعار مستقل داخل `third_party_notices` |
| إشعارات الحقوق | `third_party_notices/` و`app/src/main/assets/third_party_licenses/` | إسناد وبيان unresolved rights داخل APK | لا تمنح الإشعارات إذناً للأصول الجوهرية |
| مراجع الصور | Quran-PNG وquran.com-images | مقارنة بنيوية خارجية فقط | لا تُشحن الصور أو أصول الخط من هذه المشاريع |

## فصل الإصدارين

| الخاصية | Online | Offline |
| --- | --- | --- |
| flavor flag | `BuildConfig.OFFLINE_DISTRIBUTION=false` | `BuildConfig.OFFLINE_DISTRIBUTION=true` |
| ASR | `OnlineSpeechSession` | `LocalRecitationSession` من source set Offline |
| AAR sherpa | غير مضاف إلى variant | `offlineImplementation` فقط |
| audio | HTTPS عند الحاجة | `AssetFileDescriptor` من M4A مشتقة داخل APK |
| model | غير موجود | ZIP داخل APK، يُستخرج إلى `filesDir` محلياً |
| `INTERNET` | موجود | محذوف بواسطة `app/src/offline/AndroidManifest.xml` |
| خيارات الواجهة | Online فقط | Offline فقط؛ لا fallback شبكي |

## سياسة runtime

يقرأ `QuranRepository` و`PronunciationReference` البيانات المسبقة من canonical المحلي. لا ينشئ runtime صيغة expected جديدة ولا يستخدم `quran_content.json` مصدراً للنص. `RecognitionUpdate.Text.providerText` هو candidate القادم من provider، وهو المدخل الوحيد للمقارنة والعرض الخام.

`QuranViewModel` يقرأ preview أولاً في الخلفية لعرض الصفحة الأولى، ثم يقرأ full layout والنص والتوقيتات ويضع `assetsReady` بعد اكتمال التحقق. لا تُجلب بيانات النص أو metadata من الشبكة.

`ReferenceAudioPlayer` يشغل URL/path في Online، ويفتح `AssetFileDescriptor` في Offline. تصحيح كلمة واحدة يستعمل window QUL للكلمة مع padding داخل نصف الصمت المتاح بحد أقصى 120ms لكل طرف، ولا يمدد إلى كلمة مجاورة عند وجود فجوة غير كافية.

## أذونات وحقوق

Online يحتاج `RECORD_AUDIO` و`INTERNET`. Offline يحتاج `RECORD_AUDIO` فقط. كلاهما يخزّن sessions وcheckpoints وerrors وsettings محلياً، و`allowBackup=true` يحتاج سياسة منتج قبل النشر.

تراخيص MIT وApache-2.0 وCC BY وEPL الموثقة تخص مكونات معينة. لا توجد في هذا المستودع وثيقة كافية تثبت حق إعادة توزيع Word أو خط KFGQPC أو تسجيل الحصري أو segments المشتقة. لذلك حالة الإصدارين **internal testing only** إلى حين إذن صريح يغطي Android embedding وإعادة التوزيع والبث والتخزين والاستخدام التجاري والتوقيتات المشتقة.

## أوامر التحقق

```bash
python3 scripts/verify_word_text_unchanged.py
python3 scripts/verify_word_audio_timings.py
python3 scripts/verify_word_reference_preview.py
gradle :app:testOnlineDebugUnitTest :app:testOfflineDebugUnitTest --offline --no-daemon --max-workers=1
gradle :app:assembleOnlineDebug :app:assembleOfflineDebug --offline --no-daemon --max-workers=1
gradle :app:lintOnlineDebug :app:lintOfflineDebug --offline --no-daemon --max-workers=1
git diff --check
```

## بوابة APK

```bash
aapt dump permissions app/build/outputs/apk/online/debug/app-online-debug.apk
unzip -l app/build/outputs/apk/online/debug/app-online-debug.apk | grep -E 'mp3|fastconformer|sherpa|libonnxruntime' || true
aapt dump permissions app/build/outputs/apk/offline/debug/app-offline-debug.apk
unzip -l app/build/outputs/apk/offline/debug/app-offline-debug.apk | grep -E 'mp3|fastconformer|sherpa|third_party_licenses'
```

Online يجب ألا يحتوي MP3 أو model أو sherpa native. Offline يجب ألا يحتوي `android.permission.INTERNET`، ويجب أن يحتوي M4A والنموذج والنص والتوقيتات والإشعارات.

## حالة النشر

بناء APKs واختبارهما يثبتان صحة artifact فقط. اختبار الهاتف وADB وMediaPlayer وinference Offline وSpeechRecognizer وغياب الشبكة يجب أن يسجل منفصلاً. نشر GitHub مؤجل إذا أعاد `gh auth status` حالة 401؛ لا تُخزن credentials داخل هذا الملف.
