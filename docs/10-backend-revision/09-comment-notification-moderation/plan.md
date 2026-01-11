# Execution Plan: Immutable Votes + Notifications + Moderation

## Planning Gaps

**NONE IDENTIFIED** - Research is comprehensive. All patterns, dependencies, and existing code have been documented.

## Execution Overview

This task implements three interconnected systems across 80+ files:

1. **Maximum Immutable Voting** (Phase 1): Remove soft-delete infrastructure, enforce one-time-only voting with composite key existence check, block vote removal/change with 409 Conflict
2. **Notification System** (Phase 2): Real-time user notifications via header dialog for planner milestones, comments, and replies with batch loading and deduplication
3. **Admin Moderation** (Phase 3): Manual curation system allowing admins to hide planners from recommended list without deleting votes (arca.live pattern)
4. **Unpublish Endpoint** (Phase 4): Separate reversible unpublish action from permanent delete

**Breaking Change Alert**: Vote API no longer accepts `voteType: null`. Frontend toggle logic must be completely removed from `PlannerCardContextMenu.tsx` and replaced with immutability checks.

**Critical UX Requirement**: Pre-vote warning modal with "Votes are PERMANENT" message to mitigate accidental vote regret.

## Dependency Analysis

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| **PlannerService.java** | **HIGH** | PlannerRepository, NotificationService (new), PlannerVoteRepository | PlannerController, all planner CRUD operations |
| **PlannerVote.java** | **MEDIUM** | None (entity) | PlannerVoteRepository, UserAccountLifecycleService |
| **PlannerCommentVote.java** | **MEDIUM** | None (entity) | PlannerCommentVoteRepository, UserAccountLifecycleService |
| **Planner.java** | **HIGH** | None (entity) | PlannerRepository, all planner services |
| **PlannerRepository.java** | **HIGH** | Planner entity | PlannerService, CommentService, NotificationService |
| **CommentService.java** | **MEDIUM** | CommentRepository, NotificationService (new) | CommentController |
| **UserAccountLifecycleService.java** | **MEDIUM** | VoteRepositories (modified) | AuthenticationFacade, cleanup scheduler |
| **GlobalExceptionHandler.java** | **HIGH** | VoteAlreadyExistsException (new) | All controllers (global error handling) |
| **Header.tsx** | **HIGH** | NotificationDialog (new), useUnreadCountQuery (new) | GlobalLayout (all pages) |
| **PlannerCardContextMenu.tsx** | **MEDIUM** | usePlannerVote (modified) | PlannerMDGesellschaftPage, PlannerMDPage |
| **usePlannerVote.ts** | **LOW** | VoteRequest type (modified) | PlannerCardContextMenu, any vote buttons |

### Ripple Effect Map

**From Vote Immutability:**
- **PlannerVote.java** changes → **PlannerVoteRepository** queries must remove `deleted_at` filters → **PlannerService.castVote()** logic drastically simplifies
- **PlannerService.castVote()** rejects null → **VoteRequest.java** DTO validation updated → **Frontend usePlannerVote.ts** must validate before sending
- **PlannerCardContextMenu.tsx** toggle removal → **All vote button components** must disable both UP/DOWN after any vote → **UI translation keys** need new "Already voted" messages

**From Notification System:**
- **Planner.java** adds `recommendedNotifiedAt` → **Migration V022** → **PlannerRepository.trySetRecommendedNotified()** atomic query → **PlannerService.castVote()** threshold detection logic
- **NotificationService** created → **PlannerService** and **CommentService** inject dependency → **Notification inbox queries** require indexed `user_id, read, created_at` for performance
- **Header.tsx** adds bell button → **Global layout width** may need adjustment for mobile → **NotificationDialog** z-index must exceed other dialogs

**From Moderation System:**
- **Planner.java** adds `hiddenFromRecommended` → **PlannerRepository.findRecommendedPlanners()** adds WHERE filter → **Public recommended endpoint** excludes hidden planners
- **ModerationService** created → **AdminModerationController** endpoints → **Frontend AdminModerationPage** route (admin-only)

