# SafetyAI backend — mobile-app inference pipeline

Video → RTMPose 21-joint 2D → PoseMamba 3D → REBA (Tables A/B/C) → Activity
Layer (static/repetition/rapid-motion) → conditional final REBA → structured
JSON, for the Android app at `../app/`.

This folder is part of the canonical `AndroidStudioProjects/SafetyAI` project,
isolated from the `app/` Gradle module. It imports the validated research
pipeline as a **read-only dependency** and never modifies it. It does **not**
use, extend, or depend on `Research DATA/SafetyAI_Demo` (an unrelated,
separate research prototype).

## Canonical dependency: REBA_Engine (not SyncApp)

Angle calculation, REBA Table A/B/C scoring, and the Activity Layer
(static/repetition/rapid-motion) all come from `REBA_Engine/`, not
`SyncApp/backend/core/vicon_reba/`:

- Angles: `REBA_Engine/reba/angles.py::compute_reba_angles()`.
- REBA scoring: `REBA_Engine/reba/scorer.py::score_frame()` +
  `REBA_Engine/pipeline.py::summarise_trial()`.
- Static hold: `REBA_Engine/activity_layer/static.py::detect_static_bouts()`
  — the exact detector that reproduced **F1=0.700** on ConstructionPose
  (`REBA_Engine/experiments/activity_logic_ablation/results/static/
  baseline_summary.json`), using fixed literature/REBA-published constants
  (±5° tolerance, >60s duration) — no threshold is derived or configured by
  this pipeline. See `backend/app_pipeline/activity_layer.py`'s module
  docstring for the full threshold-provenance trace.
- Repetition: `REBA_Engine/activity_layer/repetition_bout_counter.py`'s
  frozen internals, assembled with **H3 cross-channel duplicate-event
  merging** — the ablation-supported variant
  (`REBA_Engine/experiments/activity_logic_ablation/FINDINGS.md`: DEV MAE
  1.429→1.143, TEST MAE 3.167→2.000, both improve on held-out data) that
  `REBA_Engine/activity_layer/activity_score.py`'s own production wiring does
  **not** yet include (it still calls the frozen single-channel
  `select_channel_and_count()` unmodified). This pipeline's own
  `activity_layer.py` reproduces the ablation script's exact assembly logic,
  calling only the frozen building blocks.
