# تراخيص التبعيات والمصادر

تحتوي هذه المجلدات على نصوص التراخيص أو روابطها الرسمية للمكونات التي تدخل في المستودع أو APK. يجب توزيع النصوص مع أي نسخة عامة، وإضافة هذا المحتوى إلى شاشة إشعارات قانونية قابلة للوصول من داخل التطبيق قبل النشر العام.

| المكوّن | الترخيص/الحالة | المصدر الرسمي |
|---|---|---|
| quran-align code | MIT | https://github.com/cpfair/quran-align/blob/master/LICENSE |
| quran-align timing data | CC BY 4.0 | https://creativecommons.org/licenses/by/4.0/ |
| QUL repository code | MIT | https://github.com/TarteelAI/quranic-universal-library/blob/main/LICENSE |
| QUL Husary audio/segments | ترخيص المورد غير ظاهر بوضوح؛ يلزم إذن أو شروط خاصة بالمورد؛ Online يبث MP3 وOffline يضم M4A مشتقة | https://qul.tarteel.ai/resources/recitation/112 |
| Android SpeechRecognizer | خدمة Android/مزود النظام؛ قد تحتاج الشبكة وفق المزود ولا يملك التطبيق ترخيص خدمة المزود | https://developer.android.com/reference/android/speech/SpeechRecognizer |
| Tanzil metadata | CC BY 3.0 مع منع تغيير النص حسب إشعار Tanzil | https://tanzil.net/docs/text_license |
| AndroidX/Kotlin/Compose | Apache 2.0 | https://www.apache.org/licenses/LICENSE-2.0 |
| JUnit 4.13.2 | EPL 1.0 | https://www.eclipse.org/legal/epl-v10.html |
| KFGQPC font and Word | لا يوجد EULA/إذن صادر من صاحب الحق محفوظ في المستودع؛ نتائج البحث المستقل تنقل شروطاً مقيدة لكنها لا تثبت انطباقها على ملف المشروع؛ لا يُعلن clearance | https://fonts.qurancomplex.gov.sa/ و https://scancode-licensedb.aboutcode.org/kfgqpc-uthmanic-script-hafs.html و https://font.download/font/kfgqpc-hafs-uthmanic-script |
| EveryAyah recordings | شروط إعادة التوزيع غير مثبتة في الفحص | https://everyayah.com/ |
| Quran-PNG | لا يوجد LICENSE ظاهر؛ مرجع تحقق فقط ولا يُشحن | https://github.com/GovarJabbar/Quran-PNG |
| quran.com-images fonts/pages | الكود GPL بحسب README، لكن الخطوط والصفحات من KFGQPC؛ لا تُشحن | https://github.com/quran/quran.com-images |
| QuranHub Husary repository | GPL-3.0 للمستودع، لكن لا يوجد تصريح مستقل بحقوق التسجيل أو سلسلة ملكيته؛ لا يُستخدم كإذن لصوت QUL | https://github.com/QuranHub/quran-audio-hafs-husary |
| QuranCentral/Internet Archive/Assabile/Quran.com audio pages | صفحات استماع/تنزيل أو تعريف بالقارئ دون grant ظاهر لتضمين التسجيل المحدد في APK أو إعادة استضافته | https://qurancentral.com/audio/mahmoud-khalil-al-husary و https://archive.org/details/lifeways11_gmail_001_20180201 و https://www.assabile.com/mahmoud-khalil-al-hussary-27/collection/al-mushaf-al-murattal-egyptian-radio-recordings-3217 و https://quran.com/en/reciters/6 |
| Offline Husary audio | M4A مشتقة AAC-LC 64kbps؛ 6236 ملفاً، source/derived SHA-256 في manifests؛ الحقوق تحتاج إذناً | `app/src/offline/assets/quran/audio/husary_aac/` |
| Word startup preview | ليس مكوّناً خارجياً؛ subset مطابق حرفياً لـWord canonical، للعرض المبكر فقط | `app/src/main/assets/quran/word_reference_preview.json` |

## ملاحظات

MIT وApache يتطلبان إبقاء إشعارات الحقوق ونصوص التراخيص وفق شروطهما. CC BY يتطلب الإسناد ورابط الترخيص وبيان التغييرات عند الاقتضاء. هذا الفهرس لا يمنح أي حق للأصول التي لم يظهر ترخيصها أو إذنها الخاص، ولا يحل محل مراجعة صاحب الحق أو المحامي. راجع `docs/LEGAL_INDEPENDENT_SEARCH_ADDENDUM_2026-08-27.md` و`docs/LEGAL_INDEPENDENT_SEARCH_NOTES_2026-08-27.md` للتفاصيل، وراجع سياسة preview وهامش تصحيح الصوت في `README.md` و`docs/source-lock.md`.
