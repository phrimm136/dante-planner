#!/usr/bin/env python3
"""
Theme Pack Image Composition Script

Composes pre-rendered theme pack images from multiple layers:
1. Base sprite (packSpriteId)
2. Boss sprite (optional, from packSpriteId + "_boss")
3. Alert icons (warning/baton)
4. Logo (unless isHideLogo)
5. Frame (normal or extreme)

Text is NOT included - it's rendered dynamically in the component
for i18n support and special name handling.

Usage:
    python3 scripts/compose-theme-packs.py [--preview PACK_ID]

    --preview PACK_ID: Generate only the specified pack for quick preview
"""

import json
import os
import re
import sys
import glob
from pathlib import Path
from PIL import Image

# =============================================================================
# CONFIGURATION - Adjust these values to fine-tune layer positions and scales
# =============================================================================

# Canvas size (matches sprite dimensions to preserve full image)
CANVAS_WIDTH = 380
CANVAS_HEIGHT = 690

# Base sprite configuration (normal packs)
BASE_SPRITE = {
    "x": 0,           # X offset from center
    "y": 0,           # Y offset from center
    "scale": 1.0,     # Scale factor (1.0 = original size)
    "anchor": "center" # Anchor point: "center", "top", "bottom"
}

# Base sprite configuration (extreme packs - scaled to fit frame within canvas)
# Extreme sprites are 314x628, scaled proportionally with frame
EXTREME_BASE_SPRITE = {
    "x": 0,           # X offset from center
    "y": 0,           # Y offset from center
    "scale": 1.055,   # Scale factor (proportional to frame reduction)
    "anchor": "center"
}

# Boss sprite configuration (layered on top of base)
BOSS_SPRITE = {
    "x": 0,           # X offset from center
    "y": -40,           # Y offset from center
    "scale": 1.08,     # Scale factor
    "anchor": "center"
}

# Warning icon configuration
WARNING_ICON = {
    "x": 0,         # X position from left
    "y": -250,          # Y position from top
    "scale": 0.6,     # Scale factor
    "anchor": "center" # Anchor point for positioning
}

# Baton icon configuration
BATON_ICON = {
    "x": 0,         # X position from left
    "y": 0,          # Y position from top
    "scale": 1.038,     # Scale factor
    "anchor": "center"
}

# Logo configuration for:
# - Main Canto chapters (Canto_I, Canto_II, Canto_III, Canto_IV, etc.)
# - Attack/Attribute type packs (AttackType_*, Amber_*, Azure_*, etc.)
LOGO_MAIN = {
    "x": 2,         # X position from left (center of canvas)
    "y": 148,         # Y position from top
    "scale": 0.5,     # Scale factor
    "anchor": "center"
}

# Logo configuration for:
# - Sub-chapter/event packs (Canto_HellsChicken, Canto_Miracle20, Canto_SEA, etc.)
LOGO_SUB = {
    "x": 0,         # X position from left (center of canvas)
    "y": 50,         # Y position from top (different position)
    "scale": 0.4,    # Scale factor (same as LOGO_MAIN)
    "anchor": "center"
}

# Frame configuration (normal packs)
FRAME_NORMAL = {
    "x": 0,           # X offset
    "y": 0,           # Y offset
    "scale": 1.0
}

# Frame configuration (extreme packs)
# extremeFrame is 701x1221, scale to fit canvas width (380) with vertical gap
FRAME_EXTREME = {
    "x": 0,           # X offset
    "y": 0,           # Y offset
    "scale": 0.542,   # 380/701 = 0.542, results in 380x661
    "anchor": "center"
}

# =============================================================================
# Paths
# =============================================================================

STATIC_DIR = Path(__file__).parent.parent / "static"
RAW_DIR = Path(__file__).parent.parent / "raw"
THEME_FLOOR_PATTERN = str(RAW_DIR / "Json" / "mirrordungeon-theme-floor-t*.json")
MIRROR_DUNGEON_DIR = RAW_DIR / "Mirror Dungeon"
UI_DIR = STATIC_DIR / "images" / "UI" / "themePack"
OUTPUT_DIR = STATIC_DIR / "images" / "themePack"

# =============================================================================
# Helper Functions
# =============================================================================

# Pattern to match main Canto chapters (Canto_I, Canto_II, etc. with roman numerals)
# Also matches variants like Canto_I_hard, Canto_II_mid
CANTO_MAIN_PATTERN = re.compile(r'^Canto_(I{1,3}|IV|V|VI{0,3})(_.*)?$')

