# 194 co-appearing-units-link-to-the-same-stage
epic: none · pr: none

## Decisions
- @battle @co-units — Under the stage row, a line titled "함께 등장하는 적" lists every other card the selected engagement row's stage spawns (main, sub, script and refill units, from the row's `coUnits`): one chip per card and relation, labelled with the card's list name (an envy card with its identity title), the only relation mark being "(소환)" on the summoner's page and "(소환자)" on the summoned unit's page. A co-unit belonging to the current card has no chip, a card missing from the loaded spec lists is dropped, and a row with no chip renders no line. REJECTED: marking every way a unit arrives (wave refill, stage script) — the roster answers who else is there; only a summon changes how the fight plays. REJECTED: a chip for another unit of the current card — it links to the page the reader is on.
  INVARIANT: a chip is a link to the other card with `?stage=` the selected row's stage key — the co-unit component scenarios.
- @battle @stage-link — The three battle detail routes take an optional `stage` search value (an engagement stage key); the page opens on the first row, in mode order, whose stage equals it, in whichever mode that row sits, and otherwise on the arrival-mode default of ADR 149. A malformed value is dropped at the route boundary rather than failing the page. The routes remount on a change of id or stage, because the selection is state initialised once and the router otherwise keeps a route component across param changes. REJECTED: opening the linked card on its arrival-mode default — the chip promises the same stage. REJECTED: keying the selection by id inside the page — the remount key belongs to the route that owns the params.
  NON-GOAL: writing the selected row back into the URL.

## Takeaway
- takeaway: a cross-page link carries the occurrence, not just the entity, when the page it opens is a projection of an occurrence.
