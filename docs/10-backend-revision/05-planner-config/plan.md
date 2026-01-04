# Plan: Planner Config Loading Robustness Pass

## Planning Gaps

**None identified.** Research is complete:
- All file locations verified
- Pattern sources identified (PlannerContentValidator.java)
- Config values confirmed in application.properties (lines 46-51)
- PlannerType enum exists with MIRROR_DUNGEON and REFRACTED_RAILWAY

---

## Execution Overview

Create a backend validator for content version enforcement, integrate it into PlannerService, update frontend schema constraint, and replace hardcoded values in the planner page.

**Strategy:** Backend first (safety net), then frontend (source of truth)

---

## Dependency Analysis (Senior Thinking)

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| `PlannerService.java` | HIGH | ContentVersionValidator (new) | All planner endpoints |
| `PlannerSchemas.ts` | MEDIUM | - | usePlannerConfig hook |
| `PlannerMDNewPage.tsx` | LOW | usePlannerConfig hook | Page-isolated |

### Files Being Created

| File | Pattern Source | Used By |
|------|----------------|---------|
| `ContentVersionValidator.java` | PlannerContentValidator.java | PlannerService.java |
| `ContentVersionValidatorTest.java` | Test patterns | Test suite only |

### Ripple Effect Map

- If `PlannerService.java` validation order changes → Existing content validation still runs after version check
- If `PlannerSchemas.ts` adds `.min(1)` → Frontend rejects empty rrAvailableVersions (fail-fast)
- If `PlannerMDNewPage.tsx` uses config → All 4 components receive dynamic value

### High-Risk Modifications

| File | Risk | Mitigation |
|------|------|------------|
| `PlannerService.java` | HIGH - All CRUD flows through it | Change is 2 lines only. Test existing flows still work. |

---

## Execution Order

### Phase 1: Backend Validator (Data Layer)

1. **Create `ContentVersionValidator.java`**: New validator component
   - Depends on: none
   - Enables: F1 (backend rejects invalid versions)
   - Pattern: Copy from PlannerContentValidator.java

### Phase 2: Backend Integration (Logic Layer)

2. **Modify `PlannerService.java`**: Inject validator and add validation call
   - Depends on: Step 1
   - Enables: F1
   - Change: Add to constructor, call before content validation

### Phase 3: Backend Tests

3. **Create `ContentVersionValidatorTest.java`**: Unit tests for validator
   - Depends on: Step 1
   - Enables: Verification of F1

4. **Run backend tests**: Verify validation works
   - Depends on: Steps 1-3
   - Command: `./mvnw test`

### Phase 4: Frontend Schema (Data Layer)

5. **Modify `PlannerSchemas.ts:525`**: Add `.min(1)` constraint
   - Depends on: none
   - Enables: F2 (frontend rejects empty rrAvailableVersions)

6. **Modify `usePlannerConfig.test.ts`**: Update empty array test expectation
   - Depends on: Step 5
   - Enables: Verification of F2

### Phase 5: Frontend UI (Interface Layer)

7. **Modify `PlannerMDNewPage.tsx`**: Replace 4 hardcoded values
   - Depends on: Step 5
   - Enables: F3 (frontend uses config)
   - Lines: 739, 747, 759, 768

### Phase 6: Verification

8. **Run frontend tests**: Verify schema and hook changes
   - Depends on: Steps 5-7
   - Command: `yarn test`

9. **Run full test suite**: Verify no regressions
   - Depends on: Steps 1-8

10. **Manual verification**: Test UI behavior
    - Depends on: Steps 1-9

---

## Verification Checkpoints

| After Step | Feature | Verification Method |
|------------|---------|---------------------|
| Step 4 | F1: Backend rejects invalid versions | Unit tests pass |
| Step 6 | F2: Frontend rejects empty array | Schema test expects failure |
| Step 8 | F3: Frontend uses config values | All frontend tests pass |
| Step 10 | All features | Manual curl + UI verification |

---

## Risk Mitigation

| Risk | Step Affected | Mitigation |
|------|---------------|------------|
| Frontend/backend config sync | Step 7 | Frontend fetches from backend - automatic sync |
| Backend config invalid | Step 5 | Schema `.min(1)` fails fast |
| Existing planners affected | Step 2 | Validation only on create |
| PlannerService high-impact | Step 2 | Change is 2 lines only |

---

## Dependency Verification Steps

| After Step | Verification |
|------------|--------------|
| Step 2 | Run existing PlannerService tests |
| Step 5 | Run usePlannerConfig.test.ts |
| Step 7 | Run dev server, confirm config binding |

---

## Rollback Strategy

- **Safe stopping points**: After Step 4 (backend complete), After Step 6 (schema complete)
- **If Step 2 fails**: Revert PlannerService.java constructor change
- **If Step 5 breaks tests**: Revert `.min(1)` addition
- **If Step 7 breaks UI**: Revert to hardcoded `mdVersion={6}`