# Prefixes for attack/attribute type packs
ATTACK_ATTRIBUTE_PREFIXES = [
    "AttackType",     # AttackType_normal, AttackType_effective, etc.
    "Amber_",         # Amber_normal, Amber_hard, Amber_effective
    "Azure_",
    "Crimson_",
    "Indigo_",
    "Scarlet_",
    "Shamrock_",
    "Violet_",
]

# Prefixes for keyword packs
KEYWORD_PREFIXES = [
    "Burn",
    "Bleed",
    "Tremor",
    "Rupture",
    "Sinking",
    "Poise",
    "Charge"
]


def is_main_logo_pack(pack_sprite_id: str) -> bool:
    """
    Check if pack should use LOGO_MAIN position.
    Returns True for:
    - Main Canto chapters (Canto_I, Canto_II, Canto_III, Canto_IV, etc.)
    - Attack/Attribute/Keyword type packs
    """
    # Check if it's a main Canto chapter (roman numeral)
    if CANTO_MAIN_PATTERN.match(pack_sprite_id):
        return True

    # Check if it's an attack/attribute type pack
    for prefix in ATTACK_ATTRIBUTE_PREFIXES:
        if pack_sprite_id.startswith(prefix):
            return True
    for prefix in KEYWORD_PREFIXES:
        if pack_sprite_id.startswith(prefix):
            return True
    if pack_sprite_id.endswith("Extreme"):
        return True

    return False


def is_extreme_pack(pack_data: dict) -> bool:
    """Check if theme pack is extreme (exceptionConditions is exactly [{"dungeonIdx": 3}])"""
    conditions = pack_data.get("exceptionConditions", [])
    return conditions == [{"dungeonIdx": 3}]


def find_sprite_in_mirror_dungeon(sprite_name: str) -> Path | None:
    """
    Find a sprite file across all Mirror Dungeon subdirectories.
    Searches for {sprite_name}.png in all subdirs of raw/Mirror Dungeon/.
    Returns the first match found, or None if not found.
    """
    for subdir in MIRROR_DUNGEON_DIR.iterdir():
        if not subdir.is_dir():
            continue
        sprite_path = subdir / f"{sprite_name}.png"
        if sprite_path.exists():
            return sprite_path
    return None


def load_theme_packs_from_raw() -> dict:
    """Load all theme packs from raw mirrordungeon-theme-floor-t*.json files."""
    theme_packs = {}

    for file_path in sorted(glob.glob(THEME_FLOOR_PATTERN)):
        with open(file_path, "r", encoding="utf-8") as f:
            data = json.load(f)

        for entry in data.get("list", []):
            pack_id = str(entry.get("id"))
            theme_packs[pack_id] = {
                "exceptionConditions": entry.get("exceptionConditions", []),
                "uiConfigs": entry.get("uiConfigs", {}),
            }

    return theme_packs


def load_image_safe(path: Path) -> Image.Image | None:
    """Load image if exists, return None otherwise"""
    if path.exists():
        return Image.open(path).convert("RGBA")
    return None


def scale_image(img: Image.Image, scale: float) -> Image.Image:
    """Scale image by factor"""
    if scale == 1.0:
        return img
    new_width = int(img.width * scale)
    new_height = int(img.height * scale)
    return img.resize((new_width, new_height), Image.Resampling.LANCZOS)


def get_paste_position(canvas_size: tuple, img_size: tuple, config: dict) -> tuple:
    """Calculate paste position based on config"""
    canvas_w, canvas_h = canvas_size
    img_w, img_h = img_size

    anchor = config.get("anchor", "center")
    x = config.get("x", 0)
    y = config.get("y", 0)

    if anchor == "center":
        # x, y are offsets from canvas center
        paste_x = (canvas_w - img_w) // 2 + x
        paste_y = (canvas_h - img_h) // 2 + y
    elif anchor == "top":
        paste_x = (canvas_w - img_w) // 2 + x
        paste_y = y
    elif anchor == "bottom":
        paste_x = (canvas_w - img_w) // 2 + x
        paste_y = canvas_h - img_h + y
    else:
        # x, y are absolute positions, anchor at image center
        paste_x = x - img_w // 2
        paste_y = y - img_h // 2

    return (int(paste_x), int(paste_y))


def get_boss_sprite_path(config: dict) -> Path | None:
    """Get boss sprite path only if bossSpriteId is explicitly provided"""
    boss_sprite_id = config.get("bossSpriteId")
    if boss_sprite_id:
        return find_sprite_in_mirror_dungeon(boss_sprite_id)
    return None


