# Code Review: Planner Server Sync

**Verdict: NEEDS WORK**

## Spec-Driven Compliance
- Spec-to-Code Mapping from research.md: FOLLOWED
- Spec-to-Pattern Mapping: FOLLOWED (constructor injection, DTOs, layered arch)
- Execution Order from plan.md: FOLLOWED (Phases 1-5 completed in sequence)
- Rate limiting deferred (10 req/min) - documented as future enhancement
- SSE heartbeat requires @EnableScheduling annotation (MISSING)
- Frontend Zod validation applied to SSE events, isClient guards present

## What Went Well
- Clean separation: Controller → Service → Repository with proper @Transactional
- GlobalExceptionHandler with 4 custom exceptions provides consistent error responses
- Device ID filtering via custom annotation/argument resolver is elegant
- Adapter pattern cleanly routes guest (IndexedDB) vs authenticated (server) storage
- Comprehensive test coverage in PlannerServiceTest

## Code Quality Issues
- [HIGH] PlannerSseService @Scheduled requires @EnableScheduling - heartbeats won't run
- [HIGH] PlannerService.getReferenceById without existence check - LazyInitializationException risk
- [MEDIUM] Rate limiting completely missing - no DoS protection
- [MEDIUM] usePlannerMigration.migrationAttemptedRef prevents re-migration on user switch
- [MEDIUM] No SSE emitter cleanup for zombie entries - memory leak potential
- [LOW] Note validation parses JSON twice - performance overhead
- [LOW] Disabled exhaustive-deps without clear justification in usePlannerSync

## Technical Debt Introduced
- No conflict resolution toast in usePlannerSync - components must implement themselves
- SSE notification state coupled with data operations - should be separate hook
- Import endpoint doesn't trigger SSE - multi-device users won't see imported planners
- Frontend hardcodes page=0,size=100 - pagination not exposed, 100+ planners truncated
- No Jakarta Validation constraints on Planner entity fields

## Backlog Items
- Add @EnableScheduling to application config for SSE heartbeats
- Implement rate limiting filter with Bucket4j (spec: 10 req/min per user)
- Create useSSENotifications hook to decouple from usePlannerSync
- Add Planner entity Jakarta Validation constraints (title length, status enum)
- Expose pagination params in plannerApi.listPlanners()
