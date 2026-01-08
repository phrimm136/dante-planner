#!/usr/bin/env python3
"""
Theme Pack Data Generator

Generates:
1. Individual themePack/{id}.json files
2. themePackList.json (combined index)
3. i18n/{lang}/themePack.json for EN/JP/KR
4. Updates egoGift files with themePack field

Usage:
    python3 scripts/themePack.py
"""

import json
import glob
import os
from pathlib import Path
from lang_config import LANGS as ALL_LANGS, get_raw_pattern, get_lang_dir, lang_dir_exists

# =============================================================================
# Configuration
# =============================================================================

BASE_DIR = Path(__file__).parent.parent
RAW_DIR = BASE_DIR / "raw" / "Json"
STATIC_DIR = BASE_DIR / "static"

# Input patterns
THEME_FLOOR_PATTERN = str(RAW_DIR / "mirrordungeon-theme-floor*.json")

# Output paths
PACK_DIR = STATIC_DIR / "data" / "themePack"
PACK_LIST_PATH = STATIC_DIR / "data" / "themePackList.json"
EGO_GIFT_DIR = STATIC_DIR / "data" / "egoGift"

# Fields to extract for individual files
INDIVIDUAL_FIELDS = [
    "exceptionConditions",
    "nodeOption",
    "egoGiftPool",
    "specificEgoGiftPool",
    "difficulty",
    "themePackConfig",
]


# =============================================================================
# Theme Pack Data Generation
# =============================================================================

def generate_theme_packs():
    """Generate individual themePack files and themePackList.json"""
    PACK_DIR.mkdir(parents=True, exist_ok=True)

    theme_pack_list = {}
    theme_packs = {}  # For egoGift linking

    for path in glob.glob(THEME_FLOOR_PATTERN):
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)

        for entry in data.get("list", []):
            entry_id = entry.get("id")
            if entry_id is None:
                continue

            out = {field: None for field in INDIVIDUAL_FIELDS}

            # exceptionConditions
            if "exceptionConditions" in entry:
                out["exceptionConditions"] = entry["exceptionConditions"]

            # mapGenOption -> nodeOption (remove specialEventProb)
            if "mapGenOption" in entry:
                map_gen = dict(entry["mapGenOption"])
                map_gen.pop("specialEventProb", None)
                out["nodeOption"] = map_gen

            # egoGiftPool
            if "egoGiftPool" in entry:
                out["egoGiftPool"] = entry["egoGiftPool"]

            # specificEgoGiftPool
            if "specificEgoGiftPool" in entry:
                out["specificEgoGiftPool"] = entry["specificEgoGiftPool"]

            # difficulty
            if "difficulty" in entry:
                out["difficulty"] = entry["difficulty"]

            # uiConfigs -> themePackConfig (only textColor)
            theme_pack_config = None
            if "uiConfigs" in entry:
                ui = entry["uiConfigs"]
                theme_pack_config = {
                    "textColor": ui.get("textColor", "e5c6a0")
                }
            out["themePackConfig"] = theme_pack_config

            # Write individual file
            pack_path = PACK_DIR / f"{entry_id}.json"
            with open(pack_path, "w", encoding="utf-8") as f:
                json.dump(out, f, ensure_ascii=False, indent=2)

            # Collect for list
            theme_pack_list[str(entry_id)] = {
                "exceptionConditions": out["exceptionConditions"],
                "specificEgoGiftPool": out["specificEgoGiftPool"],
                "themePackConfig": theme_pack_config,
            }

            # Store for egoGift linking
            theme_packs[str(entry_id)] = out["specificEgoGiftPool"] or []

    # Write themePackList.json
    with open(PACK_LIST_PATH, "w", encoding="utf-8") as f:
        json.dump(theme_pack_list, f, ensure_ascii=False, indent=2)

    print(f"[themePack] Generated {len(theme_pack_list)} individual files")
    print(f"[themePack] Generated {PACK_LIST_PATH}")

    return theme_packs


# =============================================================================
# i18n Generation
# =============================================================================

def generate_i18n():
    """Generate i18n/themePack.json for each language"""
    for lang in ALL_LANGS:
        if not lang_dir_exists(lang):
            continue
        input_pattern = get_raw_pattern(lang, "MirrorDungeonTheme*.json")
        output_file = STATIC_DIR / "i18n" / lang / "themePack.json"

        output_file.parent.mkdir(parents=True, exist_ok=True)

        theme_dict = {}

        for filepath in glob.glob(input_pattern):
            with open(filepath, "r", encoding="utf-8") as f:
                data = json.load(f)

            for item in data.get("dataList", []):
                theme_id = item.get("id")
                obj = {}

                if item.get("name") is not None:
                    obj["name"] = item["name"]
                if item.get("specialName") is not None:
                    obj["specialName"] = item["specialName"]

                theme_dict[str(theme_id)] = obj

        with open(output_file, "w", encoding="utf-8") as f:
            json.dump(theme_dict, f, ensure_ascii=False, indent=2)

        print(f"[i18n] Generated {output_file}")


# =============================================================================
# EGO Gift Linking
# =============================================================================

def link_ego_gifts(theme_packs: dict):
    """Append themePack field to egoGift files"""
    if not EGO_GIFT_DIR.exists():
        print(f"[egoGift] Directory not found: {EGO_GIFT_DIR}")
        return

    # Build reverse mapping: gift_id -> [pack_ids]
    gift_to_packs = {}
    for pack_id, gift_ids in theme_packs.items():
        for gift_id in gift_ids:
            gift_id_str = str(gift_id)
            if gift_id_str not in gift_to_packs:
                gift_to_packs[gift_id_str] = []
            gift_to_packs[gift_id_str].append(pack_id)

    updated_count = 0

    for gift_id, pack_ids in gift_to_packs.items():
        gift_path = EGO_GIFT_DIR / f"{gift_id}.json"

        if not gift_path.exists():
            print(f"[egoGift] WARN: Not found {gift_path}")
            continue

        with open(gift_path, "r", encoding="utf-8") as f:
            gift_data = json.load(f)

        # Initialize themePack field if not exists
        if "themePack" not in gift_data:
            gift_data["themePack"] = []

        # Append pack IDs (avoid duplicates)
        for pack_id in pack_ids:
            if pack_id not in gift_data["themePack"]:
                gift_data["themePack"].append(pack_id)

        with open(gift_path, "w", encoding="utf-8") as f:
            json.dump(gift_data, f, ensure_ascii=False, indent=2)

        updated_count += 1

    print(f"[egoGift] Updated {updated_count} files with themePack links")


# =============================================================================
# Main
# =============================================================================

def main():
    print("=" * 50)
    print("Theme Pack Data Generator")
    print("=" * 50)

    # 1. Generate theme pack data
    theme_packs = generate_theme_packs()

    # 2. Generate i18n files
    generate_i18n()

    # 3. Link egoGifts to theme packs
    link_ego_gifts(theme_packs)

    print("=" * 50)
    print("Done!")


if __name__ == "__main__":
    main()
