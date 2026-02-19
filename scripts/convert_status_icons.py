#!/usr/bin/env python3
"""
Convert Status Effect icons from PNG to WebP with ID-based naming.

Input:  raw/Status Effects/General/**/*.png
Output: static/images/icon/battleKeywords/{id}.webp

Process:
1. Load all BattleKeywords*.json to build name -> id mapping
2. For each PNG in General/ (including subdirectories):
   - Look up filename in name mapping to get id
   - If not found, use filename as-is (already an id)
   - Convert PNG to WebP

Usage:
    python convert_status_icons.py              # Dry run
    python convert_status_icons.py --execute   # Actually convert
"""

import argparse
import glob
import json
import os
from pathlib import Path

from PIL import Image

# WebP conversion settings (from pngToWebp.py)
WEBP_QUALITY = 85
WEBP_METHOD = 6
WEBP_LOSSLESS = False

# Paths
BASE_DIR = Path(__file__).parent.parent
SOURCE_DIR = BASE_DIR / "raw" / "Status Effects" / "General"
OUTPUT_DIR = BASE_DIR / "static" / "images" / "icon" / "battleKeywords"
KEYWORDS_DIR = BASE_DIR / "raw" / "LocalizeLimbusCompany" / "EN"
PROCESSED_KEYWORDS = BASE_DIR / "static" / "i18n" / "EN" / "battleKeywords.json"

# Subdirectories to ignore
IGNORED_DIRS = {
    "Sanity",
}

# Files to explicitly ignore (stem name without extension)
IGNORED_FILES = {
    "Unbreakable Coin",
    "Hunger",
    "When rending the body with a great sword..."
}

# Manual mappings for files that don't match keyword names exactly
MANUAL_MAPPING = {
    # Name variations
    "Assist Defense 2": "SupportProtect",
    "Blessing": ["BlessingAlly", "KnightBless"],  # Multiple outputs
    "Borrow Time": "TimeRental",
    "Declared Duel": "DuelDeclaration",
    "Centipede Poison": "CentipedePoison",
    "Courier Trunk": "DevyatDimensionalSack",
    "Deathrite [Venom]": "BurstPoison",
    "Deep Breath": "HoldingBreath",
    "Defense Down": "DefenseDown",
    "Defense Up": "DefenseUp",
    "Faith Beyond Question 2": "Blandishment",
    "Fanatic": "Assemble",
    "Gaze of the Contempt": "GazePersonality",
    "Gaze of the One Who Grips": "MarkOfHeresy",
    "Gluttonous Thorn": "RoseThorn",
    "Life Render": "AStrokeOfDeath",
    "Love Hate": "ThePowerOfLoveAndHate",
    "Nerver Strike - Don Quixote": "DianxueDonQuixote",
    "Prescript 2": "IndexPrescript_Base_2nd",
    "Presence of a Second Kindred": "SwirlingBlood",
    "Reload 2": "ReloadLament",
    "The Seconds Magic Bullet": "FreishutzOutisEgoBullet_2nd",
    "Total Bloodfeast Consumed": "BloodDinner_Accumulation",
    "Unrelenting Spirit - Shin [剛氣-心]": "HugeIrritation",
    "Unrelenting Spirit [剛氣]": "Irritation",
    "LineCutting": "LineCutting",
    "Hunger 3": "VerHunger",
    "The Middle - Grudge 2": "ResentmentIshmael",
    "The Middle Styled Augmentation Tattoos": "ReinforcedTattooIshmael",
    "Thermal Blades": "HeatingWireIshmael",
    "Indulgence in Prescripts 2": "BlackNightmareYisang",
    "Wound-casing Mask": "BurningWoundYisangMask",
    "Sizzling Wound 2": "BurningWoundYisang",
    "Firepower": "GiftCannon",
    "Delicateness": "GiftGlass",
    "Desire for Acknowledgement Sated": "SatisfyingEsteemNeeds",
    "Procuration [Hermes] 2": "StackYisangSpecialSkill",
    "When hacking through the ribs with a hatchet...": "RienWeapon01Hatchet",
    "When penetrating the lungs with a stiletto...": "RienWeapon02Stiletto",
    "When cleaving through the shoulder and the skull with a bastard sword...": "RienWeapon03Greatsword",
    "When punching 10 or more holes in the torso with a rapier...": "RienWeapon04Rapier",
    "When caving in the back of the skull with a hammer...": "RienWeapon05Sledgehammer",
    "When rending the body with a greatsword...":"RienWeapon06Ultragreatsword",
    "When boring a 20-inch hole with a lance...": "RienWeapon07Lance",
    "When ripping the flesh to ten thousand strips with a whip...": "RienWeapon08Chain",
    "When lacerating through space itself with a scythe, like a certain someone...": "RienWeapon09Scythe",
    # Punctuation/whitespace differences (fuzzy matched)
    "Artwork  Fascia": "BoneBladeTheRings",
    "Concentration[Sniper]": "LogicAtelierAM",
    "Entangled Curse Talisman": "EntangledCurseTalisman",
    "Meat Gear Coercion": "MeatGearForce",
    "Offense Level Down": "AttackDown",
    "Tick-Tock!": "TickTockTickTock",
    "What use... am I...": "FailedToAssistQueen",
    # Typo fixes
    "Mulitply Coin Drop": "MultiplyCoinDrop",
    "Mulitply Coin Boost": "MultiplyCoinBoost",
}


