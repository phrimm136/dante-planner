# 163 numeric-and-glyph-text-centres-on-its-ink
epic: none · pr: none

## Decisions
- @skill @typography — Coin-scale and base-power numbers draw in a numeral face whose ascent is overridden to the digits' ink top (0.7 em in PlannerNumerals) and whose descent and line gap are zero, and centre on their client anchor by translation alone. TextMeshPro's geometry alignment centres the union of the glyph boxes, while the web font carries a VDMX table from which Chromium takes whole-pixel ascent and descent per size, so a shift computed from the font asset's metrics moved the ink the wrong way (the base power sat about 25 units above its anchor). REJECTED: a shift in em from the asset's ascender and descender — the browser's line box does not use them; REJECTED: a per-platform em constant — Linux, Windows and macOS read different line metrics.
  NON-GOAL: browsers that ignore ascent-override, where the ink sits about 0.08 em high.
- @skill @typography — The attack-weight count draws as KOTRA "■" glyphs, one per target, auto-sized by the client's rule (at most 50, within the 49.5-unit text height and the 377.4-unit width, at least 15), with 0.05 em spacing, a line box of the face's ascent plus descent, and the name's underlay. The client's count is a TextMeshPro node over the glyph, not an image, and an image row was 13 % small with no shadow. REJECTED: keeping the square image resized — it cannot carry the underlay material or the glyph's spacing.

## Takeaway
- takeaway: when the engine centres ink and the browser centres a line box, make the line box the ink rather than computing a shift from metrics the browser does not read.
