"""Pipeline step 10: assembles the final structured JSON for the Android app.

Shape is deliberately close to the app's own existing analysis contract
(AnalysisModels.kt: SafetyAnalysisResult / RebaComponents / BodyRegionFinding /
ActivityFindings in AndroidStudioProjects/SafetyAI/app/src/main/java/com/
azizbek/safetyai/analysis/) so wiring a future SafetyAnalysisEngine
implementation to this backend's response is a direct field mapping, not a
redesign — but this module does not import or depend on that Kotlin code; the
two are kept in sync by convention, documented here.

Every scoring/detection field in this JSON comes from REBA_Engine (angles,
Table A/B/C scoring, Activity Layer) — see `"source"` fields throughout. The
one disclosed exception is `video.*` (RTMPose 2D detection), which runs a
vendored, byte-identical copy of SyncApp's implementation under
`vendor/rtmpose/` (see that folder's PROVENANCE.md) — REBA_Engine has no 2D
pose-extraction module of its own. This backend has no runtime dependency on
the live SyncApp project.
"""
from __future__ import annotations

import time
from typing import Any

SCHEMA_VERSION = 1

BODY_REGIONS = ("trunk", "neck", "legs", "upper_arm", "lower_arm", "wrist")


def _body_region_findings(peak_frame_detail: dict) -> list[dict[str, Any]]:
    """One finding per REBA body region, from the peak (worst, highest-
    reba_score) VALID frame — REBA_Engine/pipeline.py::summarise_trial()'s own
    established convention (an invalid/occluded frame is excluded from every
    statistic and can never be the reported peak).

    upper_arm/lower_arm/wrist are paired (L/R) in REBA_Engine's `components`;
    the peak frame's own `dominant_arm` (the side score_frame() itself used
    for Score B) selects which side is reported, matching REBA_Engine's own
    worst-arm convention exactly rather than inventing a separate rule here.
    A "tie" (both sides scored equal raw Group B) reports the right side,
    noted explicitly — REBA_Engine's own score_frame() breaks that same tie
    the same way for its numeric Score B.
    """
    components = peak_frame_detail["components"]
    side = peak_frame_detail["dominant_arm"]
    side_key = "l" if side == "left" else "r"  # "right" or "tie" -> "r"

    return [
        {"region": "trunk", "score": components["trunk"]["score"]},
        {"region": "neck", "score": components["neck"]["score"]},
        {"region": "legs", "score": components["legs"]["score"]},
        {"region": "upper_arm", "score": components[f"upper_arm_{side_key}"]["score"],
         "side": side},
        {"region": "lower_arm", "score": components[f"lower_arm_{side_key}"]["score"],
         "side": side},
        {"region": "wrist", "score": components[f"wrist_{side_key}"]["score"],
         "side": side},
    ]


def build_result_json(
    *,
    assessment_id: str,
    processing_started_at_epoch_ms: int,
    pose: dict,           # pose_pipeline.run_pose_pipeline()'s return value
    reba: dict,            # reba_scoring.score_reba()'s return value
    conditional: dict,     # conditional_reba.compute_conditional_reba()'s return value
    warnings: list[str] | None = None,
    processing_errors: list[str] | None = None,
) -> dict[str, Any]:
    """Assemble the single JSON dict handed back to the caller (the Android
    app, via whatever transport backend/README.md's chosen architecture uses).
    """
    now_ms = int(time.time() * 1000)
    summary = reba["summary"]
    peak_detail = summary["peak_frame_detail"]

    return {
        "schema_version": SCHEMA_VERSION,
        "assessment_id": assessment_id,
        "processing_started_at_epoch_ms": processing_started_at_epoch_ms,
        "processing_completed_at_epoch_ms": now_ms,

        "availability": {
            # This pipeline only ever runs when the checkpoint + CUDA + all
            # research modules resolved — if it ran at all, it is available.
            # A caller that could not even reach this pipeline (no GPU
            # backend reachable, checkpoint missing, etc.) never gets this
            # JSON at all and must report its OWN "unavailable" status —
            # this pipeline cannot speak for that failure mode.
            "engine_available": True,
            "checkpoint_label": pose["checkpoint_label"],
        },

        "video": {
            "path": pose["video_path"],
            "fps": pose["fps"],
            "width": pose["width"],
            "total_frames": pose["total_frames"],
            "used_frames": pose["used_frames"],
            "dropped_tail_frames": pose["dropped_tail_frames"],
            "n_windows": pose["n_windows"],
            "skipped_windows": pose["skipped_windows"],
            "subject_found_frac": pose["subject_found_frac"],
            "source": "vendor/rtmpose/core/rtmpose_pipeline.py (vendored, unchanged copy of SyncApp's implementation — see PROVENANCE.md; REBA_Engine has no 2D-detection equivalent)",
        },

        "reba": {
            "summary": {k: v for k, v in summary.items() if k != "peak_frame_detail"},
            "peak_frame_detail": peak_detail,
            "body_regions": _body_region_findings(peak_detail),
            "source": "REBA_Engine/reba/scorer.py::score_frame() + pipeline.py::summarise_trial()",
        },

        "activity": {
            "static": conditional["modifiers"]["static"],
            "repetition": conditional["modifiers"]["repetition"],
            "rapid_motion": conditional["modifiers"]["rapid_motion"],
            "activity_score": conditional["activity_score"],
            "source": "REBA_Engine/activity_layer/{static,repetition_bout_counter,rapid_change}.py",
        },

        "conditional_reba": {
            "table_c_score": conditional["table_c_score"],
            "activity_score": conditional["activity_score"],
            "max_score": conditional["max_score"],
            "final_conditional_reba": conditional["final_conditional_reba"],
        },

        # Visualization data: per-frame series a client can plot (risk-over-time,
        # trunk angle trace) without re-deriving anything. Kept separate from
        # `reba.summary` so a caller that only wants the scores is not forced
        # to also parse a potentially long per-frame array.
        "visualization": {
            "frame_reba_total": [r["reba_score"] for r in reba["results"]],
            "frame_risk_level": [r["risk_level"] for r in reba["results"]],
            "trunk_sagittal_deg": _safe_list_from_angles(pose["angles"], "trunk_sagittal_deg"),
        },

        "warnings": warnings or [],
        "processing_errors": processing_errors or [],
    }


def _safe_list_from_angles(angles: dict, key: str) -> list:
    if key not in angles or angles[key] is None:
        return []
    return [None if not (v == v) else round(float(v), 2) for v in angles[key]]  # v==v is False only for NaN
