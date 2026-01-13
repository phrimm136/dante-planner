#!/usr/bin/env python3
"""
Battle Keywords Icon Validation Script

Validates that battleKeywords.json entries have matching icon files
and identifies orphan icons without corresponding keywords.

Usage:
    python scripts/check_keyword_icons.py

Exit codes:
    0 - All icons matched
    1 - Mismatches found
"""

import json
import os
import sys
from pathlib import Path

# --- Config ---
SCRIPT_DIR = Path(__file__).parent
PROJECT_ROOT = SCRIPT_DIR.parent
KEYWORDS_FILE = PROJECT_ROOT / "static" / "i18n" / "EN" / "battleKeywords.json"
ICONS_DIR = PROJECT_ROOT / "static" / "images" / "icon" / "battleKeywords"


def load_keywords():
    """Load battleKeywords.json and extract expected icon names."""
    with open(KEYWORDS_FILE, encoding="utf-8") as f:
        data = json.load(f)

    expected_icons = {}
    for key, entry in data.items():
        icon_id = entry.get("iconId")
        icon_name = icon_id if icon_id else key
        expected_icons[key] = icon_name

    return expected_icons


def get_existing_icons():
    """Get set of icon names (without extension) from icons directory."""
    icons = set()
    if ICONS_DIR.exists():
        for file in ICONS_DIR.iterdir():
            if file.suffix == ".webp":
                icons.add(file.stem)
    return icons


def main():
    print("=" * 60)
    print("Battle Keywords Icon Validation")
    print("=" * 60)

    if not KEYWORDS_FILE.exists():
        print(f"ERROR: Keywords file not found: {KEYWORDS_FILE}")
        return 1

    expected_icons = load_keywords()
    existing_icons = get_existing_icons()

    # Find missing icons
    missing = []
    for key, icon_name in expected_icons.items():
        if icon_name and icon_name not in existing_icons:
            missing.append((key, icon_name))

    # Find orphan icons
    referenced = {icon for icon in expected_icons.values() if icon}
    orphans = existing_icons - referenced

    # Report
    print(f"\nKeywords: {len(expected_icons)}")
    print(f"Icons: {len(existing_icons)}")

    if missing:
        print(f"\n[MISSING] {len(missing)} keywords without icons:")
        for key, icon in sorted(missing):
            print(f"  {key} -> {icon}")

    if orphans:
        print(f"\n[ORPHAN] {len(orphans)} icons without keywords:")
        for icon in sorted(orphans)[:20]:
            print(f"  {icon}")
        if len(orphans) > 20:
            print(f"  ... and {len(orphans) - 20} more")

    print("\n" + "=" * 60)
    if not missing and not orphans:
        print("Result: PASS - All icons matched")
        return 0
    else:
        print(f"Result: FAIL - {len(missing)} missing, {len(orphans)} orphans")
        return 1


if __name__ == "__main__":
    sys.exit(main())
