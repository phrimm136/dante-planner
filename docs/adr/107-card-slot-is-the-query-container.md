# 107 card-slot-is-the-query-container

epic: none · pr: none

## Decisions

- @cards @geometry — `CardSlot` declares `container-type: inline-size`, and a card root
  that carries padding, a border, or a gap from its table declares none. A container query
  length resolves against the element's nearest *ancestor* container, never against the
  element itself, so a root that is its own container cannot anchor its own padding — the
  length falls through to the small viewport and grows with the window instead of with the
  card. The same root is also the wrong container for its children: its content box is the
  card width minus that padding, while every share in the table was transcribed against the
  full width. The slot is the one box that is the card's width with nothing subtracted.
  REJECTED: wrapping each card's children in an inner full-size div that carries the padding
  and gap — it restores the anchor but adds a div per card and still leaves each root free
  to declare a container whose content box silently shrinks its children. REJECTED: spelling
  the root's own insets as percentages — padding resolves against the parent and would work,
  but a column gap percentage resolves against an indefinite height and collapses to zero,
  so the two halves of one table would need two different units.
- @cards @geometry — A share of a card root is spelled `cqw`, not `%`, wherever the element
  is not a direct child of the root's box. A percentage resolves against the containing
  block's content box, which on a padded root is narrower than the width the share was
  transcribed against; `cqw` resolves against the slot regardless of depth. REJECTED: keeping
  `%` and re-basing the table entries on the padded width — the table would then no longer
  read as the game's rect, and every padding change would silently re-scale unrelated parts.
- @cards @geometry — A card's rect in `CARD_UNITS` is its border box: a card drawn with a
  border counts that border inside its pair, not outside it. The slot sizes the card from
  the pair and the card is `box-sizing: border-box`, so a pair that omits the border makes
  the slot narrower than the card the rect was measured from and the content inside it is
  squeezed by twice the border width. REJECTED: keeping the content-box pair and widening
  the slot by the border at the call site — the correction would have to be repeated at
  every slot and would not survive the border changing.

## Takeaway

- takeaway: a box that subtracts from itself cannot be the reference frame for what it
  contains — put the reference frame outside the box, where nothing has been taken off yet.
