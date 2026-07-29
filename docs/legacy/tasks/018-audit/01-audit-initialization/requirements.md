# Task: User Moderation System with Ban Enforcement and Audit Trail

## Description

Implement a comprehensive user moderation system that allows admins and moderators to manage user restrictions. The system should support:

**User Restrictions:**
- **Timeout**: Temporary suspension with manual duration selection (30min, 1hr, 3hr, 6hr, 1 day, 7 days, 30 days) - auto-expires when time reaches `timeoutUntil`
- **Ban**: Permanent suspension until admin manually clears it - stored as `bannedAt` timestamp
- Both restrictions can coexist (user can be timed out AND banned simultaneously)

**Authorization Hierarchy:**
- Admins can ban/timeout anyone including moderators (cannot ban other admins)
- Moderators can only timeout normal users (cannot ban, cannot timeout other mods)
- No user can promote/demote themselves
- Role changes (promote/demote) are admin-only

**Enforcement Points (Lazy Enforcement):**
- Blocked actions for restricted users:
  - Publishing new planners (create/update/publish operations)
  - Submitting new comments on published planners
  - Voting on planners or comments
  - Manual sync/save operations in planner editor
- Allowed actions for restricted users:
  - Viewing all public content (browse, read, search)
  - Exporting local planners (data portability - GDPR compliance)
  - Importing planners into local storage
  - Auto-save to IndexedDB (privacy-first - local only)
  - Maintaining SSE connection (read-only notifications)

**User Experience Flow:**
1. User gets banned/timed out by admin/moderator
2. SSE sends `account_suspended` event to all user's open connections (browser tabs, devices)
3. Frontend receives event, shows persistent dismissible banner with reason and contact email
4. Frontend calls `/api/auth/me` to refresh user profile (includes `isBanned`, `isTimedOut`, reasons)
5. UI blocks sync button and publish/comment actions with tooltip explaining restriction
6. User attempts blocked action → gets 403 response with clear error message
7. When restriction lifted (timeout expires or admin unbans), user can resume normal activity

**Audit Trail:**
- All moderation actions logged to `moderation_actions` table with:
  - Action type (BAN, UNBAN, TIMEOUT, CLEAR_TIMEOUT, PROMOTE, DEMOTE)
  - Actor ID (who performed the action)
  - Target ID (who was affected)
  - Reason (optional, up to 500 characters)
  - Duration in minutes (for timeouts)
  - Timestamp
- Audit trail visible to admins and moderators (filtered view for mods - only their own actions)
- Used for appeals, accountability, and investigating mod abuse

**Content Policy:**
- Existing published planners remain visible after ban (content not auto-unpublished)
- Existing comments remain visible after ban (no deletion)
- Votes from banned users remain counted (past participation unaffected)
- Banned user's username still displays normally (not changed to "Banned User")
- Content moderation is separate from user restriction (mods can hide individual planners via existing `hiddenFromRecommended` system)

**Contact Support:**
- Ban/timeout messages include contact email: support@example.com (or configured email)
- Appeals handled externally (email) in initial implementation
- ToS specifies that banned users lose access to server-dependent features including sync, publishing, commenting, voting

**Rate Limiting:**
- Banned users subject to same rate limits as normal users (no stricter limits)
- Prevents abuse via public endpoints, but doesn't discriminate against restricted accounts

**Multi-Device Coordination:**
- SSE `account_suspended` event broadcasts to all user's connections
- Connections remain open (read-only, no disconnect)
- Each device independently blocks UI based on user profile status
- No forced logout or token invalidation (lazy enforcement)

## Research

**Existing Patterns to Follow:**
- Timeout implementation: `User.isTimedOut()` checks `timeoutUntil` against current time
- SSE event broadcasting: `SseService.notifyAccountSuspended()` pattern from be-async skill
- Moderation authorization: `ModerationService.timeoutUser()` has role hierarchy checks
- Exception handling: `GlobalExceptionHandler` for consistent 403 responses
- Audit logging: Pattern exists in `PlannerService` for vote tracking

**Libraries/APIs:**
- Spring Security `@PreAuthorize` for role-based access control
- SSE (Server-Sent Events) for real-time notifications via `SseEmitter`
- JPA auditing for automatic timestamps in `moderation_actions` table
- `@JsonInclude(JsonInclude.Include.NON_NULL)` for optional fields in DTOs

