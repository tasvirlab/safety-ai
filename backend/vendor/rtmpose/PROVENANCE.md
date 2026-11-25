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
- Local checkout used for this copy: the owner's private SyncApp working copy
- Commit: `389752795d9a429b154613dad599061001d1e40a`
  ("Initial commit: SyncApp source, no research data", 2026-07-28 17:03:38 +0900)
- Working tree state at copy time: clean — `git status --short` reported no
  local modifications to any of the five files below.
- Copy performed: 2026-09-17, via `cp -p` (byte copy, not re-typed/re-read),
  at the user's explicit request.
- License: **no `LICENSE` file exists anywhere in the SyncApp repository
  root** (checked at copy time). SyncApp is an internal, unpublished research
  project (the user's own Master's/PhD thesis codebase) — not released under
  any open-source license. This vendored copy is used the same way the
  active pipeline already used it before vendoring: as an internal,
  read-only research dependency of the same project owner's own downstream
  application, not redistribution to a third party.

## Files copied — every original and destination path

| # | Original path (SyncApp) | Vendored path (this backend) | Kind | SHA-256 |
|---|---|---|---|---|
| 1 | `SyncApp/backend/core/rtmpose_pipeline.py` | `backend/vendor/rtmpose/core/rtmpose_pipeline.py` | whole-file copy | `9a557826ee7723b408d0901b7f0930198f7e98a0d37f7711ef7da5998914fcf2` |
| 2 | `SyncApp/backend/core/vicon_io.py` | `backend/vendor/rtmpose/core/vicon_io.py` | whole-file copy | `4c85c878592a7509c8572d4cfe9089c9b3bb3b7ff53254ba0f7c36c2279a727d` |
| 3 | `SyncApp/backend/core/__init__.py` | `backend/vendor/rtmpose/core/__init__.py` | whole-file copy (empty) | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| 4 | `SyncApp/backend/utils/task_paths.py` | `backend/vendor/rtmpose/utils/task_paths.py` | whole-file copy | `d2e4011097ffc885c11dd89c3f03726f4ad5920e23766ab287ec0cead48a3060` |
| 5 | `SyncApp/backend/utils/__init__.py` | `backend/vendor/rtmpose/utils/__init__.py` | whole-file copy (empty) | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| 6 | `SyncApp/backend/core/dataset_export.py`, lines 46-60 and 268-274 | `backend/vendor/rtmpose/normalize_2d.py` | verbatim function extract | `d46b38a7b4a3d3ba3f6e5a48e292965387ea1388aac89cead83ae2e4e8ef98cc` (of the whole extract file, my own header/footer included — see below) |

Files 1-5 checksums were computed on **both** the original and the copy and
confirmed to match exactly; `diff -q` on each pair also reported no
difference. Re-verified again (unchanged) when file 6 was added — see
`verify_checksums.py`'s own run log at the bottom of this document.

### File 6 — `normalize_2d.py` is a function EXTRACT, not a whole-file copy

By explicit instruction, `core/dataset_export.py` itself is **not** vendored,
imported, or executed anywhere in this backend — it is dataset-BUILDING code
(batch processing, train/test/val splitting, multi-trial spine-ratio
fitting), not single-video deployment code, and copying it wholesale would
pull in responsibilities this backend has no use for and no path to safely
invoke (e.g. `make_windows()` assumes a whole discovered-trials dataset).
Only `normalize_2d()` — the literal 2D-normalization step PoseMamba's
temporal input prep needs — plus the two constants it directly references
(`JOINT_NAMES`, `ROOT_IDX`, via `ROOT_JOINT`) were extracted **character for
character**, at `backend/vendor/rtmpose/normalize_2d.py`.

Reference hash of the exact source file this was extracted from (for
provenance — NOT a copy target, this file is not vendored):
`SyncApp/backend/core/dataset_export.py` = `85279cdc7dff7e59aec182aaf83a2db78ac3f4df1e3da01db3b70ec52bd52841`.

