# Third-party notices

This project is proprietary (see `README.md`). It uses the components below under their own licenses. Licenses were checked against the upstream repositories or published package metadata on 2026-11-25. Each upstream license text and copyright notice applies to the corresponding component.

| Component | Use | License | Source checked |
|---|---|---|---|
| RTMPose / RTMW (MMPose, OpenMMLab) | 2D pose model weights; `backend/vendor/rtmpose` helpers are the owner's own SyncApp code | Apache-2.0 | github.com/open-mmlab/mmpose |
| YOLOX (Megvii) and MMDetection weights | Person detection model | Apache-2.0 | github.com/Megvii-BaseDetection/YOLOX |
| PoseMamba (AAAI 2025) | 3D pose lifting architecture and checkpoint lineage | Apache-2.0 | github.com/nankingjing/PoseMamba |
| Mamba selective scan (state-spaces) | Reference for the native operator in `selective_scan_ndk/` | Apache-2.0 | github.com/state-spaces/mamba |
| ONNX Runtime | Inference runtime and C++ headers | MIT | Maven POM, onnxruntime-android 1.22.0 |
| Barlow and Barlow Condensed (Jeremy Tribby) | UI typography in `app/src/main/res/font` | SIL Open Font License 1.1 (full text in `licenses/Barlow-OFL.txt`) | github.com/jpt/barlow |
| AndroidX, Jetpack Compose, CameraX, Media3, Room, Kotlin, kotlinx | App libraries | Apache-2.0 | Each library's published POM |

REBA (Hignett and McAtamney, 2000) is a published assessment method. This project implements it and does not claim ownership of it.

Prerequisites before any distribution of a build, which is not planned for this research prototype:

- Whether PoseMamba's `nankingjing` repository is the exact upstream of the weights and code used here, and whether the Mamba-derived native operator needs a source-level Apache-2.0 notice. Apache-2.0 requires keeping the license text and notices for redistributed derived code.
- The licenses of the training datasets behind each weight file. Model code licenses do not cover dataset terms.

Model weights are not included in this repository. Check the redistribution terms of each weight file and of the training datasets behind them before distributing an APK that bundles them.

The vendored RTMPose helper code is copied from the owner's SyncApp project; provenance is in `backend/vendor/rtmpose/PROVENANCE.md`.