def load_json(path: Path) -> dict:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def build_keyword_mappings() -> tuple[dict[str, str], set[str], dict[str, str]]:
    """
    Build mappings from processed battleKeywords.json only.

    Returns:
        (name_to_id, all_ids, id_to_icon):
        - name->id mapping
        - set of all valid IDs (only from processed keywords)
        - id->iconId mapping (for keywords with custom icon)
    """
    name_to_id = {}
    all_ids = set()
    id_to_icon = {}

    if not PROCESSED_KEYWORDS.exists():
        print(f"Warning: {PROCESSED_KEYWORDS} not found")
        return name_to_id, all_ids, id_to_icon

    # Only use processed battleKeywords.json
    processed = load_json(PROCESSED_KEYWORDS)
    for keyword_id, info in processed.items():
        all_ids.add(keyword_id)
        name = info.get("name")
        if name:
            name_to_id[name] = keyword_id
        icon_id = info.get("iconId")
        if icon_id:
            id_to_icon[keyword_id] = icon_id

    return name_to_id, all_ids, id_to_icon


def convert_png_to_webp(source_path: Path, dest_path: Path) -> None:
    """Convert PNG to WebP with configured quality settings."""
    with Image.open(source_path) as img:
        img.save(
            dest_path,
            format="WEBP",
            quality=WEBP_QUALITY,
            method=WEBP_METHOD,
            lossless=WEBP_LOSSLESS
        )


def sanitize_id(name: str) -> str:
    """
    Convert filename to valid ID format.
    Removes spaces, special chars, keeps alphanumeric and underscores.
    """
    import re
    # Replace common patterns
    result = name.replace(" - ", "_").replace(" ", "_")
    # Remove special chars except underscore
    result = re.sub(r"[^\w]", "", result)
    return result


