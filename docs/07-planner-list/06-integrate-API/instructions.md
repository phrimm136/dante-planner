# Task: Fix FE/BE Username API Mismatch in Planner List

## Description

The community planner list (`/planner/md/gesellschaft`) is broken because frontend and backend have diverged on the author username API contract. The backend sends username as two separate fields (`authorUsernameKeyword` + `authorUsernameSuffix`), but the frontend expects a single `authorName` field. This causes either validation errors or undefined author names in the UI.

**Required Changes:**
- Update frontend type definitions to match backend's two-field username structure
- Update Zod validation schemas to expect both username fields
- Create a utility function to compose and translate username from keyword + suffix
- Modify PlannerCard component to use the composition function
- Update test mocks to match the new API contract

**Username Display Format:**
The final displayed username should be: `"Faust-{translatedKeyword}-{suffix}"`
- Examples (EN): `"Faust-WCorp-AB123"`, `"Faust-Blade-XY789"`
- Examples (KR): `"파우스트-W사-AB123"`, `"파우스트-검계-XY789"`

**Translation Behavior:**
- `authorUsernameKeyword` is an enum value (e.g., `"W_CORP"`, `"BLADE_LINEAGE"`)
- Translate using `association.json` i18n namespace
- If translation missing, use raw keyword as fallback (graceful degradation)
- "Faust" prefix is also translated via `association.json["sinner"]`

**Where This Matters:**
- Published planners list (`/api/planner/md/published`)
- Recommended planners list (`/api/planner/md/recommended`)
- Any component displaying `PublicPlanner` type

## Research

Before implementation, investigate:

1. **Existing i18n Patterns:**
   - How does settings page format usernames? (Check `UsernameSection.tsx`)
   - Are there other places consuming `PublicPlanner` type besides `PlannerCard`?
   - How is `association.json` namespace loaded in the app?

2. **Backend API Contract:**
   - Verify exact field names in `PublicPlannerResponse.java` (lines 28-29)
   - Confirm both fields are non-nullable in backend
   - Check if any other DTOs send similar username structure

3. **Validation Flow:**
   - Does `PaginatedPlannersSchema` currently use `.passthrough()` (allowing extra fields)?
   - Where does validation error surface if schema expects wrong field?
   - Are there other schemas consuming `PublicPlannerSchema`?

4. **i18n Loading:**
   - When/where does app load `association.json` namespace?
   - Is it loaded before first render or lazy-loaded?
   - Does language switching trigger re-render of planner cards?

5. **Similar Formatter Patterns:**
   - Why is formatter being added to `formatDate.ts` instead of separate `formatUsername.ts`?
   - Are there other domain-specific formatters in the codebase?

## Scope

**Files to READ for context:**
- `backend/src/main/java/.../dto/planner/PublicPlannerResponse.java` - Backend DTO structure
- `static/i18n/EN/association.json` - Translation keys
- `static/i18n/KR/association.json` - Korean translations
- `frontend/src/lib/i18n.ts` - i18n configuration and namespace loading
- `frontend/src/hooks/useMDGesellschaftData.ts` - Where API response is validated
- `frontend/src/types/PlannerListTypes.ts` - Canonical PublicPlanner definition (supports MD + RR)
- `frontend/src/types/MDPlannerListTypes.ts` - Has duplicate PublicPlanner (MD-only), needs removal
- `frontend/src/routes/PlannerMDPage.tsx` - Personal planner list (uses PlannerSummary, not PublicPlanner)

## Target Code Area

**Files that will be CREATED:**
- None (all modifications to existing files)

**Files that will be MODIFIED:**
1. `frontend/src/lib/formatDate.ts` - Add `formatAuthorName()` utility
2. `frontend/src/types/PlannerListTypes.ts` - Replace `authorName` with two fields (canonical definition)
3. `frontend/src/types/MDPlannerListTypes.ts` - Remove duplicate `PublicPlanner`, add re-export from PlannerListTypes
4. `frontend/src/schemas/PlannerListSchemas.ts` - Update `PublicPlannerSchema`
5. `frontend/src/components/plannerList/PlannerCard.tsx` - Use formatter
6. `frontend/src/hooks/useMDGesellschaftData.test.tsx` - Update mock data