**Data Structures:**
- `User.timeoutUntil` (Instant) - existing field for timeout expiry
- `User.bannedAt` (Instant) - new field for permanent ban timestamp
- `User.bannedBy` (Long) - new field for ban accountability
- `ModerationAction` entity - new audit trail table

**Design Decisions:**
- Ban vs timeout: Separate fields (not single "restriction" field) for clear semantics
- Lazy enforcement: Check on action, not on session (no token invalidation complexity)
- Full audit trail: Separate table (not just columns) for accountability and appeals
- Frontend detection: `/me` response includes ban/timeout status, reasons, expiry

**Dependencies:**
- `UserRepository.findById()` for user lookups in enforcement checks
- `ModerationActionRepository` for audit trail queries
- `SseService` for broadcasting suspension events
- `PlannerService.checkUserNotTimedOut()` pattern for enforcement helpers
- `CommentService` needs NEW timeout check (currently missing - bug fix)

## Scope

**Read for Context:**
- `/backend/src/main/java/org/danteplanner/backend/entity/User.java` - existing timeout fields
- `/backend/src/main/java/org/danteplanner/backend/service/ModerationService.java` - timeout logic
- `/backend/src/main/java/org/danteplanner/backend/service/PlannerService.java` - enforcement pattern
- `/backend/src/main/java/org/danteplanner/backend/service/CommentService.java` - needs enforcement
- `/backend/src/main/java/org/danteplanner/backend/service/SseService.java` - event broadcasting
- `/backend/src/main/java/org/danteplanner/backend/controller/AuthController.java` - `/me` endpoint
- `/backend/src/main/java/org/danteplanner/backend/exception/GlobalExceptionHandler.java` - error responses
- `/frontend/src/hooks/useAuthQuery.ts` - user profile fetching
- `/frontend/src/hooks/useSseConnection.ts` - SSE event handling
- `/frontend/src/hooks/usePlannerSave.ts` - save blocking logic
- `docs/architecture-map.md` - system architecture overview

## Target Code Area

**Backend - Database:**
- `/backend/src/main/resources/db/migration/V022__add_moderation_tables.sql` (NEW) - ban fields + audit table

**Backend - Entities:**
- `/backend/src/main/java/org/danteplanner/backend/entity/User.java` (MODIFY) - add `bannedAt`, `bannedBy`, `isBanned()`
- `/backend/src/main/java/org/danteplanner/backend/entity/ModerationAction.java` (NEW) - audit trail entity

**Backend - Repositories:**
- `/backend/src/main/java/org/danteplanner/backend/repository/ModerationActionRepository.java` (NEW) - audit queries

**Backend - Exceptions:**
- `/backend/src/main/java/org/danteplanner/backend/exception/UserBannedException.java` (NEW)
- `/backend/src/main/java/org/danteplanner/backend/exception/GlobalExceptionHandler.java` (MODIFY) - handle `UserBannedException`

**Backend - Services:**
- `/backend/src/main/java/org/danteplanner/backend/service/ModerationService.java` (MODIFY) - add `banUser()`, `unbanUser()`, audit logging, SSE notifications
- `/backend/src/main/java/org/danteplanner/backend/service/PlannerService.java` (MODIFY) - add `checkUserNotBanned()` enforcement
- `/backend/src/main/java/org/danteplanner/backend/service/CommentService.java` (MODIFY) - add timeout + ban checks (fix missing enforcement)
- `/backend/src/main/java/org/danteplanner/backend/service/SseService.java` (MODIFY) - add `notifyAccountSuspended()` method

**Backend - Controllers:**
- `/backend/src/main/java/org/danteplanner/backend/controller/AuthController.java` (MODIFY) - update `/me` response with ban/timeout details
- `/backend/src/main/java/org/danteplanner/backend/controller/ModerationController.java` (MODIFY) - add ban/unban/promote endpoints

**Backend - DTOs:**
- `/backend/src/main/java/org/danteplanner/backend/dto/user/UserProfileResponse.java` (MODIFY) - add ban/timeout fields
- `/backend/src/main/java/org/danteplanner/backend/dto/moderation/TimeoutRequest.java` (MODIFY) - validate preset durations
- `/backend/src/main/java/org/danteplanner/backend/dto/moderation/BanRequest.java` (NEW)
- `/backend/src/main/java/org/danteplanner/backend/dto/moderation/ModerationResponse.java` (existing)

