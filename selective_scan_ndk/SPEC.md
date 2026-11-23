# Stage 1 — Selective-scan formula and tensor-layout specification

Authoritative specification for the C++/NDK port. Every claim here is
**empirically established against the real compiled CUDA kernel**, not read
off templated CUDA source or assumed from upstream Mamba documentation.

## 1. What operation this is

PoseMamba's `lib/model/csms6s.py::SelectiveScanCore` — a
`torch.autograd.Function` whose `forward` calls the project's own compiled
extension `selective_scan_cuda_core.fwd(u, delta, A, B, C, D, delta_bias,
delta_softplus, 1)`. It is **not** the pip `mamba_ssm` package; PoseMamba
vendors its own kernel under `PoseMamba/kernels/selective_scan/`.

It is invoked **20 times per forward pass** (depth=10 × 2 scan directions).

## 2. Real dimensions (measured, not assumed)

Captured at the first real call site of a real forward pass (fold1
checkpoint, `(1, 243, 21, 2)` input):

| symbol | meaning | value |
|---|---|---|
| `Bb` | batch | 1 |
| `D` | channels | 1024 |
| `L` | sequence length | **5103** |
| `N` | state size | 16 |
| `G` | groups for B/C | 4 |
| `H` | channels per group = `D/G` | 256 |
| `delta_softplus` | | `True` |

**`L = 5103 = 243 × 21`.** The scan does *not* run over 243 timesteps. The
`CrossScan_plus_poselimbs21` cross-scan flattens the 243-frame × 21-joint
spatiotemporal grid into one linear sequence. This is the single most
important number in this document — it is ~21× larger than a naive reading of
"243-frame window" suggests, and it is why unrolled approaches fail
(see `docs/AUDIT_FINDINGS.md`).

Total sequential state updates per forward pass:
`D × L × N × 20 = 1024 × 5103 × 16 × 20 ≈ 1.67 × 10⁹`.

## 3. Tensor layout

All buffers float32, C-contiguous (row-major):

```
u          [Bb][D][L]
delta      [Bb][D][L]
A          [D][N]              real-valued (not complex)
B          [Bb][G][N][L]       "variable B"  (4-D)
C          [Bb][G][N][L]       "variable C"  (4-D)
D_skip     [D]
delta_bias [D]
out        [Bb][D][L]
```

**Group mapping (easy to get backwards):** the reference uses einops
`repeat(B, "B G N L -> B (G H) N L", H=D//G)`. In einops, `(G H)` places `G`
on the **slow** axis, so channel `d` belongs to group

```
g = d / H        (integer division, H = D/G = 256)
```

Getting this backwards (`g = d % G`) produces output that is plausible in
shape and magnitude but numerically wrong — it will not be caught by any
shape check, only by the parity fixtures.

## 4. The recurrence

Per batch `b`, per channel `d` (with `g = d/H`), state `x[0..N-1]`
initialised to zero, iterating `l = 0 … L-1`:

```
dt      = delta[b][d][l] + delta_bias[d]
dt      = softplus(dt)                      if delta_softplus
uu      = u[b][d][l]

for n in 0..N-1:
    dA     = exp(dt * A[d][n])
    x[n]   = dA * x[n] + dt * B[b][g][n][l] * uu
    y     += x[n] * C[b][g][n][l]

out[b][d][l] = y + uu * D_skip[d]
```

`softplus` must match PyTorch's `F.softplus(x, beta=1, threshold=20)`
exactly, including the stability branch:

```
softplus(x) = x                 if x > 20
            = log1p(exp(x))     otherwise
```

**The `l` axis is the only sequential axis.** The recurrence is fully
independent across `b`, `d`, and (for the state update) across `n`. This is
the basis for all parallelisation: `d` is the thread axis, `n` (=16) is the
SIMD axis.

## 5. Evidence that this specification is correct

Three independent implementations agree on identical inputs:

| comparison | fixture | max abs diff | mean abs diff |
|---|---|---|---|
| CUDA vs PyTorch reference | `small_random` (D=64, L=128) | 7.63e-06 | 5.48e-07 |
| CUDA vs PyTorch reference | `medium_random` (D=256, L=1024) | 9.54e-06 | 5.56e-07 |
| **C++** vs CUDA | `small_random` | 7.63e-06 | 5.01e-07 |
| **C++** vs PyTorch reference | `small_random` | 7.63e-06 | 4.60e-07 |
| **C++** vs CUDA | `medium_random` | 1.14e-05 | 4.96e-07 |
| **C++** vs CUDA | `real_capture` (D=1024, L=5103) | **3.38e-04** | **3.44e-06** |

The PyTorch reference is the project's **own** `selective_scan_ref()` from
`PoseMamba/kernels/selective_scan/test_selective_scan_easy.py` — the function
the upstream authors already use to validate their CUDA kernel.

### Why `real_capture` shows 3.4e-04 rather than ~1e-05

Expected and benign. Two causes, both accumulation-order effects inherent to
float32, neither a logic difference:

1. `L = 5103` vs 128/1024 — ~40× more sequential accumulation steps for
   round-off to compound through.
2. The CUDA kernel computes the scan **chunk-parallel** (its whole reason for
   existing); the C++ baseline computes it strictly sequentially. Different
   association order over the same additions gives different last-bit
   results.

Bitwise equality with the CUDA kernel is therefore **not achievable in
principle** and must not be used as the acceptance criterion. Mean abs error
is 3.4e-06 and max **relative** error is 3.4e-03 on real activations. The
meaningful acceptance criterion is end-to-end: whether Table C / Activity
Layer conclusions are unchanged (see the parity requirements in the Android
deployment plan).

## 6. What must NOT be changed

The trained weights, `A`/`D`/`delta_bias` semantics, the softplus threshold,
the group mapping, the state size, and the order of the `+ uu * D_skip`
skip-connection are all fixed by the trained checkpoint. None of them are
tunable. Any "optimisation" that alters them is a model change, not an
optimisation.
