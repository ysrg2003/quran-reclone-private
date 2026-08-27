# مصفوفة التحقق والإصدار

**المشروع:** `ysrg2003/quran-memorization-online`

**حالة المصدر عند كتابة هذه المصفوفة:** flavorان مستقلان؛ Online يستخدم SpeechRecognizer والصوت HTTPS، وOffline يضم FastConformer و6,236 ملف M4A مشتقاً من صوت الحصري. كل الأصول والـmanifests موثقة، لكن حقوق Word والخط والتسجيل والتوقيتات ما زالت unresolved للنشر العام/التجاري.

**آخر APK Online مبني من المصدر الحالي:** `app/build/outputs/apk/online/debug/app-online-debug.apk`

**حجم Online:** `23,996,078` بايت

**SHA-256 Online:** `cf8b52d41c675f1e3d514d08638ab1f5055aef61b4bf71e789549189c1ab94ff`

**آخر APK Offline مبني من المصدر الحالي:** `app/build/outputs/apk/offline/debug/app-offline-debug.apk`

**حجم Offline:** `1,467,401,640` بايت

**SHA-256 Offline:** `582f636a423e560668f546a8cc9e0dd06c2ebcc15cec0c785fa3cee8dc9ad3e5`

**اختبار الهاتف:** لم يُثبت أي من هذين artifactين على هاتف في هذه الجولة؛ ADB غير متصل. الاختبارات الحية للصوت، SpeechRecognizer، MediaPlayer، inference Offline، والعمل دون شبكة تبقى deferred.

## قرار الإصدار

اجتاز المصدر الحالي مدققات النص والتوقيتات وpreview وmanifest الصوت، واختبارات الوحدة، وassemble وlint للـflavorين. يثبت ذلك سلامة artifact والبيانات المضمنة، لا اختبار الهاتف أو الحقوق. Online لا يضم MP3/M4A أو نموذجاً أو sherpa native؛ Offline يضم 6,236 M4A والنموذج وsherpa ويحذف INTERNET. لا يثبت الإصداران التطابق البصري pixel-level ولا clearance قانونياً.

لا يعلن هذا الإصدار **تطابقاً بصرياً بكسلياً أو تطابقاً 100% لمواضع كل glyph**. التطبيق لا يشحن صور Quran-PNG ولا خطاً مرجعياً مطابقاً ذا حقوق واضحة، ويستخدم Compose لعرض النص. الدليل الحالي بنيوي، مع إشارات raster محدودة للأبعاد والنطاقات؛ أما الإحداثيات الدقيقة لكل glyph، شكل الحروف، المسافات، وعلامات أرقام الآيات داخل الصورة فلم تُثبت داخل واجهة التطبيق.

## الأدلة

