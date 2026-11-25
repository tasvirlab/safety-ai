# Third-party notices

This project is proprietary (see `README.md`). It uses the components below under their own licenses. License names are stated from general knowledge of these projects and must be confirmed against each upstream repository before any publication or redistribution. Items marked "verify" were not confirmed.

| Component | Use | License (confirm upstream) |
|---|---|---|
| RTMPose / RTMW (MMPose, OpenMMLab) | 2D pose model weights, `backend/vendor/rtmpose` helpers | Apache-2.0 |
| YOLOX (Megvii; MMDetection weights) | Person detection model | Apache-2.0 |
| PoseMamba | 3D pose lifting architecture, vendored `normalize_2d()` | verify upstream license |
| Mamba selective scan | Reference for the native operator in `selective_scan_ndk/` | Apache-2.0 (verify) |
| ONNX Runtime | Inference runtime and C++ headers | MIT |
| Barlow font family (Jeremy Tribby) | UI typography in `app/src/main/res/font` | SIL Open Font License 1.1 |
| AndroidX, Jetpack Compose, CameraX, Media3, Room | App libraries | Apache-2.0 |
| Kotlin and kotlinx libraries | App libraries | Apache-2.0 |

Model weights are not included in this repository. Check the redistribution terms of each weight file and of the training datasets behind them before distributing an APK that bundles them.

The vendored RTMPose helper code originates from the owner's SyncApp project, with provenance recorded in `backend/vendor/rtmpose/PROVENANCE.md`.