- Rapid motion: `REBA_Engine/activity_layer/rapid_change.py::
  detect_rapid_events()`, called with the ablation-recommended
  `min_velocity_deg_s=60.0` (up from that module's own 45.0 default) —
  **EXPLORATORY STATUS**, selected via grid search on a single participant
  (SUB04), not part of a broad stable plateau. See config.yaml's comment and
  `FINDINGS.md` section 3 for the full caveat, preserved in every result's
  `activity.rapid_motion.detail.exploratory_status`.

**Disclosed exception — step 1-3 (2D detection) is a vendored copy of
SyncApp's implementation, not a live dependency.** REBA_Engine has no 2D
pose-extraction module of its own — `REBA_Engine/predict/
run_posemamba_trial.py` takes a pre-existing `pose2d_rtmpose.csv` as *input*,
it does not produce one. There was nothing in REBA_Engine to substitute for
`SyncApp/backend/core/rtmpose_pipeline.py` (video → RTMPose 133-keypoint →
21-canonical-joint 2D mapping), so its complete dependency closure (3 files +
2 empty `__init__.py`) was copied byte-for-byte into `backend/vendor/rtmpose/`
— see that folder's `PROVENANCE.md` for the original paths, SHA-256
checksums, and license/source disclosure. **This backend has no runtime
dependency on the live `Research DATA/SyncApp` project** —
`app_pipeline/rtmpose_adapter.py` is the only file that knows the vendored
copy exists; `pose_pipeline.py` calls it exactly as it used to call SyncApp
directly. Nothing in `vendor/rtmpose/` was modified, and the original SyncApp
files were not touched or deleted. Every angle, REBA score, and Activity
Layer result downstream of the 2D step comes from REBA_Engine only.

## Architecture

The Android app has no on-device inference today, and PoseMamba's
`SelectiveScan` CUDA kernel has no CPU fallback, so this backend runs as a
**local service the app calls** — not on-device. That is a real change to the
app's current "everything stays on this device, no network upload" story
(visible in its own Settings/consent copy), which is worth flagging
explicitly rather than quietly overriding: **the Android-side HTTP client,
the exact wire protocol, and how the app's existing UI copy should change to
reflect it have not been built yet** — this build only covers the pipeline
that will sit behind that call. On-device deployment (no backend call at all)
is exactly what the separate ONNX/TFLite/NNAPI compatibility audit is meant
to determine feasibility for; see "Not started yet" below.

## Layout

```
backend/
  config.yaml              # every path/parameter that might change — see below
  requirements.txt
  app_pipeline/
    config.py              # loads+resolves config.yaml
    rtmpose_adapter.py      # sys.path + re-export for vendor/rtmpose/ — the ONLY file that knows it exists
    pose_pipeline.py        # steps 1-6: video -> 2D (vendored RTMPose) -> 21-joint -> 3D -> REBA_Engine angles
    reba_scoring.py         # step 7: REBA_Engine Table A/B/C (Table C only — activity=0)
    activity_layer.py       # step 8: static / repetition (H3) / rapid-motion, via REBA_Engine
    conditional_reba.py     # step 9: Activity Score + capped final REBA
    output_schema.py        # step 10: the JSON handed back
    run_pipeline.py         # orchestrates all of the above
  vendor/
    rtmpose/                # byte-identical copy of SyncApp's RTMPose 2D detector — see PROVENANCE.md
      PROVENANCE.md          # original paths, SHA-256 checksums, license/source disclosure
      verify_checksums.py    # standalone re-verification, no backend dependency
      core/, utils/          # the vendored files themselves — never import from here directly, use rtmpose_adapter.py
      normalize_2d.py        # verbatim EXTRACT of dataset_export.py's normalize_2d() (not a whole-file copy — see PROVENANCE.md)
  tests/
    test_rtmpose_mapping.py # deterministic, video-free synthetic test of the vendored 133->21 mapping + normalize_2d()
  scripts/
    run_on_video.py         # CLI test harness
```

## Swapping the checkpoint

Everything checkpoint-specific lives in `config.yaml`'s `checkpoint:` block.
Right now it points at `exp05_5fold_cv` fold1 (test subjects SUB01/SUB02) —
a cross-validation fold, **not** the final deployment model, used as a
temporary stand-in while the real deployment checkpoint trains (started
2026-09-17 16:38, `PoseMamba/experiments/deployment_training/`). Once that
run finishes, change `checkpoint.path` to its `best_epoch.bin` — nothing else
in this backend needs to change.

## Manual REBA inputs

`config.yaml`'s `manual_reba_defaults` block holds every manual REBA input
the Android app does not yet collect (load/force, coupling, shoulder-raised,
supported-or-leaning, the four structurally-unsupported modifiers,
locomotion/unstable-base). These are documented ASSUMPTION defaults (the
conservative "not present" value), not observed facts — `run_pipeline()`
accepts a `manual_reba` dict to override individual keys once the app UI for
them exists. `REBA_Engine/reba/scorer.py::score_frame()` itself has no
default for any of these by its own explicit design.

## Known gaps (report honestly, do not silently work around)

- **No test video has been run yet.** `01_RAW/` has no `.mp4` directly under
  it on this machine; the only videos found under `Research DATA` are
  calibration/overlay clips (`03_CALIBRATION/Overlay/...`), which have a
  Vicon skeleton drawn on top and are not appropriate raw pipeline input.
  `scripts/run_on_video.py` is ready to run — point it at a real per-trial RGB
  video once one is available. Every non-video-dependent stage (config
  resolution, activity_layer's REBA_Engine wiring, REBA_Engine's own
  scorer/static/repetition/rapid detectors, the vendored RTMPose module's own
  `_demo()` self-check) has been smoke-tested and passes.
- **Vendored-vs-original RTMPose cross-check not yet run — needs a real
  video.** Once one is available, run SyncApp's own
  `core/rtmpose_pipeline.run_on_video()` (from `Research DATA/SyncApp/backend`)
  and this backend's vendored copy (`app_pipeline/rtmpose_adapter.run_on_video`)
  on the identical frames and confirm identical 2D keypoint output within
  numerical tolerance — see `vendor/rtmpose/PROVENANCE.md`'s own "Outstanding
  validation step" note.
- **Not started yet: the ONNX/TFLite/NNAPI/`selective_scan` compatibility
  audit.** This is a separate, self-contained investigation (does PoseMamba's
  SelectiveScan export through ONNX, and if not, exactly which node/op fails
  and where) — see the original build request for what it needs to cover.
  Nothing in this backend depends on it; it determines whether "backend the
  app calls" can ever become "runs on-device."
- **Android-side HTTP client / wire protocol** — not designed or built (see
  "Architecture" above).

## Run

Requires the WSL `posemamba` conda env (same one PoseMamba's own training
scripts use — its SelectiveScan kernel has no CPU fallback) with this
backend's extra packages installed (`pip install -r requirements.txt`).

```bash
conda activate posemamba
cd AndroidStudioProjects/SafetyAI/backend
python scripts/run_on_video.py /path/to/trial_rgb.mp4 --load-kg 0 --coupling 0 --out result.json
```

`app_pipeline/config.py` and `app_pipeline/activity_layer.py` each have a
standalone self-check (`python -m app_pipeline.config`,
`python -m app_pipeline.activity_layer`) that need no GPU/video — the first
confirms config.yaml resolves correctly, the second exercises REBA_Engine's
real, frozen static/rapid-motion detectors directly (not a synthetic
reimplementation) against synthetic angle data. Both pass under a plain
CPU-only Python 3.11 install (numpy/scipy/pyyaml only — no torch needed for
either).
