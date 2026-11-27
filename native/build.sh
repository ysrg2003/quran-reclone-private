#!/bin/sh
# Rebuild app/src/main/jniLibs/arm64-v8a/libwhisper_jni.so (arm64-v8a only).
# Usage: native/build.sh [WORK_DIR] [WHISPER_CPP_COMMIT]
# Needs: cmake, make, git, curl, unzip. Downloads NDK r26d (about 640 MB) and whisper.cpp.
set -e
WORK=${1:-/tmp/whisper-build}; COMMIT=${2:-}
HERE=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$WORK"; cd "$WORK"
[ -d android-ndk-r26d ] || { curl -L -o ndk.zip https://dl.google.com/android/repository/android-ndk-r26d-linux.zip; unzip -q ndk.zip; rm ndk.zip; }
[ -d whisper.cpp ] || git clone https://github.com/ggml-org/whisper.cpp
[ -z "$COMMIT" ] || git -C whisper.cpp checkout "$COMMIT"
NDK=$WORK/android-ndk-r26d
cmake -S "$HERE" -B build-a64 -G 'Unix Makefiles' \
  -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DCMAKE_BUILD_TYPE=Release \
  -DWHISPER_DIR=$WORK/whisper.cpp -DGGML_OPENMP=OFF
cmake --build build-a64 -j2
$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip -o libwhisper_jni_stripped.so build-a64/libwhisper_jni.so
echo "Built: $WORK/libwhisper_jni_stripped.so  (copy to app/src/main/jniLibs/arm64-v8a/libwhisper_jni.so)"
