# 196 battle-cards-draw-their-list-image
epic: none · pr: none · supersedes: 152 @sprite

## Decisions
- @battle @sprite @list-image — A battle card draws its card's list image (static ADR 475: one image per card, framed by the pipeline from portrait, head or sprite) covering the card's image box, and does no sprite placement of its own; an image that fails to load leaves the card id in the box. Supersedes ADR 152 @sprite. The fixed 45 px per world unit showed a fragment of every boss and a speck of every small unit, and no one scale or anchor fit both. REJECTED: keeping the per-sprite placement with a per-card scale — the framing rules need the portrait and the sprite's alpha measures, which only the pipeline reads; REJECTED: fitting the battle sprite to the box in the browser — a weapon or effect sets the box, and most units have a game portrait that reads better than any sprite crop.
  NON-GOAL: the card shows no relative size between units; the detail header does.

## Takeaway
- takeaway: framing that needs pixel measures belongs where the pixels are read; the page draws the result.
