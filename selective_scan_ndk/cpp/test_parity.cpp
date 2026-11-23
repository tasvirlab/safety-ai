// Stage 4 — CUDA / reference / C++ parity harness.
//
// Usage: test_parity <fixture_dir> [repeats]
//
// Loads a fixture produced by tools/make_fixtures.py, runs the portable C++
// baseline, and compares against out_cuda.bin (the real compiled CUDA
// kernel's output) and, when present, out_ref.bin (the pure-PyTorch
// reference formula's output). Reports max/mean absolute difference and
// wall-clock timing. Exit code is non-zero if the C++ output exceeds the
// tolerance against the CUDA golden.
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <string>
#include <vector>

#include "selective_scan.h"

namespace {

std::vector<float> read_bin(const std::string& path, size_t expect_elems) {
  std::ifstream f(path, std::ios::binary | std::ios::ate);
  if (!f) {
    std::fprintf(stderr, "ERROR: cannot open %s\n", path.c_str());
    std::exit(2);
  }
  const std::streamsize bytes = f.tellg();
  f.seekg(0);
  std::vector<float> v(static_cast<size_t>(bytes) / sizeof(float));
  f.read(reinterpret_cast<char*>(v.data()), bytes);
  if (expect_elems && v.size() != expect_elems) {
    std::fprintf(stderr, "ERROR: %s has %zu floats, expected %zu\n", path.c_str(), v.size(), expect_elems);
    std::exit(2);
  }
  return v;
}

bool file_exists(const std::string& p) {
  std::ifstream f(p, std::ios::binary);
  return static_cast<bool>(f);
}

// Minimal integer/bool extractor for the manifests this project generates
// itself (fixed, known shape) -- avoids pulling in a JSON dependency into a
// component that must also build under the Android NDK.
long json_int(const std::string& text, const std::string& key, long fallback) {
  const std::string needle = "\"" + key + "\"";
  size_t p = text.find(needle);
  if (p == std::string::npos) return fallback;
  p = text.find(':', p + needle.size());
  if (p == std::string::npos) return fallback;
  return std::strtol(text.c_str() + p + 1, nullptr, 10);
}

bool json_bool(const std::string& text, const std::string& key, bool fallback) {
  const std::string needle = "\"" + key + "\"";
  size_t p = text.find(needle);
  if (p == std::string::npos) return fallback;
  p = text.find(':', p + needle.size());
  if (p == std::string::npos) return fallback;
  return text.compare(text.find_first_not_of(" \t", p + 1), 4, "true") == 0;
}

struct Cmp {
  double max_abs;
  double mean_abs;
  double max_rel;
};

Cmp compare(const std::vector<float>& a, const std::vector<float>& b) {
  Cmp c{0.0, 0.0, 0.0};
  double sum = 0.0;
  const size_t n = a.size() < b.size() ? a.size() : b.size();
  for (size_t i = 0; i < n; ++i) {
    const double d = std::fabs(static_cast<double>(a[i]) - static_cast<double>(b[i]));
    sum += d;
    if (d > c.max_abs) c.max_abs = d;
    const double denom = std::fabs(static_cast<double>(b[i]));
    if (denom > 1e-3) {
      const double r = d / denom;
      if (r > c.max_rel) c.max_rel = r;
    }
  }
  c.mean_abs = n ? sum / static_cast<double>(n) : 0.0;
  return c;
}

}  // namespace

