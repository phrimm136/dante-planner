# 156 unit-information-panels-keep-client-anchors
epic: none · pr: none · supersedes: 149 @separators

## Decisions
- @skill @passive @sanity @layout — Every element of the skill, passive and sanity panels keeps the client's anchor edge (left, right, stretch or centre) and its fixed offset in client units from that edge, on battle, identity and E.G.O pages alike; the card keeps a fluid width and line wrapping is not matched. The client panel is 1304.2 units wide at every screen size, so an element's offset from its anchor edge is the one placement fact that survives a different card width. REJECTED: a card fixed at 1304.2 units — desktop pages would show 456-pixel cards; REJECTED: offsets as fractions of the card width — they agree with the client at one width only.
  INVARIANT: no panel length derives from the card width, and fixed-offset elements measure the same at 390 and 1440 pixels — layout table tests and a browser check.
  NON-GOAL: TextMeshPro auto-size, the Japanese text-bounds height, the E.G.O requirement narrowing, runtime-only rows (panic type-change buff, lock overlays) and the client's sliced plate sprites.
- @skill @passive @sanity @stacking — Where a right-anchored decoration (importance triangle, caution bands, lock icons, sanity condition icons) overlaps left-anchored content, the content draws above it, as the client's sibling order draws it. REJECTED: document order — a condition icon painted over its own text.
  INVARIANT: right-anchored decorations sit below content — stacking class tests and browser hit tests.
- @skill @separators — A 5-unit line with 30 units on each side separates every pair of skill boxes, on every page; section titles sit directly on their content without a rule. The client's skill tab draws the line after every box but the last. REJECTED: lines only between the client's skill groups — the client draws no group boundary of its own.
- @skill @passive @sanity @typography — Elements of fixed client geometry (skill and passive name plates, level and power numerals, tags, flavor) take the client's font size in units; text colours, letter and word spacing and the description line pitch stay the page's. REJECTED: transcribing every text property — spacing and colours were not asked for and the page's type scale serves the rest of the site.

## Superseded
- @battle @separators → 156 — Skill lists draw a separator only between the client's own groups: identity skill 1/2/3/defense and E.G.O awakening/erosion; battle skills have none. Section titles sit directly on their content without a rule. REJECTED: one rule per section title — a line between a title and its own content separates nothing.

## Takeaway
- takeaway: when the page's container cannot match the client's, keep what the client fixes relative to each element's own anchor.
