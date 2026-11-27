# RECOVERY: Quran study app reclone (private)

No secrets are in this file. Last updated 2026-11-27 (after the build box was wiped once).

App id `com.example.quranoffline.apiaudioreclone`. Flavors: online, offline. Compose UI, whole app RTL.

## What lives where

| Item | Location | How to get it |
|---|---|---|
| Source code, all commits, tags apk-v12 to apk-v20.1 | Private GitHub repo `ysrg2003/quran-reclone-private`, branch `main` | `git clone https://github.com/ysrg2003/quran-reclone-private.git` (needs the GitHub login in the Instinct vault, entry "GitHub PAT for Yousef reels stock git uploads"). |
| Source bundle (offline copy) | Drive folder `1pPFP2ascGIQfE9uERpv-sgBEMtiiWIAY`, files `quran-reclone-full-v20.1wip.bundle.part00..02` and `SOURCE_BUNDLE_v20.1wip_SHA256.txt` | Download all parts, `cat ...part* > b.bundle`, check the sha256 in the txt, `git clone b.bundle src`. This one is the state at v20.1 work-in-progress. Newer work is only on GitHub. |
| Old v11 bundle | Drive: `quran-reclone-source-20261126-2354.bundle.part00..02` | Same steps. Stops at v11 (old). |
| Release keystore | Drive folder above: `reclone-release.jks` (alias `reclone`) | Download. Never commit it. |
| Keystore password | Drive folder above: `reclone-release.jks.password.txt` | Download. Move it somewhere safer than next to the keystore if you can. |
| Signed APKs, one per release | Drive folder above, `quran-reclone-*-v<N>-private.apk` | Each APK is listed in `VERSION_INDEX.md` with sha256, size, commit and tag. v12 to v20 are signed with the OLD debug key (lost). v20.1 and later use the new release key (cert sha256 `e4c456fc66939c6325619cd5013bd599fbf0da679c9a73fbdc18f769fec9ff5e`). The two keys differ, so v20 to v20.1 needs an uninstall. |
| Release log | Drive: `VERSION_INDEX.md`, `REGRESSION_V12.md` | Download the newest copy (by modified time). |
| Per-surah audio sizes | Generated into `data/AudioPackSizes.kt` (in git) | Source of truth is the file in git. To re-measure: HEAD each `https://mirrors.quranicaudio.com/everyayah/Husary_64kbps/SSSAAA.mp3`, sum `Content-Length` per surah (total 1,229,657,563 bytes). |
| Whisper native lib | `app/src/main/jniLibs/arm64-v8a/libwhisper_jni.so` (in git) | Built once earlier, copied from the v20 APK. Not rebuilt from C++ source. |

## Build tools (not in git; reinstall)

Put them under `/downloads/quran-study/toolchain` (any path works if you edit `gr.sh`).

- JDK 21 (Adoptium Temurin): `https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse`
- Gradle 8.7: `https://services.gradle.org/distributions/gradle-8.7-bin.zip`
- Android command line tools 11076708: `https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip`, then `sdkmanager --licenses` and `sdkmanager "platforms;android-35" "platforms;android-34" "build-tools;34.0.0" "platform-tools"` with `JAVA_HOME` set to the JDK.
- Needs about 2 GB RAM. Gradle runs with `--no-daemon -Xmx700m` and the in-process Kotlin compiler.

## Build steps

Write `local.properties` with `sdk.dir=<toolchain>/android-sdk`. Then, with `JAVA_HOME`, `ANDROID_HOME` and `ANDROID_SDK_ROOT` exported:

1. Tests: `gradle -q --no-daemon -Pkotlin.compiler.execution.strategy=in-process :app:testOnlineDebugUnitTest` (193 tests expected).
2. Release APK: `gradle -q --no-daemon -Pkotlin.compiler.execution.strategy=in-process :app:assembleOnlineRelease -x lintVitalOnlineRelease -x lintVitalAnalyzeOnlineRelease -x lintVitalReportOnlineRelease`
3. Output: `app/build/outputs/apk/online/release/app-online-release.apk`
4. Verify: `<sdk>/build-tools/34.0.0/apksigner verify --print-certs <apk>` and compare the cert sha256 below.

## How to sign with the same key

The release build reads the key from the environment only (see `signingConfigs` in `app/build.gradle.kts`). Without these variables it falls back to the debug key, which is NOT the release key.

```
export RECLONE_KS=/path/to/reclone-release.jks
export RECLONE_KS_PASS=<contents of reclone-release.jks.password.txt>
```

