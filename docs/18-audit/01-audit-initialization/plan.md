# Execution Plan: User Moderation System with Ban Enforcement

## Planning Gaps

NONE. All requirements explicit, research complete, patterns documented.

## Execution Overview

Implement comprehensive user moderation with ban enforcement (permanent until cleared), timeout management (temporary, auto-expiring), role-based authorization (ADMIN > MODERATOR > NORMAL), SSE real-time notifications, and immutable audit trail. System uses lazy enforcement (check at action time, not login), allows banned users to view content and export local data (GDPR), and maintains accountability through complete audit logging. Implementation: 6 phases spanning database migrations, backend services, API layer, frontend integration, and comprehensive testing.

## Dependency Analysis (Senior Thinking)

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| User.java | High | None | UserRepository, ModerationService, PlannerService, CommentService, AuthController |
| PlannerService.java | High | UserRepository | PlannerController (all CRUD), SseService (sync events) |
| CommentService.java | High | UserRepository | CommentController (all CRUD), NotificationService |
| SseService.java | High | UserSettingsService | All controllers needing SSE, frontend useSseConnection |
| GlobalExceptionHandler.java | High | All exception classes | All controllers (error responses) |
| AuthController.java | High | UserRepository | Frontend useAuthQuery (every page load) |
| useAuthQuery.ts | High | ApiClient | Header, Settings, all auth-dependent components |
| useSseConnection.ts | Medium | EventSource API | App-level SSE lifecycle |
| usePlannerSave.ts | Medium | useAuthQuery, ApiClient | Planner editor pages |

### Ripple Effect Map

- User.java changes (add bannedAt, bannedBy, isBanned()) → UserService, ModerationService, AuthController see new fields
- PlannerService.java changes (add checkUserNotBanned()) → PlannerController unchanged, frontend gets 403 responses
- CommentService.java changes (add enforcement) → CommentController unchanged, frontend gets 403 responses
- SseService.java changes (add notifyAccountSuspended()) → ModerationService calls it, frontend useSseConnection handles event
- GlobalExceptionHandler.java changes (add UserBannedException handler) → All controllers return consistent 403
- AuthController /me changes (add ban/timeout fields) → useAuthQuery must handle new optional fields
- useAuthQuery.ts schema changes → All components using hook see new data
- useSseConnection.ts adds account_suspended handler → App.tsx triggers profile refresh and banner

### High-Risk Modifications

**PlannerService.java**: All planner CRUD depends on this. Adding ban enforcement must NOT break existing functionality. Mitigation: Add enforcement AFTER timeout check, separate exception, test all endpoints.

**CommentService.java**: Currently MISSING timeout enforcement (security gap). Adding both timeout AND ban checks fixes gap. Mitigation: Follow PlannerService pattern exactly.

**GlobalExceptionHandler.java**: All errors route through here. New handler must follow pattern. Mitigation: Copy UserTimedOutException handler structure.

**useAuthQuery.ts**: User profile used by every page. Schema change must be backward compatible. Mitigation: Optional chaining (user?.isBanned), @JsonInclude(NON_NULL) on backend.

## Execution Order

### Phase 1: Database Layer (Foundation)

1. **V022__add_moderation_tables.sql**: Create ban columns and audit table
   - Depends on: none
   - Enables: F1 (Ban User), F2 (Audit Trail)
   - ADD banned_at, banned_by columns to users (nullable)
   - CREATE moderation_actions table (id, action_type, actor_id, target_id, reason, duration_minutes, created_at)
   - ADD index on moderation_actions(target_id, created_at DESC)
   - ADD index on users(banned_at)

### Phase 2: Backend Core (Entities, Exceptions, Repositories)

2. **User.java**: Add ban fields and isBanned() method
   - Depends on: Step 1
   - Enables: F1
   - ADD bannedAt (Instant, nullable), bannedBy (Long, nullable)
   - ADD isBanned() method (pattern from isTimedOut())

3. **ModerationAction.java**: New audit trail entity
   - Depends on: Step 1
   - Enables: F2
   - PATTERN: Copy User.java entity structure
   - ADD ActionType enum (BAN, UNBAN, TIMEOUT, CLEAR_TIMEOUT, PROMOTE, DEMOTE)

4. **UserBannedException.java**: New exception class
   - Depends on: none
   - Enables: F3
   - PATTERN: Copy UserTimedOutException.java exactly

5. **ModerationActionRepository.java**: New repository
   - Depends on: Step 3
   - Enables: F2
   - PATTERN: Copy UserRepository.java interface
   - ADD findFirstByTargetIdAndActionTypeOrderByCreatedAtDesc()