int main(int argc, char** argv) {
  if (argc < 2) {
    std::fprintf(stderr, "usage: %s <fixture_dir> [repeats]\n", argv[0]);
    return 2;
  }
  const std::string dir = argv[1];
  const int repeats = argc > 2 ? std::atoi(argv[2]) : 1;

  std::ifstream mf(dir + "/manifest.json");
  if (!mf) {
    std::fprintf(stderr, "ERROR: no manifest.json in %s\n", dir.c_str());
    return 2;
  }
  const std::string manifest((std::istreambuf_iterator<char>(mf)), std::istreambuf_iterator<char>());

  ssn::Dims dims{};
  dims.Bb = static_cast<int>(json_int(manifest, "B_batch", 1));
  dims.D = static_cast<int>(json_int(manifest, "D_channels", 0));
  dims.L = static_cast<int>(json_int(manifest, "L_seq", 0));
  dims.N = static_cast<int>(json_int(manifest, "N_state", 0));
  dims.G = static_cast<int>(json_int(manifest, "G_groups", 0));
  const bool softplus = json_bool(manifest, "delta_softplus", true);

  if (!dims.D || !dims.L || !dims.N || !dims.G) {
    std::fprintf(stderr, "ERROR: could not parse dims from manifest\n");
    return 2;
  }
  std::printf("fixture      : %s\n", dir.c_str());
  std::printf("dims         : B=%d D=%d L=%d N=%d G=%d H=%d softplus=%s\n", dims.Bb, dims.D, dims.L,
              dims.N, dims.G, dims.D / dims.G, softplus ? "true" : "false");

  const size_t n_udl = static_cast<size_t>(dims.Bb) * dims.D * dims.L;
  const size_t n_bc = static_cast<size_t>(dims.Bb) * dims.G * dims.N * dims.L;

  const auto u = read_bin(dir + "/u.bin", n_udl);
  const auto delta = read_bin(dir + "/delta.bin", n_udl);
  const auto A = read_bin(dir + "/A.bin", static_cast<size_t>(dims.D) * dims.N);
  const auto Bm = read_bin(dir + "/B.bin", n_bc);
  const auto Cm = read_bin(dir + "/C.bin", n_bc);
  const auto Dv = read_bin(dir + "/D.bin", static_cast<size_t>(dims.D));
  const auto dbias = read_bin(dir + "/delta_bias.bin", static_cast<size_t>(dims.D));
  const auto out_cuda = read_bin(dir + "/out_cuda.bin", n_udl);

  std::vector<float> out(n_udl, 0.0f);

  // Warm-up run (not timed) so the timed runs measure steady state.
  ssn::selective_scan_fwd_baseline(u.data(), delta.data(), A.data(), Bm.data(), Cm.data(), Dv.data(),
                                   dbias.data(), softplus, dims, out.data());

  double best_ms = 1e30, total_ms = 0.0;
  for (int r = 0; r < repeats; ++r) {
    const auto t0 = std::chrono::steady_clock::now();
    ssn::selective_scan_fwd_baseline(u.data(), delta.data(), A.data(), Bm.data(), Cm.data(), Dv.data(),
                                     dbias.data(), softplus, dims, out.data());
    const auto t1 = std::chrono::steady_clock::now();
    const double ms = std::chrono::duration<double, std::milli>(t1 - t0).count();
    total_ms += ms;
    if (ms < best_ms) best_ms = ms;
  }

  const Cmp vs_cuda = compare(out, out_cuda);
  std::printf("cpp vs CUDA  : max_abs=%.6e  mean_abs=%.6e  max_rel=%.6e\n", vs_cuda.max_abs,
              vs_cuda.mean_abs, vs_cuda.max_rel);

  if (file_exists(dir + "/out_ref.bin")) {
    const auto out_ref = read_bin(dir + "/out_ref.bin", n_udl);
    const Cmp vs_ref = compare(out, out_ref);
    const Cmp cuda_ref = compare(out_cuda, out_ref);
    std::printf("cpp vs ref   : max_abs=%.6e  mean_abs=%.6e\n", vs_ref.max_abs, vs_ref.mean_abs);
    std::printf("CUDA vs ref  : max_abs=%.6e  mean_abs=%.6e  (independent cross-check)\n",
                cuda_ref.max_abs, cuda_ref.mean_abs);
  }

  // Throughput context: one call of this size, and what 20 calls (one
  // PoseMamba forward pass) would cost at this rate.
  std::printf("timing       : best=%.2f ms  mean=%.2f ms over %d run(s)\n", best_ms,
              total_ms / repeats, repeats);
  std::printf("             : x20 calls/forward = %.2f ms  (%.3f s)\n", best_ms * 20.0,
              best_ms * 20.0 / 1000.0);

  const double tol = 1e-3;
  const bool pass = vs_cuda.max_abs <= tol;
  std::printf("VERDICT      : %s (tolerance max_abs <= %.0e vs CUDA golden)\n",
              pass ? "PASS" : "FAIL", tol);
  return pass ? 0 : 1;
}