def get_output_ids(
    filename: str,
    name_to_id: dict[str, str],
    all_ids: set[str],
    id_to_icon: dict[str, str]
) -> tuple[list[str], str]:
    """
    Determine output ID(s) for a file.

    Returns: (output_ids, mapping_type)
    output_ids: list of output IDs (usually 1, but can be multiple for manual mappings)
    mapping_type: 'manual', 'keyword', 'is_id', 'sanitized'

    Note: If keyword has iconId, that's used as output filename instead of keyword ID.
    """
    name = Path(filename).stem  # Remove .png extension

    # 1. Check manual mapping first
    if name in MANUAL_MAPPING:
        mapping = MANUAL_MAPPING[name]
        # Support list of outputs
        if isinstance(mapping, list):
            output_ids = [id_to_icon.get(kid, kid) for kid in mapping]
        else:
            output_ids = [id_to_icon.get(mapping, mapping)]
        return output_ids, "manual"

    # 2. Try keyword name mapping
    if name in name_to_id:
        keyword_id = name_to_id[name]
        # Use iconId if available
        return [id_to_icon.get(keyword_id, keyword_id)], "keyword"

    # 3. Check if filename IS already a valid keyword ID
    if name in all_ids:
        # Use iconId if available, otherwise use the ID itself
        return [id_to_icon.get(name, name)], "is_id"

    # 4. Sanitize filename as fallback
    return [sanitize_id(name)], "sanitized"


def main():
    parser = argparse.ArgumentParser(
        description="Convert Status Effect icons to WebP with ID naming"
    )
    parser.add_argument(
        "--execute",
        action="store_true",
        help="Actually convert files (default: dry run)",
    )
    args = parser.parse_args()
    dry_run = not args.execute

    print(f"{'[DRY-RUN] ' if dry_run else ''}Convert Status Effect Icons")
    print(f"Source: {SOURCE_DIR}")
    print(f"Output: {OUTPUT_DIR}")
    print("-" * 60)

    # Build keyword mappings
    name_to_id, all_ids, id_to_icon = build_keyword_mappings()
    print(f"Loaded {len(name_to_id)} keyword names, {len(all_ids)} keyword IDs, {len(id_to_icon)} iconId mappings")
    print("-" * 60)

    # Find all PNG files recursively
    png_files = list(SOURCE_DIR.rglob("*.png"))
    print(f"Found {len(png_files)} PNG files")

    stats = {
        "total": 0, "converted": 0, "skipped": 0, "ignored": 0,
        "manual": 0, "keyword": 0, "is_id": 0, "sanitized": 0
    }

    for png_path in sorted(png_files):
        stats["total"] += 1
        filename = png_path.name
        stem = png_path.stem

        # Skip ignored directories
        if any(part in IGNORED_DIRS for part in png_path.relative_to(SOURCE_DIR).parts[:-1]):
            stats["ignored"] += 1
            continue

        # Skip explicitly ignored files
        if stem in IGNORED_FILES:
            stats["ignored"] += 1
            continue

        output_ids, mapping_type = get_output_ids(filename, name_to_id, all_ids, id_to_icon)
        stats[mapping_type] += 1

        # Skip non-matching files (sanitized = no keyword mapping)
        if mapping_type == "sanitized":
            continue

        rel_source = png_path.relative_to(SOURCE_DIR)
        marker = {"manual": "*", "keyword": "", "is_id": "=", "sanitized": "~"}[mapping_type]

        for output_id in output_ids:
            webp_filename = f"{output_id}.webp"
            dest_path = OUTPUT_DIR / webp_filename

            # Check for existing file
            if dest_path.exists():
                stats["skipped"] += 1
                continue

            if args.execute:
                OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
                convert_png_to_webp(png_path, dest_path)
                print(f"  CONVERT: {rel_source} -> {webp_filename}")
            else:
                print(f"  {rel_source} -> {webp_filename} {marker}")

            stats["converted"] += 1

    print("-" * 60)
    print(f"Total: {stats['total']}")
    print(f"  - Keyword mapped: {stats['keyword']}")
    print(f"  - Manual mapped: {stats['manual']} (*)")
    print(f"  - Already ID: {stats['is_id']} (=)")
    print(f"  - Sanitized: {stats['sanitized']} (~)")
    print(f"Converted: {stats['converted']}")
    print(f"Skipped (exists): {stats['skipped']}")
    print(f"Ignored: {stats['ignored']}")

    if dry_run:
        print("\n[DRY-RUN] Use --execute to actually convert files.")

    return 0


if __name__ == "__main__":
    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    exit(main())