### Phase 3: Backend Services (Business Logic, Enforcement)

6. **ModerationService.java**: Add ban methods and audit logging
   - Depends on: Steps 2-5
   - Enables: F1, F2, F4
   - ADD banUser(actorId, targetId, reason) (check role hierarchy)
   - ADD unbanUser(actorId, targetId)
   - ADD logModerationAction() helper
   - CALL SseService.notifyAccountSuspended() after ban/timeout

7. **SseService.java**: Add account suspension notification
   - Depends on: none
   - Enables: F4
   - ADD notifyAccountSuspended(userId, reason, suspensionType)
   - PATTERN: Copy sendToUser() from existing notify* methods

8. **PlannerService.java**: Add ban enforcement
   - Depends on: Step 4
   - Enables: F3
   - ADD checkUserNotBanned(User user)
   - PATTERN: Copy checkUserNotTimedOut()
   - Call in: upsertPlanner(), togglePublish(), castVote()

9. **CommentService.java**: Add timeout AND ban enforcement (BUG FIX)
   - Depends on: Step 4
   - Enables: F3, F5
   - ADD checkUserNotTimedOut() (MISSING - security gap)
   - ADD checkUserNotBanned()
   - Call both in: createComment(), castVote()

10. **GlobalExceptionHandler.java**: Add UserBannedException handler
    - Depends on: Step 4
    - Enables: F3
    - PATTERN: Copy handleUserTimedOut()
    - Return 403 with "USER_BANNED" code

### Phase 4: Backend API (Controllers, DTOs)

11. **AuthController.java**: Extend /me response with ban/timeout details
    - Depends on: Steps 2, 5
    - Enables: F6
    - ADD isBanned, isTimedOut computed fields
    - Query latest reason from moderation_actions

12. **UserProfileResponse.java**: Add ban/timeout fields
    - Depends on: none
    - Enables: F6
    - ADD isBanned, bannedAt, banReason (all @JsonInclude(NON_NULL))
    - ADD isTimedOut, timeoutUntil, timeoutReason

13. **BanRequest.java**: New DTO
    - Depends on: none
    - Enables: F1
    - PATTERN: Copy TimeoutRequest.java
    - ADD targetId, reason fields

14. **ModerationController.java**: Add ban/unban/promote endpoints
    - Depends on: Steps 6, 13
    - Enables: F1, F7
    - ADD POST /api/moderation/user/{id}/ban
    - ADD DELETE /api/moderation/user/{id}/ban
    - ADD POST /api/moderation/user/{id}/promote
    - All require @PreAuthorize("hasRole('ADMIN')")

### Phase 5: Frontend Integration (Hooks, Components, UI Blocking)

15. **AuthSchemas.ts**: Extend User schema
    - Depends on: Step 12
    - Enables: F6
    - ADD isBanned, bannedAt, banReason (all optional)
    - ADD isTimedOut, timeoutUntil, timeoutReason

16. **useAuthQuery.ts**: Handle new profile fields
    - Depends on: Step 15
    - Enables: F6
    - NO CODE CHANGES (schema validation handles it)

17. **useSseConnection.ts**: Add account_suspended event handler
    - Depends on: Step 7
    - Enables: F4, F8
    - ADD event listener for "account_suspended"
    - On event: invalidateQueries(['auth', 'me']), show toast

18. **BanStatusBanner.tsx**: New suspension notification component
    - Depends on: Step 17
    - Enables: F9
    - PATTERN: Copy NotificationItem.tsx dismissible structure
    - Display reason, expiry (if timeout), contact email

19. **usePlannerSave.ts**: Disable sync button for restricted users
    - Depends on: Step 16
    - Enables: F10
    - CHECK: if (user?.isBanned || user?.isTimedOut) → disable sync
    - KEEP auto-save to IndexedDB enabled

20. **common.json (EN/KR/JP)**: Add ban/timeout i18n strings
    - Depends on: none
    - Enables: F9
    - ADD planner.save.banned, planner.save.timedOut
    - ADD moderation.banReason, moderation.contactSupport

### Phase 6: Testing (Unit + Integration)

21. **Write unit tests for ModerationService**
    - Depends on: Steps 2-6
    - Verify: Role hierarchy, audit logging, SSE calls

22. **Write unit tests for PlannerService enforcement**
    - Depends on: Step 8
    - Verify: checkUserNotBanned() throws/succeeds correctly

