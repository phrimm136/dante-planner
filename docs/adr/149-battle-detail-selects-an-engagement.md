# 149 battle-detail-selects-an-engagement
epic: none · pr: none · supersedes: 119 @level @engagement

## Decisions
- @battle @engagement — The battle detail page's selector row picks one engagement row (stage, unit, level) of the origin group, grouped by mode; the chosen row decides the unit's kit, name, level and the stage-script facts shown, and every section renders the composition that row spawns. The page opens on the first row of the mode the user arrived from. Supersedes ADR 119 @level and @engagement: the selector is the list of engagements, so no separate encounters panel remains. REJECTED: level buttons — 636 of 736 multi-stage files reuse one level across stages with different kits or scripts, so a level cannot pick what the player fights.
- @battle @stage-effects — Keywords a stage script grants (ADR 390 static facts), to the enemy side or the player side, render as keyword cards with their stack in a stage-effects block under the selector, only when the selected row has any; the grant's trigger and condition are not shown. REJECTED: folding them into the passive list — they belong to the stage, not the unit. REJECTED: showing the trigger and condition — 114 of 127 guards hold values the decompiled store does not resolve, so a condition would be shown for a few grants and guessed for the rest.

## Superseded
- @battle @parts → 187 — With several parts, "All" shows the whole unit the way the client's unit information does: the unit's status, one line per part with its name and HP, and the unit's and every part's passives; resistances, break sections and a part's skills appear when that part is selected. REJECTED: every part's full status block at once — a five-part unit stacks five blocks.
- @battle @background → 189 — The header draws the entity's sprite over the background of its first engagement's map in the list's mode order. REJECTED: a fixed backdrop — the stage art is the one context every entity has.
- @battle @separators → 156 — Skill lists draw a separator only between the client's own groups: identity skill 1/2/3/defense and E.G.O awakening/erosion; battle skills have none. Section titles sit directly on their content without a rule. REJECTED: one rule per section title — a line between a title and its own content separates nothing.

## Takeaway
- takeaway: when what a player fights varies per occurrence, the page selects an occurrence; every other control is a projection of it.
