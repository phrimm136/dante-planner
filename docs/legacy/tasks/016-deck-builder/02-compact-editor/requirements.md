# Task: Compact Deck Visualization in Deck Builder

## Decisions
- **Replace SinnerGrid with compact thumbnails** — current 2-row grid at 290px per card wastes ~580px of a 960px (90vh) dialog viewport, forcing constant scrolling
- **Tab-dependent ego display** — compact ego grid only visible when Entity toggle is on "EGO" tab, consistent with existing toggle pattern (no new mental model)
- **Preserve attack type and skill affinity colors** — both attack type icon and 3 affinity color boxes kept per identity thumbnail; only ego rank slots removed
- **Deployment number with color only** — no Selected/Backup text/image indicator; number color distinguishes deploy vs backup
- **Deployed thumbnails dimmed** — identity portraits with a deployment order number are dimmed (brightness-50), matching IdentityCard's `isSelected` behavior
- **Profile image for thumbnails** — use `getIdentityProfileImagePath` (square portrait), not `getIdentityInfoImagePath` (tall card art)
- **Position = sinner identity** — no sinner label needed in ego grid; fixed 12-position order conveys which sinner is which
- **Reuse existing assets** — level rendering matches IdentityCard pattern, uptie icon from `getRarityIconPath()`, attack type from `getAttackTypeIconPath()`

## Description

Replace the full `SinnerGrid` (12 large deck cards with skill rows and ego slots) with two compact visualizations that switch based on the Entity toggle tab:

### Compact Identity Row (Identity tab)
- 12 small thumbnails in a dynamic-column grid (all 12 fit in one row on PC at ~1440px)
- Each thumbnail shows:
  - Identity profile image (`getIdentityProfileImagePath` — square portrait, uptie-aware)
  - Uptie level icon (upper-right, from `getRarityIconPath`)
  - Level number (lower-right, same font/style as `IdentityCard` line 132)
  - Deployment number with color: deploy color vs backup color (from existing `formation-number-deploy` / `formation-number-backup` CSS classes)
  - Skill affinity color boxes (3 colored boxes with attack type icons, same as `SinnerDeckCard` lines 93-117)
  - Attack type icon (inside affinity color boxes, from `getAttackTypeIconPath`)
- **Dimming**: thumbnails that have a deployment order are dimmed (`brightness-50`), same as `IdentityCard`'s `isSelected` dimming behavior
- Click behavior: toggle deployment (same as current `SinnerDeckCard`)
- Removed: ego rank slot row (5 boxes) — this info moves to the compact ego grid

### Compact Ego Grid (EGO tab)
- 12 sinner cells in a dynamic-column grid (2x6 on PC)
- Each cell shows the sinner's 5 ego rank slots using the **same rendering** as current `SinnerDeckCard` lines 120-150:
  - Equipped ego: portrait image on affinity-colored background
  - Empty slot: rank type icon on muted background
- No sinner label — position in the fixed 12-sinner order identifies each cell

### Unchanged
- StatusViewer (aggregate affinity stats)
- DeckBuilderActionBar (import/export/reset)
- EntityToggle, SinnerFilter, KeywordFilter, SearchBar
- Identity/EGO selection card lists (max-h-[600px] scrollable grids)

## Scope

Files to READ for context:
- `frontend/src/components/deckBuilder/DeckBuilderContent.tsx` — main layout, where SinnerGrid is rendered (line 551-559)
- `frontend/src/components/deckBuilder/SinnerGrid.tsx` — current grid implementation (to be replaced)
- `frontend/src/components/deckBuilder/SinnerDeckCard.tsx` — current card with all sections (skill row to reuse, ego slots to extract)
- `frontend/src/components/identity/IdentityCard.tsx` — level/uptie rendering patterns and dimming behavior to reuse
- `frontend/src/lib/assetPaths.ts` — `getIdentityProfileImagePath`, `getRarityIconPath`, `getAttackTypeIconPath`, `getEGOImagePath`, `getEGOTypeIconPath`
- `frontend/src/lib/constants.ts` — `CARD_GRID`, `SINNERS`, breakpoints
- `frontend/src/components/common/ResponsiveCardGrid.tsx` — existing responsive grid pattern
- `frontend/src/components/common/ScaledCardWrapper.tsx` — existing scaling pattern

