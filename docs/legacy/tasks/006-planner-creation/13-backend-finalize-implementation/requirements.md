# Task: Backend Planner Security & Reliability Finalization

## Description

Address all identified security vulnerabilities and reliability issues in the planner sync system. This is a hardening pass that adds defense-in-depth validation, prevents resource leaks, and protects against abuse.

### HIGH Priority Issues

1. **Backend Content Structure Validation**: The backend currently accepts any JSON string as planner content without validating its structure. While frontend uses comprehensive Zod schemas (`PlannerContentSchema`), the backend only checks `@NotNull` and size limits. A malicious client could bypass frontend validation and store corrupted/malicious data. Create a `PlannerContentValidator` service that validates:
   - Required top-level keys exist (`title`, `category`, `selectedKeywords`, `equipment`, `deploymentOrder`, `floorSelections`, `sectionNotes`)
   - `category` is a valid MD category enum (`5F`, `10F`, `15F`)
   - Arrays are arrays, objects are objects (type checking)
   - Equipment sinner indices are valid (0-11)

2. **User Existence Check**: `PlannerService` uses `userRepository.getReferenceById(userId)` which returns a JPA proxy without hitting the database. If the user doesn't exist (deleted account with valid JWT), this causes `LazyInitializationException` during save. Replace with `findById().orElseThrow()` pattern for fail-fast behavior.

### MEDIUM Priority Issues

3. **Migration Key Not User-Scoped**: `usePlannerMigration.ts` uses localStorage key `planner-migration-done` without user ID. If User A logs in, migrates, logs out, then User B logs in on the same device, User B's local planners won't migrate. Change to `planner-migration-done:${userId}`.

4. **Rate Limiting Missing**: No DoS protection on planner endpoints. Implement per-user rate limiting using Bucket4j:
   - CRUD operations: 60 requests/minute
   - Import endpoint: 10 requests/minute
   - SSE subscribe: 1 request/minute

5. **SSE Emitter Zombie Cleanup**: Emitters are removed on `onCompletion`, `onTimeout`, `onError`, but ungraceful disconnects may leave zombies until the next heartbeat. Add a separate 60-second cleanup scan that probes all connections.

### LOW Priority Issues

6. **Note Validation Double JSON Parsing**: `validateNoteSize()` re-parses the JSON content that `validateContentSize()` already parsed. Refactor to pass the already-parsed `JsonNode` between methods.

7. **exhaustive-deps Disabled in usePlannerSync**: The cleanup useEffect has eslint-disable for react-hooks/exhaustive-deps. Wrap `disconnectSSE` in `useCallback` and properly list it in dependencies.

## Research

- Read `frontend/src/schemas/PlannerSchemas.ts` for content structure validation rules (reference for backend validator)
- Check existing exception patterns in `backend/src/main/java/org/danteplanner/backend/exception/`
- Review Bucket4j Spring Boot integration patterns
- Understand the SSE heartbeat mechanism in `PlannerSseService.java`
- Check how `useAuthQuery` provides user data in migration hook

## Scope

