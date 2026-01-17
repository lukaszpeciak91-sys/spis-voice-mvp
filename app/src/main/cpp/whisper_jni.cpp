#include <jni.h>

#include <android/log.h>
#include <string>

#include "third_party/whisper.cpp/whisper.h"

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
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_myapplication_whisper_WhisperEngine_nativeTranscribe(
    JNIEnv* env,
    jobject /*thiz*/,
    jlong handle,
    jstring audio_path
) {
    whisper_context* ctx = RequireContext(handle);
    if (!ctx) {
        return env->NewStringUTF("");
    }

    const char* audio_chars = env->GetStringUTFChars(audio_path, nullptr);
    __android_log_print(
        ANDROID_LOG_INFO,
        kLogTag,
        "Transcribing audio at path: %s (expects mono 16kHz PCM16)",
        audio_chars
    );
    env->ReleaseStringUTFChars(audio_path, audio_chars);

    whisper_full_params params = whisper_full_default_params(0);
    whisper_full(ctx, params, nullptr, 0);

    if (whisper_full_n_segments(ctx) <= 0) {
        return env->NewStringUTF("");
    }
    const char* text = whisper_full_get_segment_text(ctx, 0);
    return env->NewStringUTF(text ? text : "");
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
