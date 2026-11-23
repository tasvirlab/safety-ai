// Portable FP32 selective-scan — Stage 3 reference baseline.
//
// Implements exactly the recurrence specified in ../SPEC.md, which was
// empirically confirmed to match PoseMamba's compiled CUDA kernel
// (selective_scan_cuda_core.fwd) to 7.6e-6 max abs difference on identical
// inputs. No approximation, no quantisation, no reordering of the
// mathematics. FP32 throughout.
//
// Deliberately dependency-free (C++17, libm only) so it can be compiled
// unchanged by a desktop toolchain for parity testing and by the Android NDK
// for on-device use.
#pragma once

#include <cstddef>

namespace ssn {

// Tensor layout — all buffers are C-contiguous (row-major), float32:
//   u          [Bb][D][L]
//   delta      [Bb][D][L]
//   A          [D][N]
//   Bmat       [Bb][G][N][L]
//   Cmat       [Bb][G][N][L]
//   Dvec       [D]        (may be null -> skip the u*D skip-connection)
//   delta_bias [D]        (may be null -> no bias added to delta)
//   out        [Bb][D][L]
//
// Channel d belongs to group g = d / (D / G). This matches einops
// "B G N L -> B (G H) N L", where G is the SLOW axis of the combined
// channel axis (see SPEC.md §3 — getting this backwards silently produces
// plausible-looking but wrong output).
struct Dims {
  int Bb;  // batch
  int D;   // channels
  int L;   // sequence length
  int N;   // state size
  int G;   // groups for B/C
};

// Baseline: straightforward scalar loops, no SIMD, no threading.
// Streams over l holding only the N-element state in registers/stack, so
// peak working set is O(D*N) rather than the O(D*L*N) the PyTorch reference
// materialises (~334MB per call at the real dimensions).
void selective_scan_fwd_baseline(const float* u, const float* delta, const float* A,
                                 const float* Bmat, const float* Cmat, const float* Dvec,
                                 const float* delta_bias, bool delta_softplus,
                                 const Dims& dims, float* out);

}  // namespace ssn
