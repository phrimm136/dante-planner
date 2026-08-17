# Execution Plan: Home Page Implementation

## Planning Gaps

**None.** All technical requirements clear from research.

## Execution Overview

Build landing page incrementally: i18n → utility → data hook → section components → page integration.

**Strategy**: Each step verifiable independently, safe rollback at any point.

## Dependency Analysis

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| `routes/HomePage.tsx` | Low (isolated) | New components, existing hooks | Router (already wired) |
| `hooks/useHomePageData.ts` | Low (new) | useIdentityListSpec, useEGOListSpec | HomePage only |
| `components/home/RecentlyReleasedSection.tsx` | Low (new) | IdentityCard, EGOCard, entitySort | HomePage only |
| `components/home/CommunityPlansSection.tsx` | Low (new) | PublishedPlannerCard, useMDGesellschaftData | HomePage only |
| `static/i18n/{lang}/common.json` | Medium | N/A | All translated components |

### Ripple Effect Map

- useHomePageData changes → only HomePage affected
- common.json keys added → must match in all 4 language files
- Card components → NOT modified (reuse only)

### High-Risk Modifications

- **common.json (4 files)**: Additive only, must add identical keys to EN/KR/JP/CN

## Execution Order

### Phase 1: i18n Keys

1. **EN/common.json**: Add `pages.home.*` keys
   - Enables: F1 (tagline), F2 (CTA text), all section headers

2. **KR/common.json**: Add identical keys

3. **JP/common.json**: Add identical keys

4. **CN/common.json**: Add identical keys

### Phase 2: Utility

5. **Date grouping utility** (inline in hook or lib/dateUtils.ts)
   - Enables: F4 (date headers)
   - Function: Group entities by updateDate

### Phase 3: Data Hook

6. **hooks/useHomePageData.ts**
   - Depends on: Step 5
   - Enables: F3, F4 (recently released data)
   - Combines useIdentityListSpec + useEGOListSpec
   - Sorts and groups by date, limits to ~16 items

### Phase 4: Section Components

7. **components/home/RecentlyReleasedSection.tsx**
   - Depends on: Steps 1-6
   - Enables: F3, F4, F5
   - Pattern: IdentityList.tsx (card grid rendering)
   - Uses: IdentityCard, EGOCard, Link, ResponsiveCardGrid

8. **components/home/CommunityPlansSection.tsx**
   - Depends on: Steps 1-4
   - Enables: F6, F7, F8
   - Pattern: PlannerMDGesellschaftPage.tsx (tabs, data fetching)
   - Uses: PublishedPlannerCard, Tabs (shadcn), useState

### Phase 5: Page Integration

9. **routes/HomePage.tsx**
   - Depends on: Steps 7-8
   - Enables: F1, F2, F9, F10, all features integrated
   - Pattern: IdentityPage.tsx (Suspense shell)
   - Layout: Hero + two-column responsive grid

### Phase 6: Verification

10. **Manual verification**: All features and responsive behavior

## Verification Checkpoints

| After Step | Verify |
|------------|--------|
| 4 | All 4 i18n files have identical `pages.home.*` keys |
| 6 | Hook returns combined sorted data |
| 7 | RecentlyReleasedSection renders grouped cards |
| 8 | CommunityPlansSection tabs switch content |
| 9 | Full page loads, all interactions work, responsive correct |

## Risk Mitigation

| Risk | Step | Mitigation |
|------|------|------------|
| Empty community plans | 8-9 | Add empty state with i18n key `noCommunityPlans` |
| API failure | 8-9 | Isolated Suspense boundary with error fallback |
| Waterfall Suspense | 9 | Parallel Suspense for left/right columns |
| Spec list overhead | 6 | TanStack Query caching (7-day stale time) |

## Pre-Implementation Validation

All validated in research:
- Route `/` exists (router.tsx line 113-117)
- useIdentityListSpec, useEGOListSpec hooks exist
- useMDGesellschaftData supports mode param
- IdentityCard, EGOCard, PublishedPlannerCard exist
- sortByReleaseDate, sortEGOByDate exist in entitySort.ts

## Rollback Strategy

| Failure Point | Action |
|---------------|--------|
| Steps 1-4 (i18n) | Revert key additions |
| Steps 5-8 (new files) | Delete new files |
| Step 9 (HomePage) | Restore from git |

**Safe stopping points**: After Step 4, After Step 8
