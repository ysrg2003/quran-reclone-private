#include <jni.h>
#include <string>
#include <vector>
#include "whisper.h"

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_quranoffline_data_WhisperNative_nativeInit(JNIEnv *env, jobject, jstring path) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(p, cp);
    env->ReleaseStringUTFChars(path, p);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_example_quranoffline_data_WhisperNative_nativeFree(JNIEnv *, jobject, jlong handle) {
    if (handle != 0) whisper_free(reinterpret_cast<whisper_context *>(handle));
}

JNIEXPORT jstring JNICALL
Java_com_example_quranoffline_data_WhisperNative_nativeTranscribe(
        JNIEnv *env, jobject, jlong handle, jfloatArray pcm, jint threads) {
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx == nullptr) return env->NewStringUTF("");
    jsize n = env->GetArrayLength(pcm);
    std::vector<float> samples(n);
    env->GetFloatArrayRegion(pcm, 0, n, samples.data());
    whisper_full_params wp = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wp.language = "ar";
    wp.translate = false;
    wp.n_threads = threads > 0 ? threads : 4;
    wp.no_context = true;
    wp.single_segment = false;
    wp.print_progress = false;
    wp.print_realtime = false;
    wp.print_special = false;
    wp.print_timestamps = false;
    wp.suppress_blank = true;
    wp.temperature = 0.0f;
    wp.temperature_inc = 0.0f;   // no fallback re-decode: deterministic and faster
    wp.greedy.best_of = 1;
    if (whisper_full(ctx, wp, samples.data(), (int) samples.size()) != 0) return env->NewStringUTF("");
    std::string out;
    int segs = whisper_full_n_segments(ctx);
    for (int i = 0; i < segs; i++) {
        const char *t = whisper_full_get_segment_text(ctx, i);
        if (t) { if (!out.empty()) out += " "; out += t; }
    }
    return env->NewStringUTF(out.c_str());
}

}
