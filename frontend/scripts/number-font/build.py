"""Build PlannerNumerals-Regular.ttf from the clean-room numeral spec.

Every glyph is a set of closed polygons whose vertices may carry a circular
corner radius. Contours are written directly (no boolean library): outer
contours clockwise, counters counter-clockwise, as TrueType expects.
Coordinates are ink-local (x = 0 is the left ink edge); the left side
bearing is applied when the glyph is drawn.

REVISION 1 (spec "Revision 1", 2026-09-30):
- Digit advance 395 -> 400 (LSB 40 -> 42, RSB 43).
- 7: narrow advance 336 (LSB 10, RSB 11); kerning 6/7 and 7/8 and the GPOS
  table removed. Stem 82..200 -> 91..205; diagonal lean 1:2.9 -> 1:3, outer
  edge from (W, 627) to (205, 297), inner edge from (185, 592) to (91, 310);
  top bar bottom 591 -> 592.
- 1: RSB 47 -> 41 (advance 270 -> 264).
- 2: the band's two convex joins (x = W with the lower edge, x = 0 with the
  upper edge) are rounded, radius 60; concave joins stay sharp.
- 3: the right waist notch is a radius-65 round tangent to x = W at y 435,
  cut by a horizontal shelf at y 375 (was a straight chamfer 427 -> 371).

REVISION 2 (spec "Revision 2", 2026-10-01):
- New glyphs L (U+004C), v (U+0076) and period (U+002E), all square-cornered.
- Space advance 250 confirmed by the spec (was a free choice).
"""

import math
from pathlib import Path

from fontTools.fontBuilder import FontBuilder
from fontTools.misc.timeTools import timestampNow
from fontTools.pens.ttGlyphPen import TTGlyphPen

OUT = Path(__file__).resolve().parents[2] / "src/assets/fonts/planner-numerals.ttf"

# Global metrics (spec section 1)
UPM = 1000
DIGIT_HEIGHT = 700  # H
ASCENDER = 900
DESCENDER = -100
DIGIT_WIDTH = 315  # W, standard ink width
STEM = 115  # S, vertical stem
BAR = 112  # T, outer horizontal bar
MID_BAR = 122  # M, middle bar
MID_BAR_5 = 97  # M5, the 5's thinner middle bar
CORNER_RADIUS = 80  # R, circular outer rounds; counters are sharp
CUT_SLOPE = 0.5  # k, oblique terminal cut, rising left to right

# Spacing (spec section 1, "Spacing")
FIGURE_ADVANCE = 400
FIGURE_LSB = 42
SEVEN_ADVANCE = 336
SEVEN_LSB = 10
ONE_ADVANCE = 264
ONE_LSB = 22
PLUS_ADVANCE = 560
PLUS_LSB = 46
HYPHEN_ADVANCE = 300
HYPHEN_LSB = 47
SPACE_ADVANCE = 250
L_ADVANCE = 328
L_LSB = 45
V_ADVANCE = 316
V_LSB = -9  # only RSB(L) + LSB(v) = 4 is measured; the split keeps v symmetric
PERIOD_ADVANCE = 181
PERIOD_LSB = 22
NOTDEF_ADVANCE = 500

H, W, S, T, R, k = DIGIT_HEIGHT, DIGIT_WIDTH, STEM, BAR, CORNER_RADIUS, CUT_SLOPE
TOP_BAR_BOTTOM = H - T  # 588
INNER_RIGHT = W - S  # 200


def cut_rise(x0, y0, x1):
    """y on a k-slope line through (x0, y0), evaluated at x1."""
    return y0 + k * (x1 - x0)


# ---------------------------------------------------------------- primitives
# A polygon is a list of vertices (x, y), (x, y, radius) for a rounded
# corner, or (x, y, 0, False) for an explicit quadratic off-curve point.


def circular_arc(cx, cy, r, a0, a1):
    """On/off-curve points of a circular arc from angle a0 to a1 (radians),
    as two quadratic segments."""
    sweep = a1 - a0
    ctrl = r / math.cos(sweep / 4)
    at = lambda a, d: (cx + d * math.cos(a), cy + d * math.sin(a))
    return [
        (*at(a0, r), 0), (*at(a0 + sweep / 4, ctrl), 0, False),
        (*at(a0 + sweep / 2, r), 0), (*at(a0 + 3 * sweep / 4, ctrl), 0, False),
        (*at(a1, r), 0),
    ]


