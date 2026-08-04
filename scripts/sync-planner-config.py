#!/usr/bin/env python3
"""
Sync planner config values to frontend constants and backend properties.

This script is the single source of truth for planner version configuration.
Change the values below, then run the script to update both FE and BE files.

Usage:
    python scripts/sync-planner-config.py
    python scripts/sync-planner-config.py --dry-run

Exit codes:
    0 - files already in sync, or (without --dry-run) successfully updated
    1 - a pattern matched other than exactly once, or --dry-run found drift
"""

import re
import sys
from pathlib import Path

# ─────────────────────────────────────────────────
# AUTHORITATIVE CONFIG VALUES — edit these to update
# ─────────────────────────────────────────────────
SCHEMA_VERSION = 2
MD_CURRENT_VERSION = 7
MD_AVAILABLE_VERSIONS = [6, 7]
RR_AVAILABLE_VERSIONS = [1, 5]
# ─────────────────────────────────────────────────

REPO_ROOT = Path(__file__).resolve().parent.parent
FE_CONSTANTS = REPO_ROOT / "frontend" / "src" / "lib" / "constants.ts"
BE_PROPERTIES = REPO_ROOT / "backend" / "src" / "main" / "resources" / "application.properties"


def fmt_ts_array(values: list[int]) -> str:
    return "[" + ", ".join(str(v) for v in values) + "]"


def fmt_properties_list(values: list[int]) -> str:
    return ",".join(str(v) for v in values)


class PatternMiss(Exception):
    """A replacement pattern did not match exactly once in its target."""


def apply_replacements(text: str, replacements: dict[str, str], where: str) -> str:
    """Apply each pattern once, refusing any count other than one."""
    for pattern, replacement in replacements.items():
        text, count = re.subn(pattern, replacement, text)
        if count != 1:
            raise PatternMiss(f"{where}: pattern {pattern!r} matched {count} time(s), expected 1")
    return text


def sync_fe_constants(dry_run: bool = False) -> bool:
    content = FE_CONSTANTS.read_text()

    replacements = {
        r"(schemaVersion:\s*)\d+": rf"\g<1>{SCHEMA_VERSION}",
        r"(mdCurrentVersion:\s*)\d+": rf"\g<1>{MD_CURRENT_VERSION}",
        r"(mdAvailableVersions:\s*)\[[^\]]*\]": rf"\g<1>{fmt_ts_array(MD_AVAILABLE_VERSIONS)}",
        r"(rrAvailableVersions:\s*)\[[^\]]*\]": rf"\g<1>{fmt_ts_array(RR_AVAILABLE_VERSIONS)}",
    }

    # Narrow scope to PLANNER_CONFIG block to avoid replacing other objects
    config_match = re.search(
        r"(export const PLANNER_CONFIG = \{)(.*?)(\} as const)",
        content,
        re.DOTALL,
    )
    if not config_match:
        raise PatternMiss(f"{FE_CONSTANTS.name}: PLANNER_CONFIG block not found")

    block = config_match.group(2)
    new_block = apply_replacements(block, replacements, FE_CONSTANTS.name)

    if new_block == block:
        print(f"FE  {FE_CONSTANTS.name}: already in sync")
        return False

    new_content = content[:config_match.start(2)] + new_block + content[config_match.end(2):]

    if dry_run:
        print(f"FE  {FE_CONSTANTS.name}: would update")
    else:
        FE_CONSTANTS.write_text(new_content)
        print(f"FE  {FE_CONSTANTS.name}: updated")

    return True


def sync_be_properties(dry_run: bool = False) -> bool:
    content = BE_PROPERTIES.read_text()

    replacements = {
        r"(planner\.schema-version=)\S+": rf"\g<1>{SCHEMA_VERSION}",
        r"(planner\.md\.current-version=)\S+": rf"\g<1>{MD_CURRENT_VERSION}",
        r"(planner\.md\.available-versions=)\S+": rf"\g<1>{fmt_properties_list(MD_AVAILABLE_VERSIONS)}",
        r"(planner\.rr\.available-versions=)\S+": rf"\g<1>{fmt_properties_list(RR_AVAILABLE_VERSIONS)}",
    }

    new_content = apply_replacements(content, replacements, BE_PROPERTIES.name)

    if new_content == content:
        print(f"BE  {BE_PROPERTIES.name}: already in sync")
        return False

    if dry_run:
        print(f"BE  {BE_PROPERTIES.name}: would update")
    else:
        BE_PROPERTIES.write_text(new_content)
        print(f"BE  {BE_PROPERTIES.name}: updated")

    return True


def main() -> int:
    dry_run = "--dry-run" in sys.argv

    if dry_run:
        print("[dry-run] No files will be modified.\n")

    try:
        fe_changed = sync_fe_constants(dry_run)
        be_changed = sync_be_properties(dry_run)
    except PatternMiss as e:
        print(f"ERROR: {e}", file=sys.stderr)
        return 1

    if not fe_changed and not be_changed:
        print("\nAll files already in sync.")
        return 0

    if dry_run:
        print("\nRe-run without --dry-run to apply changes.")
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
