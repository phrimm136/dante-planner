# Implementation Progress: Phases 1-5
**Date**: 2026-01-10 20:30
**Status**: 49/59 steps complete (83%)

---

## ✅ Phase 1: Database Migrations (4 files)

1. ✅ `V018__make_votes_immutable.sql` - Remove soft-delete from vote tables
2. ✅ `V019__create_notifications_table.sql` - Create notifications table with indexes
3. ✅ `V020__add_moderation_fields.sql` - Add moderation fields to planners
4. ✅ `V021__add_atomic_flags.sql` - Add version and recommendedNotifiedAt

---

## ✅ Phase 2: Logic Layer (22 files)

### New Entities (3)
1. ✅ `NotificationType.java` - Enum for notification types
2. ✅ `Notification.java` - Entity with soft-delete pattern
3. ✅ `VoteAlreadyExistsException.java` - 409 Conflict exception

### New Repositories (1)
4. ✅ `NotificationRepository.java` - With atomic queries for mark-read operations

### New DTOs (5)
5. ✅ `NotificationResponse.java`
6. ✅ `NotificationInboxResponse.java`
7. ✅ `UnreadCountResponse.java`
8. ✅ `HidePlannerRequest.java`
9. ✅ `ModerationResponse.java`

### New Services (1)
10. ✅ `NotificationService.java` - Complete with cleanup scheduler

### Modified Entities (3)
11. ✅ `PlannerVote.java`
    - Removed: `deletedAt`, `updatedAt`, `version` fields
    - Removed: All setters, `softDelete()`, `reactivate()`, `isDeleted()` methods
    - Changed: `voteType` field to `final` (immutable)
    - Fixed: No-arg constructor initializes final field to null for JPA

12. ✅ `PlannerCommentVote.java`
    - Same changes as PlannerVote.java
    - Fixed: No-arg constructor initializes final field to null for JPA

13. ✅ `Planner.java`
    - Added: `@Version Long version` (optimistic locking)
    - Added: `Instant recommendedNotifiedAt` (atomic notification flag)
    - Added: `Boolean hiddenFromRecommended`, `Long hiddenByModeratorId`, `String hiddenReason`, `Instant hiddenAt`

### Modified Repositories (3)
14. ✅ `PlannerVoteRepository.java`
    - Removed: `findByUserIdAndPlannerIdAndDeletedAtIsNull()` query
    - Simplified: `findByUserIdAndPlannerIdIn()` (removed deleted check)
    - Renamed: `reassignVotesToSentinel()` → `reassignUserVotes()`

15. ✅ `PlannerCommentVoteRepository.java`
    - Removed: `deletedAt IS NULL` from `findUpvotedCommentIds()` query
    - Removed: `softDeleteVotesByUserId()` method
    - Added: `reassignUserVotes()` method

16. ✅ `PlannerRepository.java`
    - Added: `trySetRecommendedNotified()` atomic query (prevents race condition notifications)
    - Modified: All `findRecommended*()` queries now filter `hiddenFromRecommended = false`
    - Added: `findByHiddenFromRecommendedTrueAndDeletedAtIsNull()` query

### Modified DTOs (1)
17. ✅ `VoteRequest.java`
    - Added: `@NotNull` validation on `voteType` field
    - Updated: Javadoc to reflect immutability

### Modified Services (4)
18. ✅ `ModerationService.java`
    - Added: `hideFromRecommended()` method
    - Added: `unhideFromRecommended()` method
    - Added: `listHiddenPlanners()` method
    - Added: `buildModerationResponse()` helper

19. ✅ `PlannerService.java`
    - **BREAKING**: Replaced `castVote()` method with immutable voting logic
    - Removed: 4 helper methods (`handleVoteRemoval`, `handleNewVote`, `handleVoteReactivation`, `handleVoteTypeChange`)
    - Added: Vote existence check using `existsById(PlannerVoteId)` → throws `VoteAlreadyExistsException`
    - Added: Threshold detection (9→10 net votes) with atomic notification flag
    - Added: `NotificationService` integration for `notifyPlannerRecommended()`
    - Fixed: Batch vote queries to use `findByUserIdAndPlannerIdIn()` without `AndDeletedAtIsNull`
    - Fixed: Changed `planner.getUserId()` to `planner.getUser().getId()` (entity relationship)