def rect(x0, y0, x1, y1):
    return [(x0, y0), (x1, y0), (x1, y1), (x0, y1)]


def rounded_rect(x0, y0, x1, y1, r=R):
    return [(x0, y0, r), (x1, y0, r), (x1, y1, r), (x0, y1, r)]


def signed_area(poly):
    a = 0.0
    for i, p in enumerate(poly):
        q = poly[(i + 1) % len(poly)]
        a += p[0] * q[1] - q[0] * p[1]
    return a / 2


def oriented(poly, clockwise):
    """Return poly with the requested direction (y up: clockwise = negative area)."""
    is_cw = signed_area(poly) < 0
    return poly if is_cw == clockwise else list(reversed(poly))


def _unit(ax, ay):
    n = math.hypot(ax, ay)
    return ax / n, ay / n


def draw_polygon(pen, poly, dx):
    """Draw one contour; rounded vertices become a circular arc of two quadratics."""
    pts = []  # (x, y, on_curve)
    n = len(poly)
    for i, v in enumerate(poly):
        x, y = v[0], v[1]
        if len(v) > 3 and v[3] is False:
            pts.append((x, y, False))
            continue
        r = v[2] if len(v) > 2 else 0
        if not r:
            pts.append((x, y, True))
            continue
        p, q = poly[i - 1], poly[(i + 1) % n]
        ux, uy = _unit(p[0] - x, p[1] - y)
        wx, wy = _unit(q[0] - x, q[1] - y)
        half = math.acos(max(-1.0, min(1.0, ux * wx + uy * wy))) / 2
        tangent = r / math.tan(half)
        t1 = (x + ux * tangent, y + uy * tangent)
        t2 = (x + wx * tangent, y + wy * tangent)
        bx, by = _unit(ux + wx, uy + wy)
        cx, cy = x + bx * r / math.sin(half), y + by * r / math.sin(half)
        a1 = math.atan2(t1[1] - cy, t1[0] - cx)
        a2 = math.atan2(t2[1] - cy, t2[0] - cx)
        sweep = (a2 - a1 + math.pi) % (2 * math.pi) - math.pi
        # Two quadratic segments; each control sits on the bisector at r / cos(sweep/4).
        pts.append((t1[0], t1[1], True))
        ctrl_dist = r / math.cos(sweep / 4)
        for j in (1, 3):
            a = a1 + sweep * j / 4
            pts.append((cx + ctrl_dist * math.cos(a), cy + ctrl_dist * math.sin(a), False))
            if j == 1:
                a = a1 + sweep / 2
                pts.append((cx + r * math.cos(a), cy + r * math.sin(a), True))
        pts.append((t2[0], t2[1], True))

    pts = [(round(x + dx), round(y), on) for x, y, on in pts]
    # Drop consecutive duplicates (a corner tangent point can coincide with a neighbour).
    dedup = []
    for p in pts:
        if not dedup or (p[0], p[1]) != (dedup[-1][0], dedup[-1][1]):
            dedup.append(p)
    if (dedup[0][0], dedup[0][1]) == (dedup[-1][0], dedup[-1][1]):
        dedup.pop()
    start = next(i for i, p in enumerate(dedup) if p[2])
    dedup = dedup[start:] + dedup[:start]
    pen.moveTo(dedup[0][:2])
    i = 1
    while i < len(dedup):
        if dedup[i][2]:
            pen.lineTo(dedup[i][:2])
            i += 1
        else:
            pen.qCurveTo(dedup[i][:2], dedup[i + 1][:2])
            i += 2
    pen.closePath()


# ---------------------------------------------------------------- glyphs
# Each returns (outers, counters), ink-local coordinates.


def glyph_zero():
    return [rounded_rect(0, 0, W, H)], [rect(S, T, W - S, H - T)]


