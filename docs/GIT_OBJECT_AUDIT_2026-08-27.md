# Git object audit — 2026-08-27

## النتيجة الحالية

فُحصت شجرة `quran-v1-writing-mode` التاريخية ثم شجرة الإصلاح `quran-v1-writing-mode-fix` و`main`. لا تحتوي هذه الأشجار على ملفات `.mp3` أو `.m4a` أو APK أو ZIP model، ولا أسماء `sherpa` أو `onnx` أو `fastconformer`. يشير tag الإصلاح إلى commit `2d73c53727a44001443aa5e1deb6cb4a19c17bbc`، ونُشر كـGitHub prerelease مستقل بعد رفع APKين خارج Git.

```text
git ls-tree -r --name-only quran-v1-writing-mode-fix | grep -E '\.(mp3|m4a|apk|zip)$|sherpa|onnx|fastconformer'
# no output
```

أُجري أيضاً `git rev-list --objects main quran-v1-writing-mode` مع `git cat-file --batch-check` لفحص الكائنات reachable. تظهر في history القديمة بعض blobs غير المطلوبة للعقد الحالي، وأكبرها AAR Sherpa القديم بحجم `37,751,439` bytes، لأنه تاريخياً جزء من parent commit قبل حذفه. هذا لا يجعله جزءاً من tree أو APK الحالي، ولا ينبغي إعادة إضافته أو استخدام `git push --mirror` لمحاولة نشر refs غير مطلوبة.

## المراجع

| ref | commit المستهدف |
|---|---|
| `quran-v1` | `1c7d2dd9f9eff507cfe7048feacfa452ec8d2f0f` |
| `quran-v1-postfix` | `50eb22e4faaafcb758b03c53a6102690880b69c0` |
| `quran-v1-writing-mode` | artifact السابق؛ تحقق به: `git rev-parse quran-v1-writing-mode^{}` |
| `quran-v1-writing-mode-fix` | `2d73c53727a44001443aa5e1deb6cb4a19c17bbc`؛ GitHub prerelease مستقل |
| `main` | قد يحتوي متابعة توثيقية لاحقة؛ تحقق به وقت الفحص عبر `git rev-parse main` |

لا يجوز تغيير أو حذف الـtagين التاريخيين أو `quran-v1-writing-mode`. رُفع الإصلاح محدوداً إلى `main` و`quran-v1-writing-mode-fix` فقط، بينما بقيت APKs الكبيرة Release assets خارج Git history.

## حالة reflog/التخزين المحلي

الفحص عبر `git rev-list --objects --all --reflog` في جلسات البناء السابقة كشف blobs صوتية كبيرة في reflog المحلي بسبب محاولة قديمة أنشأت commit صوتياً ثم أُعيد reset قبل snapshot. هذه objects ليست ضمن الشجرة الحالية أو الـrefs التي سيُدفع إليها هذا الإصدار، لكن حجم `.git` المحلي بقي كبيراً؛ لذلك لا يُستخدم `git push --mirror` ولا تُنشأ refs جديدة إلى تلك objects.

## حدود التدقيق

هذا فحص أسماء ومسارات وأحجام كائنات Git، وليس فحص حقوق ملكية. حذف Sherpa من الـtree وAPK لا يثبت حقوق Word أو خط KFGQPC أو تسجيل الحصري أو توقيتاته المشتقة. حالة التطبيق بقيت `internal testing only`.
