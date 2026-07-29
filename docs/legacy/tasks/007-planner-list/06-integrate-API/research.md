# Research: FE/BE Username API Alignment

**Task:** Fix PublicPlanner type mismatch between frontend and backend API contract

---

## Clarifications Resolved

### 1. Type Definition Strategy
**Decision:** Update `PlannerListTypes.ts` (canonical), remove duplicate from `MDPlannerListTypes.ts`

**Rationale:**
- `PlannerListTypes.PublicPlanner` already supports both MD and RR categories (`MDCategory | RRCategory`)
- `MDPlannerListTypes.PublicPlanner` is a duplicate with only `MDCategory` support
- `PlannerCard.tsx` imports from `PlannerListTypes.ts` (not MDPlannerListTypes)
- Personal planner list uses separate `PlannerSummary` type (no conflict)
- Future RR planner type introduction requires broad category support

**Implementation:**
- Update `PublicPlanner` in `PlannerListTypes.ts` (authorName → two fields)
- Remove `PublicPlanner` from `MDPlannerListTypes.ts` (eliminate duplication)
- Add re-export: `export type { PublicPlanner } from './PlannerListTypes'` in MDPlannerListTypes
- Update imports in 5 files using MDPlannerListTypes to ensure type compatibility

### 2. Test Mock Strategy
**Decision:** Update mocks to match new schema

**Files affected:** `useMDGesellschaftData.test.tsx` mock data structure

### 3. Formatter Fallback Logic
**Decision:** Return 'Unknown Author' only if BOTH fields empty, use placeholder for single missing field

**Behavior:**
- Both empty: `"Unknown Author"`
- Keyword missing: `"Faust-???-AB123"`
- Suffix missing: `"Faust-WCorp-????"`
- Translation missing: `"Faust-{RAW_KEYWORD}-AB123"` (i18n fallback)

---

## Spec Ambiguities

**NONE** - All ambiguities resolved via user clarification.

---

## Spec-to-Code Mapping

| Requirement | Target File | Modification |
|-------------|-------------|--------------|
| Canonical type with two fields | `types/PlannerListTypes.ts` | Replace `authorName: string` with `authorUsernameKeyword: string, authorUsernameSuffix: string` |
| Remove duplicate type | `types/MDPlannerListTypes.ts` | Delete `PublicPlanner` interface, add re-export from PlannerListTypes |
| Schema validation | `schemas/PlannerListSchemas.ts` | Update `PublicPlannerSchema` to expect two fields |
| Username formatter | `lib/formatDate.ts` | Add `formatAuthorName(keyword, suffix)` function with defensive checks |
| Component usage | `components/plannerList/PlannerCard.tsx` | Update destructuring, call formatter, import from formatDate |
| Test mocks | `hooks/useMDGesellschaftData.test.tsx` | Replace `authorName: 'TestUser'` with two-field structure |

---

## Spec-to-Pattern Mapping

| Requirement | Pattern Source | Notes |
|-------------|----------------|-------|
| Formatter utility | `lib/formatDate.ts` existing pattern | Add new export to existing formatter file (co-locate related formatters) |
| i18n translation | `association.json` namespace | Use `i18next.t(keyword, { ns: 'association', defaultValue: keyword })` |
| Defensive null checks | Common validation pattern | Return "Unknown Author" if both fields empty, placeholders if one missing |
| Type re-export | TypeScript re-export pattern | `export type { PublicPlanner } from './PlannerListTypes'` consolidates imports |

---

## Pattern Enforcement

**No pattern copying required** - this is a type alignment task, not component creation.

Files are **modified**, not created from patterns:
- Type definitions: simple field replacement
- Schema: Zod object field update
- Formatter: new pure function (no pattern reference needed)
- Component: destructuring change + formatter call

---

## Existing Utilities

**Formatters checked:**
- `lib/formatDate.ts`: formatPlannerDate, formatFullDate, formatRelativeTime
- `lib/utils.ts`: cn (classname utility only)
- **No username formatters exist** - must create new

**i18n checked:**
- `association.json` exists in all languages (EN, KR, JP, CN)
- Contains `sinner` key and faction mappings (W_CORP, BLADE_LINEAGE, etc.)
- i18next already imported and configured in project

**Constants checked:**
- `lib/constants.ts` has no username-related constants
- No need to add constants (username format is fixed: "Faust-{keyword}-{suffix}")

---

## Gap Analysis

**Currently Missing:**
- `formatAuthorName()` function
- i18next import in formatDate.ts
- Two-field structure in PublicPlanner type
- Placeholder logic for partial missing data

**Needs Modification:**
- `PlannerListTypes.ts` line 93: Replace `authorName` with two fields
- `MDPlannerListTypes.ts` lines 81-108: Remove PublicPlanner interface, add re-export
- `PlannerListSchemas.ts` line 52: Update Zod schema
- `PlannerCard.tsx` line 49: Update destructuring and usage
- `useMDGesellschaftData.test.tsx` line 34: Update mock structure

**Can Reuse:**
- i18next translation system (already configured)
- association.json data (already populated)
- formatDate.ts file structure (co-locate formatters)
- Existing Zod validation patterns

---

## Testing Requirements

### Manual UI Tests
- Navigate to `/planner/md/gesellschaft` (community view)
- Verify author names display as `"Faust-{keyword}-{suffix}"`
- Change language EN → KR → verify translation to `"파우스트-{translated}-{suffix}"`
- Change to JP → verify Japanese translations
- Hard refresh → verify names persist correctly
- Check console → no validation errors or warnings
- Switch to "Best" mode → verify author names in recommended list
- Verify card layout handles long faction names (CSS truncation)