def glyph_one():
    stem_left, stem_right = 86, 201
    flag_low, flag_high = 519, 645
    return [[
        (stem_left, 0), (stem_right, 0), (stem_right, H),
        ((H - flag_high) / k, H),  # top edge meets the cap line inside the stem
        (0, flag_high), (0, flag_low),
        (stem_left, cut_rise(0, flag_low, stem_left)),
    ]], []


def glyph_two():
    band_upper_y0 = 340  # upper diagonal edge at x = 0
    band_lower_y0 = band_upper_y0 - (MID_BAR + 1)  # 216.5 + 0.5 x; the spec's vertical thickness 123
    terminal_low = 479
    join_radius = 60  # Rev 1: convex band joins are round
    return [[
        (0, 0), (W, 0), (W, T), (S, T),
        (S, band_lower_y0 + k * S),
        (W, band_lower_y0 + k * W, join_radius),
        (W, H, R), (0, H, R),
        (0, terminal_low), (S, cut_rise(0, terminal_low, S)),
        (S, TOP_BAR_BOTTOM), (INNER_RIGHT, TOP_BAR_BOTTOM),
        (INNER_RIGHT, band_upper_y0 + k * INNER_RIGHT),
        (0, band_upper_y0, join_radius),
    ]], []


def glyph_three():
    mid_low, mid_high = 346, 468
    # Rev 1: the notch is the upper half's bottom-right round, cut by a shelf.
    notch_radius, notch_tangent_y, notch_shelf = 65, 435, 375
    cx, cy = W - notch_radius, notch_tangent_y
    shelf_angle = math.atan2(notch_shelf - cy, math.sqrt(notch_radius**2 - (notch_shelf - cy) ** 2))
    top_terminal, bottom_terminal = 487, 160
    return [[
        (0, 0, R), (W, 0, R),
        (W, notch_shelf),
        *circular_arc(cx, cy, notch_radius, shelf_angle, 0),
        (W, H, R), (0, H, R),
        (0, top_terminal), (S, cut_rise(0, top_terminal, S)),
        (S, TOP_BAR_BOTTOM), (INNER_RIGHT, TOP_BAR_BOTTOM),
        (INNER_RIGHT, mid_high), (S, mid_high), (S, mid_low), (INNER_RIGHT, mid_low),
        (INNER_RIGHT, T), (S, T),
        (S, cut_rise(0, bottom_terminal, S)), (0, bottom_terminal),
    ]], []


def glyph_four():
    cross_low = 258
    cross_high = cross_low + MID_BAR + 1  # 381 per spec
    right_top_low = 515
    return [[
        (0, cross_low), (0, H), (S, H), (S, cross_high),
        (INNER_RIGHT, cross_high), (INNER_RIGHT, right_top_low),
        (W, cut_rise(INNER_RIGHT, right_top_low, W)),
        (W, 0), (INNER_RIGHT, 0), (INNER_RIGHT, cross_low),
    ]], []


def glyph_five():
    mid_low = 346
    mid_high = mid_low + MID_BAR_5
    terminal = 129
    return [[
        (0, H), (W, H), (W, TOP_BAR_BOTTOM), (S, TOP_BAR_BOTTOM),
        (S, mid_high), (W, mid_high),
        (W, 0, R), (0, 0, R),
        (0, terminal), (S, cut_rise(0, terminal, S)),
        (S, T), (INNER_RIGHT, T), (INNER_RIGHT, mid_low), (0, mid_low),
    ]], []


def glyph_six():
    mid_low, mid_high = 258, 381
    terminal = 495
    return [[
        (0, 0, R), (W, 0, R), (W, mid_high), (S, mid_high),
        (S, TOP_BAR_BOTTOM), (INNER_RIGHT, TOP_BAR_BOTTOM),
        (INNER_RIGHT, terminal), (W, cut_rise(INNER_RIGHT, terminal, W)),
        (W, H, R), (0, H, R),
    ]], [rect(S, T, W - S, mid_low)]


