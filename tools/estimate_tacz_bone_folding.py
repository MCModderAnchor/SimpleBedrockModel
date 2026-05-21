#!/usr/bin/env python3
"""Estimate TACZ gun model bone counts before and after v2-style static folding.

The estimate keeps bones that are directly animated, explicitly preserved by mount/name
rules, or required as ancestors of those bones. Geometry under non-kept bones is assumed
to fold into the nearest kept ancestor, matching the SimpleBedrockModel v2 bake strategy.
"""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass
from pathlib import Path
from statistics import mean
from typing import Any

DEFAULT_TACZ_ROOT = Path(
    r"D:\Minecraft\Dev\MCModderAnchor\TimelessAndClassicsZeroF\src\main\resources\assets\tacz\custom\tacz_default_gun\assets\tacz"
)
DEFAULT_GEO_DIR = DEFAULT_TACZ_ROOT / "geo_models" / "gun"
DEFAULT_ANIM_DIR = DEFAULT_TACZ_ROOT / "animations"

DEFAULT_PRESERVE_PATTERNS = (
    r"^camera$",
    r"camera",
    r"_pos$",
    r"_view$",
    r"view$",
    r"pos$",
    r"locator",
    r"scope",
    r"sight",
    r"muzzle",
    r"shell",
    r"eject",
    r"mag",
    r"bullet",
    r"left(hand)?$",
    r"right(hand)?$",
    r"hand$",
)


@dataclass(frozen=True)
class ModelStats:
    gun: str
    geo_file: Path
    animation_file: Path | None
    original_bones: int
    current_bones: int
    ideal_bones: int
    animated_bones: int
    preserved_bones: int
    geometry_bones: int

    @property
    def current_removed_bones(self) -> int:
        return self.original_bones - self.current_bones

    @property
    def ideal_removed_bones(self) -> int:
        return self.original_bones - self.ideal_bones

    @property
    def extra_removed_bones(self) -> int:
        return self.current_bones - self.ideal_bones

    @property
    def current_reduction_ratio(self) -> float:
        if self.original_bones == 0:
            return 0.0
        return self.current_removed_bones / self.original_bones

    @property
    def ideal_reduction_ratio(self) -> float:
        if self.original_bones == 0:
            return 0.0
        return self.ideal_removed_bones / self.original_bones


def load_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def geometry_entries(root: Any) -> list[dict[str, Any]]:
    if not isinstance(root, dict):
        return []
    modern = root.get("minecraft:geometry")
    if isinstance(modern, list):
        return [entry for entry in modern if isinstance(entry, dict)]
    legacy = []
    for key, value in root.items():
        if key.startswith("geometry.") and isinstance(value, dict):
            legacy.append(value)
    return legacy


def collect_model_bones(geo_root: Any) -> tuple[dict[str, str | None], set[str]]:
    parent_by_bone: dict[str, str | None] = {}
    geometry_bones: set[str] = set()
    for geometry in geometry_entries(geo_root):
        bones = geometry.get("bones", [])
        if not isinstance(bones, list):
            continue
        for bone in bones:
            if not isinstance(bone, dict):
                continue
            name = bone.get("name")
            if not isinstance(name, str) or not name:
                continue
            parent = bone.get("parent")
            parent_by_bone[name] = parent if isinstance(parent, str) and parent else None
            if bone.get("cubes") or bone.get("poly_mesh"):
                geometry_bones.add(name)
    return parent_by_bone, geometry_bones


def collect_animation_bones(animation_root: Any) -> set[str]:
    result: set[str] = set()
    if not isinstance(animation_root, dict):
        return result
    animations = animation_root.get("animations", {})
    if not isinstance(animations, dict):
        return result
    for animation in animations.values():
        if not isinstance(animation, dict):
            continue
        bones = animation.get("bones", {})
        if not isinstance(bones, dict):
            continue
        for bone_name in bones.keys():
            if isinstance(bone_name, str) and bone_name:
                result.add(bone_name)
    return result


def compile_patterns(patterns: list[str]) -> list[re.Pattern[str]]:
    return [re.compile(pattern, re.IGNORECASE) for pattern in patterns]


def collect_preserved_bones(parent_by_bone: dict[str, str | None], patterns: list[re.Pattern[str]]) -> set[str]:
    result: set[str] = set()
    for bone in parent_by_bone:
        if any(pattern.search(bone) for pattern in patterns):
            result.add(bone)
    return result


def include_ancestors(seeds: set[str], parent_by_bone: dict[str, str | None]) -> set[str]:
    result: set[str] = set()
    for seed in seeds:
        bone = seed
        while bone and bone in parent_by_bone and bone not in result:
            result.add(bone)
            bone = parent_by_bone.get(bone)
    return result


def gun_name_from_geo(path: Path) -> str:
    name = path.stem
    return name[:-4] if name.endswith("_geo") else name


def animation_path_for(gun_name: str, anim_dir: Path) -> Path | None:
    path = anim_dir / f"{gun_name}.animation.json"
    return path if path.exists() else None


def estimate_model(geo_file: Path, anim_dir: Path, preserve_patterns: list[re.Pattern[str]]) -> ModelStats:
    gun = gun_name_from_geo(geo_file)
    geo_root = load_json(geo_file)
    parent_by_bone, geometry_bones = collect_model_bones(geo_root)
    animation_file = animation_path_for(gun, anim_dir)
    animated_bones: set[str] = set()
    if animation_file is not None:
        animated_bones = collect_animation_bones(load_json(animation_file))
    animated_in_model = animated_bones.intersection(parent_by_bone)
    preserved_bones = collect_preserved_bones(parent_by_bone, preserve_patterns)
    seed_bones = animated_in_model | preserved_bones
    current_kept_bones = include_ancestors(seed_bones, parent_by_bone)
    ideal_kept_bones = seed_bones
    return ModelStats(
        gun=gun,
        geo_file=geo_file,
        animation_file=animation_file,
        original_bones=len(parent_by_bone),
        current_bones=len(current_kept_bones),
        ideal_bones=len(ideal_kept_bones),
        animated_bones=len(animated_in_model),
        preserved_bones=len(preserved_bones),
        geometry_bones=len(geometry_bones),
    )