| القدرة أو الحالة | نوع الدليل | النتيجة | الحالة |
|---|---|---|---|
| تحليل الأصول دون حجب واجهة Compose | بناء + فحص الكود الحالي | preview الصفحات 1–5 يظهر أولاً، ثم full load والتحقق في الخلفية؛ `assetsReady` يحجب التسميع قبل الاكتمال | مثبت برمجياً؛ اختبار الهاتف مؤجل |
| عرض سورة كاملة | اختبار هاتف سابق | سورة الفاتحة 1–7 وسورة البقرة 1–286 ظهرتا، ووصل التمرير إلى الآية 286 | مثبت للنسخة السابقة؛ إعادة اختبار الصفحة الجديدة على الهاتف مؤجل |
| التفاف كلمات الآية | اختبار هاتف سابق | `FlowRow` منع القص الأفقي في الآيات الطويلة | مثبت للنسخة السابقة؛ مراجعة الصفحة الجديدة على الهاتف مؤجلة |
| إخفاء النص وإظهار الكلمة الحالية | اختبار هاتف سابق + اختبار كود | تحولت الآيات إلى نقاط وظهرت كلمة SpeechRecognizer الحالية أثناء Online؛ وتظهر كلمات البسملة في مسارها المستقل أيضاً | مثبت برمجياً؛ اختبار الهاتف للإصدار الحالي مؤجل |
| استمرار Online بعد الصمت | اختبار هاتف سابق | بقيت الجلسة مستمرة بعد صمت ثماني ثوانٍ، ثم أمكن الإيقاف اليدوي | مثبت لمسار Online السابق |
| مطابقة صحيحة داخل الموضع | اختبار وحدة | تقدم موضعي وتقييم `MATCHED` | مثبت |
| كلمة خاطئة | اختبار وحدة + عقد المتعقب | طلب `WRONG_WORD` لموضع الكلمة المتوقعة مع مفتاح إزالة تكرار | مثبت برمجياً |
| تخطي كلمة وقفل أول خطأ | اختبار وحدة | `MISSING` للكلمة المتخطاة وطلب `SKIPPED_WORD` دون إزاحة المطابقة اللاحقة؛ لا يسمح resume بالبدء بعد أول خطأ غير محسوم، والبدء من الخطأ أو قبله فقط | مثبت برمجياً |
| قفز إلى آية غير التالية | اختبار وحدة | بقاء السياق على الآية التالية وطلب `FIRST_WORD_EXPECTED_AYAH` لأول كلمة منها | مثبت برمجياً |
| الرجوع داخل الآية | اختبار وحدة | البحث في نافذة رجوع داخل الآية وإعادة المطابقة دون انتقال حر إلى آية أخرى | مثبت برمجياً |
| الرجوع إلى الآية السابقة | اختبار وحدة + فحص الكود | زر «ابدأ من الآية السابقة» يبدأ جلسة جديدة من `currentAyahIndex - 1` ويصفر الحكم السابق ويمنع callbacks من الجلسة القديمة | مثبت برمجياً؛ اختبار الهاتف مؤجل |
| الرجوع التلقائي إلى الآية السابقة | اختبار وحدة + فحص الكود | الخيار مفعّل افتراضياً ومحفوظ محلياً؛ عند تأكيد أول كلمتين من الآية السابقة في بداية الآية الحالية يرجع المتعقب إليها دون زر، ويتجنب الحالة التي تطابق فيها الكلمة الأولى بداية الآية الحالية | مثبت برمجياً؛ اختبار الهاتف مؤجل |
| الكلمة الممدودة/المد اللازم | اختبار وحدة + فحص الكود | لا يُنشأ تصحيح من partial أو بادئة مستقرة مثل `الضال`؛ كل موضع يحمل علامة المد الظاهرة في Word يقبل 2 أو 4 أو 6 حركات، ولا يقبل 8؛ مهلة الصمت النهائية في SpeechRecognizer بقيت 2400ms | مثبت برمجياً؛ اختبار النطق الفعلي مؤجل |
| استئناف checkpoint | فحص `QuranViewModel.startListening` + اختبارات checkpoint | تُستخدم السورة والآية المحفوظتان لتحديد الموضع، ويُجبر `startWordIndex=0`؛ لا يبدأ الاستئناف من كلمة وسط الآية حتى لو كان checkpoint السابق عندها | مثبت برمجياً؛ اختبار الهاتف للإصدار الحالي مؤجل |
| إلزام البسملة بين السور | اختبار tracker + فحص ViewModel | عند اكتمال آخر آية والعبور إلى `ayah=1` من سورة غير التوبة، يتوقف callback قبل تقييم كلمات السورة الجديدة، وتبدأ بسملة مستقلة إلزامية؛ التوبة استثناء | مثبت برمجياً؛ اختبار الهاتف للإصدار الحالي مؤجل |
| مقاطع تصحيح كلمات الحصري | `verify_word_audio_timings.py` + `TimingWordMapperTest` | `Husary_Murattal.json`: 6236 آية و77430 نافذة canonical، 77897 raw segment، 366 حدثاً خارج نطاق الكلمات، و34 آية بها تكرارات؛ التصحيح يستخدم نافذة الكلمة داخل ملف آية الحصري فقط | مثبت بنيوياً؛ الحدود مولدة آلياً وتحتاج مراجعة سمعية للحالات المعلّمة |
| partial غير مستقر | اختبار وحدة | لا تقدم ولا تشغيل صوت قبل final/segment-complete | مثبت برمجياً |
| تسميع البسملة كلمة بكلمة | `OpeningBasmalaProgressTrackerTest` + فحص مسار ViewModel | البسملة خارج `allAyahs`، وتُقيّم كلماتها الأربع من Word؛ الخطأ يطلب كلمة واحدة فقط، وتبقى الكلمات اللاحقة UNCERTAIN حتى الإصلاح؛ `ayah=0` في سجل خطأ البسملة | مثبت برمجياً؛ اختبار الهاتف مؤجل |
| نتائج الحروف المقطعة المتتابعة | `MuqattaatTest` + `RecitationProgressTrackerTest` | `ألف` ثم `لام` ثم `ميم`، أو بقية الأسماء في callback واحد، تبقى prefix حتى تكتمل الكلمة المرئية ولا تُسجل خطأ مبكراً | مثبت باختبارات الوحدة |
| تشغيل مقطع التصحيح | فحص الكود + البناء | `QuranViewModel` يمرر آية الحصري ونافذة الكلمة إلى `ReferenceAudioPlayer`؛ يستخدم `SEEK_CLOSEST` ويوقف عند `endMs`، ولا يعيد آخر نافذة عند الغياب | مثبت بنيوياً؛ غير مثبت صوتياً لكل الحالات |
| سياسة تصحيح الشيخ وإعداداتها | تثبيت APK v10 + `quran-online-settings-v10-policy-card.png` | ظهرت بطاقة الإعدادات، والافتراضي المعروض 3 أخطاء متتالية، وتكرار واحد، وفاصل فوري، وهامش صفري، وترتيب مقطع أول خطأ ثم الآية | مثبت على الهاتف كواجهة سابقة |
| حفظ الإعدادات وإعادة الافتراضي | تفاعل ADB + `run-as` على APK v10 | تغيّرت العتبة إلى 4 وحُفظت في `quran_correction_settings.xml`، ثم أُعيدت إلى 3 بمسح الاختيارات المحفوظة | مثبت على الهاتف كواجهة سابقة |
| تغطية مصدر القرآن الكامل للمطابقة | `scripts/verify_word_text_unchanged.py` + مولد Word | فُحصت 6236 آية و77430 كلمة؛ كل كلمة تحمل الحقول المسبقة المطلوبة، مع بقاء أزواج `text`/`matchText` والنص المعروض من Word دون تغيير؛ أضيفت صيغة `اانذرتهم` موضعياً | مثبت كمصدر بيانات، وليس ضماناً لفهم SpeechRecognizer لكل نطق |
| metadata العربي للسور والآيات | `scripts/analyze_quran_metadata.py` + `QuranMetadataTest` | 114 سورة، 6236 آية، 30 جزءاً، 240 ربعاً، 7 منازل، 556 ركوعاً، 604 صفحات، و15 سجدة؛ الفهارس ونقاط البداية مرتبة | مثبت كمصدر وأداة تحقق |
| توافق metadata مع النص الكامل | `scripts/validate_quran_metadata_against_content.py` | `metadata-json-ayah-order-ok=6236`، و`metadata-boundaries-resolve-against-json=true`، و`english-sura-fields-ignored=true` | مثبت كمصدر بيانات |
| تسلسل الآيات لكل صفحة | `scripts/validate_quran_png_page_layout.py` | `metadata-ayahs-covered=6236/6236` و`page-ayah-sequences=604/604` مقابل خريطة SQL المستقلة | مثبت بنيوياً لكل الصفحات |
| عدد الأسطر التنظيمية لكل صفحة | `glyph_page_line` في quran.com-images | `image-layout-line-counts={'8': 2, '15': 602}`؛ الصفحتان 1 و2 ثماني أسطر، والصفحات 3–604 خمس عشرة | مثبت بنيوياً عبر الخريطة المستقلة |
| ملفات Quran-PNG وأبعادها | validator + Pillow على `/home/ubuntu/quran-png-reference` | `quran-png-files=604/604` و`quran-png-dimensions=604/2600x4206` | مثبت كمرجع محلي؛ الصور غير مشحونة |
| إشارات raster للصور | `scripts/analyze_quran_png_layout.py` + ملخص الإسقاط | الأبعاد `(2600,4206)` في 604/604، والشفافية في 604/604، لكن توزيع النطاقات 7:1، 8:1، 13:13، 14:83، 15:494، 16:12 | مثبت كإشارة محدودة؛ غير كافٍ لإثبات النص أو glyph placement |
| خريطة glyph/word المرجعية | SQL المستخرج من quran.com-images | 77430 كلمة، 88246 صف glyph-ayah، و565 glyph تنظيمية غير مرتبطة بكلمة؛ الكلمات القرآنية الـ77430 مطابقة raw للمصدر بعد المواءمة، والفرق 448 token هو بسملات عرضية | مثبت كمرجع تحليل؛ لا تُضمّن الخريطة الخام 21MB في APK |
| تقطيع الصفحة داخل التطبيق | `QuranPageLayout` + `QuranMetadataTest` | طبقة اختبارية مستقلة تحسب الآيات المرئية، أول فهرس عالمي، الصفحة من الفهرس، و`pageWordOffset` | مثبت برمجياً |
| تحديث cache النص بعد إصدار الأصول | `RemoteAssetManager` + `TEXT_ASSET_VERSION=2026.08.24.2` | يعيد تنزيل النص المحاذى بالكامل عند اختلاف الإصدار، ويحافظ على cache القديم كـfallback عند انقطاع الشبكة | مثبت برمجياً |
| حدود الصفحات الحرجة | `QuranMetadataTest` | الصفحة 1=`1:1–1:7`، الصفحة 2=`2:1–2:5`، الصفحة 3=`2:6–2:16`، الصفحة 300=`18:54–18:61`، والصفحة 604 تسلسل الإخلاص والفلق والناس حتى `114:6` | مثبت برمجياً |
| بدء التسميع من الصفحة الحالية | فحص الكود + اختبار helper | `startListening()` يستخدم أول آية في `currentPage` عبر `firstAyahIndexOnPage` بدلاً من العودة إلى الآية 1 من السورة المحددة | مثبت برمجياً؛ اختبار واجهة الهاتف مؤجل |
| ألوان الكلمات مع الفهرس العالمي | فحص الكود + اختبار `pageWordOffset` | `AyahBlock` يضيف `pageWordOffset` إلى offset المحلي، والاختبارات تتحقق من الصفحات 1 و2 و300 و604 مع قائمة عالمية | مثبت برمجياً؛ اختبار بصري للهاتف مؤجل |
| التصحيح في صفحات خارج الصفحة المرئية | فحص الكود | `playCorrection` و`correctionMessage` و`locateWord` وcaller التصحيح اليدوي تستخدم `allAyahs` عند استقبال global indices | مثبت برمجياً؛ اختبار صوتي للهاتف مؤجل |
| كلفة tracker العالمي | فحص الكود | أُنشئت ذاكرة تقييم واحدة، وحُفظت العدادات، وأصبح بحث الكلمة المقبولة داخل الآية الحالية؛ snapshot النتيجة يبقى مستقلاً | مثبت برمجياً؛ قياس جهاز 32-bit مؤجل |
| سبب إغلاق Offline السابق | logcat جهاز حقيقي | `SIGSEGV` داخل مكتبة sherpa native بعد نجاح AudioRecord ووجود ملفات النموذج على `armeabi-v7a`/`zygote32` | مثبت تشخيصياً |
| منع الإغلاق على الهاتف الحالي | تثبيت APK سابق + لقطتا `offline-final-card-verified.png` و`offline-final-start-after.png` + PID/logcat | ظهر تحذير 32-bit وتعطل زر بدء Offline، ثم بقي PID التطبيق موجوداً دون `FATAL` أو `SIGSEGV` بعد محاولة الضغط على موضع البدء | مثبت على الهاتف الحالي للنسخة السابقة |
| inference Offline على 64-bit | compile/assemble فقط؛ لا يوجد جهاز 64-bit متصل في الجولة | لم يُشغّل ولم يُثبت على هاتف | مؤجل |
| اختبار صوت خاطئ/تخطي/قفز/رجوع ومد طويل والرجوع التلقائي بالمستخدم على SpeechRecognizer بعد المتعقب | لم تُسجّل نُطُق على APK الحالي؛ v10 السابق كان آخر إصدار مثبت، وتعذر اتصال جلسة Windows أثناء النقل | يحتاج اختباراً صوتياً مباشراً مع حالات مقصودة، خصوصاً مد «الضالين» وإعادة أول كلمتين من الآية السابقة | مؤجل |
| اختبار FastConformer بعد الحارس على 64-bit | لا يوجد دليل | يحتاج هاتفاً 64-bit متوافقاً وذاكرة كافية | مؤجل |
| حزمة صوت Offline | `verify_offline_audio_bundle.py` + `husary_aac_manifest.json` | 6,236 M4A، 1,274,873,643 bytes، وكل SHA-256 مطابق؛ لا MP3 خام داخل assets | مثبت بنيوياً؛ تشغيل الهاتف مؤجل |
| تطابق بصري بكسلي 100% مع Quran-PNG | لا توجد حقوق وخط/إحداثيات مشحونة، ولا فحص pixel diff كامل داخل التطبيق | لم يُثبت ولا يُعلن؛ يحتاج خطاً وlayout مطابقين وحقوق إعادة توزيع وفحصاً مرئياً قابلاً لإعادة التشغيل | غير مثبت/مؤجل |

