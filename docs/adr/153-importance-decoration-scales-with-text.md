# 153 importance-decoration-scales-with-text
epic: none · pr: none · supersedes: 150 @importance @stripe @passive @coin

## Decisions
- @skill @importance — Client UI units map to page length through the text: 50 client units are one text-sm line (1.25 rem), for every client-sized element, calibrated against in-game unit information captures. A skill's importance icon is 159 units, 95 units in from the box's right edge (86 when locked) and 45 down, in the header row's part-icon slot; a passive's is 163 units at the right edge, 14 down; the glow is the icon's child and inherits its alpha. REJECTED: client units against the card's width — our cards are about 2.3 times wider relative to their text than the client's panels, so every element came out 2.3 times too large.
- @skill @stripe — Skills and passives carry caution bands 27 units thick inside the box, top and bottom, scrolling in opposite directions and blinking at one speed for every level; a passive's bands span the pane edge to edge and the passive pads its content by a band so no text sits under them. No band may overlap a neighbouring card. REJECTED: bands straddling the box edge — adjacent cards' bands overlapped.
- @passive @importance — A passive's importance paints its name plate as the client does: the level's panel color on the plate body, a darker slanted tail with the caution hatch, the level's text color, and a glow that follows the plate outline and pulses within the level's range. REJECTED: a rectangular glow behind a flat banner — it blinks behind the plate instead of hugging it.
- @skill @coin — A coin is 50 client units wide with its aspect kept.
  INVARIANT: every importance and coin length derives from the one client-unit scale — theme constants and the importance table tests.

## Takeaway
- takeaway: transcribe client geometry against the element the client scales it with; a panel's width is a layout accident, its text size is the design.
