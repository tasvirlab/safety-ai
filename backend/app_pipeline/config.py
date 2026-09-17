"""Loads config.yaml and resolves every research-data path against
research_data_root. Nothing else in app_pipeline/ should read config.yaml
directly or hardcode a path under Research DATA — go through this module so
the checkpoint swap described in config.yaml stays a one-line, one-file change.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

import yaml

_CONFIG_PATH = Path(__file__).resolve().parents[1] / "config.yaml"


@dataclass(frozen=True)
class CheckpointConfig:
    path: Path
    label: str
    state_dict_key: str
    num_frame: int
    embed_dim_ratio: float
    depth: int
    mlp_ratio: float


@dataclass(frozen=True)
class RapidMotionConfig:
    velocity_threshold_deg_s: float
    excursion_threshold_deg: float


@dataclass(frozen=True)
class RepetitionConfig:
    merge_tolerance_s: float


@dataclass(frozen=True)
class ActivityLayerConfig:
    rapid_motion: RapidMotionConfig
    repetition: RepetitionConfig


@dataclass(frozen=True)
class ManualRebaDefaults:
    load_kg: float
    load_shock: bool
    coupling: int
    unilateral: bool
    shoulder_raised_r: bool
    shoulder_raised_l: bool
    supported_or_leaning_r: bool
    supported_or_leaning_l: bool
    neck_twist_modifier: bool | None
    upper_arm_rotated_r: bool | None
    upper_arm_rotated_l: bool | None
    wrist_deviation_r: bool | None
    wrist_deviation_l: bool | None
    is_locomotion_task: bool
    unstable_base: bool


@dataclass(frozen=True)
class PipelineConfig:
    research_data_root: Path
    checkpoint: CheckpointConfig
    device: str
    canonical_joints: tuple[str, ...]
    root_joint: str
    activity_layer: ActivityLayerConfig
    manual_reba_defaults: ManualRebaDefaults
    conditional_reba_max_score: int
    raw: dict = field(repr=False)  # the untouched parsed YAML, for anything not modeled above

    @property
    def root_idx(self) -> int:
        return self.canonical_joints.index(self.root_joint)


def load_config(path: Path | str | None = None) -> PipelineConfig:
    """Parse config.yaml and resolve every relative path against research_data_root."""
    cfg_path = Path(path) if path is not None else _CONFIG_PATH
    doc = yaml.safe_load(cfg_path.read_text(encoding="utf-8"))

    root = Path(doc["research_data_root"])
    if not root.exists():
        raise FileNotFoundError(
            f"research_data_root {root} does not exist — fix config.yaml "
            f"({cfg_path}) before running the pipeline."
        )

    def _under_root(p: str) -> Path:
        return root / p

    ck = doc["checkpoint"]
    checkpoint = CheckpointConfig(
        path=_under_root(ck["path"]),
        label=ck["label"],
        state_dict_key=ck["state_dict_key"],
        num_frame=int(ck["num_frame"]),
        embed_dim_ratio=float(ck["embed_dim_ratio"]),
        depth=int(ck["depth"]),
        mlp_ratio=float(ck["mlp_ratio"]),
    )

    al = doc["activity_layer"]
    rm = al["rapid_motion"]
    rep = al["repetition"]
    activity_layer = ActivityLayerConfig(
        rapid_motion=RapidMotionConfig(
            velocity_threshold_deg_s=float(rm["velocity_threshold_deg_s"]),
            excursion_threshold_deg=float(rm["excursion_threshold_deg"]),
        ),
        repetition=RepetitionConfig(
            merge_tolerance_s=float(rep["merge_tolerance_s"]),
        ),
    )

    md = doc["manual_reba_defaults"]
    manual_reba_defaults = ManualRebaDefaults(
        load_kg=float(md["load_kg"]), load_shock=bool(md["load_shock"]),
        coupling=int(md["coupling"]), unilateral=bool(md["unilateral"]),
        shoulder_raised_r=bool(md["shoulder_raised_r"]), shoulder_raised_l=bool(md["shoulder_raised_l"]),
        supported_or_leaning_r=bool(md["supported_or_leaning_r"]), supported_or_leaning_l=bool(md["supported_or_leaning_l"]),
        neck_twist_modifier=md["neck_twist_modifier"],
        upper_arm_rotated_r=md["upper_arm_rotated_r"], upper_arm_rotated_l=md["upper_arm_rotated_l"],
        wrist_deviation_r=md["wrist_deviation_r"], wrist_deviation_l=md["wrist_deviation_l"],
        is_locomotion_task=bool(md["is_locomotion_task"]), unstable_base=bool(md["unstable_base"]),
    )

    return PipelineConfig(
        research_data_root=root,
        checkpoint=checkpoint,
        device=doc["device"],
        canonical_joints=tuple(doc["canonical_joints"]),
        root_joint=doc["root_joint"],
        activity_layer=activity_layer,
        manual_reba_defaults=manual_reba_defaults,
        conditional_reba_max_score=int(doc["conditional_reba"]["max_score"]),
        raw=doc,
    )


# ── Self-check ───────────────────────────────────────────────────────────────
def _demo() -> None:
    cfg = load_config()
    assert cfg.root_idx == cfg.canonical_joints.index("PELCEN") == 3, cfg.root_idx
    assert cfg.checkpoint.path.suffix == ".bin", cfg.checkpoint.path
    assert cfg.activity_layer.rapid_motion.velocity_threshold_deg_s == 60.0
    assert cfg.activity_layer.rapid_motion.excursion_threshold_deg == 20.0
    assert cfg.activity_layer.repetition.merge_tolerance_s == 0.3
    assert cfg.manual_reba_defaults.neck_twist_modifier is None
    reba_engine = cfg.research_data_root / "REBA_Engine"
    assert reba_engine.is_dir(), f"REBA_Engine not found at {reba_engine}"
    print(f"config OK - checkpoint: {cfg.checkpoint.label}")
    print(f"  path exists: {cfg.checkpoint.path.exists()} ({cfg.checkpoint.path})")
    print(f"  REBA_Engine root: {reba_engine}")


if __name__ == "__main__":
    _demo()
