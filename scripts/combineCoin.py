from pathlib import Path
from PIL import Image

# =========================
# Config
# =========================
RAW_DIR = Path(__file__).parent.parent / "raw"
OUTPUT_DIR = Path(__file__).parent.parent / "static" / "images" / "UI" / "common"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

QUALITY = 85
HEX_COLOR = "#e7c39c"
TEXT_SCALE = 0.8  # Scale factor for number text (1.0 = original size)
BG_SCALE = 1.2    # Scale factor for background (1.0 = original size)

# =========================
# Prepare multiply color
# =========================
hex_color = HEX_COLOR.lstrip("#")
mul_r = int(hex_color[0:2], 16)
mul_g = int(hex_color[2:4], 16)
mul_b = int(hex_color[4:6], 16)


def multiply_color(img: Image.Image) -> Image.Image:
    """Multiply RGB channels with the target color."""
    img = img.convert("RGBA")
    pixels = img.load()
    width, height = img.size

    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            r = (r * mul_r) // 255
            g = (g * mul_g) // 255
            b = (b * mul_b) // 255
            pixels[x, y] = (r, g, b, a)

    return img


def scale_image(img: Image.Image, scale: float) -> Image.Image:
    """Scale image by given factor."""
    if scale == 1.0:
        return img
    new_w = int(img.width * scale)
    new_h = int(img.height * scale)
    return img.resize((new_w, new_h), Image.Resampling.LANCZOS)


# =========================
# Process each coin
# =========================
coin_frame = Image.open(RAW_DIR / "coin.png").convert("RGBA")
coin_bg_raw = Image.open(RAW_DIR / "coinbg.png").convert("RGBA")

# Color coinbg black (multiply with black = set RGB to 0, keep alpha)
coin_bg_black = coin_bg_raw.copy()
pixels = coin_bg_black.load()
for y in range(coin_bg_black.height):
    for x in range(coin_bg_black.width):
        r, g, b, a = pixels[x, y]
        pixels[x, y] = (0, 0, 0, a)

for i in range(1, 11):
    number_path = RAW_DIR / f"coin{i}.png"
    output_path = OUTPUT_DIR / f"coin{i}.webp"

    with Image.open(number_path).convert("RGBA") as number_img:
        frame_w, frame_h = coin_frame.size

        # Start with transparent canvas
        combined = Image.new("RGBA", (frame_w, frame_h), (0, 0, 0, 0))

        # Layer 1: coinbg (scaled, centered, colored black)
        scaled_bg = scale_image(coin_bg_black, BG_SCALE)
        bg_w, bg_h = scaled_bg.size
        bg_offset_x = (frame_w - bg_w) // 2
        bg_offset_y = (frame_h - bg_h) // 2
        combined.paste(scaled_bg, (bg_offset_x, bg_offset_y), scaled_bg)

        # Layer 2: coin frame (no tint)
        combined.paste(coin_frame, (0, 0), coin_frame)

        # Layer 3: number text (scaled, centered, tinted)
        scaled_number = scale_image(number_img, TEXT_SCALE)
        tinted_number = multiply_color(scaled_number)
        num_w, num_h = tinted_number.size
        num_offset_x = (frame_w - num_w) // 2
        num_offset_y = (frame_h - num_h) // 2
        combined.paste(tinted_number, (num_offset_x, num_offset_y), tinted_number)

        tinted = combined

        # Save as webp
        tinted.save(
            output_path,
            format="WEBP",
            quality=QUALITY,
            method=6,
            lossless=False
        )

        print(f"Created: {output_path.name}")

coin_frame.close()
coin_bg_raw.close()
print(f"\nDone: 10 coin indicators created in {OUTPUT_DIR}")