## System Context (Senior Thinking)

**Feature domain:** Planner List (Community View)

**Core files in this domain:**
- `routes/PlannerMDGesellschaftPage.tsx` - Community planner list page
- `routes/PlannerMDPage.tsx` - Personal planner list page (uses different type: PlannerSummary)
- `hooks/useMDGesellschaftData.ts` - Fetches published/recommended planners
- `hooks/useMDUserPlannersData.ts` - Fetches personal planners (no PublicPlanner usage)
- `hooks/useMDGesellschaftFilters.ts` - URL state management
- `types/PlannerListTypes.ts` - Canonical PublicPlanner definition (MD + RR support)
- `types/MDPlannerListTypes.ts` - MD-specific search params (will re-export PublicPlanner)
- `components/plannerList/PlannerCard.tsx` - Card display component (currently imports from PlannerListTypes)
- `components/plannerList/MDPlannerToolbar.tsx` - Search + mode toggle

**Cross-cutting concerns touched:**
- **Validation:** Zod schemas in `schemas/PlannerListSchemas.ts`
- **i18n:** Translation via `association.json` namespace
- **Type Safety:** TypeScript interfaces and Zod runtime validation alignment

**Data Flow:**
```
Backend: PublicPlannerResponse {authorUsernameKeyword, authorUsernameSuffix}
         ↓
Frontend API call: useMDGesellschaftData
         ↓
Zod validation: PaginatedPlannersSchema
         ↓
Component: PlannerCard
         ↓
Formatter: formatAuthorName(keyword, suffix) → i18n lookup → display string
```

## Impact Analysis

**Type Consolidation Strategy:**
- **Problem:** Two files define identical `PublicPlanner` (PlannerListTypes + MDPlannerListTypes)
- **Decision:** Use `PlannerListTypes.ts` as canonical source (supports MD + RR categories)
- **Action:** Remove duplicate from `MDPlannerListTypes.ts`, add re-export
- **Benefit:** Prepares for future RR planner type introduction, eliminates duplication

**Files being modified:**

| File | Impact Level | Reason |
|------|--------------|--------|
| `lib/formatDate.ts` | Low | Adding new export, no breaking changes to existing functions |
| `types/PlannerListTypes.ts` | Low | Canonical type definition, compile-time only (dev-only, not in prod) |
| `types/MDPlannerListTypes.ts` | Low | Remove duplicate, add re-export (consolidation, no breaking change) |
| `schemas/PlannerListSchemas.ts` | Medium | Validation schema change affects runtime behavior |
| `components/plannerList/PlannerCard.tsx` | Low | Isolated component, only used in planner list pages |
| `hooks/useMDGesellschaftData.test.tsx` | Low | Test-only file |

**What depends on these files:**

From architecture-map File Dependency Graph:
- `PublicPlannerSchema` is consumed by:
  - `useMDGesellschaftData.ts` (validation of API response)
  - Test files
- `PlannerCard` is consumed by:
  - `PlannerMDGesellschaftPage.tsx` (community list)
  - Possibly personal planner list (check if reused)
- `MDPlannerListTypes` is consumed by:
  - All planner list components and hooks
  - Query keys in `useMDGesellschaftData.ts`

**Potential ripple effects:**
- Schema validation failure will prevent list from loading (shows error boundary)
- Type change will cause TypeScript compile errors in any file destructuring `authorName`
- Formatter function affects display only - no data mutation

**High-impact files to watch:**
- None directly modified (all files are domain-isolated)
- Indirectly: If `PublicPlanner` type is used elsewhere (beyond gesellschaft page), those files will need updates too

**Files NOT affected (important to verify):**
- Personal planner list uses `PlannerSummaryResponse` (different DTO) - unaffected
- Planner detail page doesn't show author name - unaffected
- Settings username display uses different data source - unaffected

## Risk Assessment

**Edge cases to handle:**

