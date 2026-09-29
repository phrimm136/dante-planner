# 153 importance-decoration-scales-with-text
epic: none · pr: none · supersedes: 150 @importance @stripe @passive @coin

## Decisions
- @skill @importance — Client UI units map to page length through the text: 50 client units are one text-sm line (1.25 rem), for every client-sized element. A skill's importance icon is 200 units pinned at the client's offset from the box's top-right (286 units in, 86 when locked); a passive's is 118 units at the right edge, 14 units down, as the in-game unit information panel draws it; the glow is the icon's child and inherits its alpha. REJECTED: client units against the card's width — our cards are about 2.3 times wider relative to their text than the client's panels, so every element came out 2.3 times too large.
- @skill @stripe — A skill's caution bands run inside the box at 4 % of the client box width (899.4 units), top and bottom, scrolling in opposite directions and blinking at one speed for every level; a passive shows only a bottom band of 25 units below the box inside the list gap and a dashed top edge. No band may overlap a neighbouring card. REJECTED: bands straddling the box edge — adjacent cards' bands overlapped.
- @passive @importance — A passive's importance tints only its name plate with the panel color and the caution hatch, with a glow around the plate; the name uses the level's text color. REJECTED: a separate outline sprite around a flat banner — the in-game plate carries the tint itself.
- @skill @coin — A coin is 50 client units wide with its aspect kept.
  INVARIANT: every importance and coin length derives from the one client-unit scale — theme constants and the importance table tests.

## Takeaway
- takeaway: transcribe client geometry against the element the client scales it with; a panel's width is a layout accident, its text size is the design.