## أوامر التحقق المنفذة

```text
python3 scripts/verify_word_text_unchanged.py
python3 scripts/verify_word_audio_timings.py
python3 scripts/verify_word_reference_preview.py
python3 scripts/verify_offline_audio_bundle.py
gradle :app:testOnlineDebugUnitTest :app:testOfflineDebugUnitTest :app:assembleOnlineDebug :app:assembleOfflineDebug :app:lintOnlineDebug :app:lintOfflineDebug --offline --no-daemon --max-workers=1
python3 scripts/analyze_quran_metadata.py
python3 scripts/validate_quran_metadata_against_content.py app/src/main/assets/quran/quran-meta-data.xml /tmp/quran_content_remote.json
python3 scripts/validate_quran_png_page_layout.py --metadata app/src/main/assets/quran/quran-meta-data.xml --content /tmp/quran_content_remote.json --image-layout /tmp/quran_image_layout_full.json --png-dir /home/ubuntu/quran-png-reference
python3 scripts/analyze_quran_png_layout.py /home/ubuntu/quran-png-reference
python3 scripts/summarize_quran_png_layout.py /tmp/quran_png_layout_scan.json
```

نجحت البوابة الأخيرة لمدققات النص والتوقيتات والpreview وOffline audio، ثم `:app:testOnlineDebugUnitTest :app:testOfflineDebugUnitTest :app:assembleOnlineDebug :app:assembleOfflineDebug :app:lintOnlineDebug :app:lintOfflineDebug --offline --no-daemon --max-workers=1`، ومرّ `git diff --check`. فُحص Online فوجد 24,208,929 bytes بلا MP3/M4A أو model أو sherpa، وفُحص Offline فوجد 6,236 M4A والنموذج وsherpa بلا INTERNET. لا يوجد اختبار هاتف حي لهذا commit.

