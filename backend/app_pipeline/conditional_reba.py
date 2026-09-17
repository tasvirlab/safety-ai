"""Pipeline step 9: the conditional final REBA score.

App-layer only — REBA_Engine/reba/scorer.py is never modified. Table C's
score is the input; this module adds the Activity Score (the three
activity_layer.py modifiers) on top, per the build instruction:

    activity_score          = int(static_flag) + int(repetition_flag) + int(rapid_change_flag)
    final_conditional_reba  = min(15, table_c_score + activity_score)

Note this is structurally the SAME formula REBA_Engine/reba/scorer.py::
score_frame() already applies internally (`reba_score = table_c_score +
activity`, with `activity` a required int 0-3) — this module does not
duplicate that arithmetic differently, it computes the same 0-3 int
externally (from real detector evidence instead of a caller-supplied
assumption) and could, in principle, be passed straight into score_frame()'s
own `activity` kwarg. It is kept as a separate, explicit step here (rather
than folded into reba_scoring.py) so each modifier's own status
(positive/negative/insufficient/unavailable) stays visible in the output
JSON — score_frame() itself only ever sees the final 0-3 int, never the
per-criterion evidence behind it.

Each modifier keeps its own status in the returned dict — never collapsed
into the numeric score alone, so a reader can always tell whether a 0-point
modifier means "checked, not present" or "could not be checked".
"""
from __future__ import annotations

from .activity_layer import ModifierResult, rapid_motion_modifier, repetition_modifier, static_modifier
from .config import PipelineConfig


def compute_conditional_reba(
    table_c_score: int,
    angles: dict,
    fps: float,
    cfg: PipelineConfig,
    *,
    is_locomotion_task: bool,
    unstable_base: bool,
) -> dict:
    """Run all three Activity Score modifiers and combine them with
    `table_c_score` (the trial's REBA score from reba_scoring.py — see
    run_pipeline.py for which frame/statistic is used as "the" trial score).

    Returns a dict with each modifier's full ModifierResult, the summed
    activity_score, and the capped final_conditional_reba.
    """
    static: ModifierResult = static_modifier(angles, fps, cfg)
    repetition: ModifierResult = repetition_modifier(angles, fps, cfg, is_locomotion_task=is_locomotion_task)
    rapid: ModifierResult = rapid_motion_modifier(angles, fps, cfg, unstable_base=unstable_base)

    activity_score = static["modifier"] + repetition["modifier"] + rapid["modifier"]
    final = min(cfg.conditional_reba_max_score, table_c_score + activity_score)

    return {
        "table_c_score": table_c_score,
        "modifiers": {
            "static": static,
            "repetition": repetition,
            "rapid_motion": rapid,
        },
        "activity_score": activity_score,
        "max_score": cfg.conditional_reba_max_score,
        "final_conditional_reba": final,
    }
