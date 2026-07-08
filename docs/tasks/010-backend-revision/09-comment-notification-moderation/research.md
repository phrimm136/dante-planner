# Research: Immutable Votes + Notifications + Moderation

## Clarifications Resolved

**Database System:** MySQL (current setup, not PostgreSQL)
- Use MySQL-compatible migration syntax: `TIMESTAMP` (not `TIMESTAMP WITHOUT TIME ZONE`)
- Dialect: `org.hibernate.dialect.MySQLDialect`

**Sentinel User ID:** Use existing constant
- Reference: `CommentResponse.AuthorInfo.SENTINEL_USER_ID`
- Apply to: Vote reassignment, comment reassignment on user deletion

**Admin Role Authorization:** Spring Security standard
- Use: `@PreAuthorize("hasRole('ROLE_ADMIN')")`
- Pattern: Standard Spring Security role-based authorization
- Revise any non-standard role checking patterns found

**Vote Migration Strategy:** Hard-delete soft-deleted votes
- Migration V019 removes rows with `deleted_at IS NOT NULL`
- Rationale: Soft-deleted votes represent "removed" votes, acceptable data loss
- Keep only active votes (deleted_at IS NULL)

---

## Backend Spec-to-Code Mapping

### Immutable Voting System

**Remove Vote Toggle Logic:**
- Current: `PlannerService.castVote()` line 446 allows toggle via soft-delete
- Change: Check vote existence with `voteRepository.existsById()` → throw `VoteAlreadyExistsException` (409)
- Remove: All soft-delete methods from `PlannerVote.java`, `PlannerCommentVote.java`

**Entity Modifications:**
- `PlannerVote.java`: Remove setters, `softDelete()`, `reactivate()`, `isDeleted()` methods
- `PlannerCommentVote.java`: Same changes
- Make `voteType` field final (immutable after construction)

**DTO Validation:**
- `VoteRequest.java`: Add `@NotNull` on voteType field
- Reject null with 400 Bad Request (vote removal not allowed)

**Database Migration V019:**
- Drop columns: `planner_votes.deleted_at`, `planner_votes.updated_at`
- Drop columns: `planner_comment_votes.deleted_at`, `planner_comment_votes.updated_at`
- Remove soft-deleted rows: `DELETE FROM planner_votes WHERE deleted_at IS NOT NULL`
- Remove soft-deleted rows: `DELETE FROM planner_comment_votes WHERE deleted_at IS NOT NULL`

**User Deletion Integration:**
- `UserAccountLifecycleService.performHardDelete()`: Call `reassignUserVotes()`
- `PlannerVoteRepository.java`: Add `@Modifying @Query void reassignUserVotes(Long userId, Long sentinelUserId)`
- `PlannerCommentVoteRepository.java`: Add same reassignment query
- Use: `CommentResponse.AuthorInfo.SENTINEL_USER_ID` constant

### Notification System

**New Entities:**
- `Notification.java`: Fields: id, userId, contentId, notificationType, read, createdAt, readAt, deletedAt
- `NotificationType.java`: Enum values: PLANNER_RECOMMENDED, COMMENT_RECEIVED, REPLY_RECEIVED, REPORT_RECEIVED

**Database Migration V020:**
- Create `notifications` table with 7 columns
- Indexes: `idx_notifications_user_read (user_id, read, created_at DESC)`, `idx_notifications_created (created_at)`
- UNIQUE constraint: `UNIQUE (user_id, content_id, notification_type)` for deduplication

**Repository:**
- `NotificationRepository.java`: Extends JpaRepository
- Query methods: `findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId, Pageable pageable)`
- Query methods: `countByUserIdAndReadFalseAndDeletedAtIsNull(Long userId)`
- Atomic update: Mark as read, mark all as read, soft-delete

**Service:**
- `NotificationService.java`: Business logic layer
- Methods: `notifyPlannerRecommended()`, `notifyCommentReceived()`, `notifyReplyReceived()`, `notifyReportReceived()`
- Method: `@Scheduled cleanupOldNotifications()` (soft-delete >90 days, hard-delete >1 year)
- Integration: Call from PlannerService, CommentService at event points

**Controller:**
- `NotificationController.java`: REST endpoints
- `GET /api/notifications/inbox?page=0&size=20` → Returns NotificationInboxResponse
- `POST /api/notifications/{id}/mark-read` → Returns NotificationResponse
- `POST /api/notifications/mark-all-read` → Returns count
- `DELETE /api/notifications/{id}` → 204 No Content (soft-delete)
- `GET /api/notifications/unread-count` → Returns UnreadCountResponse
- Authorization: All endpoints require authentication

