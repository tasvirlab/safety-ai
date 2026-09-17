"""Pipeline step 8: the Activity Layer — the three REBA Activity Score
criteria (static hold, repeated action, rapid/unstable motion).

Every detector here is REBA_Engine's own frozen, ablation-tested module —
never modified, never copied — imported directly from
config.research_data_root/REBA_Engine:

  - static:   REBA_Engine/activity_layer/static.py::detect_static_bouts()
              UNCHANGED, called with its own default constants (+/-5deg
              tolerance, >60s duration — see that module's own docstring for
              full provenance). This is the exact detector that reproduced
              F1=0.700 on ConstructionPose
              (REBA_Engine/experiments/activity_logic_ablation/results/
              static/baseline_summary.json) — no threshold is configured or
              derived by this pipeline.
  - repetition: REBA_Engine/activity_layer/repetition_bout_counter.py's
              FROZEN internals (_noise_floor/detect_bouts/_estimate_thresholds/
              _count_cycles_in_bout), assembled here with H3 cross-channel
              duplicate-event merging — the ablation-supported variant
              (FINDINGS.md: DEV MAE 1.429->1.143, TEST MAE 3.167->2.000, both
              improve) that REBA_Engine/activity_layer/activity_score.py's own
              production wiring does NOT yet include (it still calls the
              frozen single-channel repetition_bout_counter.select_channel_
              and_count() unmodified). The assembly logic below reproduces
              REBA_Engine/experiments/activity_logic_ablation/scripts/
              02_repetition_variants.py's cross_channel_merge=True path
              exactly (merge_tolerance_s=0.3, every other hypothesis at its
              baseline/off value) — new app-layer code, per that ablation
              script's own docstring: "only the assembly logic around
              [the frozen building blocks] is new code."
  - rapid:    REBA_Engine/activity_layer/rapid_change.py::detect_rapid_events()
              UNCHANGED, called with min_velocity_deg_s=60.0 (config.yaml
              override of its own default 45.0) and min_excursion_deg=20.0
              (already its own default) — the ablation-recommended config
              (recommended_configs.json), EXPLORATORY STATUS per FINDINGS.md
              (selected via grid search on SUB04, the same single participant
              every prior A1/A2/B decision was made on — see config.yaml's
              own comment for the full caveat, preserved in `detail` below).

Every one of the three modifiers below reports a `status` of exactly one of:
    "positive"     — criterion met
    "negative"     — criterion evaluated and NOT met (real evidence either way)
    "insufficient" — evaluated, but the input data does not clear the
                     evidence bar (e.g. too much occlusion, or a trial too
                     short for static's own >60s gate)
    "unavailable"  — the required input does not exist at all (e.g. a
                     channel entirely missing from `angles`)

"insufficient"/"unavailable" NEVER get silently written as "negative" — the
status field always distinguishes "checked, not present" from "could not
check". Only for the ARITHMETIC activity_score in conditional_reba.py, a
non-"positive" status contributes 0 — nothing is scored as a modifier without
positive evidence for it — but the status string next to that 0 always says
which of the three reasons applies. This exactly mirrors REBA_Engine's own
`fully_evaluated`/`static_flag`(etc.) split in static.py/rapid_change.py/
activity_score.py — this module does not invent a new convention, it adapts
REBA_Engine's own to this pipeline's existing ModifierResult shape.
"""
from __future__ import annotations

import sys
from typing import TypedDict

import numpy as np

from .config import PipelineConfig


class ModifierResult(TypedDict):
    status: str          # "positive" | "negative" | "insufficient" | "unavailable"
    modifier: int         # 0 or 1 — the value conditional_reba.py sums; 0 unless status == "positive"
    reason: str | None
    detail: dict


def _register_research_paths(cfg: PipelineConfig) -> None:
    p = cfg.research_data_root / "REBA_Engine"
    if str(p) not in sys.path:
        sys.path.insert(0, str(p))


# ── Criterion 1: static hold ─────────────────────────────────────────────────

