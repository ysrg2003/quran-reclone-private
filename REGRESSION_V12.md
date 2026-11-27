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
