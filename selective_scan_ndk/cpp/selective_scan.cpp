#include "selective_scan.h"

#include <cmath>

namespace ssn {
namespace {

// PyTorch's F.softplus(x, beta=1, threshold=20): for numerical stability it
// returns x unchanged once x*beta > threshold. Matching this exactly matters
// for parity -- log1p(exp(x)) alone overflows/denormalises differently at the
// tail and would drift from the reference.
inline float softplus_like_torch(float x) {
  return x > 20.0f ? x : std::log1p(std::exp(x));
}

}  // namespace

void selective_scan_fwd_baseline(const float* u, const float* delta, const float* A,
                                 const float* Bmat, const float* Cmat, const float* Dvec,
                                 const float* delta_bias, bool delta_softplus,
                                 const Dims& dims, float* out) {
  const int Bb = dims.Bb, D = dims.D, L = dims.L, N = dims.N, G = dims.G;
  const int H = D / G;  // channels per group

  for (int b = 0; b < Bb; ++b) {
    // The recurrence is INDEPENDENT per channel d -- l is the only
    // sequential axis. That makes d the natural parallel axis, and
    // parallelising it does not reorder any arithmetic within a channel, so
    // results are bit-identical to the serial build. Only active when
    // compiled with -fopenmp; otherwise this is the plain serial baseline.
#ifdef _OPENMP
#pragma omp parallel for schedule(static)
#endif
    for (int d = 0; d < D; ++d) {
      const int g = d / H;

      const float* u_row = u + (static_cast<size_t>(b) * D + d) * L;
      const float* dt_row = delta + (static_cast<size_t>(b) * D + d) * L;
      const float* A_row = A + static_cast<size_t>(d) * N;
      float* out_row = out + (static_cast<size_t>(b) * D + d) * L;

      const float Dd = Dvec ? Dvec[d] : 0.0f;
      const float dbias = delta_bias ? delta_bias[d] : 0.0f;

      // Recurrent state for this channel; lives in the stack/registers for
      // the whole sweep over l. The recurrence is independent per channel,
      // which is what makes d the natural parallel axis later (Stage 5).
      float x[64];  // N is 16 in this model; 64 is a safe compile-time cap
      for (int n = 0; n < N; ++n) x[n] = 0.0f;

      for (int l = 0; l < L; ++l) {
        float dt = dt_row[l] + dbias;
        if (delta_softplus) dt = softplus_like_torch(dt);
        const float uu = u_row[l];

        // B/C slices for this group: [g][n][l], stride L along n.
        const float* B_g = Bmat + ((static_cast<size_t>(b) * G + g) * N) * L + l;
        const float* C_g = Cmat + ((static_cast<size_t>(b) * G + g) * N) * L + l;

        float y = 0.0f;
        for (int n = 0; n < N; ++n) {
          const float dA = std::exp(dt * A_row[n]);
          const float Bv = B_g[static_cast<size_t>(n) * L];
          const float Cv = C_g[static_cast<size_t>(n) * L];
          x[n] = dA * x[n] + dt * Bv * uu;
          y += x[n] * Cv;
        }
        out_row[l] = y + uu * Dd;
      }
    }
  }
}

}  // namespace ssn