### Automated Tests

**Unit Tests:**
- `formatAuthorName("W_CORP", "AB123")` → `"Faust-WCorp-AB123"` (EN)
- `formatAuthorName("W_CORP", "AB123")` → `"파우스트-W사-AB123"` (KR after i18next.changeLanguage)
- `formatAuthorName("UNKNOWN", "XYZ99")` → `"Faust-UNKNOWN-XYZ99"` (fallback)
- `formatAuthorName("", "AB123")` → `"Faust-???-AB123"` (keyword placeholder)
- `formatAuthorName("W_CORP", "")` → `"Faust-WCorp-????"` (suffix placeholder)
- `formatAuthorName("", "")` → `"Unknown Author"` (both empty)

**Integration Tests:**
- `PaginatedPlannersSchema.safeParse(mockResponse)` passes validation
- `PlannerCard` renders author name without undefined
- Language change triggers re-render with new translation
- `yarn tsc --noEmit` passes with no type errors

---

## Technical Constraints

| Constraint | Impact | Mitigation |
|-----------|--------|-----------|
| Two type files with duplicate PublicPlanner | High duplication risk | Remove from MDPlannerListTypes, re-export from canonical source |
| Category type difference (MD-only vs MD+RR) | Breaking change if RR added later | Use PlannerListTypes with broad category support (MD \| RR) |
| i18next synchronous lookup | Low - i18n loads before render | Defensive fallback with `defaultValue` parameter |
| Formatter called on every card render | Negligible - React Compiler memoizes | No manual optimization needed |

---

## Implementation Order

**Phase 1: Type Alignment**
1. Update `PlannerListTypes.ts` PublicPlanner (authorName → two fields)
2. Remove PublicPlanner from `MDPlannerListTypes.ts`, add re-export
3. Run `yarn tsc --noEmit` to find all type errors

**Phase 2: Formatter Creation**
4. Add i18next import to `formatDate.ts`
5. Add `formatAuthorName()` with defensive checks and placeholder logic

**Phase 3: Validation & Component**
6. Update `PlannerListSchemas.ts` schema
7. Update `PlannerCard.tsx` to use formatter
8. Update test mocks in `useMDGesellschaftData.test.tsx`

**Phase 4: Verification**
9. Run `yarn tsc --noEmit` (should pass)
10. Test manually in browser (language switching, data display)
11. Check console for errors

---

## Files to Modify (6 total)

| File | Lines | Change Type | Risk |
|------|-------|-------------|------|
| `types/PlannerListTypes.ts` | 93 | Replace single field with two | Low (compile-time) |
| `types/MDPlannerListTypes.ts` | 81-108 | Remove duplicate, add re-export | Low (consolidation) |
| `schemas/PlannerListSchemas.ts` | 52 | Update Zod schema fields | Medium (runtime validation) |
| `lib/formatDate.ts` | 1, 106+ | Add import, add formatter function | Low (new code) |
| `components/plannerList/PlannerCard.tsx` | 49, 134 | Update destructuring and JSX | Low (isolated component) |
| `hooks/useMDGesellschaftData.test.tsx` | 34 | Update mock data structure | Low (test-only) |

---

## Import Changes Required

**Add imports:**
- `formatDate.ts`: `import i18next from 'i18next'`
- `PlannerCard.tsx`: `import { formatAuthorName } from '@/lib/formatDate'`

**Update imports (after re-export):**
- Files using `MDPlannerListTypes.PublicPlanner` will transparently use re-exported type
- No import statement changes needed (re-export maintains compatibility)

---

## Formatter Function Specification

**Signature:**
```
formatAuthorName(keyword: string, suffix: string): string
```

**Logic:**
1. If both empty → return `"Unknown Author"`
2. If keyword empty → use placeholder `"???"`
3. If suffix empty → use placeholder `"????"`
4. Translate sinner name: `i18next.t('sinner', { ns: 'association' })`
5. Translate keyword: `i18next.t(keyword, { ns: 'association', defaultValue: keyword })`
6. Return formatted: `${sinner}-${translatedKeyword}-${suffix}`

**Edge cases:**
- Missing i18n key → uses raw keyword (defaultValue fallback)
- Null/undefined inputs → defensive check treats as empty string
- Very long keywords → CSS truncation in component handles (no formatter concern)

---

## Schema Update Specification

**Before:**
```
authorName: z.string()
```

**After:**
```
authorUsernameKeyword: z.string()
authorUsernameSuffix: z.string()
```

**Validation impact:**
- Current: Schema expects field that doesn't exist → validation fails
- After: Schema expects both fields, API sends both → validation passes

---

## Component Update Specification

**Before:**
```
const { authorName, ... } = planner
<span>{authorName}</span>
```

**After:**
```
const { authorUsernameKeyword, authorUsernameSuffix, ... } = planner
const authorName = formatAuthorName(authorUsernameKeyword, authorUsernameSuffix)
<span>{authorName}</span>
```

**Behavior:**
- Composition happens at render time (React Compiler memoizes)
- Language change triggers re-render → re-translates automatically
- Defensive formatter handles edge cases gracefully

---

## Success Criteria

- [ ] `yarn tsc --noEmit` passes
- [ ] `PaginatedPlannersSchema` validates API response successfully
- [ ] Community planner list loads without errors
- [ ] Author names display in correct format for all languages
- [ ] Language switching updates author names
- [ ] No console errors or warnings
- [ ] Empty/missing field cases handled gracefully
- [ ] Test mocks match new schema structure
