# v12 regression checklist vs v11 (b57b62c)
Change surface: new files AudioPackStore, AudioDownloader, AudioDownloadManager, AudioPackSizes, AudioDownloadSettings; ReferenceAudioPlayer (ayah reference branch only); MainActivity (one Settings card). No change to recognition, matcher, text, tracker.
Automated: full online unit suite 160 tests, 0 failures (v11 suite + 6 new AudioDownloaderTest).
Manual on device (UNTESTED by me, no device available):
1. Fresh start: app opens, RTL, text and tracker unchanged.
2. No download: tap ayah audio, it streams as in v11.
3. Word audio plays online as in v11 (not downloadable in v12).
4. Tasmee with SpeechRecognizer: same behavior as v11.
5. Settings: new "إعدادات التنزيل" card; existing cards unchanged.
6. Download Al-Fatiha, airplane mode, ayah audio plays offline.
7. Pause/resume, delete one surah, delete all, Wi-Fi-only toggle with mobile data.
8. Uninstall removes files (private storage).

## v14 additions
- Reciter picker (Settings download card and reader screen). Default stays reciter 6 (Husary murattal) = v11 source. Pick another and streaming/ downloaded files use it.
- Downloads per reciter in separate private folders; reciter 6 folder unchanged from v12.
- Manual: switch reciter in reader, play ayah (streams); download a surah for reciter 7, airplane mode, plays offline; switch back to 6, uses 6's files only.

## v15 additions
- Word audio per surah (private, with identity index), entire-Quran download (ayah audio for chosen reciter, or all words), confirmation with sizes and free space, pause/resume.
- Manual: word tap plays local file when downloaded and streams otherwise; word audio with a mismatched count/text is never played locally; bulk pause then resume continues; low-space stops with message.

## v16 additions
- Hide-ayahs mode: new setting "show only spoken words" (default ON): unsaid words render nothing instead of a dot. Colours unchanged (green/red/yellow), a wrong word shows the correct text in red. Turn the setting off for the exact v11 view (dots).
- Manual: hide mode in tasmee and reader; say a word right (green), wrong (red, correct text, correction audio plays), skip a word.

## v17 immersive reader
- Full-screen reader overlay (ImmersiveReader.kt), opened by a Fullscreen button in the top bar. Single tap on the page toggles top/bottom bars. Back exits.
- Normal mode unchanged: onPlainTap is null there, so tap behaviour is the old one.
- 178 unit tests, 0 failures. No on-device test.

## v17.1 freeze/lag audit
- Whisper stop() no longer blocks the main thread (was join up to 8 s); worker posts last decode then Stopped. Next session waits for previous worker so the mic is free.
- Immersive mode no longer keeps the normal Scaffold page composed underneath (double rendering on each tasmee update).
- Download progress: file-existence counting moved off the main thread (was ~N^2 file checks on main per surah).
- 178 tests, 0 failures. No on-device profiling.

## v18 swipe, quran.com layout, rotation
- Page text auto-fits screen width (no sideways scroller at default size) so swipe works over the text; works in portrait and landscape. Text scale above 1.0 re-enables sideways scroll (swipe then only from page margins).
- Optional quran.com page/line layout (Settings), downloaded on request, validated (6236 ayahs once, in order, 604 pages), applied after restart. Bundled Word layout stays the default and fallback.
- Per-line word ranges so green/red highlight only touches words on that line (new layout only).
- Auto-rotate on (fullUser + configChanges, activity not recreated, tasmee keeps running).
- 182 tests, 0 failures. No on-device test.

## v18.1 edge page-swipe and automatic quran.com layout
- At the sideways-scroll edge, a further drag changes the page (same 80px rule as swipe).
- quran.com layout downloads automatically on first launch with internet (private storage), validated, applied without restart when idle (not listening/playing/downloading). Falls back to Word layout offline or on any failure. "Back to Word layout" stops auto-download.
- 182 tests, 0 failures. No on-device test.

## v18.2 (5ce9120, tag apk-v18.2)
- Unit tests: 182 pass, 0 failures. Release signed with the same key (6688c267...d8a8 15). Device untested.
- Perf: settings screens no longer walk files on the main thread (IO polling, single-pass per-surah counts); WordPackStore caches parsed per-surah index; layout download reuses loaded ayahs/bundled layout.
- Long-press opens the ayah actions sheet (select, set hifz start, start tasmee here), reader and immersive.
- Immersive tasmee/hidden/evaluation uses the memorization-tab rendering (same colours and word rules).
- Layout download: one-time, persisted in filesDir, no re-download once installed.

## v19.0 (a3f1b3c, tag apk-v19.0)
- 185 tests pass (3 new SaveMarks tests). Same signing key. Device untested.
- Save-marks: HIFZ and READ stored separately, last 3 each, same session (<30 min gap) updates in place; shown in history tab, tap resumes.
- Error log: on/off switch; off stops new entries.

## v19.1 (a323a4f, tag apk-v19.1)
- 189 tests pass (4 new search tests). Same signing key. Device untested.
- Search: diacritic-insensitive word/phrase, exact then substring fallback, scope whole Quran or one surah, banner counts.
- Go-to dialog: last-edited field decides target.

