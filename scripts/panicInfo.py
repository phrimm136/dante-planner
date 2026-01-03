#!/usr/bin/env python3
"""
Parse PanicInfo JSON files from raw data and output merged i18n files.

Input:  raw/Json/{LANG}/{LANG}_PanicInfo*.json
Output: static/i18n/{LANG}/panicInfo.json

Output format (id as key for O(1) lookup):
{
  "1001": {
    "name": "Compulsion",
    "lowMoraleDesc": "Turn Start: Gain 2 Haste...",
    "panicDesc": "Turn Start: Lose 10 HP."
  }
}
"""

import json
import glob
import os
import re

# --- Configuration ---
LANGS = ["KR", "EN", "JP"]
BASE_INPUT = "../raw/Json"
BASE_OUTPUT = "../static/i18n"

# Regex to strip Unity rich text tags (optional cleanup)
RICH_TEXT_PATTERN = re.compile(
    r'<sprite[^>]*>|<color[^>]*>|</color>|<u>|</u>|<link[^>]*>|</link>'
)


def strip_rich_text(text: str) -> str:
    """Remove Unity rich text markup for clean display."""
    return RICH_TEXT_PATTERN.sub('', text).strip()


def load_panic_files(lang: str) -> dict:
    """
    Load all PanicInfo files for a language and merge by id.
    Later files override earlier ones (variants have latest data).
    """
    panic_map = {}

    # Glob all PanicInfo files for this language
    pattern = f"{BASE_INPUT}/{lang}/{lang}_PanicInfo*.json"
    files = sorted(glob.glob(pattern))

    if not files:
        print(f"  Warning: No files found for pattern: {pattern}")
        return panic_map

    for filepath in files:
        filename = os.path.basename(filepath)
        try:
            with open(filepath, encoding="utf-8") as f:
                data = json.load(f)

            entries = data.get("dataList", [])
            for entry in entries:
                panic_id = str(entry.get("id", ""))
                if not panic_id:
                    continue

                # TODO(human): Implement the transformation logic here
                # Transform raw entry to output format
                panic_map[panic_id] = {
                    "name": entry.get("panicName", ""),
                    "lowMoraleDesc": entry.get("lowMoraleDescription", ""),
                    "panicDesc": entry.get("panicDescription", ""),
                }

            print(f"  Loaded {len(entries)} entries from {filename}")

        except json.JSONDecodeError as e:
            print(f"  Error parsing {filename}: {e}")
        except Exception as e:
            print(f"  Error reading {filename}: {e}")

    return panic_map


def save_output(lang: str, panic_map: dict) -> None:
    """Save merged panic data to i18n output directory."""
    output_dir = f"{BASE_OUTPUT}/{lang}"
    output_path = f"{output_dir}/panicInfo.json"

    os.makedirs(output_dir, exist_ok=True)

    # Sort by numeric id for consistent output
    sorted_map = dict(sorted(panic_map.items(), key=lambda x: int(x[0])))

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(sorted_map, f, ensure_ascii=False, indent=2)

    print(f"  Saved {len(sorted_map)} entries to {output_path}")


def main():
    print("=== PanicInfo Parser ===\n")

    for lang in LANGS:
        print(f"Processing {lang}...")
        panic_map = load_panic_files(lang)

        if panic_map:
            save_output(lang, panic_map)
        else:
            print(f"  Skipped {lang} - no data")

        print()

    print("Done!")


if __name__ == "__main__":
    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    main()
