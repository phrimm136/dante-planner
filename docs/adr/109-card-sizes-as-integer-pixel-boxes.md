# 109 card-sizes-as-integer-pixel-boxes
epic: none · pr: none · supersedes: 106 @cards @geometry, 102 @cards @geometry

## Decisions

- @cards @geometry — A card's size is stored as the integer pixel box it is laid out at
  (`CARD_LAYOUT.SIZE_PX`, `widthPx` over `heightPx`), and every ratio is derived from a box by one
  pure function, `aspectOf(size)`. The frontend lays cards out in pixels and nothing
  downstream of the table can act on a canvas unit, so a table in game units makes every
  consumer convert before it can use the number, and the width the slot renders at has to
  be carried beside the pair to make the conversion possible at all. Consequence accepted:
  each derived height rounds to the nearest pixel once, in the table — identity 232 against
  232.26, EGO 206 against 205.71, theme pack 395 against 394.76. REJECTED: keeping the
  game's rect in the frontend — the rect is the transcription source, not a runtime value,
  and holding it here forces a second number (the slot width) to travel with it everywhere
  a height is wanted. REJECTED: the rect plus a pixels-per-unit scale — it spends a third
  stored number to reproduce a height the box already states, and the scale is a float that
  every card would round differently.
- @cards @geometry — Every card box carries a height; a grid whose rows take their content
  instead of the card's box says so with its own `rows: 'auto'`. Whether a row is pinned is
  a property of the grid, not of the card in it, and a box with a missing height forces
  every reader of the table — slot, grid, skeleton, text fit — to carry a null branch for a
  card that has a perfectly good size. REJECTED: modelling auto rows as a card without a
  height — one card then has two boxes, the real one and the null-height copy each call
  site builds, and nothing keeps the widths in step.
- @cards @geometry — One constants group, `CARD_LAYOUT`, holds the boxes (`SIZE_PX`) and the
  settings a grid of them is laid out with (`MOBILE_SCALE`, `GAP_PX`, `BREAKPOINT_PX`).
  Every consumer of a box also reads at least one of the settings, so splitting them puts
  two imports and two names in front of one concern. REJECTED: a settings group beside a
  sizes group — that is the split this decision collapses, and it is what let a second
  breakpoint constant live beside the card one without either naming the other.
- @cards @text — A card's text is fitted in percent of the card root, so the fitted size is
  a `cqw` length and no card is told its pixel width. A card already positions its interior
  in percent of its own root, so a pixel fit is the one number that has to be threaded from
  the slot down through every card and name component, and it re-enters the tree as a prop
  the layer boundaries otherwise keep out. Consequence accepted: jsdom drops a `cqw`
  length, so a component test reads the size off the pure fit rather than the DOM.
  REJECTED: pixel fits fed by a slot render prop — the render prop exists only to publish
  the width, and it makes every card's subtree re-render on a width the card cannot use.

## Takeaway

- takeaway: store a measurement in the unit its consumers act in; a conversion the
  consumer cannot skip belongs at the transcription boundary, not in the table.