def create_content_mask(base_sprite: Image.Image, canvas_size: tuple, base_pos: tuple) -> Image.Image:
    """
    Create a mask from base sprite's alpha channel.
    This mask defines where content (boss, icons) can be visible.
    Areas outside the base sprite (holes, edges) will be transparent.
    """
    mask = Image.new("L", canvas_size, 0)  # Start with all black (transparent)

    # Get alpha channel from base sprite
    if base_sprite.mode == "RGBA":
        alpha = base_sprite.split()[3]
        # Paste the alpha channel at the base sprite position
        mask.paste(alpha, base_pos)

    return mask


def apply_mask_to_layer(layer: Image.Image, mask: Image.Image, layer_pos: tuple) -> Image.Image:
    """
    Apply content mask to a layer.
    Only pixels where both the layer and mask have alpha > 0 will be visible.
    """
    # Create a full-size canvas for the layer
    layer_canvas = Image.new("RGBA", mask.size, (0, 0, 0, 0))
    layer_canvas.paste(layer, layer_pos, layer)

    # Get layer's alpha channel
    layer_alpha = layer_canvas.split()[3]

    # Combine layer alpha with mask (both must be opaque)
    from PIL import ImageChops
    combined_alpha = ImageChops.multiply(layer_alpha, mask)

    # Apply combined alpha back to layer
    layer_canvas.putalpha(combined_alpha)

    return layer_canvas


def compose_theme_pack(pack_id: str, config: dict, is_extreme: bool) -> Image.Image | None:
    """Compose a single theme pack image from layers"""
    pack_sprite_id = config.get("packSpriteId")
    if not pack_sprite_id:
        print(f"  [SKIP] {pack_id}: No packSpriteId")
        return None

    # Create canvas
    canvas = Image.new("RGBA", (CANVAS_WIDTH, CANVAS_HEIGHT), (0, 0, 0, 0))

    # Layer 1: Base sprite
    base_path = find_sprite_in_mirror_dungeon(pack_sprite_id)
    if not base_path:
        print(f"  [SKIP] {pack_id}: Base sprite not found: {pack_sprite_id}")
        return None
    base = load_image_safe(base_path)
    if not base:
        print(f"  [SKIP] {pack_id}: Failed to load base sprite: {base_path}")
        return None

    # Use different config for extreme packs (smaller sprites need scaling up)
    base_config = EXTREME_BASE_SPRITE if is_extreme else BASE_SPRITE
    base = scale_image(base, base_config["scale"])
    base_pos = get_paste_position((CANVAS_WIDTH, CANVAS_HEIGHT), base.size, base_config)
    canvas.paste(base, base_pos, base)

    # Create content mask from base sprite
    # This mask ensures boss/icons don't appear outside the base sprite area
    content_mask = create_content_mask(base, (CANVAS_WIDTH, CANVAS_HEIGHT), base_pos)

    # Layer 2: Boss sprite (optional) - masked to base sprite area
    boss_path = get_boss_sprite_path(config)
    boss = load_image_safe(boss_path) if boss_path else None
    if boss:
        boss = scale_image(boss, BOSS_SPRITE["scale"])
        boss_pos = get_paste_position((CANVAS_WIDTH, CANVAS_HEIGHT), boss.size, BOSS_SPRITE)
        # Apply mask so boss doesn't appear in holes/outside base
        masked_boss = apply_mask_to_layer(boss, content_mask, boss_pos)
        canvas = Image.alpha_composite(canvas, masked_boss)

    # Layer 3: Warning icon - masked to base sprite area
    if config.get("isShowWarning"):
        warning = load_image_safe(UI_DIR / "warning.webp")
        if warning:
            warning = scale_image(warning, WARNING_ICON["scale"])
            warning_pos = get_paste_position((CANVAS_WIDTH, CANVAS_HEIGHT), warning.size, WARNING_ICON)
            # Apply mask so warning doesn't appear in holes/outside base
            masked_warning = apply_mask_to_layer(warning, content_mask, warning_pos)
            canvas = Image.alpha_composite(canvas, masked_warning)

    # Layer 4: Baton icon - masked to base sprite area
    if config.get("isShowBaton"):
        baton = load_image_safe(UI_DIR / "Baton.webp")
        if baton:
            baton = scale_image(baton, BATON_ICON["scale"])
            baton_pos = get_paste_position((CANVAS_WIDTH, CANVAS_HEIGHT), baton.size, BATON_ICON)
            # Apply mask so baton doesn't appear in holes/outside base
            masked_baton = apply_mask_to_layer(baton, content_mask, baton_pos)
            canvas = Image.alpha_composite(canvas, masked_baton)

    # Layer 5: Logo (unless hidden) - NOT masked (logo can extend beyond base)
    # Note: Sub-chapter packs have isHideLogo=true because their base sprites
    # already contain the logo baked into the artwork
    if not config.get("isHideLogo"):
        logo = load_image_safe(UI_DIR / "logo.webp")
        if logo:
            # Choose logo config:
            # - LOGO_MAIN for main Canto chapters + attack/attribute packs
            # - LOGO_SUB for sub-chapters/events (if needed in future)
            if is_main_logo_pack(pack_sprite_id):
                logo_config = LOGO_MAIN
            else:
                logo_config = LOGO_SUB

            logo = scale_image(logo, logo_config["scale"])
            pos = get_paste_position((CANVAS_WIDTH, CANVAS_HEIGHT), logo.size, logo_config)
            canvas.paste(logo, pos, logo)

    # Layer 6: Frame (always on top, not masked)
    if is_extreme:
        frame = load_image_safe(UI_DIR / "extremeFrame.webp")
        frame_config = FRAME_EXTREME
    else:
        frame = load_image_safe(UI_DIR / "frame.webp")
        frame_config = FRAME_NORMAL

    if frame:
        frame = scale_image(frame, frame_config["scale"])
        if "anchor" in frame_config and frame_config["anchor"] == "center":
            pos = get_paste_position((CANVAS_WIDTH, CANVAS_HEIGHT), frame.size, frame_config)
        else:
            pos = (frame_config["x"], frame_config["y"])
        canvas.paste(frame, pos, frame)

    return canvas


