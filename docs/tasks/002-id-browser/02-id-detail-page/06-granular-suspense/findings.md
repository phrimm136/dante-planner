# Findings: Granular Suspense for Identity Detail Page

## What Was Easy

- Paired hooks pattern well-documented in useIdentityListData.ts - copy-paste-adapt worked
- Micro-Suspense boundaries had precedent in IdentityCard + IdentityName
- Query key factory was reusable - extending with .i18n() required 2 lines
- Component wrapping pattern scaled linearly across 5 new components
- Tests passed on first run (26/26)

## What Was Challenging

- Passive component architecture mismatch - required adapter pattern not direct wrap
- Skeleton dimension sizing - spec said "derived" but no measurement methodology
- TraitsDisplay conversion was data-transform heavy - new hook from scratch
- Test brittleness with hardcoded constants (level 55 → MAX_LEVEL)
- File naming too specific - StyledSkillName → StyledName after detecting duplication

## Key Learnings

- Paired hooks enable granular loading without architectural redesign
- Micro-Suspense scales better with user perception than full-page suspension
- Query cache keys must be designed upfront for future Suspense patterns
- Error handling in i18n needs defensive patterns (?? '' fallback)
- Skeleton sizing is empirical measurement, not algorithmic
- Pattern enforcement requires reading working examples FIRST

## Spec-Driven Process Feedback

- Research.md mapping was 100% accurate - no spec ambiguities
- Bottom-up order correct but missed PassiveI18n's adapter requirement
- Skeleton sizing spec was under-specified (no measurement method)
- Pattern sources listed but read-first discipline not enforced

## Pattern Recommendations

- Document paired-hook factory pattern as reusable template
- Establish micro-Suspense boundary sizing guidelines (when to split vs batch)
- Add defensive pattern for i18n error handling (?? '' fallback)
- Export skeleton dimensions as constants from components

## Next Time

- Run architecture audit before refactoring - find hidden coupling
- Prototype skeleton sizing before implementation
- Make pattern study a pre-implementation gate
- Validate spec with implementation constraints during planning
- Document trade-offs explicitly in comments or decision log