def glyph_seven():
    top_bar_low = 592
    shear_top = 627
    lean = 1 / 3  # dx per dy
    stem_left, stem_right = 91, 205
    inner_top_x = 185
    outer_kink = shear_top - (W - stem_right) / lean  # 297
    inner_kink = top_bar_low - (inner_top_x - stem_left) / lean  # 310
    return [[
        (0, H), (W, H), (W, shear_top), (stem_right, outer_kink), (stem_right, 0),
        (stem_left, 0), (stem_left, inner_kink), (inner_top_x, top_bar_low), (0, top_bar_low),
    ]], []


def glyph_eight():
    right_notch_top, right_notch_shelf, right_notch_depth = 406, 354, 43
    left_notch_shelf, left_notch_bottom, left_notch_depth = 363, 306, 38
    upper_floor = 396
    lower_ceiling = 277
    outer = [
        (0, 0, R), (W, 0, R),
        (W, right_notch_shelf), (W - right_notch_depth, right_notch_shelf), (W, right_notch_top),
        (W, H, R), (0, H, R),
        (0, left_notch_shelf), (left_notch_depth, left_notch_shelf), (0, left_notch_bottom),
    ]
    upper = [(S, upper_floor), (INNER_RIGHT, cut_rise(S, upper_floor, INNER_RIGHT)),
             (INNER_RIGHT, H - T), (S, H - T)]
    lower = [(S, T), (INNER_RIGHT, T),
             (INNER_RIGHT, cut_rise(S, lower_ceiling, INNER_RIGHT)), (S, lower_ceiling)]
    return [outer], [upper, lower]


def glyph_nine():
    mid_low, mid_high = 328, 451
    terminal = 155
    return [[
        (0, 0, R), (W, 0, R), (W, H, R), (0, H, R),
        (0, mid_low), (INNER_RIGHT, mid_low), (INNER_RIGHT, T), (S, T),
        (S, cut_rise(0, terminal, S)), (0, terminal),
    ]], [rect(S, mid_high, W - S, TOP_BAR_BOTTOM)]


PLUS_WIDTH = 468
PLUS_ARM_LOW, PLUS_ARM_HIGH = 206, 302
PLUS_STEM_LEFT, PLUS_STEM_RIGHT = 184, 284  # spec 184.5..283.5, snapped to centre on 234
PLUS_END_RISE = 36  # ~20 deg across the stem
PLUS_TOP_LOW = 511


def glyph_plus():
    l, r = PLUS_STEM_LEFT, PLUS_STEM_RIGHT
    return [[
        (l, 0), (r, PLUS_END_RISE), (r, PLUS_ARM_LOW), (PLUS_WIDTH, PLUS_ARM_LOW),
        (PLUS_WIDTH, PLUS_ARM_HIGH), (r, PLUS_ARM_HIGH), (r, PLUS_TOP_LOW + PLUS_END_RISE),
        (l, PLUS_TOP_LOW), (l, PLUS_ARM_HIGH), (0, PLUS_ARM_HIGH), (0, PLUS_ARM_LOW),
        (l, PLUS_ARM_LOW),
    ]], []


def glyph_minus():
    return [rect(0, PLUS_ARM_LOW, PLUS_WIDTH, PLUS_ARM_HIGH)], []


def glyph_hyphen():
    width, low, thickness = 206, 189, 102
    return [[(0, low), (0, low + thickness),
             (width, low + thickness + k * width), (width, low + k * width)]], []


def glyph_L():
    stem, foot_width, foot_bar = 116, 270, 111  # spec: ~S and ~T
    return [[(0, 0), (foot_width, 0), (foot_width, foot_bar), (stem, foot_bar), (stem, H), (0, H)]], []


def glyph_v():
    height = 562
    width = 334
    foot_left, foot_right = 100, 234  # outer edges lean 10.1 deg to a flat foot
    notch_left, notch_right = 112, 222  # inner edges lean 7.8 deg
    crotch = 158
    return [[
        (0, height), (notch_left, height), (width / 2, crotch), (notch_right, height),
        (width, height), (foot_right, 0), (foot_left, 0),
    ]], []


def glyph_period():
    width, height = 114, 116  # ~S x S
    return [rect(0, 0, width, height)], []


def glyph_notdef():
    return [rect(0, 0, 400, H)], [rect(50, 50, 350, H - 50)]


def glyph_space():
    return [], []


