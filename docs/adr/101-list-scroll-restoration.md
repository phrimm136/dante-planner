# 101 list-scroll-restoration
epic: none · pr: none

## Decisions
- @grid @scroll — The filtered entity grid renders every item's cell on the first commit and lets a reveal window decide which cells hold a card. The router replays a saved scroll offset against whatever document exists at that moment, so a grid that renders only its revealed batches is a few hundred pixels tall on return and the replay clamps to near the top. REJECTED: an app-owned scroll store restoring the offset after the cards arrive — it fights the router's own replay and lands a visible jump. REJECTED: a virtualized list — it reserves the scroll height but re-introduces the same problem for any offset whose rows are not yet materialized, and costs the CSS-only filtering that the per-card visibility subscription relies on.
- @grid @reveal — The reveal window opens on the first animation frame, at the row the viewport starts on, and grows by one batch toward each end per frame; the slots outside it stay empty. Measuring in a layout effect reads a scroll offset the router has not replayed yet, so the window would open at the top of the list and walk down to the visible row. REJECTED: opening the window at index 0 and growing only downward — on return it fills rows nobody is looking at first.
- @grid @cells — A hidden cell keeps its slot and its `hidden` class; only the card inside it is withheld. The slot's height comes from the grid's `grid-auto-rows`, which is what makes the reserved document the right height before any card renders.

## Takeaway
- takeaway: scroll restoration is a contract about document height at replay time, not about content — reserve the geometry on the first commit and let the expensive part arrive later, measured from where the viewport actually is.
