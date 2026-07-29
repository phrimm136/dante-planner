## What Was Done
- Replaced full SinnerGrid (~580px) with compact identity thumbnails (~128px) and ego grid (~70px) in deck builder dialog
- Identity tab: 12 compact thumbnails with profile portrait, uptie tier icon (upper-right), level (lower-right), deployment number with color, 3 skill affinity boxes with attack type icons, brightness dimming on deployed
- EGO tab: 2x6 grid (on PC) showing 5 ego rank slots per sinner, matching SinnerDeckCard rendering
- Tab-dependent switching via conditional rendering based on `filterState.entityMode`
- Added `COMPACT_IDENTITY` width/height constants to `CARD_GRID`
- Removed unused `COMPACT_EGO_CELL` constants after switching ego grid to Tailwind responsive classes
- Code review passed: ACCEPTABLE (4.0/5.0)

## Files Changed
- `frontend/src/components/deckBuilder/CompactIdentityRow.tsx` (NEW) — compact identity thumbnail grid
- `frontend/src/components/deckBuilder/CompactEgoGrid.tsx` (NEW) — compact ego slots grid
- `frontend/src/components/deckBuilder/DeckBuilderContent.tsx` (MODIFIED) — replaced SinnerGrid with compact components
- `frontend/src/lib/constants.ts` (MODIFIED) — added COMPACT_IDENTITY dimensions

## Verification
- Build: TypeScript compiles clean (pre-existing error in useKeywordFormatter.ts unrelated)
- Manual: user verified identity row and ego grid layout through multiple iterations

## Issues & Resolutions
- `Identity` type deprecated → switched to `IdentityListItem`
- `replace_all` on "Identity" mangled compound words → full file rewrite to fix
- `auto-fill` grid for ego grid fit 8 per row instead of 6 → switched to explicit `lg:grid-cols-6` breakpoints
- CSS Grid stretched ego cells across full width → added `w-fit` to constrain grid to content width
- Flexbox wrap didn't enforce 2x6 → back to CSS Grid with `w-fit`
- Spec said `getRarityIconPath` for uptie icon (wrong — that's star rarity) → corrected to `getEGOTierIconPath(uptie)` which shows roman numeral tier icons
- Unused constants (`COMPACT_EGO_CELL`) after layout approach change → removed

## Learnings
- `Edit` with `replace_all: true` is dangerous for short strings embedded in compound words — always use targeted edits or full rewrites
- CSS Grid `auto-fill` doesn't cap column count — use explicit responsive breakpoint classes (`lg:grid-cols-6`) when exact column count matters
- `w-fit` on CSS Grid prevents columns from stretching to fill available width — essential for content-width grids
- `getEGOTierIconPath` produces generic tier roman numerals usable for both identity uptie and EGO threadspin
