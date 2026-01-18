#include <jni.h>

#include <android/log.h>
#include <algorithm>
#include <cstdint>
#include <string>
#include <thread>
#include <vector>

#include "whisper.h"

namespace {
constexpr const char* kLogTag = "WhisperJNI";

whisper_context* RequireContext(jlong handle) {
    return reinterpret_cast<whisper_context*>(handle);
}
}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_myapplication_whisper_WhisperEngine_nativeInit(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring model_path
) {
    const char* path_chars = env->GetStringUTFChars(model_path, nullptr);
    whisper_context* ctx = whisper_init_from_file(path_chars);
    env->ReleaseStringUTFChars(model_path, path_chars);
    if (!ctx) {
        __android_log_write(ANDROID_LOG_ERROR, kLogTag, "Failed to init whisper context.");
        return 0;
    }
    __android_log_write(ANDROID_LOG_INFO, kLogTag, "Whisper model loaded.");
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_myapplication_whisper_WhisperEngine_nativeTranscribe(
    JNIEnv* env,
    jobject /*thiz*/,
    jlong handle,
    jfloatArray audio_samples
) {
    whisper_context* ctx = RequireContext(handle);
    if (!ctx) {
        return env->NewStringUTF("");
    }

    if (!audio_samples) {
        __android_log_write(ANDROID_LOG_ERROR, kLogTag, "Audio samples were null.");
        return env->NewStringUTF("");
    }

    const jsize sample_count = env->GetArrayLength(audio_samples);
    if (sample_count <= 0) {
        __android_log_write(ANDROID_LOG_ERROR, kLogTag, "Audio samples were empty.");
        return env->NewStringUTF("");
    }

    std::vector<float> samples(static_cast<size_t>(sample_count));
    env->GetFloatArrayRegion(audio_samples, 0, sample_count, samples.data());

    whisper_full_params params = whisper_full_default_params(0);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.translate = false;
    params.n_threads = std::max(1u, std::min(4u, std::thread::hardware_concurrency()));
    __android_log_print(
        ANDROID_LOG_INFO,
        kLogTag,
        "Transcription started with %d samples.",
        sample_count
    );
    int result = whisper_full(ctx, params, samples.data(), sample_count);
    if (result != 0) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Transcription failed with code: %d", result);
        return env->NewStringUTF("");
    }

    if (whisper_full_n_segments(ctx) <= 0) {
        __android_log_write(ANDROID_LOG_INFO, kLogTag, "Transcription finished with no segments.");
        return env->NewStringUTF("");
    }
    std::string transcript;
    const int segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < segments; ++i) {
        const char* text = whisper_full_get_segment_text(ctx, i);
        if (text) {
            transcript += text;
        }
    }
    __android_log_write(ANDROID_LOG_INFO, kLogTag, "Transcription finished.");
    __android_log_print(
        ANDROID_LOG_INFO,
        kLogTag,
        "Returned transcription length: %zu",
        transcript.size()
    );
    return env->NewStringUTF(transcript.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_myapplication_whisper_WhisperEngine_nativeRelease(
    JNIEnv* /*env*/,
    jobject /*thiz*/,
    jlong handle
) {
    whisper_context* ctx = RequireContext(handle);
    if (!ctx) {
        return;
    }
    whisper_free(ctx);
}