## Target

Files to CREATE or MODIFY:
- **CREATE** `frontend/src/components/deckBuilder/CompactIdentityRow.tsx` — compact identity thumbnail grid
- **CREATE** `frontend/src/components/deckBuilder/CompactEgoGrid.tsx` — compact ego slots grid
- **MODIFY** `frontend/src/components/deckBuilder/DeckBuilderContent.tsx` — replace `<SinnerGrid>` with conditional `<CompactIdentityRow>` / `<CompactEgoGrid>` based on `filterState.entityMode`
- **MODIFY** `frontend/src/lib/constants.ts` — add compact card dimensions to `CARD_GRID` (e.g., `COMPACT_IDENTITY`, `COMPACT_EGO_CELL`)

## Impact Analysis
- **DeckBuilderContent.tsx** (HIGH) — layout restructure in the deck visualization section (lines 548-569); SinnerGrid import replaced with compact components; must preserve all existing props/data flow (equipment, deploymentOrder, skillDataMap, egoAffinityMap, handleToggleDeploy)
- **SinnerGrid.tsx** (NONE) — not modified; may still be used by DeckBuilderSummary or tracker mode
- **SinnerDeckCard.tsx** (NONE) — not modified; skill row and ego slot rendering logic to be replicated in compact components
- **constants.ts** (LOW) — additive change only (new dimension constants)
- **DeckBuilderSummary.tsx** — verify it still uses SinnerGrid independently (no ripple)
- **DeckBuilderPane.tsx** — no changes needed (just wraps DeckBuilderContent)

## Risk Assessment
- **Edge cases:**
  - Sinner with no equipped ego (all 5 slots empty) — ego grid cell shows 5 empty rank icons
  - Sinner with no identity change from default — thumbnail still renders with default identity data
  - Very narrow viewport (< 640px) — dynamic columns must gracefully wrap to fewer columns
  - Deployment number > 9 (double digit) — must fit within compact thumbnail
- **Performance:** Compact components render 12 items vs 12 large cards — strictly fewer DOM nodes, should improve
- **Accessibility:** Attack type icons need alt text; deployment numbers need sufficient contrast on dimmed backgrounds

## Done When
- [ ] Identity tab shows compact thumbnail row instead of full SinnerGrid
- [ ] Each thumbnail displays: profile portrait, uptie icon (upper-right), level (lower-right), deployment number with deploy/backup color, 3 skill affinity color boxes with attack type icons
- [ ] Deployed thumbnails are dimmed (brightness-50), matching IdentityCard selected behavior
- [ ] Clicking a thumbnail toggles deployment (same behavior as current)
- [ ] EGO tab shows compact ego grid (2x6 on PC) with 5 rank slots per sinner
- [ ] Ego slot rendering matches current SinnerDeckCard style (portrait on affinity color, rank icon when empty)
- [ ] Dynamic columns adapt to viewport width (one row of 12 on PC for identities, 2x6 for egos)
- [ ] StatusViewer, action bar, filters, and selection lists are unchanged
- [ ] No TypeScript errors
- [ ] All existing tests pass

## Verification
### Automated
- [ ] TypeScript: `yarn tsc --noEmit` passes
- [ ] Lint: no new warnings

### Manual
1. Open deck builder dialog on PC (1920px) — identity thumbnails in one row with skill affinity boxes below each, no vertical scroll needed to reach selection list
2. Switch to EGO tab — ego grid appears in 2x6 layout
3. Click identity thumbnail — deployment number toggles on/off with correct color, thumbnail dims when deployed
4. Resize to tablet (768px) — columns reduce, layout remains usable
5. Resize to mobile (375px) — columns reduce further, thumbnails scale down
6. Equip/unequip egos — ego grid reflects changes in real time
7. Verify StatusViewer still shows correct aggregate affinities

### Edge Cases
- [ ] All 12 sinners deployed: numbers 1-12 all visible and legible on dimmed compact thumbnails
- [ ] Sinner with 0 equipped egos: ego grid cell shows 5 empty rank slots
- [ ] Sinner with all 5 egos equipped: all 5 slots show portraits correctly
- [ ] Double-digit deployment number (10, 11, 12): fits within thumbnail without overflow
