# Image Analyzer Skill

Analyze image files at the pixel level using Python PIL before making any CSS or code changes.

## When to Use

- Measuring border thickness, padding, or margins in UI images
- Calculating slice values for 9-slice scaling (border-image)
- Comparing dimensions or structure between multiple images
- Setting precise CSS values for image-based UI elements
- Any task requiring exact pixel measurements

## Requirements

**ALWAYS use Python PIL to analyze images. NEVER guess or estimate pixel values.**

## Analysis Approach

1. **Load the image** with PIL and convert to RGBA
2. **Print pixel data** along center row/column to identify:
   - Border regions (color/alpha transitions)
   - Content regions
   - Transparent/semi-transparent areas (shimmer, glow effects)
3. **Identify transitions** where colors change significantly
4. **Calculate values** based on pixel positions of transitions

## Key Measurements

- **Image dimensions**: Total width and height
- **Border thickness**: Distance from edge to content area
- **Inner content area**: Region between borders
- **Glow/shimmer extent**: Semi-transparent pixels beyond the main border

## Output Format

Present findings as a table:

| Image | Dimensions | Top Border | Bottom Border | Inner Height |
|-------|------------|------------|---------------|--------------|
| file1 | WxH | Npx | Npx | Npx |
| file2 | WxH | Npx | Npx | Npx |

## Application to CSS

Based on analysis:

- `border-image-slice`: Pixels to cut from source image edges
- `border-image-width`: Rendered border width
- `border-image-outset`: Extension beyond element box (for glow effects)
