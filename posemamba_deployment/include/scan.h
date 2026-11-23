#pragma once
#include <stddef.h>
#include <stdint.h>

// Candidate ABI v1: real FP32, contiguous BDL / DN / BGNL, grouped variable B/C.
// Caller owns buffers of the documented size. No overlapping input/output buffers.
// No internal gating z, complex A, FP16, training/backward, or CUDA prefix scratch.
#ifdef __cplusplus
extern "C" {
#endif
typedef struct {
    int64_t batch, channels, length, state, groups;
    int32_t softplus; // 0 or 1; beta=1, threshold=20
    int32_t threads;  // 1..64, parallel independent channels only
    int32_t variant;  // 0=strided scalar, 1=packed scalar, 2=packed SIMD4
} ScanConfig;

// u/delta: [batch,channels,length], A: [channels,state]
// B/C: [batch,groups,state,length]; groups must divide channels.
// skip/bias: [channels], nullable means zero.
// initial/final state: [batch,channels,state], nullable means zero/not requested.
// y: [batch,channels,length]. Outputs must not alias each other or inputs.
// Returns 0 on success; otherwise writes a bounded error string.
int scan_run(const ScanConfig* config,
             const float* u, const float* delta, const float* A,
             const float* B, const float* C, const float* skip, const float* bias,
             const float* initial_state, float* y, float* final_state,
             char* error, size_t error_capacity);
const char* scan_simd_backend(void);
#ifdef __cplusplus
}
#endif
