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

## v23 (c68383d, tag apk-v23)
- 218 tests pass (JVM), 4 skipped. Same release key. Device untested.
- Reveal: WordDisplayRules.shown no longer treats UNCERTAIN (every unsaid word) as judged. Tasmee start sets hidden=true unless the user turned text on during a previous tasmee. showPage keeps hidden while listening. In the full-screen reader, while tasmee runs everything shown is hidden and, with a selection, only the selected ayahs are shown.
- Actions circle: ActionsCircle (ReaderUi.kt) shows while a selection exists; buttons act on the whole selection; middle button taps close+clear, drag moves; size setting stored under new key actionCircleDp (default 34, 22..64). The small x selection circle was removed.
- Khatma: line for the whole Quran (6236 ayahs) at the daily wird, from day zero.
- Reader: READER_EDGE_MARGIN_DP=4 around the page (plus 2dp column padding).
- Numbering: quran.com API puts the end marker of 566 of 6236 ayahs on the line after the last word; QuranComLayoutBuilder now puts the number after the last word. Downloaded layout file renamed to _v2, old _v1 file deleted on first use and the download retried; the bundled layout (all 6236 markers verified in order) shows meanwhile.

## v23.1 (e987e1f, tag apk-v23.1)
- 220 tests pass (JVM), 4 skipped. Same release key. Device untested.
- Numbering digits: stored marker text is correct for all 6236 (bundled layout, checked by test; quran.com builder uses arabicDigits). On the phone the font drew 12 as 21 and 10 as a dot outside the circle, which matches shaping of the reversed string (reproduced with the real font offline). WordReferenceLine.displayText() reverses the digits of each 2+ digit marker in the string given to Text only; length and offsets unchanged, so selection, hit-testing and highlights are unaffected. Single digits and palindromes unchanged. Not seen on a phone.

## v23.2 (b24ca19, tag apk-v23.2)
- 220 tests pass (JVM), 4 skipped. Same release key. Device untested.
- Reader margin: READER_EDGE_MARGIN_DP=8. Vertical padding 8dp; horizontal padding 6dp plus the 2dp column padding in ImmersiveReader = 8dp total on left and right. Top and bottom bars unchanged (user: second screenshot only showed the page number).

## v23.3 (bcf11c7, tag apk-v23.3)
- 222 tests pass (JVM), 4 skipped. Same release key. Device untested; smoothness is by code reading, not profiled on a phone.
- Tasmee lag (code reading): every recognizer update ran page() (filter over 6236 ayahs) on the main thread and gave the UI a new ayah list, and the live word went to every AyahBlock, so the whole page (about 150 Surface+Text word nodes, each building an AnnotatedString) recomposed several times per second. Now: same list when the page is unchanged, live word only to the block that holds it (and surah openings for the basmala), word AnnotatedStrings memoized, onlySpoken pref read once per block, page key list hoisted. Tasmee logic and colours untouched.
- Margin: READER_EDGE_MARGIN_DP=12 (vertical 12dp, horizontal 10dp + 2dp column padding = 12dp).
- Pages 1 and 2: bundled layout and preview layout re-lined as the user's pictures (Fatiha 7 text lines with the ayah marker wrapping to the start of the next line; Baqarah first page 7 lines after the basmala). WordReferenceLayout.withModelPages keeps them when the quran.com layout is used. Ornamental frames and surah banners from the pictures were not copied.

## v24 (b099d68, tag apk-v24)
- 229 tests pass (JVM), 4 skipped. Same release key. Device untested.
- New Appearance.kt: AppearanceStore (SharedPreferences quran_appearance, read once at launch), presets, free accent/paper/text, tab order (invalid saved order falls back to default). Applied to theme primary, top bars, tab row, immersive reader paper, mushaf card paper, line text, highlights (dark variants on dark paper), tab-0 ayah card. Words with a tasmee status keep dark text on the green/red/yellow backgrounds. Other cards stay white (no full dark mode).
- Juz tab: tap expands surahs of the juz (data/JuzSurahs.kt from metadata, partial ranges marked); tapping opens ayah 1 via selectSurah, otherwise the ayah's mushaf page via openSurahAt.