## ملفات الأدلة

| الملف | الغرض |
|---|---|
| `docs/QURAN_PNG_COMPARISON.md` | منهج المقارنة، أعداد SQL، نتيجة validator، إشارة raster، والقيود الحقوقية والبصرية |
| `scripts/validate_quran_png_page_layout.py` | فحص قابل لإعادة التشغيل لكل الصفحات والآيات والأسطر وملفات PNG |
| `scripts/validate_quran_word_text_against_image_layout.py` | مقارنة كل tokens الآيات مع `word` و`word_arabic`، والتحقق من الصفحة والموضع والتصنيف |
| `scripts/analyze_quran_word_comparison.py` | تلخيص كل فئات المقارنة والفروق غير المحلولة |
| `scripts/extract_quran_image_layout.py` | استخراج خريطة page/line/glyph/ayah من SQL المستقل |
| `scripts/analyze_quran_png_layout.py` | قياس dimensions وalpha وrow bands من الصور دون OCR |
| `app/src/main/java/com/example/quranoffline/data/QuranPageLayout.kt` | helper تقطيع الصفحة وحساب global indices |
| `app/src/test/java/com/example/quranoffline/QuranMetadataTest.kt` | اختبارات boundaries وoffsets للصفحات الحرجة |
| `docs/RECITATION_CORRECTION_EPISODE_SPEC.md` | عقد المتعقب وقواعد المحاذاة والانتقال والتصحيح |
| `docs/CORRECTION_SETTINGS_SPEC.md` | عقد خيارات تصحيح الشيخ وقيمها الافتراضية والحفظ |
| `docs/OFFLINE_CRASH_FINDINGS.md` | تشخيص logcat وملفات النموذج وABI للهاتف |
| `docs/PHONE_DIRECT_TEST.md` | نتائج الرحلات المباشرة ولقطات الهاتف |
| `/home/ubuntu/phone-direct-test-findings.md` | السجل الزمني التفصيلي لجلسة ADB |
| `offline-final-card-verified.png` | لقطة هاتف تاريخية لإصدار سابق؛ ليست دليلاً على flavor Offline الحالي |
| `offline-guard-after-tap.png` | لقطة سابقة تثبت بقاء العملية بعد محاولة الضغط على بدء Offline |
| `quran-online-settings-v10-home.png` | شاشة الجاهزية للإصدار المثبت السابق مع بقاء PID التطبيق |
| `quran-online-settings-v10-policy-card.png` | بطاقة إعدادات سياسة تصحيح الشيخ في الإصدار المثبت السابق |
| `app/build/outputs/apk/online/debug/app-online-debug.apk` | APK Online الحالي، غير مثبت على الهاتف حتى الآن |
| `app/build/outputs/apk/offline/debug/app-offline-debug.apk` | APK Offline الحالي، غير مثبت على الهاتف حتى الآن |
| `/tmp/quran_word_text_comparison.json` | تقرير word-level كامل غير مشحون إلى المستودع؛ يسجل الحالات غير exact وspot checks |

