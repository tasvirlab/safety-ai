#!/usr/bin/env python
"""CLI harness: run the full pipeline on one saved video and print/save the
resulting JSON. Requires the WSL `posemamba` conda env (CUDA).

Usage (from backend/ as cwd):
    python scripts/run_on_video.py /path/to/video.mp4 \
        --load 0 --coupling 0 --out result.json
"""
from __future__ import annotations

import argparse
import json
import sys
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))  # backend/ itself, for `app_pipeline`

from app_pipeline.run_pipeline import run_pipeline  # noqa: E402


def _progress(pct: int, msg: str) -> None:
    print(f"[{pct:3d}%] {msg}", file=sys.stderr)


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("video", type=Path)
    ap.add_argument("--load-kg", type=float, default=0.0)
    ap.add_argument("--coupling", type=int, default=0, choices=(0, 1, 2))
    ap.add_argument("--unilateral", action="store_true")
    ap.add_argument("--out", type=Path, default=None)
    args = ap.parse_args()

    result = run_pipeline(
        args.video,
        assessment_id=str(uuid.uuid4()),
        manual_reba={"load_kg": args.load_kg, "coupling": args.coupling, "unilateral": args.unilateral},
        progress_cb=_progress,
    )

    text = json.dumps(result, indent=2, default=str)
    if args.out:
        args.out.write_text(text, encoding="utf-8")
        print(f"Wrote {args.out}", file=sys.stderr)
    else:
        print(text)

    # Plain-language summary on stderr so a human doesn't have to parse JSON
    # to sanity-check a run.
    reba_summary = result["reba"]["summary"]
    cond = result["conditional_reba"]
    act = result["activity"]
    print("\n── Summary ──────────────────────────────────────", file=sys.stderr)
    print(f"REBA (Table C, worst observed frame): {result['conditional_reba']['table_c_score']}", file=sys.stderr)
    print(f"Activity Score: {cond['activity_score']} "
          f"(static={act['static']['status']}, "
          f"repetition={act['repetition']['status']}, "
          f"rapid_motion={act['rapid_motion']['status']})", file=sys.stderr)
    print(f"Final conditional REBA: {cond['final_conditional_reba']}", file=sys.stderr)
    if result["warnings"]:
        print("Warnings:", file=sys.stderr)
        for w in result["warnings"]:
            print(f"  - {w}", file=sys.stderr)


if __name__ == "__main__":
    main()