# name, codepoint, builder, advance, lsb
GLYPHS = [
    (".notdef", None, glyph_notdef, NOTDEF_ADVANCE, 50),
    ("space", 0x20, glyph_space, SPACE_ADVANCE, 0),
    ("plus", 0x2B, glyph_plus, PLUS_ADVANCE, PLUS_LSB),
    ("hyphen", 0x2D, glyph_hyphen, HYPHEN_ADVANCE, HYPHEN_LSB),
    ("period", 0x2E, glyph_period, PERIOD_ADVANCE, PERIOD_LSB),
    ("zero", 0x30, glyph_zero, FIGURE_ADVANCE, FIGURE_LSB),
    ("one", 0x31, glyph_one, ONE_ADVANCE, ONE_LSB),
    ("two", 0x32, glyph_two, FIGURE_ADVANCE, FIGURE_LSB),
    ("three", 0x33, glyph_three, FIGURE_ADVANCE, FIGURE_LSB),
    ("four", 0x34, glyph_four, FIGURE_ADVANCE, FIGURE_LSB),
    ("five", 0x35, glyph_five, FIGURE_ADVANCE, FIGURE_LSB),
    ("six", 0x36, glyph_six, FIGURE_ADVANCE, FIGURE_LSB),
    ("seven", 0x37, glyph_seven, SEVEN_ADVANCE, SEVEN_LSB),
    ("eight", 0x38, glyph_eight, FIGURE_ADVANCE, FIGURE_LSB),
    ("nine", 0x39, glyph_nine, FIGURE_ADVANCE, FIGURE_LSB),
    ("L", 0x4C, glyph_L, L_ADVANCE, L_LSB),
    ("v", 0x76, glyph_v, V_ADVANCE, V_LSB),
    ("minus", 0x2212, glyph_minus, PLUS_ADVANCE, PLUS_LSB),
]

NAMES = {
    "copyright": "Copyright 2026 Dante's Planner contributors",
    "familyName": "PlannerNumerals",
    "styleName": "Regular",
    "uniqueFontIdentifier": "1.000;PlannerNumerals-Regular",
    "fullName": "PlannerNumerals Regular",
    "version": "Version 1.000",
    "psName": "PlannerNumerals-Regular",
    "licenseDescription": "This Font Software is licensed under the SIL Open Font License, Version 1.1.",
    "licenseInfoURL": "https://openfontlicense.org",
}


def build():
    order = [g[0] for g in GLYPHS]
    fb = FontBuilder(UPM, isTTF=True)
    fb.setupGlyphOrder(order)
    fb.setupCharacterMap({cp: name for name, cp, *_ in GLYPHS if cp is not None})

    glyf, metrics = {}, {}
    for name, _, builder, advance, lsb in GLYPHS:
        outers, counters = builder()
        pen = TTGlyphPen(None)
        for poly in outers:
            draw_polygon(pen, oriented(poly, clockwise=True), lsb)
        for poly in counters:
            draw_polygon(pen, oriented(poly, clockwise=False), lsb)
        glyf[name] = pen.glyph()
        metrics[name] = (advance, lsb)
    fb.setupGlyf(glyf)
    # lsb from the actual bounds, so hmtx matches glyf exactly
    glyf_table = fb.font["glyf"]
    fb.setupHorizontalMetrics({
        n: (adv, getattr(glyf_table[n], "xMin", 0)) for n, (adv, _) in metrics.items()
    })
    fb.setupHorizontalHeader(ascent=ASCENDER, descent=DESCENDER)
    fb.setupNameTable(NAMES)
    fb.setupOS2(
        version=4,
        sTypoAscender=ASCENDER, sTypoDescender=DESCENDER, sTypoLineGap=0,
        usWinAscent=ASCENDER, usWinDescent=-DESCENDER,
        sCapHeight=DIGIT_HEIGHT, sxHeight=0,
        fsSelection=0x0040 | 0x0080,  # REGULAR | USE_TYPO_METRICS
        achVendID="NONE", fsType=0,
    )
    fb.setupPost()
    now = timestampNow()
    fb.setupHead(unitsPerEm=UPM, created=now, modified=now)
    fb.save(OUT)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    build()
