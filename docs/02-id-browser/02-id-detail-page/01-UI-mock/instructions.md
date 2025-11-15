# Task: UI Mock

## Description
See `ego info pc.png` and follow the following descriptions to draw a layout. Note that the colors are for identification; do not use that color for styling; follow the global theme:

**Header Area** (top-left quadrant):
1. **Grade Icon** (yellow box) - Top-left corner
2. **Identity Name** (orange box) - Below grade, multi-line support
3. **Character Image** (red box) - Large portrait
   - **Image Toggle Icon** (black box) - Switch between alternate portraits
   - **Full-Size Icon** (gray box) - Opens image in new tab

**Status Panels** (left column, green boxes):

1. **Status Panel**:
   - Title: "Status"
   - Content rows (icon + text pairs with spacing):
     - HP: `hp_icon` + `{hp_value}`
     - Speed: `speed_icon` + `{min_speed}-{max_speed}`
     - Defense: `defense_icon` + `defense_level`

2. **Resistance Panel**:
   - Title: "Resistance"
   - Content rows:
     - Slash: `slash_icon` + `{slash_resistance}` (e.g., "x1.5", "Normal", "Resist")
     - Pierce: `pierce_icon` + `{pierce_resistance}`
     - Blunt: `blunt_icon` + `{blunt_resistance}`

3. **Stagger Panel**:
   - Title: "Stagger Thresholds"
   - Content: `{stagger_array}` (e.g., "20%, 40%, 60%, 75%")

4. **Traits Panel**:
   - Title: "Traits"
   - Content: Space-separated trait tags from `identity_spec.json`

**Skills Panel** (top-right quadrant, blue box):

**Tab Selector** (purple box): Four buttons
- Skill 1, Skill 2, Skill 3, Defense (Guard)
- Active tab highlighted
- Default: Skill 1

**Skill Display** (black box - switched by tabs):

1. **Skill Image Composite** (red box):
   - Layer structure (back to front):
     - Skill background image
     - Skill artwork
     - Skill frame overlay
   - **Bottom Overlay**: Attack type indicator
     - Attack type background → Attack type image → Attack type frame
   - **Left Overlay**: Base power text (e.g., "4-6")
   - **Top Overlay**: Coin power modifier (e.g., "+3", format positive with "+" prefix)

2. **Coin Icons** (blue box):
   - Display sequence of coins
   - "C" = Standard coin (breakable)
   - "U" = Unbreakable coin
   - Example: "C C U" for 2 breakable + 1 unbreakable

3. **Skill Name** (orange box):
   - Text with background pill
   - Background width = text width + padding

4. **Offense/Defense Level** (yellow box):
   - Shows both conventions simultaneously:
     - **Total Level**: `{attack_icon} {base_level + modifier}` 
     - **Modifier**: `({sign}{modifier})`
   - Example: "Offense 58 (+3)" where 58 = global base (55) + modifier (3)
   - **Global Level Setting**:
     - Editable base level control (default: 55, current max)
     - Located in settings/preferences or as global control on planner pages
     - Applies to all Identity/E.G.O skill calculations site-wide

5. **Attack Weight** (green box):
   - Label: "Attack Weight:"
   - Visual: Filled squares (e.g., "■ ■ ■" for weight 3)

6. **Skill Description** (cyan box):
   - **Base Description** (blue box): Main skill effect text
   - **Coin Effects** (repeating purple + pink box pairs):
     - **Coin Number Icon** (purple box): "Coin 1", "Coin 2", etc.
     - **Coin Effect Text** (pink box): Per-coin mechanics
   - Stack pairs vertically in `<div>` containers

**Passive Skills Panel** (bottom-right, blue box):

Two sub-sections stacked vertically:

1. **Passive** (white box header):
   - Can have multiple passives (stack vertically)
   - Each passive (black box) contains:
     - **Passive Name** (red box): Text with background pill
     - **Activation Condition** (orange box):
       - Format: `{sin_icon} x{count} {own/res}`
       - Multiple sins possible: "Wrath x3 Pride x2 Res"
     - **Passive Effect** (yellow box): Description text

2. **Support Passive** (white box header):
   - Same structure as Passive
   - Applies when this Identity is in reserve

**Sanity Panel** (bottom-left, blue box):

1. **Panic Type** (red box):
   - Fixed label: "Panic Type"
   - Icon (pink box) + Name (cyan box)
   - Description (purple box)

2. **Sanity Gain Condition** (orange box):
   - Fixed label: "Sanity Increment Condition"
   - Description (purple box)

3. **Sanity Loss Condition** (yellow box):
   - Fixed label: "Sanity Decrement Condition"
   - Description (purple box)

## Research
- Create a text UI mock up with identifier first - this is not included to the hard line limit. Prioritize this.

## Scope
- `ego info pc.png`
- `/frontend/src/routes/IdentityDetailPage.tsx`

## Target Code Area
- `/frontend/src/routes/IdentityDetailPage.tsx`
- `/frontend/src/components/`

## Testing Guidelines