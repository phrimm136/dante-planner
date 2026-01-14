#!/usr/bin/env python3
"""
Reorganize Identity & EGO art files by ID.

Handles two source directories:

1. raw/Identities & EGO Art/{Sinner}/{Identities|EGO}/{Name}/{ID}_*.png
   - Files named with 5-digit prefix: 10103_normal.png
   - ID extracted from prefix

2. raw/Sinners/{Sinner}/{Identity Name}/{7-digit}.png
   - Files named with 7-digit ID: 1010301.png
   - First 5 digits = identity ID

Output:
    static/images/identity/{ID}/{filename}.webp  (for 1xxxx IDs)
    static/images/ego/{ID}/{filename}.webp       (for 2xxxx IDs)

PNG files are converted to WebP (quality=85, method=6, lossy).

Usage:
    python reorganize_art.py              # Dry run (preview changes)
    python reorganize_art.py --execute    # Actually copy files
"""

import argparse
import re
from pathlib import Path

from PIL import Image

# WebP conversion settings (from pngToWebp.py)
WEBP_QUALITY = 85  # 0–100
WEBP_METHOD = 6    # best compression
WEBP_LOSSLESS = False

# Pattern 1: 5-digit prefix with underscore (e.g., 10103_normal.png)
ID_PREFIX_PATTERN = re.compile(r'^(\d{5})_')

# Pattern 2: 7-digit filename (e.g., 1010301.png) - extract first 5 digits
ID_7DIGIT_PATTERN = re.compile(r'^(\d{5})(\d{2})(_4)*\.png$')

BASE_DIR = Path(__file__).parent.parent
SOURCE_ART = BASE_DIR / "raw" / "Identities & EGO Art"
SOURCE_SINNERS = BASE_DIR / "raw" / "Sinners"
OUTPUT_BASE = BASE_DIR / "static" / "images"


def get_output_dir(file_id: str) -> Path:
    """Determine output directory based on ID prefix."""
    if file_id.startswith('1'):
        return OUTPUT_BASE / "identity"
    elif file_id.startswith('2'):
        return OUTPUT_BASE / "ego"
    else:
        return OUTPUT_BASE / "other"


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


def find_art_files(source_dir: Path) -> list[tuple[Path, str, str]]:
    """
    Find all art files with valid ID patterns.

    Handles:
    - 5-digit prefix: 10103_normal.png -> ID=10103
    - 7-digit filename: 1010301.png -> ID=10103

    Returns:
        List of (source_path, id, filename) tuples
    """
    results = []

    for png_file in source_dir.rglob("*.png"):
        filename = png_file.name

        # Try pattern 1: 5-digit prefix with underscore
        match = ID_PREFIX_PATTERN.match(filename)
        if match:
            file_id = match.group(1)
            results.append((png_file, file_id, filename))
            continue

        # Try pattern 2: 7-digit filename (first 5 = ID)
        match = ID_7DIGIT_PATTERN.match(filename)
        if match:
            file_id = match.group(1)
            results.append((png_file, file_id, filename))

    return results


def reorganize_files(
    files: list[tuple[Path, str, str]],
    execute: bool = False
) -> dict[str, int]:
    """
    Reorganize files into ID-based folders.

    Args:
        files: List of (source_path, id, filename) tuples
        execute: If True, actually perform the copy

    Returns:
        Stats dict with counts
    """
    stats = {"total": 0, "converted": 0, "skipped": 0}

    # Group files by ID
    by_id: dict[str, list[tuple[Path, str]]] = {}
    for source_path, file_id, filename in files:
        if file_id not in by_id:
            by_id[file_id] = []
        by_id[file_id].append((source_path, filename))

    # Process each ID group
    for file_id in sorted(by_id.keys()):
        file_list = by_id[file_id]
        output_dir = get_output_dir(file_id)
        id_dir = output_dir / file_id

        for source_path, filename in file_list:
            stats["total"] += 1
            # Convert .png filename to .webp
            webp_filename = Path(filename).stem + ".webp"
            dest_path = id_dir / webp_filename

            # Check for existing file
            if dest_path.exists():
                print(f"  SKIP (exists): {file_id}/{webp_filename}")
                stats["skipped"] += 1
                continue

            if execute:
                id_dir.mkdir(parents=True, exist_ok=True)
                convert_png_to_webp(source_path, dest_path)
                print(f"  CONVERT: {file_id}/{webp_filename}")
            else:
                # Show relative to raw/ directory
                rel_source = source_path.relative_to(BASE_DIR / "raw")
                print(f"  {rel_source} -> {file_id}/{webp_filename}")

            stats["converted"] += 1

    return stats


def main():
    parser = argparse.ArgumentParser(
        description="Reorganize Identity & EGO art by ID"
    )
    parser.add_argument(
        "--execute",
        action="store_true",
        help="Actually copy files (default: dry run)",
    )

    args = parser.parse_args()
    dry_run = not args.execute

    print(f"{'[DRY-RUN] ' if dry_run else ''}Reorganize Identity & EGO Art")
    print(f"Output: {OUTPUT_BASE}/{{identity|ego}}/{{ID}}/")
    print("-" * 60)

    # Collect files from both sources
    all_files = []

    # Source 1: Identities & EGO Art
    if SOURCE_ART.exists():
        files = find_art_files(SOURCE_ART)
        print(f"[Identities & EGO Art] Found {len(files)} files")
        all_files.extend(files)
    else:
        print(f"[Identities & EGO Art] Not found: {SOURCE_ART}")

    # Source 2: Sinners
    if SOURCE_SINNERS.exists():
        files = find_art_files(SOURCE_SINNERS)
        print(f"[Sinners] Found {len(files)} files")
        all_files.extend(files)
    else:
        print(f"[Sinners] Not found: {SOURCE_SINNERS}")

    if not all_files:
        print("No files found to process.")
        return 1

    # Group by type for summary
    identity_count = sum(1 for _, fid, _ in all_files if fid.startswith('1'))
    ego_count = sum(1 for _, fid, _ in all_files if fid.startswith('2'))
    print("-" * 60)
    print(f"Total files: {len(all_files)}")
    print(f"  - Identity files (1xxxx): {identity_count}")
    print(f"  - EGO files (2xxxx): {ego_count}")
    print("-" * 60)

    # Process
    stats = reorganize_files(all_files, args.execute)

    print("-" * 60)
    print(f"Total: {stats['total']}")
    print(f"Converted: {stats['converted']}")
    print(f"Skipped: {stats['skipped']}")

    if dry_run:
        print("\n[DRY-RUN] Use --execute to actually copy files.")

    return 0


if __name__ == "__main__":
    exit(main())
