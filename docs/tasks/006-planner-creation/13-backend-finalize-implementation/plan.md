# Execution Plan: Backend Planner Security & Reliability Finalization

## Planning Gaps

**None identified.** Research document is comprehensive with clear spec-to-code mappings.

**Minor decisions made:**
- Bucket4j version: Latest stable Spring Boot 4.x compatible
- Rate limit storage: In-memory ConcurrentHashMap (suitable for single-instance)

---

## Execution Overview

Implements 7 security/reliability fixes across 6 phases (16 steps):
1. **Exceptions** - Create UserNotFoundException, RateLimitExceededException
2. **Validation** - Create STRICT PlannerContentValidator
3. **Service Layer** - Fix PlannerService (user lookup, validator, note parse)
4. **Rate Limiting** - Bucket4j dependency, config, controller integration
5. **Frontend + SSE** - Migration key scoping, useCallback fix, SSE cleanup
6. **Tests** - Unit and integration tests

---

## Execution Order

### Phase 1: Exception Classes

**Step 1.** `exception/UserNotFoundException.java`
- Action: Create exception following PlannerNotFoundException pattern
- Depends on: none
- Enables: F2

**Step 2.** `exception/RateLimitExceededException.java`
- Action: Create exception with userId and endpoint context
- Depends on: none
- Enables: F4

**Step 3.** `exception/GlobalExceptionHandler.java`
- Action: Add handlers for UserNotFoundException (404) and RateLimitExceededException (429)
- Depends on: Steps 1, 2
- Enables: F2, F4

### Phase 2: Content Validator

**Step 4.** `validation/PlannerContentValidator.java`
- Action: Create STRICT JSON validator
  - Required keys: title, category, selectedKeywords, equipment, deploymentOrder, floorSelections, sectionNotes
  - Category enum: 5F, 10F, 15F
  - Type validation (arrays/objects)
  - Sinner indices 0-11
  - Reject unknown fields
- Depends on: none
- Enables: F1

### Phase 3: Service Layer Fixes

**Step 5.** `service/PlannerService.java`
- Action: Three modifications:
  1. Replace getReferenceById with findById().orElseThrow(UserNotFoundException) at lines 67, 203
  2. Integrate PlannerContentValidator before save
  3. Refactor validateContentSize/validateNoteSize to pass JsonNode
- Depends on: Steps 1, 4
- Enables: F1, F2, F6

**Step 6.** `service/PlannerSseService.java`
- Action: Add @Scheduled cleanup task (60s interval)
- Depends on: none
- Enables: F5

### Phase 4: Rate Limiting

**Step 7.** `pom.xml`
- Action: Add Bucket4j dependency
- Depends on: none
- Enables: F4

**Step 8.** `application.properties`
- Action: Add configurable rate limit properties
- Depends on: none
- Enables: F4

**Step 9.** `config/RateLimitConfig.java`
- Action: Create @Configuration with @ConfigurationProperties
- Depends on: Steps 7, 8
- Enables: F4

**Step 10.** `controller/PlannerController.java`
- Action: Add rate limit checks to all endpoints
- Depends on: Steps 2, 9
- Enables: F4

### Phase 5: Frontend Fixes

**Step 11.** `hooks/usePlannerMigration.ts`
- Action: Change key to `planner-migration-done:${user.id}`
- Depends on: none
- Enables: F3

**Step 12.** `hooks/usePlannerSync.ts`
- Action: Wrap disconnectSSE in useCallback, fix deps array
- Depends on: none
- Enables: F7

### Phase 6: Tests

**Step 13.** `PlannerContentValidatorTest.java`
- Action: Unit tests for validator (valid, invalid, strict mode)
- Depends on: Step 4
- Enables: F1 test coverage

**Step 14.** `PlannerServiceTest.java`
- Action: Add tests for user existence, validator integration
- Depends on: Step 5
- Enables: F2, F6 test coverage

**Step 15.** `RateLimitConfigTest.java`
- Action: Unit tests for rate limiting buckets
- Depends on: Step 9
- Enables: F4 test coverage

**Step 16.** `PlannerControllerTest.java`
- Action: Integration tests for 400, 404, 429 responses
- Depends on: Step 10
- Enables: Integration test coverage

---

## Verification Checkpoints

| After Step | Verify | Method |
|------------|--------|--------|
| 3 | Exception handlers return correct HTTP codes | Unit test GlobalExceptionHandler |
| 5 | F1, F2, F6 work in isolation | Run PlannerServiceTest |
| 6 | F5 cleanup runs | Check logs for scheduled task |
| 10 | F4 rate limiting works | Manual curl test (61 requests) |
| 12 | F7 no eslint warnings | yarn lint |
| 16 | All features integrated | Full test suite pass |

---

## Rollback Strategy

| Step | If Fails | Rollback Action |
|------|----------|-----------------|
| 7 | Maven dependency issue | Remove Bucket4j from pom.xml |
| 5 | User lookup breaks flow | Revert to getReferenceById |
| 10 | Rate limiting too aggressive | Comment out checks in controller |
| 12 | React hook issues | Re-add eslint-disable |

**Safe Stopping Points:**
- After Phase 2: Core validation works, rate limiting deferred
- After Phase 5: All features implemented, tests pending
- After Phase 6: Complete with test coverage
