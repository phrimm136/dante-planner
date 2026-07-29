# Execution Plan: Planner Schema Reconstruction

## Execution Overview

Restructure planner schema to support discriminated union pattern for multiple planner types. Key change: move `category` from `content` into new `config` layer with type discrimination.

**Strategy:** Foundation-first (types define contract) → Database → Backend DTOs → Frontend Hooks → Tests

---

## Execution Order

### Phase 1: Frontend Types & Schemas (Foundation)

1. **`frontend/src/lib/constants.ts`**: Add RR_CATEGORIES constant
   - Depends on: none
   - Enables: F2, F3
   - Add: `RR_CATEGORIES = ['RR_PLACEHOLDER'] as const` and `RRCategory` type

2. **`frontend/src/types/PlannerTypes.ts`**: Add config types and restructure SaveablePlanner
   - Depends on: Step 1
   - Enables: F1, F4, F5
   - Add: `MDConfig`, `RRConfig`, `PlannerConfig` discriminated union
   - Rename: `PlannerContent` → `MDPlannerContent`
   - Add: `RRPlannerContent` placeholder, `PlannerContent` union
   - Update: `SaveablePlanner` to include `config: PlannerConfig`
   - Remove: `category` from content (now in config)

3. **`frontend/src/types/PlannerListTypes.ts`**: Add plannerType to PlannerSummary
   - Depends on: Step 2
   - Enables: F6
   - Add: `plannerType: PlannerType` and `category: MDCategory | RRCategory`

4. **`frontend/src/schemas/PlannerSchemas.ts`**: Add config schemas with discriminated union
   - Depends on: Steps 1, 2
   - Enables: F7, F8
   - Add: `RRCategorySchema`, `MDConfigSchema`, `RRConfigSchema`
   - Add: `PlannerConfigSchema` using `z.discriminatedUnion('type', [...])`
   - Add: `validateSaveablePlanner()` two-step validation function
   - Update: `PlannerSummarySchema` to include plannerType + category

### Phase 2: Database Schema

5. **`backend/.../db/migration/V012__planner_category_to_string.sql`** (NEW)
   - Depends on: none (parallel with Phase 1)
   - Enables: F9
   - SQL: `ALTER TABLE planners MODIFY COLUMN category VARCHAR(50) NOT NULL`

6. **`backend/.../entity/RRCategory.java`** (NEW): Create RR category enum
   - Depends on: Step 5
   - Enables: F10
   - Add: Placeholder enum with `RR_PLACEHOLDER` value and `isValid()` helper

7. **`backend/.../entity/Planner.java`**: Change category from enum to String
   - Depends on: Steps 5, 6
   - Enables: F11
   - Replace: `@Enumerated(EnumType.STRING) private MDCategory category` → `@Column(length = 50) private String category`

### Phase 3: Backend DTOs

8. **`backend/.../dto/planner/PlannerSummaryResponse.java`**: Add plannerType field
   - Depends on: Step 7
   - Enables: F12
   - Add: `private PlannerType plannerType`, change `category` to String

9. **`backend/.../dto/planner/CreatePlannerRequest.java`**: Update category to String
   - Depends on: Step 7
   - Enables: F13
   - Change: `category` from `MDCategory` to `String`

10. **`backend/.../dto/planner/UpdatePlannerRequest.java`**: Add category field
    - Depends on: Step 7
    - Enables: F14
    - Add: `private String category` (optional)

11. **`backend/.../service/PlannerService.java`**: Add category validation by type
    - Depends on: Steps 6-10
    - Enables: F15
    - Add: `isValidCategory(PlannerType type, String category)` method
    - Validate: category in `createPlanner()` and `updatePlanner()`

### Phase 4: Frontend Hooks (Storage Layer)

12. **`frontend/src/hooks/usePlannerStorage.ts`**: Update for config structure
    - Depends on: Step 4
    - Enables: F16
    - Update: `listPlanners()` to handle config.category
    - Use: two-step validation in `loadPlanner()`

13. **`frontend/src/hooks/usePlannerSave.ts`**: Update serialization
    - Depends on: Step 4
    - Enables: F17
    - Update: `createSaveablePlanner()` to build config object
    - Remove: category from content construction

14. **`frontend/src/hooks/usePlannerStorageAdapter.ts`**: Update type handling
    - Depends on: Steps 12, 13
    - Enables: F18
    - Ensure: adapter handles new structure for guest + auth modes

### Phase 5: Tests

15. **`frontend/src/schemas/PlannerSchemas.test.ts`** (NEW)
    - Depends on: Step 4
    - Enables: F19
    - Test: Config discrimination, cross-type rejection, two-step validation

16. **`frontend/src/hooks/usePlannerStorage.test.ts`** (UPDATE)
    - Depends on: Step 12
    - Enables: F20
    - Update: fixtures to use new config structure

17. **`frontend/src/hooks/usePlannerSave.test.ts`** (UPDATE)
    - Depends on: Step 13
    - Enables: F21
    - Verify: serialization produces correct config structure

18. **`backend/.../controller/PlannerControllerTest.java`** (UPDATE)
    - Depends on: Steps 8, 11
    - Enables: F22
    - Test: response includes plannerType + category, invalid category returns 400

19. **`backend/.../service/PlannerServiceTest.java`** (UPDATE)
    - Depends on: Step 11
    - Enables: F23
    - Test: `isValidCategory()` for MD/RR categories

### Phase 6: Cleanup

20. **`docs/architecture-map.md`**: Update with new patterns
    - Depends on: All above
    - Document: discriminated union pattern, two-step validation

---

## Verification Checkpoints

| After Step | Verify | Method |
|------------|--------|--------|
| 4 | Config schema validates | `yarn tsc` |
| 7 | Entity compiles | `./mvnw compile` |
| 11 | Category validation works | Run PlannerServiceTest |
| 13 | Serialization produces config | Browser DevTools |
| 15-17 | Frontend tests pass | `yarn test` |
| 18-19 | Backend tests pass | `./mvnw test` |
| 20 | Full E2E | Create planner, verify IndexedDB |

---

## Rollback Strategy

**Safe stopping points:**
- After Step 4: Frontend changes only, backend unchanged
- After Step 11: All code changes done, tests pending

**If step fails:**
- Steps 1-4: Revert TypeScript files only
- Step 5: Flyway clean if migration applied
- Steps 6-11: Revert Java files, may need DB rollback migration
- Steps 12-14: Revert hook files
- Steps 15-19: Fix failing tests
