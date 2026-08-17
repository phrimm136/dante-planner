# Review: Format Description Text

## Spec-Driven Compliance

- **Spec-to-Code Mapping**: FOLLOWED - All mappings implemented per research.md
- **Spec-to-Pattern Mapping**: FOLLOWED - Pattern sources correctly identified and copied
- **Technical Constraints**: FOLLOWED - Popover, icon path, skill tag behavior all correct
- **Execution Order**: FOLLOWED - All 5 phases completed in order with checkpoints verified
- **Icon Path Correction**: FOLLOWED - Used actual path `/images/battleKeywords/`
- **No Deviations**: Implementation matches all spec requirements

## What Went Well

- Clean SOLID separation: pure functions in lib/, hooks in hooks/, presentation in components/
- Pattern consistency with formatSanityCondition.ts using String.matchAll() for stateless regex
- Comprehensive TypeScript types with discriminated unions
- Excellent test coverage (62 tests) covering edge cases
- Forgiving error handling with fallbacks for unknown keywords, missing colors, broken icons

## Code Quality Issues

- **[HIGH]** FormattedKeyword manages both presentation AND image loading state - violates SRP
- **[HIGH]** resolveKeyword() has 3 return shapes coupled to rendering - violates Open/Closed
- **[MEDIUM]** useKeywordFormatter exposes 3 methods + context when only format() is needed
- **[MEDIUM]** FormattedDescription splits by newlines before parsing - keywords spanning lines would break
- **[MEDIUM]** Color applied to both trigger and popover header - DRY violation
- **[LOW]** KEYWORD_PATTERN exported but not documented why
- **[LOW]** BuffType allows string escape hatch defeating type safety

## Technical Debt Introduced

- Accessibility shortcuts: No ARIA labels or aria-describedby on popovers
- No loading states: useSuspenseQuery provides no per-component loading indication
- Icon path logic split between keywordFormatter.ts and component fallback
- Color fallback chain has 3 levels that could break on data structure changes

## Backlog Items

- Extract icon loading to useKeywordIcon() hook to remove state from FormattedKeyword
- Simplify useKeywordFormatter API to only expose format()
- Add keyword validation for newlines and nested brackets
- Add ARIA labels and aria-describedby for accessibility
- Consolidate color fallback to pure function getColorWithFallback()
