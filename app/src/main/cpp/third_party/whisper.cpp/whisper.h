#pragma once

#ifdef __cplusplus
extern "C" {
#endif

struct whisper_context;

struct whisper_full_params {
    int dummy;
};

whisper_full_params whisper_full_default_params(int strategy);

whisper_context* whisper_init_from_file(const char* path_model);

void whisper_free(whisper_context* ctx);

int whisper_full(
    whisper_context* ctx,
    whisper_full_params params,
    const float* samples,
    int n_samples
);

int whisper_full_n_segments(whisper_context* ctx);

const char* whisper_full_get_segment_text(whisper_context* ctx, int index);

#ifdef __cplusplus
}
#endif
