# 164 skill-and-passive-names-fit-the-client-text-width
epic: none · pr: none

## Decisions
- @skill @passive @nameplate @fit — A skill or passive name auto-sizes within the client's own text width, the name area of the 1304.2-unit panel less the node's right padding (100 units for skills, 400 for passives), or the card's available width when that is narrower, and its plate follows the fitted text's width. The client's name script sets the text width to the smaller of the preferred width and that fixed limit, so on a card wider than the client panel our names grew to their largest size and their ink sank onto the plate's shadow rows, while the game shrinks them. REJECTED: fitting against the card's width — names come out larger on every desktop card than the game ever draws them; REJECTED: a fixed name size — the client auto-sizes between the node's minimum and maximum.
  INVARIANT: a name never fits a box wider than the client's text width — name fit tests at a card wider than the client panel.

## Takeaway
- takeaway: a fluid card still inherits the client's fixed limits on elements the client caps; fluid width is the container's property, not every child's.
