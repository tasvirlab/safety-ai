"""Top-level orchestrator: video path in, the step-10 JSON dict out.

Wires together, in order: pose_pipeline (1-6) -> reba_scoring (7, Table C
only) -> activity_layer + conditional_reba (8-9) -> output_schema (10). This
is the one function a caller (a CLI test script now, an HTTP endpoint later
once backend/README.md's architecture question is settled) should call.

Table C vs. Activity ordering: REBA_Engine/reba/scorer.py::score_frame()'s
own `activity` kwarg does not affect `table_c_score` at all (`table_c_score =
TABLE_C[score_a-1][score_b-1]`, computed before `reba_score = table_c_score +
activity`). This pipeline calls score_frame() with `activity=0` on every
frame — not a placeholder or a hack, but the correct value for a call whose
only purpose is to obtain `table_c_score`, since it provably cannot change
that field. The REAL Activity Score (from static/repetition/rapid-motion
evidence, not a caller-supplied assumption) is computed separately in
conditional_reba.py and added to `table_c_score` there. Passing activity=0
also means summarise_trial()'s own peak-frame selection (by max reba_score)
reduces to "the frame with the worst Table C score" — the same
worst-observed-posture convention this pipeline always used.
"""
from __future__ import annotations

import time
from pathlib import Path
from typing import Callable, Optional

from .config import PipelineConfig, load_config
from .conditional_reba import compute_conditional_reba
from .output_schema import build_result_json
from .pose_pipeline import run_pose_pipeline

ProgressCb = Optional[Callable[[int, str], None]]


def run_pipeline(
    video_path: str | Path,
    *,
    assessment_id: str,
    manual_reba: dict | None = None,
    is_locomotion_task: bool | None = None,
    unstable_base: bool | None = None,
    cfg: PipelineConfig | None = None,
    progress_cb: ProgressCb = None,
) -> dict:
    """Run the full video -> structured-JSON pipeline.

    `manual_reba`: overrides for individual keys of
        cfg.manual_reba_defaults (load_kg, load_shock, coupling, unilateral,
        shoulder_raised_r/l, supported_or_leaning_r/l, neck_twist_modifier,
        upper_arm_rotated_r/l, wrist_deviation_r/l) — entered task metadata,
        never inferred from pose, per REBA_Engine/reba/posture_scores.py's own
        design. None (default) uses config.yaml's documented ASSUMPTION
        defaults unchanged — see that file's own comment for why each one is
        an assumption, not an observed fact, until the app collects it.
    `is_locomotion_task`/`unstable_base`: same convention, for
        activity_layer.py's repetition/rapid-motion detectors specifically
        (not REBA_Engine/reba/scorer.py). None uses config.yaml's default
        (False for both).
    """
    cfg = cfg or load_config()
    started_ms = int(time.time() * 1000)
    warnings: list[str] = []

    md = cfg.manual_reba_defaults
    manual = dict(
        load_kg=md.load_kg, load_shock=md.load_shock, coupling=md.coupling, unilateral=md.unilateral,
        shoulder_raised_r=md.shoulder_raised_r, shoulder_raised_l=md.shoulder_raised_l,
        supported_or_leaning_r=md.supported_or_leaning_r, supported_or_leaning_l=md.supported_or_leaning_l,
        neck_twist_modifier=md.neck_twist_modifier,
        upper_arm_rotated_r=md.upper_arm_rotated_r, upper_arm_rotated_l=md.upper_arm_rotated_l,
        wrist_deviation_r=md.wrist_deviation_r, wrist_deviation_l=md.wrist_deviation_l,
    )
    if manual_reba:
        manual.update(manual_reba)
    is_locomotion_task = md.is_locomotion_task if is_locomotion_task is None else is_locomotion_task
    unstable_base = md.unstable_base if unstable_base is None else unstable_base

    pose = run_pose_pipeline(video_path, cfg, progress_cb=progress_cb)
    if pose["skipped_windows"]:
        warnings.append(
            f"{len(pose['skipped_windows'])}/{pose['n_windows']} windows skipped "
            f"(subject not found for part of the window) — see video.skipped_windows."
        )
    if pose["subject_found_frac"] < 0.9:
        warnings.append(
            f"Subject found in only {pose['subject_found_frac']:.1%} of frames "
            f"(unguided detection — no Vicon reference for this video). Treat "
            f"results with caution; see rtmpose_pipeline.py's subject-selection docstring."
        )
    if any(v is None for k, v in manual.items() if k in
          ("neck_twist_modifier", "upper_arm_rotated_r", "upper_arm_rotated_l",
           "wrist_deviation_r", "wrist_deviation_l")):
        warnings.append(
            "One or more structurally-unsupported REBA modifiers (neck twist, "
            "upper-arm rotation, wrist deviation) have no reference value — "
            "Table C score is conservative (under-estimate only), see reba.fully_evaluated."
        )

    from .reba_scoring import score_reba
    reba = score_reba(
        pose["angles"], pose["total_frames"], cfg,
        activity=0,  # see module docstring — table_c_score is unaffected by this
        **manual,
    )

    table_c_score = reba["summary"]["peak_frame_detail"]["table_c_score"]
    conditional = compute_conditional_reba(
        table_c_score, pose["angles"], pose["fps"], cfg,
        is_locomotion_task=is_locomotion_task, unstable_base=unstable_base,
    )

    return build_result_json(
        assessment_id=assessment_id,
        processing_started_at_epoch_ms=started_ms,
        pose=pose,
        reba=reba,
        conditional=conditional,
        warnings=warnings,
    )
