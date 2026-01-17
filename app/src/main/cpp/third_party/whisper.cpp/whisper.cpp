#include "whisper.h"

#include <string>

struct whisper_context {
    std::string model_path;
    std::string last_result;
};

whisper_full_params whisper_full_default_params(int /*strategy*/) {
    whisper_full_params params{};
    return params;
}

whisper_context* whisper_init_from_file(const char* path_model) {
    auto* ctx = new whisper_context();
    if (path_model) {
        ctx->model_path = path_model;
    }
    return ctx;
}

void whisper_free(whisper_context* ctx) {
    delete ctx;
}

int whisper_full(
    whisper_context* ctx,
    whisper_full_params /*params*/,
    const float* /*samples*/,
    int /*n_samples*/
) {
    if (!ctx) {
        return -1;
    }
    ctx->last_result = "[stub] whisper.cpp transcription placeholder";
    return 0;
}

int whisper_full_n_segments(whisper_context* ctx) {
    return ctx ? 1 : 0;
}

const char* whisper_full_get_segment_text(whisper_context* ctx, int /*index*/) {
    return ctx ? ctx->last_result.c_str() : "";
}
