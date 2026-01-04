# Learning Reflection: User Account Soft-Delete

## What Was Easy

- Pattern reuse from Planner/PlannerVote soft-delete mapped directly to User without reinvention
- Research.md's unambiguous gap analysis (6 create, 8 modify) meant zero scope guessing
- Instant timestamp consistency eliminated timezone ambiguity across all new fields
- 29+ tests written systematically because edge cases were explicitly in instructions.md

## What Was Challenging

- Scheduler transaction scope: loop-level @Transactional caused cascading failures, needed per-user
- SQL COMMENT syntax unsupported by Flyway, discovered only at build verification
- Sentinel user guard placement required testing multiple locations in auth flow
- Rate limiting on DELETE endpoint forgotten initially, discovered in review
- reactivateAccount() idempotency required early-return guard addition

## Key Learnings

- Soft-delete adoption curve: once first pattern exists, subsequent implementations are exponentially faster
- Scheduler isolation: per-entity @Transactional essential for batch operations on failing entities
- Spec-driven test completeness: writing tests from requirements catches integration gaps
- Sentinel records as null-safety pattern: actual DB user (id=0) simplifies cascade logic vs nullable FKs
- Multi-phase deletion is UX design: 30-day grace period separates angry deletion from real departure
- Config-driven grace period caught requirements bug that code review alone missed

## Spec-Driven Process Feedback

- Research.md mapping was 100% accurate: every Spec-to-Code row matched reality, no rework
- Bottom-up implementation order prevented dependency inversions perfectly
- Edge case testing list in instructions.md was gold for catching real implementation gaps
- API contract spec revealed AuthResult needed new reactivated field before building wrong DTO
- Manual testing checklist would catch scheduler isolation faster than unit tests

## Pattern Recommendations

- Extract soft-delete as composable mixin to be-service skill (Planner + PlannerVote + User pattern)
- Document scheduler isolation as mandatory: per-entity @Transactional with loop-level warning
- Document sentinel/guardian entity pattern as alternative to nullable FKs
- Config-driven time durations as non-negotiable for user-facing SLAs
- Reactivation flag in auth responses as standard pattern for account recovery flows

## Next Time

- Run integration tests before code review to catch mocking and idempotency issues earlier
- Validate scheduler timing separately with manual past-date test before full implementation
- Add feature-level state diagrams and API contracts upfront for all multi-phase features
- Include SQL sanity checks in migration tests for Flyway compatibility
- Test guard paths (sentinel, deleted token rejection) with equal investment as happy paths
