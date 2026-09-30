# 162 abnormality-skills-show-their-part-icons
epic: none · pr: none

## Decisions
- @skill @importance @battle — An abnormality main unit's unlocked skill, in the whole-unit view, draws one icon per linked part type in the client's strip: up to five 260-unit icons (a 200-unit slot at scale 1.3) down a right column 70 units in and then a left column 270 units in, on a 200-unit pitch, tinted #ebcaa2 at 0.275, above the importance triangle and bands and below the content; the box grows to at least 260, 460 or 660 units for one, two or three rows, and the importance triangle moves to 486 units in when more than three icons show. The client's skill content sets these from the skill's related part list only for an abnormality owner outside a part view, and a part opened on its own shows none. REJECTED: one icon per distinct part — the client draws the list as given; REJECTED: showing the strip in part views too — the client disables every slot there.
  INVARIANT: the strip shows only in the whole-unit view of an abnormality's unlocked skill — part-icon card tests and the battle visuals test.
  NON-GOAL: more than five icons, which the client leaves undrawn.

## Takeaway
- takeaway: a client feature gated on view state needs the gate transcribed with it; drawn everywhere, it contradicts the view it was meant for.
