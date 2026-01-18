#include <jni.h>

#include <android/log.h>
#include <algorithm>
#include <cstdint>
#include <fstream>
#include <string>
#include <thread>
#include <vector>

#include "whisper.h"

namespace {
constexpr const char* kLogTag = "WhisperJNI";
constexpr int kTargetSampleRate = 16000;

whisper_context* RequireContext(jlong handle) {
    return reinterpret_cast<whisper_context*>(handle);
}

struct WavData {
    int sample_rate = 0;
    int channels = 0;
    int bits_per_sample = 0;
    std::vector<float> samples;
};

bool ReadWavFile(const std::string& path, WavData* out, std::string* error) {
    std::ifstream input(path, std::ios::binary);
    if (!input) {
        if (error) {
            *error = "failed to open audio file";
        }
        return false;
    }

    auto read_u32 = [&input](uint32_t* value) {
        input.read(reinterpret_cast<char*>(value), sizeof(uint32_t));
        return input.good();
    };

    auto read_u16 = [&input](uint16_t* value) {
        input.read(reinterpret_cast<char*>(value), sizeof(uint16_t));
        return input.good();
    };

    char riff[4] = {};
    input.read(riff, 4);
    if (std::string(riff, 4) != "RIFF") {
        if (error) {
            *error = "unsupported audio format";
        }
        return false;
    }

    uint32_t riff_size = 0;
    if (!read_u32(&riff_size)) {
        if (error) {
            *error = "invalid wav header";
        }
        return false;
    }

    char wave[4] = {};
    input.read(wave, 4);
    if (std::string(wave, 4) != "WAVE") {
        if (error) {
            *error = "unsupported audio format";
        }
        return false;
    }

    bool fmt_found = false;
    bool data_found = false;
    uint16_t audio_format = 0;
    uint16_t channels = 0;
    uint32_t sample_rate = 0;
    uint16_t bits_per_sample = 0;
    std::vector<int16_t> pcm_samples;

    while (input && (!fmt_found || !data_found)) {
        char chunk_id[4] = {};
        uint32_t chunk_size = 0;
        input.read(chunk_id, 4);
        if (!input) {
            break;
        }
        if (!read_u32(&chunk_size)) {
            break;
        }

        std::string chunk(chunk_id, 4);
        if (chunk == "fmt ") {
            fmt_found = true;
            if (!read_u16(&audio_format) ||
                !read_u16(&channels) ||
                !read_u32(&sample_rate)) {
                if (error) {
                    *error = "invalid wav format";
                }
                return false;
            }
            uint32_t byte_rate = 0;
            uint16_t block_align = 0;
            if (!read_u32(&byte_rate) || !read_u16(&block_align) || !read_u16(&bits_per_sample)) {
                if (error) {
                    *error = "invalid wav format";
                }
                return false;
            }
            if (chunk_size > 16) {
                input.seekg(chunk_size - 16, std::ios::cur);
            }
        } else if (chunk == "data") {
            data_found = true;
            if (chunk_size == 0) {
                break;
            }
            pcm_samples.resize(chunk_size / sizeof(int16_t));
            input.read(reinterpret_cast<char*>(pcm_samples.data()), chunk_size);
        } else {
            input.seekg(chunk_size, std::ios::cur);
        }

        if (chunk_size % 2 == 1) {
            input.seekg(1, std::ios::cur);
        }
    }

    if (!fmt_found || !data_found) {
        if (error) {
            *error = "unsupported audio format";
        }
        return false;
    }

    if (audio_format != 1 || channels != 1 || sample_rate != kTargetSampleRate || bits_per_sample != 16) {
        if (error) {
            *error = "unsupported audio format";
        }
        return false;
    }

    out->sample_rate = static_cast<int>(sample_rate);
    out->channels = static_cast<int>(channels);
    out->bits_per_sample = static_cast<int>(bits_per_sample);
    out->samples.reserve(pcm_samples.size());
    for (int16_t sample : pcm_samples) {
        out->samples.push_back(sample / 32768.0f);
    }
    return true;
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
    jstring audio_path
) {
    whisper_context* ctx = RequireContext(handle);
    if (!ctx) {
        return env->NewStringUTF("");
    }

    const char* audio_chars = env->GetStringUTFChars(audio_path, nullptr);
    if (!audio_chars) {
        __android_log_write(ANDROID_LOG_ERROR, kLogTag, "Audio path was null.");
        return env->NewStringUTF("");
    }
    __android_log_print(
        ANDROID_LOG_INFO,
        kLogTag,
        "Transcribing audio at path: %s (expects mono 16kHz PCM16)",
        audio_chars
    );
    std::string audio_path_string(audio_chars ? audio_chars : "");
    env->ReleaseStringUTFChars(audio_path, audio_chars);

    WavData wav;
    std::string error;
    if (!ReadWavFile(audio_path_string, &wav, &error)) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "Transcription failed: %s", error.c_str());
        return env->NewStringUTF("");
    }

    whisper_full_params params = whisper_full_default_params(0);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.translate = false;
    params.n_threads = std::max(1u, std::min(4u, std::thread::hardware_concurrency()));
    __android_log_write(ANDROID_LOG_INFO, kLogTag, "Transcription started.");
    int result = whisper_full(ctx, params, wav.samples.data(), static_cast<int>(wav.samples.size()));
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
