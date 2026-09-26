# 119 battle-detail-phases-parts-and-levels
epic: none · pr: none

## Decisions
- @battle @phase — A multi-phase enemy gets a phase selector in the selector row; each phase swaps sprite, stats, skills, passives, E.G.O and sanity. 57 of 72 multi-phase enemies change skill sets between phases. The selector is not rendered for single-phase entities. REJECTED: stacking phases — up to four full skill, passive and sanity blocks on one page.
- @battle @part — A multi-part phase gets a part selector with an "All" option; selecting a part shows that part's status (HP, resistances, break sections, speed), its passives, and the skills linked to its part type, as the client's unit information does for a part. REJECTED: a row of all parts with no selector — it cannot filter skills, and the client does. REJECTED: filtering status and passives only — the client filters the skill list per part through each skill's linked part types.
- @battle @level — Level is chosen with buttons, one per distinct engagement level, defaulting to the first engagement of the case the user arrived from; HP and defense use the identity formulas per unit and per part. The level is a property of the engagement, not the file: 284 Mirror Dungeon enemies are spawned at two or more Mirror Dungeon levels. REJECTED: splitting each enemy file per stage case — it duplicates an identical spec and still leaves a file with many levels. REJECTED: a free slider — levels between engagements are levels the enemy never appears at.
- @battle @engagement — Engagements render in the metadata block as stage label and level ("4-48 (Lv. 35)", theme pack name, line and node), grouped by case and collapsed to distinct label and level pairs. REJECTED: raw rows — one filler enemy carries 953.
- @battle @ego — Peccatulum Invidiae phases render their E.G.O skills and passives inline at the phase's E.G.O level in an E.G.O section. The enemy record only references E.G.O ids and levels; the cards read the E.G.O records. REJECTED: links to the E.G.O pages — the page exists to show what the enemy does.

## Takeaway
- takeaway: a value that varies per occurrence belongs to the occurrence; splitting the owner by one axis of occurrence moves the problem without removing it.
