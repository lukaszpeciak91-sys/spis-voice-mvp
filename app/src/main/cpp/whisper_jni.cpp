#include <jni.h>

#include <android/log.h>
#include <algorithm>
#include <cstdint>
#include <chrono>
#include <string>
#include <thread>
#include <vector>

#include "whisper.h"

namespace {
constexpr const char* kLogTag = "WhisperJNI";
constexpr const char* kErrorPrefix = "ERROR:";
constexpr unsigned int kDefaultThreads = 2;

whisper_context* RequireContext(jlong handle) {
    return reinterpret_cast<whisper_context*>(handle);
}

jstring NewError(JNIEnv* env, const std::string& message) {
    const std::string full = std::string(kErrorPrefix) + " " + message;
    return env->NewStringUTF(full.c_str());
}
}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_myapplication_whisper_WhisperEngine_nativeInit(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring model_path
) {
    const char* path_chars = env->GetStringUTFChars(model_path, nullptr);
    whisper_context_params ctx_params = whisper_context_default_params();
    whisper_context* ctx = whisper_init_from_file_with_params(path_chars, ctx_params);
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
        __android_log_write(ANDROID_LOG_ERROR, kLogTag, "Whisper context was null.");
        return NewError(env, "Whisper context not initialized.");
    }

    if (!audio_samples) {
        __android_log_write(ANDROID_LOG_ERROR, kLogTag, "Audio samples were null.");
        return NewError(env, "Audio samples were null.");
    }

    const jsize sample_count = env->GetArrayLength(audio_samples);
    if (sample_count <= 0) {
        __android_log_write(ANDROID_LOG_ERROR, kLogTag, "Audio samples were empty.");
        return NewError(env, "Audio samples were empty.");
    }

    std::vector<float> samples(static_cast<size_t>(sample_count));
    env->GetFloatArrayRegion(audio_samples, 0, sample_count, samples.data());

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);

    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.translate = false;
    const unsigned int available_threads = std::max(1u, std::thread::hardware_concurrency());
    params.n_threads = std::min(kDefaultThreads, available_threads);
    __android_log_print(
        ANDROID_LOG_INFO,
        kLogTag,
        "Using %u threads (available: %u).",
        params.n_threads,
        available_threads
    );
    __android_log_print(
        ANDROID_LOG_INFO,
        kLogTag,
        "Transcription started with %d samples using %u threads.",
        sample_count,
        params.n_threads
    );
    __android_log_write(ANDROID_LOG_INFO, kLogTag, "Calling whisper_full...");
    auto start = std::chrono::steady_clock::now();
    int result = whisper_full(ctx, params, samples.data(), sample_count);
    auto end = std::chrono::steady_clock::now();
    auto elapsed_ms = std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count();
    __android_log_print(
        ANDROID_LOG_INFO,
        kLogTag,
        "whisper_full returned %d in %lld ms.",
        result,
        static_cast<long long>(elapsed_ms)
    );
    if (result != 0) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Transcription failed with code: %d", result);
        return NewError(env, "Native transcription failed with code: " + std::to_string(result));
    }

    if (whisper_full_n_segments(ctx) <= 0) {
        __android_log_write(ANDROID_LOG_INFO, kLogTag, "Transcription finished with no segments.");
        return NewError(env, "Transcription produced no segments.");
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
