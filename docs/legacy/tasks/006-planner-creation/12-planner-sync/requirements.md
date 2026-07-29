# Task: Planner Server Sync with SSE Notifications

## Description

Implement server-side planner persistence with real-time cross-device synchronization via Server-Sent Events (SSE).

### Core Functionality

**Storage Adapter Pattern**: Create a routing layer that directs planner operations to the appropriate backend:
- Guest users: Continue using IndexedDB (existing behavior)
- Authenticated users: Save to server via REST API

**Server Persistence**: Authenticated users' planners are stored in MySQL with:
- UUID as primary key (client-generated allowed)
- User ownership enforcement on all operations
- Soft delete (deleted_at column, never auto-deleted)
- Optimistic locking via syncVersion field

**Real-Time Notifications via SSE**:
- Server maintains per-user SSE connections
- When a planner is saved/deleted, notify all other devices of that user
- Device that made the change should NOT receive notification (filter by X-Device-Id header)
- Auto-reconnect with exponential backoff (1s, 2s, 4s, 8s max)

**Conflict Resolution**:
- Use syncVersion for optimistic locking
- On version mismatch: auto-accept server version, show toast notification
- No conflict dialog UI needed

**Migration Flow**:
- On first login, detect local IndexedDB planners
- Check if import would exceed 100 planner limit
- If would exceed: reject entire import with error message
- If within limit: bulk import to server, then clear IndexedDB

### Constraints & Limits

| Limit | Value |
|-------|-------|
| Max planners per user | 100 |
| Max planner size | 50KB |
| Max note size | 1KB (~1000 characters) |
| Rate limiting | 10 requests/minute per user |
| SSE heartbeat | 10 seconds |
| SSE connection timeout | 1 hour |

### API Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/planners` | Create planner |
| GET | `/api/planners` | List user's planners |
| GET | `/api/planners/{id}` | Get single planner |
| PUT | `/api/planners/{id}` | Update (with syncVersion) |
| DELETE | `/api/planners/{id}` | Soft delete |
| POST | `/api/planners/import` | Bulk import (max 50 at once) |
| GET | `/api/planners/events` | SSE subscription |

### Error Responses

| HTTP | Code | When |
|------|------|------|
| 400 | PLANNER_TOO_LARGE | Content exceeds 50KB |
| 400 | NOTE_TOO_LARGE | Note exceeds 1KB |
| 404 | PLANNER_NOT_FOUND | Planner doesn't exist or not owned |
| 409 | CONCURRENT_MODIFICATION | syncVersion mismatch |
| 409 | PLANNER_LIMIT_EXCEEDED | Would exceed 100 planners |
| 429 | RATE_LIMITED | Exceeded 10 req/min |

### Multi-Device Behavior

- On page load (authenticated): Fetch planner from server
- On save: Optimistic local update, then sync to server
- On SSE notification: Invalidate TanStack Query cache, show toast
- On 409 conflict: Auto-fetch server version, replace local, show toast

## Research

### Backend Patterns to Study
- `backend/src/main/java/org/danteplanner/backend/entity/User.java` - JPA entity pattern with timestamps
- `backend/src/main/java/org/danteplanner/backend/service/UserService.java` - Service layer pattern
- `backend/src/main/java/org/danteplanner/backend/controller/AuthController.java` - Controller pattern, user ID extraction
- `backend/src/main/java/org/danteplanner/backend/repository/UserRepository.java` - Repository pattern
- Spring Boot SSE with SseEmitter - connection lifecycle, heartbeat, cleanup

### Frontend Patterns to Study
- `frontend/src/hooks/usePlannerStorage.ts` - Interface to mirror for server storage
- `frontend/src/hooks/usePlannerAutosave.ts` - Auto-save pattern with debounce
- `frontend/src/schemas/PlannerSchemas.ts` - Validation schemas, serialization helpers
- `frontend/src/types/PlannerTypes.ts` - Type definitions
- `frontend/src/lib/api.ts` - API client pattern
- EventSource API for SSE subscription

### Technical Investigation
- Rate limiting in Spring Boot (bucket4j or filter-based)
- MySQL JSON column with JPA/Hibernate
- SSE emitter thread safety (ConcurrentHashMap + CopyOnWriteArrayList)
- Flyway migration for planners table

## Scope

