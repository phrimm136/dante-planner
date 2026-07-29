# Learning Reflection: Planner Config Loading Robustness Pass

## What Was Easy

- Pattern replication from PlannerContentValidator to ContentVersionValidator - @Component, @Value injection mapped cleanly
- Config values already existed in application.properties - no discovery work needed
- usePlannerConfig hook infrastructure already fetching correctly - only needed to use the data
- Hardcoded values isolated in one file at predictable locations - syntactic replacement worked

## What Was Challenging

- Two-tier validation model - strict for new planners vs lenient for existing ones required careful specification
- Test expectation reversal - schema change meant usePlannerConfig.test.ts empty array test needed to invert
- Error message security - code review caught version list exposure, required generic error messages
- Duplicate logic detection - RR validation in two paths initially missed, caught in review

## Key Learnings

- Constructor injection ordering matters - validators must sequence correctly (version before content)
- Zod array .min(1) catches backend misconfiguration at parse time, preventing silent downstream errors
- Backend validation must run before content JSON parsing to fail fast on invalid versions
- Two-tier validation (strict-on-write, lenient-on-read) is legitimate for backward compatibility
- Once frontend fetches config from backend, hardcoded values become dangerous drift points
- Service integration tests need different assertions than unit tests (exception propagation vs mock verification)

## Spec-Driven Process Feedback

- Research mapping was accurate - gap identification matched implementation needs exactly
- Pattern source guidance (PlannerContentValidator) eliminated design decisions - copy structure, adjust logic
- Plan order held without reordering - suggests good upfront analysis
- Missing clarification: RR feature is hidden from users - discovered from code context, not instructions

## Pattern Recommendations

- Add two-tier validation pattern to be-service skill (strict create, lenient read)
- Document error factory methods in validators for consistent exception creation
- Document risk of hardcoded config values alongside dynamic fetching in frontend patterns
- Add integration test structure for validators (assertThrows + never().save verification)

## Next Time

- Audit for config drift proactively - search all files for hardcoded numeric versions before implementation
- Test rejection cases before acceptance cases - catches "what makes this valid?" faster
- Run frontend schema tests first - catches data shape problems before backend work
- Add explicit "what should NOT be in error messages?" as validation step during implementation