def static_modifier(
    angles: dict, fps: float, cfg: PipelineConfig, *, channels: tuple[str, ...] | None = None,
) -> ModifierResult:
    """One or more of REBA_Engine's whole-body channels held statically for
    >60s within +/-5deg (REBA Activity criterion 1) — a direct, unmodified
    call to the validated F1=0.700 detector. See module docstring.

    `channels`: None (default, production) uses REBA_Engine's own default
    scope (every channel reba/angles.py can compute). Overridable only for
    testing against a synthetic angles dict that doesn't carry every channel.
    """
    _register_research_paths(cfg)
    from activity_layer import static as st  # REBA_Engine

    kwargs = {"channels": channels} if channels is not None else {}
    result = st.detect_static_bouts(angles, fps, **kwargs)

    if not result["static_evaluable"]:
        return ModifierResult(
            status="insufficient", modifier=0,
            reason=f"static: {result['static_reason']} (needs a continuous observation >60s; "
                   f"see per-channel detail for the longest window actually found)",
            detail=result,
        )
    return ModifierResult(
        status="positive" if result["static_flag"] else "negative",
        modifier=int(result["static_flag"]),
        reason=None,
        detail=result,
    )


# ── Criterion 2: repeated small-range action (H3 cross-channel merge) ───────

def _channel_evidence(bc, kin, x_raw: np.ndarray, fps: float, coverage: float, min_coverage_fraction: float) -> dict:
    """Reproduces repetition_bout_counter.py::_channel_evidence() /
    02_repetition_variants.py::_channel_evidence_variant() with every
    hypothesis except H3 held at baseline (min_cycles_for_evidence=2,
    no separation gate, no amplitude cap) — calling ONLY the frozen
    building blocks, never reimplementing them."""
    if coverage < min_coverage_fraction:
        return {"status": "insufficient_coverage"}
    filtered = kin.causal_filter(x_raw, fps)
    noise_floor = bc._noise_floor(filtered)
    bouts = bc.detect_bouts(filtered, fps, noise_floor)
    if not bouts:
        return {"status": "insufficient_evidence", "reason": "no bout of sustained activity detected", "bouts": []}

    bout_results, all_cycles, all_amps = [], [], []
    for start, end in bouts:
        seg = filtered[start:end]
        thresholds = bc._estimate_thresholds(seg, noise_floor)
        if thresholds is None:
            bout_results.append({"start": start, "end": end, "status": "insufficient_evidence"})
            continue
        l_thresh, h_thresh = thresholds
        r = bc._count_cycles_in_bout(seg, l_thresh, h_thresh)
        for cy in r["cycles"]:
            for k in ("cycle_start", "opposite_threshold_time", "cycle_end", "confirmation_time"):
                cy[k] += start
        bout_results.append({"start": start, "end": end, "status": "evaluated", **r, "cycles": r["cycles"]})
        all_cycles.extend(r["cycles"])
        all_amps.append(h_thresh - l_thresh)

    if len(all_cycles) < bc.DEFAULT_MIN_CYCLES_FOR_EVIDENCE:
        return {"status": "insufficient_evidence",
               "reason": f"only {len(all_cycles)} cycle(s) across {len(bouts)} bout(s) "
                        f"(need >={bc.DEFAULT_MIN_CYCLES_FOR_EVIDENCE})", "bouts": bout_results}

    ends = np.array([c["cycle_end"] for c in all_cycles])
    intervals = np.diff(np.sort(ends)) / fps
    intervals = intervals[intervals > 0]
    regularity = 1.0 / (1.0 + float(np.std(intervals) / np.mean(intervals))) if len(intervals) and np.mean(intervals) > 0 else 0.5
    amplitude_snr = float(np.mean(all_amps)) / max(noise_floor, 0.1) if all_amps else 0.0
    score = amplitude_snr * regularity

    return {"status": "evaluated", "bouts": bout_results, "cycles": all_cycles, "count": len(all_cycles),
           "score": score, "amplitude_snr": amplitude_snr, "regularity": regularity, "noise_floor_deg": noise_floor}