## v19.2 (b117a74, tag apk-v19.2)
- 193 tests pass (4 new Khatma tests). Same signing key. Device untested (notifications/alarms never run on a phone).
- New permissions: POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED.

## v19.3 (bb38175, tag apk-v19.3)
- 193 tests pass. Same signing key. Device untested.
- Settings tab removed from tab row; gear opens full-screen layer with 6 sections.

## v19.4 (65cc58e, tag apk-v19.4)
- 193 tests pass. Same signing key. Device untested.
- Perf: AyahLookup (one-time maps/offsets, built in asset prep), page turn no longer scans/copies 6236 ayahs, khatma hex encode 271us -> fast loop, search index warmed on opening search.

## v20 (787005b, tag apk-v20)
- 193 tests pass. Same signing key. Device untested; layout/radial positions not visually verified.
- Reading tab opens the full-screen reader; page full-bleed, lines spread over screen height.
- Surah cartouche drawn natively. Radial menu (6 actions) at touch point. Listen-to sheet with custom range and repeat 1-5. Surah-tab search.

## v20.1 (3beeeda, tag apk-v20.1)
- 193 tests pass. Device untested. NEW signing key (release keystore, backed up to Drive); not installable over v20 (different key).
- Radial: play/tasmee/copy/share use the selection (contiguous range) or the tapped ayah; tasmee stops after the last selected ayah.
- Source recovered after box wipe: replay of 265 logged commands on v11 bundle; libwhisper_jni.so restored from v20 APK; AudioPackSizes re-measured (1229657563 bytes total, identical to before).

## v20.2 (b737eae, tag apk-v20.2)
- 196 tests pass (3 new estimate tests), 4 skipped. Same release key as v20.1. Device untested (alarms, notifications, reciter menu never run on a phone).
- Reminders: setExactAndAllowWhileIdle (+ USE_EXACT_ALARM / SCHEDULE_EXACT_ALARM<=32), channel created before use (HIGH), re-armed after each fire, boot, app update, time and timezone change, and whenever the Khatmat screen opens. Test-notification button and status line.
- Khatma: finish estimate by real pace and by daily target.
- Copy/share: each ayah followed by its number. Selection bottom bar removed (Back cancels selection until the circle exists in v21).

## v21 (84d6948, tag apk-v21)
- 199 tests pass (SelectionRange, ayahSegments tests added), 4 skipped. Same release key. Device untested: touch hit-testing, circle position, justified stretch and the range sheet were never seen on a screen.
- Tap selects the ayah under the finger via text-offset segments split at ayah-end markers. Base (long-pressed) ayah stays; tap before it moves the start side, after it moves the end side, on it collapses.
- Tasmee hides only selected ayahs when a selection exists. Mic with selection starts at its first ayah and stops after the last; mic without selection opens a range sheet (from current position).

## v21.1 (0e8f6d7, tag apk-v21.1)
- Same release key. Device untested.
- Root cause of line-wide selection: the bundled line data lists only some ayahs per line (an ayah starting mid-line is missing from ayahKeys). Segments now come from end markers.
- New res/raw/khatma_duff.wav (synthesized in-house), channel khatma_reminders_v3. Repeat up to 3 times while wird not done. Wird units ayahs/pages/surahs (pages and surahs approximate).
- Hide ayahs: with a selection on another page the old rule hid nothing; now hides the whole page unless a selected ayah is on it.

## v22 (54a2378, tag apk-v22)
- 212 tests pass (JVM), 4 skipped. Same release key. Device untested.
- ParsedCache: first launch parses the JSON as before, then writes parsed Quran text (with recognizer forms), pronunciation reference and bundled layout to files/parsed_cache/*.bin (atomic temp+rename, on the loading thread already off main). Next launches read the binary. Header = magic, format, app install time, expected size; end marker; any mismatch, truncation, exception or old app build => delete and rebuild from JSON. Downloaded quran.com layout still takes precedence as before.
- JVM timing on synthetic full-size data (6236 ayahs, 77k words): Quran text 7.0 MB read 86 ms, pronunciation 4.7 MB read 227 ms (cold JVM). Real JSON parse time was not measured on a phone.
- Khatma reminder repeat: interval in minutes (1..1440) and repeat count (1..50) typed by the user; empty interval = no repeat.
- Hide ayahs: hides the whole shown page and the opening basmala (v21.1 hid only the selected ayah(s), so selecting the one-word first ayah of Al-Baqarah looked like only the basmala hid). Flag HIDE_ONLY_SELECTED restores the old behaviour. Reading-mushaf basmala line now follows hide too.

## v22.1 (7916183, tag apk-v22.1)
- Same release key. Device untested.
- Hide ayahs (full-screen reader): hides exactly the selected ayahs' words (ayah 1 also hides its basmala box); no selection or a selection on another page hides nothing; the hide button with no selection shows a hint toast and does not switch modes. Flag HIDE_ONLY_SELECTED=false gives the v22 whole-page behaviour. Memorization tab 0 hide button is unchanged (whole page).
- Mushaf basmala line no longer follows hide (that view is not shown while hide is on).
