#!/usr/bin/env python3
"""
Sanity 아이콘 변환 스크립트

raw/Sanity의 PNG 파일을 WEBP로 변환하여 static/images/icon/sanity/로 이동합니다.
Public_Panic.png는 9999.webp로 rename됩니다.

사용법:
  python convert_sanity_icons.py
"""

from PIL import Image
from pathlib import Path

INPUT_DIR = Path(__file__).parent.parent / "raw" / "Sanity"
OUTPUT_DIR = Path(__file__).parent.parent / "static" / "images" / "icon" / "sanity"
QUALITY = 85  # 0–100

# Create output directory if it doesn't exist
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

converted = 0
skipped = 0

for png_path in INPUT_DIR.glob("*.png"):
    # Handle Public_Panic.png rename
    if png_path.name == "Public_Panic.png":
        output_name = "9999.webp"
    else:
        output_name = png_path.stem + ".webp"

    webp_path = OUTPUT_DIR / output_name

    if webp_path.exists():
        skipped += 1
        continue

    with Image.open(png_path) as img:
        img.save(
            webp_path,
            format="WEBP",
            quality=QUALITY,
            method=6,   # best compression
            lossless=False
        )

    converted += 1
    print(f"Converted: {png_path.name} → {output_name}")

print(f"\nDone: {converted} converted, {skipped} skipped (already exist)")