1. **Missing translation key:**
   - If `authorUsernameKeyword = "UNKNOWN_FACTION"` not in `association.json`
   - Expected behavior: Use raw keyword as fallback (`defaultValue: keyword`)
   - Result: `"Faust-UNKNOWN_FACTION-AB123"` (degrades gracefully)

2. **Language switch during display:**
   - User changes language while viewing planner list
   - Expected behavior: i18next re-renders, usernames update to new language
   - Performance: Minimal (hash-based lookup, React Compiler memoizes)

3. **Partial missing fields (CLARIFIED):**
   - Both empty/null: Return `"Unknown Author"`
   - Keyword missing: Return `"Faust-???-AB123"` (placeholder for keyword)
   - Suffix missing: Return `"Faust-WCorp-????"` (placeholder for suffix)
   - Translation missing: Return `"Faust-{RAW_KEYWORD}-AB123"` (i18n fallback)

4. **i18n not loaded:**
   - What if `formatAuthorName()` called before `association.json` loaded?
   - Current: App wraps routes in Suspense, i18n loads during init
   - Risk: Very low (i18n loads before any component renders)

5. **Deleted user account:**
   - Backend reassigns to sentinel user (id=0) with valid keyword + suffix
   - Frontend impact: None (formats sentinel username normally)

**Performance concerns:**
- i18n lookup on every card render (potentially 20 cards per page)
- Mitigation: Hash-based O(1) lookup, React Compiler memoizes, negligible overhead
- If proven problematic: Backend could pre-format username (architecture alternative)

**Backward compatibility:**
- Breaking change: Frontend expects different API contract
- Impact: Dev-only (not yet in production per user confirmation)
- Safe to change: No migration needed

**Security considerations:**
- None (display-only formatting, no user input involved)
- `authorUsernameKeyword` is enum from backend (trusted source)
- `authorUsernameSuffix` is generated server-side (5-char random alphanumeric)

**Type safety risks:**
- Two separate type definitions existed: `MDPlannerListTypes.ts` and `PlannerListTypes.ts`
- Risk: Only updating one leads to divergence
- Mitigation: Remove duplicate from MDPlannerListTypes, re-export from canonical PlannerListTypes

## Testing Guidelines

### Manual UI Testing

1. Start backend and frontend in dev mode
2. Ensure at least one planner is published (create via `/planner/md/new` if needed)
3. Navigate to `/planner/md/gesellschaft`
4. Verify planner cards display with author names in format `"Faust-{keyword}-{suffix}"`
5. Change language to Korean (via settings or language switcher)
6. Verify author names re-translate: `"Faust-WCorp-AB123"` → `"파우스트-W사-AB123"`
7. Change language to Japanese
8. Verify author names use Japanese translations (or fallback if not available)
9. Switch back to English
10. Refresh page (hard reload)
11. Verify author names still display correctly after page load
12. Open browser console
13. Verify no validation errors, warnings, or `undefined` values logged
14. Switch to "Best" mode (if different from "Published")
15. Verify author names display correctly in recommended list too

### Automated Functional Verification

- [ ] **Type safety:** `yarn tsc --noEmit` passes with no errors
- [ ] **Schema validation:** API response passes `PaginatedPlannersSchema.safeParse()`
- [ ] **Username composition:** `formatAuthorName("W_CORP", "AB123")` returns `"Faust-WCorp-AB123"` (EN)
- [ ] **Translation:** `formatAuthorName("W_CORP", "AB123")` returns `"파우스트-W사-AB123"` (KR)
- [ ] **Fallback:** `formatAuthorName("UNKNOWN", "XYZ99")` returns `"Faust-UNKNOWN-XYZ99"` (raw keyword)
- [ ] **Component render:** PlannerCard renders author name without `undefined` or blank values
- [ ] **Mock data:** Test mocks use new schema structure (two fields instead of `authorName`)

### Edge Cases

