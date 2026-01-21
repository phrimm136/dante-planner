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
from lang_config import LANGS, I18N_DIR, get_raw_pattern, lang_dir_exists

# Non-localized raw data directory (for buff files in step_keyword)
RAW_DIR = "../raw/Json"

# Regex to strip Unity rich text tags (optional cleanup)
RICH_TEXT_PATTERN = re.compile(
    r'<sprite[^>]*>|<color[^>]*>|</color>|<u>|</u>|<link[^>]*>|</link>'
)

# Regex to extract bracketed keywords
BRACKET_PATTERN = re.compile(r"\[([^\[\]]+)\]")


def strip_rich_text(text: str) -> str:
    """Remove Unity rich text markup for clean display."""
    return RICH_TEXT_PATTERN.sub('', text).strip()


def load_panic_files(lang: str) -> dict:
    """
    Load all PanicInfo files for a language and merge by id.
    Later files override earlier ones (variants have latest data).
    """
    panic_map = {}

    # Glob all PanicInfo files for this language (uses lang_config for correct path/prefix)
    pattern = get_raw_pattern(lang, "PanicInfo*.json")
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
    output_dir = f"{I18N_DIR}/{lang}"
    output_path = f"{output_dir}/panicInfo.json"

    os.makedirs(output_dir, exist_ok=True)

    # Sort by numeric id for consistent output
    sorted_map = dict(sorted(panic_map.items(), key=lambda x: int(x[0])))

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(sorted_map, f, ensure_ascii=False, indent=2)

    print(f"  Saved {len(sorted_map)} entries to {output_path}")


def extract_bracketed_keywords(text: str) -> set:
    """Extract all [KeywordID] patterns from text."""
    if not text:
        return set()
    return set(BRACKET_PATTERN.findall(text))


def collect_used_keywords_from_panic_data(panic_map: dict) -> set:
    """Collect all bracketed keyword IDs from panicInfo data."""
    keywords = set()

    for panic_entry in panic_map.values():
        # Scan both low morale and panic descriptions
        keywords.update(extract_bracketed_keywords(panic_entry.get("lowMoraleDesc", "")))
        keywords.update(extract_bracketed_keywords(panic_entry.get("panicDesc", "")))

    return keywords


def load_json(path):
    """Load JSON file."""
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def save_json(path, data):
    """Save JSON file."""
    dir_name = os.path.dirname(path)
    if dir_name:
        os.makedirs(dir_name, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def load_battle_keywords(lang):
    """Load battleKeywords.json for a language (created by identity.py)."""
    keywords_path = os.path.join(I18N_DIR, lang, "battleKeywords.json")
    if os.path.exists(keywords_path):
        return load_json(keywords_path)
    return {}


def load_battle_keywords_raw():
    """Load raw BattleKeywords from game data for all languages."""
    all_keywords = {}

    for lang in LANGS:
        if not lang_dir_exists(lang):
            continue
        lang_keywords = {}
        pattern = get_raw_pattern(lang, "BattleKeywords*.json")

        for file_path in glob.glob(pattern):
            data = load_json(file_path)
            for obj in data.get("dataList", []):
                keyword_id = obj.get("id")
                if keyword_id:
                    lang_keywords[keyword_id] = {
                        "name": obj.get("name", ""),
                        "desc": obj.get("desc", ""),
                        "iconId": None,
                        "buffType": None,
                    }

        all_keywords[lang] = lang_keywords

    return all_keywords


def merge_buff_info(keyword_map):
    """Merge buff icon and type info into keyword map."""
    buff_files = glob.glob(os.path.join(RAW_DIR, "*buff*.json"))
    for file in buff_files:
        data = load_json(file)
        for buff in data.get("list", []):
            buff_id = buff.get("id")
            if buff_id in keyword_map:
                entry = keyword_map[buff_id]
                icon_id = buff.get("iconId") or buff.get("iconID")
                if icon_id is not None and entry["iconId"] is None:
                    entry["iconId"] = icon_id

                buff_type = buff.get("buffType")
                if buff_type is not None and entry["buffType"] is None:
                    entry["buffType"] = buff_type

    return keyword_map


def step_keyword():
    """Append panicInfo keywords to battleKeywords.json."""
    print("\n=== Appending panicInfo keywords to battleKeywords ===")

    # Step 1: Scan all panicInfo data to find used keyword IDs
    panic_keywords = set()
    for lang in LANGS:
        panic_path = os.path.join(I18N_DIR, lang, "panicInfo.json")
        if os.path.exists(panic_path):
            panic_data = load_json(panic_path)
            panic_keywords.update(collect_used_keywords_from_panic_data(panic_data))

    print(f"  Found {len(panic_keywords)} unique keywords in panicInfo descriptions")

    # Step 2: Load raw battle keywords to get info for new keywords
    all_keywords_raw = load_battle_keywords_raw()

    # Step 3: Append new panicInfo keywords to existing battleKeywords.json
    for lang in LANGS:
        if not lang_dir_exists(lang):
            continue
        existing_keywords = load_battle_keywords(lang)
        lang_keywords_raw = all_keywords_raw.get(lang, {})

        # Find new keywords not already in battleKeywords.json
        new_keywords = {k: lang_keywords_raw[k] for k in panic_keywords
                        if k not in existing_keywords and k in lang_keywords_raw}

        if new_keywords:
            # Merge buff info for new keywords
            new_keywords = merge_buff_info(new_keywords)
            # Append to existing
            existing_keywords.update(new_keywords)
            output_path = os.path.join(I18N_DIR, lang, "battleKeywords.json")
            save_json(output_path, existing_keywords)
            print(f"  [{lang}] Added {len(new_keywords)} new keywords to battleKeywords.json")


def main():
    print("=== PanicInfo Parser ===\n")

    for lang in LANGS:
        if not lang_dir_exists(lang):
            continue
        print(f"Processing {lang}...")
        panic_map = load_panic_files(lang)

        if panic_map:
            save_output(lang, panic_map)
        else:
            print(f"  Skipped {lang} - no data")

        print()

    # After processing panicInfo, extract keywords
    step_keyword()

    print("Done!")


if __name__ == "__main__":
    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    main()