## v24.1 (0e38054, tag apk-v24.1)
- Tests: see VERSION_INDEX row (JVM). Device untested.
- Report export: data/LogExport.kt (xlsx and docx as zip+XML via java.util.zip, RTL sheet/table), ReportPdf.kt (platform PdfDocument + StaticLayout). Generated on Dispatchers.IO into cache/exports, shared through FileProvider ({appId}.exports) and the share sheet. Sessions and corrected errors (full store, not the 5 shown). No new dependency. xlsx/docx structure tested in JVM; PDF and Arabic shaping only checkable on a phone.
- Pages 1-2: WordReferenceLineView centered=isOpeningPage: TextAlign.Center and no stretch.
- Markers: reference page 6 (ayahs 30-37) matches quran.com end-marker lines exactly (30, 32, 34 on the next line). QuranComLayoutBuilder (v23 hack removed) keeps the marker on its reported line, leading that line. Cache renamed quran_com_layout_v3.json; v1/v2 files dropped once and re-downloaded. The bundled Word layout has different line breaks and is unchanged (shows until the download finishes or when offline).

## v24.2 (8d25c0d, tag apk-v24.2)
- Device untested. JVM benchmark (TasmeeBenchmarkTest, needs /tmp/quran_words.tsv, skipped otherwise): real Quran text, 1500 words recited from the start with the whole-Quran tracker: stable update avg 96 ms before, 19 ms after; partial 0.16-0.2 ms. Hot spots found by stack sampling: Regex objects rebuilt on each call and the same Arabic words re-normalised in RecitationMatcher (arabicSpellingForms, writtenSpellingForms, removeSilentMarkedLetters), Muqattaat.pronunciationFor Regex. Fix: hoisted Regexes + per-string memo (pure functions, results identical). isClearJump still scans later ayahs per mismatching token (left as is).
- Partial results: handled at most every 120 ms (latest kept), finals/segments immediately. TasmeePerf records per-update main-thread time; exported as a table in the Log report.
- Log report: previously read the stores again (both files the user sent held headers only); now uses the same lists the Log screen shows, plus a save-marks table and counts in titles. Root cause of the empty files is NOT proven: both stores used by the Log screen are the ones the export read. The likeliest gap is that the user's records are the save marks, which were never exported.
- Colours: deep accent shade (20% darker) for the two tab-0 boxes (موضع الحفظ الحالي, حفظ وتسميع القرآن) and the old reader card; ImmersiveReader top and bottom bars use the accent. Default accent 0xFF276456.