def _select_with_cross_channel_merge(bc, kin, per_channel_x: dict, per_channel_coverage: dict, fps: float,
                                     min_coverage_fraction: float, merge_tolerance_s: float) -> dict:
    """REBA_Engine/experiments/activity_logic_ablation/scripts/
    02_repetition_variants.py::select_channel_and_count_variant()'s
    cross_channel_merge=True path, H3 only (H1/H2/H4 off)."""
    evidence = {}
    for ch, x in per_channel_x.items():
        if ch not in bc.CANDIDATE_CHANNELS:
            continue
        evidence[ch] = _channel_evidence(bc, kin, x, fps, per_channel_coverage.get(ch, 0.0), min_coverage_fraction)

    scored = {ch: e["score"] for ch, e in evidence.items() if e.get("status") == "evaluated"}
    if not scored:
        return {"status": "insufficient_evidence", "count": None, "evidence": evidence}

    ranked = sorted(scored, key=scored.get, reverse=True)
    winner = ranked[0]
    if len(ranked) < 2:
        sel = evidence[winner]
        return {"status": "evaluated", "selected_channel": winner, "count": sel["count"],
               "cycle_times_s": [cy["cycle_end"] / fps for cy in sel["cycles"]], "merged_channels": [winner]}

    runner_up = ranked[1]
    times_a = sorted(cy["cycle_end"] / fps for cy in evidence[winner]["cycles"])
    times_b = sorted(cy["cycle_end"] / fps for cy in evidence[runner_up]["cycles"])
    merged = list(times_a)
    for tb in times_b:
        if not any(abs(tb - ta) <= merge_tolerance_s for ta in times_a):
            merged.append(tb)
    merged.sort()
    return {"status": "evaluated", "selected_channel": f"{winner}+{runner_up}", "count": len(merged),
           "cycle_times_s": merged, "merged_channels": [winner, runner_up]}


def repetition_modifier(angles: dict, fps: float, cfg: PipelineConfig, *, is_locomotion_task: bool) -> ModifierResult:
    """Repeated small-range action, >4x/minute (REBA Activity criterion 2),
    via H3 cross-channel merge (see module docstring). Rate formula/threshold
    and locomotion-exclusion policy are REBA_Engine's own frozen rule,
    reproduced from activity_layer/activity_score.py::
    _detect_repetition_via_bout_counter() unchanged: `rate = count /
    observed_duration_s * 60; flag = (not is_locomotion_task) and
    rate > REPETITION_RATE_THRESH_PER_MIN`."""
    _register_research_paths(cfg)
    from activity_layer import constants as ac  # REBA_Engine
    from activity_layer import kinematics as kin  # REBA_Engine
    from activity_layer import repetition_bout_counter as bc  # REBA_Engine

    per_channel_x = {ch: np.asarray(angles[ch], dtype=float) for ch in bc.CANDIDATE_CHANNELS if ch in angles}
    per_channel_coverage = {ch: float(np.isfinite(x).mean()) if len(x) else 0.0 for ch, x in per_channel_x.items()}

    result = _select_with_cross_channel_merge(
        bc, kin, per_channel_x, per_channel_coverage, fps,
        min_coverage_fraction=ac.REPETITION_MIN_COVERAGE_FRACTION,
        merge_tolerance_s=cfg.activity_layer.repetition.merge_tolerance_s,
    )

    if result["status"] != "evaluated":
        return ModifierResult(
            status="insufficient", modifier=0,
            reason="repetition: no candidate channel met the minimum bout/cycle evidence criteria",
            detail=result,
        )

    ch = result["merged_channels"][0]
    x = per_channel_x[ch]
    valid_frames = int(np.isfinite(x).sum())
    observed_duration_s = valid_frames / fps if fps > 0 else 0.0
    count = result["count"]
    rate = (count / observed_duration_s * 60.0) if observed_duration_s > 0 else 0.0
    flag = (not is_locomotion_task) and (rate > ac.REPETITION_RATE_THRESH_PER_MIN)

    detail = {**result, "observed_duration_s": round(observed_duration_s, 3),
             "rate_per_min": round(rate, 3), "rate_thresh_per_min": ac.REPETITION_RATE_THRESH_PER_MIN,
             "is_locomotion_task": is_locomotion_task, "merge_tolerance_s": cfg.activity_layer.repetition.merge_tolerance_s}

    if is_locomotion_task:
        return ModifierResult(status="negative", modifier=0,
                              reason="repetition: is_locomotion_task=True — REBA_Engine's own whole-trial exclusion policy",
                              detail=detail)
    return ModifierResult(
        status="positive" if flag else "negative",
        modifier=int(flag),
        reason=None,
        detail=detail,
    )


# ── Criterion 3: rapid / large-range / unstable-base motion ─────────────────

