# 106 card-rects-as-integer-units

epic: none · pr: none

## Decisions

- @cards @geometry — The slot hands its rendered width to its card through a render-prop
  child, not through context. The card is always the slot's own child, so the width has
  one short path to travel and no provider to be missing at the top of it; a context would
  make a card silently renderable outside a slot, which is exactly the arrangement the
  slot exists to prevent. REJECTED: a `CardSlotContext` with a `useCardSlotWidth` hook — it
  buys nothing at this depth and adds an error mode (a card read outside any slot) that the
  types cannot catch. REJECTED: each card calling the sizing hook itself — that is the
  duplication the slot replaced, and it subscribes every card in a grid to the breakpoint
  separately.

## Superseded

- @cards @geometry → 109 — A card's root is stored as the integer pair its rect was transcribed
  as (`CARD_UNITS`), and every ratio is derived from a pair by one pure function,
  `aspectOf(units)`. The source of truth is the game's rect in canvas units, and a stored
  ratio is a lossy copy of it: the two numbers it came from can no longer be read back, so
  a transcription error has nothing to check against and a re-measured rect has to be
  divided out by hand before it can be compared. REJECTED: `CARD_ASPECT`, a table of
  `310 / 450` expressions — the divisions survive in the source but not in the value, so
  every consumer downstream holds a float and the pair is unavailable where a height or a
  second rect is needed. REJECTED: storing both the pair and the ratio — two spellings of
  one fact, and they disagree the first time either is edited.

## Takeaway

- takeaway: store the measurement, derive the ratio; a number computed at the point of use
  keeps its provenance, a number stored in place of its inputs loses it.
