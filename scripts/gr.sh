#!/bin/sh
# Build helper. Usage: sh scripts/gr.sh <logfile> <gradle args>. Writes "DONE rc=N" at the end of the log.
T=/downloads/quran-study/toolchain
export JAVA_HOME=$T/jdk21 ANDROID_HOME=$T/android-sdk ANDROID_SDK_ROOT=$T/android-sdk
export RECLONE_KS=/downloads/quran-study/keys/reclone-release.jks
export RECLONE_KS_PASS="$(cat /downloads/quran-study/keys/reclone-release.jks.password.txt)"
export GRADLE_OPTS="-Xmx700m"
LOG=$1; shift
cd /downloads/quran-study/original-reclone
echo "sdk.dir=$T/android-sdk" > local.properties
$T/gradle/bin/gradle -q --no-daemon -Pkotlin.compiler.execution.strategy=in-process "$@" > $LOG 2>&1
echo "DONE rc=$?" >> $LOG
