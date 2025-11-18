# Task: UI Mock Up

## Description
Build a page for details about an ego gift. See `ego gift info pc.png`:

**Components**:

1. **Gift Image** (red box): Large centered artwork

2. **Gift Name** (orange box): Title text

3. **Cost** (yellow box):
   - Layout: `{cost_icon} {cost_value}`

4. **Enhancement Descriptions** (green box):
   - Three panels (gray boxes) for enhancement levels 0, 1, 2
   - Each panel contains:
     - **Enhancement Icon** (cyan box): Level indicator (0, +1, +2)
     - **Enhancement Cost** (pink box): Additional cost for this level
     - **Description** (purple box): Effect at this enhancement level

5. **Acquisition Method** (blue box):
   - Text explaining how to obtain this gift (e.g., "Theme Pack: Sweeper")

## Research
- Create a text version of layout so that the AI can use it and image for layout composition.
- Leave a name for each component and make the contents empty; will be filled later

## Scope
- `ego gift info pc.png`
- `/frontend/src/components/gift`
- `/frontend/src/routes/`

## Target Code Areas
- `/frontend/src/components/gift`
- `/frontend/src/routes/`

## Testing Guidelines