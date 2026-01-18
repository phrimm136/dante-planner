from pathlib import Path
from PIL import Image

# =========================
# Config
# =========================
INPUT_DIR = Path("../static/images/dummy")
OUTPUT_DIR = Path("../static/images/dummy")
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

QUALITY = 90

# Hex color to multiply with
HEX_COLOR = "#828280"

# =========================
# Prepare color
# =========================
hex_color = HEX_COLOR.lstrip("#")
mul_r = int(hex_color[0:2], 16)
mul_g = int(hex_color[2:4], 16)
mul_b = int(hex_color[4:6], 16)

# =========================
# Convert
# =========================
for png_path in INPUT_DIR.glob("*.png"):
    webp_path = OUTPUT_DIR / (png_path.stem + ".webp")

    with Image.open(png_path).convert("RGBA") as img:
        pixels = img.load()
        width, height = img.size

        for y in range(height):
            for x in range(width):
                r, g, b, a = pixels[x, y]

                # Multiply RGB, preserve alpha
                r = (r * mul_r) // 255
                g = (g * mul_g) // 255
                b = (b * mul_b) // 255

                pixels[x, y] = (r, g, b, a)

        img.save(
            webp_path,
            format="WEBP",
            quality=QUALITY,
            method=6,
            lossless=False
        )

    print(f"Converted: {png_path.name} → {webp_path.name}")
