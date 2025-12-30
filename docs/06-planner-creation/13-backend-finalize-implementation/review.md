# Code Quality Review: Backend Planner Security & Reliability

## Spec-Driven Compliance

- **Spec-to-Code Mapping: FOLLOWED** - All 7 requirements implemented (content validation, user existence, rate limiting, SSE cleanup, double parse fix, useCallback fix). Step 11 deferred.
- **Spec-to-Pattern Mapping: FOLLOWED** - Exception patterns copied from PlannerNotFoundException, validation patterns match PlannerValidationException, RateLimitConfig follows @ConfigurationProperties pattern.
- **Technical Constraints: MINOR VIOLATION** - Bucket4j version mismatch (pom.xml has 8.10.1, code.md claims 8.17.0).
- **Execution Order: FOLLOWED** - All 6 phases completed in sequence. Phase 5 Step 11 intentionally skipped with justification.

## What Went Well

- **Comprehensive validation** - PlannerContentValidator validates JSON structure AND ID existence using GameDataRegistry.
- **Clean separation of concerns** - GameDataRegistry, SinnerIdValidator, PlannerContentValidator follow SRP.
- **SSE zombie cleanup** - @Scheduled 60s cleanup with proper error handling.
- **Configurable rate limiting** - @ConfigurationProperties with separate CRUD/import/SSE buckets per user+endpoint.
- **Proper exception handling** - UserNotFoundException (404) and RateLimitExceededException (429) in GlobalExceptionHandler.

## Code Quality Issues

- [HIGH] **Migration key NOT user-scoped** - usePlannerMigration.ts uses generic key without user ID. Deferred but not tracked.
- [MEDIUM] **Single error code for all validation failures** - Client cannot distinguish failure types.
- [MEDIUM] **Rate limit config naming inconsistency** - import-config vs import pattern mismatch.
- [MEDIUM] **No rate limit observability** - No metrics/logging for bucket state.
- [LOW] **Magic constants in validator** - Size limits hardcoded instead of in application.properties.
- [LOW] **SSE cleanup logs at INFO** - Should be DEBUG (runs every 60s).

## Technical Debt Introduced

- **In-memory rate limiting** - ConcurrentHashMap not horizontally scalable.
- **No configuration hot-reload** - Rate limit changes require restart.
- **Validator tightly coupled** - GameDataRegistry/SinnerIdValidator not abstracted.
- **localStorage no error handling** - usePlannerMigration lacks try-catch.

## Backlog Items

- **Redis-backed rate limiting** - For multi-instance deployment support.
- **Granular validation error codes** - MISSING_REQUIRED_FIELD, INVALID_CATEGORY, etc.
- **Server-side migration tracking** - User entity field + API endpoint.
- **Rate limit metrics** - Micrometer counters for Prometheus/Grafana.
- **Configuration validation** - @Min/@Max constraints on RateLimitConfig fields.
