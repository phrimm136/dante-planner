# 197 edge-importance-bands-sit-inside-the-pane
epic: none · pr: none

## Decisions
- @skill @passive @importance @layout — At both ends of every skill and passive pane, the edge item's outermost visible element (its caution band when decorated; otherwise a passive's box, or a skill's content inside its card padding) sits 15 units inside the pane's border: a decorated first or last passive cancels its 40-unit overhang against the pane's 15-unit padding, a decorated first or last skill gains 15 units outside its band, the passive pane pads its bottom by 15 units instead of 30, and the skill pane drops its 50-unit bottom padding; decorated items between the ends keep the client's overhang. The client's tabs have no border and sit in a masked scroll viewport that clips an edge item's overhang and hides trailing space, while the page frames each pane with a border, so the client's offsets let the first passive band cross the border, put a decorated skill's band on it, and left a 65-unit gap under the last skill. REJECTED: the client's offsets unchanged — they read as a collision at the top and a gap at the bottom against a border; REJECTED: clipping the pane as the viewport does — the first band then showed 27 of 52.2 units and still touched the border; REJECTED: padding every pane by the overhang — it moves undecorated edge items off the 15-unit inset; REJECTED: dropping the border — it changes every panel's look to fix an edge case.
  INVARIANT: decorated first and last items reach the pane's edge inset and middle items keep the overhang — pane edge tests for skills and passives.
  NON-GOAL: the identity passive pane's first battle passive, which follows the pane header rather than the border.
- @skill @passive @stripe — The top caution band scrolls right and the bottom band scrolls left. The client's stripe shader offsets the texture by the animation speed times −1 for the top band and +1 for the bottom band.

## Takeaway
- takeaway: an offset copied from a clipped, borderless client container needs a rule for the edges once the page frames it.
