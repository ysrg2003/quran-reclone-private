# سجل بحث التراخيص وشروط الاستخدام — 2026-08-27

> هذا سجل بحث تقني/امتثالي وليس رأياً قانونياً. يجب مراجعة صاحب الحق أو محامٍ قبل نشر تجاري أو رفع التطبيق إلى متجر.

## EveryAyah والسكربت التاريخي

تمت مراجعة [EveryAyah](https://everyayah.com/) ومجلد بياناته. الصفحة تعرض ملفات التلاوات ومجلدات التوقيت، لكنها لا تعرض في الصفحة المفحوصة ترخيص إعادة توزيع عام لكل التسجيلات. الـgist الذي تمت مراجعته هو [everyayah_downloader.py](https://gist.github.com/abdalrohman/11da7e35b6006744275e2dfc2e918aa8) ويعلن MIT للكود الموجود فيه فقط. أما `scripts/download_full_everyayah_audio.sh` في هذا المشروع فهو سكربت Bash منفصل غير مشحون داخل APK، ولا ينبغي نسبته إلى صاحب الـgist دون مقارنة provenance. ترخيص أداة التنزيل لا يمنح حق إعادة توزيع ملفات MP3 أو تسجيل قارئ معين.

## QUL وTarteel

المصدر: https://qul.tarteel.ai/resources/recitation/112

صفحة QUL الرسمية تسمي المورد **Mahmoud Khalil Al-Husary — Ayah by Ayah**، وتضع الوسوم `Recitation`, `Murattal`, `Hafs`, `With segments`, `Muallim`. الصفحة تعرض تنزيل JSON وSQLite وبيانات segments، لكنها لا تعرض في النص المستخرج ترخيصاً خاصاً واضحاً لتسجيل الحصري أو لكل ملف صوتي.

FAQ QUL: https://qul.tarteel.ai/faq

- QUL يذكر أن استخدام البيانات في مشروع تجاري ممكن، لكن يجب مراجعة ترخيص كل مورد على حدة.
- QUL يذكر أن حالة الحقوق تختلف بين الموارد؛ بعضها public domain وبعضها يخضع لترخيص خاص، ويوصي بمراجعة معلومات الترخيص التي يقدمها مؤلف المورد.
- هذا لا يساوي إذناً عاماً بإعادة توزيع تسجيل الحصري أو استضافة الملفات على CDN خاص.

توثيق تنزيل البيانات: https://qul.tarteel.ai/docs/downloading-data

- يشرح كيفية تنزيل JSON/SQLite.
- يذكر أن روابط `audio_url` إلى `audio-cdn.tarteel.ai` موارد CDN مشتركة وليست اعتماداً إنتاجياً مضموناً.
- يوصي في التكامل الإنتاجي بتنزيل الأصول وخدمتها من تخزين/CDN خاص، لكن هذه توصية تشغيلية لا تمنح وحدها حقوق إعادة التوزيع.

توثيق segments: https://qul.tarteel.ai/docs/with-segments

النتيجة: ترخيص كود مستودع QUL منفصل عن ترخيص الموارد. مستودع QUL نفسه: https://github.com/TarteelAI/quranic-universal-library ويظهر له MIT، لكن MIT يخص كود المستودع ولا يكفي لإثبات حق نشر تلاوة الحصري أو البيانات المشتقة منها. يجب الحصول على تأكيد كتابي أو سجل ترخيص resource-specific قبل شحن MP3 أو استضافته.

شروط Tarteel العامة: https://tarteel.ai/en/terms

- تمنح شروط الخدمة ترخيصاً محدوداً وغير حصري وغير قابل للتحويل للوصول إلى Services، وتمنع نسخ أو توزيع Service Content إلا إذا سمحت الشروط صراحةً.
- تنص على أن محتوى الخدمة، بما فيه الصوت والملفات، ملك لـTarteel أو المرخصين لها، وأن الحقوق غير الممنوحة صراحة محفوظة.
- هذه الشروط تخص استخدام خدمة Tarteel، وليست ترخيصاً تلقائياً لإعادة توزيع ملفات QUL أو تسجيل الحصري.

## quran-align

المستودع: https://github.com/cpfair/quran-align

- كود المستودع مرخص MIT، ويجب إبقاء إشعار حقوق Collin Fair ونص MIT عند إعادة توزيع الكود أو أجزاء جوهرية منه.
- README يذكر صراحةً أن ملفات بيانات التوقيت مرخصة CC BY 4.0، مع طلب الإسناد والتواصل عند الاستخدام.
- README يذكر أن البيانات مولدة آلياً، وأن دقة الحدود ذاتية/ملتبسة عند التكرار، وأن بعض الكلمات قد تُحذف أو تبقى داخل segment مدمج.
- الإصدار المستخدم تاريخياً: https://github.com/cpfair/quran-align/releases/tag/release-2016-11-24
- بيانات الإصدار تشمل `Alafasy_128kbps` و`Husary_64kbps` و`Husary_Muallim_128kbps`، لكنها لا تثبت أنها نفس تسجيل Husary QUL الحالي.

النتيجة: يمكن إبقاء بيانات quran-align القديمة مع إسناد CC BY 4.0 صحيح، لكن لا يجوز نسبها إلى تسجيل QUL الحصري أو وصف حدودها بأنها مراجعة بشرية مطلقة.

## النص القرآني والـmetadata

Tanzil text license: https://tanzil.net/docs/text_license

Tanzil يسمح بنسخ وتوزيع نسخ verbatim، ويمنع تغيير النص، ويطلب إظهار مصدر Tanzil ورابطه وإدراج إشعار الحقوق في النسخ المشتقة أو التي تحتوي جزءاً جوهرياً منه. هذا الترخيص لا يحكم تلقائياً ملف Word المستخدم هنا إذا كان النص الحاكم مختلفاً أو مشتقاً من مصدر آخر.

في التطبيق الحالي، النص الحاكم هو Word المستخدم من صاحب المشروع: `reference-input/UthmanicHafs_V20.doc`، والأصل runtime هو `word_reference_ayahs.json`. يجب الاحتفاظ بإثبات مصدر Word وحق إعادة توزيعه؛ لا يجوز وضع إشعار Tanzil على أنه مصدر Word إن لم يكن ذلك صحيحاً. `quran-meta-data.xml` هو metadata تنظيمي من Tanzil ويحتاج إشعار Tanzil وفق شروطه.

## الخط KFGQPC

موقع المجمع: https://qurancomplex.gov.sa/

قائمة خطوط Tanzil: https://tanzil.net/docs/quranic_fonts

Tanzil يعرّف `KFGQPC Hafs Uthmanic Script` بأنه من King Fahd Quran Complex، لكن صفحة Tanzil لا تمنح رخصة خط مستقلة. موقع المجمع يعرض الخطوط الحاسوبية وخدمات النص القرآني، لكنه لا يوفر في الصفحة التي فُحصت نص رخصة واضحاً يثبت شروط تضمين ملف TTF المرفق في تطبيق تجاري.

النتيجة الحرجة: وجود ملف الخط في المشروع أو توفره في مواقع تنزيل خارجية لا يثبت حق إعادة توزيعه. يجب الحصول على ملف الخط من المصدر الرسمي وقراءة `EULA/LICENSE` المرفقة به أو طلب إذن واضح من المجمع. إلى أن يتم ذلك، لا يصح إعلان APK تجارياً على أنه يملك حق شحن الخط.

## Quran.com-images وQuran-PNG

Quran.com-images: https://github.com/quran/quran.com-images

README الرسمي يقول إن **الكود GPL**، لكنه يقول أيضاً إن الخطوط والصفحات في `res/fonts` تعود إلى King Fahd Quran Complex. لذلك لا يجوز اعتبار GPL للكود ترخيصاً للخطوط أو الصفحات أو glyph data. GitHub API لا يعرض license repository واضحاً (`license=null`)؛ يلزم فصل كود GPL عن assets ذات الحقوق الخاصة.

Quran-PNG: https://github.com/GovarJabbar/Quran-PNG

GitHub API لا يعرض ترخيصاً (`license=null`) ولا يظهر ملف LICENSE في جذر المستودع الذي فُحص. README يذكر أن الصور مولدة من quran.com-images. التطبيق الحالي لا يشحن صور Quran-PNG، ويستخدمها كمرجع تحقق خارجي فقط؛ لذلك لا توجد صور PNG أو حقوقها ضمن APK، لكن لا ينبغي إعادة توزيعها أو تحويلها إلى أصول تطبيقية دون إذن.

## sherpa-onnx وAndroid libraries

sherpa-onnx: https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE

ترخيص Apache License 2.0. عند شحن AAR أو native libraries يجب تضمين نص Apache 2.0 والإشعارات المطلوبة، وعدم استخدام أسماء/علامات المشروع بطريقة توحي بتأييد. يجب أيضاً فحص مكونات AAR المضمنة؛ ملف AAR الحالي يحوي native `libonnxruntime.so` في بعض ABI، وقد يكون لـONNX Runtime إشعار/ترخيص منفصل يجب إضافته إذا كان فعلاً ضمن binary المشحون.

التبعيات المباشرة في `app/build.gradle.kts`:

- AndroidX Core KTX 1.15.0
- Activity Compose 1.10.0
- Lifecycle Runtime KTX 2.8.7
- Compose BOM 2024.12.01، وCompose UI/Material3/Material Icons
- DataStore Preferences 1.1.1
- Kotlin Android/Compose plugins 2.0.21
- Android Gradle Plugin 8.6.1
- JUnit 4.13.2 للاختبارات

المكتبات AndroidX وKotlin وCompose وGradle/JUnit موزعة عادةً تحت Apache 2.0 أو تراخيص مفتوحة مرتبطة بها، لكن يجب توليد قائمة licenses من resolved dependency graph والإصدار الفعلي، وعدم الاكتفاء باسم المكتبة. لا ينبغي حذف `/META-INF/{AL2.0,LGPL2.1}` من APK من دون توفير نصوصها في شاشة/ملف notices؛ إعداد packaging الحالي يستبعدها من الدمج، لذلك يلزم إضافة إشعار نصي واضح داخل المشروع/التطبيق.

## النموذج المحلي

FastConformer Quran Arabic ONNX INT8: https://huggingface.co/mohammed/fastconformer-quran-ar-onnx-int8

- بطاقة النموذج تضع `License: cc-by-4.0`.
- النموذج أساسه `mohammed/fastconformer-quran-ar`، وبطاقة الأساس أيضاً CC BY 4.0.
- البطاقة تطلب الإسناد، وتذكر أن اختبار الجهاز ما زال pending وأن INT8 ONNX يحتاج تحققاً على الجهاز.
- بطاقة النموذج تذكر dataset `tarteel-ai/everyayah`؛ يجب مراجعة ترخيص dataset/تسجيلات التدريب إذا كان نشر النموذج أو مشتقاته يفرض شروطاً إضافية.

NVIDIA base model: https://huggingface.co/nvidia/stt_ar_fastconformer_hybrid_large_pcd_v1.0

- البطاقة تذكر أنه متاح للاستخدام التجاري وغير التجاري تحت CC BY 4.0.
- البطاقة تنبه إلى حدود الدقة وعدم ملاءمته للنسخ كلمة بكلمة في الحالة العامة.

النتيجة: إذا شُحن النموذج، يجب تضمين CC BY 4.0 وإسناد Mohammed وNVIDIA والروابط، وحفظ نسخة/commit النموذج المستخدم وhash الملفات. لا يجوز وصف نتائج WER في بطاقات النموذج كاختبار أداء لهذا التطبيق.

## صلاحيات Android والخصوصية

Manifest الحالي يطلب:

- `android.permission.INTERNET`: لبث/تنزيل صوت الحصري وتنزيل النموذج.
- `android.permission.RECORD_AUDIO`: للتسميع؛ صلاحية خطرة runtime ويجب طلب موافقة المستخدم قبل التسجيل.
- `allowBackup=true`: يسمح بنسخ بيانات التطبيق وفق سياسات Android/الشركة المصنّعة؛ يجب مراجعة ما إذا كانت checkpoints أو إعدادات التلاوة تحتوي بيانات يريد المستخدم استبعادها.
- النشاط الرئيسي exported للـlauncher فقط مع `MAIN/LAUNCHER`، ولا توجد خدمة أو receiver أو provider مخصص ظاهر في Manifest.

في Online SpeechRecognizer قد يرسل الصوت إلى مزود النظام، لذلك يلزم إفصاح خصوصية واضح. التطبيق لا يملك من شروط Google/مزود SpeechRecognizer حق استخدام أو تخزين الصوت؛ يجب الالتزام بسياسة الجهاز والخدمة، وعدم تسجيل الصوت الخام أو رفعه إلى خدمة أخرى دون إفصاح وموافقة.

## خلاصة الحالة

- **مسموح مبدئياً مع إشعارات:** quran-align code MIT، quran-align timing data CC BY 4.0، QUL repository code MIT، sherpa-onnx Apache 2.0، ONNX Runtime MIT، FastConformer model CC BY 4.0، Tanzil metadata CC BY 3.0، AndroidX/Kotlin/Compose/JUnit بعد توليد notices فعلياً.
- **مسموح للاستخدام التقني لكن حق الشحن غير مثبت:** صوت الحصري وQUL segments، خط KFGQPC/Word، Word source نفسه، ملفات CDN، EveryAyah recordings/dataset، وأي مكونات أصلية داخل AAR غير موثقة في its notices.
- **مرجع تحقق فقط ولا يُشحن:** Quran-PNG images، quran.com-images fonts/pages/glyph assets.
- **ممنوع الإعلان عن إطلاق تجاري أو نشر عام قبل الإغلاق:** صوت الحصري وملف توقيتاته المشتق، خط KFGQPC وWord source ما لم توجد وثائق إذن/ترخيص قابلة للإرفاق؛ وكذلك أي AAR/model notices غير مكتملة.

## نتيجة فحص AAR الفعلية

فُحص `app/libs/sherpa-onnx-1.13.6.aar` نفسه. لا يحتوي الأرشيف على ملف LICENSE أو NOTICE واضحاً، ويضم `libonnxruntime.so` داخل `jni/x86/`. أظهر الفحص النصي للـbinary الإصدار **ONNX Runtime 1.27.1**. لذلك يجب معاملة AAR على أنه يحتوي مكوّنين قانونيين على الأقل: sherpa-onnx تحت Apache-2.0، وONNX Runtime تحت MIT، مع حفظ نصي الترخيص في إشعارات التوزيع. لا يجوز الاكتفاء بإشعار sherpa-onnx وحده.

## قرار النشر الحالي

حتى اكتمال المستندات التالية، حالة المشروع **مناسب للاختبار الداخلي فقط، وليس clearance للنشر العام أو التجاري**:

1. إذن أو EULA قابل للإرفاق لخط KFGQPC ونسخة Word المقدمة من King Fahd Complex.
2. إذن أو ترخيص resource-specific لتسجيل Mahmoud Khalil Al-Husary وبيانات QUL segments المشتقة منه، مع توضيح جواز إعادة الاستضافة أو الاعتماد على CDN.
3. provenance وترخيص كل ملف ضمن AAR، بما في ذلك ONNX Runtime 1.27.1.
4. تثبيت نسخة model commit/hash وإضافة attribution CC BY 4.0 للنموذج وNVIDIA عند تفعيل/توزيع نموذج FastConformer.
5. توليد ملف notices قابل للوصول من داخل APK، وليس فقط في مستودع المصدر.

لا توجد في هذه المراجعة إشارة إلى مخالفة مؤكدة؛ الموجود هو **نقص في الدليل القابل للإثبات** لبعض الأصول الجوهرية، وهذا وحده كافٍ لمنع إعلان صلاحية تجارية نهائية إلى أن يقدّم صاحب الحق الشروط أو الإذن.

## مراجع إضافية

- ONNX Runtime license: https://github.com/microsoft/onnxruntime/blob/main/LICENSE
- ONNX Runtime Android Maven metadata: https://central.sonatype.com/artifact/com.microsoft.onnxruntime/onnxruntime-android
- sherpa-onnx AAR source page: https://huggingface.co/csukuangfj/sherpa-onnx-libs
- sherpa-onnx license: https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE
- FastConformer INT8 model card: https://huggingface.co/mohammed/fastconformer-quran-ar-onnx-int8
- King Fahd Complex fonts portal: https://fonts.qurancomplex.gov.sa/
- Quran.com images README: https://github.com/quran/quran.com-images
- Quran-PNG reference repository: https://github.com/GovarJabbar/Quran-PNG
- EveryAyah: https://everyayah.com/
- EveryAyah downloader gist: https://gist.github.com/abdalrohman/11da7e35b6006744275e2dfc2e918aa8
- Tanzil text license: https://tanzil.net/docs/text_license
- Tanzil Quranic fonts: https://tanzil.net/docs/quranic_fonts
- QUL resource: https://qul.tarteel.ai/resources/recitation/112
- QUL FAQ: https://qul.tarteel.ai/faq
- QUL downloading guide: https://qul.tarteel.ai/docs/downloading-data
- Tarteel terms: https://tarteel.ai/en/terms
- quran-align README and data license: https://github.com/cpfair/quran-align#readme
- quran-align MIT code license: https://github.com/cpfair/quran-align/blob/master/LICENSE
- quran-align release data: https://github.com/cpfair/quran-align/releases/tag/release-2016-11-24
- Tarteel QUL repository license: https://github.com/TarteelAI/quranic-universal-library/blob/main/LICENSE