### Files to Read for Context
- `frontend/src/hooks/usePlannerStorage.ts` - Current storage implementation
- `frontend/src/hooks/usePlannerAutosave.ts` - Auto-save logic
- `frontend/src/routes/PlannerMDNewPage.tsx` - Planner page structure
- `frontend/src/schemas/PlannerSchemas.ts` - Validation and serialization
- `frontend/src/types/PlannerTypes.ts` - Type definitions
- `frontend/src/lib/api.ts` - API client
- `backend/src/main/java/org/danteplanner/backend/entity/User.java` - Entity pattern
- `backend/src/main/java/org/danteplanner/backend/controller/AuthController.java` - Controller pattern
- `backend/src/main/java/org/danteplanner/backend/config/SecurityConfig.java` - Security setup

## Target Code Area

### Backend - New Files

**Database Migration:**
- `backend/src/main/resources/db/migration/V002__create_planners_table.sql`

**Entity & Repository:**
- `backend/src/main/java/org/danteplanner/backend/entity/MDCategory.java` (enum)
- `backend/src/main/java/org/danteplanner/backend/entity/Planner.java`
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerRepository.java`

**Exceptions:**
- `backend/src/main/java/org/danteplanner/backend/exception/PlannerNotFoundException.java`
- `backend/src/main/java/org/danteplanner/backend/exception/PlannerConflictException.java`
- `backend/src/main/java/org/danteplanner/backend/exception/PlannerLimitExceededException.java`
- `backend/src/main/java/org/danteplanner/backend/exception/PlannerValidationException.java`
- `backend/src/main/java/org/danteplanner/backend/exception/GlobalExceptionHandler.java`

**DTOs:**
- `backend/src/main/java/org/danteplanner/backend/dto/planner/CreatePlannerRequest.java`
- `backend/src/main/java/org/danteplanner/backend/dto/planner/UpdatePlannerRequest.java`
- `backend/src/main/java/org/danteplanner/backend/dto/planner/PlannerResponse.java`
- `backend/src/main/java/org/danteplanner/backend/dto/planner/PlannerSummaryResponse.java`
- `backend/src/main/java/org/danteplanner/backend/dto/planner/ImportPlannersRequest.java`
- `backend/src/main/java/org/danteplanner/backend/dto/planner/ImportPlannersResponse.java`

**Services & Controller:**
- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java`
- `backend/src/main/java/org/danteplanner/backend/service/PlannerSseService.java`
- `backend/src/main/java/org/danteplanner/backend/controller/PlannerController.java`

### Frontend - New Files
- `frontend/src/lib/plannerApi.ts` - API functions for planner endpoints
- `frontend/src/hooks/usePlannerSync.ts` - Server storage + SSE subscription
- `frontend/src/hooks/usePlannerStorageAdapter.ts` - Routes to local/server based on auth
- `frontend/src/hooks/usePlannerMigration.ts` - Local → server migration on first login

### Frontend - Modified Files
- `frontend/src/types/PlannerTypes.ts` - Add syncVersion to PlannerMetadata
- `frontend/src/schemas/PlannerSchemas.ts` - Add syncVersion to schema
- `frontend/src/lib/api.ts` - Add put, delete methods; add SSE connection method
- `frontend/src/hooks/usePlannerAutosave.ts` - Use adapter instead of direct storage

## Database Schema