Alias is `reclone`. Expected cert sha256: `e4c456fc66939c6325619cd5013bd599fbf0da679c9a73fbdc18f769fec9ff5e`. If the cert differs, stop: you are signing with the wrong key and the phone will refuse the update.

## Creating a new signing key (only if the release key is lost)

Use this only if `reclone-release.jks` cannot be recovered from Drive. A new key means Android refuses to install the new APK over the installed app (signature mismatch). The user must uninstall the app first, which deletes its local data (marks, khatmat, progress, downloaded audio).

```
export JAVA_HOME=<toolchain>/jdk21
PW=$(python3 -c "import secrets;print(secrets.token_urlsafe(18))")
echo "$PW" > store.pass && chmod 600 store.pass
$JAVA_HOME/bin/keytool -genkeypair -keystore reclone-release.jks -storepass "$PW" -keypass "$PW" \
  -alias reclone -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Quran Reclone Private"
$JAVA_HOME/bin/keytool -list -keystore reclone-release.jks -storepass "$PW"   # shows the SHA-256 fingerprint
```

Then:
1. Keep the alias `reclone` (the build expects it) and point `RECLONE_KS` / `RECLONE_KS_PASS` at the new files.
2. Back up `reclone-release.jks` and the password to the private Drive folder right away, before the first build. Never put either in git.
3. Build, run `apksigner verify --print-certs` and write the new cert sha256 into `VERSION_INDEX.md` and this file.
4. Tell the user to uninstall the old app before installing.
The current key (created 2026-11-27) has cert sha256 `e4c456fc66939c6325619cd5013bd599fbf0da679c9a73fbdc18f769fec9ff5e`.

## Clean-build proof (2026-11-27)

A fresh `git clone` of this repo into an empty directory was built with the steps above. Result: 193 tests, 0 failures, 4 skipped. The release APK has the same size (17,297,662 bytes) as the archived v20.1 APK, an identical file list, byte-identical contents for every file outside META-INF (the signature folder), and the same signing cert. So everything the app needs is in this repo (source, assets, fonts, `libwhisper_jni.so`, `AudioPackSizes.kt`).

## Needed only for audits, NOT in the repo (not part of the app)

The 4 skipped tests (`RecognizerSpellingCorpusTest`, `ImlaeiCorpusAuditTest`, `TrackerCorpusAuditTest`, `AsrTranscriptAuditTest`) run only when these environment variables point to data files, and skip otherwise:
- `WORDS_TSV` (words.tsv), `PAIRS_TSV` (pairs_full.tsv, imlaei_pairs.tsv), `ASR_TSV` (recognizer transcripts of Alafasy audio).
- They were generated files on the old build box and were lost in the wipe. They are not backed up. They can be regenerated from the assets in `app/src/main/assets/quran` with the scripts in `scripts/` (word and pair tables) and by running a speech recognizer over reference audio (ASR table). I have not regenerated them. They are large and derived, so they are not stored in git.
- The only effect: these four audit tests do not run until the data is rebuilt. The 189 other tests run.

## Binary pieces and how each is obtained

- `app/src/main/jniLibs/arm64-v8a/libwhisper_jni.so` (3 MB, whisper.cpp, MIT): committed in git. It was recovered from the v20 APK, so its C++ build recipe is not in this repo. To rebuild it you need the NDK and whisper.cpp source; not done.
- `AudioPackSizes.kt`: in git; re-measure with HEAD requests if the audio mirror changes (see table above).
- Quran text, layout and timing assets, fonts: in git under `app/src/main/assets`. Quran Foundation content is never redistributed from this repo; users download packs into private storage in the app.

## After every build (standing rule)

1. Copy the APK to the archive with a new name, never overwrite old ones.
2. Commit, `git tag apk-v<N>`, add a row to `VERSION_INDEX.md` and a section to `REGRESSION_V12.md`.
3. Push `main` and the tag to GitHub (use `git push <url> main apk-v<N>`; pass the token with `http.extraheader`, never put it in a remote URL or a file in the repo).
4. Upload the APK, `VERSION_INDEX.md` and `REGRESSION_V12.md` to the Drive folder. Back up the keystore only when it changes.

## Known gaps

- Old APKs v12 to v20 cannot be re-signed with their original key.
- Unit tests that use ASR and pairs data files (`ASR_TSV`, `PAIRS_TSV`, `WORDS_TSV`) were run without those files after the wipe. They passed (193).
- v12 to v20 source was rebuilt by replaying logged edit commands onto the v11 bundle. The v20.1 APK matches the v20 file list, and the dex differs only by the v20.1 change.
- The original base project is never pushed to its own origin. Only this private repo gets pushes.
