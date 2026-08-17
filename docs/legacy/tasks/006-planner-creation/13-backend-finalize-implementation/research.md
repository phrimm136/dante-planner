# Research: Backend Planner Security & Reliability Finalization

## Clarifications Resolved

- **Rate limit values**: Must be configurable via `application.properties` (not hardcoded)
- **Content validation strictness**: Validator should be STRICT - reject unknown fields (not forward-compatible)

---

## Spec-to-Code Mapping

| Requirement | Target Files | Modification Type |
|------------|-------------|-------------------|
| HIGH.1: Content Validation | New: `validation/PlannerContentValidator.java` | Create STRICT JSON structure validator |
| HIGH.2: User Existence | `service/PlannerService.java` (lines 67, 203) | Replace `getReferenceById()` with `findById().orElseThrow()` |
| MEDIUM.3: Migration Key | `frontend/src/hooks/usePlannerMigration.ts` (line 15) | Add user ID to localStorage key |
| MEDIUM.4: Rate Limiting | New: `config/RateLimitConfig.java`, `exception/RateLimitExceededException.java` | Configurable Bucket4j |
| MEDIUM.5: SSE Cleanup | `service/PlannerSseService.java` | Add 60s scheduled cleanup task |
| LOW.6: Double Parse | `service/PlannerService.java` (lines 243-295) | Pass `JsonNode` between methods |
| LOW.7: useCallback | `frontend/src/hooks/usePlannerSync.ts` (lines 153, 171) | Fix React hook deps |

---

## Spec-to-Pattern Mapping

| Requirement | Existing Pattern | Source File |
|------------|-----------------|-------------|
| Content Validation Exception | `PlannerValidationException` with errorCode | `exception/PlannerValidationException.java` |
| User Not Found Exception | Similar to `PlannerNotFoundException` | `exception/PlannerNotFoundException.java` |
| Service Validation | Called before repository save | `service/PlannerService.java:64` |
| Exception Handler | `@ExceptionHandler` + ErrorResponse record | `exception/GlobalExceptionHandler.java` |
| Frontend Hook Cleanup | useCallback + useRef + useEffect | `hooks/usePlannerSync.ts` |

---

## Pattern Enforcement (MANDATORY)

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `PlannerContentValidator.java` | `PlannerValidationException.java` | Error code + message structure |
| `UserNotFoundException.java` | `PlannerNotFoundException.java` | @Getter class with ID constructor |
| `RateLimitExceededException.java` | `PlannerNotFoundException.java` | Simple RuntimeException with context |
| `RateLimitConfig.java` | `SecurityConfig.java` | @Configuration with @ConfigurationProperties |

---

## Existing Utilities

| Category | Location | Found |
|----------|----------|-------|
| Exceptions | `exception/*.java` | PlannerValidationException, PlannerNotFoundException, PlannerConflictException, PlannerLimitExceededException |
| Services | `service/*.java` | PlannerService, PlannerSseService, UserService, JwtService |
| Validation | `PlannerService.java` | validateContentSize(), validateNoteSize() |
| Rate Limiting | None | Must add Bucket4j |
| Frontend Auth | `hooks/useAuthQuery.ts` | Provides user.id for scoping |

---

## Gap Analysis

### Must Create
- `validation/PlannerContentValidator.java` - STRICT JSON structure validation (reject unknown fields)
- `exception/UserNotFoundException.java` - 404 for missing users
- `exception/RateLimitExceededException.java` - 429 response
- `config/RateLimitConfig.java` - Bucket4j beans with @ConfigurationProperties

### Must Modify
- `application.properties` - Add configurable rate limit values
- `PlannerService.java` - Validator integration, user lookup fix, note validation refactor
- `PlannerController.java` - Rate limit checks
- `PlannerSseService.java` - Scheduled cleanup task
- `GlobalExceptionHandler.java` - New exception handlers
- `usePlannerMigration.ts` - User-scoped key
- `usePlannerSync.ts` - useCallback fix
- `pom.xml` - Bucket4j dependency

### Can Reuse
- `PlannerValidationException` error code pattern
- `GlobalExceptionHandler` handler pattern
- `PlannerSseService.subscribe()` cleanup callbacks

---

## Testing Requirements

### Manual Tests
- POST `/api/planners` with invalid content → 400 INVALID_CONTENT_STRUCTURE
- POST with unknown extra fields → 400 (strict validation)
- POST with non-existent user JWT → 404 USER_NOT_FOUND
- Requests exceeding configured limit → 429
- User A/B login cycle → separate migration keys in localStorage
- Kill SSE connection abruptly → cleaned up within 60s

### Automated Tests
- Unit: PlannerContentValidator with valid/invalid/unknown-field payloads
- Unit: Rate limit bucket accumulation and reset
- Integration: Content validation rejection in controller
- Integration: User existence 404 response
- Frontend: Migration key includes user ID

---

## Technical Constraints

| Constraint | Solution |
|-----------|----------|
| Bucket4j dependency | Add to pom.xml |
| JSON validation (STRICT) | Use ObjectMapper.readTree(), validate ONLY known fields |
| User lookup fail-fast | findById().orElseThrow() |
| Double JSON parse | Pass JsonNode parameter |
| Hook deps warning | Wrap in useCallback |
| Per-user rate limit | Key by userId from JWT |
| Configurable limits | @ConfigurationProperties in RateLimitConfig |

---

## Implementation Order

1. Create exception classes (dependencies for other components)
2. Create PlannerContentValidator service (STRICT mode)
3. Add Bucket4j config with configurable properties
4. Modify PlannerService (validator, user lookup, note refactor)
5. Modify PlannerController (rate limits)
6. Modify PlannerSseService (cleanup task)
7. Modify GlobalExceptionHandler (new handlers)
8. Fix frontend hooks (migration key, useCallback)
