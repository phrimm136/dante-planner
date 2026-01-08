from PIL import Image
from pathlib import Path

INPUT_DIR = Path(__file__).parent.parent / "static" / "images"
QUALITY = 85  # 0–100

converted = 0
skipped = 0

for png_path in INPUT_DIR.rglob("*.png"):
    webp_path = png_path.with_suffix(".webp")

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
    print(f"Converted: {png_path.relative_to(INPUT_DIR)}")

print(f"\nDone: {converted} converted, {skipped} skipped (already exist)")