### High-Risk Modifications

| File | Risk | Mitigation |
|------|------|-----------|
| **PlannerService.castVote()** | Breaking existing vote toggle behavior | Add comprehensive integration tests for all vote immutability scenarios (double-click, vote type change attempts, null vote attempts) |
| **Migration V019** | Hard-deleting soft-deleted votes loses data | Acceptable loss per research - soft-deleted votes represent "removed" votes under old toggle system |
| **Header.tsx** | Bell icon breaks mobile header layout | Test on 320px width (iPhone SE), use Flexbox gap instead of margin |
| **PlannerRepository.trySetRecommendedNotified()** | Race condition duplicate notifications | Use atomic UPDATE with WHERE clause (`SET ... WHERE ... AND flag IS NULL`), returns affected row count |
| **GlobalExceptionHandler** | New 409 Conflict response breaks existing error handling | Add dedicated `VoteAlreadyExistsException` handler, test frontend toast error display |

## Execution Order

### Phase 1: Data Layer (Steps 1-4)

1. **Migration V019 - Remove Vote Soft-Delete**
   - Depends on: none
   - Enables: F1 (Immutable votes - backend)
   - Action: Drop `deleted_at`, `updated_at` from vote tables, DELETE soft-deleted rows

2. **Migration V020 - Create Notifications Table**
   - Depends on: none
   - Enables: F2 (Notification backend storage)
   - Action: Create table with indexes, UNIQUE constraint (user_id, content_id, notification_type)

3. **Migration V021 - Add Moderation Fields**
   - Depends on: none
   - Enables: F3 (Admin moderation backend)
   - Action: Add `hidden_from_recommended`, `hidden_by_admin_id`, `hidden_reason`, `hidden_at` to planners

4. **Migration V022 - Add Atomic Flags**
   - Depends on: none
   - Enables: F4 (Threshold notification atomicity)
   - Action: Add `version` (optimistic locking), `recommended_notified_at` (atomic flag) to planners

### Phase 2: Logic Layer - Backend Entities & Services (Steps 5-26)

5. **Backend Entity: NotificationType.java (enum)**
6. **Backend Entity: Notification.java**
7. **Backend Exception: VoteAlreadyExistsException.java**
8. **Backend Entity: Modify PlannerVote.java**
9. **Backend Entity: Modify PlannerCommentVote.java**
10. **Backend Entity: Modify Planner.java**
11. **Backend Repository: Modify PlannerVoteRepository.java**
12. **Backend Repository: Modify PlannerCommentVoteRepository.java**
13. **Backend Repository: NotificationRepository.java**
14. **Backend Repository: Modify PlannerRepository.java**
15. **Backend DTO: Modify VoteRequest.java**
16. **Backend DTO: NotificationResponse.java**
17. **Backend DTO: NotificationInboxResponse.java**
18. **Backend DTO: UnreadCountResponse.java**
19. **Backend DTO: HidePlannerRequest.java**
20. **Backend DTO: ModerationResponse.java**
21. **Backend Service: NotificationService.java**
22. **Backend Service: Modify PlannerService.java**
23. **Backend Service: Modify CommentService.java**
24. **Backend Service: ModerationService.java**
25. **Backend Service: Modify UserAccountLifecycleService.java**
26. **Backend Exception: Modify GlobalExceptionHandler.java**

### Phase 3: Interface Layer - Backend Controllers (Steps 27-29)

27. **Backend Controller: Modify PlannerController.java**
28. **Backend Controller: NotificationController.java**
29. **Backend Controller: AdminModerationController.java**

### Phase 4: Frontend - Types, Schemas, Hooks (Steps 30-38)

