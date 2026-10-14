# 189 the-header-background-follows-the-selected-stage
epic: none · pr: none · supersedes: 149 @background

## Decisions
- @battle @background — The battle detail header draws the background of the selected stage entry's own map (`mapName` on the engagement row, the unit's wave map in the client's `waveList[i].battleMapInfo`), and changes when the user selects another entry. Supersedes ADR 149 @background. REJECTED: the first entry's map in mode order (149) — selecting a later stage left the first stage's art behind it.
  INVARIANT: the header's map is the selected row's `mapName` — the header background scenarios.
  NON-GOAL: a map that changes during a fight (for example Camellia's second state); that needs per-phase map facts from the static pipeline.

## Takeaway
- takeaway: everything the header shows is a projection of the selected occurrence, the background included.
