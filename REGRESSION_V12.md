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
