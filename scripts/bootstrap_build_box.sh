#!/bin/sh
# The sandbox box can be reset between runs. Rebuild it: toolchain (JDK21, Gradle 8.7, Android SDK 35 / build-tools 34).
# Before: keystore + password in /downloads/quran-study/keys (Drive: reclone-release.jks, reclone-release.jks.password.txt),
# repo cloned to /downloads/quran-study/original-reclone. Never commit the keystore or the token.
set -e
T=/downloads/quran-study/toolchain; mkdir -p $T; cd $T
curl -sSL -o jdk21.tgz "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" &
curl -sSL -o gradle.zip https://services.gradle.org/distributions/gradle-8.7-bin.zip &
curl -sSL -o cmd.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip &
wait
tar xzf jdk21.tgz && mv jdk-21* jdk21 && unzip -q gradle.zip && mv gradle-8.7 gradle
mkdir -p android-sdk/cmdline-tools && unzip -q cmd.zip -d android-sdk/cmdline-tools && mv android-sdk/cmdline-tools/cmdline-tools android-sdk/cmdline-tools/latest
rm -f jdk21.tgz gradle.zip cmd.zip
export JAVA_HOME=$T/jdk21
yes | android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=$T/android-sdk --licenses >/dev/null 2>&1 || true
android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=$T/android-sdk "platforms;android-35" "build-tools;34.0.0" "platform-tools" >/dev/null 2>&1
