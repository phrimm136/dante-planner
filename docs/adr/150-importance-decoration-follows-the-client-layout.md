# 150 importance-decoration-follows-the-client-layout
epic: none · pr: none

## Decisions
- @skill @importance — The importance icon is the client's fixed-size sprite pinned at the client's offset from the box's top-right corner (skills 286 units in, passives at the edge, 37 down), with the glow as its child so it inherits the icon's per-level alpha; the flicker runs on the icon at level 1 and on the glow from level 2, between the level's curve values (0.7↔1.0, 0.5↔1.0 at level 3) over the level's period. Supersedes the icon and glow placement of ADR 121 @importance. REJECTED: an icon filling the box height — the client scales nothing by box size.
- @skill @stripe — Each caution band is 4 % of the box width tall and straddles the box edge as the client's group extends past the box; the bands blink at one speed for every level within the level's alpha range and scroll in opposite directions. Supersedes ADR 121 @stripe's band height and blink timing. REJECTED: a band inside the box sized by its height — the client sizes by width and overhangs the edge.
- @passive @importance — A passive's importance tints only its name banner, flickers a separate outline around the banner, and colors the name; the rest of the box is untouched. REJECTED: tinting the whole passive box — the client does not.
- @skill @coin — Coins render at the client's size ratio with their aspect kept. REJECTED: a square 24 px box — it squeezes the sprite and turns the art's speckle into smear.

## Takeaway
- takeaway: a decoration transcribed from a client is placed in the client's units relative to the box it decorates; sizing it by the web box's own height reinvents the layout the client already fixed.