## القيود والخصوصية والحقوق

محرك Online يعتمد `SpeechRecognizer` الموجود في Android، وقد يمر الصوت إلى مزود النظام؛ لا يوصف بأنه معالجة محلية بالكامل. محرك Offline، عندما يكون مدعوماً على جهاز 64-bit متوافق، مصمم للمعالجة المحلية بعد تنزيل النموذج، لكن هذا لم يُثبت في هذه الجولة. التصحيح مطابقة نصية سياقية تعليمية ولا يحكم التجويد أو المدود أو مخارج الحروف، ولا يمثل إجازة علمية.

ملفات Quran-PNG المستنسخة محلياً من `GovarJabbar/Quran-PNG` لا تحمل في بيانات GitHub التي فُحصت ترخيصاً معلناً (`license: null`) ولا يوجد ملف LICENSE واضح في النسخة المفحوصة؛ لذلك لم تُنسخ الصور إلى `app/src/main` ولم تُضمّن في APK. كما أن مصادر `quran.com-images` تستخدم موارد خطوط/صفحات تحتاج مراجعة حقوق مستقلة. لا ينبغي إعادة توزيع الصور أو الخطوط أو glyph data المشتقة منها قبل الحصول على إذن واضح.

## المراجع

[1]: https://k2-fsa.github.io/sherpa/onnx/android/index.html "توثيق sherpa-onnx الرسمي لتطبيق Android"

