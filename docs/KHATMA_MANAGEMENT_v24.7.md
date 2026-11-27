# v24.7 plan: Khatmat management page

Status: not implemented. This file is the spec the next build run works from.

## What the user asked for (Telegram 26668855 / 26595019 batch, forwarded by main)
A Khatmat management page: add, edit and delete khatmat. Adding one opens a page like the
current khatma page (same fields: target, unit, reminder, repeat) so the new khatma is set up
the same way the existing ones are.

## Current state (v24.6)
Khatmat are hard-coded to two kinds, `SaveMarkKind.HIFZ` and `SaveMarkKind.READ`, stored in
SharedPreferences "khatmat" with per-kind keys (`$kind.covered`, `$kind.target`, ...) by
`LocalKhatmaStore` (data/Khatma.kt). `KhatmaScreen.kt` renders exactly two cards and every
callback (`onTarget`, `onReminder`, `onOpen`, `onTargetUnit`, `onRepeat`) is keyed by
`SaveMarkKind`. Recitation/reading tracking writes through the same kind-keyed store, so the
tracker never needs to know which named khatma is active.

## Design that does not disturb tracking
1. Data: `data class KhatmaRecord(val id: String, val name: String, val kind: SaveMarkKind, val state: KhatmaState)`.
2. Store: `LocalKhatmaListStore`, prefs "khatmat_v2", one JSON array of records, each record's
   `KhatmaState` serialised with the same field names the old store used (covered/today as the
   existing base64 bitset string, so `KhatmaLogic.encode/decode` is reused unchanged).
3. Active record per kind: `active_id_HIFZ` and `active_id_READ`. Tracking keeps writing to the
   active record of its kind, so `QuranViewModel` and the reader stay kind-keyed and unchanged.
4. Migration, once, flag `khatmat_v2_migrated`: read the two existing kinds from
   `LocalKhatmaStore`, create one record each with the current Arabic names
   ("ختمة التسميع والحفظ", "ختمة القراءة والاستماع"), copy every field verbatim, set both as
   active. Never delete the old "khatmat" prefs, so a rollback to v24.6 still finds its data.
5. UI: `KhatmaScreen` becomes a list (name, progress bar, "today x of y", active badge) plus a
   "＋ ختمة جديدة" button. Tapping a row opens `KhatmaDetailScreen` = today's `KhatmaCard`
   content plus: rename field, "تعيين كختمة نشطة لهذا النوع", and "حذف الختمة" with a
   confirmation that says the covered progress of that khatma is lost. Adding opens the same
   detail screen with empty state and a name field, and saves on "حفظ".
6. Guard: deleting the last record of a kind is refused with a notice (tracking needs one), or
   a fresh empty record of that kind is created automatically. Pick the refusal: it is clearer.

## Tests to add (JVM, no Android)
- Migration copies both kinds field-for-field, including covered/today bitsets, reminder times
  and repeat settings, and is idempotent (second load does not duplicate).
- Add / rename / delete keep the other records untouched.
- Deleting the active record of a kind moves the active pointer to another record of that kind.
- Deleting the last record of a kind is refused.
- A record round-trips through JSON unchanged.

## Known risks
- Deleting a khatma destroys its covered bitset; there is no undo, so the confirmation text must
  say so plainly.
- The reader/tasmee code paths assume one khatma per kind; the active-record pointer is what
  keeps that true. Any new code that reads `LocalKhatmaStore` directly must be found and moved to
  the list store (grep for `LocalKhatmaStore(` before building).