## v24.3 (af92f17, tag apk-v24.3)
- Device untested. Tab-0 recitation card gets a button that opens TasmeeRangeSheet (already used by the reader's radial menu). Start anchor = tasmeeAnchor() (checkpoint or first ayah of the page); options end of ayah, page, surah, juz, quarter, whole Quran, or custom from/to via the same dialog as the reader. Uses prepareTasmeeRange / prepareTasmeeCustom, which set the start and tasmeeEndIndex; a notice says where the range stops; tasmee starts with the microphone button. No page-range or juz-number entry (juz/page only as "end of current juz/page"; custom covers any surah:ayah span).

## v24.4 (tag apk-v24.4)
- Device untested. TasmeeRangeSheet gets two rows opening a from/to number dialog: pages (1-604) and juz (1-30). Start and end are typed freely, independent of the current position; reversed values are swapped; out of range shows a notice. Pure helper TasmeeRanges.span (JVM tests) gives first/last ayah; QuranViewModel.prepareTasmeeByRange starts tasmee at the first ayah and sets the end index, announced as in v24.3. The custom surah:ayah dialog was already free.
- Added TasmeeMismatchBenchmarkTest (skipped without /tmp/quran_words.tsv): with wrong tokens, stable update median 13.9 ms, p90 34 ms, max 473 ms on desktop JVM. Suspect isClearJump scanning all later ayahs on each mismatch. Not changed in v24.4.

## v24.5 (tag apk-v24.5)
- Device untested. Lag: RecitationProgressTracker.isClearJump grouped later ayahs by first word + its pronunciation reference (firstWordGroups, lazy); one matchesWord per distinct first word, full check only for members of a matching group. Same boolean result. TasmeeMismatchBenchmarkTest (desktop JVM, real text, wrong tokens injected): median 13.9 to 2.3 ms, p90 34 to 4.3 ms, max 473 to 26 ms.
- Duff: tools/gen_duff.py synthesizes res/raw/khatma_duff_1..4.wav (pure python, seeded, no samples, no licence). Pattern dum, tak, tak, dum. Setting in the Khatmat reminder card: hit count 1-4 (default 2), prefs quran_reminder_sound. One channel per count (khatma_reminders_duff_N); the chosen one is created, the old khatma_reminders_v3 and the other counts are deleted. Applies to both the HIFZ and READ wird reminders (same channel). Limit: changing the count changes channel, so any per-channel tweak the user made in system settings is lost.
- Colours: Appearance.colorScheme() derives every Material role (primary/secondary/tertiary containers, surfaceVariant, outline, surface containers, inverse) from the accent; OnDeep helpers give chips, text fields, buttons, outlined buttons readable selected/unselected/disabled/hint colours on the dark cards (tab-0 recitation card, reader card). Tan buttons use dark text.
- Tabs: DEFAULT_TAB_ORDER now Juz first (2,0,1,3,4,6); app opens on the first tab of the order (skipping the reader tab 4). A saved custom order is kept; a saved order equal to the old default (never reordered) moves to the new default.

## v24.6 (tag apk-v24.6)
- Device untested (no phone in the build box). Signed with the same release key, cert sha256 e4c456fc...9ff5e.
- Waqf word audio: root cause is the API text, not the player. At 4:70 the API returns word 4 as "ٱللَّهِ ۚ" (waqf mark after a space) and numbers its audio 004_070_004.mp3, then skips 005, so the 5th word's file is 006. QuranComAudio compared the API text verbatim, so the trailing space failed the identity check and the failure path advanced to the next word: that is the reported "word after a waqf mark plays the next word's audio". Fix: normalizedWord() (waqf marks, whitespace, alif maqsura/ya, ta marbuta/ha) is used for the identity comparison only; normalized() is unchanged so existing offline word packs keep their keys. WordPackStore.localWord uses the same tolerance and strips spaces on both sides, so packs downloaded by earlier versions still match.
- Waqf index audit, all 6,236 ayahs / 77,430 words against api.quran.com verses/by_chapter: identity failures 4,808 before, 57 after. The 57 are in 5 ayahs (2:181, 8:6, 13:37, 15:7, 27:20) where the app's word split differs from the API's (the asset splits "بَعْدَ مَا" into two words, the API keeps one). Those still refuse rather than play a guessed word. Audit script: audit/ (api snapshots + norm.py).
- Copy/share: an ayah is now wrapped as ﴿text﴾ followed by the existing ﴿number﴾ and [سورة ...] suffix; a multi-ayah selection wraps each ayah separately.
- Whole-ayah after consecutive errors: the counter only counted inside one recognizer utterance, so three consecutive errors spread over separate utterances never reached the threshold. It now counts across segments per ayah and resets on a match and on ayah change. New "إيقاف" option (value 0) turns the whole-ayah correction off; MIN_ERROR_THRESHOLD is 0 and shouldReadWholeAyah treats 0 as off.
- Notifications: the khatma reminder uses the phone's own default notification sound. Channels moved to khatma_reminders_system_N (no setSound); the old khatma_reminders_duff_1..4 and khatma_reminders_v3 channels are deleted, and the duff hit-count picker is gone from the Khatmat card. Note for the user: a per-channel sound he chose in system settings is lost with the old channel.
- Matcher: tanween is also accepted when the recognizer writes it as a final nun (عليما -> عليمن). The ة/ه, silent-alif, waqf/wasl and madd cases were already handled by the existing matcher and are unchanged.
- Tabs: one-time v24.6 migration forces tab 2 (Juz) to the front of the saved tab order and rewrites it, so an install that carried an older saved order (Khatmat first) now opens on Juz. The flag is tab_order_juz_first_v246; after that a deliberate reorder is kept.
- Build: GitHub Actions still cannot run on the private repo (22 of 23 runs died in 1-3 s with zero steps and no runner; the only success was the 4-minute public window). Built locally with gradle -Xmx700m --no-daemon, in-process Kotlin.
