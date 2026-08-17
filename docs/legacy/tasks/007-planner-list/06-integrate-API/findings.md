# Learning Reflection: FE/BE Username API Alignment

## What Was Easy

- Type alignment process - research mapped clear spec-to-code path, execution order prevented circular dependencies
- i18n integration - `association.json` namespace already existed, fallback logic with `defaultValue` worked out of the box
- Validation mocking - test mocks followed straightforward pattern change from single field to two fields
- Component isolation - PlannerCard is single-purpose, change had zero ripple effects

## What Was Challenging

- Duplicate type detection - two identical `PublicPlanner` definitions existed with subtle differences
- Schema vs type sync risk - schema field update must match type exactly or component destructuring fails silently
- Defensive null validation discovery - instructions showed "Unknown" fallback but single missing field case needed placeholder logic
- Existing pattern discovery - Header already had username formatting logic that needed extraction

## Key Learnings

- Consolidating duplicate types during feature work prevents future maintenance debt
- i18n `defaultValue` parameter eliminates need for static validation of translation keys
- Phase-ordered implementation (Type → Formatter → Schema → Component) prevents cascading errors
- Formatter as pure composition layer separates concerns cleanly - easy to test, easy to refactor
- React Compiler memoization eliminates need for premature `useMemo` optimization
- Type safety cascades through validation - TypeScript catches surface area before runtime

## Spec-Driven Process Feedback

- Research.md mapping was accurate - implementation order matched planned phases
- Plan order was optimal - Phase 1 isolated TypeScript errors in one pass
- Spec ambiguity: formatter location was underspecified (co-location principle not documented)

## Pattern Recommendations

- Document co-location principle: related formatters in single file, not separate files
- Add re-export pattern to skill docs: remove duplicate type, re-export from canonical source
- Defensive fallback template: define behavior for each missing field case in composed strings
- Formatter testing template: valid inputs, missing translations, empty inputs, edge cases

## Next Time

- Lead with type consolidation in spec phase - catch duplicates during research
- Explicit documentation of composition logic - specify placeholder strategy upfront
- Check for existing implementations before creating new utilities
- Profile before adding memoization - establish baseline first
