# Task: Planner Card Reconstruction

## Description

Reconstruct the planner cards in both personal and community list views to achieve visual consistency and consolidate information display.

### Layout Changes (Both Card Types)

**Top Row:**
- Left side: Floor indicator badge (5F/10F/15F) followed by keyword chips inline (horizontal)
- Right side: Status indicator (varies by card type)
- Keywords display: Show up to 3 keywords with "+N" overflow indicator

**Title:**
- Unified `text-sm` font size for both card types (currently inconsistent: personal uses `text-base`, published uses `text-sm`)
- Keep `line-clamp-2` for truncation

### Personal Card Specifics

**Upper-right indicator (mutually exclusive):**
| Condition | Indicator |
|-----------|-----------|
| Guest user | "Draft" or nothing |
| Auth + sync OFF | (empty) |
| Auth + sync ON + `status='draft'` | "Unsynced" |
| Auth + sync ON + `status='saved'` + `!published` | ✓ Synced |
| Auth + sync ON + `published=true` + `status='draft'` | "Unpublished change" |
| Auth + sync ON + `published=true` + `status='saved'` | "Published" |

**Bottom section:**
- Date only (no author, no stats)
- Remove bookmark indicator from this position

### Published Card Specifics

**Upper-right indicator:**
| Condition | Indicator |
|-----------|-----------|
| `upvotes >= 10` | ⭐ Star (recommended) |
| otherwise | (empty) |

**Bottom section:**
- Stats row: upvotes, views
- Date + Author name

### Data Layer Changes

Keywords must be available in personal card data. Currently:
- `PublicPlanner` has `selectedKeywords` (used by published cards)
- `PlannerSummary` does NOT have `selectedKeywords` (used by personal cards)

The full planner content is already parsed in `listPlanners()` - keywords just need to be extracted into the summary.

## Research

- Current `PlannerCard.tsx` component structure and props
- Current `PersonalPlannerCard` implementation in `PlannerMDPage.tsx`
- Existing keyword chip rendering pattern (already in PlannerCard)
- Existing status badge styles in `PLANNER_STATUS_BADGE_STYLES`
- How sync status is currently determined (auth state, syncEnabled setting, planner status)

## Scope

Files to READ for context:
- `frontend/src/components/plannerList/PlannerCard.tsx` - Published card implementation
- `frontend/src/routes/PlannerMDPage.tsx` - Personal card implementation (PersonalPlannerCard)
- `frontend/src/hooks/usePlannerStorage.ts` - Summary extraction logic (listPlanners)
- `frontend/src/types/PlannerTypes.ts` - PlannerSummary type definition
- `frontend/src/types/MDPlannerListTypes.ts` - PublicPlanner type
- `frontend/src/lib/constants.ts` - Badge styles, existing constants

## Target Code Area

Files to CREATE or MODIFY:
- `frontend/src/types/PlannerTypes.ts` - Add `selectedKeywords?: string[]` to PlannerSummary
- `frontend/src/hooks/usePlannerStorage.ts` - Extract keywords in listPlanners()
- `frontend/src/components/plannerList/PlannerCard.tsx` - Add star indicator, adjust layout
- `frontend/src/routes/PlannerMDPage.tsx` - Refactor PersonalPlannerCard with keywords + consolidated indicators
- `frontend/src/lib/constants.ts` - Add `RECOMMENDED_THRESHOLD = 10`, update `MD_CATEGORY_STYLES` (5F=orange, 10F=red, 15F=white)

## System Context (Senior Thinking)

- **Feature domain**: Planner List (from architecture-map)
- **Core files**:
  - `routes/PlannerMDPage.tsx` (personal)
  - `routes/PlannerMDGesellschaftPage.tsx` (community)
  - `components/plannerList/PlannerCard.tsx`
  - `hooks/useMDUserPlannersData.ts`
- **Cross-cutting concerns**:
  - Auth state (`useAuthQuery`)
  - User settings (`useUserSettingsQuery` for syncEnabled)
  - Constants (`PLANNER_STATUS_BADGE_STYLES`, `MD_CATEGORY_STYLES`)
  - i18n (badge label translations)