20. ✅ `CommentService.java`
    - Added: `NotificationService` injection via constructor
    - Added: Notification call in `createComment()` for top-level comments → `notifyCommentReceived()`
    - Added: Notification call for replies → `notifyReplyReceived()`
    - **BREAKING**: Updated `toggleUpvote()` to enforce immutable comment votes (throws 409 on duplicate)
    - Fixed: `updateComment()` vote check to use `isPresent()` instead of `isDeleted()`
    - Fixed: Changed `planner.getUserId()` to `planner.getUser().getId()`

21. ✅ `UserAccountLifecycleService.java`
    - Updated: `performHardDelete()` to call `reassignUserVotes()` for both planner and comment votes
    - Removed: References to old method names (`reassignVotesToSentinel`, `softDeleteVotesByUserId`)

### Modified Exception Handlers (1)
22. ✅ `GlobalExceptionHandler.java`
    - Added: `@ExceptionHandler` for `VoteAlreadyExistsException` → returns 409 Conflict with error code `VOTE_ALREADY_EXISTS`

---

## ✅ Phase 3: Interface Layer - Controllers (3 files)

23. ✅ `PlannerController.java`
    - Updated: JavaDoc for `castVote()` endpoint to reflect immutable voting
    - Updated: Log message to say "immutable vote"
    - Status: Endpoint already functional - documentation update only

24. ✅ `NotificationController.java` (NEW)
    - Created: REST controller with 5 endpoints
    - `GET /api/notifications/inbox` - Paginated notification list (max 100 per page)
    - `GET /api/notifications/unread-count` - Badge count for header
    - `POST /api/notifications/{id}/mark-read` - Mark single notification as read
    - `POST /api/notifications/mark-all-read` - Bulk mark as read
    - `DELETE /api/notifications/{id}` - Soft-delete notification
    - Authorization: All endpoints require authentication

25. ✅ `AdminModerationController.java` (NEW)
    - Created: Moderation controller with 2 endpoints
    - `POST /api/admin/planner/{id}/hide-from-recommended` - Hide planner from recommended list
    - `POST /api/admin/planner/{id}/unhide-from-recommended` - Restore to recommended list
    - Authorization: Both endpoints require `ROLE_ADMIN` OR `ROLE_MODERATOR`
    - Pattern: Manual curation without deleting votes (arca.live pattern)

---

## Breaking Changes Introduced

### API Contract Changes
- **Before**: `POST /api/planner/{id}/vote` accepted `voteType: null` to remove vote
- **After**: `voteType: null` returns `400 Bad Request` (validation error)
- **Impact**: Frontend must remove ALL toggle logic from vote buttons

### Database Schema Changes
- **Vote tables**: No longer have `deleted_at`, `updated_at`, `version` columns
- **Planners table**: Added 6 new columns (moderation + atomic flags)
- **Notifications table**: New table created with UNIQUE constraint

### Vote Behavior Changes
- **Before**: Users could toggle votes (UP ↔ null ↔ DOWN)
- **After**: Users can vote ONCE (UP or DOWN), no changes allowed
- **Error Response**: Duplicate vote attempts return `409 Conflict` with `VoteAlreadyExistsException`

---

## Compilation Status

**✅ BUILD SUCCESS**
- Total source files: 132
- All files compile without errors
- Backend ready for frontend integration

---

## ✅ Phase 4: Frontend - Types, Schemas, Hooks (9 files)

**Status**: 9/9 files complete (100%)

### New Types (1)
26. ✅ `NotificationTypes.ts` (NEW)
    - TypeScript interfaces: NotificationResponse, NotificationInboxResponse, UnreadCountResponse
    - NotificationType enum: PLANNER_RECOMMENDED, COMMENT_RECEIVED, REPLY_RECEIVED, REPORT_RECEIVED
    - Matches backend DTOs exactly

### New Schemas (1)
27. ✅ `NotificationSchemas.ts` (NEW)
    - Zod validation schemas for runtime type safety
    - NotificationResponseSchema, NotificationInboxResponseSchema, UnreadCountResponseSchema
    - All schemas use .strict() for extra field rejection

### New Hooks (7)
28. ✅ `useNotificationsQuery.ts` (NEW)
    - TanStack Query hook for GET /api/notifications/inbox?page=0&size=20
    - Uses useSuspenseQuery for Suspense integration
    - Query key factory pattern: notificationQueryKeys.inbox(page, size)
    - 1-minute staleTime

