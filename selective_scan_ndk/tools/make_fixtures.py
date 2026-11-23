"""Stage 2 — golden fixture generation for the selective-scan NDK port.

Produces three fixture sets, each as raw little-endian float32 .bin files plus
a JSON manifest (language-agnostic on purpose, so an independent
implementation can consume them without any Python/PyTorch dependency):

  small_random  D=64,  L=128,  N=16, G=4  -- cheap; used to cross-validate the
                Python reference formula against the real CUDA kernel, and as
                the first C++ correctness target.
  medium_random D=256, L=1024, N=16, G=4  -- intermediate size.
  real_capture  D=1024,L=5103, N=16, G=4  -- the ACTUAL tensors observed at the
                first selective-scan call site of a real PoseMamba forward pass
                with the real fold1 checkpoint and a real (1,243,21,2) input.

For every fixture: `out_cuda.bin` is produced by the REAL
selective_scan_cuda_core.fwd() kernel and is the golden output. For the two
random fixtures, `out_ref.bin` is additionally produced by the project's own
pure-PyTorch selective_scan_ref() formula, so CUDA-vs-reference agreement is
established on identical inputs before any C++ is written.

Reads only. Does not modify PoseMamba, the checkpoint, the validated Python
pipeline, or any threshold.
"""
from __future__ import annotations

import json
import sys
import time
from pathlib import Path

import numpy as np
import torch
import torch.nn.functional as F

POSEMAMBA_ROOT = Path("/mnt/c/Users/tempadmin/Research DATA/PoseMamba")
CHECKPOINT = POSEMAMBA_ROOT / ("experiments/exp05_5fold_cv/checkpoints/fold1/"
                               "exp05_fold1_2026_09_15_T_11_23_07/best_epoch.bin")
OUT_DIR = Path(__file__).resolve().parents[1] / "fixtures"
OUT_DIR.mkdir(parents=True, exist_ok=True)

sys.path.insert(0, str(POSEMAMBA_ROOT))
import selective_scan_cuda_core  # noqa: E402  -- the real compiled CUDA kernel


def log(m: str) -> None:
    print(f"[{time.strftime('%H:%M:%S')}] {m}", file=sys.stderr)


# ── The project's own reference formula, transcribed from
#    PoseMamba/kernels/selective_scan/test_selective_scan_easy.py::
#    selective_scan_ref(), specialised to the real code path this model
#    actually takes (real A, 4-D variable B and C, no z gate). The MATH is
#    unchanged; only the dead branches for cases this model never hits
#    (complex A, non-variable B/C, 3-D B/C, z gating) are omitted, and
#    einops.repeat is replaced by an equivalent torch.repeat_interleave on
#    the group axis. Verified against the real CUDA kernel below. ───────────
def selective_scan_ref_4d(u, delta, A, B, C, D=None, delta_bias=None, delta_softplus=False):
    dtype_in = u.dtype
    u = u.float()
    delta = delta.float()
    if delta_bias is not None:
        delta = delta + delta_bias[..., None].float()
    if delta_softplus:
        delta = F.softplus(delta)
    batch, dim, dstate = u.shape[0], A.shape[0], A.shape[1]
    B = B.float()
    C = C.float()
    x = A.new_zeros((batch, dim, dstate))
    ys = []
    deltaA = torch.exp(torch.einsum("bdl,dn->bdln", delta, A))
    # einops: repeat(B, "B G N L -> B (G H) N L", H=dim // B.shape[1])
    # "(G H)" puts G on the SLOW axis, so channel d belongs to group d // H.
    H = dim // B.shape[1]
    Br = torch.repeat_interleave(B, H, dim=1)
    Cr = torch.repeat_interleave(C, H, dim=1)
    deltaB_u = torch.einsum("bdl,bdnl,bdl->bdln", delta, Br, u)
    for i in range(u.shape[2]):
        x = deltaA[:, :, i] * x + deltaB_u[:, :, i]
        y = torch.einsum("bdn,bdn->bd", x, Cr[:, :, :, i])
        ys.append(y)
    y = torch.stack(ys, dim=2)
    out = y if D is None else y + u * D.unsqueeze(-1)
    return out.to(dtype=dtype_in)


def save_fixture(name: str, tensors: dict, meta: dict) -> None:
    d = OUT_DIR / name
    d.mkdir(parents=True, exist_ok=True)
    manifest = {"name": name, "dtype": "float32", "byte_order": "little", "layout": "C_contiguous_row_major",
                "tensors": {}, **meta}
    for key, t in tensors.items():
        if t is None:
            manifest["tensors"][key] = None
            continue
        arr = t.detach().cpu().contiguous().float().numpy()
        assert arr.dtype == np.float32
        (d / f"{key}.bin").write_bytes(arr.tobytes(order="C"))
        manifest["tensors"][key] = {"shape": list(arr.shape), "file": f"{key}.bin",
                                    "n_elements": int(arr.size), "bytes": int(arr.nbytes)}
    (d / "manifest.json").write_text(json.dumps(manifest, indent=2))
    log(f"wrote fixture '{name}' -> {d}")