[2]: https://github.com/k2-fsa/sherpa-onnx/releases "إصدارات sherpa-onnx الرسمية"

[3]: https://github.com/GovarJabbar/Quran-PNG "مستودع Quran-PNG المستخدم كمرجع تحقق خارجي"

[4]: https://github.com/quran/quran.com-images "مستودع quran.com-images ومخطط glyph/page/line المستقل"


## بوابة التحقق النصي المرتبط بصور Quran-PNG — 2026-08-24

أضيفت مقارنة حتمية على مستوى كل آية وكل token بين `/tmp/quran_content_remote.json` وبين `word` و`word_arabic` في SQL الذي استخدمه مولد quran.com-images. لم تُشحن صور Quran-PNG أو خطوطها في التطبيق. النتيجة الكاملة محفوظة في `/tmp/quran_word_text_comparison.json`، ويُعاد تشغيلها بالأوامر المدرجة أدناه. مصدر النص العام هو `ysrg2003/quran-memorization-assets`، وقد أُصلح محتوى القرآن بالكامل في commit `0a33667`. الإصدار هو `assetVersion=2026.08.24.2`، وSHA-256 للملف هو `2c0630aed197a56058405e8f84dc39edd9c2d8b0c0ac31df2f24924b01e6b594`. جرى تحديث `matchText` عند الحاجة وتصحيح `canonicalTextUnchanged=false` حتى تعكس metadata اعتماد النص المرجعي الجديد.

| الفحص | النتيجة | الحالة |
|---|---:|---|
| آيات التطبيق المفحوصة | 6,236/6,236 | مثبت |
| صفحات layout المفحوصة | 604/604 | مثبت |
| صفوف `word` المرجعية | 77,430 | مثبت |
| قيم `word_arabic` المرجعية | 18,994 | مثبت |
| تطابق raw كامل لتسلسل كلمات الآية | 6,124 آية | مثبت بعد مواءمة كل token |
| تطابق raw لكل token قرآني قابل للمقارنة | 77,430/77,430 | مثبت؛ يشمل التشكيل والعلامات والرسم |
| بسملة منفصلة متوقعة مع نواة raw مطابقة | 112 آية؛ 448 token فرق عرض | مثبت ومفسر |
| تجزئة `بَعْدَ` + `مَا` مقابل `بَعْدَمَا` | 0 بعد الدمج في مصدر التطبيق | مثبت ومفسر |
| فرق عدد الكلمات غير المفسر | 0 | مثبت |
| فروق raw غير محلولة | 0 | مثبت بعد اعتماد صيغة الـReference لكل tokens |
| `raw_token_difference_count` | 0 | مثبت |
| inventory الخام قبل المواءمة | 351 فرق token؛ 317 استبدالاً نصياً و3 عمليات دمج للتجزئة | مثبت بتقرير audit مستقل |
| غياب الفروق النصية غير المفسرة | نعم، 6,236/6,236 مصنفة؛ 6,124 raw exact و112 بسملة عرضية مفسرة | مثبت نصياً؛ لا يعني تطابق glyph/pixel |

