# Implementation Results: Extraction Probability Calculator

## What Was Done

- Created extraction probability calculator page at `/planner/extraction`
- Implemented probability math with correct extraction mechanics:
  - EGO: 비복원추출 (without replacement) - every hit is unique
  - Identity/Announcer: 복원추출 (with replacement) - Coupon Collector model
- Added multi-pity support (400 pulls = 2 pity, 600 pulls = 3 pity)
- Fixed "All EGO Collected" rate logic (rate-up EGO gets full 1.3%)
- Added featured-wanted sync (wanted auto-adjusts when featured changes)
- Removed incorrect disabled state from EGO inputs when allEgoCollected checked
- Added comprehensive test coverage (77 tests)

## Files Changed

- `frontend/src/lib/constants.ts` - Added EXTRACTION_RATES constant
- `frontend/src/types/ExtractionTypes.ts` - Type definitions
- `frontend/src/schemas/ExtractionSchemas.ts` - Zod schemas
- `frontend/src/lib/extractionCalculator.ts` - Probability math functions
- `frontend/src/lib/__tests__/extractionCalculator.test.ts` - 77 unit tests
- `frontend/src/components/extraction/ExtractionInputs.tsx` - Input controls
- `frontend/src/components/extraction/ExtractionResults.tsx` - Results display
- `frontend/src/components/extraction/ExtractionCalculator.tsx` - Container
- `frontend/src/routes/ExtractionPlannerPage.tsx` - Main page
- `frontend/src/lib/router.tsx` - Added route
- `frontend/src/lib/i18n.ts` - Registered namespace
- `static/i18n/{EN,JP,KR}/extraction.json` - Translations

## Verification Results

- Checkpoint 1 (EXTRACTION_RATES): PASS
- Checkpoint 5 (calculator tests): PASS - 77 tests
- Checkpoint 10 (TypeScript): PASS - no errors
- Checkpoint 14 (i18n): PASS
- Checkpoint 15 (navigation): PASS - page renders
- Manual Verification: PASS - 4 EGO @ 400 pulls shows 96.7%
- Code Review: ACCEPTABLE (architecture, security, performance, reliability)

## Issues & Resolutions

- "All EGO Collected" misunderstood as "no EGO available" → Fixed: means 픽뚫 pool empty, rate-up gets full 1.3%
- Data model "wanted: 4" interpreted as "4 copies of 1 item" → Fixed: means "4 different items" with 비복원/복원추출 model
- Pity only handled single trigger → Fixed: supports multiple pity (floor(pulls/200))
- EGO inputs disabled when allEgoCollected checked → Fixed: removed incorrect disabled prop
- Unused variable `probMiss` in Coupon Collector → Fixed: removed dead code, clarified comments
