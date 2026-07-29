# Code: Identity Card Level and Name Display

## What Was Done

- Added `IdentityName` component with `\n` line break rendering via JSX split/map
- Added Layer 5 info panel to `IdentityCard` with level display and Suspense-wrapped name
- Imported `Suspense`, `Skeleton`, `IdentityName`, and `MAX_LEVEL` constant
- Applied white text with drop-shadow for readability on all card backgrounds
- Used `line-clamp-3` to handle long/multi-line names gracefully
- Created 2-line skeleton fallback matching expected content height

## Files Changed

- `frontend/src/components/identity/IdentityName.tsx` (new)
- `frontend/src/components/identity/IdentityCard.tsx` (modified)

## Verification Results

- Checkpoint 1 (IdentityName line breaks): PASS
- Checkpoint 2 (Layer 5 info panel): PASS
- F1 Level display "Lv. 55": PASS
- F2 Name with line breaks: PASS
- F3 Suspense skeleton: PASS
- D3 SinnerDeckCard overlay compatibility: PASS
- TypeScript build: PASS
- Manual verification at `/identity`: PASS
- Manual verification at `/planner/md/new`: PASS

## Issues & Resolutions

- Gradient background proposed → Skipped (frame already provides dark gradient)
- XSS concern raised in review → False positive (React auto-escapes text content)
- `key={index}` flagged → Changed to `key={\`line-${index}\`}` for cleaner pattern
- Single-line skeleton mismatch → Updated to 2-line skeleton with staggered widths

## Pattern References

- Layer 5 structure: `EGOCard.tsx` lines 100-143
- Name component: `EGOName.tsx` (same Suspense pattern)
- Constants: `MAX_LEVEL` from `lib/constants.ts`