```sql
CREATE TABLE planners (
    id CHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL DEFAULT 'Untitled',
    category ENUM('5F', '10F', '15F') NOT NULL,
    status ENUM('draft', 'saved') NOT NULL DEFAULT 'draft',
    content JSON NOT NULL,
    version INT NOT NULL DEFAULT 1,
    sync_version BIGINT NOT NULL DEFAULT 1,
    device_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    saved_at TIMESTAMP NULL,
    deleted_at TIMESTAMP NULL,

    CONSTRAINT fk_planner_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_modified (user_id, last_modified_at DESC),
    INDEX idx_user_status (user_id, status),
    INDEX idx_user_deleted (user_id, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## Implementation Phases

### Phase 1: Core Backend (Entity & Repository)
1. Flyway migration for planners table
2. MDCategory enum
3. Planner entity with all fields
4. PlannerRepository with custom queries
5. Exception classes
6. GlobalExceptionHandler

### Phase 2: CRUD Service & Controller
1. All DTO classes
2. PlannerService (create, read, update, delete, import)
3. PlannerController (6 REST endpoints)
4. Size validation (50KB planner, 1KB note)
5. User limit validation (100 planners)
6. Rate limiting filter

### Phase 3: SSE Notifications
1. PlannerSseService (emitter management)
2. SSE endpoint in controller
3. Device filtering (X-Device-Id header)
4. Heartbeat (10s interval)
5. Cleanup on disconnect/timeout

### Phase 4: Frontend Integration
1. Update types/schemas for syncVersion
2. Add put/delete/SSE to api.ts
3. Create plannerApi.ts
4. Create usePlannerSync hook
5. Create usePlannerStorageAdapter hook
6. Update usePlannerAutosave to use adapter

### Phase 5: Migration & Polish
1. Create usePlannerMigration hook
2. First-login detection and migration
3. Toast notifications for conflicts and updates
4. SSE auto-reconnect with backoff
5. Error handling for all edge cases

## Testing Guidelines

### Manual UI Testing

**Basic CRUD (Authenticated):**
1. Log in with Google OAuth
2. Navigate to planner page
3. Create a new planner
4. Make some edits (add identities, gifts)
5. Wait 2 seconds for auto-save
6. Refresh the page
7. Verify all edits are preserved
8. Open DevTools Network tab
9. Verify requests go to `/api/planners/{id}`

**Multi-Device Sync:**
1. Log in on Device A (or browser Tab A)
2. Log in on Device B (or incognito Tab B)
3. Open the same planner on both
4. Make an edit on Device A
5. Verify Device B shows toast notification within 2 seconds
6. Verify Device B data updates after clicking toast or refreshing

**SSE Connection:**
1. Log in and open planner page
2. Open DevTools Network tab, filter by "events"
3. Verify SSE connection to `/api/planners/events`
4. Verify heartbeat comments every 10 seconds
5. Kill network briefly, then restore
6. Verify SSE reconnects automatically

**Migration Flow:**
1. Log out
2. Create 3 planners as guest (uses IndexedDB)
3. Verify planners appear in planner list
4. Log in with Google OAuth
5. Verify migration prompt or automatic migration
6. Verify all 3 planners now on server
7. Clear IndexedDB manually (DevTools > Application)
8. Refresh page
9. Verify planners still load (from server)

**Guest User (No Migration):**
1. Log out
2. Create a planner
3. Verify it saves to IndexedDB (DevTools > Application > IndexedDB)
4. Verify no requests to `/api/planners`

### Automated Functional Verification

**Backend:**
- [ ] POST /api/planners: Creates planner with generated UUID
- [ ] GET /api/planners: Returns only current user's non-deleted planners
- [ ] GET /api/planners/{id}: Returns 404 for non-existent or other user's planner
- [ ] PUT /api/planners/{id}: Increments syncVersion on success
- [ ] PUT /api/planners/{id}: Returns 409 with current version on conflict
- [ ] DELETE /api/planners/{id}: Sets deleted_at, doesn't hard delete
- [ ] POST /api/planners/import: Rejects if would exceed 100 limit
- [ ] All endpoints return 401 without valid JWT cookie
- [ ] All endpoints return 429 after 10 requests in 1 minute

**Frontend:**
- [ ] Adapter routes to IndexedDB when not authenticated
- [ ] Adapter routes to server API when authenticated
- [ ] SSE connection established on authenticated page load
- [ ] SSE reconnects with backoff on disconnect
- [ ] TanStack Query cache invalidated on SSE notification
- [ ] Toast shown on SSE update notification
- [ ] Toast shown on conflict resolution

### Edge Cases

**Size Limits:**
- [ ] Planner exceeding 50KB: Save fails with clear error message
- [ ] Note exceeding 1KB: Save fails with clear error message

**User Limits:**
- [ ] 100th planner: Creates successfully
- [ ] 101st planner: Fails with PLANNER_LIMIT_EXCEEDED
- [ ] Import that would exceed 100: Entire import rejected

**Concurrency:**
- [ ] Two tabs save simultaneously: One succeeds, one gets 409
- [ ] 409 response: Auto-fetches server version, updates local
- [ ] User sees toast explaining what happened

**Network Issues:**
- [ ] Network offline during save: Error shown, retry possible
- [ ] SSE disconnect: Auto-reconnect with backoff
- [ ] Server down: Graceful error, no crash

**Authentication:**
- [ ] Token expires during SSE: Reconnect with new token
- [ ] Token expires during save: 401 triggers re-auth flow
- [ ] Logout: SSE connection closed, switch to IndexedDB mode

**Migration:**
- [ ] No local planners: No migration needed
- [ ] 50 local + 60 server: Migration rejected (would exceed 100)
- [ ] Import fails mid-way: Local planners NOT deleted
- [ ] Import succeeds: Local planners deleted

### Integration Points

- [ ] Auth system: Planner endpoints respect JWT cookie auth
- [ ] TanStack Query: Planner queries use proper cache keys
- [ ] Toast system: Notifications appear for sync events
- [ ] IndexedDB: Guest mode uses existing storage implementation
- [ ] Existing planner page: Works without modification via adapter pattern