**Integration Points:**
- `PlannerService.castVote()`: After vote increment, check threshold transition
- If `netBefore < threshold AND netAfter >= threshold`: Call `plannerRepository.trySetRecommendedNotified()`
- If atomic flag set succeeds: Call `notificationService.notifyPlannerRecommended()`
- `CommentService.createComment()`: After comment save, call `notificationService.notifyCommentReceived()`
- `CommentService.createComment()` with parentId: Call `notificationService.notifyReplyReceived()`

**Atomic Flag for Race Condition:**
- Migration V022: Add `planner.recommended_notified_at TIMESTAMP NULL`
- Repository method: `@Modifying @Query int trySetRecommendedNotified(UUID plannerId, int threshold)`
- SQL: `UPDATE planners SET recommended_notified_at = NOW() WHERE id = ? AND (upvotes - downvotes) >= ? AND recommended_notified_at IS NULL`
- Returns 1 if updated (first thread wins), 0 if already set

### Admin Moderation System

**Planner Entity Modifications:**
- Add fields: `hiddenFromRecommended BOOLEAN DEFAULT false`, `hiddenByAdminId BIGINT`, `hiddenReason TEXT`, `hiddenAt TIMESTAMP`
- Add field: `version BIGINT DEFAULT 0` (optimistic locking)

**Database Migration V021:**
- Add columns to `planners` table
- Add constraint: `FOREIGN KEY (hidden_by_admin_id) REFERENCES users(id)`
- Add index: `idx_planner_recommended (published, deleted_at, hidden_from_recommended)`

**Repository Changes:**
- `PlannerRepository.findRecommendedPlanners()`: Add filter `WHERE hidden_from_recommended = false`

**Service Methods:**
- `ModerationService.java`: Add methods (may already partially exist)
- Methods: `hideFromRecommended(UUID plannerId, Long adminId, String reason)`
- Methods: `unhideFromRecommended(UUID plannerId, Long adminId)`
- Methods: `listHiddenPlanners(Pageable pageable)`
- Scheduled: `@Scheduled detectSuspiciousVoting()` (flags planners with unusual patterns)

**Controller:**
- `AdminModerationController.java`: NEW controller
- `POST /api/admin/planner/{id}/hide-from-recommended` → Requires ROLE_ADMIN
- `POST /api/admin/planner/{id}/unhide-from-recommended` → Requires ROLE_ADMIN
- `GET /api/admin/planner/hidden?page=0&size=20` → Requires ROLE_ADMIN
- Authorization: `@PreAuthorize("hasRole('ROLE_ADMIN')")`

---

## Frontend Spec-to-Code Mapping

### Vote Button Changes (BREAKING)

**Remove Toggle Logic:**
- Current: `PlannerCardContextMenu.tsx` lines 134-144 toggle `userVote === 'UP' ? null : 'UP'`
- Change: Remove toggle, check `planner.userVote !== null` → show error toast
- Change: Disable BOTH vote buttons after any vote (not just the voted button)

**Pre-Vote Warning:**
- New component: Modal/Dialog with strong warning text
- Trigger: Before first vote (check localStorage for "warning-shown" flag)
- Message: "Votes are PERMANENT. Once you choose UP or DOWN, you cannot change your vote or remove it."
- Buttons: "Cancel" (dismiss, no vote) | "I Understand" (proceed with vote)

**Vote Button States:**
- Unvoted: Both buttons enabled
- After voting: Both buttons disabled with "✓ Upvoted" or "✓ Downvoted" label

### Notification System Frontend

**Header Modification:**
- `Header.tsx`: Add notification bell button LEFT of language dropdown (line ~169)
- Button: Bell icon with unread count badge (red dot with number)
- Badge: Show "99+" if count > 99

**Notification Dialog:**
- `NotificationDialog.tsx`: NEW component (shadcn Dialog, NOT DropdownMenu)
- Trigger: Click bell button
- Position: `align="end"` (right-aligned below bell)
- Size: Width 400px (90vw on mobile), max-height 600px, scrollable
- Content: List of notifications (max 20, scrollable if more)

**Notification Item:**
- `NotificationItem.tsx`: Single notification display
- Shows: Type icon (trophy/message/reply), title, timestamp (relative), read indicator
- Click: Navigate to content, close dialog, mark as read

**Dialog Footer:**
- Button: "Mark all as read" at bottom of dialog
- Updates all unread notifications, refreshes unread count

**Hooks:**
- `useNotificationsQuery.ts`: Fetch notifications with `useQuery` (NOT useSuspenseQuery - dialog should render empty if loading)
- `useUnreadCountQuery.ts`: Fetch unread count with 30-second polling (`refetchInterval: 30000`)
- `useMarkReadMutation.ts`: Mark single notification as read
- `useDeleteNotificationMutation.ts`: Soft-delete (dismiss) notification

**Types & Schemas:**
- `NotificationTypes.ts`: TypeScript interfaces (Notification, NotificationType, etc.)
- `NotificationSchemas.ts`: Zod validation schemas for API responses

