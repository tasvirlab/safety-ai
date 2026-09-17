"""Adapter for the vendored RTMPose 2D-detection module (steps 1-3).

This is the ONLY place in the active backend that knows about
`vendor/rtmpose/`'s internal package layout (`core.rtmpose_pipeline`,
`core.vicon_io`, `utils.task_paths`) — those files are an unmodified,
byte-for-byte copy of SyncApp's implementation (see
`vendor/rtmpose/PROVENANCE.md`) and are never imported directly by
pose_pipeline.py or anything else outside this module, per the instruction to
"handle integration through a separate adapter outside the copied folder."

Registers `vendor/rtmpose/` (not `vendor/` itself) onto `sys.path`, so the
vendored files' own unchanged internal imports (`from utils.task_paths import
Artifacts`, `from core.vicon_io import read_frame_ids`) resolve exactly as
they do inside SyncApp, without this backend needing SyncApp on sys.path at
all. `run_on_video` is re-exported UNCHANGED — this module adds no wrapping
logic, no preprocessing, no parameter translation; it is purely an import
path indirection. Any real integration logic (config plumbing, canonical
joint ordering, etc.) belongs in pose_pipeline.py, which calls this adapter's
`run_on_video` exactly as it used to call SyncApp's.

After this adapter exists, the active backend has NO runtime dependency on
`Research DATA/SyncApp` — `research_data_root` is only ever used for
PoseMamba and REBA_Engine (see pose_pipeline.py / config.py).
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

from core.rtmpose_pipeline import run_on_video  # noqa: E402  — vendored, unchanged

__all__ = ["run_on_video"]
