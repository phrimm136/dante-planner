# Status: Extraction Probability Calculator

## Execution Progress

Last Updated: 2026-01-03
Current Step: 15/16
Current Phase: Phase 4 Complete

### Milestones
- [x] M1: Data Layer Complete (Steps 1-4)
- [x] M2: Calculator Logic + Tests Pass (Steps 5-6)
- [x] M3: Components Complete (Steps 7-10)
- [x] M4: Integration Complete (Steps 11-15)
- [ ] M5: Manual Verification Passed
- [ ] M6: Code Review Passed

### Step Log
| Step | Status | File |
|------|--------|------|
| 1 | ✅ | constants.ts - EXTRACTION_RATES |
| 2 | ✅ | ExtractionTypes.ts |
| 3 | ✅ | ExtractionSchemas.ts |
| 4 | ✅ | schemas/index.ts |
| 5 | ✅ | extractionCalculator.ts |
| 6 | ✅ | extractionCalculator.test.ts |
| 7 | ✅ | ExtractionInputs.tsx |
| 8 | ✅ | ExtractionResults.tsx |
| 9 | ✅ | ExtractionCalculator.tsx |
| 10 | ✅ | ExtractionPlannerPage.tsx |
| 11 | ✅ | EN/extraction.json |
| 12 | ✅ | JP/extraction.json |
| 13 | ✅ | KR/extraction.json |
| 14 | ✅ | i18n.ts |
| 15 | ✅ | router.tsx |
| 16 | ⏳ | Integration tests (optional) |

---

## Feature Status

### Core Features
- [ ] F1: Rate constants available
- [ ] F2: Type-safe inputs/outputs
- [ ] F3: Input validation works
- [ ] F4: Single target probability
- [ ] F5: Multi-target probability
- [ ] F6: Pity-adjusted probability
- [ ] F7: Expected pulls calculation
- [ ] F8: Lunacy cost calculation
- [ ] F9: Input controls respond
- [ ] F10: Results display correctly
- [ ] F11: Calculator integrated
- [ ] F12: Page accessible at route
- [ ] F13: i18n (EN/JP/KR)
- [ ] F14: i18n loads correctly
- [ ] F15: Navigation works

### Edge Cases
- [ ] E1: Zero pulls → 0%
- [ ] E2: Zero targets → 100%
- [ ] E3: More targets than pity → <100%
- [ ] E4: All EGO modifier adjusts rates
- [ ] E5: Announcer presence implicit
- [ ] E6: Large pulls (1000+) no overflow

---

## Testing Checklist

### Automated Tests
- [ ] UT1: calculateSingleTargetProbability accuracy
- [ ] UT2: calculateRateForTarget splitting
- [ ] UT3: Pity guarantee at 200 pulls
- [ ] UT4: Edge case: 0 pulls
- [ ] UT5: Edge case: 0 targets
- [ ] UT6: Lunacy cost calculation

### Manual Verification
- [ ] MV1: Navigate to /planner/extraction
- [ ] MV2: Enter 100 pulls, verify ~76%
- [ ] MV3: Enable "All EGO collected"
- [ ] MV4: Check Lunacy cost
- [ ] MV5: Responsive layout
- [ ] MV6: Theme support
- [ ] MV7: i18n switching

---

## Summary

Steps: 0/16 complete
Features: 0/15 verified
Edge Cases: 0/6 verified
Overall: 0%
