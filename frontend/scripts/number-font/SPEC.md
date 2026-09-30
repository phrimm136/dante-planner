# Condensed squared numerals: design specification

Clean-room spec. Every number here was measured on raster images only, and no font file,
font asset or atlas was opened. Sources:

- **R**: the top glyph row of `excelsior-replacement/comparison.png`
  (`+0123456789 -9 +12 88`, black on white, digit height 80 px, sharp and antialiased). This is
  the main measurement source.
- **G**: the in-game capture `~/Downloads/Screenshot_20260930_141504_LimbusCompany.png`. It has
  `LV 85` (digits 81 px tall), the coin `+3` (32 px) and base power `3` (53 px). Its SDF
  rendering, outline and drop shadow soften the shapes, so it was used to confirm features, not
  to measure them.

Measurement method: subpixel 50%-coverage edge crossings and least-squares line fits on R. One
R pixel is 1/80 H, which is 8.75 units, so any single dimension below is good to about ±5 units.
Features smaller than about 10 units cannot be resolved (see "Unresolvable").

## 1. Global metrics

| Parameter | Value (units) | Ratio of H | Evidence |
|---|---|---|---|
| Units per em | 1000 | | chosen |
| Digit height **H** (cap = figure height) | 700 | 1.000 | chosen; every figure spans exactly 0–H |
| Baseline | 0 | | |
| Suggested ascender / descender | 900 / −100 | | chosen; not observable |
| Standard digit ink width **W** | 315 | 0.450 | R: 35.9 px. G LV85: 0.44 H |
| Vertical stem **S** | 115 | 0.164 | R: 13.1 px on 0, 4, 8 |
| Outer horizontal bar **T** (top and bottom bars) | 112 | 0.160 | R: 0.156–0.163 H |
| Middle bar **M** (3, 4, 6, 9, and the vertical thickness of the 2 and 8 diagonals) | 122 | 0.175 | R: 0.175 H on all five |
| Middle bar of 5 only (**M5**) | 97 | 0.138 | R: 0.494–0.632 H. G agrees (thinner than the other middles) |
| Counter width (W − 2S) | 85 | 0.121 | R: 9–10 px |
| Outer corner radius **R** (circular quarter arc) | 80 | 0.1125 | R: arc fit r ≈ 9 px. Tested r = 8 and 9 row by row; 9 fits |
| Inner (counter) corner radius | 0 | | R: counters are sharp rectangles. G shows ≤ 1–2 px softening, which is SDF blur |
| Oblique cut slope **k** | 1 : 2 (26.6° from horizontal) | | R: 12 independent fits, 25.5–28.4° |
| Overshoot | none | | R: round and flat figures share the same top and bottom rows to within 0.5 px |
| Contrast | near-monoline; horizontals ≈ 0.97 × stems, middles ≈ 1.06 × stems | | |

**Corner rule.** A corner is either a circular round of R = 80 or dead square. There are no
chamfered outer corners. Every "cut" in the design is an oblique terminal at slope k, rising
left to right: the low end is on the left and the high end on the right, on every glyph. The
only exceptions are the 7 diagonal (about 19° from vertical) and the plus ends (about 20°).

**Spacing.**

- **[Rev 1 changes this]** Figures 0 and 2–9 are **tabular**: advance 395, LSB 40, RSB 40. In R the gap between
  neighbouring figures is 9.1 px, which gives these sidebearings.
- **[Rev 1 changes this]** **1 is proportional**: advance 270, LSB 22, ink 201, RSB 47. R measured gaps of 7.0 px before
  the 1 and 8.7–9.6 px after it.
- **[resolved in Rev 1]** **7 anomaly (unresolved).** In R, the gaps 6→7 and 7→8 are 7.3 and 6.2 px, where 9.1 is
  standard. Either the 7 is about 40 units narrower, or those pairs are kerned. One string
  cannot tell the two apart. The recommendation is to keep the 7 tabular and add optional kerns
  of 6/7 −15 and 7/8 −25.

## 2. Per-glyph construction (v0; items marked [Rev 1] are replaced in Revision 1)

Coordinates are (x, y) in units. x = 0 is the glyph's left ink edge and y = 0 is the baseline.
"Outer" means the side at x = 0 or x = W. Quoted fractions are fractions of H.

### 0
- Rounded rectangle 0..W × 0..H, with all four corners at R.
- Counter: a sharp rectangle x S..W−S, y T..H−T (0.16–0.84), 85 × 476.