def rapid_motion_modifier(
    angles: dict, fps: float, cfg: PipelineConfig, *, unstable_base: bool,
    channels: tuple[str, ...] | None = None,
) -> ModifierResult:
    """Rapid large-range posture change or unstable base (REBA Activity
    criterion 3) — REBA_Engine/activity_layer/rapid_change.py::
    detect_rapid_events(), unmodified, called with the ablation-recommended
    velocity threshold (60 deg/s, up from that module's own 45 deg/s
    default). EXPLORATORY STATUS — see module docstring and config.yaml.

    `channels`: None (default, production) uses REBA_Engine's own default
    scope (RAPID_EVENT_CHANNELS). Overridable only for testing.
    """
    _register_research_paths(cfg)
    from activity_layer import rapid_change as rc  # REBA_Engine

    rm = cfg.activity_layer.rapid_motion
    kwargs = {"channels": channels} if channels is not None else {}
    result = rc.detect_rapid_events(
        angles, fps, unstable_base=unstable_base,
        min_velocity_deg_s=rm.velocity_threshold_deg_s,
        min_excursion_deg=rm.excursion_threshold_deg,
        **kwargs,
    )

    if not result["rapid_change_evaluable"]:
        return ModifierResult(
            status="unavailable", modifier=0,
            reason=f"rapid_motion: {result['rapid_change_reason']}",
            detail=result,
        )
    return ModifierResult(
        status="positive" if result["rapid_change_flag"] else "negative",
        modifier=int(result["rapid_change_flag"]),
        reason=None,
        detail={**result, "exploratory_status": (
            "velocity_thresh=60deg/s was selected via a grid search on SUB04 -- the same "
            "single participant every prior rapid-detector decision (A1/A2/B) was already "
            "made on; not part of a broad stable plateau (excursion=10 at the same velocity "
            "collapses accuracy). Re-check once more participants are annotated -- see "
            "REBA_Engine/experiments/activity_logic_ablation/FINDINGS.md section 3."
        )},
    )


# ── Self-check (no video/model needed — exercises REBA_Engine's own frozen
#    detectors directly on synthetic data, same convention as REBA_Engine's
#    own module self-checks) ──────────────────────────────────────────────────
def _demo() -> None:
    from .config import load_config
    cfg = load_config()
    fps = 60.0

    # ── static: a 90s held-still trunk must be positive. ─────────────────────
    rng = np.random.default_rng(1)
    n90 = int(90 * fps)
    held = {"trunk_sagittal_deg": 30.0 + rng.uniform(-1.0, 1.0, size=n90)}
    r_static = static_modifier(held, fps, cfg, channels=("trunk_sagittal_deg",))
    assert r_static["status"] == "positive" and r_static["modifier"] == 1, r_static

    # ── static: a 19s clip cannot clear the >60s gate -> insufficient, never
    #    a false "negative". ─────────────────────────────────────────────────
    n19 = int(19 * fps)
    short = {"trunk_sagittal_deg": np.full(n19, 20.0)}
    r_static_short = static_modifier(short, fps, cfg, channels=("trunk_sagittal_deg",))
    assert r_static_short["status"] == "insufficient", r_static_short

    # ── rapid: a genuine fast, large trunk-sagittal swing must be positive. ──
    t = np.arange(0, 3, 1 / fps)
    seq = np.concatenate([np.zeros(int(1 * fps)), np.linspace(0.0, 40.0, int(0.3 * fps), endpoint=False),
                          np.full(len(t) - int(1.3 * fps), 40.0)])[:len(t)]
    angles_rapid = {"trunk_sagittal_deg": seq}
    r_rapid = rapid_motion_modifier(angles_rapid, fps, cfg, unstable_base=False, channels=("trunk_sagittal_deg",))
    assert r_rapid["status"] == "positive" and r_rapid["modifier"] == 1, r_rapid

    # ── rapid: unstable_base=True alone must force positive even with no motion. ──
    flat = {"trunk_sagittal_deg": np.full(len(t), 10.0)}
    r_unstable = rapid_motion_modifier(flat, fps, cfg, unstable_base=True, channels=("trunk_sagittal_deg",))
    assert r_unstable["status"] == "positive" and r_unstable["modifier"] == 1, r_unstable

    print("activity_layer self-check OK")


if __name__ == "__main__":
    _demo()
