# 121 skill-decoration-follows-client-tables
epic: none · pr: none

## Decisions
- @skill @coin — A coin is drawn with the client's sprite for its color (gold, grey, green, purple) in the skill-panel state, for every entity. The two-icon scheme keyed on the special flag drew excision and purple coins as unbreakable ones, and the client picks the sprite by color alone. REJECTED: keeping the existing gold and unbreakable icons for the two old colors — they are near but not exact matches of the client's sprites, and two sources for one sprite table drift.
- @skill @importance — A skill or passive with importance 1 to 3 is decorated as the client's `ImportanceUI` does: the level's warning icon and, from level 2, its glow sprite with the level's flicker period; a caution stripe band at the top and bottom of the box scrolling in opposite directions without reversal and blinking within the level's alpha range; passive panel, glow and name colors from the level's table. Enemies use the enemy palette and assistants the player palette. The client ships these as tables keyed by level, so the page reads the same tables. REJECTED: a single accent color per level — it loses the stripe, which is the element players recognize.
- @skill @stripe — The stripe is a CSS emulation of the client shader: the client's caution texture repeated along each band and shifted by exactly one rendered tile width per animation cycle, so the loop has no visible seam. The scroll rate is tuned against the client by eye. REJECTED: a canvas or WebGL port of the shader — a second render path for a 2.5 %-high band.

## Takeaway
- takeaway: when the client drives a visual from data tables, ship the tables and render from them; a hand-picked approximation looks right until the tables change.
