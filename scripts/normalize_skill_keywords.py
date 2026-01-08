#!/usr/bin/env python3
"""
Normalize skill descriptions to use format string keywords like [Sinking] instead of localized text.

Usage:
    python normalize_skill_keywords.py           # Apply changes
    python normalize_skill_keywords.py --dry-run # Preview only
    python normalize_skill_keywords.py -l KR     # Process single language
"""

import json
import re
import glob
import os
import sys
from lang_config import LANGS

# --- 설정 ---
base_i18n = "../static/i18n"

# --- 함수 정의 ---

def load_json(path):
    """Load JSON file with error handling."""
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError) as e:
        print(f"Warning: Could not load {path}: {e}")
        return None


def save_json(path, data):
    """Save JSON file with consistent formatting."""
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def build_keyword_mapping(lang):
    """
    Build mapping from localized name -> keyword ID.
    Returns dict sorted by name length (longest first) to handle overlapping names.
    """
    keywords_path = f"{base_i18n}/{lang}/battleKeywords.json"
    keywords_data = load_json(keywords_path)

    if not keywords_data:
        return {}

    mapping = {}
    for keyword_id, keyword_info in keywords_data.items():
        if isinstance(keyword_info, dict) and "name" in keyword_info:
            name = keyword_info["name"]
            if name and isinstance(name, str):
                # Map localized name to keyword ID
                # For EN: "Sinking" -> "Sinking" (same, but needed for [Sinking] wrapping)
                # For KR: "침잠" -> "Sinking"
                mapping[name] = keyword_id

    return mapping


def replace_keywords_in_text(text, mapping, sorted_names):
    """
    Replace localized keywords with bracketed format strings.
    e.g., "침잠 1 부여" -> "[Sinking] 1 부여"
    """
    if not text or not mapping:
        return text

    result = text

    for name in sorted_names:
        keyword_id = mapping[name]

        # Skip if name is too short (avoid false positives)
        if len(name) < 2:
            continue

        # Pattern: match the name but not if already in brackets
        # Negative lookbehind for '[' and negative lookahead for ']'
        escaped_name = re.escape(name)

        # For ASCII-only keywords (English), add word boundary to prevent
        # matching partial words like "Discard" in "Discarded"
        if name.isascii() and name.isalpha():
            pattern = rf"(?<!\[)\b{escaped_name}\b(?!\])"
        else:
            # For non-ASCII (KR, JP, CN), no word boundary needed
            pattern = rf"(?<!\[){escaped_name}(?!\])"

        replacement = f"[{keyword_id}]"

        result = re.sub(pattern, replacement, result)

    return result


def process_skill_file(file_path, mapping, sorted_names, dry_run=False):
    """
    Process a single identity/ego JSON file.
    Returns dict with change statistics.
    """
    data = load_json(file_path)
    if not data:
        return {"file": file_path, "error": "Could not load file", "changes": []}

    changes = []
    modified = False

    def process_text_field(obj, key, location):
        nonlocal modified
        if key in obj and obj[key]:
            original = obj[key]
            replaced = replace_keywords_in_text(original, mapping, sorted_names)
            if original != replaced:
                changes.append({
                    "location": location,
                    "original": original,
                    "replaced": replaced
                })
                if not dry_run:
                    obj[key] = replaced
                    modified = True

    def process_list_field(obj, key, location):
        nonlocal modified
        if key in obj and isinstance(obj[key], list):
            for j, item in enumerate(obj[key]):
                if item and isinstance(item, str):
                    original = item
                    replaced = replace_keywords_in_text(original, mapping, sorted_names)
                    if original != replaced:
                        changes.append({
                            "location": f"{location}[{j}]",
                            "original": original,
                            "replaced": replaced
                        })
                        if not dry_run:
                            obj[key][j] = replaced
                            modified = True

    # Process skills
    if "skills" in data:
        for skill_id, skill_info in data["skills"].items():
            if not isinstance(skill_info, dict):
                continue

            # Process descs array
            if "descs" in skill_info and isinstance(skill_info["descs"], list):
                for i, desc_obj in enumerate(skill_info["descs"]):
                    if not isinstance(desc_obj, dict):
                        continue

                    process_text_field(desc_obj, "desc", f"skills.{skill_id}.descs[{i}].desc")
                    process_list_field(desc_obj, "coinDescs", f"skills.{skill_id}.descs[{i}].coinDescs")

    # Process passives
    if "passives" in data:
        for passive_id, passive_info in data["passives"].items():
            if not isinstance(passive_info, dict):
                continue
            process_text_field(passive_info, "desc", f"passives.{passive_id}.desc")

    # Process EGO-specific fields (awakening, corrosion)
    for ego_field in ["awakening", "corrosion"]:
        if ego_field in data and isinstance(data[ego_field], dict):
            process_text_field(data[ego_field], "desc", f"{ego_field}.desc")
            process_list_field(data[ego_field], "coinDescs", f"{ego_field}.coinDescs")

    # Save if modified
    if modified and not dry_run:
        save_json(file_path, data)

    return {
        "file": file_path,
        "changes": changes,
        "modified": modified
    }


