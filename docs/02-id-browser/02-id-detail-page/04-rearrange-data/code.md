# Code Documentation: Modular Detail Page Layout

## What Was Done

- Added DETAIL_PAGE constants (breakpoint, column ratios, sticky offset) to constants.ts
- Added SANITY_INDICATOR_COLORS constant for semantic color management
- Installed shadcn/ui Slider and ScrollArea components
- Created DetailEntitySelector component (uptie buttons + level slider for identity/ego/egoGift)
- Created DetailRightPanel component (sticky selector + ScrollArea content)
- Created MobileDetailTabs component (Skills/Passives/Sanity tabs)
- Refactored DetailPageLayout for 4:6 ratio desktop, mobile tabs support
- Refactored IdentityDetailPage with Suspense wrapper, uptie/level state, cn() styling

## Files Changed

### New Components
- `frontend/src/components/common/DetailEntitySelector.tsx`
- `frontend/src/components/common/DetailEntitySelector.test.tsx`
- `frontend/src/components/common/DetailRightPanel.tsx`
- `frontend/src/components/common/MobileDetailTabs.tsx`
- `frontend/src/components/ui/slider.tsx`
- `frontend/src/components/ui/scroll-area.tsx`
- `frontend/src/routes/IdentityDetailPage.test.tsx`

### Modified
- `frontend/src/components/common/DetailPageLayout.tsx`
- `frontend/src/routes/IdentityDetailPage.tsx`
- `frontend/src/lib/constants.ts`

### Documentation
- `docs/.../research.md` - Spec-to-code mappings, pattern decisions
- `docs/.../status.md` - Progress tracking

## Verification Results

- TypeScript compile: PASS (yarn tsc --noEmit)
- Tests: PASS (42/42 tests)
- Code Review Round 1: NEEDS WORK (5 issues)
- Code Review Round 2: ACCEPTABLE (all fixed)
- Code Review Round 3: ACCEPTABLE (final verification)

## Issues & Resolutions

- Dynamic Tailwind `grid-cols-${n}` → Replaced with cn() explicit conditionals
- Template literals in className → Replaced with cn() utility throughout
- DETAIL_PAGE constants defined but unused → Applied in DetailPageLayout.tsx
- Missing Suspense boundary → Added wrapper/content pattern
- Hardcoded colors (bg-red-500) → Created SANITY_INDICATOR_COLORS constant
- research.md ScrollArea docs incorrect → Updated to match actual implementation
- Breakpoint 768px too narrow → Changed to 1024px, documented rationale in research.md
