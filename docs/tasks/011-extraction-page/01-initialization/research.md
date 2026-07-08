# Research: Extraction Probability Calculator

## Spec Ambiguities

**None detected.** Spec is well-defined with explicit rate mechanics, pity rules, and edge cases.

---

## Spec-to-Code Mapping

| Requirement | Location | Action |
|-------------|----------|--------|
| Route `/planner/extraction` | `lib/router.tsx` | Add route + routeTree |
| Page component | `routes/ExtractionPlannerPage.tsx` | CREATE |
| Calculator logic | `lib/extractionCalculator.ts` | CREATE |
| Types/Schemas | `types/ExtractionTypes.ts`, `schemas/ExtractionSchemas.ts` | CREATE |
| UI components | `components/extraction/*` | CREATE 3 files |
| Rate constants | `lib/constants.ts` | ADD `EXTRACTION_RATES` |
| i18n | `static/i18n/{EN,JP,KR}/extraction.json` | CREATE 3 files |

---

## Spec-to-Pattern Mapping

| Requirement | Pattern Source |
|-------------|----------------|
| Page structure | `routes/PlannerMDNewPage.tsx` → `PlannerSection` wrapper |
| Route definition | `lib/router.tsx` → `createRoute()` pattern |
| Real-time updates | Planner `useState` + immediate recalc (no submit) |
| Input controls | shadcn/ui `Input`, `Slider`, `Checkbox` |
| Constants | `lib/constants.ts` → add `EXTRACTION_RATES` object |
| Type definitions | `types/PlannerTypes.ts` pattern |
| Zod validation | `schemas/PlannerSchemas.ts` pattern |

---

## Pattern Enforcement (MANDATORY)

| New File | MUST Read First | Patterns to Copy |
|----------|-----------------|------------------|
| `ExtractionPlannerPage.tsx` | `routes/PlannerMDNewPage.tsx` | Page structure, useState, Suspense/ErrorBoundary |
| `ExtractionCalculator.tsx` | `components/deckBuilder/DeckBuilderSummary.tsx` | Container with child sections |
| `ExtractionInputs.tsx` | `components/startBuff/StartBuffEditPane.tsx` | Input controls, onChange handlers |
| `ExtractionResults.tsx` | `components/egoGift/EGOGiftObservationSection.tsx` | Display-only component |
| `extractionCalculator.ts` | `lib/entitySort.ts` | Pure functions, no side effects |
| `ExtractionSchemas.ts` | `schemas/PlannerSchemas.ts` | Zod patterns, `.refine()` for cross-field rules |

---

## Existing Utilities

| Category | Location | Found |
|----------|----------|-------|
| Class names | `lib/utils.ts` | `cn()` available |
| Constants pattern | `lib/constants.ts` | `MAX_LEVEL`, `SINNERS`, `AFFINITIES` |
| i18n setup | `lib/i18n.ts` | EN/JP/KR configured |
| UI components | `components/ui/*` | `Button`, `Input`, `Slider`, `Checkbox` |
| Validation | `lib/validation.ts` | `validateData()` available |

---

## Gap Analysis

**Missing (must create):**
- `calculateSingleTargetProbability(pulls, rate)` function
- `calculateMultipleTargetsProbability(targets, pulls, pity)` function
- `calculateOptimalPityStrategy(targets, pulls)` function
- Zod schemas for input validation
- i18n translations (3 JSON files)
- Route integration in router.tsx

**Can reuse:**
- `PlannerSection` component for layout
- shadcn/ui input components
- Constants pattern from `lib/constants.ts`
- i18n infrastructure

---

## Testing Requirements

### Manual UI Tests
- Navigate `/planner/extraction` → page loads without error
- Enter 100 pulls, 2 featured IDs, want 1 → probabilities render
- Modify featured counts → results recalculate instantly
- Enable "All EGO collected" → EGO inputs adjust
- Set 200 pulls, single target → shows 100% with pity
- Extreme values (0, 1000 pulls) → no NaN/negative values

### Automated Tests
- `calculateSingleTargetProbability(100, 0.0145)` ≈ 0.765
- `calculateSingleTargetProbability(200, 0.0145)` with pity = 1.0
- Rate-up split: 3 featured IDs → 0.00483 each
- Lunacy calculation: 100 pulls → 13,000

### Edge Cases
- Zero pulls → 0% all outputs
- Zero targets → handle gracefully (100% or message)
- One featured item → full rate-up (no division)
- Both modifiers (All EGO + Announcer) → rates combine correctly

---

## Technical Constraints

| Constraint | Enforcement |
|------------|-------------|
| React Compiler | No manual memo/useCallback |
| Suspense | Wrap page in `<Suspense>` |
| Error Boundary | Mandatory at page level |
| Zod validation | All inputs validated before calculator |
| Constants centralized | Import from `constants.ts` only |
| Pure functions | `extractionCalculator.ts` has zero side effects |
| i18n | No hardcoded strings |
| Responsive | Mobile-first Tailwind |