الحالة `2:1` التي طلبها المستخدم مفحوصة صراحةً: التطبيق يحمل البسملة وأداة `الٓمٓ` في قائمة واحدة، وجدول المرجع يحمل `الٓمٓ` داخل `word` فقط لأن البسملة سطر مستقل في العرض. لذلك تصنف `expected_basmala_omission_exact_core`، وتبقى النواة نفسها خاماً. كما تمر `1:1` و`1:2` و`2:2` خاماً، وتمر آيات `18:54–18:61` في الصفحة 300 خاماً، وتُفسر بدايات 112 و113 و114 في الصفحة 604 بقاعدة البسملة نفسها، مع تطابق بقية الآيات في spot checks.

كان الفرق السابق في `12:39` و`12:41` هو أن التطبيق يحمل `يَٰصَٰحِبَىِ` بينما يحمل المرجع `يَٰصَىٰحِبَىِ`. وبناءً على طلب المستخدم تم تعديل هذه الكلمة، ثم وُسّعت العملية لتشمل كل token مختلف raw، بما في ذلك التشكيل والعلامات والرسم، ودمجت الحالات الثلاث `بَعْدَ` + `مَا` إلى `بَعْدَمَا` كما في المرجع. أُعيد تشغيل المقارنة وأصبح `raw_token_difference_count=0` و`true_text_mismatch=0`. بقيت القيود البصرية مستقلة: لا يعني ذلك أن خط Compose أو glyph أو pixel مطابق 100%.

المعاينة البصرية للبلاطات المتداخلة من صفحات 240 و300 و604، إضافة إلى معاينة 001 و002 و003، تؤكد مواضع العناوين والبسملات وعلامات الآيات وتسلسل المقاطع في العينات، لكنها لا تُستخدم لإثبات code points عبر OCR. يظل التطابق البكسلي أو glyph-level بنسبة 100% غير مثبت، كما أن اختبار النطق الفعلي ليس جزءاً من هذه البوابة.

أوامر البوابة النصية هي:

```text
python3 scripts/validate_full_quran_matching.py /tmp/quran_content_remote.json
python3 scripts/align_quran_content_to_reference.py \
  --content /tmp/quran_content_remote.json \
  --sql /home/ubuntu/quran-com-images-reference/sql/02-database.sql \
  --output /tmp/quran_content_reference_aligned.json \
  --audit /tmp/quran_content_reference_alignment_audit.json
python3 scripts/validate_quran_word_text_against_image_layout.py --self-check
python3 scripts/validate_quran_word_text_against_image_layout.py \
  --content /tmp/quran_content_remote.json \
  --sql /home/ubuntu/quran-com-images-reference/sql/02-database.sql \
  --layout /tmp/quran_image_layout_full.json \
  --report /tmp/quran_word_text_comparison.json
python3 scripts/analyze_quran_word_comparison.py /tmp/quran_word_text_comparison.json
```

نجح فحص النص والـschema، ونجح `verify_word_audio_timings.py` لملف الحصري، مع حفظ التكرارات والأحداث خارج النص بدلاً من نقلها إلى كلمات لاحقة. لا يعني ذلك مراجعة بشرية لكل حد زمني؛ يلزم اختبار سمعي للحالات المعلّمة.


## ملحق العقد الوظيفي الجديد — 2026-08-24

هذا الملحق **يستبدل الأرقام القديمة** التي كانت تصف وجود 77878 كلمة وبسملات داخل الآيات؛ مصدر الأصول الحالي هو `assetVersion=2026.08.24.3`.

