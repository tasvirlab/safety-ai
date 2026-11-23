# selective_scan_ndk — C++/NDK selective-scan operator for on-device PoseMamba

Isolated working folder for the Android on-device selective-scan operator.
Touches nothing else: the validated Python pipeline (`../backend/`), the
PoseMamba checkpoint, model mathematics, thresholds, and the Android UI
(`../app/`) are all untouched and remain the immutable reference.

Self-describing and evidence-first: every number below is reproducible from
this folder.

## Status

| Stage | State |
|---|---|
| 1. Formula + tensor-layout spec | **done** — `SPEC.md`, empirically validated |
| 2. Golden fixtures (real + random) | **done** — `fixtures/` |
| 3. Portable FP32 C++ baseline | **done** — `cpp/selective_scan.{h,cpp}` |
| 4. CUDA / reference / C++ parity | **done — PASS** on all three fixtures |
| 5. ARM NEON, threading, memory layout | **partly measured** (threading yes, NEON not yet) |
| 6. ONNX Runtime Android custom op | not started |
| 7. On-device runtime / RAM / thermal | not started — **no claim of on-device viability yet** |

Nothing here has run on an Android device. No claim of working on-device
deployment is made or implied.

## Layout

```
SPEC.md                  Stage 1: the authoritative formula + layout spec
tools/make_fixtures.py   Stage 2: fixture generator (reads PoseMamba, writes nothing to it)
fixtures/<name>/         raw float32 .bin + manifest.json  (language-agnostic on purpose)
cpp/selective_scan.{h,cpp}   Stage 3: portable FP32 baseline, dependency-free C++17
cpp/test_parity.cpp      Stage 4: parity + timing harness
docs/                    findings and benchmark records
```

Reproduce:

```bash
conda activate safetyai_backend
python tools/make_fixtures.py                     # needs CUDA + the real checkpoint
g++ -O3 -std=c++17 -march=native -fopenmp -o build/tp cpp/selective_scan.cpp cpp/test_parity.cpp -lm
./build/tp fixtures/real_capture 3
```

## Stage 4 result — parity PASSES

Three independent implementations (compiled CUDA kernel, the project's own
pure-PyTorch `selective_scan_ref`, and this C++ baseline) agree to float32
round-off. Full table in `SPEC.md` §5. Headline, on the real captured
tensors (D=1024, L=5103, N=16, G=4):

```
cpp vs CUDA : max_abs = 3.38e-04   mean_abs = 3.44e-06   max_rel = 3.40e-03
```

Bitwise equality with CUDA is **not achievable in principle** (the CUDA
kernel is chunk-parallel, this baseline is strictly sequential — different
float association order). See `SPEC.md` §5.

## Stage 5 (partial) — measured performance, and it is NOT fast

Measured on the development host, **not** a phone: Intel Xeon Silver 4215R
@ 3.2 GHz, 16 physical / 32 logical cores, `-O3 -march=native`.
One call = D=1024, L=5103, N=16. A full PoseMamba forward pass = **20 calls**.

| configuration | per call | ×20 = per 243-frame window |
|---|---|---|
| serial baseline | 568 ms | 11.3 s |
| OpenMP 2 threads | 310 ms | 6.20 s |
| OpenMP 4 threads | 173 ms | 3.46 s |
| OpenMP 8 threads | 133 ms | 2.67 s |
| OpenMP 16 threads | 66 ms | 1.32 s |
| OpenMP 8 + `-ffast-math` | 94 ms | 1.88 s |

Findings:

- **Threading scales well at no numerical cost.** By construction it cannot
  change results — parallelising `d` reorders no arithmetic *within* a
  channel, and channels never interact. Consistent with that, the measured
  error vs CUDA is identical to 7 significant figures (3.376007e-04) at
  1/2/4/8/16 threads. (Note: that is the max_abs statistic matching, not a
  verified bitwise comparison of every element — the bitwise claim rests on
  the structural argument, not on this measurement.)
- **`exp()` is the dominant cost.** 568 ms / (1024×5103×16) ≈ 6.6 ns per state
  update ≈ 21 cycles — far above the ~4 FLOPs of arithmetic involved.
  `-ffast-math` (which lets GCC vectorise/approximate `exp`) buys 1.42× at
  matched thread count, while changing parity only from 3.376e-04 to
  3.414e-04. A hand-written NEON polynomial `exp` is therefore a real lever
  with near-zero accuracy cost — but it is worth **less** than threading.