### Admin Dashboard

**Route:**
- `AdminModerationPage.tsx`: NEW route at `/admin/moderation`
- Tabs: "Recommended Review" | "Hidden Planners"
- Authorization: Check `user.role === 'ADMIN'` before rendering

**Recommended Review Tab:**
- `RecommendedPlannerList.tsx`: Lists current recommended planners
- `VotingAnalytics.tsx`: Chart showing vote distribution, account age analysis
- Action: "Hide from Recommended" button → Opens `HideReasonModal.tsx`

**Hidden Planners Tab:**
- `HiddenPlannerList.tsx`: Lists hidden planners with reason, admin, timestamp
- Action: "Unhide" button → Restores to recommended list

---

## Pattern References (MUST READ BEFORE WRITING)

### Backend Patterns

**Immutable Entity with Composite Key:**
- Read: `PlannerCommentVote.java` lines 22-71
- Pattern: `@IdClass`, `implements Persistable`, no setters on ID fields, `isNew()` tracking
- Apply to: `PlannerVote.java` modifications

**Exception Handling:**
- Read: `GlobalExceptionHandler.java`, `PlannerNotFoundException.java`
- Pattern: Custom exception class, `@ExceptionHandler` method in GlobalExceptionHandler
- Apply to: `VoteAlreadyExistsException.java` → 409 Conflict

**Batch Loading (N+1 Prevention):**
- Read: `CommentService.getComments()` lines 69-75
- Pattern: Collect IDs, `findAllById()`, stream to Map
- Apply to: `NotificationService` batch-loading related planners/comments

**Soft-Delete Pattern:**
- Read: `Planner.softDelete()` line 125, `PlannerComment.softDelete()` line 85
- Pattern: `deletedAt` field, `WHERE deleted_at IS NULL` in queries
- Apply to: `Notification.deletedAt` for user dismissal

**Atomic Counter:**
- Read: `PlannerRepository.incrementUpvotes()` (mentioned in architecture-map)
- Pattern: `@Modifying @Query("UPDATE ... SET field = field + 1 WHERE id = ?")`
- Apply to: Vote count increments (already exists, reuse pattern)

### Frontend Patterns

**Header Dropdown/Dialog:**
- Read: `Header.tsx` lines 169-198 (Language dropdown)
- Pattern: `DropdownMenu` or `Dialog`, `align="end"`, state management with `useState(false)`
- Apply to: Notification bell trigger + dialog

**TanStack Query Hook:**
- Read: `useIdentityListData.ts`
- Pattern: `useSuspenseQuery` or `useQuery`, Zod validation, error handling
- Apply to: `useNotificationsQuery.ts`, `useUnreadCountQuery.ts`

**Mutation Hook:**
- Read: `usePlannerVote.ts`
- Pattern: `useMutation`, onSuccess invalidates queries, onError shows toast
- Apply to: `useMarkReadMutation.ts`, `useDeleteNotificationMutation.ts`

---

## Existing Utilities (Verified Available)

**Backend:**
- `RateLimitConfig.java`: Bucket4j rate limiting (reuse pattern)
- `GlobalExceptionHandler.java`: Exception → HTTP status mapping
- `UserAccountLifecycleService.java`: User deletion with content reassignment
- `CommentResponse.AuthorInfo.SENTINEL_USER_ID`: Sentinel user constant

**Frontend:**
- `lib/constants.ts`: Constants storage (check for MAX_NOTIFICATIONS = 20)
- `lib/utils.ts`: cn() for className merging
- `lib/validation.ts`: validateData() for Zod validation
- `hooks/useAuthQuery.ts`: Get current user for admin check
- `components/ui/dialog.tsx`: shadcn Dialog component
- `components/ui/badge.tsx`: shadcn Badge for unread count

---

## Gap Analysis

**Must Create (Backend):**
- VoteAlreadyExistsException.java
- Notification.java
- NotificationType.java
- NotificationRepository.java
- NotificationService.java
- NotificationController.java
- AdminModerationController.java (or extend existing ModerationService)
- 4 database migrations (V019-V022)

**Must Create (Frontend):**
- NotificationDialog.tsx
- NotificationItem.tsx
- NotificationIcon.tsx
- useNotificationsQuery.ts
- useUnreadCountQuery.ts
- useMarkReadMutation.ts
- useDeleteNotificationMutation.ts
- AdminModerationPage.tsx
- NotificationTypes.ts
- NotificationSchemas.ts

**Must Modify (Backend - BREAKING):**
- PlannerVote.java (remove soft-delete, setters)
- PlannerCommentVote.java (same)
- PlannerService.castVote() (add immutability check)
- CommentService.toggleUpvote() (add immutability check)
- Planner.java (add moderation fields, version)
- PlannerRepository.java (add atomic methods)
- UserAccountLifecycleService.performHardDelete() (add vote reassignment)
- GlobalExceptionHandler.java (add VoteAlreadyExistsException handler)