29. ✅ `useUnreadCountQuery.ts` (NEW)
    - TanStack Query hook for GET /api/notifications/unread-count
    - **CRITICAL**: refetchInterval: 30000 (30-second polling for real-time badge updates)
    - Uses useSuspenseQuery

30. ✅ `useMarkReadMutation.ts` (NEW)
    - Mutation for POST /api/notifications/{id}/mark-read
    - onSuccess: Invalidates 'notifications' queries
    - Validates response with NotificationResponseSchema

31. ✅ `useDeleteNotificationMutation.ts` (NEW)
    - Mutation for DELETE /api/notifications/{id}
    - No response body (204 No Content)
    - onSuccess: Invalidates notification queries

32. ✅ `useHideFromRecommendedMutation.ts` (NEW)
    - Mutation for POST /api/moderator/planner/{id}/hide-from-recommended
    - Request body: { reason: string }
    - onSuccess: Invalidates planner and gesellschaft queries

33. ✅ `useUnhideFromRecommendedMutation.ts` (NEW)
    - Mutation for POST /api/moderator/planner/{id}/unhide-from-recommended
    - onSuccess: Invalidates planner and gesellschaft queries

### Modified Hooks (1)
34. ✅ `usePlannerVote.ts` (MODIFIED - **BREAKING CHANGE**)
    - **BREAKING**: Removed vote toggle logic - voteType no longer accepts null
    - Updated type: `voteType: VoteDirection` (was `VoteDirection | null`)
    - Added 409 Conflict error handling with ConflictError instanceof check
    - Added import: `ConflictError` from '@/lib/api'
    - Updated JSDoc with breaking change warning

---

## ✅ Phase 5: Frontend - Components & UI (11/11 files - 100%)

**Status**: All components complete, TypeScript compilation verified

### New Components - Notification System (3)
35. ✅ `NotificationIcon.tsx` (NEW)
    - Bell icon button with unread count badge
    - Shows "99+" if count > 99
    - Badge hidden when count is 0
    - Pattern: shadcn Badge component

36. ✅ `NotificationItem.tsx` (NEW)
    - Single notification display component
    - Icon mapping: Trophy (recommended), MessageSquare (comment), Reply (reply), Flag (report)
    - Timestamp display using formatRelativeTime()
    - Click handler: navigates to content, marks as read
    - Delete button: soft-deletes notification

37. ✅ `NotificationDialog.tsx` (NEW)
    - Dialog component with scrollable notification list
    - Uses ScrollArea with max-height 600px
    - Empty state: "No notifications" message
    - Footer: "Mark all as read" button
    - Width: 400px desktop, 90vw mobile

### Modified Components (2)
38. ✅ `Header.tsx` (MODIFIED)
    - Added notification bell icon LEFT of language dropdown (line 178-189)
    - Imports: useUnreadCountQuery, NotificationIcon, NotificationDialog
    - State: notificationDialogOpen managed with useState
    - Only shown when user is logged in (conditional rendering)
    - Polling: 30-second unread count updates

39. ✅ `PlannerCardContextMenu.tsx` (MODIFIED - **BREAKING CHANGE**)
    - **BREAKING**: Removed vote toggle logic (lines 134-152)
    - Both UP/DOWN buttons disabled after any vote (disabled={isPending || planner.userVote !== null})
    - New button states:
      - Not voted: "Upvote" / "Downvote"
      - Voted UP: "✓ Upvoted" (disabled)
      - Voted DOWN: "✓ Downvoted" (disabled)
      - Already voted (opposite): "Already Voted" (disabled)
    - Error handling: 409 Conflict handled by usePlannerVote hook

### I18n Updates
40. ✅ Updated i18n files (EN, JP, KR, CN)
    - Added: `upvoted`: "✓ Upvoted"
    - Added: `downvoted`: "✓ Downvoted"
    - Added: `alreadyVoted`: "Already Voted"
    - Removed: `removeUpvote`, `removeDownvote` (obsolete with immutable voting)