## Impact Analysis

- **Files being modified**:
  - `PlannerTypes.ts` (Low - type addition only)
  - `usePlannerStorage.ts` (Low - extracting already-parsed data)
  - `PlannerCard.tsx` (Medium - published card UI)
  - `PlannerMDPage.tsx` (Medium - personal card UI)
  - `constants.ts` (Low - new constant)

- **What depends on these files**:
  - `PlannerSummary` type used by `useMDUserPlannersData`, `usePlannerSaveAdapter`
  - `PlannerCard` used by `PlannerMDGesellschaftPage`
  - Personal card used only in `PlannerMDPage`

- **Potential ripple effects**:
  - Adding optional field to `PlannerSummary` is backward compatible
  - Card height changes may affect grid appearance (test on various viewport sizes)

- **High-impact files to watch**: None of the modified files are in the high-impact category

## Risk Assessment

- **Edge cases**:
  - No keywords selected (empty array) - show only floor badge
  - Keyword count = MAX_DISPLAY (3) - hide "+N more"
  - Guest user viewing personal cards - show "Draft" or nothing
  - RR planners (future) - `selectedKeywords` only exists on MD content, guard with type check

- **Performance concerns**:
  - None - keywords already parsed, just not extracted

- **Backward compatibility**:
  - Adding optional `selectedKeywords` field won't break existing code
  - Existing planners in IndexedDB will have keywords (inside content)

- **Security considerations**: None - no user input handling changes

## Testing Guidelines

### Manual UI Testing

**Personal Cards (PlannerMDPage):**
1. Navigate to `/planner/md` as guest user
2. Create a new planner with keywords selected (Combustion, Slash)
3. Return to planner list
4. Verify keywords appear inline next to floor badge
5. Verify "Draft" indicator appears in upper-right (or nothing, based on final decision)
6. Log in with sync OFF
7. Verify indicator area is empty
8. Enable sync in settings
9. Return to planner list
10. Verify "Unsynced" indicator appears for draft planners
11. Manually save a planner
12. Verify "Synced" checkmark appears
13. Publish a planner
14. Verify "Published" indicator appears
15. Edit the published planner (create draft changes)
16. Verify "Unpublished change" indicator appears

**Published Cards (PlannerMDGesellschaftPage):**
1. Navigate to `/planner/md/gesellschaft`
2. Find a planner with keywords
3. Verify keywords appear inline next to floor badge
4. Find a planner with 10+ upvotes
5. Verify star indicator appears in upper-right
6. Find a planner with <10 upvotes
7. Verify no star indicator
8. Verify stats row (upvotes, views) and author name still appear at bottom

**Cross-viewport Testing:**
1. Test on mobile viewport (320px width)
2. Verify keywords don't overflow or wrap unexpectedly
3. Verify indicators don't collide with keywords
4. Verify card height remains reasonable

### Automated Functional Verification

- [ ] Keywords display: Up to 3 keywords shown with "+N" overflow
- [ ] Floor badge: Always visible at top-left
- [ ] Personal indicators: Mutually exclusive (only one shows at a time)
- [ ] Published star: Shows only when upvotes >= 10
- [ ] Title sizing: Both card types use `text-sm`
- [ ] Bottom section: Personal shows date only; Published shows stats + date + author

### Edge Cases

- [ ] Empty keywords: Only floor badge shown, no empty chip container
- [ ] Max keywords (3): No "+N" indicator when exactly 3 keywords
- [ ] 4+ keywords: Shows 3 chips + "+1" (or "+N")
- [ ] Guest user: Shows "Draft" or nothing in indicator area
- [ ] Sync disabled: Empty indicator area
- [ ] Long keyword names: Text truncated or chips wrap gracefully

### Integration Points

- [ ] Auth state: Indicator changes correctly when user logs in/out
- [ ] Sync setting: Indicator changes when sync is toggled in settings
- [ ] Planner save: Indicator updates from "Unsynced" to "Synced" after manual save
- [ ] Planner publish: Indicator updates to "Published" after publishing