23. **Write unit tests for CommentService enforcement**
    - Depends on: Step 9
    - Verify: Both timeout AND ban checks run

24. **Write integration test for ban enforcement flow**
    - Depends on: Steps 1-14
    - Test: Create planner → ban → attempt save → verify 403

25. **Write integration test for SSE notification flow**
    - Depends on: Steps 7, 17
    - Test: Ban user → verify account_suspended event sent

26. **Write frontend tests for UI blocking**
    - Depends on: Steps 18-19
    - Test: Banner renders, sync button disabled, tooltip shows

## Verification Checkpoints

**After Phase 1 (Database):**
- Migration V022 runs without errors
- users.banned_at, users.banned_by columns exist
- moderation_actions table exists with correct schema

**After Phase 2 (Backend Core):**
- User.isBanned() returns false for existing users
- ModerationAction entity persists to database
- UserBannedException can be thrown and caught

**After Phase 3 (Backend Services):**
- ModerationService.banUser() updates user and logs to audit
- SseService.notifyAccountSuspended() sends event
- PlannerService throws UserBannedException for banned users
- CommentService throws both UserTimedOutException AND UserBannedException

**After Phase 4 (Backend API):**
- GET /api/auth/me returns isBanned, banReason fields
- POST /api/moderation/user/{id}/ban returns 200
- PUT /api/planner/{id} returns 403 for banned user

**After Phase 5 (Frontend Integration):**
- useAuthQuery() handles new fields without errors
- BanStatusBanner appears when user banned
- Sync button disabled with tooltip when restricted
- Auto-save to IndexedDB still works

**After Phase 6 (Testing):**
- All unit tests pass (21 tests minimum)
- All integration tests pass
- Manual testing checklist complete

## Risk Mitigation

| Risk | Step Affected | Mitigation |
|------|---------------|------------|
| Concurrent moderation actions | Step 6 | Acceptable - audit logs both, last write wins |
| Ban during active SSE (network issue) | Step 7 | Acceptable - lazy enforcement, user discovers on next action |
| Timeout expires during save | Step 8 | Check at START of method, not during DB write |
| Admin bans themselves | Step 6 | Allow for testing, document needs another admin to unban |
| N+1 query loading ban reason | Step 11 | Use JOIN FETCH or @EntityGraph |
| Enforcement adds latency | Steps 8-9 | User lookup cached by Hibernate, <5ms impact |
| Schema change breaks old frontend | Step 12 | Use @JsonInclude(NON_NULL), old frontend ignores unknown fields |
| SSE event crashes app | Step 17 | Wrap handler in try-catch, ignore unknown events |

## Pre-Implementation Validation Gate

**BEFORE Step 1, verify research completed:**

| Validation Category | Check | Blocker if Missing |
|---------------------|-------|-------------------|
| Reference Completeness | Read User.java, ModerationService.java, PlannerService.java, SseService.java, UserTimedOutException.java? | YES |
| Pattern Alignment | Timeout pattern documented for ban pattern? | YES |
| Contract Alignment | UserProfileResponse schema matches User entity? | YES |
| Dependency Resolution | ModerationActionRepository available? | YES |
| Structure Documentation | Exception handler pattern documented? | YES |

## Dependency Verification Steps

**After Step 2 (User.java):**
- Test: UserService.findById() returns user with ban fields
- Test: AuthController /me compiles (no type errors)

**After Step 8 (PlannerService):**
- Test: PlannerController.upsertPlanner() compiles
- Test: Existing planner endpoints return 200 for non-banned

**After Step 11 (AuthController /me):**
- Test: Frontend useAuthQuery schema validation passes
- Test: All components using useAuthQuery() render

**After Step 17 (useSseConnection):**
- Test: App.tsx SSE lifecycle still connects
- Test: Existing SSE events still work

## Rollback Strategy

**If Step 8 fails (PlannerService):**
- Remove checkUserNotBanned() calls
- Keep exception class (no harm)
- Safe stopping point: Ban disabled, existing functionality intact

**If Step 11 fails (/me breaks frontend):**
- Rollback: Remove new fields from UserProfileResponse
- Keep backend enforcement (still works, no frontend display)

**If Step 17 fails (SSE crashes):**
- Wrap handler in try-catch (defensive)
- Log error, ignore unknown events

**General Strategy:**
- Each phase independent (database → backend → frontend)
- Can stop after Phase 3 (backend enforcement only)
- Frontend can remain on old version (gracefully ignores new fields)
- Database migration safe (nullable columns, no data loss)
