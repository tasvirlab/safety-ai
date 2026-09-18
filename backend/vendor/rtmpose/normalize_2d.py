"""Vendored, byte-verbatim extract of SyncApp's normalize_2d() and its
strictly-required constants — NOT a whole-file copy (see PROVENANCE.md for
why this function lives in its own module instead of alongside
rtmpose_pipeline.py's whole-file vendor copy).

Source: SyncApp/backend/core/dataset_export.py, lines 46-60 (JOINT_NAMES /
ROOT_JOINT / ROOT_IDX) and lines 268-274 (normalize_2d itself). Everything
between the BEGIN/END markers below is copied character-for-character from
that file — not retyped, not reformatted, not reinterpreted. Verified via
`diff` against the live extraction of those exact line ranges — see
PROVENANCE.md's "normalize_2d.py verification" section.

dataset_export.py itself is NOT vendored, imported, or executed anywhere in
this backend — only this one function and the constants it directly
references. Its other responsibilities (dataset building, train/test
splitting, multi-trial processing, spine-ratio fitting) are out of scope by
explicit instruction and are not present here in any form.
"""
from __future__ import annotations

import numpy as np

# ============================================================================
# BEGIN VERBATIM COPY — SyncApp/backend/core/dataset_export.py, lines 46-60
# ============================================================================
JOINT_NAMES: list[str] = [
    "HD", "C7", "T10", "PELCEN", "LSHO", "RSHO", "LELB", "RELB", "LWR", "RWR",
    "LHAND", "RHAND", "LHIP", "RHIP", "LKNE", "RKNE", "LANK", "RANK",
    "LTOE", "RTOE", "L45",
]
N_JOINTS = len(JOINT_NAMES)
assert N_JOINTS == 21
assert (JOINT_NAMES[1] == "C7" and JOINT_NAMES[2] == "T10"
        and JOINT_NAMES[3] == "PELCEN" and JOINT_NAMES[20] == "L45"), (
    "Joint order drifted from the canonical loss_construction.py contract "
    "(Pelvis must be index 3) — do not silently re-index."
)

ROOT_JOINT = "PELCEN"
ROOT_IDX = JOINT_NAMES.index(ROOT_JOINT)
# ============================================================================
# END VERBATIM COPY (lines 46-60)
# ============================================================================


# ============================================================================
# BEGIN VERBATIM COPY — SyncApp/backend/core/dataset_export.py, lines 268-274
# ============================================================================
def normalize_2d(positions_2d_px: np.ndarray, width: int) -> tuple[np.ndarray, np.ndarray]:
    pelvis = positions_2d_px[:, ROOT_IDX, :]
    pelvis_valid = np.isfinite(pelvis).all(axis=1)
    centered = positions_2d_px - pelvis[:, None, :]
    normalized = centered / (width / 2.0)
    joints_valid = np.isfinite(positions_2d_px).all(axis=(1, 2))
    return normalized, joints_valid & pelvis_valid
# ============================================================================
# END VERBATIM COPY (lines 268-274)
# ============================================================================
