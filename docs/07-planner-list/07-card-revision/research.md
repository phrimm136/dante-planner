# Research: Planner Card Reconstruction

## Clarifications Resolved

- **Indicator area spacing**: Reserve space in upper-right even when empty (auth+syncOFF) for layout stability

---

## Spec-to-Code Mapping

| Requirement | File | Modification |
|-------------|------|--------------|
| Keywords in PlannerSummary | `types/PlannerTypes.ts` | Add `selectedKeywords?: string[]` |
| Keywords extraction | `hooks/usePlannerStorage.ts` | Extract from content in `listPlanners()` |
| Floor + keywords (top-left) | `PlannerCard.tsx`, `PlannerMDPage.tsx` | Restructure top row layout |
| Star indicator (published) | `PlannerCard.tsx` | Add star when `upvotes >= 10` |
| Personal indicators | `PlannerMDPage.tsx` | 6 mutually exclusive states |
| Title unification | `PlannerMDPage.tsx` | Change `text-base` → `text-sm` |
| Threshold constant | `lib/constants.ts` | Add `RECOMMENDED_THRESHOLD = 10` |
| Floor badge colors | `lib/constants.ts` | Update `MD_CATEGORY_STYLES`: 5F=orange, 10F=red, 15F=white |

---

## Spec-to-Pattern Mapping

| Requirement | Pattern Source |
|-------------|----------------|
| Keyword chips | `PlannerCard.tsx:128-144` (slice + "+N" overflow) |
| Status badge styling | `PLANNER_STATUS_BADGE_STYLES` in constants.ts |
| Category badge | `MD_CATEGORY_STYLES` in constants.ts |
| Date formatting | `formatPlannerDate()` from lib/formatDate.ts |

---

## Pattern Enforcement

| Modified File | MUST Read First | Pattern to Copy |
|---------------|-----------------|-----------------|
| PersonalPlannerCard | `PlannerCard.tsx` | Top row layout, keyword rendering |
| usePlannerStorage.ts | Current `listPlanners()` | Summary extraction pattern |

---

## Cross-Reference Validation

| Layer | Reference | Actual | Match |
|-------|-----------|--------|-------|
| PlannerSummary | No keywords field | MDPlannerContent has selectedKeywords | ✗ Add field |
| PublicPlanner | Has selectedKeywords | API returns keywords | ✓ |
| Upvotes | PublicPlanner.upvotes | Available in published cards | ✓ |
| Sync state | useUserSettingsQuery | Returns syncEnabled | ✓ |

---

## Existing Utilities

| Category | Location | Available |
|----------|----------|-----------|
| Badge styles | constants.ts | `PLANNER_STATUS_BADGE_STYLES`, `MD_CATEGORY_STYLES` |
| Keyword limit | constants.ts | `PLANNER_LIST.MAX_KEYWORDS_DISPLAY` (3) |
| Date format | lib/formatDate.ts | `formatPlannerDate()` |
| Username format | lib/formatUsername.ts | `formatUsername()` |
| Icons | lucide-react | Star, CheckCircle, ThumbsUp, Eye |

---

## Gap Analysis

**Missing:**
- `selectedKeywords` in PlannerSummary type
- Keywords extraction in listPlanners()
- Star indicator in PlannerCard
- RECOMMENDED_THRESHOLD constant

**Needs Modification:**
- PlannerCard.tsx: Top row (add star, move keywords inline)
- PersonalPlannerCard: Add keywords, restructure indicators, fix title size
- usePlannerStorage.ts: Extract keywords in listPlanners()

**Reusable:**
- Keyword chip rendering logic (PlannerCard:128-144)
- All badge styling constants
- Date/username formatting utilities

---

## Testing Requirements

### Manual UI Tests
- Personal: Keywords display with floor badge
- Personal: 6 indicator states (guest/syncOFF/unsynced/synced/published/unpublished-change)
- Published: Star appears at 10+ upvotes
- Both: Title uses text-sm
- Viewport: 320px mobile, 768px tablet

### Automated Tests
- Keywords: Max 3 shown, "+N" overflow
- Indicators: Mutually exclusive
- Star threshold: >= 10 upvotes

---

## Technical Constraints

- `selectedKeywords` must be optional (backward compatible)
- Reserve indicator space even when empty
- No backend changes needed
- Reuse existing constants verbatim