**Verification performed at extraction time** (not just at write time — see
"normalize_2d.py verification" below): the two source line ranges (46-60,
268-274) were extracted fresh via `sed -n` directly into temp files, the same
two ranges were extracted back out of the written vendor file by line number,
and `diff` between each pair reported **zero difference** — confirmed
character-for-character, not just visually similar.

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
$ sha256sum core/rtmpose_pipeline.py core/vicon_io.py core/__init__.py utils/task_paths.py utils/__init__.py normalize_2d.py
```
run in both `SyncApp/backend/` (files 1-5 only — `normalize_2d.py` has no
whole-file source equivalent, see above) and `backend/vendor/rtmpose/`
produced identical hashes for every file (see table above); `diff -q` on each
of the five whole-file pairs reported no difference. Re-run any time via
`verify_checksums.py` in this folder — a standalone script with no dependency
on the rest of this backend.

**Latest verification run** (after adding `normalize_2d.py`, re-checking
files 1-5 were untouched):

```
OK  core/rtmpose_pipeline.py  9a557826ee7723b408d0901b7f0930198f7e98a0d37f7711ef7da5998914fcf2
OK  core/vicon_io.py          4c85c878592a7509c8572d4cfe9089c9b3bb3b7ff53254ba0f7c36c2279a727d
OK  core/__init__.py          e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
OK  utils/task_paths.py       d2e4011097ffc885c11dd89c3f03726f4ad5920e23766ab287ec0cead48a3060
OK  utils/__init__.py         e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
OK  normalize_2d.py           d46b38a7b4a3d3ba3f6e5a48e292965387ea1388aac89cead83ae2e4e8ef98cc
All 6 vendored files match their recorded SHA-256 — unchanged since copy.
```

### normalize_2d.py verification (function-level, since it's an extract not a whole-file copy)

```
$ sed -n '46,60p'  SyncApp/backend/core/dataset_export.py > src_block1.txt
$ sed -n '268,274p' SyncApp/backend/core/dataset_export.py > src_block2.txt
$ sed -n '26,40p'  backend/vendor/rtmpose/normalize_2d.py > dst_block1.txt   # JOINT_NAMES/ROOT_IDX
$ sed -n '49,55p'  backend/vendor/rtmpose/normalize_2d.py > dst_block2.txt   # normalize_2d()
$ diff src_block1.txt dst_block1.txt   ->  IDENTICAL (no output)
$ diff src_block2.txt dst_block2.txt   ->  IDENTICAL (no output)
```

## Behavior — unchanged

No line of any copied file, or of the copied normalize_2d() code block
itself, was edited: same preprocessing, normalization, confidence thresholds
(`CONF_THRESHOLD_DEFAULT = 0.3`), person-selection logic
(`select_tracked_person()`), resizing/coordinate handling, joint mapping
(`map_to_21_joints()`), PoseMamba input normalization (`normalize_2d()` —
root-center by PELCEN, divide by half video width), and output format (the
same `frame, <JOINT>_X/_Y/_conf/_valid, subject_match_px, subject_found`
columns). Integration (sys.path registration so `from core...`/`from
utils...`/`import normalize_2d` resolve, and re-exporting the functions a
caller needs) is handled entirely in `app_pipeline/rtmpose_adapter.py`,
outside this folder — see that file's own docstring. A deterministic,
video-free synthetic test suite at `backend/tests/test_rtmpose_mapping.py`
exercises the vendored 133-to-21 mapping and normalize_2d() through that
adapter and confirms: exactly 21 output joints; the vendored module's own
joint order AND (separately, since they differ) the actual PoseMamba
training joint order; every direct mapping; every constructed proxy joint
(HD/C7/PELCEN/T10/L45); no left/right swaps; raw-confidence propagation below
threshold; expected tensor shapes through to the `(1, window, 21, 2)` PoseMamba
input shape; normalize_2d()'s exact formula and validity mask against a
hand-computed reference; and zero active runtime dependency on
`Research DATA/SyncApp` (checked via both `sys.modules[...].__file__` and
`sys.path`). All 9 checks pass — see that file's own module docstring for the
full list and how to re-run it.

## Joint-order reconciliation (vendored order vs. PoseMamba training order)

The vendored module's own internal order (`core/rtmpose_pipeline.JOINT_NAMES`
— used by `DIRECT_JOINTS`/`map_to_21_joints()`) has **PELCEN at index 12**.
The order PoseMamba was actually trained on (`config.yaml`'s
`canonical_joints`, identical to `SyncApp/backend/core/dataset_export.py`'s
own `JOINT_NAMES` and to `REBA_Engine/reba/joint_mapping.JOINT_NAMES`) has
**PELCEN at index 3**. These are genuinely different orders of the same 21
joint names — full table and reconciliation in `app_pipeline/pose_pipeline.py`:

- The explicit reorder already existed as `_reorder_to_canonical()` (name-based
  column lookup, correct regardless of the vendored file's internal order).
  It is unchanged in mechanism, but now: (a) takes the vendored order as an
  explicit parameter rather than an implicit assumption, (b) computes the same
  permutation a second way as a literal integer index array
  (`_reorder_index()`), and (c) asserts the two agree on every call — this
  lives entirely in `pose_pipeline.py`, not in any vendored file.
- A hard assertion, `_assert_training_joint_order()`, compares
  `config.yaml`'s `canonical_joints` against `_POSEMAMBA_TRAINING_JOINT_ORDER`
  — a literal tuple hardcoded from `dataset_export.py`'s own source, kept
  independent of `config.yaml` on purpose — and raises immediately if they
  ever diverge. Runs once per `run_pose_pipeline()` call, before any 2D
  detection happens.
- `REBA_Engine/reba/joint_mapping.JOINT_NAMES` is checked against
  `canonical_joints` too (a live dependency, so checked dynamically rather
  than hardcoded).
- `backend/tests/test_rtmpose_mapping.py::test_full_active_pipeline_order()`
  traces unique per-joint sentinel coordinates through the complete active
  path — `map_to_21_joints()` → a `run_on_video()`-shaped DataFrame → the
  real `_reorder_to_canonical()` → the real vendored `normalize_2d()` → the
  exact `(1, window, 21, 2)` tensor shape handed to PoseMamba — and confirms
  every final tensor index holds the anatomically correct joint.
  `test_hard_assertion_catches_drift()` proves the assertion actually raises
  on a deliberately wrong order, not just passes on the correct one. Both
  pass — see that file for the full 11-check list.

## Outstanding validation step (not yet run — needs a real video)

Per the build instruction: once a real trial video is available, run the
**original** SyncApp `rtmpose_pipeline.run_on_video()` + `dataset_export.
normalize_2d()` (from `Research DATA/SyncApp/backend`) and this **vendored**
copy (via `rtmpose_adapter.py`) on the identical frames and confirm identical
2D keypoint output AND identical normalized PoseMamba input within numerical
tolerance. Both code paths are byte-identical/character-identical Python
calling the byte-identical `rtmlib.Wholebody` model, so this is expected to
be an exact match (same code, same model, same input); it has not been
executed yet because no appropriate raw trial video has been located on this
machine (see `backend/README.md`'s "Known gaps"). The deterministic synthetic
test (`backend/tests/test_rtmpose_mapping.py`) already covers the
video-independent logic (mapping formulas, joint order, normalization
formula) exactly — what remains is only an end-to-end check against
`rtmlib`'s real model output on real footage.