30. **Frontend Types: NotificationTypes.ts**
31. **Frontend Schema: NotificationSchemas.ts**
32. **Frontend Hook: useNotificationsQuery.ts**
33. **Frontend Hook: useUnreadCountQuery.ts**
34. **Frontend Hook: useMarkReadMutation.ts**
35. **Frontend Hook: useDeleteNotificationMutation.ts**
36. **Frontend Hook: useHideFromRecommendedMutation.ts**
37. **Frontend Hook: useUnhideFromRecommendedMutation.ts**
38. **Frontend Hook: Modify usePlannerVote.ts**

### Phase 5: Frontend - Components & UI (Steps 39-49)

39. **Frontend Component: NotificationIcon.tsx**
40. **Frontend Component: NotificationItem.tsx**
41. **Frontend Component: NotificationDialog.tsx**
42. **Frontend Component: Modify Header.tsx**
43. **Frontend Component: Modify PlannerCardContextMenu.tsx**
44. **Frontend Component: VoteWarningModal.tsx**
45. **Frontend Component: HideReasonModal.tsx**
46. **Frontend Component: VotingAnalytics.tsx**
47. **Frontend Component: RecommendedPlannerList.tsx**
48. **Frontend Component: HiddenPlannerList.tsx**
49. **Frontend Route: AdminModerationPage.tsx**

### Phase 6: Testing (Steps 50-56)

50. **Backend Test: PlannerServiceTest - Vote Immutability**
51. **Backend Test: NotificationServiceTest - Notification Logic**
52. **Backend Test: PlannerRepositoryTest - Atomic Notification**
53. **Backend Test: UserAccountLifecycleServiceTest - Vote Reassignment**
54. **Frontend Test: NotificationDialog.test.tsx**
55. **Frontend Test: PlannerCardContextMenu.test.tsx**
56. **Integration Test: Vote → Notification Flow**

### Phase 7: Documentation (Steps 57-59)

57. **Update: architecture-map.md**
58. **Update: backend/CLAUDE.md**
59. **Update: frontend/CLAUDE.md**

## Verification Checkpoints

**After Phase 1 (Step 4):**
- Verify F-DB1: Database migrations applied without errors
- Verify F-DB2: Vote tables no longer have `deleted_at`, `updated_at` columns
- Verify F-DB3: Notifications table exists with UNIQUE constraint

**After Phase 2 (Step 26):**
- Verify F-BE1: PlannerService.castVote() throws VoteAlreadyExistsException on duplicate vote
- Verify F-BE2: NotificationService creates notifications without duplicates
- Verify F-BE3: PlannerRepository.trySetRecommendedNotified() returns 1 on first call, 0 on second

**After Phase 3 (Step 29):**
- Verify F-API1: POST /api/planner/{id}/vote with `voteType: null` returns 400 Bad Request
- Verify F-API2: POST /api/planner/{id}/vote with duplicate vote returns 409 Conflict
- Verify F-API3: GET /api/notifications/inbox returns notification list
- Verify F-API4: POST /api/admin/planner/{id}/hide-from-recommended requires ROLE_ADMIN

**After Phase 4 (Step 38):**
- Verify F-FE1: usePlannerVote.ts shows error toast on 409 response
- Verify F-FE2: useUnreadCountQuery polls every 30 seconds

**After Phase 5 (Step 49):**
- Verify F-UI1: Notification bell appears in header LEFT of language dropdown
- Verify F-UI2: Click bell opens dialog below icon, right-aligned
- Verify F-UI3: Vote buttons show "Already voted" state after voting
- Verify F-UI4: Pre-vote warning modal shows before first vote
- Verify F-UI5: Admin moderation page accessible only to admins

**After Phase 6 (Step 56):**
- Verify F-TEST1: All unit tests pass (vote immutability, notifications, atomic operations)
- Verify F-TEST2: Integration test passes (vote → threshold → notification)

## Risk Mitigation

