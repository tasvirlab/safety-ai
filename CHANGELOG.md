# Changelog

## Unreleased

- Android backup and device transfer now exclude all app data.
- Video import validates a decodable video track, duration and dimensions, and removes partial files on failure or cancellation. Cancellation is no longer swallowed.
- Deleting an assessment, or all assessments, also deletes its analysis result. Orphaned results are swept at startup. Video deletion is limited to the managed directory.
- Changing load, sudden force, coupling, task type or the video discards the stored result and requires reanalysis. Descriptive edits keep it. A result is not saved if inputs changed during analysis.
- Assessments left in the analyzing state by a killed process return to ready for analysis at startup.
- Analysis results are replaced atomically and read through a class allow-list. The storage format is unchanged.
- Analysis completion, failure and cancellation no longer re-save a stale snapshot. They use guarded status transitions, so a deleted assessment is not restored and metadata edited during analysis is kept. Edits no longer overwrite the analysis status.
- Added the Barlow OFL license text under `licenses/`.
- Added CI workflow, `scripts/check_setup.py`, third-party notices and a rewritten README.

No scientific pipeline code, parameters or model files were changed.
