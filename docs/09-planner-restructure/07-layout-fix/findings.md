# Learning Reflection: Planner Editor UI Standardization

## What Was Easy

- Pattern extraction from reference components (ComprehensiveGiftSummary, EGOGiftObservationEditPane)
- Constants foundation first approach unblocked all summary conversions
- Native button accessibility handled Enter/Space automatically without manual onKeyDown
- i18n key additions followed consistent naming pattern across 4 languages
- Test updates were mechanical once button conversion pattern understood

## What Was Challenging

- Test mock inconsistency - i18n mocks hardcoded keys; required updating to "egoGift" pattern
- Filter reset timing semantics - `if (!open)` felt counterintuitive until research confirmed it
- EA counter removal mid-execution - User decision changed StartGiftSummary props post-test
- FloorThemeGiftSection flex layout preservation - Required verification after PlannerSection wrap

## Key Learnings

- Foundation-first mitigates ripple effects (EMPTY_STATE before component changes)
- Pattern divergence emerges from incremental features without standardization policy
- Native HTML elements reduce code (~10 LOC per component) while gaining browser defaults
- Test mocks can hide inconsistencies - hardcoded i18n keys masked naming drift
- Korean terminology context-specific - "E.G.O 기프트" over literal "선물" translation

## Spec-Driven Process Feedback

- research.md mapping was precise - all 21 items covered, no discovery surprises
- plan.md execution order worked - foundation-first prevented rework
- Spec gap: filter reset timing lacked `if (!open)` semantics explanation
- EA counter removal not spec'd - should define "summary view content" constraints explicitly

## Pattern Recommendations

- Document button anti-pattern: `div role="button" + onKeyDown` less accessible than native
- Create EMPTY_STATE pattern in constants reference for all empty summary states
- Standardize DialogFooter: Reset (outline, left) | Done (default, right) as mandatory
- Add filter reset on close pattern to fe-data skill with useEffect explanation

## Next Time

- Audit i18n test mocks before test phase to ensure key naming consistency
- Define content constraints (EA counter, filter counts) in spec, not as late user feedback
- Validate flex layout in isolation with micro-test before full integration
- Schedule user clarifications earlier via three-question protocol in research phase