def make_random_fixture(name: str, Dc: int, L: int, N: int, G: int, seed: int) -> None:
    torch.manual_seed(seed)
    dev = "cuda"
    # Ranges chosen to resemble real activations (A negative, as in S4D/Mamba
    # parameterisation where A = -exp(A_log); delta positive pre-softplus).
    u = torch.randn(1, Dc, L, device=dev)
    delta = torch.rand(1, Dc, L, device=dev) * 0.5
    A = -torch.rand(Dc, N, device=dev) - 0.1
    Bm = torch.randn(1, G, N, L, device=dev)
    Cm = torch.randn(1, G, N, L, device=dev)
    Dv = torch.randn(Dc, device=dev)
    dbias = torch.rand(Dc, device=dev) * 0.5

    out_cuda, x_state, *_ = selective_scan_cuda_core.fwd(u, delta, A, Bm, Cm, Dv, dbias, True, 1)
    out_ref = selective_scan_ref_4d(u, delta, A, Bm, Cm, D=Dv, delta_bias=dbias, delta_softplus=True)
    diff = (out_cuda.float() - out_ref.float()).abs()
    log(f"{name}: CUDA vs reference-formula  max={diff.max().item():.3e}  mean={diff.mean().item():.3e}")

    save_fixture(name, {"u": u, "delta": delta, "A": A, "B": Bm, "C": Cm, "D": Dv,
                        "delta_bias": dbias, "out_cuda": out_cuda, "out_ref": out_ref},
                 {"B_batch": 1, "D_channels": Dc, "L_seq": L, "N_state": N, "G_groups": G,
                  "H_channels_per_group": Dc // G, "delta_softplus": True,
                  "cuda_vs_ref_max_abs_diff": float(diff.max()),
                  "cuda_vs_ref_mean_abs_diff": float(diff.mean()),
                  "source": "randomly generated, seed=%d" % seed})


def make_real_capture() -> None:
    """Capture the tensors at the FIRST real selective-scan call of a real
    PoseMamba forward pass (real checkpoint, real (1,243,21,2) input)."""
    import lib.model.csms6s as csms6s
    from lib.model.PoseMamba_Construction import PoseMambaConstruction

    captured: dict = {}

    def spy_forward(ctx, u, delta, A, B, C, D=None, delta_bias=None, delta_softplus=False,
                    nrows=1, backnrows=1, oflex=True):
        out, x, *rest = selective_scan_cuda_core.fwd(u, delta, A, B, C, D, delta_bias, delta_softplus, 1)
        if not captured:
            captured.update(dict(u=u.clone(), delta=delta.clone(), A=A.clone(), B=B.clone(),
                                 C=C.clone(), D=None if D is None else D.clone(),
                                 delta_bias=None if delta_bias is None else delta_bias.clone(),
                                 delta_softplus=bool(delta_softplus), out_cuda=out.clone()))
        ctx.save_for_backward(u, delta, A, B, C, D, delta_bias, x)
        return out

    csms6s.SelectiveScanCore.forward = staticmethod(spy_forward)

    torch.manual_seed(0)
    x_in = torch.randn(1, 243, 21, 2, device="cuda")
    model = PoseMambaConstruction(num_frame=243, embed_dim_ratio=128, depth=10, mlp_ratio=2.0).to("cuda")
    model.load_state_dict(torch.load(CHECKPOINT, map_location="cuda")["model_pos"])
    model.eval()
    with torch.no_grad():
        model(x_in)

    c = captured
    Dc, N = c["A"].shape
    G, L = c["B"].shape[1], c["B"].shape[3]
    log(f"real capture: D={Dc} L={L} N={N} G={G} H={Dc // G} delta_softplus={c['delta_softplus']}")

    save_fixture("real_capture",
                 {k: c[k] for k in ("u", "delta", "A", "B", "C", "D", "delta_bias", "out_cuda")},
                 {"B_batch": 1, "D_channels": Dc, "L_seq": L, "N_state": N, "G_groups": G,
                  "H_channels_per_group": Dc // G, "delta_softplus": c["delta_softplus"],
                  "source": ("first selective-scan call of a real PoseMamba forward pass; "
                             "checkpoint=exp05_5fold_cv fold1; input=torch.manual_seed(0) "
                             "randn(1,243,21,2); out_cuda is the real selective_scan_cuda_core.fwd output"),
                  "note": ("out_ref is NOT provided for this fixture: the pure-PyTorch reference at "
                           "L=5103 materialises two (1,D,L,N) tensors of ~334MB each and is "
                           "impractically slow (see docs/AUDIT_FINDINGS.md). CUDA-vs-reference "
                           "agreement is established on the random fixtures instead.")})


if __name__ == "__main__":
    make_random_fixture("small_random", Dc=64, L=128, N=16, G=4, seed=1)
    make_random_fixture("medium_random", Dc=256, L=1024, N=16, G=4, seed=2)
    make_real_capture()
    log("done")
