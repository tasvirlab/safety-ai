# Vendored RTMPose 2D detection — provenance record

Copied **unchanged** (byte-identical, verified by SHA-256 below) from the
SyncApp research project into this canonical SafetyAI backend, per explicit
instruction: the active backend must have no runtime dependency on the
SyncApp project, while the vendored RTMPose behavior stays byte-for-byte
identical to the original.

**Nothing in this folder (`vendor/rtmpose/`) was modified.** Integration
(sys.path registration, re-export) lives outside this folder, in
`app_pipeline/rtmpose_adapter.py` — see that file.

## Source

- Repository: `https://github.com/TasvirLab/SyncApp.git`
- Local checkout used for this copy: `C:\Users\tempadmin\Research DATA\SyncApp`
- Commit: `389752795d9a429b154613dad599061001d1e40a`
  ("Initial commit: SyncApp source, no research data", 2026-07-28 17:03:38 +0900)
- Working tree state at copy time: clean — `git status --short` reported no
  local modifications to any of the five files below.
- Copy performed: 2026-09-17, via `cp -p` (byte copy, not re-typed/re-read),
  from a running Claude Code session at the user's explicit request.
- License: **no `LICENSE` file exists anywhere in the SyncApp repository
  root** (checked at copy time). SyncApp is an internal, unpublished research
  project (the user's own Master's/PhD thesis codebase) — not released under
  any open-source license. This vendored copy is used the same way the
  active pipeline already used it before vendoring: as an internal,
  read-only research dependency of the same project owner's own downstream
  application, not redistribution to a third party.

## Files copied — every original and destination path

| # | Original path (SyncApp) | Vendored path (this backend) | SHA-256 |
|---|---|---|---|
| 1 | `SyncApp/backend/core/rtmpose_pipeline.py` | `backend/vendor/rtmpose/core/rtmpose_pipeline.py` | `9a557826ee7723b408d0901b7f0930198f7e98a0d37f7711ef7da5998914fcf2` |
| 2 | `SyncApp/backend/core/vicon_io.py` | `backend/vendor/rtmpose/core/vicon_io.py` | `4c85c878592a7509c8572d4cfe9089c9b3bb3b7ff53254ba0f7c36c2279a727d` |
| 3 | `SyncApp/backend/core/__init__.py` | `backend/vendor/rtmpose/core/__init__.py` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` (empty file) |
| 4 | `SyncApp/backend/utils/task_paths.py` | `backend/vendor/rtmpose/utils/task_paths.py` | `d2e4011097ffc885c11dd89c3f03726f4ad5920e23766ab287ec0cead48a3060` |
| 5 | `SyncApp/backend/utils/__init__.py` | `backend/vendor/rtmpose/utils/__init__.py` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` (empty file) |

All five checksums were computed on **both** the original and the copy and
confirmed to match exactly (see "Verification" below); `diff -q` on each pair
also reported no difference.

## Why exactly these five files — the complete dependency closure

`core/rtmpose_pipeline.py` (the module actually called — `run_on_video()`)
has exactly two top-level local imports:

```python
from utils.task_paths import Artifacts
from core.vicon_io import read_frame_ids
```

Both are required for the file to **import at all** (Python evaluates
top-level imports at module load, whether or not `Artifacts`/
`read_frame_ids` are ever used by the functions this backend actually calls
— `run_on_video()`, `select_tracked_person()`, `map_to_21_joints()`).
`utils/task_paths.py` and `core/vicon_io.py` were each read in full and have
**no further local-module imports** of their own (only stdlib + third-party:
`json`, `re`, `subprocess`, `datetime`, `pathlib`, `typing`, `cv2`, `numpy`,
`pandas`) — so the closure terminates at these three files. `core/__init__.py`
and `utils/__init__.py` (both empty) are copied too, because `core`/`utils`
must be importable Python packages for `from core.rtmpose_pipeline import
...` / `from utils.task_paths import ...` to resolve at all — this is exactly
what "copy the minimum complete dependency closure unchanged" means here: the
whole-file import graph, not just the specific functions this backend calls.

**Not vendored, and why not:**
- `rtmlib` (the `Wholebody` pose detector class) — an installed third-party
  pip package (`backend/requirements.txt`), not a local SyncApp file. It is
  not "the SyncApp RTMPose implementation," it is a dependency SyncApp's
  implementation also uses. Already required by this backend independently.
- **Model weight files** — none exist locally anywhere in the SyncApp
  repository (confirmed: no `.onnx` files found in the whole tree). `rtmlib`
  manages its own ONNX model download/cache internally; SyncApp never
  vendors or pins a specific local model file, so there is nothing under
  SyncApp to copy for this.
- `routers/pose2d.py` (SyncApp's HTTP endpoint wrapper around
  `process_trial()`/`process_trials_batch()`) — a **caller** of
  `rtmpose_pipeline.py`, not a dependency of it; irrelevant to this backend,
  which calls `run_on_video()` directly.

## Verification

```
$ sha256sum core/rtmpose_pipeline.py core/vicon_io.py core/__init__.py utils/task_paths.py utils/__init__.py
```
run in both `SyncApp/backend/` and `backend/vendor/rtmpose/` produced
identical hashes for every file (see table above); `diff -q` on each of the
five pairs reported no difference. Re-run both commands any time to
re-verify — see also `verify_checksums.py` in this folder, a small
standalone script with no dependency on the rest of this backend.

## Behavior — unchanged

No line of any copied file was edited: same preprocessing, normalization,
confidence thresholds (`CONF_THRESHOLD_DEFAULT = 0.3`), person-selection
logic (`select_tracked_person()`), resizing/coordinate handling, joint
mapping (`map_to_21_joints()`), and output format (the same
`frame, <JOINT>_X/_Y/_conf/_valid, subject_match_px, subject_found` columns).
Integration (sys.path registration so `from core...`/`from utils...` resolve,
and re-exporting `run_on_video`) is handled entirely in
`app_pipeline/rtmpose_adapter.py`, outside this folder — see that file's own
docstring.

## Outstanding validation step (not yet run — needs a real video)

Per the build instruction: once a real trial video is available, run the
**original** SyncApp `rtmpose_pipeline.run_on_video()` (from
`Research DATA/SyncApp/backend`) and this **vendored** copy (via
`rtmpose_adapter.py`) on the identical frames and confirm identical 2D
keypoint output within numerical tolerance. Both code paths are byte-identical
Python calling the byte-identical `rtmlib.Wholebody` model, so this is
expected to be an exact match (same code, same model, same input); it has not
been executed yet because no appropriate raw trial video has been located on
this machine (see `backend/README.md`'s "Known gaps").
