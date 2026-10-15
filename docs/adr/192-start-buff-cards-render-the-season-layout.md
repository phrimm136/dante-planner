# 192 start-buff-cards-render-the-season-layout
epic: none · pr: none

## Decisions
- @planner @start-buff @card: The start-buff card and the result-screen mini card render from
  their season's layout files, `static/data/MD{N}/startBuffCard.json` and `startBuffMiniCard.json`
  (static ADR 452). Every element is an absolute rect in the layout's canvas. It draws the sprite
  the layout names. The frontend holds no per-season table, offset, colour or image name. The
  flex layout it replaces approximated the client rects with rows and margins and drifted from
  the game by up to 6 units per element.
  REJECTED: keeping the flex layout with its numbers moved to JSON. The approximation moves into
  the data unchanged.
  REJECTED: a fallback layout for a season without files. A season without files is a data error,
  and a silent fallback showed MD6 art on MD7 cards before.
- @planner @start-buff @text: Card text is laid out with the TextMeshPro pen over the shipped
  advance tables (ADR 158). The description uses the description face table of the current
  language. It keeps the localized line breaks and starts each effect with the layout's bullet
  prefix and bullet advance. It is drawn at the layout's description size, with no auto-size. A
  space never forces a line break. The name auto-sizes within the prefab range. The cost uses the
  layout's cost colour. If the buff level is above the layout's threshold, it uses the second
  colour.
  REJECTED: the browser's line breaker. It disagreed with the game on the captured lines, and it
  turned the localized line breaks into spaces.

INVARIANT 192 @planner @start-buff: For the ten MD7 "++" cards of the recorded in-game capture
(`md7-plus2-capture-lines.json`), the description lines equal the captured lines. Gate: a frontend test over the MD7
layout, the description table and the captured line list.

NON-GOAL 192 @planner @start-buff: Hover and press animation is not reproduced. The hover state
shows the settled overlay.

## Takeaway
- takeaway: if the game draws a card from a prefab, the frontend draws the prefab's rects and
  keeps no layout numbers of its own.