**Frontend - Hooks:**
- `/frontend/src/hooks/useAuthQuery.ts` (MODIFY) - handle new user profile fields
- `/frontend/src/hooks/useSseConnection.ts` (MODIFY) - add `account_suspended` event handler
- `/frontend/src/hooks/usePlannerSave.ts` (MODIFY) - block sync button for restricted users
- `/frontend/src/hooks/useUserModerationList.ts` (NEW) - fetch user list for mod dashboard
- `/frontend/src/hooks/useUserModerationMutations.ts` (NEW) - ban/unban/promote mutations

**Frontend - Components:**
- `/frontend/src/routes/moderator/UserManagementPage.tsx` (NEW) - admin dashboard for user list
- `/frontend/src/components/moderator/UserModerationTable.tsx` (NEW) - user list with action buttons
- `/frontend/src/components/moderator/BanDialog.tsx` (NEW) - ban/timeout reason dialogs

**Frontend - i18n:**
- `/frontend/src/static/i18n/EN/common.json` (MODIFY) - add ban/timeout messages
- `/frontend/src/static/i18n/KR/common.json` (MODIFY) - add ban/timeout messages
- `/frontend/src/static/i18n/JP/common.json` (MODIFY) - add ban/timeout messages

## System Context (Senior Thinking)

**Feature Domain:** User Management + Authorization + Moderation

**Core Files in This Domain (from architecture-map.md):**
- User Management: `service/UserService.java`, `controller/UserController.java`, `entity/User.java`
- Authorization: `config/SecurityConfig.java`, `security/JwtAuthenticationFilter.java`
- Moderation: `service/ModerationService.java`, `controller/AdminModerationController.java` (exists for planner hiding)
- SSE: `controller/SseController.java`, `service/SseService.java` (user-centric SSE endpoint)
- Exception Handling: `exception/GlobalExceptionHandler.java`

**Cross-Cutting Concerns Touched:**
- **Authentication**: `/me` endpoint returns user restrictions (read by all pages)
- **Authorization**: Role hierarchy (ADMIN > MODERATOR > NORMAL) enforced in service layer
- **Real-time Sync**: SSE broadcasts suspension events to all user connections
- **Validation**: Jakarta Validation for timeout duration presets (30, 60, 180, 360, 1440, 10080, 43200 minutes)
- **Exception Handling**: 403 responses for banned/timed out users attempting restricted actions
- **Audit Logging**: New `moderation_actions` table for accountability trail

**Data Flow Pattern:**
```
Frontend                      Backend                      Database
    │                            │                            │
    ├─[1] GET /auth/me─────────>│                            │
    │                            ├─[2] Query user─────────────>│
    │                            │<─[3] User with ban/timeout─┤
    │                            ├─[4] Query latest reason────>│
    │<─[5] UserProfileResponse───┤                            │
    │    (includes isBanned,     │                            │
    │     isTimedOut, reasons)   │                            │
    │                            │                            │
    ├─[6] PUT /planner/{id}────>│ (blocked if restricted)    │
    │                            ├─[7] checkUserNotBanned()   │
    │                            ├─[8] User.isBanned()────────>│
    │<─[9] 403 Forbidden─────────┤                            │
    │                            │                            │
    │                            │  [Admin bans user]         │
    │                            ├─[10] Update user.bannedAt─>│
    │                            ├─[11] Log to audit table───>│
    │                            ├─[12] Broadcast SSE event   │
    │<─[13] SSE: account_suspended──┤                         │
```

## Impact Analysis

**Files Being Modified:**

High-Impact Files:
- `service/PlannerService.java` - All planner CRUD operations depend on this
- `service/CommentService.java` - All comment CRUD operations depend on this
- `service/SseService.java` - All real-time sync and notifications use this
- `controller/AuthController.java` - `/me` endpoint called on every page load
- `exception/GlobalExceptionHandler.java` - All error responses route through this
- `hooks/useAuthQuery.ts` - User profile used by Header, Settings, all auth-dependent components

Medium-Impact Files:
- `service/ModerationService.java` - Used only by admin/mod features
- `controller/ModerationController.java` - Isolated to mod dashboard
- `hooks/usePlannerSave.ts` - Used only by planner editor pages
- `hooks/useSseConnection.ts` - Used by app-level SSE lifecycle

