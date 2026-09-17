"""Pipeline steps 1-6: video -> RTMPose 21-joint 2D -> PoseMamba 3D -> REBA angles.

Reuses validated research code (never modified):
  - 2D pose + 133-to-21 joint mapping: a VENDORED, byte-identical copy of
    SyncApp/backend/core/rtmpose_pipeline.py under `vendor/rtmpose/` (see
    that folder's PROVENANCE.md), imported only via `.rtmpose_adapter` — this
    file no longer imports anything from `Research DATA/SyncApp` at runtime.
    REBA_Engine has no 2D pose-extraction module of its own —
    REBA_Engine/predict/run_posemamba_trial.py takes a pre-existing
    pose2d_rtmpose.csv as INPUT, it does not produce one — so vendoring
    SyncApp's implementation (rather than depending on the live SyncApp
    project) was the way to remove that runtime dependency while keeping the
    exact same, unmodified 2D-detection behavior.
  - 3D lift: PoseMamba/lib/model/PoseMamba_Construction.py + the checkpoint
    named in config.yaml (same model class REBA_Engine/predict/
    run_posemamba_trial.py itself loads — a neutral, third dependency, not
    owned by either SyncApp or REBA_Engine).
  - Anatomical angles: REBA_Engine/reba/angles.py::compute_reba_angles()
    (NOT SyncApp/backend/core/vicon_reba/angles.py — different channel set,
    e.g. upper_arm split into _sagittal_deg/_abd_deg; REBA_Engine's static/
    repetition/rapid-motion detectors and reba/scorer.py all expect THIS
    module's channels).
  - Joint packing: REBA_Engine/reba/joint_mapping.py::array_to_dict() — its
    JOINT_NAMES is identical, in content and order, to config.yaml's
    canonical_joints (verified directly against that file; both have PELCEN
    at index 3, the PoseMamba training order).

Runs inside a CUDA-capable Python environment — PoseMamba's SelectiveScan
kernel has no CPU fallback (see ../README.md and the ONNX/TFLite/NNAPI audit).

Windowing: REBA_Engine/predict/run_posemamba_trial.py's own convention
(make_window_starts/stitch_windows) — sequential non-overlapping windows plus
one backward-aligned tail window `[T-window, T)` when T is not an exact
multiple of window size, so the WHOLE trial is covered and no tail frames are
ever dropped (this is "the exact REBA_Engine [...] implementation that
produced the manuscript results" for windowing, per the build instruction;
reimplemented here rather than imported, since importing would pull in
run_posemamba_trial.py's own torch/model-loading code for the sake of two
small helper functions — the window-index arithmetic itself is copied
verbatim in behavior, and covered by the same boundary self-checks). Unlike
that research script (which requires every input frame to be finite and
raises otherwise — no gap-filling, out of scope there), a deployment video can
have real subject-detection dropouts, so a window containing ANY NaN 2D
frame is skipped (left NaN in the stitched 3D output) rather than raising —
an app-layer deployment adaptation, not a change to REBA_Engine's own
windowing arithmetic.
"""
from __future__ import annotations

import sys
from pathlib import Path
from typing import Callable, Optional

import cv2
import numpy as np
import torch

from .config import PipelineConfig

ProgressCb = Optional[Callable[[int, str], None]]

_paths_registered: set[Path] = set()


def _register_research_paths(cfg: PipelineConfig) -> None:
    """Insert PoseMamba and REBA_Engine onto sys.path so their internal
    imports resolve, without ever copying their code into this backend.
    2D detection no longer needs SyncApp on sys.path — see .rtmpose_adapter,
    which registers the vendored copy separately."""
    for sub in ("PoseMamba", "REBA_Engine"):
        p = cfg.research_data_root / sub
        if not p.is_dir():
            raise FileNotFoundError(
                f"Expected research code at {p} — check research_data_root "
                f"in config.yaml."
            )
        if p not in _paths_registered:
            sys.path.insert(0, str(p))
            _paths_registered.add(p)


_model_cache: dict[tuple[str, str], "torch.nn.Module"] = {}