- [ ] **Empty keyword:** `formatAuthorName("", "AB123")` logs warning and returns `"Unknown Author"`
- [ ] **Empty suffix:** `formatAuthorName("W_CORP", "")` logs warning and returns `"Unknown Author"`
- [ ] **Both empty:** `formatAuthorName("", "")` returns `"Unknown Author"`
- [ ] **Null inputs:** (TypeScript should prevent, but verify) Formatter handles gracefully
- [ ] **Missing i18n key:** Keyword not in `association.json` uses raw value as fallback
- [ ] **Missing namespace:** If `association.json` fails to load, formatter doesn't crash app
- [ ] **Language switch:** Changing language triggers re-render and re-translation
- [ ] **Long keyword:** Very long faction name doesn't break card layout (use CSS truncation)

### Integration Points

- [ ] **Published list endpoint:** `/api/planner/md/published` response validates correctly
- [ ] **Recommended list endpoint:** `/api/planner/md/recommended` response validates correctly
- [ ] **Pagination:** Author names display correctly on all pages (page 0, 1, 2...)
- [ ] **Search filter:** Filtered results still show author names correctly
- [ ] **Category filter:** Filtered by category (5F, 10F, 15F) still shows author names
- [ ] **Mode toggle:** Switching between "Published" and "Best" preserves author name display
- [ ] **Query invalidation:** After voting/bookmarking, refetched data still shows author names
- [ ] **Real-time updates:** If planner SSE updates fire, author names remain stable

### Regression Checks

