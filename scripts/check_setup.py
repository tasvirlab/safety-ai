"""Checks a fresh checkout for everything the build needs. Read-only; run with: python scripts/check_setup.py"""
import hashlib, os, re, shutil, subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODELS = {
    "app/src/main/assets/models/rtmpose/yolox_m_8xb8-300e_humanart-c2c7a14a.onnx":
        "3dea6513388889f0fff4b77bf7a26013600321b9eb9ceb0e9a400a82572f5f23",
    "app/src/main/assets/models/rtmpose/rtmw-dw-x-l_simcc-cocktail14_270e-256x192_20231122.onnx":
        "9a9bc17c13ff0a1e37507f45a037a21bc410cd08374b0e664c57d0080843aa58",
    "posemamba_deployment/android/posemamba-scan/src/main/assets/model/posemamba.onnx": None,
}
problems = 0

def report(ok, label, hint=""):
    global problems
    print(("ok      " if ok else "MISSING ") + label + ("" if ok else f"  ({hint})"))
    problems += not ok

def gradle_props():
    props = {}
    for f in (Path.home() / ".gradle" / "gradle.properties", ROOT / "local.properties"):
        if f.is_file():
            for line in f.read_text(errors="ignore").splitlines():
                m = re.match(r"\s*([\w.]+)\s*=\s*(.*)", line)
                if m: props[m[1]] = re.sub(r"\\(.)", lambda x: x.group(1), m[2].strip())
    return props

java = os.path.join(os.environ["JAVA_HOME"], "bin", "java") if os.environ.get("JAVA_HOME") else shutil.which("java")
ver = ""
if java:
    ver = subprocess.run([java, "-version"], capture_output=True, text=True).stderr
m = re.search(r'version "(\d+)', ver)
report(bool(m) and int(m[1]) >= 17, "JDK 17 or newer", "Android Studio's bundled JBR works; set JAVA_HOME")

props = gradle_props()
sdk = props.get("sdk.dir") or os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
report(bool(sdk) and Path(sdk).is_dir(), "Android SDK", "set sdk.dir in local.properties")
inc = props.get("SCAN_ORT_INCLUDE")
report(bool(inc) and (Path(inc) / "onnxruntime_c_api.h").is_file(), "SCAN_ORT_INCLUDE (ONNX Runtime headers)",
       "headers are in the onnxruntime-android AAR under headers/")
ndk = props.get("SCAN_NDK_VERSION")
report(bool(ndk) and bool(sdk) and (Path(sdk) / "ndk" / ndk).is_dir(), "SCAN_NDK_VERSION installed", "install via SDK Manager")

for rel, sha in MODELS.items():
    p = ROOT / rel
    if not p.is_file():
        report(False, rel, "model not in repository; app builds but analysis is unavailable")
    elif sha and hashlib.sha256(p.read_bytes()).hexdigest() != sha:
        report(False, rel, "SHA-256 mismatch")
    else:
        report(True, rel)

print("\nAll checks passed." if not problems else f"\n{problems} item(s) need attention. Model files are only needed to run analysis, not to build or run unit tests.")
sys.exit(1 if problems else 0)
