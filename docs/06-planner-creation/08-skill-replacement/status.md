# Skill Replacement - Feature Status

Last Updated: 2025-12-28

## Core Features

- [ ] F1: Skill replacement section displays between observation and comprehensive sections with 12 sinner grid
  - Verify: Visual inspection of page layout

- [ ] F2: Each sinner shows identity info picture and skill list with EA counts (default 3,2,1)
  - Verify: Inspect sinner card in grid

- [ ] F3: Clicking grid opens modal with skill exchange options (S1→S2, S2→S3, S1→S3, reset)
  - Verify: Click sinner, verify modal content

- [ ] F4: Clicking reset resets sinner's skill EA to 3,2,1
  - Verify: Perform exchanges, click reset, verify EA values

## Edge Cases

- [ ] E1: Grid is responsive (6→4→3→2 columns at breakpoints)
  - Verify: Resize browser window

- [ ] E2: EA resets when identity changes
  - Verify: Change sinner identity in deck builder, check EA values

- [ ] E3: Exchange disabled when source skill EA <= 0
  - Verify: Deplete EA, try exchange

## Integration

- [ ] I1: Section uses Suspense boundary for data loading
  - Verify: Check component wrapped in Suspense in PlannerMDNewPage

## Progress

Complete: 0/8 (0%)