| الفحص | الدليل والنتيجة | الحالة |
|---|---|---|
| عقد النص بعد فصل البسملة | `validate_full_quran_matching.py`: 6236 آية، 77430 كلمة، وكل أزواج `text`/`matchText` مقبولة | مثبت |
| قاعدة توقيتات Husary QUL المحلية | `scripts/verify_word_audio_timings.py`: 6236 آية و77430 نافذة canonical، 77897 raw segment، 366 حدثاً خارج نطاق الكلمات، و34 آية بها تكرارات | مثبت كبيانات تغطية؛ QUL يصف الحدود بأنها مولدة آلياً وقابلة للمراجعة، لا كإثبات phoneme مطلق |
| حدود قص كلمة الحصري | `scripts/build_husary_audio_timings.py` + `Husary_Murattal.json` | نافذة canonical واحدة لكل 77430 كلمة؛ التكرارات وsegments خارج النص محفوظة للتدقيق ولا تُنقل إلى كلمات لاحقة | مثبت بنيوياً؛ الحدود الآلية ليست مراجعة بشرية لكل phoneme |
| قاعدة expected المسبقة | `word_reference_ayahs.json` + schema validator: كل 77430 كلمة تحمل `spokenForm` و`recognizedForms` و`waqfForms`، و5219 كلمة تحمل `maddPositions` فعلية | مثبت |
| المد المرئي العام | مولد Word واختبارات matcher | مواضع `U+0653` الظاهرة تُحوّل مسبقاً إلى metadata، وmatcher يقبل نسخ 2/4/6 للحامل الممدود فقط | مثبت برمجياً |
| مقارنة SQL المرجعي | `audit_quran_ayah_contract.py`: 6236 آية مصدر ومرجع، 77430 كلمة لكل منهما، `mismatchCount=0` | مثبت |
| افتتاحات السور | 1:1 فقط يحوي بسملة داخل الآية؛ 112 افتتاحاً لها `OpeningBasmala` مشتقة خارج `QuranAyah`؛ 9:1 بلا بسملة | مثبت |
| 2:1 على الهاتف | البسملة ظهرت كسطر مستقل، وظهرت `الٓمٓ` وحدها في الآية 2:1 | مثبت بصرياً |
| الحروف المقطعة | 30 token فعلياً في Word؛ `Muqattaat` يحافظ على token مرئي واحد، يقبل أسماء الحروف منفصلة والمد المناسب، ويحمي prefix بين callbacks | مثبت باختبارات الوحدة |
| توقيتات quran-align القديمة | بقيت كأصل غير افتراضي للمراجعة/التوافق؛ لا يستخدمها runtime لمسار تصحيح كلمة الحصري | مثبت كأصل تاريخي؛ ليست مصدر الإصدار الافتراضي |
| تصحيح كلمة من آية الحصري | `RemoteAssetManager.audioUrl()` + `ReferenceAudioPlayer.playCorrection()`؛ يستخدم نافذة QUL داخل ملف آية الحصري مع `SEEK_CLOSEST` ثم يوقف عند `endMs`، ولا يعيد آخر نافذة عند الغياب | مثبت برمجياً ومدققاً على 6236 آية/77430 نافذة؛ يلزم اختبار Android حي لجودة التشغيل والشبكة |
| checkpoint | SharedPreferences مستقل يحفظ `(surah, ayah, wordIndex, includeOpeningBasmala, updatedAt, schemaVersion)`، مع codec واختبارات حدود | مثبت |
| استئناف الهاتف | بعد إعادة تشغيل العملية بقي `2:2، الكلمة 1`، ثم نقل زر الاستئناف الشاشة إلى البقرة وفعّل «إيقاف الجلسة» مع `الآية المتوقعة: 2` | مثبت هاتفياً |
| الإعدادات المنفصلة | أيقونة الترس فتحت صفحة مستقلة للتصحيح والمحرك والتنزيل، ولم تعد البطاقات ضمن أعلى تجربة الحفظ | مثبت بصرياً |
| القراءة والقارئ | تبويب القراءة عرض آية/سورة/جزء/صفحات/المصحف وزر تشغيل من checkpoint؛ طابور الصوت قابل للإلغاء ويستخدم stream/download الفعلي | مثبت بناءً ومسار UI؛ الدقة السمعية غير مقاسة |
| قائمة الآية | زر `MoreVert` الظاهر بجانب الآية فتح الإجراءات الأربعة: تعيين بداية الحفظ، بدء التسميع، تشغيل الآية، بدء القراءة من هنا | مثبت هاتفياً |
| تشغيل صوت الآية | اختيار «تشغيل الآية» من القائمة أبقى MainActivity دون FATAL أو MediaPlayer exception في Logcat | مثبت عدم الانهيار؛ ليس قياس جودة سمعية |
| بوابة Gradle التاريخية | `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --no-daemon --max-workers=1` | دليل سابق؛ البوابة الحالية flavor-specific ومذكورة أعلاه |
| مشروع offline | `/home/ubuntu/tarteel-android-offline` لم يتغير؛ `git status` لم يكن مستودعاً قابلاً للتقرير، ولم تُكتب فيه ملفات | مثبت بالتحقق التشغيلي |

يبقى اختبار النطق البشري الكامل، ودقة `SpeechRecognizer` لكل المدود، وInference Offline على هاتف 64-bit، واختبار تشغيل نافذة كلمة الحصري على Android، والمراجعة السمعية للحالات المكررة، والتطابق البصري pixel/glyph بنسبة 100% خارج ما تثبته هذه الجولة. QUL يوفر حدوداً مرتبطة بملف الحصري نفسه، لكنه يصفها بأنها مولدة آلياً وقابلة للمراجعة؛ لذلك لا تُوصف كل millisecond بأنها مراجعة بشرية أو حد phoneme نهائي.
