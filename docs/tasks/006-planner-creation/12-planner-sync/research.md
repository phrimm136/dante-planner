# Research: Planner Server Sync

## Clarifications Resolved

| Question | Decision |
|----------|----------|
| User ID type mapping | Long everywhere - change frontend PlannerMetadata.userId to number |
| Device ID persistence | Persist in localStorage (existing pattern in usePlannerStorage) |
| Conflict UX (409) | Toast with manual refresh button - user clicks to reload |
| Import exceeds limit | 400 Bad Request - hard error, must delete before importing |
| syncVersion vs version | Separate fields - version for schema, syncVersion for optimistic lock |

---

## Spec-to-Code Mapping

### Backend - New Files Required

| Spec Requirement | File to Create | Pattern Source |
|------------------|----------------|----------------|
| Planner entity with UUID, JSON content, soft delete | `entity/Planner.java` | `entity/User.java` |
| MDCategory enum (5F, 10F, 15F) | `entity/MDCategory.java` | New enum |
| Data access with custom queries | `repository/PlannerRepository.java` | `repository/UserRepository.java` |
| CRUD + import + validation | `service/PlannerService.java` | `service/UserService.java` |
| SSE emitter management | `service/PlannerSseService.java` | Spring SseEmitter pattern |
| REST + SSE endpoints | `controller/PlannerController.java` | `controller/AuthController.java` |
| Exception classes (4) | `exception/Planner*.java` | New classes |
| Global error handler | `exception/GlobalExceptionHandler.java` | @RestControllerAdvice |
| Request/Response DTOs (6) | `dto/planner/*.java` | `dto/OAuthCallbackRequest.java` |
| Database schema | `db/migration/V002__create_planners_table.sql` | Flyway |

### Frontend - New Files Required

| Spec Requirement | File to Create | Pattern Source |
|------------------|----------------|----------------|
| Planner API functions | `lib/plannerApi.ts` | `lib/api.ts` |
| Server storage + SSE | `hooks/usePlannerSync.ts` | `hooks/usePlannerStorage.ts` |
| Storage adapter (guest/auth routing) | `hooks/usePlannerStorageAdapter.ts` | New pattern |
| First-login migration | `hooks/usePlannerMigration.ts` | New pattern |

### Modifications Required

| File | Change |
|------|--------|
| `frontend/src/types/PlannerTypes.ts` | Add syncVersion: number, change userId to number |
| `frontend/src/schemas/PlannerSchemas.ts` | Add syncVersion to schema, update userId type |
| `frontend/src/lib/api.ts` | Add put(), delete(), createEventSource() methods |
| `frontend/src/hooks/usePlannerAutosave.ts` | Use adapter instead of direct storage |

---

## Spec-to-Pattern Mapping

| Requirement | Pattern | Source |
|-------------|---------|--------|
| Entity with timestamps | @PrePersist/@PreUpdate, Lombok | `User.java` |
| Repository queries | JpaRepository extension | `UserRepository.java` |
| Service layer | Constructor injection, @Transactional | `UserService.java` |
| Controller layer | @RestController, DTOs, ResponseEntity | `AuthController.java` |
| Bean Validation | Jakarta @NotNull, @Size, @Valid | `OAuthCallbackRequest.java` |
| Error handling | @RestControllerAdvice | New (not yet in codebase) |
| SSE emitters | ConcurrentHashMap + CopyOnWriteArrayList | Spring SseEmitter docs |
| Zod validation | Strict schemas with runtime validation | `PlannerSchemas.ts` |
| TanStack Query | useSuspenseQuery with staleTime | `useIdentityListData.ts` |
| API client | ApiClient class with typed methods | `lib/api.ts` |

---

## Gap Analysis

### Currently Missing
- All backend planner components (entity, repo, service, controller, DTOs, exceptions)
- Frontend server sync hooks and API functions
- Global exception handler for consistent error responses
- Rate limiting configuration
- SSE infrastructure

### Can Reuse
- User authentication flow (JWT cookies)
- JwtAuthenticationFilter for token validation
- usePlannerStorage for guest mode (unchanged)
- usePlannerAutosave debounce pattern
- ApiClient base HTTP handling
- Zod schema patterns
- Entity/Service/Repository/Controller layering

---

## Testing Requirements

### Automated E2E Tests (Playwright)

**CRUD Flow:**
- Login with test user → Navigate to planner page → Create new planner
- Add identity to deck → Add EGO gift → Wait 2s for auto-save
- Refresh page → Assert all selections persist
- Delete planner → Assert removed from list

**Multi-Device Sync:**
- Open two browser contexts with same user credentials
- Context A: Edit planner (add gift)
- Context B: Assert SSE event received within 3s
- Context B: Assert toast notification appears
- Context B: Click refresh button → Assert data matches Context A

**SSE Connection:**
- Login → Navigate to planner page
- Intercept network → Assert EventSource to /api/planners/events
- Wait 15s → Assert at least one heartbeat received
- Simulate network disconnect → Restore → Assert reconnection within 10s

**Migration Flow:**
- Logout → Create 3 planners as guest (IndexedDB)
- Assert IndexedDB contains 3 planners
- Login with test user
- Assert POST to /api/planners/import called
- Assert server returns 3 planners in GET /api/planners
- Assert IndexedDB is cleared