Low-Impact Files (New):
- `entity/ModerationAction.java` - New entity, no existing dependencies
- `routes/moderator/UserManagementPage.tsx` - New page, isolated

**What Depends on These Files:**

`service/PlannerService.java`:
- `controller/PlannerController.java` - All REST endpoints
- `service/SseService.java` - Broadcasts planner updates
- Frontend `usePlannerSave.ts`, `usePlannerSync.ts` - All save/sync operations

`service/CommentService.java`:
- `controller/CommentController.java` - All comment endpoints
- `service/NotificationService.java` - Comment notifications
- Frontend `useCommentMutations.ts` - All comment CRUD

`controller/AuthController.java` `/me`:
- `hooks/useAuthQuery.ts` - Called by Header, Settings, every auth check
- All protected routes check user authentication status
- SSE connection setup uses auth status

**Potential Ripple Effects:**

1. **Adding fields to UserProfileResponse**: All components using `useAuthQuery()` may need updates to handle new fields (isBanned, isTimedOut, reasons)
2. **Enforcement in PlannerService/CommentService**: Any code calling these services will now throw UserBannedException (need to verify all call sites handle 403)
3. **SSE event changes**: Frontend SSE event handlers must handle new `account_suspended` event type
4. **Database schema**: Adding `banned_at`, `banned_by` columns to users table (safe - defaults to NULL for existing users)

**High-Impact Files to Watch:**
- `lib/api.ts` (frontend) - HTTP error handling must support new 403 error messages
- `components/Header.tsx` - May need to show ban status indicator
- `stores/usePlannerEditorStore.tsx` - Save operations must respect ban status

## Risk Assessment

**Edge Cases Not Yet Defined:**

1. **Concurrent moderation actions**: What if two mods try to timeout same user simultaneously? (Audit table logs both, last write wins on user.timeoutUntil)
2. **Ban during active SSE connection**: If SSE event fails to deliver (network issue), user discovers ban on next action (acceptable - lazy enforcement)
3. **Timeout expires during planner save**: `checkUserNotTimedOut()` checks at action time, so save succeeds if timeout just expired (correct behavior)
4. **Admin bans themselves**: Currently allowed for testing, should this be prevented? (Decision: Allow for now)
5. **Banned user exports planner then unbanned**: Export/import is local-only, works as expected (no server involvement)

**Performance Concerns:**

1. **Audit table growth**: `moderation_actions` grows unbounded, needs retention policy (recommend: archive after 1 year)
2. **Reason lookup on /me**: Joins to `moderation_actions` to fetch latest reason - indexed by `target_id, created_at DESC` for performance
3. **SSE broadcast to many connections**: If user has 10 tabs open, sends 10 events - acceptable overhead
4. **Enforcement checks on every save/comment**: Adds one user lookup per action - cached by Hibernate, minimal impact

**Backward Compatibility:**

1. **New columns default NULL**: Existing users have `banned_at = NULL`, `banned_by = NULL` - safe migration
2. **UserProfileResponse schema change**: Frontend must handle new optional fields gracefully (use `@JsonInclude(NON_NULL)`)
3. **SSE event handling**: Old frontend versions ignore unknown event types - graceful degradation
4. **Ban check in PlannerService**: Adds new exception type - must update frontend error handling

**Security Considerations:**

1. **Information disclosure**: Ban reason visible to user - may reveal moderation strategy (acceptable per decision)
2. **Mod abuse**: Audit trail tracks all actions by actor_id - can investigate rogue moderators
3. **Ban evasion**: User creates new account - detect via IP/device fingerprinting (out of scope for Phase 1)
4. **Rate limiting bypass**: Banned user shouldn't have stricter limits per decision, but may need monitoring for abuse
5. **GDPR compliance**: Banned users can still export local data (data portability right), can request account deletion (right to be forgotten)

**Operational Semantics Ambiguities:**

1. **Ban reason required or optional?** (Decision: Optional, but recommended for audit trail)
2. **Concurrent timeout + ban**: Both restrictions checked independently, either blocks action (acceptable)
3. **Moderator demotes themselves**: Should this be prevented? (Recommend: No self-demotion)
4. **Empty reason string vs null**: Treat as equivalent (both mean "no reason provided")
5. **Timeout duration validation**: Enforce preset values (30, 60, 180, 360, 1440, 10080, 43200 min) in DTO validation

