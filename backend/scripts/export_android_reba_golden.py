from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

import numpy as np

PROJECT_ROOT = Path(__file__).resolve().parents[2]
REBA_ROOT = Path("/mnt/c/Users/tempadmin/Research DATA/REBA_Engine")
MODEL_DIR = (
    PROJECT_ROOT
    / "posemamba_deployment/android/posemamba-scan"
    / "src/androidTest/assets/model"
)
EXPECTED_PATH = MODEL_DIR / "expected.bin"
MANIFEST_PATH = MODEL_DIR / "model_manifest.json"
OUTPUT_PATH = (
    PROJECT_ROOT
    / "app/src/androidTest/assets/reba/reba_golden.json"
)

sys.path.insert(0, str(REBA_ROOT))

from reba.angles import compute_reba_angles
from reba.scorer import score_frame

JOINT_NAMES = [
    "HD", "C7", "T10", "PELCEN",
    "LSHO", "RSHO", "LELB", "RELB",
    "LWR", "RWR", "LHAND", "RHAND",
    "LHIP", "RHIP", "LKNE", "RKNE",
    "LANK", "RANK", "LTOE", "RTOE", "L45",
]

VALUES_PER_WINDOW = 243 * 21 * 3

raw = np.fromfile(EXPECTED_PATH, dtype="<f4")
if raw.size % VALUES_PER_WINDOW != 0:
    raise RuntimeError(
        f"Unexpected expected.bin size: {raw.size} floats"
    )

window_count = raw.size // VALUES_PER_WINDOW
windows = raw.reshape(window_count, 243, 21, 3)

manifest = {}
if MANIFEST_PATH.is_file():
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))

candidates = [
    (0, 0), (0, 40), (0, 80), (0, 120),
    (0, 160), (0, 200), (0, 242),
]
if window_count > 1:
    candidates += [
        (1, 0), (1, 60), (1, 120), (1, 180), (1, 242),
    ]

manual_profiles = [
    {"loadKg": 0.0, "suddenForce": False, "coupling": 0},
    {"loadKg": 5.0, "suddenForce": False, "coupling": 1},
    {"loadKg": 10.0, "suddenForce": True, "coupling": 2},
    {"loadKg": 12.0, "suddenForce": False, "coupling": 3},
]

samples = []

for sample_index, (window_index, frame_index) in enumerate(candidates):
    xyz = windows[window_index, frame_index]

    if not np.isfinite(xyz).all():
        raise RuntimeError(
            f"Non-finite 3D fixture at window={window_index}, "
            f"frame={frame_index}"
        )

    joints = {
        name: xyz[joint_index][None, :].astype(np.float64)
        for joint_index, name in enumerate(JOINT_NAMES)
    }

    angle_arrays = compute_reba_angles(joints)
    angles = {
        name: None if values is None else float(values[0])
        for name, values in angle_arrays.items()
    }

    manual = manual_profiles[sample_index % len(manual_profiles)]

    result = score_frame(
        angles,
        load_kg=manual["loadKg"],
        load_shock=manual["suddenForce"],
        coupling=manual["coupling"],
        activity=0,
        unilateral=False,
        shoulder_raised_r=False,
        shoulder_raised_l=False,
        supported_or_leaning_r=False,
        supported_or_leaning_l=False,
        neck_twist_modifier=None,
        upper_arm_rotated_r=None,
        upper_arm_rotated_l=None,
        wrist_deviation_r=None,
        wrist_deviation_l=None,
    )

    if not result["valid"]:
        raise RuntimeError(
            f"Invalid REBA sample at window={window_index}, "
            f"frame={frame_index}: {result['invalid_reasons']}"
        )

    samples.append({
        "windowIndex": window_index,
        "frameIndex": frame_index,
        "coordinates": xyz.reshape(-1).astype(float).tolist(),
        "manual": manual,
        "activityScore": 0,
        "activityPending": True,
        "angles": angles,
        "expected": result,
    })

fixture = {
    "schemaVersion": 1,
    "description": (
        "PoseMamba 3D to REBA golden parity fixture. "
        "Activity is temporarily fixed to zero."
    ),
    "jointNames": JOINT_NAMES,
    "modelSha256": manifest.get("model_sha256"),
    "windowCount": int(window_count),
    "sampleCount": len(samples),
    "samples": samples,
}

OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
text = json.dumps(
    fixture,
    indent=2,
    sort_keys=True,
    allow_nan=False,
)
OUTPUT_PATH.write_text(text + "\n", encoding="utf-8")

digest = hashlib.sha256(OUTPUT_PATH.read_bytes()).hexdigest()

print("Windows:", window_count)
print("Samples:", len(samples))
print("Output:", OUTPUT_PATH)
print("SHA256:", digest)
print("REBA GOLDEN FIXTURE: PASS")