def get_model(cfg: PipelineConfig):
    """Load (and cache) the checkpoint named in config.yaml. Cache key includes
    the checkpoint path, so changing config.yaml's checkpoint mid-process (e.g.
    in a test that swaps configs) does not serve a stale model."""
    _register_research_paths(cfg)
    from lib.model.PoseMamba_Construction import PoseMambaConstruction  # PoseMamba

    key = (cfg.device, str(cfg.checkpoint.path))
    if key not in _model_cache:
        ck = cfg.checkpoint
        m = PoseMambaConstruction(
            num_frame=ck.num_frame,
            embed_dim_ratio=ck.embed_dim_ratio,
            depth=ck.depth,
            mlp_ratio=ck.mlp_ratio,
        )
        m = m.to(cfg.device)
        state = torch.load(ck.path, map_location=cfg.device)
        if ck.state_dict_key not in state:
            raise KeyError(
                f"Checkpoint {ck.path} has no key {ck.state_dict_key!r} — keys "
                f"present: {sorted(state.keys())}. Update checkpoint.state_dict_key "
                f"in config.yaml if this checkpoint's format differs."
            )
        m.load_state_dict(state[ck.state_dict_key])
        m.eval()
        _model_cache[key] = m
    return _model_cache[key]


def _reorder_to_canonical(df, canonical_joints: tuple[str, ...]) -> np.ndarray:
    """rtmpose_pipeline's per-frame <JOINT>_X/_Y columns -> (F, 21, 2) px, in
    config.yaml's canonical_joints order (= REBA_Engine's joint_mapping.
    JOINT_NAMES order — NOT rtmpose_pipeline.JOINT_NAMES's own order, e.g.
    PELCEN is index 12 there vs. 3 here; training used the order in config.yaml)."""
    n_frames = len(df)
    out = np.full((n_frames, len(canonical_joints), 2), np.nan)
    for j, name in enumerate(canonical_joints):
        out[:, j, 0] = df[f"{name}_X"].to_numpy()
        out[:, j, 1] = df[f"{name}_Y"].to_numpy()
    return out


def _normalize_2d(pos2d_px: np.ndarray, width: int, root_idx: int) -> np.ndarray:
    """Root-center on the pelvis, divide both axes by width/2 (same divisor
    for both axes, so aspect ratio is preserved) — the exact transform the
    model was trained under, re-derived identically by both SyncApp's
    dataset_export.py and REBA_Engine/predict/run_posemamba_trial.py::normalize_2d()."""
    root = pos2d_px[:, root_idx, :]
    return (pos2d_px - root[:, None, :]) / (width / 2.0)


def _make_window_starts(t: int, window_size: int) -> list[int]:
    """Sequential non-overlapping window starts, plus one backward-aligned
    tail window [T-window_size, T) when T is not an exact multiple of
    window_size — REBA_Engine/predict/run_posemamba_trial.py's own convention,
    covering the WHOLE trial with no dropped frames."""
    if t < window_size:
        raise InsufficientFramesError(
            f"Video has {t} frames, needs at least {window_size} "
            f"(the checkpoint's trained window size, config.yaml checkpoint.num_frame)."
        )
    starts = list(range(0, t - window_size + 1, window_size))
    covered_until = (starts[-1] + window_size) if starts else 0
    if covered_until < t:
        starts.append(t - window_size)
    return starts


class InsufficientFramesError(ValueError):
    """Video is shorter than one PoseMamba window — cannot run 3D lifting."""


