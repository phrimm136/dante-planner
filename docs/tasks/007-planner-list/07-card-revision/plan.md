# Execution Plan: Planner Card Reconstruction

## Execution Overview

This task reconstructs planner cards in both personal and community list views for visual consistency:

1. **Data Layer** - Add keywords to PlannerSummary type and extract in listPlanners()
2. **Constants** - Add RECOMMENDED_THRESHOLD constant
3. **Published Card** - Add star indicator for recommended planners (upvotes >= 10)
4. **Personal Card** - Add keywords display, restructure indicators, fix title size

All changes are frontend-only with no backend modifications required.

---

## Dependency Analysis

### Files Being Modified

| File | Impact | Depends On | Used By |
|------|--------|------------|---------|
| `types/PlannerTypes.ts` | Low | None | usePlannerStorage, useMDUserPlannersData, usePlannerSaveAdapter |
| `lib/constants.ts` | Low | None | PlannerCard.tsx |
| `hooks/usePlannerStorage.ts` | Low | PlannerTypes | useMDUserPlannersData |
| `components/plannerList/PlannerCard.tsx` | Medium | PlannerListTypes, constants | PlannerMDGesellschaftPage |
| `routes/PlannerMDPage.tsx` | Medium | PlannerTypes, constants | Router |

### Ripple Effect Map

- `PlannerSummary` type change → Backward compatible (optional field)
- `constants.ts` addition → No ripple (additive)
- `PlannerCard.tsx` layout → Affects only PlannerMDGesellschaftPage
- `PersonalPlannerCard` → Isolated within PlannerMDPage

### High-Risk Modifications

- **usePlannerStorage.ts**: Must type-guard for MD planners (RR has no keywords)
  - Mitigation: Check `config.type === 'MIRROR_DUNGEON'` before accessing selectedKeywords

---

## Execution Order

### Phase 1: Data Layer

1. **types/PlannerTypes.ts**: Add `selectedKeywords?: string[]` to PlannerSummary
   - Depends on: none
   - Enables: F1 (keywords in personal cards)

2. **lib/constants.ts**: Add `RECOMMENDED_THRESHOLD = 10` + Update `MD_CATEGORY_STYLES` colors
   - Depends on: none
   - Enables: F2 (star indicator threshold), F4 (floor badge colors)
   - Color changes:
     - 5F: bg-green-600 → bg-orange-500 text-white
     - 10F: bg-blue-600 → bg-red-500 text-white
     - 15F: bg-purple-600 → bg-white text-black

3. **hooks/usePlannerStorage.ts**: Extract selectedKeywords in listPlanners()
   - Depends on: Step 1
   - Enables: F1 (keywords data available)
   - Guard: Check MIRROR_DUNGEON type before accessing content

### Phase 2: UI Components

4. **components/plannerList/PlannerCard.tsx**: Add star indicator upper-right
   - Depends on: Step 2
   - Enables: F2 (star for recommended)
   - Pattern: Conditional Star icon when upvotes >= threshold

5. **routes/PlannerMDPage.tsx**: Reconstruct PersonalPlannerCard
   - Depends on: Steps 1, 3
   - Enables: F1, F3 (keywords display, unified layout)
   - Changes:
     - Add keywords display (copy PlannerCard pattern)
     - Restructure top row: floor+keywords (left), indicator (right)
     - Fix title: text-base → text-sm
     - Add Published indicator state

### Phase 3: Verification

6. **Manual Testing**: Verify all features across viewports and auth states
   - Depends on: Steps 1-5

---

## Verification Checkpoints

- **After Step 3**: Keywords extracted - listPlanners() returns summaries with selectedKeywords
- **After Step 4**: Star indicator - /planner/md/gesellschaft shows star for 10+ upvotes
- **After Step 5**: Keywords + layout - /planner/md shows keywords inline, text-sm title

---

## Risk Mitigation

| Risk | Step | Mitigation |
|------|------|------------|
| Empty keywords | 5 | Show only floor badge when length === 0 |
| Max keywords (3) | 5 | Hide "+N" when count equals MAX |
| 4+ keywords | 5 | Show 3 chips + "+N" (existing pattern) |
| Guest user | 5 | Show "Draft" badge |
| RR planners | 3 | Type-guard for MIRROR_DUNGEON |

---

## Pre-Implementation Validation

| Category | Check | Status |
|----------|-------|--------|
| Reference: keyword pattern | PlannerCard.tsx:128-144 | ✓ |
| Reference: PersonalPlannerCard | PlannerMDPage.tsx | ✓ |
| Reference: listPlanners() | usePlannerStorage.ts | ✓ |
| Contract: PlannerSummary | Optional field extension | ✓ |
| Contract: MDPlannerContent | selectedKeywords is string[] | ✓ |
| Dependency: MAX_KEYWORDS_DISPLAY | Exists (=3) | ✓ |
| Dependency: PLANNER_STATUS_BADGE_STYLES | Exists | ✓ |

---

## Rollback Strategy

- **Steps 1-2**: Pure additions - no runtime impact if unused
- **Step 3**: Catch error, return summary without keywords (optional field)
- **Step 4**: Remove conditional Star icon if layout breaks
- **Step 5**: Git revert PersonalPlannerCard if broken
- **Safe stops**: After Step 3 (data complete), After Step 4 (published card done)