### 1
- Right-aligned stem x 86..201, 0..H, with square foot and square top-right corner. There is no
  base serif.
- The flag is a parallelogram hanging off the left of the stem:
  - Vertical left edge at x = 0, from y 519 (0.741) to 645 (0.922). That is 126 tall, which is
    slightly more than a stem width.
  - Top edge from (0, 645) up at slope k to the cap line at x ≈ 110, which is about 25 units
    inside the stem, so the stem has no top-left corner.
  - Bottom edge parallel to the top, from (0, 519) to the stem at (86, 562) (0.80).
  - The flag sticks out 86 (0.12 H).

### 2
- Top bar 0..W at y 588..700. Top-left and top-right corners are R.
- **Top-left terminal**: the left arm drops from the top bar and ends in a k-cut from (0, 479)
  (0.684) up to (S, 536) (0.766). The aperture on the left side is open from y 340 to 479.
- Right arm x W−S..W, going down from the top bar into the diagonal.
- **Diagonal band**: two parallel lines at slope k.
  - Upper edge: y = 340 + 0.5x. It meets the left edge at 0.486 and the right arm's inner edge
    at 0.63.
  - Lower edge: y = 216.5 + 0.5x. It meets the left stem's inner edge at 0.392 and the right
    outer edge at 0.537.
  - Vertical thickness 123 (M).
- Left stem x 0..S from the band down to the base.
- **Base bar** 0..W at 0..112, with square bottom corners (the only square-bottomed round-top
  figure).
- **[Rev 1 replaces this]** R shows a softened corner of ≤ 3 px (≤ 25 units) where the band meets the left edge and where
  the right arm meets the band. This detail is optional.

### 3
- Top bar and bottom bar, each 0..W × T, with outer rounds R: top-left, top-right, bottom-left,
  bottom-right.
- Right stem runs the full height.
- **Top-left terminal**: k-cut from (0, 487) (0.695) to (S, 544) (0.777).
- **Bottom-left terminal**: k-cut from (0, 160) (0.229) to (S, 218) (0.311). The lower terminal
  is a copy of the upper one, shifted down; it is not a mirror image.
- **Middle bar**: x S..W (it starts at the stem line, not at x = 0), y 346..468 (0.494–0.669,
  centre 0.58), square left end. The upper counter (0.669–0.84) is much shorter than the lower
  one (0.16–0.494).
- **[Rev 1 replaces this]** **Waist notch** on the outer right edge: a triangular bite. It chamfers inward from (W, 427)
  (0.61) to (W−39, 371) (0.53), then a horizontal shelf returns to W at 371. Depth is 0.055 H.
  G's base-power 3 clearly shows the same notch.

### 4
- Open top. There is no diagonal, and the two uprights are separate.
- Left upright x 0..S, from H down to y 258 (0.369). Its top is square and its foot ends at the
  crossbar.
- **Crossbar** 0..W, y 258..381 (0.369–0.544, M). Square ends.
- Right upright x W−S..W, from the baseline up to a k-cut top, from (W−S, 515) (0.735) to
  (W, 573) (0.819). It is shorter than H by 0.18.
- Counter between the uprights: 85 wide, open upward from 0.544.
- All corners are square.

### 5
- Top bar 0..W × T, with square top-left and top-right corners.
- Left stem from H down to the middle bar.
- **Middle bar** 0..W, y 346..443 (M5, thinner than the others). Its right end forms a square
  top-right corner of the bowl.
- Right stem from 443 down. Bottom bar with bottom-left and bottom-right corners at R.
- **Bottom-left terminal**: k-cut from (0, 129) (0.184) to (S, 186) (0.266).
- Upper open area: x S..W, y 443..588.

### 6
- Top bar with top-left and top-right corners at R. Bottom bar with bottom-left and
  bottom-right corners at R. Left stem runs the full height.
- **Top-right terminal**: the right arm descends from the top bar and ends in a k-cut from
  (W−S, 495) (0.707) to (W, 554) (0.791).