## Testing Guidelines

### Manual UI Testing

**Phase 1: Ban Enforcement (Backend)**

1. Open MySQL client and connect to database
2. Manually ban a test user: `UPDATE users SET banned_at = NOW(), banned_by = 1 WHERE id = 123;`
3. Use that user's JWT token in Postman/curl to test endpoints
4. Try to save a planner: `PUT /api/planner/md/{id}` with banned user's token
5. Verify response is 403 Forbidden with error code "USER_BANNED"
6. Try to post a comment: `POST /api/planner/{id}/comments` with banned user's token
7. Verify response is 403 Forbidden
8. Try to vote: `POST /api/planner/{id}/vote` with banned user's token
9. Verify response is 403 Forbidden
10. Unban user: `UPDATE users SET banned_at = NULL, banned_by = NULL WHERE id = 123;`
11. Retry save planner
12. Verify response is 200 OK
13. Query audit table: `SELECT * FROM moderation_actions WHERE target_id = 123;`
14. Verify BAN and UNBAN records exist with correct timestamps and actor_id

**Phase 2: Frontend Integration**

15. Login as normal user in browser
16. Open DevTools Network tab
17. Have admin ban the user via API: `POST /api/moderation/user/{id}/ban`
18. Observe SSE event in Network tab: `account_suspended` event received
19. Verify banner appears at top of page with ban reason and contact email
20. Navigate to planner editor page
21. Attempt to click "Sync" button
22. Verify button is disabled with tooltip showing ban message
23. Hover over disabled "Sync" button
24. Verify tooltip displays: "Your account is suspended. Reason: [reason]. Contact support@example.com"
25. Open new browser tab with same session
26. Verify banner also appears in new tab (SSE event broadcasted to all connections)
27. Refresh page and call `/api/auth/me`
28. Verify response includes: `{"isBanned": true, "bannedAt": "...", "banReason": "..."}`
29. Have admin unban the user: `DELETE /api/moderation/user/{id}/ban`
30. Verify banner disappears (after next `/me` poll or SSE event)
31. Verify "Sync" button is now enabled

**Phase 3: Timeout Testing**

32. Timeout user for 30 minutes: `POST /api/moderation/user/{id}/timeout` with `{"durationMinutes": 30, "reason": "Test"}`
33. Verify SSE `account_suspended` event received with type "TIMEOUT"
34. Verify tooltip shows: "You are timed out for 30 minutes. Reason: Test"
35. Wait 31 minutes (or manually update `timeout_until` to past time)
36. Attempt to save planner
37. Verify save succeeds (timeout expired, auto-cleared)

**Phase 4: User Management Page (Future)**

38. Login as admin
39. Navigate to `/moderator/users`
40. Verify paginated user list loads with columns: username, role, timeout status, ban status
41. Click "Ban" button on a normal user
42. Enter ban reason in dialog
43. Submit ban
44. Verify user's ban indicator updates in list
45. Click "Unban" button
46. Verify confirmation dialog appears
47. Confirm unban
48. Verify ban indicator clears
49. Click "Promote" on normal user
50. Verify role changes to MODERATOR
51. Login as moderator account
52. Navigate to `/moderator/users`
53. Verify only normal users have "Ban" button enabled (mods cannot ban other mods)
54. Logout and login as admin
55. Try to ban another admin account
56. Verify error message: "Cannot ban other admins"

### Automated Functional Verification

**Backend Tests:**

- [ ] **Ban enforcement in PlannerService**: Banned user cannot create/update/publish planners (throws UserBannedException)
- [ ] **Ban enforcement in CommentService**: Banned user cannot post comments (throws UserBannedException)
- [ ] **Timeout enforcement**: Timed out user cannot perform restricted actions until timeout expires
- [ ] **Timeout auto-expiry**: User with past timeoutUntil can perform actions (no exception thrown)
- [ ] **Audit trail logging**: All ban/unban/timeout actions logged with actor, target, reason, timestamp
- [ ] **SSE event broadcasting**: `notifyAccountSuspended()` sends event to all user's connections
- [ ] **Authorization hierarchy**: Moderators cannot ban other mods, admins can ban mods but not other admins
- [ ] **Role changes**: Only admins can promote/demote users
- [ ] **Reason retrieval**: `/me` endpoint fetches latest ban/timeout reason from audit table
- [ ] **Idempotent ban**: Banning already-banned user updates timestamp and reason (no error)
- [ ] **Concurrent restrictions**: User can be both timed out AND banned, either restriction blocks actions

