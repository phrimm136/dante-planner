# Learning Reflection: Handle Backend Critical Issues

## What Was Easy
- Atomic repository queries required minimal syntax - @Modifying @Query with WHERE clause
- PII protection was straightforward hardcoded "Anonymous" swap
- @Value injection for configurable properties leveraged existing codebase patterns
- Test coverage expanded naturally from atomic ops → vote logic → anonymization
- Comprehensive error code spec in research.md eliminated guesswork

## What Was Challenging
- Hybrid error handling balance - categorizing user-fixable vs schema-revealing codes
- Vote decrement floor logic (preventing negative counts) needed careful WHERE design
- GlobalExceptionHandler discarded granular codes - required post-implementation redesign
- Test mocks were stale - castVote expected old save() pattern, needed findById() mocks
- Validation error whitelist violates DRY when adding new user-fixable errors

## Key Learnings
- Atomic SQL via @Modifying prevents race conditions - single DB op beats app-level locking
- Hybrid error exposure (user-fixable visible, structural hidden) balances UX with security
- @Value injection scatters config across files - consider @ConfigurationProperties instead
- Test suite reveals assumptions - mock mismatches exposed service re-fetch behavior
- Property naming consistency matters - discovered pattern conflict with existing conventions

## Spec-Driven Process Feedback
- research.md mapping was accurate and complete - every requirement traced to specific files
- plan.md execution order worked well - layer-by-layer progression prevented dependencies
- Order enabled safe stopping points after Phase 2 if security fixes needed to pause
- Spec gap: atomic operations lack idempotency for network retries - not surfaced in research
- Review identified technical debt not in plan - GlobalExceptionHandler hardcoding

## Pattern Recommendations
- Add @Modifying atomic query pattern to be-service skill with WHERE clause guards
- Document hybrid error exposure pattern - explicit user-fixable vs structural guidance
- Create @ConfigurationProperties class pattern for grouped size limits
- Add idempotency requirement to atomic operation pattern for network retry safety
- Document vote floor pattern (WHERE count > 0) to prevent negative counters

## Next Time
- Surface idempotency constraints during research phase for atomic operations
- Create error code registry at planning stage - avoid hardcoding discovery later
- Validate test mocks align with implementation before execution - stale patterns cost time
- Plan configuration consolidation early - @ConfigurationProperties over scattered @Value
- Identify DRY violations in error handling upfront - whitelist maintenance signals smell
