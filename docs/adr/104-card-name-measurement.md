# 104 card-name-measurement
epic: none · pr: none · supersedes: 103 @cards @text @i18n

## Decisions
- @cards @text — A card name's width comes from a pure fit function over an injected measure whose
  advances are the game's own, shipped as static data per display face. The browser's answer for the
  same name depends on which face it actually resolved and on its own rounding, so one name broke on
  one line in a production build and on two in a dev server, on a margin of a hundredth of a percent.
  REJECTED: a canvas measurer built after the face has loaded — it still asks the browser, which is
  the thing that disagreed with itself.
- @cards @text — The tables are generated from the shipped `TMP_FontAsset` dumps for the faces the
  game ships and from the served TTF for the one it does not, and validated at load like every other
  static read. Both sources carry the same two numbers per glyph, so one generator can emit one
  shape. REJECTED: transcribing the metrics into the layout tables by hand — the advances are
  thousands of numbers per face, and a transcription cannot be regenerated when a client ships a new
  face.
- @cards @text @i18n — A code point the face has no glyph for is drawn at a full em when it is a CJK
  ideograph and at the table's mean advance otherwise. The display faces carry no hanja, and every
  face in the game's own fallback chain draws full-width CJK at exactly one em, so the fallback needs
  no table of its own. REJECTED: shipping the fallback faces' tables as well — four more tables to
  keep in step for one glyph class whose advance is fixed by construction.
- @cards @text — Letter spacing is charged after every code point of a line, including the last.
  TextMeshPro advances the pen by the character spacing on every glyph it sets, and the cards are
  drawn with CSS letter-spacing, which does the same. REJECTED: charging it only between neighbours —
  it is a hundredth of an em narrower per line, which is enough to move a break on a name whose own
  margin is that small.
- @cards @text — A line's ascender, descender and line height are the tallest of the faces whose
  glyphs that line actually carries, and the block is the first line's ascender, each line's own
  pitch, and the last line's descender. TextMeshPro lays each line out from the font assets that drew
  it, so one hanja on a line sets that whole line from the fallback face's taller metrics.
  REJECTED: one pitch for the block, from the display face — two names that differ only in which
  fallback face supplies their hanja then stop auto-sizing at the same size, and one of them breaks
  in a place the game does not.
- @cards @text — Auto-sizing steps down from the band's top and stops at the first size whose laid
  out lines fit the box, and never keeps shrinking towards a layout with fewer lines. TextMeshPro
  reduces the size only while the text overflows, so a name that fits on two lines at a smaller size
  is never offered the chance to become one line. REJECTED: scanning the whole band and taking any
  layout that fits — it collapses a two-line hanja name the game draws on two lines.
- @cards @text — A name block is centred on its box by the face's cap line, translated by
  `capLine / 2 - (ascender - descender) / 2` from where its line boxes centre. TextMeshPro's midline
  alignment centres the drawn geometry, which CSS has no measurement for, and the cap line is the
  face's own record of where its ink stops. REJECTED: centring the line boxes and leaving it —
  the ink then sits above the game's by most of a point of card height.
- @cards @text — The identity card's level line and the name's first-line offset are computed from the
  face metrics the browser reports, read off the rendered card, and not from the shipped advance
  table. A CSS line box derives its half leading from the ascent and descent Chrome takes from the
  font file's own OS/2 and hhea tables and rounds to whole pixels; the TextMeshPro asset's metrics
  disagree with those, and for `ExcelsiorSans SDF` carry a descent line above the baseline and an
  ascent 0.028em taller. REJECTED: deriving both from the advance table like every other vertical
  number — the table holds the face's own metrics, which is not what the browser lays the line box
  out from, and the digits then sit off the box edge by a point of card height.

- @cards @text — The name underlay's offsets are stored on CSS axes, y downward. The material's
  `_UnderlayOffsetY` is a UV offset with y upward, and a table that keeps the game's sign reads as a
  shadow thrown in the opposite direction from the one the card shows. REJECTED: keeping the game's
  sign and negating at the call site — every reader then has to know which axis the table is in.
- @cards — The identity hover ring is stretched over a rect derived per rank frame, so its opaque
  pixels land on that frame's opaque pixels. The twelve frame sprites and the ring carry different
  canvases and different ink boxes, so no single rect — the node's own or the frame's — can make them
  coincide, and the game draws them coincident. REJECTED: drawing the ring on the frame's rect with
  the frame's fit — it leaves the ring two pixels past the frame on the right at a 160px card.

- @cards @text @i18n — The English identity name carries the `Mikodacs SDF UnderLine` underlay,
  transcribed on the same columns as every other row. The identity name node ships the plain
  `Mikodacs SDF Material`, which has no underlay at all, so this row is a product decision taken
  against the material data: English was the one language whose identity name read flat against the
  art. REJECTED: leaving English undecorated to match the shipped material — the card then contradicts
  every other language on the same grid.

## Takeaway
- takeaway: when a layout decision turns on a margin the browser itself cannot reproduce twice, move
  the measurement off the browser and onto the data the original was laid out from.
