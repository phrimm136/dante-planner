# 154 importance-decoration-scales-with-the-box
epic: none · pr: none · supersedes: 153 @importance @stripe

## Decisions

## Superseded
- @skill @stripe → 155 — Caution bands are about 2.4 % of the box width thick, inside the box, top and bottom, for skills and passives, a passive's spanning the pane edge to edge. REJECTED: bands through the text line — thin against a wide card; REJECTED: 4 % of the prefab width — adjacent cards' bands overlapped.
  INVARIANT: band lengths are fractions of the decorated box's width — importance table tests.
- @skill @importance → 155 — The importance icon is sized and placed as a fraction of its skill or passive box's width, taken from in-game unit information captures: the triangle's body is about 13 % of the box width, at the header row's right in the skill's part-icon slot and at the passive box's right edge. REJECTED: sizing through the text line — our cards are about 2.3 times wider relative to their text than the client's panels, so a text-scaled triangle reads a third of its in-game size against the box; REJECTED: the prefab's 200 of 899.4 units against the card width — the prefab box is not the box the capture shows, and the triangle came out about twice its in-game share.

## Takeaway
- takeaway: calibrate a decoration against what the reader compares it with; a triangle is read against its box, a coin against its text.
