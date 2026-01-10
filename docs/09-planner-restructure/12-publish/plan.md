# Publish Functionality - Execution Plan

## Planning Gaps (STOP if any)

**NONE FOUND** - All requirements are clear and research is complete:
- Backend API endpoint confirmed: `PUT /api/planner/md/{id}/publish`
- Response schema includes `published` field but frontend schema needs update
- Save-before-publish flow pattern clear from existing `handleSave()` code
- Button loading state infrastructure already implemented

**ONE ASSUMPTION requiring confirmation:**
- Should button text toggle between "Publish" and "Unpublish" based on current `published` state?
  - Research suggests YES (spec mentions "View button text that changes based on publishing state")
  - Frontend schema (`ServerPlannerResponseSchema`) doesn't currently include `published` field
  - **Decision:** Add `published` field to schema and implement toggle text (spec-aligned)

---

## Execution Overview

**Task Type:** Straightforward API integration connecting existing UI skeleton to working backend endpoint.

**Implementation Pattern:**
- API Client Extension: Add one method following `update()` pattern
- Handler Implementation: Replace TODO with save-then-publish flow
- Schema Enhancement: Add missing `published` field to response schema
- State Tracking: Track published state for conditional button text

**Risk Level:** LOW - Purely additive, no changes to existing save flow, backend complete.

**Complexity:** LOW-MEDIUM - Sequential async operations with error handling.

**Scope:** ~50-80 lines across 3 files.

---

## Dependency Analysis (Senior Thinking)

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| `frontend/src/schemas/PlannerSchemas.ts` | Medium | Zod library, constants.ts | plannerApi.ts, all planner data hooks |
| `frontend/src/lib/plannerApi.ts` | Medium | ApiClient, PlannerSchemas | usePlannerStorageAdapter, usePlannerSync, planner pages |
| `frontend/src/routes/PlannerMDNewPage.tsx` | Low | plannerApi, usePlannerSave, toast, i18n | None (standalone route) |

### Ripple Effect Map

**Schema Change (PlannerSchemas.ts):**
- If `ServerPlannerResponseSchema` adds `published` field → All consumers get type-safe access
- Existing code unaffected (additive change)
- No migration needed (backend already returns this field)

**API Method (plannerApi.ts):**
- If `togglePublish()` added → Available to all consumers, only used by PlannerMDNewPage
- No impact on existing methods
- Pattern identical to `update()` - minimal risk

**Handler Implementation (PlannerMDNewPage.tsx):**
- If `handlePublish()` implemented → Button becomes functional
- No impact on save flow (separate concerns)
- No impact on auto-save (different trigger)
- Button state already wired

### High-Risk Modifications

**NONE** - All changes additive and isolated:
- Schema: Optional field addition (backward compatible)
- API: New method following existing pattern
- Handler: Replacing TODO with implementation (no existing logic to break)

---

## Execution Order

### Phase 1: Data Layer (Schema + Types)

**Step 1: Add `published` field to ServerPlannerResponseSchema**
- File: `frontend/src/schemas/PlannerSchemas.ts`
- Depends on: none
- Enables: F1 (published state tracking)
- Action: Add `published: z.boolean()` after line 592
- Pattern: Copy existing field patterns in schema

### Phase 2: Interface Layer (API Client)

**Step 2: Add `togglePublish()` method to plannerApi**
- File: `frontend/src/lib/plannerApi.ts`
- Depends on: Step 1 (updated schema)
- Enables: F2 (publish API call)
- Action: Insert method after `import()` (line 102)
- Pattern: Copy `update()` structure, remove request body parameter

### Phase 3: Integration (UI Handler)

**Step 3: Implement `handlePublish()` save-then-publish flow**
- File: `frontend/src/routes/PlannerMDNewPage.tsx`
- Depends on: Step 2 (API method available)
- Enables: F3 (save-before-publish), F4 (error handling), F5 (toast feedback)
- Action: Replace TODO block (lines 626-640) with implementation
- Pattern: Sequential await with early return

**Step 4: Add published state tracking for toggle text**
- File: `frontend/src/routes/PlannerMDNewPage.tsx`
- Depends on: Step 3 (handler implemented)
- Enables: F6 (conditional button text)
- Action: Extract `published` from response, use in conditional rendering
- Pattern: useState + response field extraction

### Phase 4: Verification

**Step 5: Manual UI Testing**
- Depends on: Steps 1-4 (complete implementation)
- Enables: Validation of all features
- Action: Execute 17 manual verification steps from status.md

---

## Test Steps (MANDATORY)