**Guest Mode:**
- Logout → Navigate to planner page → Create planner
- Assert IndexedDB contains planner data
- Assert NO network requests to /api/planners/**
- Refresh → Assert planner loads from IndexedDB

**Conflict Resolution:**
- Context A and B: Load same planner
- Context A: Edit title → Save (succeeds)
- Context B: Edit title → Save → Assert 409 response
- Context B: Assert toast with "Refresh" button appears
- Context B: Click refresh → Assert data matches Context A

### Automated Integration Tests (Backend)

**Endpoints:**
- POST /api/planners: Creates with UUID, returns 201, correct response body
- GET /api/planners: Returns paginated list, only current user's, excludes soft-deleted
- GET /api/planners/{id}: Returns 200 for owned, 404 for non-existent or other user's
- PUT /api/planners/{id}: Increments syncVersion, returns updated planner
- PUT /api/planners/{id} with wrong version: Returns 409 with current server state
- DELETE /api/planners/{id}: Sets deleted_at, returns 204, subsequent GET returns 404

**Authorization:**
- All endpoints without JWT: Assert 401
- Access other user's planner: Assert 404 (not 403, to prevent enumeration)

**Validation:**
- POST with content > 50KB: Assert 400 PLANNER_TOO_LARGE
- POST with note > 1KB: Assert 400 NOTE_TOO_LARGE
- POST when user has 100 planners: Assert 409 PLANNER_LIMIT_EXCEEDED

**Rate Limiting:**
- Send 11 requests in 1 minute: Assert 429 on 11th request
- Assert Retry-After header present

**SSE:**
- Subscribe to /api/planners/events → Trigger planner update → Assert event received
- Assert device that updated does NOT receive event (X-Device-Id filtering)
- Assert heartbeat sent every 10s

### Manual UX Verification

**Toast Notification Timing & Appearance:**
1. Open two browser tabs, logged in as same user
2. Tab A: Make an edit to a planner (add a gift)
3. Tab B: Observe the toast notification
4. Verify: Toast appears within 2-3 seconds of Tab A's save
5. Verify: Toast message is clear ("Planner updated on another device")
6. Verify: Toast has visible "Refresh" button
7. Verify: Toast auto-dismisses after ~5 seconds if not clicked
8. Verify: Clicking "Refresh" updates the planner content immediately

**SSE Reconnection Feedback:**
1. Open planner page while logged in
2. Open DevTools Network tab, filter by "events"
3. Verify: SSE connection shows as "pending" (long-lived)
4. Disable network (DevTools → Network → Offline)
5. Verify: No jarring error message appears immediately
6. Re-enable network
7. Verify: SSE reconnects within 10 seconds (may show brief reconnecting state)
8. Verify: No data loss or duplicate notifications after reconnect

**Conflict Resolution UX:**
1. Open same planner in two tabs (Tab A, Tab B)
2. Tab A: Change planner title to "Version A"
3. Tab A: Wait for auto-save (2s debounce)
4. Tab B: Change planner title to "Version B"
5. Tab B: Wait for auto-save attempt
6. Verify: Tab B shows toast indicating conflict
7. Verify: Toast message explains situation clearly
8. Verify: "Refresh" button is prominent and clickable
9. Click "Refresh" in Tab B
10. Verify: Tab B now shows "Version A" (server version)
11. Verify: No data corruption or mixed state

**Migration Flow UX:**
1. Log out completely
2. Create 2-3 planners as guest user
3. Make meaningful edits (add identities, gifts, notes)
4. Log in with Google OAuth
5. Verify: Migration happens automatically OR clear prompt appears
6. Verify: No data loss - all planners appear after login
7. Verify: Planners are fully functional after migration
8. Log out and log back in
9. Verify: Planners still exist (confirms server persistence)

**Guest Mode Clarity:**
1. Visit site without logging in
2. Create a planner and make edits
3. Verify: No confusing error messages about server sync
4. Verify: Clear indication that data is stored locally (if any UI shows this)
5. Close browser completely
6. Reopen browser and visit site
7. Verify: Guest planner still exists (localStorage/IndexedDB persistence)

**Rate Limiting UX:**
1. Log in and open planner page
2. Rapidly make 15+ edits in quick succession (within 1 minute)
3. Verify: After rate limit hit, clear error message appears
4. Verify: Error message suggests waiting before retrying
5. Verify: No data loss - local state preserved
6. Wait 1 minute
7. Verify: Saves resume working normally

---

## Technical Constraints

### Dependencies
- Backend: Spring Boot 4.0, JPA/Hibernate, MySQL 5.7+, Bucket4j (rate limiting)
- Frontend: React 19, TanStack Query, Zod, EventSource API (native)

### Compatibility
- JWT cookies: httpOnly + secure (existing)
- EventSource: No IE11 support (acceptable)
- UUID: crypto.randomUUID() with fallback
- Flyway: Immutable migrations

### Performance
- SSE heartbeat: 10s interval
- Auto-save debounce: 2s (existing)
- TanStack Query staleTime: 7 days (existing)
- DB indexes: (user_id, last_modified_at DESC), (user_id, status)

### Pattern Compliance
- Frontend: useSuspenseQuery, Zod, shadcn/ui, Tailwind
- Backend: Constructor injection, DTOs, @Transactional, Flyway
- API: Consistent error format from GlobalExceptionHandler
