#!/usr/bin/env python3
"""Resolve unit-keyword ids to their display labels across all four locales.

Read-only accuracy aid for the plan-keyword skill. Prints each id's EN/KR/JP/CN
label exactly as stored in unitKeywords.json so the label can be copied into
plannerKeywords.json without retyping. Writes nothing.

The printed `utf-8 bytes=NN` is a copy-fidelity check: after hand-editing
plannerKeywords.json, the stored label must have the same byte length.

Usage:
    resolve-labels.py THUMB_FINGER SPIDER_HOUSE
    resolve-labels.py AccelBullet=THUMB_FINGER SojiRyoshuEntangle=SPIDER_HOUSE

A bare token is treated as a unit-keyword id. A KEYWORD_ID=UNIT_KEYWORD_ID pair
additionally prints a paste-ready plannerKeywords.json property line per locale.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

LOCALES = ("EN", "KR", "JP", "CN")


def find_repo_root(start: Path) -> Path:
    for d in (start, *start.parents):
        if (d / "static" / "i18n" / "EN" / "unitKeywords.json").is_file():
            return d
    sys.exit(f"error: could not locate static/i18n/EN/unitKeywords.json from {start}")


def load_unit_keywords(root: Path) -> dict[str, dict]:
    data: dict[str, dict] = {}
    for loc in LOCALES:
        path = root / "static" / "i18n" / loc / "unitKeywords.json"
        data[loc] = json.loads(path.read_text(encoding="utf-8"))
    return data


def main(argv: list[str]) -> int:
    if not argv:
        sys.exit("usage: resolve-labels.py [KEYWORD_ID=]UNIT_KEYWORD_ID ...")

    root = find_repo_root(Path(__file__).resolve().parent)
    unit = load_unit_keywords(root)

    for token in argv:
        keyword_id, sep, unit_id = token.partition("=")
        if not sep:
            keyword_id, unit_id = "", keyword_id

        header = f"\n# {unit_id}"
        if keyword_id:
            header += f"  (keyword-id: {keyword_id})"
        print(header)

        for loc in LOCALES:
            label = unit[loc].get(unit_id)
            if label is None:
                print(f"  {loc}: !! MISSING in unitKeywords.json")
                continue
            nbytes = len(label.encode("utf-8"))
            print(f"  {loc}: {label}   [utf-8 bytes={nbytes}]")
            if keyword_id:
                obj = json.dumps({keyword_id: {"label": label}}, ensure_ascii=False)
                print(f"      paste -> {loc}/plannerKeywords.json: {obj[1:-1].strip()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
