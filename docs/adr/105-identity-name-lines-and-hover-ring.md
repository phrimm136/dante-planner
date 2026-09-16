# 105 identity-name-lines-and-hover-ring
epic: none · pr: none

## Decisions
- @cards @text — The identity name is drawn as one nowrap span per line, from the same advance-table
  fit the EGO name already uses, instead of handing the string to the browser under `pre-wrap` /
  `word-break` / `line-break: strict`. Chrome rounds this face's advances to whole pixels — the
  Japanese `ロボトミーE.G.O::` measures 122.59 px against the face's own 126.29 px at the rendered
  18.36 px size — so the browser and the model disagreed on four of the twelve cards of the Japanese
  formation reference in one direction and on two in the other, and no fixture could hold both.
  REJECTED: keeping the browser's wrapping and fixturing what it produces — the breaks then move with
  the platform's hinting, which is what the shipped advance tables exist to take out of the loop.
  REJECTED: keeping ` ` before a dash to glue `" - "` — the game breaks at exactly that space
  (identity 10314 draws `人差し指代行者` over `-`), so the glue was moving a break the game makes.
- @cards @text — The wrapping name segment measures against its own box, 242 units of the 305-unit
  formation slot, not against the 240-unit `[Text]GroupName` rect the name block is positioned from.
  The twelve reference cards bracket it: the widest line the game keeps whole measures 240.75u and
  the narrowest it breaks measures 243.00u, so no box of 240u can produce the game's output, and the
  two readings of TextMeshPro's pen — tracking after every glyph, or only between them — move both
  bounds by one 0.7u step. REJECTED: the earlier "kerning-sized overrun" reading — the shipped
  `Corporate-Logo-Bold-ver2` face has no GPOS pair adjustment for any pair in that string, so kerning
  cannot supply the missing 0.021 em. REJECTED: transcribing `[Text]GroupUpperName`'s rect instead —
  the prefab is not in a checkout without `static/raw/`, and the bracket is the evidence that exists.
- @cards @geometry — The identity hover ring ships as one sprite per frame sprite, carrying that
  frame's canvas with the ring's ink resampled onto the frame's ink, and is drawn with the frame's
  own rect, fit and origin. One ring on a derived `fill` rect matched the frame's ink to a hundredth
  of a pixel only at the scale it was measured at; two elements with different rects and different
  fits round to different device pixels at other zoom levels. REJECTED: keeping the per-sprite rect
  derivation and the ink tables in the layout — twenty-four hand-transcribed sprite boxes existed
  only to line up two layers that can share one box instead.

## Takeaway
- takeaway: when two layers must coincide, give them one box rather than two boxes computed to agree;
  and when a transcribed constant cannot reproduce the source, say what the source brackets it to.