def format_percent(value: float) -> str:
    return f"{value * 100.0:.1f}%"


def print_table(rows: list[ModelStats], limit: int | None) -> None:
    shown = rows if limit is None else rows[:limit]
    header = f"{'gun':<22} {'before':>6} {'cur':>6} {'ideal':>6} {'extra':>6} {'cur%':>8} {'ideal%':>8} {'anim':>6} {'keep':>6} {'geom':>6}"
    print(header)
    print("-" * len(header))
    for row in shown:
        print(
            f"{row.gun:<22} {row.original_bones:>6} {row.current_bones:>6} {row.ideal_bones:>6} {row.extra_removed_bones:>6} "
            f"{format_percent(row.current_reduction_ratio):>8} {format_percent(row.ideal_reduction_ratio):>8} "
            f"{row.animated_bones:>6} {row.preserved_bones:>6} {row.geometry_bones:>6}"
        )


def print_summary(rows: list[ModelStats]) -> None:
    if not rows:
        print("No models found.")
        return
    before = [row.original_bones for row in rows]
    current = [row.current_bones for row in rows]
    ideal = [row.ideal_bones for row in rows]
    current_removed = [row.current_removed_bones for row in rows]
    ideal_removed = [row.ideal_removed_bones for row in rows]
    extra_removed = [row.extra_removed_bones for row in rows]
    print()
    print("Summary")
    print("-------")
    print(f"models:                    {len(rows)}")
    print(f"total bones before:        {sum(before)}")
    print(f"current-v2 bones after:    {sum(current)}")
    print(f"ideal-fold bones after:    {sum(ideal)}")
    print(f"current-v2 removed:        {sum(current_removed)}")
    print(f"ideal-fold removed:        {sum(ideal_removed)}")
    print(f"extra removable ancestors: {sum(extra_removed)}")
    print(f"current-v2 reduction:      {format_percent(sum(current_removed) / sum(before) if sum(before) else 0.0)}")
    print(f"ideal-fold reduction:      {format_percent(sum(ideal_removed) / sum(before) if sum(before) else 0.0)}")
    print(f"extra reduction gain:      {format_percent(sum(extra_removed) / sum(before) if sum(before) else 0.0)}")
    print(f"average before/model:      {mean(before):.2f}")
    print(f"average current/model:     {mean(current):.2f}")
    print(f"average ideal/model:       {mean(ideal):.2f}")
    print(f"average extra/model:       {mean(extra_removed):.2f}")
    print(f"min before/current/ideal:  {min(before)} / {min(current)} / {min(ideal)}")
    print(f"max before/current/ideal:  {max(before)} / {max(current)} / {max(ideal)}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Estimate v2 static bone folding for TACZ gun models.")
    parser.add_argument("--geo-dir", type=Path, default=DEFAULT_GEO_DIR)
    parser.add_argument("--anim-dir", type=Path, default=DEFAULT_ANIM_DIR)
    parser.add_argument("--preserve", action="append", default=[], help="Additional regex for locator/mount bones to preserve.")
    parser.add_argument("--no-default-preserve", action="store_true", help="Disable built-in camera/view/pos/mount regexes.")
    parser.add_argument("--sort", choices=("gun", "before", "current", "ideal", "extra", "current-reduction", "ideal-reduction"), default="extra")
    parser.add_argument("--limit", type=int, default=None, help="Only print the first N rows after sorting.")
    parser.add_argument("--csv", type=Path, default=None, help="Optional CSV output path.")
    args = parser.parse_args()

    patterns = [] if args.no_default_preserve else list(DEFAULT_PRESERVE_PATTERNS)
    patterns.extend(args.preserve)
    preserve_patterns = compile_patterns(patterns)

    geo_files = sorted(path for path in args.geo_dir.glob("*_geo.json") if path.is_file())
    rows = [estimate_model(path, args.anim_dir, preserve_patterns) for path in geo_files]

    key_funcs = {
        "gun": lambda row: row.gun,
        "before": lambda row: row.original_bones,
        "current": lambda row: row.current_bones,
        "ideal": lambda row: row.ideal_bones,
        "extra": lambda row: row.extra_removed_bones,
        "current-reduction": lambda row: row.current_reduction_ratio,
        "ideal-reduction": lambda row: row.ideal_reduction_ratio,
    }
    reverse = args.sort != "gun"
    rows.sort(key=key_funcs[args.sort], reverse=reverse)

    print_table(rows, args.limit)
    print_summary(rows)

    if args.csv is not None:
        with args.csv.open("w", encoding="utf-8", newline="") as handle:
            handle.write("gun,original_bones,current_bones,ideal_bones,current_removed,ideal_removed,extra_removed,current_reduction,ideal_reduction,animated_bones,preserved_bones,geometry_bones,geo_file,animation_file\n")
            for row in rows:
                handle.write(
                    f"{row.gun},{row.original_bones},{row.current_bones},{row.ideal_bones},{row.current_removed_bones},{row.ideal_removed_bones},"
                    f"{row.extra_removed_bones},{row.current_reduction_ratio:.6f},{row.ideal_reduction_ratio:.6f},"
                    f"{row.animated_bones},{row.preserved_bones},{row.geometry_bones},{row.geo_file},{row.animation_file or ''}\n"
                )
        print(f"\nCSV written to: {args.csv}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