def main():
    """Main entry point"""
    print("Theme Pack Image Composition")
    print("=" * 50)

    # Check for preview mode
    preview_id = None
    if "--preview" in sys.argv:
        idx = sys.argv.index("--preview")
        if idx + 1 < len(sys.argv):
            preview_id = sys.argv[idx + 1]
            print(f"Preview mode: {preview_id}")

    # Load theme packs from raw files
    theme_packs = load_theme_packs_from_raw()
    print(f"Found {len(theme_packs)} theme packs from raw files")

    # Print current configuration
    print("\nCurrent Configuration:")
    print(f"  Canvas: {CANVAS_WIDTH}x{CANVAS_HEIGHT}")
    print(f"  Base sprite: {BASE_SPRITE}")
    print(f"  Boss sprite: {BOSS_SPRITE}")
    print(f"  Warning icon: {WARNING_ICON}")
    print(f"  Baton icon: {BATON_ICON}")
    print(f"  Logo (main Canto + atk/attr): {LOGO_MAIN}")
    print(f"  Logo (sub-chapter/events): {LOGO_SUB}")
    print()

    # Create output directory
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    # Filter packs if preview mode
    if preview_id:
        if preview_id not in theme_packs:
            print(f"Error: Pack ID {preview_id} not found")
            return
        theme_packs = {preview_id: theme_packs[preview_id]}

    # Process each theme pack
    success_count = 0
    skip_count = 0

    for pack_id, pack_data in theme_packs.items():
        config = pack_data.get("uiConfigs", {})
        is_extreme = is_extreme_pack(pack_data)
        pack_sprite_id = config.get("packSpriteId", "")

        composed = compose_theme_pack(pack_id, config, is_extreme)

        if composed:
            output_path = OUTPUT_DIR / f"{pack_id}.webp"
            composed.save(output_path, "WEBP", quality=90)
            success_count += 1
            flags = []
            if config.get("isShowWarning"):
                flags.append("warning")
            if config.get("isShowBaton"):
                flags.append("baton")
            # Logo flags: respect isHideLogo (sub-chapter sprites have logo baked in)
            if config.get("isHideLogo"):
                flags.append("no-logo")
            elif is_main_logo_pack(pack_sprite_id):
                flags.append("logo-main")
            else:
                flags.append("logo-sub")
            if is_extreme:
                flags.append("EXTREME")
            flag_str = f" [{', '.join(flags)}]" if flags else ""
            print(f"  [OK] {pack_id}{flag_str}")
        else:
            skip_count += 1

    print("=" * 50)
    print(f"Composed: {success_count}, Skipped: {skip_count}")
    print(f"Output: {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