**Must Modify (Frontend - BREAKING):**
- PlannerCardContextMenu.tsx lines 134-144 (remove toggle)
- usePlannerVote.ts (reject null voteType)
- Header.tsx (add notification bell)

---

## Pattern Enforcement

**Frontend Pattern Requirements (MUST read before creating):**

| New File | MUST Read First | Pattern to Copy |
|----------|----------------|-----------------|
| `NotificationTypes.ts` | `frontend/src/types/PlannerTypes.ts` | TypeScript interface patterns, enum definitions |
| `NotificationSchemas.ts` | `frontend/src/schemas/PlannerSchemas.ts` | Zod schema validation patterns, z.object() structure |
| `useNotificationsQuery.ts` | `frontend/src/hooks/useIdentityListData.ts` | TanStack Query useQuery pattern (NOT useSuspenseQuery) |
| `useUnreadCountQuery.ts` | `frontend/src/hooks/useIdentityListData.ts` | TanStack Query with polling (refetchInterval) |
| `useMarkReadMutation.ts` | `frontend/src/hooks/usePlannerVote.ts` | useMutation pattern, query invalidation, error toast |
| `useDeleteNotificationMutation.ts` | `frontend/src/hooks/usePlannerVote.ts` | useMutation pattern with no response body |
| `useHideFromRecommendedMutation.ts` | `frontend/src/hooks/usePlannerVote.ts` | useMutation pattern with request body |
| `useUnhideFromRecommendedMutation.ts` | `frontend/src/hooks/usePlannerVote.ts` | useMutation pattern, multiple query invalidations |
| `NotificationIcon.tsx` | `frontend/src/components/ui/badge.tsx` | shadcn Badge component, conditional rendering |
| `NotificationItem.tsx` | `frontend/src/components/plannerList/PlannerCard.tsx` | Click handler, timestamp display, icon mapping |
| `NotificationDialog.tsx` | `frontend/src/components/ui/dialog.tsx` | shadcn Dialog component, scroll area, button actions |
| `VoteWarningModal.tsx` | `frontend/src/components/ui/dialog.tsx` | shadcn Dialog with alert variant, confirmation buttons |
| `HideReasonModal.tsx` | `frontend/src/components/ui/dialog.tsx` | Dialog with form input, textarea validation |
| `VotingAnalytics.tsx` | `frontend/src/components/plannerList/PlannerCard.tsx` | Data visualization, statistics display |
| `RecommendedPlannerList.tsx` | `frontend/src/components/plannerList/PlannerCard.tsx` | List rendering with pagination, card layout |
| `HiddenPlannerList.tsx` | `frontend/src/components/plannerList/PlannerCard.tsx` | List rendering with moderation metadata |
| `AdminModerationPage.tsx` | `frontend/src/routes/PlannerMDNewPage.tsx` | TanStack Router page, tabs component, role check |

---

## Testing Requirements

**Manual UI Verification:**
- Pre-vote warning modal shows with strong warning text
- Both vote buttons disable after voting ANY direction
- Attempting to vote again shows error toast "Votes are permanent"
- Notification bell badge shows correct unread count
- Dialog opens below bell, right-aligned, scrollable
- Click notification navigates to content, marks as read, closes dialog
- "Mark all as read" updates all notifications, badge updates to 0
- Mobile: Dialog width adjusts to 90vw on small screens
- Admin: Hide/unhide planners, verify filtering on recommended list

**Automated Unit Tests:**
- Vote existence check throws VoteAlreadyExistsException (409)
- Null voteType rejected with 400 Bad Request
- User deletion reassigns votes to sentinel user
- Notification creation deduplicates via UNIQUE constraint
- Atomic notification flag prevents race condition duplicates
- Mark as read updates read field, sets readAt timestamp
- Unread count query excludes read/deleted notifications

**Automated Integration Tests:**
- Vote → Threshold → Notification flow (end-to-end)
- Comment → Notification for planner owner
- Reply → Notification for parent comment author
- Concurrent votes on threshold-1 planner create single notification
- Admin hide → Planner removed from recommended query
- Admin unhide → Planner reappears in recommended query

---

## Implementation Order

1. Database migrations (V019-V022) - Foundation
2. Backend entities (PlannerVote, Notification, etc.)
3. Backend vote immutability (VoteAlreadyExistsException, service changes)
4. Backend notification system (NotificationService, Controller)
5. Frontend vote button changes (remove toggle, add warning)
6. Frontend notification system (Dialog, hooks)
7. Admin moderation backend (ModerationService, Controller)
8. Admin moderation frontend (AdminModerationPage)
9. Testing (manual UI + automated)
10. Documentation updates (architecture-map, CLAUDE.md)