- [ ] **Personal planner list:** `/planner/md` (personal) still works (uses different DTO)
- [ ] **Planner detail page:** `/planner/md/$id` still loads (doesn't use PublicPlanner type)
- [ ] **Settings username display:** Settings page username section unaffected
- [ ] **Existing planners:** Old published planners show author names (no migration needed)

## Implementation Notes

### Code Quality Requirements

**Formatter Function:**
```typescript
/**
 * Format author username from keyword and suffix.
 *
 * Composes username in format: "Faust-{translatedKeyword}-{suffix}"
 * Translates keyword using association i18n namespace.
 *
 * @param keyword - Association keyword (e.g., "W_CORP", "BLADE_LINEAGE")
 * @param suffix - Random alphanumeric suffix (e.g., "AB123")
 * @returns Formatted username (e.g., "Faust-WCorp-AB123" in EN, "파우스트-W사-AB123" in KR)
 *
 * @example
 * formatAuthorName("W_CORP", "AB123")
 * // => "Faust-WCorp-AB123" (EN)
 * // => "파우스트-W사-AB123" (KR)
 *
 * @example
 * // If keyword translation is missing, uses raw keyword
 * formatAuthorName("UNKNOWN_FACTION", "XYZ99")
 * // => "Faust-UNKNOWN_FACTION-XYZ99"
 */
export function formatAuthorName(keyword: string, suffix: string): string {
  // Defensive check for both fields empty
  if (!keyword && !suffix) {
    console.warn('formatAuthorName: both keyword and suffix missing')
    return 'Unknown Author'
  }

  const sinnerName = i18next.t('sinner', { ns: 'association' })

  // Use placeholders for partial missing data
  const translatedKeyword = keyword
    ? i18next.t(keyword, { ns: 'association', defaultValue: keyword })
    : '???'
  const displaySuffix = suffix || '????'

  return `${sinnerName}-${translatedKeyword}-${displaySuffix}`
}
```

**Type Definition Changes:**

**Step 1: Update canonical definition (PlannerListTypes.ts)**
```typescript
// BEFORE
export interface PublicPlanner {
  authorName: string  // ← REMOVE THIS
  category: MDCategory | RRCategory  // ← Already supports both MD and RR
  // ...
}

// AFTER
export interface PublicPlanner {
  /** Author's association keyword (e.g., "W_CORP", "BLADE_LINEAGE") */
  authorUsernameKeyword: string
  /** Author's random alphanumeric suffix (5 characters) */
  authorUsernameSuffix: string
  category: MDCategory | RRCategory  // ← Unchanged (future-proof)
  // ...
}
```

**Step 2: Remove duplicate and re-export (MDPlannerListTypes.ts)**
```typescript
// BEFORE - Duplicate definition (lines 81-108)
export interface PublicPlanner {
  authorName: string
  category: MDCategory  // ← MD-only, not future-proof
  // ...
}

// AFTER - Re-export from canonical source
export type { PublicPlanner } from './PlannerListTypes'
// Note: This maintains import compatibility for files using MDPlannerListTypes
```

**Schema Validation Changes:**
```typescript
// BEFORE (PlannerListSchemas.ts)
export const PublicPlannerSchema = z.object({
  authorName: z.string(),  // ← REMOVE THIS
  // ...
})

// AFTER
export const PublicPlannerSchema = z.object({
  /** Author's association keyword */
  authorUsernameKeyword: z.string(),
  /** Author's random alphanumeric suffix */
  authorUsernameSuffix: z.string(),
  // ...
})
```

**Component Usage:**
```typescript
// BEFORE (PlannerCard.tsx)
const { authorName, ... } = planner
<span>{authorName}</span>

// AFTER
import { formatAuthorName } from '@/lib/formatDate'

const { authorUsernameKeyword, authorUsernameSuffix, ... } = planner
const authorName = formatAuthorName(authorUsernameKeyword, authorUsernameSuffix)
<span>{authorName}</span>
```

### Import Requirements

**Add to formatDate.ts:**
```typescript
import i18next from 'i18next'
```

**Add to PlannerCard.tsx:**
```typescript
import { formatAuthorName } from '@/lib/formatDate'
```

### Search Pattern for All Occurrences

Before implementing, search codebase for all references:
```bash
# Find all files using PublicPlanner type
grep -r "PublicPlanner" frontend/src --include="*.ts" --include="*.tsx"

# Find all files destructuring authorName
grep -r "authorName" frontend/src --include="*.ts" --include="*.tsx"

# Find duplicate type definitions
grep -r "interface PublicPlanner" frontend/src --include="*.ts"
```

Update ALL occurrences found.

## Verification Checklist

**Before submitting for review:**
- [ ] All TypeScript compile errors resolved (`yarn tsc --noEmit`)
- [ ] All existing tests pass (`yarn test`)
- [ ] New formatter function has unit tests
- [ ] Manual testing completed (all steps above)
- [ ] No console errors in browser
- [ ] Author names display correctly in EN and KR
- [ ] Language switching works without page reload
- [ ] Code follows existing patterns in `formatDate.ts`
- [ ] JSDoc comments added to new function
- [ ] Git diff shows only intended changes (no accidental modifications)

**Optional (if time permits):**
- [ ] Add Storybook story for PlannerCard with different username combinations
- [ ] Add test coverage for edge cases (empty strings, missing translations)
- [ ] Check bundle size impact (should be negligible - only adding i18next import)

## Decisions Made (Clarified During Research)

1. **Type Consolidation Strategy: RESOLVED**
   - Keep `PlannerListTypes.ts` as canonical source (supports MD + RR categories)
   - Remove duplicate `PublicPlanner` from `MDPlannerListTypes.ts`
   - Add re-export: `export type { PublicPlanner } from './PlannerListTypes'`
   - Rationale: Prepares for future RR planner type, eliminates duplication risk

2. **Formatter Location: Use existing formatDate.ts**
   - Add to `formatDate.ts` (keeps all formatters together)
   - Co-location principle: Similar utilities in same file
   - No need for separate `formatUsername.ts` file

3. **Fallback Logic: RESOLVED**
   - Both fields empty: `"Unknown Author"`
   - Keyword missing: `"Faust-???-{suffix}"`
   - Suffix missing: `"Faust-{keyword}-????"`
   - Translation missing: Use raw keyword via `defaultValue` parameter

4. **Test Mocks: Update to match new schema**
   - Change mock data in `useMDGesellschaftData.test.tsx`
   - Use two-field structure instead of single `authorName`

## Optional Future Enhancements

1. **Sentry Monitoring:**
   - Add breadcrumb when translation fallback occurs (detects unknown faction keywords)
   - Implementation: `Sentry.addBreadcrumb()` in formatter when `defaultValue` used
   - Trade-off: Observability vs. noise (may be intentional for new factions)

2. **Performance Profiling:**
   - React Compiler handles memoization automatically
   - Manual `useMemo` not needed unless profiling shows issues
   - Monitor with React DevTools Profiler if concerned