- **Middle bar** 0..W, y 258..381 (the same height as the 4's crossbar). The bowl's top-right
  corner at y 381 is square.
- Lower counter: S..W−S × 112..258.

### 7 [Rev 1 replaces the diagonal, stem and spacing]
- Top bar 0..W, y 591..700. Square top-left, top-right and bottom-left corners.
- **Diagonal**, leaning toward the lower left at about 19° from vertical (slope 1 : 2.9):
  - Outer (right) edge from (W, 630) (0.90) straight down to (200, 311). This means the top
    bar's right end is sheared off for its bottom 70 units.
  - Inner (left) edge from (183, 591) to (82, 311).
- **Vertical stem** x 82..200 (about centred), from the kink at y 311 (0.444) to the baseline.
  The 7 is "bent", not a single straight diagonal. Square foot.
- See the spacing anomaly in section 1.

### 8
- Rounded rectangle 0..W × 0..H, with all four corners at R.
- **Upper counter**: x S..W−S. Its floor is a k-slope rising to the right, from (S, 396) (0.565)
  to (W−S, 438) (0.625). Its top is at H−T.
- **Lower counter**: x S..W−S from the base bar up to a ceiling that rises to the right, from
  (S, 277) (0.396) to (W−S, 319) (0.455).
- The waist is therefore a k-slanted band with vertical thickness 119 (≈ M).
- **Outer waist notches** (small bites that mark the two stacked boxes):
  - Right: chamfer inward from (W, 406) (0.58) to (W−43, 354) (0.506), then a horizontal shelf
    back out.
  - Left: horizontal shelf at y 363 (0.519) running in to x = 38, then a chamfer back out to
    (0, 306) (0.44).
  - Depth is 0.054 H. The right bite sits just above the joint and the left bite just below it.
    G's `LV 85` and `88` both show the notches.

### 9
- Top bar with top-left and top-right corners at R. Bottom bar with bottom-left and
  bottom-right corners at R. Right stem runs the full height.
- **Middle bar** 0..W, y 328..451 (0.469–0.644, centre 0.556), with a square left corner.
- Left arm from the middle bar up to the top. Upper counter: S..W−S × 451..588.
- **Bottom-left terminal**: k-cut from (0, 155) (0.222) to (S, 213) (0.304).
- The 9 is **not** a rotated 6. Its bar is at 0.556 against the 6's 0.456, and its lower
  terminal is lower than the 6's upper terminal would be after rotation.

### + (plus)
- Ink width 468 (0.669 H). Advance 560, sidebearings 46.
- **Horizontal arm**: 0..468, y 206..302. Thickness 96 (0.1375 H) and square ends. Its centre is
  at 0.36 H, which is **below** the figure midline; G's coin shows 0.34 H.
- **Vertical arm**: x 184.5..283.5 (thickness 99, 0.141 H), centred on the horizontal arm.
  - Bottom end is on the baseline: from (184.5, 0) up to (283.5, 36).
  - Top end: from (184.5, 511) to (283.5, 547) (0.78 H).
  - Both ends are cut parallel, rising to the right at about 20° (R fits 18.9° and 21.4°).
  - The vertical arm (547) is taller than the horizontal arm is wide (468). The plus sits on the
    baseline and does not reach cap height.
- The plus strokes are about 15% thinner than the figure stems.

### − (U+2212 minus): inferred, not in the reference
R has no U+2212 (its label says so), so this glyph is a proposal. It is the plus's horizontal
arm on its own: 0..468 × 206..302, square ends, advance 560. With this, `+3` and `−3` align and
take the same width.

### - (hyphen)
- A parallelogram with **vertical** ends and top and bottom edges at slope k, rising to the
  right:
  - Corner points (0, 189), (0, 291), (206, 394), (206, 292).
  - Ink width 206 (0.294 H). Vertical thickness 102 (0.146 H).
  - Centre at about 0.42 H: higher than the plus arm and lower than the figure midline.
- Advance 300, sidebearings 47. R's measured RSB was 5.2 px, which is about 46 units.

## 3. Proportion checks (sketch vs R, per glyph)
The sketches in `sketch.py` are drawn only from the numbers above. Their antialiased coverage
IoU against the matching R glyph, at best integer alignment, is:

| Glyph | + | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | - |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| IoU | 0.93 | 0.97 | 0.96 | 0.96 | 0.95 | 0.96 | 0.95 | 0.95 | 0.95 | 0.97 | 0.95 | 0.96 |

At an 80 px digit height, the edge antialiasing alone limits IoU to about 0.97. The residual
differences are 1 px fringes; overlays are in `sketch-overlay-per-glyph.png`.

## 4. Unresolvable at the reference resolution
- The exact outer corner curve: circle or superellipse, and radius 72–88.
- Whether counter corners carry a hairline radius (≤ 10 units).
- The softening where the 2's diagonal joins its stems.
- The 3 and 8 notch chamfer angles. They measure 45–55°, with ±1 px on a 4-px feature.
- Whether the 7 is narrower or kerned.
- The plus-end angle, which could be 18° or 22°.
- The minus, which is entirely inferred.
- Sidebearings to the left of the plus and the hyphen. Only their right-side gaps were
  observable, so these are assumed symmetric.

## Files
- `sketch.py`: parametric sketch built from this spec, using PIL polygons and rounded
  rectangles only.
- `sketch-glyphs.png`: each glyph with baseline (red), H (blue), H/2 (grey) and advance (green).
- `sketch-vs-reference.png`: R row vs sketch row at the same height, plus G crops next to
  sketches of `85`, `+3` and `3`.
- `sketch-overlay-per-glyph.png`: reference | sketch | overlay (red = reference only, blue =
  sketch only; crops include the neighbouring glyph on the right).

## Revision 1 (2026-09-30): the 7, and the lost curves on the 2 and 3

**Source.** The reference row of `compare-v1.png`. It shows the same reference at a larger size
(digit height 139 px, so 1 px ≈ 5 units), which resolves more than R did. The v0 values are
kept above, with each replaced item marked. This section is authoritative where the two
disagree.

### R1-a. Spacing (the 7 is narrow, not kerned)

| Item | v0 | Rev 1 | Evidence |
|---|---|---|---|
| Digit advance (0, 2–6, 8, 9) | 395 (LSB 40, RSB 40) | **400 (LSB 42, RSB 43)** | Mean figure-to-figure gap is 16.8 px, i.e. 84 units |
| **7 advance** | 395, plus optional kerns 6/7 −15 and 7/8 −25 | **336 (LSB 10, ink 315, RSB 11); no kerning** | Gaps 6→7 = 10.4 px and 7→8 = 10.5 px, against 16.8 standard. Both sides are about equally tight, so the 7 has a narrow advance: its ink sits almost flush with its advance box |
| 1 RSB | 47 (advance 270) | **41 (advance 264)** | The 1→2 gap is 16.5 px |

These numbers also account for the v1 build's 7 sitting too far right. The build kept the
tabular advance and applied the optional kerns, which leaves the 7's ink about 3 px (at 139 px)
right of the reference.

### R1-b. The 7's shape

| Item | v0 | Rev 1 |
|---|---|---|
| Stem x-range | 82..200 | **91..205** (width 114). The stem is about centred: its centre is at 0.47 W |
| Diagonal lean | about 19–20° from vertical (1 : 2.9) | **18.4° (1 : 3)**. Both edges are parallel; ref fits give 18.2° and 18.5° |
| Diagonal outer (right) edge | from (W, 630) to (200, 311) | **leaves the top bar's right edge at 0.896 H (627), 1:3 down to the stem's right edge at 0.424 H (297)** |
| Diagonal inner (left) edge | from (183, 591) to (82, 311) | **from under the top bar at x 185 (0.26 H from the left), 1:3 down to the stem's left edge at 0.443 H (310)** |
| Diagonal horizontal thickness | about 118 | 118 (unchanged; about 112 measured perpendicular, ≈ S) |
| Top bar | 0..W, 591..700 | 0..W, 592..700 (unchanged in effect) |
| Kink | sharp | sharp (unchanged); no curve at the diagonal-to-stem kink |

The 7's width is unchanged at W (the reference measured 0.6 px wider, within noise).

### R1-c. The 2: rounded convex joins (the "lost curve")
The v0 build drew the diagonal band's two **convex** junctions as sharp corners. In the
reference both are circular rounds:

- **Right join**: the right arm's outer edge (x = W) meets the band's lower edge (slope 1:2).
  - v0: a sharp corner at (W, 374).
  - **Rev 1: round of radius 60 (0.086 H)**, tangent to both edges. The round starts leaving
    the vertical at about 0.59 H and has blended into the 1:2 edge by about 0.50 H.
- **Left join**: the band's upper edge meets the left outer edge (x = 0).
  - v0: a sharp corner at (0, 340).
  - **Rev 1: round of radius 60**. The curve runs from about 0.52 H on the slope down to about
    0.43 H on the vertical.
- The two **concave** joins stay sharp: the band's upper edge meeting the right arm's inner
  edge, and the band's lower edge meeting the left stem's inner edge. The reference shows a
  one-pixel step there.
- Fit quality: a radius search over 0–100 in steps of 5 gives its best error at 60/60. Relative
  to sharp corners, that error is 36% lower.

### R1-d. The 3: the waist notch is a round, not a straight chamfer
- v0: a straight triangular chamfer from (W, 427) to (W−39, 371).
- **Rev 1**: the notch is the **bottom-right corner round of the upper half** of the 3:
  - **Radius 65 (0.093 H)**, tangent to the right edge at **0.62 H (435)**.
  - The round is cut off by a horizontal shelf at **0.536 H (375)**. The lower half's
    top-right corner at that shelf is square and flush with W.
  - Visible depth at the shelf is about 40 (0.057 H).
  - The profile is convex. It leaves the vertical gently and is steepest, about 60° from
    vertical, where it meets the shelf.
- Fit: best error at r 65–70 with tangent 0.62 H. That is 25–30% below the v0 triangle.
- The 3's middle-bar left end, its terminals and its counter corners stay sharp. Its outer
  rounds stay at R = 80, confirmed at 139 px.
- The 8's waist notches were rechecked at 139 px and match v0. They are not changed.

### Unchanged
Everything not listed in R1-a to R1-d is unchanged: the global metrics and all other glyphs.
Rev 1 sketches were compared against the 139 px reference. The 2, 3 and 7 now differ only by a
uniform subpixel offset of 1 px or less (`sketch.py` carries the Rev 1 geometry).

## Revision 2 (2026-10-01): L, v, period, space

**Source.** `reference-Lv.png` (text "Lv. 60  Lv.85  LvL v. 0Lv"). Digit height is 139 px, so
1 px ≈ 5.04 units. Digit widths there measure 62.8 px (316 units), consistent with W. Nothing in
Revisions 0–1 changes.

The shared style: all corners are square. There are no rounds; only the round-bowled digits
carry R. Stems and bars use the digit weights S and T.

### L
- **Stem**: x 0..116 (≈ S), running the full height 0..H. Flat square top.
- **Foot bar**: x 0..270, y 0..111 (≈ T, 0.159 H). Square outer corners and a sharp inner
  corner.
- Ink width 270 (0.386 H), narrower than a digit.
- Spacing: **LSB 45, RSB 13, advance 328**.

### v (lowercase, a tall x-height)
- Height **562 (0.803 H)**. Flat, horizontal top terminals and a flat foot, both square-cornered.
- Symmetric about x = 167. Ink width 334 (0.477 H), wider than a digit.
- **Outer edges** lean 10.1° from vertical (slope 0.178). They run from the top corners at x 0
  and 334 down to a **flat foot 134 wide (0.19 H)**, spanning x 100..234.
- **Inner edges** (the V counter) are steeper, 7.8° from vertical (slope 0.136). The notch
  opens 112 wide at the top (x 112..222) and closes in a **sharp crotch at 0.226 H (158)**.
- Each arm is therefore 112 wide horizontally at the top (≈ S) and tapers slightly toward the
  crotch (about 98 at 0.29 H).
- The ref fits agree left and right to 0.2°.
- Spacing: **LSB −9, RSB −9, advance 316**. The negative sidebearings let the wide top tuck
  against the L's foot. See the evidence below.

### . (period)
- A square block **x 0..114, y 0..116** (S × S, top at 0.166 H), sitting on the baseline.
  Square corners.
- Spacing: **LSB 22, RSB 45, advance 181**.

### Space
- **Advance 250 (0.357 H)**.

### Spacing evidence
Measured ink gaps, in px at 139 px digit height, set against the spec's predictions. Digit
sidebearings are 42 (Rev 1).

| Pair | Measured gap (px) | Spec prediction (px) |
|---|---|---|
| 0 → L | 17.4 | 17.3 |
| L → v (in "Lv") | 0.7 | 0.8 |
| L → space → v (in "L v") | 50.8 | 50.4 |
| v → L | 7.4 | 7.1 |
| v → . | 2.4 and 3.4 | 2.6 |
| . → 8 | 17.3 | 17.3 |
| . → space → 6 or 0 | 67.1 and 66.1 | 66.9 |

These gaps fit one consistent set of sidebearings, so no kerning is needed. One caveat: only
the sum RSB(L) + LSB(v) = 4 is observable. The split 13 / −9 is a choice; it keeps the v
symmetric.

### Sketch check
`sketch-Lv-vs-reference.png` sets "Lv. 60  Lv.85" from these numbers under the reference crop.
The shapes and spacing match by eye.
