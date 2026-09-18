"""Standalone SHA-256 verification: confirms every file in this vendor/
folder still matches the checksum recorded in PROVENANCE.md at copy time.

No dependency on the rest of this backend (stdlib only) — run any time to
re-verify the vendored copy has not drifted from the original:

    python vendor/rtmpose/verify_checksums.py
"""
from __future__ import annotations

import hashlib
from pathlib import Path

_HERE = Path(__file__).resolve().parent

# {relative path: sha256 hex digest}, from PROVENANCE.md's own table —
# recorded once at copy time, both source and destination confirmed to match.
EXPECTED: dict[str, str] = {
    "core/rtmpose_pipeline.py": "9a557826ee7723b408d0901b7f0930198f7e98a0d37f7711ef7da5998914fcf2",
    "core/vicon_io.py": "4c85c878592a7509c8572d4cfe9089c9b3bb3b7ff53254ba0f7c36c2279a727d",
    "core/__init__.py": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "utils/task_paths.py": "d2e4011097ffc885c11dd89c3f03726f4ad5920e23766ab287ec0cead48a3060",
    "utils/__init__.py": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    # NOT a whole-file copy of dataset_export.py — a verbatim EXTRACT of just
    # normalize_2d() + its required constants (see that file's own docstring
    # and PROVENANCE.md). This hash detects drift of the EXTRACT itself
    # (my own header/footer + the copied code together), not equivalence to
    # the full source file — the copied blocks were separately diff-verified
    # against dataset_export.py line-for-line at extraction time.
    "normalize_2d.py": "d46b38a7b4a3d3ba3f6e5a48e292965387ea1388aac89cead83ae2e4e8ef98cc",
}


def sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    h.update(path.read_bytes())
    return h.hexdigest()


def main() -> int:
    failures = []
    for rel, expected in EXPECTED.items():
        p = _HERE / rel
        if not p.is_file():
            failures.append(f"MISSING: {rel}")
            continue
        actual = sha256_of(p)
        if actual != expected:
            failures.append(f"MISMATCH: {rel}\n  expected {expected}\n  actual   {actual}")
        else:
            print(f"OK  {rel}  {actual}")

    if failures:
        print("\nCHECKSUM VERIFICATION FAILED:")
        for f in failures:
            print(f"  {f}")
        return 1

    print(f"\nAll {len(EXPECTED)} vendored files match their recorded SHA-256 — unchanged since copy.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