### New Components - Vote Warning (1)
44. ✅ `VoteWarningModal.tsx` (NEW)
    - Pre-vote confirmation dialog with permanent vote warning
    - Shows once per planner (tracked via localStorage: `vote-warning-shown-${plannerId}`)
    - Props: open, onOpenChange, onConfirm, voteDirection ('UP'|'DOWN'), plannerId
    - Buttons: "Cancel" (outline) and "I Understand" (destructive variant)
    - i18n keys: voteWarning.title, messageUp, messageDown, understand, cancel

### New Components - Moderator Dashboard (5)
45. ✅ `HideReasonModal.tsx` (NEW)
    - Moderator dialog for hiding planner from recommended list
    - Text area with min 10 chars validation, 500 char max
    - Uses useHideFromRecommendedMutation hook (already created in Phase 4)
    - Shows planner title for context

46. ✅ `VotingAnalytics.tsx` (NEW)
    - Vote statistics card component (upvotes, downvotes, net, velocity)
    - Calculates vote velocity (votes per hour since publish)
    - Warning flags badge for suspicious patterns (prop: hasWarningFlags)
    - Responsive grid: 2 cols mobile, 4 cols desktop

47. ✅ `RecommendedPlannerList.tsx` (NEW)
    - Moderator tab showing current recommended planners
    - Each row: planner info + VotingAnalytics + "Hide" button
    - Opens HideReasonModal on hide click
    - Uses useMDGesellschaftData with recommended filter

48. ✅ `HiddenPlannerList.tsx` (NEW)
    - Moderator tab showing hidden planners with unhide controls
    - Displays: hide reason, moderator username, hidden timestamp
    - "Unhide" button calls useUnhideFromRecommendedMutation
    - Uses useHiddenPlannersQuery (new hook created)

49. ✅ `ModeratorDashboardPage.tsx` (NEW)
    - Route at `/moderator/dashboard` (directory: `routes/moderator/`)
    - Two tabs: "Recommended Review" | "Hidden Planners"
    - Uses shadcn Tabs component
    - Route protection: Frontend UI guard (backend has @PreAuthorize)

### New Hook (1)
50. ✅ `useHiddenPlannersQuery.ts` (NEW)
    - Location: `frontend/src/hooks/useHiddenPlannersQuery.ts`
    - Endpoint: `GET /api/admin/planner/hidden?page={page}&size={size}`
    - Pattern: Follows useNotificationsQuery.ts (pagination, useSuspenseQuery)
    - Zod schemas: HiddenPlannerSchema, HiddenPlannerPageSchema
    - Query key factory: hiddenPlannersQueryKeys.page(page, size)

### i18n Updates (4 files)
51. ✅ Updated all language files (EN, JP, KR, CN)
    - Added `voteWarning` section (title, messageUp, messageDown, understand, cancel)
    - Added `moderation` section (15 keys: title, tabs, buttons, placeholders, stats labels)

---

## TypeScript Compilation Status

**✅ COMPILATION SUCCESS (Phase 5 Final)**
- Command: `cd frontend && yarn tsc --noEmit`
- Result: No type errors
- All Phase 1-5 files type-check successfully
- Verified: 2026-01-10 20:30

---

## Breaking Changes Summary

### Backend Breaking Changes
1. **Vote API**: `voteType: null` no longer accepted (400 Bad Request)
2. **Vote Behavior**: No toggle - votes are permanent (409 Conflict on re-vote)

### Frontend Breaking Changes
1. **usePlannerVote.ts**: `voteType` parameter type changed from `VoteDirection | null` to `VoteDirection`
2. **PlannerCardContextMenu.tsx**: Vote buttons disabled after voting (no toggle UI)
3. **I18n keys**: Removed `removeUpvote`/`removeDownvote`, added `upvoted`/`downvoted`/`alreadyVoted`

---

## Files Modified Summary

**Created**: 24 new files (11 backend + 13 frontend)
**Modified**: 25 files (18 backend + 3 frontend + 4 i18n)
**Total Files**: 49 files

**Phases Complete**: 1-5 (100%)
**Overall Progress**: 49/59 steps (83%)

---

## Next Steps (Remaining Work)

### Phase 6: Testing (7 tests)
- Backend unit tests (vote immutability, notifications, atomic operations)
- Frontend component tests (NotificationDialog, PlannerCardContextMenu)
- Integration test (vote → threshold → notification flow)

### Phase 7: Documentation (3 updates)
- architecture-map.md
- backend/CLAUDE.md
- frontend/CLAUDE.md