- **Compiled is ~10³× faster than the Python reference, but still slow in
  absolute terms.** The pure-PyTorch reference never completed a single
  forward pass in 4h+ (see `docs/AUDIT_FINDINGS.md`); the C++ baseline does
  one in 11.3 s serial. That is a huge win and still not obviously fast
  enough.

### Extrapolation to mobile — explicitly a projection, not a measurement

A phone has ~4 performance cores at lower clock, far less memory bandwidth
and smaller caches than this Xeon. Taking ~0.5–0.7× per-core throughput and 4
effective cores, a NEON-optimised build projects to roughly **3–5 s per
243-frame window**, i.e. **~35–55 s of scan time for the 2600-frame reference
video (11 windows)**. That is a projection with wide error bars. It is not
evidence. Stage 7 exists to replace it with a measurement, and until then no
on-device viability claim should be made — in either direction.

Note this is the PoseMamba scan **only**. On-device RTMPose 2D over 2600
frames is a separate and probably larger cost.

## Prior art — investigated before making any novelty claim

Selective-scan in native C++ with ARM NEON **already exists** and is not new:

- **llama.cpp / ggml** has Mamba and Mamba2 support, including ARM NEON as a
  first-class target, and ggml ops added specifically for SSMs (`expf`,
  prefix-sum, `ssd_segsum`); `mamba-base.cpp` exists in-tree.
  ([issue #4353](https://github.com/ggml-org/llama.cpp/issues/4353),
  [Mamba2 discussion #9196](https://github.com/ggml-org/llama.cpp/discussions/9196),
  [issue #20570](https://github.com/ggml-org/llama.cpp/issues/20570))
- **BitMamba-2** demonstrates Mamba-2 with custom AVX2 kernels ported to ARM
  NEON, run on Apple M1.
  ([discussion #19233](https://github.com/ggml-org/llama.cpp/discussions/19233))
- The **ONNX `Loop` route is independently reported as unusable** for SSMs:
  ONNX Runtime issue #27796 reports a scripted Mamba selective scan taking
  1.7 s for 0.1 s of audio on an Apple M3 (17× slower than realtime) because
  per-iteration interpreter overhead dwarfs the trivial per-step arithmetic.
  ([onnxruntime #27796](https://github.com/microsoft/onnxruntime/issues/27796),
  [onnx #7689](https://github.com/onnx/onnx/issues/7689))
  This independently corroborates our own measurement and is the reason the
  C++ custom-operator route was chosen over ONNX `Loop`.

**Therefore: no "first implementation" or "novel algorithm" claim is made or
supportable.** The recurrence is the published Mamba selective scan; the
reference formula is PoseMamba's own; native + NEON implementations of this
operator predate this work.

### What is actually specific to this work

Modest and worth stating precisely:

1. **A different execution regime from the LLM prior art.** llama.cpp-class
   work is autoregressive *decode* — one step at a time with persistent state,
   heavily quantised (down to 1.58-bit). Ours is a fixed-window *full-sequence*
   scan of L=5103 × 20 calls, in **FP32**, required to hold parity against a
   validated research pipeline. Different bottlenecks, different acceptance
   criteria.
2. **The `CrossScan_plus_poselimbs21` spatiotemporal layout** — a 4-group
   cross-scan over a flattened 243-frame × 21-joint pose grid, rather than a
   token sequence. The `g = d/H` group mapping and L=5103 flattening are
   specific to this model.
3. **Integration target is an ONNX Runtime Android custom operator** (Stage 6),
   not a bespoke inference runtime like ggml.
4. **The acceptance criterion is downstream-categorical**, not tensor-wise:
   Table C peak, Activity Score and final conditional REBA must match the
   verified Python result, rather than a raw tensor tolerance.

None of those are algorithmic novelty. They are integration and validation
specifics.

## Guarantees held throughout

- No PoseMamba source file, checkpoint, threshold or model constant modified.
- No change to `../backend/` (the validated Python reference pipeline).
- No change to `../app/` (Android UI).
- Fixtures are raw float32 + JSON, deliberately free of Python/PyTorch
  dependencies so an independent implementation can consume them directly.
