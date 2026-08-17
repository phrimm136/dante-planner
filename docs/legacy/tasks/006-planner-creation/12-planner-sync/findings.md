# Learning Reflection: Planner Server Sync

## What Was Easy
- Layered architecture pattern transfer - Backend CRUD followed proven User model pattern smoothly
- Storage adapter routing - Adapter pattern cleanly separated guest vs authenticated paths
- DTO validation alignment - Jakarta Validation paired naturally with Zod schemas
- SSE emitter thread safety - ConcurrentHashMap + CopyOnWriteArrayList provided clear mental model
- Spec clarity on soft deletes - deleted_at column prevented hard delete confusion

## What Was Challenging
- Custom argument resolver complexity - DeviceId header injection required understanding Spring's argument resolution pipeline
- OptimisticLockException handling - Distinguishing first-save vs update conflict required careful exception mapping
- SSE connection lifecycle - Emitter cleanup, heartbeat scheduling had subtle race conditions
- Frontend syncVersion reconciliation - Keeping version separate from syncVersion required both Types AND Schemas updates
- Rate limiting deferral - Implementing Bucket4j correctly delayed Phase 3 completion

## Key Learnings
- Spec-to-code mapping requires bidirectional tracing - prevents false starts
- Migration atomicity demands @Transactional - validate limits before persisting any records
- SSE is connection-oriented, not REST - heartbeats prevent proxy timeouts
- Device ID as HTTP header keeps API surface clean - enables filtering without modifying URLs
- Testing spec completeness reveals edge cases before code review
- Adapter pattern enables graceful feature flags without scattered conditionals

## Spec-Driven Process Feedback
- Research.md mapping was highly accurate - Spec-to-Code tables correctly predicted file dependencies
- Plan.md execution order prevented backtracking - 5-phase sequencing built upon completed layers
- Review.md revealed deferred work early - caught @EnableScheduling gap before commit
- Testing section in Instructions.md was executable - prevented discovering SSE gaps in production

## Pattern Recommendations
- Add @RestControllerAdvice pattern to backend skill docs
- Document custom argument resolver pattern for header-based routing
- Codify adapter pattern for dual storage transitions (guest → authenticated)
- Anti-pattern: Disabling exhaustive-deps without documented reason
- Document optimistic locking exception flow with HTTP status mapping

## Next Time
- Flag deferral decisions in plan.md upfront - rate limiting wasn't blocking but marked MISSING post-impl
- Add @EnableScheduling in Phase 1 dependency analysis, not Phase 3 review
- Create separate SSE notification hook - decouple from data operations
- Spec pagination parameters explicitly if >100 items expected
- Test migration re-entrance edge case (user switch) earlier