| Risk | Step Affected | Mitigation |
|------|---------------|------------|
| User accidentally votes wrong direction | Step 44 | Strong pre-vote warning modal with explicit "Votes are PERMANENT" message, "I Understand" confirmation |
| User deletion breaks vote foreign keys | Step 25 | Reassign votes to sentinel user (id=0) using `reassignUserVotes()` query |
| Race condition duplicate notifications | Steps 14, 21 | Atomic `trySetRecommendedNotified()` query with `WHERE ... AND flag IS NULL`, returns affected row count (1 = success, 0 = already set) |
| Concurrent votes crossing threshold | Steps 22, 52 | Atomic notification flag + UNIQUE constraint on notifications table prevents duplicates |
| Frontend vote toggle still exists | Step 43 | Search codebase for `userVote === 'UP' ? null` pattern, remove ALL toggle logic |
| Header bell icon breaks mobile layout | Step 42 | Test on 320px width, use Flexbox gap, max-width: 90vw for dialog on mobile |
| Unread badge number >99 overlaps icon | Step 42 | Show "99+" for counts >99 |
| Notification dialog too wide on mobile | Step 41 | Responsive width: 400px desktop, 90vw mobile |
| Admin endpoints accessible to non-admins | Step 29 | `@PreAuthorize("hasRole('ROLE_ADMIN')")` on all admin endpoints |
| Existing soft-deleted votes lost | Step 1 | Acceptable data loss - soft-deleted votes represent "removed" votes under old toggle system |

## Pre-Implementation Validation Gate

**BEFORE Step 1 execution, verify research completed:**

| Validation Category | Check | Blocker if Missing |
|---------------------|-------|-------------------|
| **Reference Completeness** | Read all lines of PlannerCommentVote.java (composite key pattern)? | YES |
| **Reference Completeness** | Read all lines of CommentService.java (batch loading pattern)? | YES |
| **Reference Completeness** | Read all lines of Header.tsx (dropdown pattern)? | YES |
| **Contract Alignment** | Vote API contract change (`voteType: 'UP' \| 'DOWN'` only) documented? | YES |
| **Dependency Resolution** | NotificationService dependency added to PlannerService and CommentService? | YES |
| **Structure Documentation** | Atomic query pattern (`UPDATE ... WHERE ... AND flag IS NULL`) documented? | YES |
| **Difference Justification** | Vote immutability stricter than typical systems - justified with security rationale? | NO (but must document) |

**Execution Rule**: Do NOT proceed if critical blockers unresolved.

## Dependency Verification Steps

**After modifying PlannerService.castVote() (Step 22):**
- Test PlannerController vote endpoint (Step 27)
- Test frontend usePlannerVote hook (Step 38)
- Test PlannerCardContextMenu buttons (Step 43)

**After modifying Header.tsx (Step 42):**
- Test all pages to verify header layout intact
- Test mobile view (320px width) for bell icon positioning
- Test notification dialog opens without z-index conflicts

**After modifying PlannerRepository.findRecommendedPlanners() (Step 14):**
- Test PlannerService.getRecommended() (depends on repository query)
- Test frontend gesellschaft recommended page (Step 49 integration)

**After adding NotificationService (Step 21):**
- Verify PlannerService (Step 22) injects dependency
- Verify CommentService (Step 23) injects dependency
- Test notification creation in both services

## Rollback Strategy

**If Step 1 (Migration V019) fails:**
- Migration is versioned and tracked by Flyway
- Rollback: Restore database from backup (pre-migration state)
- Safe stopping point: Before Step 1

**If Step 22 (PlannerService vote logic) fails tests:**
- Revert PlannerService.java changes
- Vote API still functional (existing soft-delete logic)
- Safe stopping point: Before Step 22 (Phase 2 complete)

**If Step 42 (Header bell icon) breaks layout:**
- Revert Header.tsx changes
- Application still functional without notifications
- Safe stopping point: Before Phase 5

**If Step 49 (Admin page) has security issues:**
- Remove AdminModerationController endpoints
- Application still functional without admin features
- Safe stopping point: Before Phase 3 (Step 27-29 controllers)

**Critical Rollback Points:**
- After Phase 1: Database migrations applied (cannot rollback without DB restore)
- After Step 22: Vote logic changed (frontend compatibility required)
- After Step 42: Header modified (global UI change)
