# Code: Granular Suspense for Identity Detail Page

## What Was Done
- Split `useIdentityDetailData` into `useIdentityDetailSpec` (no language) + `useIdentityDetailI18n` (suspends)
- Created suspending components: `IdentityHeaderI18n`, `SkillI18n`, `PassiveI18n`, `SanityI18n`, `TraitsI18n`
- Refactored page to use spec hook in shell, wrap i18n in micro-Suspense boundaries
- Split `PassiveI18n` into `PassiveCard` (base) + `PassiveCardWithSuspense` (wrapper)
- Consolidated skeleton to shared `StyledNameSkeleton` in `StyledName.tsx`
- Renamed `StyledSkillName.tsx` → `StyledName.tsx` for generic naming

## Files Changed

### New Files
- `hooks/useTraitsI18n.ts`
- `hooks/useIdentityDetailData.test.ts`
- `components/identity/TraitsI18n.tsx`
- `components/identity/TraitsI18n.test.tsx`
- `components/common/StyledName.tsx` (renamed from StyledSkillName.tsx)

### Modified Files
- `hooks/useIdentityDetailData.ts`
- `components/identity/IdentityHeaderI18n.tsx`
- `components/identity/SkillI18n.tsx`
- `components/identity/SkillInfoPanel.tsx`
- `components/identity/SkillDescription.tsx`
- `components/identity/SkillCard.tsx`
- `components/identity/PassiveI18n.tsx`
- `components/identity/SanityI18n.tsx`
- `components/identity/TraitsDisplay.tsx`
- `routes/IdentityDetailPage.tsx`
- `routes/IdentityDetailPage.test.tsx`

## Verification Results
- Build: PASS
- Tests: 26/26 PASS (identity-related)
- Manual UI: 7/7 PASS (language switching shows inline skeletons)
- Edge Cases: 5/5 PASS (rapid switching, cached data, error boundary)

## Issues & Resolutions
- Duplicate skeleton code → Created shared `StyledNameSkeleton` component
- Passive structure mismatch with Skills → Split into base + wrapper pattern
- Test brittleness (hardcoded level 55) → Use `MAX_LEVEL` constant
- File naming (`StyledSkillName`) too specific → Renamed to `StyledName`