**No automated tests required** - Feature uses existing patterns with proven test coverage:
- `plannerApi.ts` methods follow tested pattern (update, create, etc.)
- `usePlannerSave.ts` save flow already tested
- Button state management uses existing React patterns

**Manual verification only** - See Verification Checkpoints section.

---

## Verification Checkpoints

### After Step 3: Verify F1-F5 (Core Publish Flow)

1. Create new planner
2. Click Publish button
3. Verify save occurs first (loading indicator)
4. Verify publish call succeeds (network tab: PUT to /publish)
5. Verify success toast appears
6. Navigate to `/planner/md/gesellschaft`
7. Verify planner appears in published list

### After Step 4: Verify F6 (Toggle Text)

1. Return to planner editor
2. Verify button text reads "Unpublish" (if published state tracked)
3. Click Unpublish button
4. Verify success toast
5. Verify button text reads "Publish"
6. Navigate to Gesellschaft page
7. Verify planner removed from list

### Error Scenarios

1. Simulate 403 error → Error toast shown
2. Simulate network failure (offline mode) → Error toast shown
3. Verify save failure aborts publish → Publish not attempted

---

## Risk Mitigation (from instructions.md Risk Assessment)

| Risk | Step Affected | Mitigation |
|------|---------------|------------|
| Publish before first save | Step 3 | Handler calls `save()` first with early return on failure |
| Auto-save in progress during publish | Step 3 | Separate operations - manual save completes immediately |
| Network failure during publish | Step 3 | Try-catch with error toast, no partial state (atomic) |
| Rapid publish clicks | Step 3 | `isPublishing` flag disables button during operation |
| Save fails before publish | Step 3 | Early return if `save()` returns false |
| Schema mismatch | Step 1 | Backend already returns `published` - adding missing field |

---

## Pre-Implementation Validation Gate

**BEFORE Step 1 execution, verify research completed:**

| Validation Category | Check | Blocker if Missing | Status |
|---------------------|-------|-------------------|---------|
| Reference Completeness | Read plannerApi.update(), handleSave(), ServerPlannerResponseSchema? | YES | ✅ |
| Contract Alignment | Backend PlannerResponse includes `published`? | YES | ✅ |
| Dependency Resolution | Zod, ApiClient, toast, i18n available? | YES | ✅ |
| Structure Documentation | PUT pattern, save-then-X pattern, schema extension documented? | YES | ✅ |
| Difference Justification | No request body (idempotent toggle) vs update (has body) | NO (documented) | ✅ |

**Execution Rule:** All blockers resolved ✅ - PROCEED with implementation.

---

## Dependency Verification Steps

### After Step 1 (Schema Update)

- No compilation errors in plannerApi.ts (schema import valid)
- No runtime errors in existing save flow (backward compatible)

### After Step 2 (API Method)

- TypeScript types resolve correctly
- No import errors in PlannerMDNewPage.tsx

### After Step 3 (Handler Implementation)

- Button click triggers handler (no console errors)
- Toast library functional (success/error messages display)
- i18n keys resolve (no missing translation warnings)

---

## Rollback Strategy

### Step 1 Failure (Schema)
- Revert PlannerSchemas.ts to previous version
- Safe stopping point: No consumers affected

### Step 2 Failure (API Method)
- Revert plannerApi.ts to previous version
- Safe stopping point: Handler not yet implemented

### Step 3 Failure (Handler)
- Revert handlePublish() to TODO block
- Button remains non-functional but app stable
- Safe stopping point: Can deploy without publish feature

### Complete Rollback
- Remove all changes (3 files)
- Feature remains in "Coming Soon" state
- Button shows but doesn't work beyond save

---

## Critical Files for Implementation

1. `/home/user/github/LimbusPlanner/frontend/src/schemas/PlannerSchemas.ts`
   - Add `published: z.boolean()` field to `ServerPlannerResponseSchema` (line ~592)

2. `/home/user/github/LimbusPlanner/frontend/src/lib/plannerApi.ts`
   - Add `togglePublish()` method after line 102
   - Pattern: `PUT ${PLANNERS_BASE}/${id}/publish` with schema validation

3. `/home/user/github/LimbusPlanner/frontend/src/routes/PlannerMDNewPage.tsx`
   - Implement `handlePublish()` at lines 626-640
   - Add published state tracking after line 625

4. `/home/user/github/LimbusPlanner/static/i18n/EN/planner.json`
   - Reference only: i18n keys already exist - no changes needed

5. `/home/user/github/LimbusPlanner/backend/src/main/java/org/danteplanner/backend/dto/planner/PlannerResponse.java`
   - Reference only: Confirms `published` field exists in backend response
