# Execution Plan: Extraction Probability Calculator

## Planning Gaps

**None detected.** Research is complete with all mappings defined.

---

## Execution Overview

Create extraction probability calculator following established patterns:
- Data Layer First: Types, schemas, constants (no dependencies)
- Logic Layer: Pure calculator functions (testable)
- Interface Layer: React components with shadcn/ui
- Integration: Router, i18n
- Tests: Unit tests for calculator math

---

## Execution Order

### Phase 1: Data Layer

1. **`lib/constants.ts`**: Add `EXTRACTION_RATES` constant
   - Depends on: none
   - Enables: F1 (rate calculations)

2. **`types/ExtractionTypes.ts`**: Create type definitions
   - Depends on: Step 1
   - Enables: F2 (type safety)

3. **`schemas/ExtractionSchemas.ts`**: Create Zod schemas
   - Depends on: Step 2
   - Enables: F3 (validation)

4. **`schemas/index.ts`**: Export new schemas
   - Depends on: Step 3
   - Enables: Central imports

### Phase 2: Logic Layer

5. **`lib/extractionCalculator.ts`**: Probability math functions
   - Depends on: Step 1
   - Enables: F4-F8 (all calculations)
   - Functions: calculateRateForTarget, calculateSingleTargetProbability, calculateMultiTargetProbability, calculatePityAdjustedProbability, calculateExpectedPulls, calculateLunacyCost

6. **`lib/extractionCalculator.test.ts`**: Unit tests
   - Depends on: Step 5
   - Enables: Verified calculations
   - Tests: Single target probability, pity guarantee, rate-up splits, edge cases

### Phase 3: Interface Layer

7. **`components/extraction/ExtractionInputs.tsx`**: Input controls
   - Depends on: Steps 2, 3
   - Enables: F9 (user input)

8. **`components/extraction/ExtractionResults.tsx`**: Results display
   - Depends on: Step 2
   - Enables: F10 (probability display)

9. **`components/extraction/ExtractionCalculator.tsx`**: Container component
   - Depends on: Steps 5, 7, 8
   - Enables: F11 (integrated calculator)

10. **`routes/ExtractionPlannerPage.tsx`**: Main page
    - Depends on: Step 9
    - Enables: F12 (accessible page)

### Phase 4: Integration

11. **`static/i18n/EN/extraction.json`**: English translations
    - Depends on: Steps 7, 8, 10
    - Enables: F13 (i18n)

12. **`static/i18n/JP/extraction.json`**: Japanese translations
    - Depends on: Step 11
    - Enables: F13

13. **`static/i18n/KR/extraction.json`**: Korean translations
    - Depends on: Step 11
    - Enables: F13

14. **`lib/i18n.ts`**: Register extraction namespace
    - Depends on: Steps 11-13
    - Enables: F14 (i18n loading)

15. **`lib/router.tsx`**: Add extraction route
    - Depends on: Step 10
    - Enables: F15 (navigation)

### Phase 5: Tests

16. **Component integration test** (optional)
    - Depends on: Steps 9, 10
    - Enables: UI confidence

---

## Verification Checkpoints

| After Step | Verify |
|------------|--------|
| 1 | `EXTRACTION_RATES` importable |
| 5 | Run `yarn vitest extractionCalculator` |
| 6 | All calculator tests pass |
| 10 | `yarn dev` - no TypeScript errors |
| 14 | i18n loads without warnings |
| 15 | Navigate `/planner/extraction` - page renders |

**Manual Verification (after Step 15):**
1. Navigate to `/planner/extraction`
2. Set 100 pulls, 2 featured IDs, want 1 → verify ~76%
3. Set 200 pulls → verify pity shows 100%
4. Toggle "All EGO collected" → verify rates adjust
5. Check Lunacy cost shows 13,000 for 100 pulls

---

## Rollback Strategy

| Failure Point | Action |
|---------------|--------|
| Steps 1-4 | Revert constants.ts, delete type/schema files |
| Steps 5-6 | Delete extractionCalculator.ts |
| Steps 7-10 | Delete extraction/ folder, revert router |
| Steps 11-15 | Revert i18n.ts, router.tsx, delete i18n files |

**Safe Stopping Points:**
- After Step 6: Calculator logic complete + tested
- After Step 10: Components exist, not yet routed
- After Step 15: Feature complete
