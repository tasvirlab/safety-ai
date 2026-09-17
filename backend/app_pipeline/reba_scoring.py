"""Pipeline step 6-7: REBA Tables A/B/C scoring via REBA_Engine's frozen
scorer, with load/force, coupling, and every other manual REBA input applied
as manual task metadata (never inferred from pose).

Thin wrapper only — REBA_Engine/reba/scorer.py::score_frame() and
REBA_Engine/pipeline.py::summarise_trial() are never modified, and this
module never writes to REBA_Engine/outputs/ the way REBA_Engine/pipeline.py's
own run_trial() does (that function's file-writing side effects don't belong
in a read-only dependency's own folder) — it calls the same per-frame scoring
function in the same loop pattern REBA_Engine's own run_trial() uses, and
reuses summarise_trial() for the trial-level rollup, without any of that
function's disk I/O.

Note on `activity` (REBA's own Score-A-adjacent Activity Score, 0-3): this is
NOT computed here. run_pipeline.py passes it in as a required kwarg, sourced
from activity_layer.py's static/repetition/rapid-motion flags
(int(static_flag) + int(repetition_flag) + int(rapid_change_flag), each 0 or
1) — see conditional_reba.py. This wrapper just forwards whatever activity
int it is given straight into score_frame(), exactly like REBA_Engine's own
pipeline.py does.
"""
from __future__ import annotations

import sys
from pathlib import Path

from .config import PipelineConfig


def _register_research_paths(cfg: PipelineConfig) -> None:
    p = cfg.research_data_root / "REBA_Engine"
    if str(p) not in sys.path:
        sys.path.insert(0, str(p))


def score_reba(
    angles: dict,
    n_frames: int,
    cfg: PipelineConfig,
    *,
    activity: int,
    load_kg: float,
    load_shock: bool,
    coupling: int,
    unilateral: bool,
    shoulder_raised_r: bool,
    shoulder_raised_l: bool,
    supported_or_leaning_r: bool,
    supported_or_leaning_l: bool,
    neck_twist_modifier: bool | None,
    upper_arm_rotated_r: bool | None,
    upper_arm_rotated_l: bool | None,
    wrist_deviation_r: bool | None,
    wrist_deviation_l: bool | None,
) -> dict:
    """Frame-wise REBA Tables A/B/C over the whole clip, plus trial summary.

    Args:
        angles: pose_pipeline.run_pose_pipeline()'s "angles" dict (REBA_Engine
            channel set).
        n_frames: number of frames in `angles` (pose["total_frames"]).
        activity: the REBA Activity Score (0-3) for the WHOLE trial — a single
            int applied identically to every frame, matching how
            REBA_Engine/pipeline.py::run_trial() itself applies one set of
            manual_inputs to every frame in a trial. See module docstring for
            where this int comes from.
        Every other kwarg: passed straight through to
            REBA_Engine/reba/scorer.py::score_frame() — see that function's
            own docstring for exactly what each one means and why none of
            them has a default there.

    Returns:
        {"results": list[dict] (score_frame() per frame), "summary": dict
        (REBA_Engine/pipeline.py::summarise_trial()'s own rollup)}
    """
    _register_research_paths(cfg)
    from reba import scorer as reba_scorer  # REBA_Engine
    from pipeline import summarise_trial  # REBA_Engine

    manual_inputs = dict(
        load_kg=load_kg, load_shock=load_shock, coupling=coupling, activity=activity,
        unilateral=unilateral, shoulder_raised_r=shoulder_raised_r, shoulder_raised_l=shoulder_raised_l,
        supported_or_leaning_r=supported_or_leaning_r, supported_or_leaning_l=supported_or_leaning_l,
        neck_twist_modifier=neck_twist_modifier, upper_arm_rotated_r=upper_arm_rotated_r,
        upper_arm_rotated_l=upper_arm_rotated_l, wrist_deviation_r=wrist_deviation_r,
        wrist_deviation_l=wrist_deviation_l,
    )

    results = []
    for i in range(n_frames):
        frame_angles = {k: (float(v[i]) if v is not None else None) for k, v in angles.items()}
        results.append(reba_scorer.score_frame(frame_angles, **manual_inputs))

    summary = summarise_trial(results)
    return {"results": results, "summary": summary, "manual_inputs": manual_inputs}
