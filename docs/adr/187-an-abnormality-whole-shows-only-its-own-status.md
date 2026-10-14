# 187 an-abnormality-whole-shows-only-its-own-status
epic: none · pr: none · supersedes: 149 @parts

## Decisions
- @battle @whole — With "All" selected on a non-shadow abnormality, the status shows only what the client's stage preview fills for the whole unit: the whole's own HP, and "-" for speed, defense level, the three attack-type resistances and the seven sin resistances, with no multiplier, no break sections and no per-part HP lines. Selecting a part shows that part's real values. Skills list every skill of the whole, and passives list the whole's own plus every part's, deduplicated, as before. This follows `UnitInfomationStatusData.SetData` (client 4540518d06efd18e, @0x180eba710), which calls `SetEmptyBehaviorResist` / `SetEmptyAttributeResist` for an abnormality unit and clears speed and defense in `SetAbnormalityStatus`; the per-part HP list (`ABNORMALITY_PART_STATUS`) is added in battle only. Supersedes ADR 149 @parts. REJECTED: the whole's status plus one HP line per part (149 @parts) — the client's stage preview has no part list; REJECTED: the single part's full status under "All" when the unit has one part — 911 phases showed part values as if they were the whole's.
- @battle @shadow — A shadow abnormality (unit keyword `SHADOW_ENEMY`) keeps its full status under "All", because the client shows its first part's values and the planner's data carries those values on the whole (153 of 153 shadow phases). REJECTED: blanking shadow units like other abnormalities — the client does not.
  INVARIANT: a non-shadow abnormality whole never renders a speed, defense or resistance number — the statusView and battle detail page whole-unit scenarios.
  NON-GOAL: the battle-only HP gauge and part status list of the client's in-battle panel.

## Takeaway
- takeaway: a blank the client draws on purpose is data; render it as "-", never by omitting the panel or by borrowing a part's numbers.
