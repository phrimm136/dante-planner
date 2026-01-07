#!/usr/bin/env python3
"""
i18n Key Validation Script

Validates that all language files (EN, JP, KR, CN) have matching key structures.
Run this during CI or before commits to catch missing translations early.

Usage:
    python scripts/validate_i18n_keys.py

Exit codes:
    0 - All keys match across languages
    1 - Key mismatches found
"""

import json
import sys
import os

# --- Config ---
LANGUAGES = ['EN', 'JP', 'KR', 'CN']
NAMESPACES = ['common', 'database', 'planner', 'extraction']
BASE_PATH = os.path.join(os.path.dirname(__file__), '..', 'static', 'i18n')


# --- Functions ---

def extract_keys(obj, prefix=''):
    """Recursively extract all keys from a nested dict."""
    keys = set()
    if isinstance(obj, dict):
        for key, value in obj.items():
            full_key = f"{prefix}.{key}" if prefix else key
            keys.add(full_key)
            keys.update(extract_keys(value, full_key))
    return keys


def load_json(path):
    """Load JSON file, return empty dict if not found."""
    if not os.path.exists(path):
        return {}
    with open(path, 'r', encoding='utf-8') as f:
        return json.load(f)


def validate_namespace(namespace):
    """
    Validate a namespace across all languages.
    Returns dict of issues: {lang: set of missing keys}
    """
    lang_keys = {}
    for lang in LANGUAGES:
        path = os.path.join(BASE_PATH, lang, f'{namespace}.json')
        data = load_json(path)
        lang_keys[lang] = extract_keys(data)

    # Find union of all keys (expected keys)
    all_keys = set()
    for keys in lang_keys.values():
        all_keys.update(keys)

    # Find missing keys per language
    issues = {}
    for lang, keys in lang_keys.items():
        missing = all_keys - keys
        if missing:
            issues[lang] = missing

    return issues, len(all_keys)


def main():
    print("=" * 60)
    print("i18n Key Validation")
    print("=" * 60)

    total_issues = 0

    for namespace in NAMESPACES:
        print(f"\nChecking {namespace}.json...")
        issues, key_count = validate_namespace(namespace)

        if not issues:
            print(f"  [OK] All {len(LANGUAGES)} languages have {key_count} matching keys")
        else:
            for lang, missing in issues.items():
                print(f"  [WARN] {lang} missing {len(missing)} keys:")
                for key in sorted(missing)[:5]:
                    print(f"    - {key}")
                if len(missing) > 5:
                    print(f"    ... and {len(missing) - 5} more")
                total_issues += len(missing)

    print("\n" + "=" * 60)
    if total_issues == 0:
        print("Result: PASS - All keys match across languages")
        return 0
    else:
        print(f"Result: FAIL - {total_issues} total missing keys")
        return 1


if __name__ == '__main__':
    sys.exit(main())