def process_language(lang, dry_run=False):
    """Process all identity and ego files for a language."""
    print(f"\n{'='*60}")
    print(f"Processing language: {lang}")
    print(f"{'='*60}")

    # Build keyword mapping
    mapping = build_keyword_mapping(lang)
    print(f"Loaded {len(mapping)} keyword mappings")

    if not mapping:
        print(f"No keywords found for {lang}, skipping...")
        return []

    # Sort by length (longest first) to handle overlapping names
    # e.g., "진동 폭발" should be matched before "진동"
    sorted_names = sorted(mapping.keys(), key=len, reverse=True)

    results = []
    lang_dir = f"{base_i18n}/{lang}"

    # Process identity files
    identity_files = sorted(glob.glob(f"{lang_dir}/identity/*.json"))
    print(f"Processing {len(identity_files)} identity files...")

    for file_path in identity_files:
        result = process_skill_file(file_path, mapping, sorted_names, dry_run)
        if result.get("changes"):
            results.append(result)

    # Process ego files
    ego_files = sorted(glob.glob(f"{lang_dir}/ego/*.json"))
    print(f"Processing {len(ego_files)} ego files...")

    for file_path in ego_files:
        result = process_skill_file(file_path, mapping, sorted_names, dry_run)
        if result.get("changes"):
            results.append(result)

    return results


def print_summary(all_results):
    """Print summary of all changes."""
    print(f"\n{'='*60}")
    print("SUMMARY")
    print(f"{'='*60}")

    total_files = 0
    total_changes = 0

    for lang, results in all_results.items():
        lang_changes = sum(len(r.get("changes", [])) for r in results)
        lang_files = len([r for r in results if r.get("changes")])

        print(f"\n{lang}:")
        print(f"  Files with changes: {lang_files}")
        print(f"  Total replacements: {lang_changes}")

        total_files += lang_files
        total_changes += lang_changes

        # Show sample changes (first 3 files, 2 changes each)
        for result in results[:3]:
            if result.get("changes"):
                filename = os.path.basename(result["file"])
                print(f"\n  {filename}:")
                for change in result["changes"][:2]:
                    orig = change["original"][:50] + "..." if len(change["original"]) > 50 else change["original"]
                    repl = change["replaced"][:50] + "..." if len(change["replaced"]) > 50 else change["replaced"]
                    print(f"    - {orig}")
                    print(f"    + {repl}")

    print(f"\n{'='*60}")
    print(f"TOTAL: {total_files} files, {total_changes} replacements")
    print(f"{'='*60}")


# --- 메인 ---
def main():
    import argparse

    parser = argparse.ArgumentParser(
        description="Normalize skill descriptions to use format string keywords"
    )
    parser.add_argument(
        "--dry-run", "-n",
        action="store_true",
        help="Only show what would be changed, without modifying files"
    )
    parser.add_argument(
        "--lang", "-l",
        choices=["KR", "EN", "JP", "CN", "all"],
        default="all",
        help="Language to process (default: all)"
    )

    args = parser.parse_args()

    # Change to script directory for relative paths
    script_dir = os.path.dirname(os.path.abspath(__file__))
    os.chdir(script_dir)

    print(f"Working directory: {os.getcwd()}")
    print(f"Mode: {'DRY RUN' if args.dry_run else 'APPLY CHANGES'}")

    # Determine languages to process
    target_langs = LANGS if args.lang == "all" else [args.lang]

    # Process each language
    all_results = {}
    for lang in target_langs:
        lang_dir = f"{base_i18n}/{lang}"
        if os.path.exists(lang_dir):
            results = process_language(lang, args.dry_run)
            all_results[lang] = results
        else:
            print(f"Warning: Language directory not found: {lang_dir}")

    # Print summary
    print_summary(all_results)

    if args.dry_run:
        print("\nDry run complete. Use without --dry-run to apply changes.")
    else:
        print("\nChanges applied successfully.")

    return 0


if __name__ == "__main__":
    sys.exit(main())
