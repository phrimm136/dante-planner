# 093 start-buff-description-scrolls

## Decisions
- @frontend @planner @start-buff — The start buff card's description keeps its fixed height and
  scrolls when the text overflows; the box sits above the full-card click target so wheel and
  touch gestures reach it, and a click on the box still toggles selection. Enhanced entries add
  whole effect lines to a card that is a fixed-size reproduction of the in-game artwork.
  REJECTED: shrink the text to fit — four-effect entries fall below readable size at mobile
  scale, where the cards are already scaled down as a whole.
  REJECTED: enlarge the card — breaks the five-column grid and the artwork proportions.
  REJECTED: forward scroll events through the click target — wheel forwarding works, but touch
  scrolling needs the native scrollable element under the finger.
- @frontend @planner @start-buff (taste) — The description's scrollbar stays hidden to preserve
  the artwork, accepting that a clipped card gives no visual hint that more text exists.
- @frontend @a11y — The description carries a presentational role and its own click handler;
  accessibility semantics stay on the single card button beneath it.
  REJECTED: make the description a button — nested interactive content inside the enhancement
  row is invalid, and two buttons would announce the card twice.

## Takeaway
- takeaway: A scrollable box with a hidden scrollbar under a pointer-capturing overlay is a
  silent clamp; check who owns the pointer events before blaming the data.
