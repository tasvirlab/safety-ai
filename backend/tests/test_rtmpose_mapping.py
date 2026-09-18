"""Deterministic, synthetic verification of the vendored RTMPose 133-to-21
mapping and PoseMamba input normalization — no video, no model, no network.

Imports the vendored implementation ONLY through `app_pipeline.rtmpose_adapter`
(never `core.rtmpose_pipeline`/`normalize_2d` directly), per the instruction
that the vendored folder is integrated exclusively through that adapter.

Covers, in order:
  1. exactly 21 output joints (shape checks on joints_2d/valid/conf);
  2. joint order — the vendored module's OWN order (JOINT_NAMES, PELCEN@12,
     used internally by DIRECT_JOINTS/map_to_21_joints) is distinct from the
     order actually fed to the trained PoseMamba model (config.yaml's
     canonical_joints, PELCEN@3) — pose_pipeline.py's _reorder_to_canonical()
     converts between them by NAME, never by position. Both orders are
     checked explicitly, labeled, so the two are never conflated;
  3. every DIRECT_JOINTS entry maps position+confidence unchanged;
  4. constructed proxy joints (HD = mean of 4 head kpts; C7/PELCEN =
     shoulder/hip midpoints; T10/L45 = lerp(C7, PELCEN, t));
  5. left/right keypoints are never swapped;
  6. confidence propagation (a below-threshold joint keeps its RAW score,
     never NaN, while its position/valid flag reflect the threshold);
  7. expected tensor shapes end to end, including the (1, window, 21, 2)
     shape pose_pipeline.py actually feeds PoseMamba;
  8. vendored normalize_2d()'s exact formula and validity mask, against a
     hand-computed reference;
  9. no active runtime import or sys.path entry pointing at the real
     Research DATA/SyncApp project;
 10. the COMPLETE active path — map_to_21_joints() -> a run_on_video()-shaped
     DataFrame -> the real pose_pipeline._reorder_to_canonical() -> the real
     vendored normalize_2d() -> the exact (1, window, 21, 2) tensor shape fed
     to PoseMamba — using unique per-joint sentinel coordinates, confirming
     every FINAL tensor index holds the anatomically correct joint, not just
     that each function is correct in isolation;
 11. the hard training-joint-order assertion actually raises on a
     deliberately wrong order (not just passes on the correct one).

Run:
    python -m tests.test_rtmpose_mapping        (from backend/, any env with
    numpy — no torch/cv2/rtmlib/GPU needed; this only exercises pure-Python
    array logic, never rtmlib.Wholebody or cv2.VideoCapture).
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))  # backend/, for app_pipeline

import pandas as pd  # noqa: E402

from app_pipeline.rtmpose_adapter import (  # noqa: E402
    CONF_THRESHOLD_DEFAULT,
    DEFAULT_T_L45,
    DEFAULT_T_T10,
    DIRECT_JOINTS,
    HEAD_KPTS,
    JOINT_NAMES,
    map_to_21_joints,
    normalize_2d,
)
from app_pipeline.config import load_config  # noqa: E402
from app_pipeline import pose_pipeline as pp  # noqa: E402  — the REAL, non-vendored reorder/assertion code under test


def _build_synthetic_133(rng: np.random.Generator) -> tuple[np.ndarray, np.ndarray]:
    """(133, 2) keypoints / (133,) scores. Every DIRECT_JOINTS source and
    every HEAD_KPTS source gets a DISTINCT, deterministic position (seeded
    rng) and a confidence above CONF_THRESHOLD_DEFAULT, except RWR (forced
    below threshold) — everything else is left at (0, 0) / 0.0, which the
    test never reads."""
    kp = np.zeros((133, 2))
    sc = np.zeros(133)
    for name, idx in DIRECT_JOINTS.items():
        kp[idx] = [float(idx) * 3.0 + 1.0, float(idx) * 5.0 + 2.0]
        sc[idx] = 0.9
    for i in HEAD_KPTS:
        kp[i] = [10.0 + i, 20.0 + i]
        sc[i] = 0.8
    R_WR = DIRECT_JOINTS["RWR"]
    sc[R_WR] = 0.1  # deliberately below CONF_THRESHOLD_DEFAULT (0.3)
    return kp, sc


def test_shapes_and_joint_count() -> None:
    kp, sc = _build_synthetic_133(np.random.default_rng(0))
    joints_2d, valid, conf = map_to_21_joints(kp, sc, CONF_THRESHOLD_DEFAULT, DEFAULT_T_T10, DEFAULT_T_L45)
    assert len(JOINT_NAMES) == 21, f"expected exactly 21 canonical joints, got {len(JOINT_NAMES)}"
    assert joints_2d.shape == (21, 2), joints_2d.shape
    assert valid.shape == (21,), valid.shape
    assert conf.shape == (21,), conf.shape

    # The (1, window, 21, 2) shape pose_pipeline.py actually builds before
    # calling PoseMamba (torch.from_numpy(chunk).float().unsqueeze(0)) —
    # checked here as plain numpy, no torch dependency needed.
    window = 243
    stacked = np.tile(joints_2d[None, :, :], (window, 1, 1))  # (window, 21, 2)
    batched = stacked[None, :, :, :]  # (1, window, 21, 2) — matches unsqueeze(0)
    assert batched.shape == (1, window, 21, 2), batched.shape
    print("test_shapes_and_joint_count OK")


def test_vendored_own_joint_order() -> None:
    """The vendored module's OWN internal order (rtmpose_pipeline.JOINT_NAMES,
    used by DIRECT_JOINTS/map_to_21_joints) — PELCEN at index 12. This is
    frozen exactly as SyncApp defines it; drift here means the vendored file
    itself changed, which must never happen silently."""
    expected = [
        "HD", "C7", "RSHO", "LSHO", "RELB", "LELB", "RWR", "LWR", "RHAND", "LHAND",
        "T10", "L45", "PELCEN", "RHIP", "LHIP", "RKNE", "LKNE", "RANK", "LANK",
        "RTOE", "LTOE",
    ]
    assert list(JOINT_NAMES) == expected, (list(JOINT_NAMES), expected)
    assert JOINT_NAMES.index("PELCEN") == 12
    print("test_vendored_own_joint_order OK")


def test_pose_mamba_training_joint_order() -> None:
    """The order actually fed to the trained PoseMamba model — config.yaml's
    canonical_joints (= REBA_Engine's reba/joint_mapping.JOINT_NAMES), PELCEN
    at index 3. Distinct from the vendored module's own order above;
    pose_pipeline.py's _reorder_to_canonical() converts between them by NAME
    (df[f"{name}_X"]), which this test reproduces directly on the array."""
    cfg = load_config()
    assert cfg.canonical_joints.index("PELCEN") == 3
    assert set(cfg.canonical_joints) == set(JOINT_NAMES), (
        "the two orders must be the same 21 joint NAMES, just reordered"
    )

    kp, sc = _build_synthetic_133(np.random.default_rng(1))
    joints_2d, valid, conf = map_to_21_joints(kp, sc, CONF_THRESHOLD_DEFAULT, DEFAULT_T_T10, DEFAULT_T_L45)

    # Name-indexed reorder, exactly _reorder_to_canonical()'s own logic.
    reordered = np.stack([joints_2d[JOINT_NAMES.index(name)] for name in cfg.canonical_joints])
    assert reordered.shape == (21, 2)
    assert np.allclose(reordered[cfg.root_idx], joints_2d[JOINT_NAMES.index("PELCEN")]), (
        "PELCEN's position must be identical in both orders — only its INDEX changes"
    )
    print("test_pose_mamba_training_joint_order OK")


def test_direct_mappings() -> None:
    kp, sc = _build_synthetic_133(np.random.default_rng(2))
    joints_2d, valid, conf = map_to_21_joints(kp, sc, CONF_THRESHOLD_DEFAULT, DEFAULT_T_T10, DEFAULT_T_L45)

    for name, src_idx in DIRECT_JOINTS.items():
        if name == "RWR":
            continue  # covered separately by test_confidence_propagation
        j = JOINT_NAMES.index(name)
        assert np.array_equal(joints_2d[j], kp[src_idx]), f"{name}: position not passed through unchanged"
        assert valid[j] is True or valid[j] == True, f"{name}: must be valid (above threshold)"  # noqa: E712
        assert np.isclose(conf[j], sc[src_idx]), f"{name}: confidence not passed through unchanged"
    print("test_direct_mappings OK")


def test_proxy_joints() -> None:
    kp, sc = _build_synthetic_133(np.random.default_rng(3))
    joints_2d, valid, conf = map_to_21_joints(kp, sc, CONF_THRESHOLD_DEFAULT, DEFAULT_T_T10, DEFAULT_T_L45)

    L_SHO, R_SHO = DIRECT_JOINTS["LSHO"], DIRECT_JOINTS["RSHO"]
    L_HIP, R_HIP = DIRECT_JOINTS["LHIP"], DIRECT_JOINTS["RHIP"]
    c7_expected = (kp[L_SHO] + kp[R_SHO]) / 2.0
    pelcen_expected = (kp[L_HIP] + kp[R_HIP]) / 2.0
    t10_expected = (1 - DEFAULT_T_T10) * c7_expected + DEFAULT_T_T10 * pelcen_expected
    l45_expected = (1 - DEFAULT_T_L45) * c7_expected + DEFAULT_T_L45 * pelcen_expected
    hd_expected = np.mean(np.stack([kp[i] for i in HEAD_KPTS]), axis=0)

    assert np.allclose(joints_2d[JOINT_NAMES.index("C7")], c7_expected)
    assert np.allclose(joints_2d[JOINT_NAMES.index("PELCEN")], pelcen_expected)
    assert np.allclose(joints_2d[JOINT_NAMES.index("T10")], t10_expected)
    assert np.allclose(joints_2d[JOINT_NAMES.index("L45")], l45_expected)
    assert np.allclose(joints_2d[JOINT_NAMES.index("HD")], hd_expected)
    assert valid[JOINT_NAMES.index("HD")]
    assert valid[JOINT_NAMES.index("C7")] and valid[JOINT_NAMES.index("PELCEN")]
    print("test_proxy_joints OK")


def test_left_right_not_swapped() -> None:
    """Left and right keypoints get deliberately DIFFERENT synthetic
    coordinates (via their distinct COCO-WholeBody indices in
    _build_synthetic_133); a left/right swap anywhere in DIRECT_JOINTS or the
    proxy construction would fail these exact-equality checks."""
    kp, sc = _build_synthetic_133(np.random.default_rng(4))
    joints_2d, valid, conf = map_to_21_joints(kp, sc, CONF_THRESHOLD_DEFAULT, DEFAULT_T_T10, DEFAULT_T_L45)

    left_right_pairs = [
        ("LSHO", "RSHO"), ("LELB", "RELB"), ("LWR", "RWR"), ("LHIP", "RHIP"),
        ("LKNE", "RKNE"), ("LANK", "RANK"), ("LTOE", "RTOE"), ("LHAND", "RHAND"),
    ]
    for l_name, r_name in left_right_pairs:
        l_idx, r_idx = JOINT_NAMES.index(l_name), JOINT_NAMES.index(r_name)
        l_src, r_src = DIRECT_JOINTS[l_name], DIRECT_JOINTS[r_name]
        assert not np.allclose(kp[l_src], kp[r_src]), "test fixture bug: left/right sources must differ"
        if valid[l_idx]:
            assert np.array_equal(joints_2d[l_idx], kp[l_src]), f"{l_name} landed on the wrong source keypoint"
        if valid[r_idx]:
            assert np.array_equal(joints_2d[r_idx], kp[r_src]), f"{r_name} landed on the wrong source keypoint"
    print("test_left_right_not_swapped OK")


def test_confidence_propagation() -> None:
    kp, sc = _build_synthetic_133(np.random.default_rng(5))
    joints_2d, valid, conf = map_to_21_joints(kp, sc, CONF_THRESHOLD_DEFAULT, DEFAULT_T_T10, DEFAULT_T_L45)

    j_rwr = JOINT_NAMES.index("RWR")
    r_wr_src = DIRECT_JOINTS["RWR"]
    assert not valid[j_rwr], "RWR was set below CONF_THRESHOLD_DEFAULT, must be invalid"
    assert np.isnan(joints_2d[j_rwr]).all(), "an invalid joint's position must be NaN"
    assert np.isclose(conf[j_rwr], sc[r_wr_src]), (
        "confidence must be the RAW score even when below threshold, never NaN or 0"
    )
    print("test_confidence_propagation OK")


def test_normalize_2d_exact_formula() -> None:
    """Hand-computed reference for vendored normalize_2d() — root-center by
    PELCEN (index 3), divide by width/2, plus its exact validity mask."""
    F, J = 4, 21
    rng = np.random.default_rng(6)
    arr = rng.uniform(-500, 500, size=(F, J, 2))
    width = 1920

    pelvis = arr[:, 3, :].copy()
    expected_normalized = (arr - pelvis[:, None, :]) / (width / 2.0)
    expected_valid = np.isfinite(arr).all(axis=(1, 2)) & np.isfinite(pelvis).all(axis=1)

    normalized, valid = normalize_2d(arr, width)
    assert normalized.shape == (F, J, 2), normalized.shape
    assert np.allclose(normalized, expected_normalized)
    assert np.array_equal(valid, expected_valid)
    assert valid.all(), "no NaN injected in this case — every frame must be valid"

    # NaN pelvis -> that frame invalid, regardless of other joints.
    arr_nan = arr.copy()
    arr_nan[1, 3, :] = np.nan
    _, valid_nan = normalize_2d(arr_nan, width)
    assert not valid_nan[1] and valid_nan[0] and valid_nan[2] and valid_nan[3]

    # NaN on a non-pelvis joint -> that frame invalid too (joints_valid gate).
    arr_nan2 = arr.copy()
    arr_nan2[2, 7, :] = np.nan
    _, valid_nan2 = normalize_2d(arr_nan2, width)
    assert not valid_nan2[2] and valid_nan2[0] and valid_nan2[1] and valid_nan2[3]
    print("test_normalize_2d_exact_formula OK")


def test_no_active_syncapp_dependency() -> None:
    """After importing everything through the adapter, no loaded module and
    no sys.path entry may point at the real Research DATA/SyncApp project."""
    import core.rtmpose_pipeline as vendored_rp
    import core.vicon_io as vendored_vio
    import utils.task_paths as vendored_tp
    import normalize_2d as vendored_norm

    for mod in (vendored_rp, vendored_vio, vendored_tp, vendored_norm):
        f = str(Path(mod.__file__).resolve())
        assert "SyncApp" not in f, f"{mod.__name__} resolved to a live SyncApp path: {f}"
        assert "vendor" in f.lower() and "rtmpose" in f.lower(), f"{mod.__name__} did not resolve under vendor/rtmpose: {f}"

    for p in sys.path:
        assert "SyncApp" not in p, f"sys.path still contains a SyncApp path: {p}"

    print("test_no_active_syncapp_dependency OK")


def _build_synthetic_df2d(joints_2d: np.ndarray, conf: np.ndarray, valid: np.ndarray) -> pd.DataFrame:
    """One-row DataFrame shaped exactly like run_on_video()'s real output —
    <NAME>_X/_Y/_conf/_valid per vendored JOINT_NAMES, plus frame/
    subject_match_px/subject_found — built from a single map_to_21_joints()
    result, standing in for one video frame."""
    row = {"frame": 0}
    for j, name in enumerate(JOINT_NAMES):
        row[f"{name}_X"] = joints_2d[j, 0]
        row[f"{name}_Y"] = joints_2d[j, 1]
        row[f"{name}_conf"] = conf[j]
        row[f"{name}_valid"] = bool(valid[j])
    row["subject_match_px"] = float("nan")
    row["subject_found"] = True
    return pd.DataFrame([row])


def test_full_active_pipeline_order() -> None:
    """Traces UNIQUE sentinel coordinates through the complete active path —
    map_to_21_joints() -> a run_on_video()-shaped DataFrame -> the REAL
    (non-vendored) pose_pipeline._reorder_to_canonical() -> the REAL vendored
    normalize_2d() -> the (1, window, 21, 2) tensor shape actually handed to
    PoseMamba (torch.from_numpy(chunk).float().unsqueeze(0)) — confirming
    every final index holds the anatomically correct joint, not just that the
    individual functions behave correctly in isolation."""
    cfg = load_config()
    width = 1920

    # Every DIRECT_JOINTS source + every HEAD_KPTS source gets a distinct
    # sentinel coordinate (no two joints share a value) so a swap anywhere in
    # the chain is guaranteed to produce a detectable mismatch.
    kp, sc = _build_synthetic_133(np.random.default_rng(42))
    joints_2d, valid, conf = map_to_21_joints(kp, sc, CONF_THRESHOLD_DEFAULT, DEFAULT_T_T10, DEFAULT_T_L45)
    # RWR was deliberately below threshold in the shared fixture (NaN) — give
    # it a real value here so every one of the 21 canonical joints carries a
    # traceable, finite sentinel through the WHOLE chain (its confidence-
    # propagation behavior is already covered by test_confidence_propagation).
    j_rwr = JOINT_NAMES.index("RWR")
    joints_2d[j_rwr] = kp[DIRECT_JOINTS["RWR"]]
    valid[j_rwr] = True

    df2d = _build_synthetic_df2d(joints_2d, conf, valid)

    # ── The real, active reorder — pose_pipeline.py's own function, unchanged. ──
    pos2d_px = pp._reorder_to_canonical(df2d, cfg.canonical_joints, tuple(JOINT_NAMES))
    assert pos2d_px.shape == (1, 21, 2), pos2d_px.shape
    for j, name in enumerate(cfg.canonical_joints):
        expected = joints_2d[JOINT_NAMES.index(name)]
        assert np.array_equal(pos2d_px[0, j], expected), (
            f"canonical index {j} ({name}): expected the {name} sentinel, "
            f"got a value belonging to a different joint — reorder is wrong"
        )

    # ── The real, active hard assertion. ─────────────────────────────────────
    pp._assert_training_joint_order(cfg)  # must not raise

    # ── The real, vendored normalize_2d() on the reordered array. ────────────
    normalized, frame_valid = normalize_2d(pos2d_px, width)
    assert frame_valid[0], "every joint was given a finite sentinel — frame must be valid"
    pelvis = pos2d_px[0, cfg.root_idx].copy()
    for j, name in enumerate(cfg.canonical_joints):
        expected_norm = (joints_2d[JOINT_NAMES.index(name)] - pelvis) / (width / 2.0)
        assert np.allclose(normalized[0, j], expected_norm), (
            f"canonical index {j} ({name}): normalized value does not match "
            f"the expected root-centered, scaled sentinel"
        )
    assert np.allclose(normalized[0, cfg.root_idx], 0.0), "PELCEN must be exactly (0, 0) after root-centering"

    # ── The final tensor shape actually handed to PoseMamba (numpy stand-in
    #    for torch.from_numpy(chunk).float().unsqueeze(0) — no torch needed
    #    to check shape/values). ────────────────────────────────────────────
    window = cfg.checkpoint.num_frame
    chunk = np.tile(normalized, (window, 1, 1))          # (window, 21, 2)
    batched = chunk[None, :, :, :]                        # (1, window, 21, 2)
    assert batched.shape == (1, window, 21, 2), batched.shape
    for j, name in enumerate(cfg.canonical_joints):
        expected_norm = (joints_2d[JOINT_NAMES.index(name)] - pelvis) / (width / 2.0)
        assert np.allclose(batched[0, :, j, :], expected_norm), (
            f"final tensor index {j} does not hold {name} across every window frame"
        )

    # ── Which order places PELCEN at index 3? The PoseMamba/canonical order
    #    does; the vendored module's own internal order does not. ───────────
    assert cfg.canonical_joints[3] == "PELCEN"
    assert JOINT_NAMES[3] != "PELCEN" and JOINT_NAMES.index("PELCEN") == 12
    print("test_full_active_pipeline_order OK")


def test_hard_assertion_catches_drift() -> None:
    """The hard assertion is not decorative — prove it actually raises when
    the two orders genuinely disagree, using a minimal fake config object
    (pp._assert_training_joint_order only reads `.canonical_joints`)."""
    class _FakeCfg:
        canonical_joints = ("HD", "C7", "T10", "LSHO", "PELCEN") + tuple(JOINT_NAMES[5:])  # PELCEN moved, wrong

    try:
        pp._assert_training_joint_order(_FakeCfg())
        assert False, "a deliberately wrong joint order must raise, not pass silently"
    except AssertionError as e:
        assert "canonical_joints" in str(e) and "training order" in str(e)
    print("test_hard_assertion_catches_drift OK")


def _demo() -> None:
    test_shapes_and_joint_count()
    test_vendored_own_joint_order()
    test_pose_mamba_training_joint_order()
    test_direct_mappings()
    test_proxy_joints()
    test_left_right_not_swapped()
    test_confidence_propagation()
    test_normalize_2d_exact_formula()
    test_no_active_syncapp_dependency()
    test_full_active_pipeline_order()
    test_hard_assertion_catches_drift()
    print("\nAll test_rtmpose_mapping checks passed.")


if __name__ == "__main__":
    _demo()