**Frontend Tests:**

- [ ] **User profile refresh**: `useAuthQuery` handles new fields (isBanned, isTimedOut, reasons) without errors
- [ ] **Sync button blocking**: Disabled when user is restricted, tooltip shows reason
- [ ] **SSE event handler**: `account_suspended` event triggers banner display and profile refresh
- [ ] **Multi-tab sync**: Ban event received in all open tabs, all show banner
- [ ] **403 error handling**: Banned user attempting save shows clear error message
- [ ] **Export still works**: Banned user can export local planners to JSON
- [ ] **Public viewing works**: Banned user can browse published planners and comments

### Edge Cases

- [ ] **Empty ban reason**: System accepts NULL or empty string, displays generic message
- [ ] **Self-ban by admin**: Admin can ban themselves (for testing), can be unbanned by another admin
- [ ] **Ban during offline**: User goes offline → gets banned → comes back online → SSE event delivers on reconnect
- [ ] **SSE connection drops**: User doesn't receive `account_suspended` event → discovers ban on next action (403)
- [ ] **Expired timeout at exact moment of action**: checkUserNotTimedOut() evaluates at action time, allows if expired
- [ ] **Concurrent timeout by two mods**: Both succeed, last write wins on user.timeoutUntil, both logged in audit table
- [ ] **Ban user with active planner save in progress**: Save fails mid-flight with 403, frontend shows error
- [ ] **Unban during SSE connection**: No proactive notification (user discovers on next /me poll or action)
- [ ] **Invalid timeout duration**: DTO validation rejects non-preset values (e.g., 45 minutes)
- [ ] **Moderator tries to ban admin**: Throws ForbiddenException, returns 403
- [ ] **Banned user votes**: Vote endpoint returns 403 (enforcement in service layer)
- [ ] **Timeout expires during comment post**: Comment succeeds (checked at start of method, not during DB write)
- [ ] **User refreshes /me while banned**: Response correctly includes ban status on every call
- [ ] **Frontend with old version**: Ignores `account_suspended` SSE event (unknown event type), discovers ban on action

### Integration Points

- [ ] **SSE integration**: Ban/timeout triggers `account_suspended` event, all user's connections receive it
- [ ] **Auth integration**: `/me` endpoint returns ban/timeout status, frontend uses for UI blocking
- [ ] **Planner save integration**: `usePlannerSave` respects ban status, blocks sync button
- [ ] **Comment system integration**: `CommentService` enforces timeout AND ban (fix missing timeout check)
- [ ] **Audit trail integration**: All moderation actions logged, visible to admins in future dashboard
- [ ] **Exception handling integration**: `UserBannedException` caught by `GlobalExceptionHandler`, returns 403 with clear message
- [ ] **Settings page integration**: Banned user can still access settings to export local data (GDPR compliance)
- [ ] **Notification system**: No integration needed (notifications are read-only, banned users can still receive)

### Security Verification

- [ ] **Role hierarchy enforcement**: Cannot escalate privileges, cannot ban superiors
- [ ] **Audit trail immutability**: Moderation actions cannot be deleted, only queried
- [ ] **Reason visibility**: Ban reason visible to target user (per decision), not exposed in public API
- [ ] **GDPR compliance**: Banned users can export data, can request account deletion (existing user deletion flow)
- [ ] **Rate limiting**: Banned users subject to same limits (no stricter limits per decision)
- [ ] **Token validity**: JWT remains valid after ban (lazy enforcement, no token blacklist)
- [ ] **SSE connection**: Remains open after ban (read-only, no forced disconnect)
- [ ] **Actor accountability**: All actions tracked with actor_id, can investigate mod abuse

### Performance Verification

- [ ] **Audit table indexing**: `target_id + created_at DESC` index exists, reason queries are fast
- [ ] **Enforcement check overhead**: User lookup per action is acceptable (Hibernate caching)
- [ ] **SSE broadcast**: Broadcasting to multiple connections completes in <100ms
- [ ] **Frontend /me polling**: No increase in /me call frequency (existing 30s polling interval)
- [ ] **Database migration**: Adding columns to users table completes in <1 second (NULL defaults, no data migration)
