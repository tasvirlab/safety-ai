"""Adapter for the vendored RTMPose 2D-detection + PoseMamba-input-
normalization modules (steps 1-3 and the 2D normalization half of step 4's
temporal input prep).

This is the ONLY place in the active backend that knows about
`vendor/rtmpose/`'s internal package layout (`core.rtmpose_pipeline`,
`core.vicon_io`, `utils.task_paths`, `normalize_2d`) — those files are
unmodified (whole-file copies) or verbatim extracts (`normalize_2d.py` — see
that file's own docstring) of SyncApp's implementation (see
`vendor/rtmpose/PROVENANCE.md`) and are never imported directly by
pose_pipeline.py or anything else outside this module, per the instruction to
"handle integration through a separate adapter outside the copied folder."

Registers `vendor/rtmpose/` (not `vendor/` itself) onto `sys.path`, so the
vendored files' own unchanged internal imports (`from utils.task_paths import
Artifacts`, `from core.vicon_io import read_frame_ids`) resolve exactly as
they do inside SyncApp, without this backend needing SyncApp on sys.path at
all. `run_on_video` and `normalize_2d` are re-exported UNCHANGED — this
module adds no wrapping logic, no preprocessing, no parameter translation; it
is purely an import path indirection. Any real integration logic (config
plumbing, canonical joint ordering, windowing, etc.) belongs in
pose_pipeline.py, which calls this adapter exactly as it used to call SyncApp
directly.

After this adapter exists, the active backend has NO runtime dependency on
`Research DATA/SyncApp` — `research_data_root` is only ever used for
PoseMamba and REBA_Engine (see pose_pipeline.py / config.py). Note
`normalize_2d`'s ROOT_IDX=3 (vendored, from dataset_export.py's own
canonical JOINT_NAMES) matches config.yaml's canonical_joints order exactly
— both put PELCEN at index 3 — so no reordering is needed between
pose_pipeline.py's 2D array and this function's expectations.
"""
from __future__ import annotations

import sys
from pathlib import Path

_VENDOR_ROOT = Path(__file__).resolve().parents[1] / "vendor" / "rtmpose"
if not _VENDOR_ROOT.is_dir():
    raise FileNotFoundError(
        f"Vendored RTMPose module not found at {_VENDOR_ROOT} — see "
        f"vendor/rtmpose/PROVENANCE.md for what should be there."
    )
if str(_VENDOR_ROOT) not in sys.path:
    sys.path.insert(0, str(_VENDOR_ROOT))

from core.rtmpose_pipeline import (  # noqa: E402  — vendored, unchanged
    CONF_THRESHOLD_DEFAULT,
    DEFAULT_T_L45,
    DEFAULT_T_T10,
    DIRECT_JOINTS,
    HEAD_KPTS,
    JOINT_NAMES,
    MIN_HEAD_KPTS,
    map_to_21_joints,
    run_on_video,
    select_tracked_person,
)
from normalize_2d import normalize_2d  # noqa: E402  — vendored verbatim extract, unchanged

# Re-exported for backend/tests/test_rtmpose_mapping.py, which must exercise
# the vendored implementation only through this adapter — never by importing
# core.rtmpose_pipeline directly (see that test file's own docstring).
__all__ = [
    "run_on_video", "normalize_2d", "map_to_21_joints", "select_tracked_person",
    "JOINT_NAMES", "DIRECT_JOINTS", "HEAD_KPTS", "MIN_HEAD_KPTS",
    "CONF_THRESHOLD_DEFAULT", "DEFAULT_T_T10", "DEFAULT_T_L45",
]
