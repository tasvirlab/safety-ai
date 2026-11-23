# Why a C++/NDK custom operator — the evidence that ruled out the alternatives

Condensed from the PoseMamba Android export audit. Full machine-readable
record: `../../backend/test_runs/2026-09-17_how_to_lift_box/posemamba_onnx_audit_report.json`.

## 1. Naive `torch.onnx.export()` — exports cleanly, and is silently WRONG

Exporting the real CUDA-path model raised **no exception** and produced a
3920-node graph with **zero custom or non-standard-domain nodes** — every node
a standard ONNX op. It looks like a clean success.

It is not. Against the real CUDA model, **on the exact input it was traced
from**:

| | max abs diff |
|---|---|
| exported ONNX vs real model, traced input | 1.120 |
| exported ONNX vs real model, different input | 1.075 |
| *two completely unrelated real outputs, for scale* | *1.277* |

The exported graph's error on its own tracing input is nearly as large as the
natural spread between two unrelated outputs — it is effectively uncorrelated
with the true computation. The node counts corroborate this: only 40 `Einsum`
total, nowhere near what a genuinely captured 5103-step recurrence would
require. The tracer never captured the scan's data dependency at all, because
`selective_scan_cuda_core.fwd` is a raw pybind11 extension call, not a
dispatcher-registered ATen op.

**Export completing without an exception is not evidence of correctness.**
This was caught only by running the exported graph and comparing — which is
why fixture-based parity, not export success, is the acceptance criterion
everywhere in this project.

## 2. Unrolled pure-Python reference scan — computationally impractical

Substituting PoseMamba's own `selective_scan_ref()` (pure PyTorch) and running
a single **eager** forward pass, before any export attempt:

- 4 h 08 m+ wall clock with **zero artifact growth**
- 34.3 GB RSS and climbing (of 62 GB system RAM)
- terminated gracefully via SIGTERM once confirmed stuck

Root cause is not the arithmetic. It is that the reference materialises two
`(1, D, L, N)` tensors (~334 MB each) per call and then runs `L` Python-level
iterations — **5103 × 20 calls ≈ 102,000 sequential interpreted steps** per
forward pass. Recorded as **technically impractical, not a working solution**.

The C++ baseline in this folder does the same mathematics in **11.3 s serial /
1.3 s at 16 threads**, holding only the `O(D×N)` state (64 KB) instead of
`O(D×L×N)` (334 MB) — a ~5000× working-set reduction.

## 3. ONNX `Loop` operator — rejected on independent evidence

Not attempted here, because it is independently reported as unusable for
exactly this workload: ONNX Runtime issue
[#27796](https://github.com/microsoft/onnxruntime/issues/27796) reports a
scripted Mamba selective scan at **1.7 s for 0.1 s of audio on an Apple M3
(17× slower than realtime)**, with per-iteration interpreter overhead
dominating trivial per-step arithmetic. Our workload is 102,000 iterations per
forward pass — the same failure mode, larger.

## 4. Conclusion

A compiled custom operator is the only remaining credible path, which is what
this folder implements. That conclusion is about *execution strategy* only —
the model mathematics, weights and thresholds are unchanged throughout, and
the C++ implementation is validated to float32 round-off against both the
CUDA kernel and the project's own reference formula.

Compiled is **~1000× faster than the Python reference and still not
obviously fast enough** — see the measured numbers and the explicit
mobile projection caveat in `../README.md`.
