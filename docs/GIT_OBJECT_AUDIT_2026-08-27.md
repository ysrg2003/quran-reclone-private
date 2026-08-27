# Git object audit — 2026-08-27

## النتيجة الحالية

فُحصت الشجرة القابلة للدفع للـ`main` والـtag الجديد `quran-v1-writing-mode`. كلاهما يشير إلى commit الذي يعيده `git rev-parse quran-v1-writing-mode^{}` وقت التدقيق، ولا تحتوي شجرتهما الحالية على ملفات `.mp3` أو `.m4a` أو APK أو ZIP model، ولا أسماء `sherpa` أو `onnx` أو `fastconformer`.

```text
git ls-tree -r --name-only quran-v1-writing-mode | grep -E '\.(mp3|m4a|apk|zip)$|sherpa|onnx|fastconformer'
# no output
```

أُجري أيضاً `git rev-list --objects main quran-v1-writing-mode` مع `git cat-file --batch-check` لفحص الكائنات reachable. تظهر في history القديمة بعض blobs غير المطلوبة للعقد الحالي، وأكبرها AAR Sherpa القديم بحجم `37,751,439` bytes، لأنه تاريخياً جزء من parent commit قبل حذفه. هذا لا يجعله جزءاً من tree أو APK الحالي، ولا ينبغي إعادة إضافته أو استخدام `git push --mirror` لمحاولة نشر refs غير مطلوبة.

## المراجع

| ref | commit المستهدف |
|---|---|
| `quran-v1` | `1c7d2dd9f9eff507cfe7048feacfa452ec8d2f0f` |
| `quran-v1-postfix` | `50eb22e4faaafcb758b03c53a6102690880b69c0` |
| `quran-v1-writing-mode` | تحقق به: `git rev-parse quran-v1-writing-mode^{}` |

لا يجوز تغيير أو حذف الـtagين التاريخيين. رفع الإصدار الحالي يجب أن يكون محدوداً إلى `main` و`quran-v1-writing-mode` فقط.

## حالة reflog/التخزين المحلي

الفحص عبر `git rev-list --objects --all --reflog` في جلسات البناء السابقة كشف blobs صوتية كبيرة في reflog المحلي بسبب محاولة قديمة أنشأت commit صوتياً ثم أُعيد reset قبل snapshot. هذه objects ليست ضمن الشجرة الحالية أو الـrefs التي سيُدفع إليها هذا الإصدار، لكن حجم `.git` المحلي بقي كبيراً؛ لذلك لا يُستخدم `git push --mirror` ولا تُنشأ refs جديدة إلى تلك objects.

## حدود التدقيق

هذا فحص أسماء ومسارات وأحجام كائنات Git، وليس فحص حقوق ملكية. حذف Sherpa من الـtree وAPK لا يثبت حقوق Word أو خط KFGQPC أو تسجيل الحصري أو توقيتاته المشتقة. حالة التطبيق بقيت `internal testing only`.
