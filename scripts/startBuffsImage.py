from pathlib import Path
from PIL import Image
from md_config import MD_VERSION

TINT_COLOR = "#B00000"

INPUT_DIR = Path("../raw/Mirror Dungeon/Mirror of the Dreaming")
OUTPUT_DIR = Path(f"../static/images/UI/MD{MD_VERSION}")

OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

hex_color = TINT_COLOR.lstrip("#")
mul_r = int(hex_color[0:2], 16)
mul_g = int(hex_color[2:4], 16)
mul_b = int(hex_color[4:6], 16)

for png_path in INPUT_DIR.glob("StartBuffIcon_*.png"):
    webp_path = OUTPUT_DIR / (png_path.stem + ".webp")

    with Image.open(png_path).convert("RGBA") as img:
        pixels = img.load()
        width, height = img.size

        for y in range(height):
            for x in range(width):
                r, g, b, a = pixels[x, y]
                pixels[x, y] = (
                    (r * mul_r) // 255,
                    (g * mul_g) // 255,
                    (b * mul_b) // 255,
                    a,
                )

        img.save(webp_path, format="WEBP", quality=90, method=6, lossless=False)

    print(f"Converted: {png_path.name} → {webp_path.name}")