Files to READ for context:
- `frontend/src/schemas/PlannerSchemas.ts` - Zod schemas defining content structure
- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java` - Current validation logic
- `backend/src/main/java/org/danteplanner/backend/service/PlannerSseService.java` - SSE emitter management
- `backend/src/main/java/org/danteplanner/backend/exception/*.java` - Exception patterns
- `frontend/src/hooks/usePlannerMigration.ts` - Migration key usage
- `frontend/src/hooks/usePlannerSync.ts` - SSE and deps issue

## Target Code Area

### New Files to Create
- `backend/src/main/java/org/danteplanner/backend/validation/PlannerContentValidator.java` - JSON structure validator
- `backend/src/main/java/org/danteplanner/backend/exception/UserNotFoundException.java` - Missing user exception
- `backend/src/main/java/org/danteplanner/backend/exception/RateLimitExceededException.java` - Rate limit exception
- `backend/src/main/java/org/danteplanner/backend/config/RateLimitConfig.java` - Bucket4j configuration

### Files to Modify
- `backend/pom.xml` - Add Bucket4j dependency
- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java` - Integrate validator, fix getReferenceById, refactor note validation
- `backend/src/main/java/org/danteplanner/backend/service/PlannerSseService.java` - Add zombie cleanup scheduled task
- `backend/src/main/java/org/danteplanner/backend/controller/PlannerController.java` - Add rate limiting
- `backend/src/main/java/org/danteplanner/backend/exception/GlobalExceptionHandler.java` - Handle new exceptions
- `frontend/src/hooks/usePlannerMigration.ts` - User-scoped migration key
- `frontend/src/hooks/usePlannerSync.ts` - Fix useCallback/deps

## Testing Guidelines

### Manual Backend Testing

#### Content Validation Testing
1. Start the backend server with `./mvnw spring-boot:run`
2. Obtain a valid JWT by logging in through the frontend
3. Use curl to POST to `/api/planners` with the JWT in cookies
4. Send valid planner content matching PlannerContentSchema
5. Verify 201 Created response with planner data returned
6. Send content missing `category` field
7. Verify 400 response with `INVALID_CONTENT_STRUCTURE` error code
8. Send content with `category: "INVALID"` (not 5F/10F/15F)
9. Verify 400 response with validation error
10. Send content with `equipment` as an array instead of object
11. Verify 400 response with type mismatch error
12. Send content with sinner index `99` in equipment keys
13. Verify 400 response with invalid sinner index error

#### User Existence Testing
1. Manually create a JWT token for non-existent user ID 999999
2. POST to `/api/planners` with this JWT
3. Verify 404 response with `USER_NOT_FOUND` error (not 500 Internal Server Error)

#### Rate Limiting Testing
1. Authenticate as a test user
2. Write a script to send 61 rapid requests to GET `/api/planners`
3. Verify requests 1-60 succeed with 200 OK
4. Verify request 61 returns 429 Too Many Requests
5. Wait 60 seconds
6. Verify subsequent request succeeds

### Manual Frontend Testing

#### Migration User Scoping Testing
1. Open browser, ensure completely logged out
2. Navigate to planner page
3. Create a new planner locally (should save to IndexedDB)
4. Log in as User A
5. Verify planner migrates to server (check network tab for import call)
6. Open DevTools > Application > Local Storage
7. Verify key exists: `planner-migration-done:${userA.id}`
8. Log out completely
9. Create another local planner
10. Log in as User B (different account)
11. Verify new local planner migrates (import call in network tab)
12. Verify separate key exists: `planner-migration-done:${userB.id}`

#### SSE Connection Testing
1. Log in and navigate to planner page
2. Open DevTools > Network tab, filter by EventStream
3. Verify SSE connection established
4. On another device/browser, log in as same user and modify a planner
5. Verify first browser receives SSE notification and updates

### Automated Functional Verification

- [ ] Content validation rejects missing required fields with 400 and `INVALID_CONTENT_STRUCTURE`
- [ ] Content validation rejects invalid category enum values
- [ ] Content validation rejects wrong types (array where object expected)
- [ ] Content validation accepts valid content with all required fields
- [ ] User existence check returns 404 for non-existent user IDs
- [ ] User existence check allows valid users to create planners
- [ ] Rate limiting returns 429 after exceeding 60 requests/minute
- [ ] Rate limiting bucket resets after time window expires
- [ ] SSE zombie cleanup removes stale emitters within 60 seconds
- [ ] Migration creates separate state per user ID in localStorage
- [ ] Migration works correctly when switching between users
- [ ] Note validation uses single JSON parse (verify with profiler/logging)
- [ ] usePlannerSync has no eslint warnings after fix
- [ ] usePlannerSync properly cleans up SSE on unmount

### Edge Cases

- [ ] Empty content string `""`: Rejected as invalid, not just null check
- [ ] Content with extra unknown fields: Accepted (forward compatibility - don't break existing clients)
- [ ] Unicode characters in planner title: Accepted and stored correctly
- [ ] Large valid content at 49KB: Accepted (under 50KB limit)
- [ ] Content at exactly 50KB: Rejected (limit is exclusive)
- [ ] Concurrent rapid requests: All correctly counted against rate limit
- [ ] Rapid user switching: Each user's migration state tracked independently
- [ ] SSE reconnect after server restart: Client reconnects successfully via exponential backoff
- [ ] Malformed JSON content: Returns 400 with `INVALID_JSON` error code

### Integration Points

- [ ] GlobalExceptionHandler: `UserNotFoundException` returns 404 status
- [ ] GlobalExceptionHandler: `RateLimitExceededException` returns 429 status
- [ ] GlobalExceptionHandler: `PlannerValidationException` returns 400 with error code
- [ ] JWT filter: Extracts user ID correctly for rate limiting key
- [ ] PlannerService: Content validator called before repository save
- [ ] PlannerSseService: Cleanup runs independently of heartbeat schedule
- [ ] IndexedDB + localStorage: Migration uses IndexedDB for data, localStorage for flag
- [ ] TanStack Query: SSE events invalidate `plannerQueryKeys.list()` and `plannerQueryKeys.detail()`