def run_pose_pipeline(
    video_path: str | Path,
    cfg: PipelineConfig,
    progress_cb: ProgressCb = None,
) -> dict:
    """Steps 1-6. Returns a dict — see the `return` block for the exact keys —
    including per-joint 3D trajectories (mm, root-relative) and every REBA
    anatomical angle (deg) computed by REBA_Engine/reba/angles.py, ready for
    reba_scoring.py and activity_layer.py.
    """
    _register_research_paths(cfg)
    from .rtmpose_adapter import run_on_video  # vendored SyncApp copy — see vendor/rtmpose/PROVENANCE.md
    from reba import angles as reba_angles  # REBA_Engine
    from reba import joint_mapping as jm  # REBA_Engine

    video_path = Path(video_path)
    if not video_path.exists():
        raise FileNotFoundError(video_path)

    # ── Step 1-3: video -> RTMPose 2D -> 21-joint mapping ────────────────────
    if progress_cb:
        progress_cb(0, "Running RTMPose 21-joint 2D detection...")
    # vicon_pelvis_2d=None: a new deployment video has no synced Vicon pelvis
    # to guide subject selection, so this uses the unguided (confidence +
    # nearest-neighbour) fallback — see rtmpose_pipeline.py's own docstring
    # for why that is unsafe for BUILDING the training dataset but is the
    # only option available at deployment time. subject_found_frac reports
    # how often it worked so a caller can judge trust, not to hide the risk.
    df2d = run_on_video(
        video_path, device=cfg.device, progress_cb=progress_cb, progress_range=(0, 60),
    )
    subject_found_frac = float(df2d["subject_found"].mean())

    cap = cv2.VideoCapture(str(video_path))
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    fps = float(cap.get(cv2.CAP_PROP_FPS)) or 30.0
    cap.release()

    pos2d_px = _reorder_to_canonical(df2d, cfg.canonical_joints)
    pos2d_norm = _normalize_2d(pos2d_px, width, cfg.root_idx)

    total_frames = len(pos2d_norm)
    window = cfg.checkpoint.num_frame
    window_starts = _make_window_starts(total_frames, window)
    n_windows = len(window_starts)
    used_frames = total_frames  # backward-aligned tail window covers every frame — nothing dropped

    # ── Step 4: PoseMamba 3D inference ───────────────────────────────────────
    if progress_cb:
        progress_cb(65, f"Running PoseMamba 3D lifting ({cfg.checkpoint.label})...")
    model = get_model(cfg)
    pred_3d_m = np.full((total_frames, len(cfg.canonical_joints), 3), np.nan)
    skipped_windows: list[int] = []
    covered_until = 0
    with torch.no_grad():
        for w, start in enumerate(window_starts):
            chunk = pos2d_norm[start:start + window]
            if np.isnan(chunk).any():
                # Subject not found for at least one frame in this window —
                # never feed a partially-NaN window through the model. Left
                # NaN in pred_3d_m; a deployment adaptation over
                # run_posemamba_trial.py's own "raise on any NaN 2D input"
                # rule, which assumes clean research-trial input.
                skipped_windows.append(w)
                covered_until = start + window
                continue
            x = torch.from_numpy(chunk).float().unsqueeze(0).to(cfg.device)
            out = model(x)  # (1, window, J, 3) metres, root-relative (training convention)
            pred = out.squeeze(0).cpu().numpy()
            # REBA_Engine's own stitch_windows(): keep only the frames not
            # already emitted by an earlier window, so the backward-aligned
            # tail window's overlap is de-duplicated, not double-written.
            emit_from = max(start, covered_until)
            pred_3d_m[emit_from:start + window] = pred[emit_from - start:]
            covered_until = start + window
            if progress_cb:
                progress_cb(65 + int(25 * (w + 1) / n_windows), f"3D lifting window {w + 1}/{n_windows}")

    pred_3d_mm = pred_3d_m * 1000.0  # angles.py expects mm; angles are translation-invariant,
    # so root-relative (not absolute-world) coordinates are fine for REBA/kinematics.
    joints = jm.array_to_dict(pred_3d_mm)

    # ── Step 5-6: anatomical angles ──────────────────────────────────────────
    if progress_cb:
        progress_cb(92, "Computing anatomical angles...")
    angle_arrs = reba_angles.compute_reba_angles(joints)
    if progress_cb:
        progress_cb(95, "Pose pipeline done")

    return {
        "video_path": str(video_path),
        "fps": fps,
        "width": width,
        "total_frames": total_frames,
        "used_frames": used_frames,
        "dropped_tail_frames": 0,  # backward-aligned tail window — see module docstring
        "n_windows": n_windows,
        "skipped_windows": skipped_windows,
        "subject_found_frac": subject_found_frac,
        "joints_mm": joints,
        "angles": angle_arrs,
        "checkpoint_label": cfg.checkpoint.label,
        "angle_source": "REBA_Engine/reba/angles.py",
    }
