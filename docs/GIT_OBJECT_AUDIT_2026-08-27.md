# Git object audit — 2026-08-27

## النتيجة

تم فحص المستودع `/home/ubuntu/quran-github-restore` قبل أي push. tag `quran-v1` موجود محلياً ويشير إلى commit `1c7d2dd`. ناتج:

```text
git ls-tree -r --name-only quran-v1 | grep -E 'husary(_aac)?/.*\.(mp3|m4a)$'
# no output
```

أي أن snapshot نفسه لا يحتوي ملفات MP3 أو M4A في tree. كما فُحصت كل objects المرتبطة بالـrefs عبر `git rev-list --objects --all` و`git cat-file --batch-check`؛ أكبر blob مرتبط بالـrefs هو sherpa AAR بحجم 37,751,439 bytes، ثم pronunciation/timing/layout، ولم يظهر صوت MP3/M4A في هذه المجموعة.

## حالة reflog/التخزين المحلي

`git rev-list --objects --all --reflog` كشف blobs صوتية كبيرة، منها MP3 وM4A، لأن محاولة سابقة أنشأت commit صوتياً ثم أُعيد reset قبل إنشاء snapshot. لذلك حجم `.git` المحلي ما زال تقريباً `3.4G`، وpack size تقريباً `3.36 GiB`. هذه objects ليست ضمن refs الحالية التي سيدفعها Git عادةً، لكن لا ينبغي تشغيل `git push --mirror` أو إنشاء ref جديد إليها.

```text
reachable refs: no audio paths in quran-v1 tree
largest ref-reachable blob: 37,751,439 bytes sherpa AAR
including reflogs: old MP3/M4A blobs present
.git: 3.4G
pack: 3.36 GiB
```

## قرار الرفع

لا يوجد audio blob في `main` أو `quran-v1` بحسب الفحص الحالي، لذلك لا توجد بوابة تمنع push بسبب tree الحالية. مع ذلك، يجب استخدام push محدود للفرع والـtags المطلوبة فقط، وعدم دفع reflog أو refs محلية غير معروفة. لا تُخزن MP3/M4A داخل Git history؛ تبقى أصول Offline ignored وتُوزع، إن سمحت الحقوق، كـRelease artifact خارجي.

قبل الرفع النهائي أعد تشغيل الفحص بعد commit الإصلاح، وسجّل أي object جديد يتجاوز حجم الأصول المتوقعة. لا تحذف reflog أو تعمل prune قسرياً ضمن هذه الجولة لأن ذلك إجراء destructive غير مطلوب لإثبات سلامة refs.
