# Research: Home Page Implementation

## Spec Ambiguities

**None blocking.** Minor assumptions made:
- Date headers: Exact date format "Jan 9, 2025" (not relative)
- Card navigation: TanStack Router `<Link>` pattern
- Tab switching: Suspense boundary per existing patterns

## Spec-to-Code Mapping

| Requirement | Action | File |
|-------------|--------|------|
| Route registration | DONE | `lib/router.tsx` (line 116, indexRoute exists) |
| Home page | IMPLEMENT | `routes/HomePage.tsx` |
| Recently Released | CREATE | `components/home/RecentlyReleasedSection.tsx` |
| Community Plans | CREATE | `components/home/CommunityPlansSection.tsx` |
| Entity cards | REUSE | `IdentityCard.tsx`, `EGOCard.tsx` |
| Planner cards | REUSE | `PublishedPlannerCard.tsx` |
| i18n strings | ADD | `static/i18n/{lang}/common.json` |

## Pattern Enforcement

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `HomePage.tsx` | `routes/IdentityPage.tsx` | Suspense shell, container layout |
| `RecentlyReleasedSection.tsx` | `components/identity/IdentityList.tsx` | Card grid, sorting, responsive |
| `CommunityPlansSection.tsx` | `routes/PlannerMDGesellschaftPage.tsx` | Tabs, data hook, empty state |

## Existing Utilities to Reuse

| Category | Location | Function |
|----------|----------|----------|
| Sorting | `lib/entitySort.ts` | `sortByReleaseDate()`, `sortEGOByDate()` |
| Grid | `components/common/ResponsiveCardGrid.tsx` | Auto-fill card layout |
| Constants | `lib/constants.ts` | `CARD_GRID.WIDTH.IDENTITY`, `CARD_GRID.WIDTH.PLANNER` |
| Data hooks | `hooks/useIdentityListData.ts` | `useIdentityListSpec()` |
| Data hooks | `hooks/useEGOListData.ts` | `useEGOListSpec()` |
| Data hooks | `hooks/useMDGesellschaftData.ts` | Community planner queries |

## Gap Analysis

### Missing (to create)
- `RecentlyReleasedSection.tsx` - left column component
- `CommunityPlansSection.tsx` - right column component
- Date grouping utility (small helper)
- i18n keys: `pages.home.*` (5-10 keys)

### Can Reuse (no changes)
- IdentityCard, EGOCard, PublishedPlannerCard
- ResponsiveCardGrid
- sortByReleaseDate, sortEGOByDate
- All data hooks
- CARD_GRID constants

## Cross-Reference Validation

| Layer | Reference | Actual | Match |
|-------|-----------|--------|-------|
| Identity spec | `useIdentityListSpec()` | Static JSON data | ✓ |
| EGO spec | `useEGOListSpec()` | Static JSON data | ✓ |
| Community plans | `useMDGesellschaftData()` | API endpoints | ✓ |
| Card components | IdentityCard/EGOCard props | Spec data structure | ✓ |
| Grid layout | ResponsiveCardGrid | CARD_GRID constants | ✓ |

## Technical Constraints

- Suspense boundaries required for useSuspenseQuery
- Mixed ID/EGO sorting needs normalized updateDate field
- Responsive breakpoints: 768px (tablet), 1024px (desktop)
- Tab state local to CommunityPlansSection (useState)
- Date formatting: Use date-fns with i18n locale

## Testing Summary

### Manual UI (13 steps)
1. Navigate `/` - verify layout
2. Click Create Plan - navigate to `/planner/md/new`
3. Verify Recently Released cards grouped by date
4. Click Identity/EGO cards - navigate to detail pages
5. Click browse links - navigate to list pages
6. Verify Community Plans tabs work
7. Click Browse All - navigate to Gesellschaft
8. Test responsive layout at 800px and 375px

### Automated
- Route renders correct component
- Navigation links work
- Tab switching updates content
- Responsive layout breakpoints
- Empty state displays correctly

### Edge Cases
- No community plans: empty state message
- API error: error boundary fallback
- Language switch: deferred i18n loading
