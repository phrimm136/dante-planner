#!/usr/bin/env python3
"""
Sync planner config values to frontend constants and backend properties.

This script is the single source of truth for planner version configuration.
Change the values below, then run the script to update both FE and BE files.

Usage:
    python scripts/sync-planner-config.py
    python scripts/sync-planner-config.py --dry-run
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
        print("ERROR: PLANNER_CONFIG block not found in constants.ts")
        sys.exit(1)

    block = config_match.group(2)
    new_block = block
    for pattern, replacement in replacements.items():
        new_block = re.sub(pattern, replacement, new_block)

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

    new_content = content
    for pattern, replacement in replacements.items():
        new_content = re.sub(pattern, replacement, new_content)

    if new_content == content:
        print(f"BE  {BE_PROPERTIES.name}: already in sync")
        return False

    if dry_run:
        print(f"BE  {BE_PROPERTIES.name}: would update")
    else:
        BE_PROPERTIES.write_text(new_content)
        print(f"BE  {BE_PROPERTIES.name}: updated")

    return True


def main() -> None:
    dry_run = "--dry-run" in sys.argv

    if dry_run:
        print("[dry-run] No files will be modified.\n")

    fe_changed = sync_fe_constants(dry_run)
    be_changed = sync_be_properties(dry_run)

    if not fe_changed and not be_changed:
        print("\nAll files already in sync.")
    elif dry_run:
        print("\nRe-run without --dry-run to apply changes.")


if __name__ == "__main__":
    main()
