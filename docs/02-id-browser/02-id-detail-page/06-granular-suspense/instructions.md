# Task: Granular Suspense for Identity Detail Page

## Description
Implement granular Suspense boundaries for the Identity detail page so that when the user changes language, the page layout stays visible while only i18n text elements show loading skeletons. This follows the existing pattern used in `IdentityCard` + `IdentityName` and filter dropdowns (`SeasonDropdown`, `AssociationDropdown`).

### Current Behavior (Problem)
- When language changes, `useIdentityDetailData` hook's i18n query key changes
- `useSuspenseQuery` throws a Promise on cache miss
- The entire `IdentityDetailContent` component suspends
- User sees full-page `DetailPageSkeleton` during language change

### Desired Behavior (Solution)
- Page layout (images, frames, panels) stays visible during language change
- Only i18n text elements show inline skeletons:
  - Identity name in header → skeleton
  - Skill names and descriptions → skeleton
  - Passive names and descriptions → skeleton
  - Panic info (from `usePanicInfo`) → skeleton (part of i18n boundary)
  - Trait names → skeleton (convert TraitsDisplay to Suspense pattern)
- Section labels (Status, Resistances, Stagger) use bundled `t()` which doesn't suspend
- Skeleton sizes derived from actual content dimensions (not standardized)

### Pattern to Follow
The **Suspense + Skeleton** pattern (same as IdentityCard):
1. **Suspending Hook**: Uses `useSuspenseQuery` with language in query key
2. **Suspending Component**: Calls the suspending hook internally
3. **Micro-Suspense Boundary**: Parent wraps component in `<Suspense fallback={<Skeleton />}>`

```
ParentComponent (no suspension - uses spec-only hook)
├── Static content (images, layout)
└── <Suspense fallback={<Skeleton />}>     ← Catches suspension here
      └── <I18nComponent />                 ← Suspends here
            └── useI18nHook()               ← Language change triggers suspend
```

## Research
- Pattern source: `IdentityCard.tsx` + `IdentityName.tsx` + `useIdentityListData.ts`
- Filter dropdown pattern: `SeasonDropdown.tsx` + `useFilterI18nData.ts`
- Existing skeleton components: `Skeleton` from shadcn/ui
- Panic info hook: `usePanicInfo.ts` - include in i18n Suspense boundary
- TraitsDisplay: Currently uses `useEffect` + `useState` - convert to Suspense pattern for consistency
- Skeleton sizing: Analyze actual content dimensions in each section to derive skeleton sizes

## Scope
Files to READ for context:
- `frontend/src/components/identity/IdentityCard.tsx` - Micro-Suspense pattern
- `frontend/src/components/identity/IdentityName.tsx` - Suspending component pattern
- `frontend/src/hooks/useIdentityListData.ts` - Split hook pattern (spec vs i18n)
- `frontend/src/routes/IdentityPage.tsx` - Filter Suspense boundaries (lines 193-212)
- `frontend/src/components/common/SeasonDropdown.tsx` - Dropdown Suspense pattern
- `frontend/src/hooks/useFilterI18nData.ts` - Suspending i18n hook
- `frontend/src/routes/IdentityDetailPage.tsx` - Current implementation
- `frontend/src/hooks/useIdentityDetailData.ts` - Hook to split
- `frontend/src/components/identity/TraitsDisplay.tsx` - Convert from useEffect to Suspense
- `frontend/src/hooks/usePanicInfo.ts` - Include in i18n boundary

## Target Code Area
Files to CREATE or MODIFY:

### Hooks (modify)
- `frontend/src/hooks/useIdentityDetailData.ts`
  - Add `useIdentityDetailSpec(id)` - spec only, no language key
  - Add `useIdentityDetailI18n(id)` - i18n only, suspends on language change
  - Keep `useIdentityDetailData` for backward compatibility (mark as deprecated)

### New Suspending Components (create)
- `frontend/src/components/identity/IdentityHeaderI18n.tsx` - Suspending name display
- `frontend/src/components/identity/SkillI18n.tsx` - Suspending skill name/desc
- `frontend/src/components/identity/PassiveI18n.tsx` - Suspending passive name/desc
- `frontend/src/components/identity/SanityI18n.tsx` - Suspending panic info
- `frontend/src/components/identity/TraitsI18n.tsx` - Suspending trait names (refactor from TraitsDisplay)

### Components to Refactor (modify)
- `frontend/src/components/identity/TraitsDisplay.tsx` - Convert from useEffect+useState to Suspense pattern

### Page (modify)
- `frontend/src/routes/IdentityDetailPage.tsx`
  - Use `useIdentityDetailSpec` in shell (non-suspending)
  - Wrap i18n components in `<Suspense fallback={<Skeleton />}>`

## System Context (Senior Thinking)
- Feature domain: Identity Browser / Detail Page
- Core files: `useIdentityDetailData.ts`, `IdentityDetailPage.tsx`
- Cross-cutting concerns:
  - i18n (react-i18next + TanStack Query for fetched translations)
  - Suspense boundaries (React 18+)
  - Query cache (TanStack Query)

## Impact Analysis
- Files being modified:
  - `useIdentityDetailData.ts` (Medium impact - adds new exports, keeps old one)
  - `IdentityDetailPage.tsx` (Low impact - page isolated)
- What depends on `useIdentityDetailData`:
  - Only `IdentityDetailPage.tsx` currently uses it
- Potential ripple effects: None - adding new hooks doesn't break existing code
- High-impact files to watch: None for this task

## Risk Assessment
- Edge cases:
  - Initial page load should show `DetailPageSkeleton` (unchanged behavior)
  - Language change mid-render should handle gracefully
  - Network failure during i18n fetch should show error boundary
- Performance: Multiple small Suspense boundaries vs one large one
  - Trade-off: More network requests but better perceived performance
- Backward compatibility: Keep original `useIdentityDetailData` working

## Testing Guidelines

### Manual UI Testing
1. Navigate to Identity detail page (e.g., `/identity/10101`)
2. Verify page loads completely with all sections visible
3. Open language selector in header
4. Switch language (e.g., EN → KR)
5. **Verify layout stays visible** - Status panel, Resistance panel, Stagger panel, images
6. **Verify i18n text shows skeletons**:
   - Identity name in header shows skeleton, then updates
   - Skill names/descriptions show skeleton, then update
   - Passive names/descriptions show skeleton, then update
   - Panic info shows skeleton, then update
   - Trait names show skeleton, then update
7. Switch language back (KR → EN)
8. **Verify same granular loading behavior**
9. Switch language rapidly multiple times
10. Verify no crashes or stuck loading states

### Automated Functional Verification
- [ ] Spec data loads without language dependency: Query key has no language
- [ ] I18n data suspends on language change: Query key includes language
- [ ] Header name shows skeleton during language change
- [ ] Skills section shows skeleton during language change
- [ ] Passives section shows skeleton during language change
- [ ] Sanity section shows skeleton during language change
- [ ] Traits section shows skeleton during language change
- [ ] Static labels (Status, Resistances, Stagger) remain visible during language change
- [ ] Skeleton dimensions match actual content dimensions

### Edge Cases
- [ ] Initial load: Full page skeleton shown (unchanged behavior)
- [ ] Cached language: Instant switch with no skeleton (data already in cache)
- [ ] Network error on i18n: Error boundary catches, shows error UI
- [ ] Rapid language switching: No race conditions or stale data

### Integration Points
- [ ] Query cache: Verify i18n data is cached per language
- [ ] Route navigation: Verify switching identities (different ID) still works
- [ ] Mobile layout: Verify tabs (Skills/Passives/Sanity) work with granular Suspense
