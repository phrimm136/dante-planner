# Comment System Status

Last Updated: 2026-01-18 (Session 5)
Current Step: 34/34
Current Phase: Complete

---

## Phase Progress

| Phase | Steps | Status |
|-------|-------|--------|
| 1: Backend Data Layer | 1-5 | ✅ done |
| 2: Backend Logic Layer | 6-12 | ✅ done |
| 3: Frontend Data Layer | 13-17 | ✅ done |
| 4: Frontend UI Layer | 18-24 | ✅ done |
| 5: Integration | 25-26 | ✅ done |
| 6: i18n | 27-30 | ✅ done |
| 7: Tests | 31-34 | ✅ done |

---

## Non-Planned Changes

### Comment Editor Fixes (2026-01-17)
- [x] `CommentEditor.tsx` - Added `onMouseDown={(e) => e.preventDefault()}` to Submit/Cancel buttons to prevent blur before click
- [x] `CommentEditor.tsx` - Added `onUpdate`/`onCreate` callbacks with `charCount` state for Tiptap reactivity
- [x] `CommentActionButtons.tsx` - Added `fill-current text-primary` to Bell icon for enabled state

### CORS Fix (2026-01-17)
- [x] `CorsConfig.java` - Added `PATCH` to allowed methods (was missing, causing preflight failures)

### Owner Notification Toggle Endpoint (2026-01-17)
Missing endpoint for planner owner notification toggle:
- [x] `PlannerController.java` - Added `PATCH /{id}/notifications` endpoint
- [x] `PlannerService.java` - Added `toggleOwnerNotifications()` method
- [x] `ToggleOwnerNotificationsRequest.java` - New DTO
- [x] `ToggleOwnerNotificationsResponse.java` - New DTO

### Notification Duplicate Constraint Fix (2026-01-17)
Notifications were failing on second comment/reply due to unique constraint on `(user_id, content_id, notification_type)`:
- [x] `NotificationService.java` - Changed `content_id` from parent ID to NEW comment/reply ID
- [x] `NotificationService.java` - Added `@Transactional(propagation = Propagation.REQUIRES_NEW)` to both notify methods
- [x] `CommentService.java` - Pass `saved.getId()` to notification methods
- [x] `NotificationServiceTest.java` - Updated tests for new method signatures (4 params)
- [x] `CommentServiceNotificationTest.java` - Updated verify calls for new method signatures

### Notification UI Completion
Enabled the previously disabled notification system in Header:
- [x] `NotificationIcon.tsx` - Bell button with unread count badge
- [x] `NotificationItem.tsx` - Single notification row with actions
- [x] `NotificationDialog.tsx` - Modal dialog with notification list
- [x] `Header.tsx` - Uncommented and integrated NotificationBell
- [x] i18n keys for notifications (all 4 languages)

### Backend Fixes
- [x] `ownerNotificationsEnabled` now returns `false` for non-owners (was `null`)
- [x] `PlannerServiceTest` updated with `commentRepository` mock
- [x] `PlannerCommentRepository.countByPlannerIdAndDeletedAtIsNull()` added

### Frontend Schema Fixes
- [x] `ownerNotificationsEnabled` changed from optional to required boolean
- [x] Removed `?? true` fallbacks in `PlannerDetailHeader.tsx`

### SSE Notification Handler Fix (2026-01-17)
SSE events for notifications were being sent but ignored by frontend:
- [x] `useSseConnection.ts` - Added event listeners for `notify:comment`, `notify:recommended`, `notify:published`
- [x] `useSseConnection.ts` - `handleNotification` callback invalidates notification queries on SSE event

### Comment Delete 500 Error Fix (2026-01-17)
Soft delete was setting `content = null` but DB column is NOT NULL:
- [x] `PlannerComment.java` - Changed `softDelete()` to set `content = ""` instead of `null`

### Delete Confirmation Dialog (2026-01-17)
- [x] `CommentCard.tsx` - Added confirmation dialog before delete action

### DTO Response Minimization (2026-01-18)
Reduced API responses to return only necessary data (frontend invalidates cache, doesn't use response):

**New DTOs:**
- [x] `CreateCommentResponse.java` - Minimal response: `{ id: UUID, createdAt: Instant }`
- [x] `UpdateCommentResponse.java` - Minimal response: `{ editedAt: Instant }`

**Modified DTOs:**
- [x] `CommentReportResponse.java` - Reduced from 4 fields to `{ createdAt: Instant }`
- [x] `CommentVoteResponse.java` - Changed `commentId` from `Long` to `UUID`
- [x] `ToggleNotificationResponse.java` - Already minimal: `{ authorNotificationsEnabled: boolean }`

**Service Changes:**
- [x] `CommentService.createComment()` - Returns `CreateCommentResponse` instead of full `CommentResponse`
- [x] `CommentService.updateComment()` - Returns `UpdateCommentResponse` instead of full `CommentResponse`
- [x] `PlannerService.toggleOwnerNotifications()` - Returns `ToggleOwnerNotificationsResponse` directly (was `boolean`)

### UUID Migration for Comment API (2026-01-18)
All comment operations now use public UUID instead of internal Long ID:

**Service Methods Updated:**
- [x] `CommentService.updateComment(UUID commentPublicId, ...)` - Was `Long commentId`
- [x] `CommentService.deleteComment(UUID commentPublicId, ...)` - Was `Long commentId`
- [x] `CommentService.toggleUpvote(UUID commentPublicId, ...)` - Was `Long commentId`
- [x] `CommentService.toggleNotification(UUID commentPublicId, ...)` - Was `Long commentId`
- [x] `CommentReportService.createReport(UUID commentPublicId, ...)` - Was `Long commentId`

**Controller Updated:**
- [x] `CommentController.java` - All `@PathVariable` changed from `Long` to `UUID`

**Entity Setters (for testing):**
- [x] `PlannerComment.java` - Added `setPublicId(UUID)` setter
- [x] `PlannerCommentReport.java` - Added `setCreatedAt(Instant)` setter

### Guest Visibility Fix (2026-01-18)
- [x] `CommentActionButtons.tsx` - Hide Reply and Report buttons for unauthenticated users
- [x] `CommentCard.tsx` - Added `isAuthenticated` prop passthrough
- [x] `CommentThread.tsx` - Added `isAuthenticated` prop passthrough
- [x] `CommentSection.tsx` - Passes `isAuthenticated` to `CommentThread`

### Test Updates (2026-01-18)
- [x] `CommentServiceTest.java` - Updated for new response types and UUID params
- [x] `CommentReportServiceTest.java` - Updated for UUID params and minimal response

### Reply Editor Indentation Fix (2026-01-18)
- [x] `CommentCard.tsx` - Added `marginLeft: COMMENT_INDENT_PER_LEVEL` and `border-l-2 border-border pl-3` to reply editor wrapper to visually indicate where reply will appear

### Reply Endpoint Restructure (2026-01-18)
Moved reply creation from body param to dedicated endpoint:

**New Endpoint:**
- [x] `POST /comments/{parentCommentId}/replies` - Dedicated reply endpoint with UUID path variable

**Backend Changes:**
- [x] `CommentController.java` - Added `createReply()` endpoint
- [x] `CommentService.java` - Added `createReply(UUID parentPublicId, Long userId, String content)` method
- [x] `CreateCommentRequest.java` - Changed `parentCommentId` from `Long` to `UUID`
- [x] `PlannerCommentRepository.java` - Added `findByPublicId(UUID publicId)` method
- [x] `CommentNotFoundException.java` - Added constructor accepting `UUID`

**Frontend Changes:**
- [x] `useCommentMutations.ts` - Routes replies to `/api/comments/${parentCommentId}/replies`, top-level to `/api/planner/${plannerId}/comments`

### Vote Persistence Fix (2026-01-18)
Votes were not being persisted correctly:
- [x] `CommentService.java` - Changed `save()` to `saveAndFlush()` for immediate DB write
- [x] `CommentService.java` - Changed duplicate check from `findByCommentIdAndUserId()` to `existsById(PlannerCommentVoteId)` for direct composite key lookup

### Notification UUID Migration (2026-01-18)
All notification operations now use public UUID instead of internal Long ID:

**Database Migration:**
- [x] `V028__add_notification_public_id.sql` - Added `public_id` UUID column to `notifications` table with unique constraint and index

**Entity:**
- [x] `Notification.java` - Added `publicId` UUID field with `@PrePersist` generation and setter for testing

**Repository:**
- [x] `NotificationRepository.java` - Added `findByPublicId(UUID publicId)` method

**Service:**
- [x] `NotificationService.markAsRead(UUID publicId, Long userId)` - Was `Long notificationId`
- [x] `NotificationService.deleteNotification(UUID publicId, Long userId)` - Was `Long notificationId`

**Controller:**
- [x] `NotificationController.java` - All `@PathVariable("id")` changed from `Long` to `UUID`

**DTO:**
- [x] `NotificationResponse.java` - Changed `id` field from `Long` to `UUID`, updated `fromEntity()` to use `getPublicId()`

**Frontend:**
- [x] `NotificationSchemas.ts` - Changed `id` from `z.number().int().positive()` to `z.string().uuid()`
- [x] `NotificationTypes.ts` - Changed `id` from `number` to `string`
- [x] `useDeleteNotificationMutation.ts` - Changed `notificationId` param from `number` to `string`
- [x] `useMarkReadMutation.ts` - Changed `notificationId` param from `number` to `string`

**Tests:**
- [x] `NotificationServiceTest.java` - Updated method calls for new signatures

### Real-time New Comments Banner (2026-01-18)
Implemented planner-centric SSE for real-time "X new comments" banner:

**Architecture:**
- New SSE system separate from user SSE: `plannerId → [devices]` instead of `userId → [devices]`
- Works for guests (no authentication required)
- Author's device excluded from receiving their own comment events

**Backend - New Files:**
- [x] `PlannerCommentSseService.java` - Planner-centric SSE registry with subscribe/unsubscribe/broadcast
- [x] `PlannerCommentSseController.java` - Public `GET /api/planner/{plannerId}/comments/events` endpoint

**Backend - Modified Files:**
- [x] `SecurityConfig.java` - Added permitAll for `/api/planner/{plannerId}/comments/events`
- [x] `RateLimitConfig.java` - Added `checkPlannerCommentSseLimit(UUID deviceId)` for device-based rate limiting
- [x] `CommentController.java` - Added `@DeviceId` parameter to `createComment()` and `createReply()`
- [x] `CommentService.java` - Accept `deviceId`, call `broadcastCommentAdded(plannerId, deviceId)` after save

**Frontend - New Files:**
- [x] `usePlannerCommentsSse.ts` - Hook that subscribes to planner comment feed, returns `{ newCommentsCount, resetCount }`

**Frontend - Modified Files:**
- [x] `CommentSection.tsx` - Uses `usePlannerCommentsSse` hook, `handleRefresh` invalidates comments query

**Event Flow:**
1. User opens planner → frontend connects to `/api/planner/{id}/comments/events`
2. Someone posts comment → backend broadcasts `comment:added` to all subscribers except author's device
3. Frontend increments `newCommentsCount` → banner appears
4. User clicks banner → query invalidates, banner resets

### Notification Schema Fix (2026-01-18)
Fixed Zod schema mismatch causing "Invalid notifications response from server" error:
- [x] `NotificationSchemas.ts` - Added missing rich content fields: `plannerId`, `plannerTitle`, `commentSnippet`, `commentPublicId`

### CommentActionButtons Mobile Dropdown (2026-01-18)
Responsive refactor for comment action buttons:
- [x] `CommentActionButtons.tsx` - Desktop: inline buttons (sm+), Mobile: hamburger dropdown menu
- [x] Removed `Flag` import and made `onReport` optional (report feature temporarily disabled)

### Comment API Response Restructure (2026-01-17)
Major refactor to hide internal IDs and build tree server-side:

**Backend:**
- [x] `V027__add_comment_public_id.sql` - Added UUID to `planner_comments` and `users`
- [x] `PlannerComment.java` - Added `publicId` UUID field with `@PrePersist` generation
- [x] `User.java` - Added `publicId` UUID field with `@PrePersist` generation
- [x] `CommentTreeNode.java` - New hierarchical DTO with minimal fields:
  - `id` (UUID), `content`, `authorAssoc`, `authorSuffix`, `isAuthor`, `updatedAt`, `isUpdated`, `isDeleted`, `upvoteCount`, `hasUpvoted`, `authorNotificationsEnabled`, `replies`
- [x] `CommentService.java` - `getCommentTree()` builds tree server-side, prunes deleted without children
- [x] `CommentController.java` - Returns `List<CommentTreeNode>`

**Frontend:**
- [x] `CommentSchemas.ts` - Recursive `CommentNodeSchema` with new fields
- [x] `CommentTypes.ts` - Updated `CommentNode` interface
- [x] `useCommentsQuery.ts` - Returns tree directly from API
- [x] `useCommentMutations.ts` - Uses UUID string for commentId
- [x] `CommentSection.tsx` - Uses `isAuthenticated` boolean instead of `currentUserId`
- [x] `CommentCard.tsx` - Uses `comment.isAuthor`, i18n for author association
- [x] `CommentActionButtons.tsx` - Uses `comment.isAuthor` instead of ID comparison
- [x] `CommentThread.tsx` - Uses string UUIDs
- [x] `DeletedCommentPlaceholder.tsx` - Removed date display
- [x] `useCommentTree.ts` - **Deleted** (tree built server-side now)
- [x] `useCommentTree.test.ts` - **Deleted**
- [x] `CommentSchemas.test.ts` - Updated for new schema structure

### Planner Vote Field Rename (2026-01-18)
Renamed `userVote` to `hasUpvoted` for consistency with comment voting:
- [x] `VoteResponse.java` - Changed field from `userVote` to `hasUpvoted`
- [x] `PlannerListSchemas.ts` - Changed field from `userVote` to `hasUpvoted`
- [x] `PlannerListTypes.ts` - Changed field from `userVote` to `hasUpvoted`
- [x] `usePlannerVote.ts` - Updated to use `hasUpvoted`

### Deleted Comment i18n (2026-01-18)
- [x] Added `planner.comments.deleted` i18n key (all 4 languages)
- [x] `DeletedCommentPlaceholder.tsx` - Uses i18n instead of hardcoded "[deleted]"

### Test Signature Updates (2026-01-18)
Updated tests to match new method signatures after SSE and notification changes:
- [x] `CommentServiceTest.java` - Added `PlannerCommentSseService` mock, added `deviceId` param to all `createComment` calls
- [x] `CommentServiceNotificationTest.java` - Added `PlannerCommentSseService` mock, added `deviceId` param, updated `verify` calls to 7-param signatures
- [x] `NotificationServiceTest.java` - Updated `notifyCommentReceived` and `notifyReplyReceived` calls to 7-param signatures

### Report Feature Temporarily Disabled (2026-01-18)
Admin review interface not yet implemented:
- [x] `CommentActionButtons.tsx` - Report button commented out
- [x] `CommentController.java` - Report endpoint commented out
- [x] `docs/TODO.md` - Added FEATURE-001 tracking item

### Notification Toggle Flush Fix (2026-01-18)
Toggle wasn't immediately persisted, causing race condition with replies:
- [x] `CommentService.toggleNotification()` - Changed `save()` to `saveAndFlush()` for immediate DB write
- [x] `PlannerService.toggleOwnerNotifications()` - Changed `save()` to `saveAndFlush()` for immediate DB write
- [x] `UserSettingsService.updateSettings()` - Changed `save()` to `saveAndFlush()` for global notification settings

### Comment Timestamp Enhancement (2026-01-18 Session 2)
Split single `updatedAt` field into separate `createdAt` and `updatedAt`:
- [x] `CommentTreeNode.java` - Added `createdAt` field, `updatedAt` now null if never edited
- [x] `CommentSchemas.ts` - Added `createdAt`, made `updatedAt` nullable
- [x] `CommentTypes.ts` - Added `createdAt`, made `updatedAt` nullable
- [x] `CommentCard.tsx` - Shows `createdAt` always, `(updated {{date}})` only if edited
- [x] `CommentSchemas.test.ts` - Updated fixtures for new schema
- [x] i18n: Added `updatedAt` key (all 4 languages)

### Notification System Rich Content (2026-01-18 Session 2)
Notifications now show content preview and link directly to comments (arca.live style):

**Database Migration:**
- [x] `V029__add_notification_rich_content.sql` - Added `planner_id`, `planner_title`, `comment_snippet`, `comment_public_id` columns

**Backend Entity:**
- [x] `Notification.java` - Added rich content fields with new constructor for comment notifications

**Backend DTO:**
- [x] `NotificationResponse.java` - Added `plannerId`, `plannerTitle`, `commentSnippet`, `commentPublicId` fields

**Backend Service:**
- [x] `NotificationService.notifyCommentReceived()` - New signature with 7 params (id, publicId, plannerId, title, content, owner, commenter)
- [x] `NotificationService.notifyReplyReceived()` - New signature with 7 params
- [x] `NotificationService.pushNotification()` - SSE payload includes rich content
- [x] `NotificationService.deleteAllNotifications()` - New method for bulk delete

**Backend Repository:**
- [x] `NotificationRepository.softDeleteAllByUserId()` - New method for bulk soft-delete

**Backend Controller:**
- [x] `NotificationController.java` - Added `DELETE /api/notifications/all` endpoint

**CommentService Integration:**
- [x] `CommentService.createComment()` - Passes rich content to notification service
- [x] `CommentService.createReply()` - Passes rich content to notification service

**Frontend Types:**
- [x] `NotificationTypes.ts` - Added `plannerId`, `plannerTitle`, `commentSnippet`, `commentPublicId` fields

**Frontend Components:**
- [x] `NotificationItem.tsx` - Shows plan title + comment snippet, click deletes & navigates
- [x] `NotificationDialog.tsx` - Navigation with `#comment-{uuid}` hash, "Clear all" button

### Comment Hash Scroll Support (2026-01-18 Session 2)
Direct linking to comments from notification clicks:
- [x] `CommentCard.tsx` - Added `id="comment-{uuid}"` and `scroll-mt-20` class for anchor scroll
- [x] `CommentSection.tsx` - Added `useEffect` to scroll to hash and highlight comment on load

### Mark as Read = Delete Integration (2026-01-18 Session 2)
Simplified notification UX (arca.live style):
- [x] `NotificationItem.tsx` - Removed separate "mark as read" button, click = delete + navigate
- [x] `NotificationDialog.tsx` - Removed `useMarkReadMutation`, uses delete for all actions
- [x] i18n: Added `clearAll` key, shortened notification type labels (all 4 languages)

### Planner Detail Header Button Swap (2026-01-18 Session 3)
- [x] `PlannerDetailHeader.tsx` - Swapped delete and notification toggle button positions (delete now before bell)

### Comment Indentation Fixes (2026-01-18 Session 3)
Fixed Fibonacci-like cumulative indentation and added responsive limits:
- [x] `CommentThread.tsx` - Changed from `depth * INDENT` to fixed indent per level
- [x] `CommentThread.tsx` - Added responsive depth limits (mobile: 3, desktop: 10)
- [x] `constants.ts` - Added `COMMENT_MAX_VISUAL_DEPTH_MOBILE` (3) and `COMMENT_MAX_VISUAL_DEPTH_DESKTOP` (10)
- [x] `PlannerComment.java` - Changed `MAX_DEPTH` from 5 to `Integer.MAX_VALUE` (unlimited backend depth)

### Responsive Comment Action Buttons (2026-01-18 Session 3)
Mobile-friendly dropdown menu for comment actions:
- [x] `CommentActionButtons.tsx` - Desktop (sm+): inline buttons; Mobile: hamburger dropdown menu
- [x] Upvote button always visible, other actions in dropdown on mobile

### Comment Timestamp Improvements (2026-01-18 Session 3)
Shorter format with i18n locale support:
- [x] `utils.ts` - Added `formatShortRelativeTime()` with date-fns locales (EN/JP/KR/CN)
- [x] `CommentCard.tsx` - Uses short format ("23m ago" instead of "23 minutes ago")
- [x] `CommentCard.tsx` - Added `whitespace-nowrap` to prevent timestamp line breaks

### Comment Author Name Format (2026-01-18 Session 3)
- [x] `CommentCard.tsx` - Uses `formatUsername()` for proper "Faust-{Assoc}#{suffix}" format with i18n

### Browser Notification for SSE Events (2026-01-18 Session 4)
Real-time browser push notifications when tab is not visible:

**New Files:**
- [x] `browserNotification.ts` - Utility for Web Notifications API (permission, visibility check, show notification)
- [x] `NotificationSchemas.ts` - Added `SseNotificationEventSchema` for SSE payload validation
- [x] `NotificationSchemas.ts` - Added `SsePublishedEventSchema` for planner publish broadcast

**SSE Handler:**
- [x] `useSseConnection.ts` - Parse SSE event data, show browser notification via `showBrowserNotificationForEvent()`
- [x] `useSseConnection.ts` - Notification shows title (i18n), body (plannerTitle: commentSnippet), click navigates to comment

**Permission Request Triggers:**
- [x] `NotificationSection.tsx` - Request permission when toggling ON any notification setting
- [x] `useCommentMutations.ts` - Request permission on successful comment creation
- [x] `usePlannerPublish.ts` - Request permission when publishing (not unpublishing)

**Behavior:**
- Only shows when tab is hidden (`document.hidden`)
- Click focuses tab and navigates to `/planner/md/gesellschaft/{id}#comment-{uuid}`
- Uses existing i18n keys (`notifications.types.commentReceived`, etc.)

### Comment Hash Scroll Fix (2026-01-18 Session 4)
Fixed scroll-to-comment issues when navigating from notifications:

- [x] `CommentSection.tsx` - Changed to instant scroll (was smooth with delays)
- [x] `CommentSection.tsx` - Uses `scrollIntoView({ behavior: 'instant' })` - browser scroll anchoring handles content loading above
- [x] `CommentSection.tsx` - Highlight duration reduced from 2000ms to 800ms with 500ms fade-out transition

### Copy URL Button Fix (2026-01-18 Session 4)
- [x] `CopyUrlButton.tsx` - Strip hash fragment from URL (uses `origin + pathname` instead of `href`)
- [x] Prevents long URLs with `#comment-xxx` from causing horizontal overflow in attribution

### Planner Published Notification System (2026-01-18 Session 5)
Complete notification system for new planner publications:

**Backend - Entity:**
- [x] `NotificationType.java` - Added `PLANNER_PUBLISHED` enum value

**Backend - Repository:**
- [x] `UserSettingsRepository.java` - Added `findUserIdsWithNewPublicationsEnabled()` query (respects user setting)

**Backend - Service:**
- [x] `NotificationService.java` - Added `notifyPlannerPublished()` - bulk creates DB notifications for opted-in users
- [x] `PlannerService.togglePublish()` - Calls `notifyPlannerPublished()` on first publish only

**Backend - SSE Broadcast:**
- [x] `SseService.java` - Added `broadcastToAll()` method for site-wide notifications
- [x] `PlannerService.togglePublish()` - Broadcasts `notify:published` SSE to all connected users

**Database:**
- [x] `V030__add_planner_first_published_at.sql` - Added `first_published_at` column for one-time notification
- [x] `Planner.java` - Added `firstPublishedAt` field

**Frontend - Schema/Types:**
- [x] `NotificationSchemas.ts` - Added `PLANNER_PUBLISHED` to enum, `SsePublishedEventSchema`
- [x] `NotificationTypes.ts` - Added `PLANNER_PUBLISHED` to type

**Frontend - SSE Handler:**
- [x] `useSseConnection.ts` - Added `handlePublished` callback with query invalidation and browser notification

**Frontend - UI:**
- [x] `NotificationItem.tsx` - Added `PLANNER_PUBLISHED` config (purple FileText icon)

**i18n:**
- [x] Added `notifications.types.plannerPublished` key (all 4 languages)

**Tests:**
- [x] `NotificationServiceTest.java` - Updated constructor with `UserSettingsRepository` mock
- [x] `PlannerServiceTest.java` - Updated constructor with `NotificationService` mock

**Behavior:**
- One-time only: uses `firstPublishedAt` flag, republishing doesn't re-notify
- Respects user setting: only notifies users with `notifyNewPublications = true`
- Real-time SSE: browser notification when tab hidden
- DB notifications: shows in notification inbox

### In-App Notification Toast (2026-01-18 Session 5)
Show popup in bottom-right corner when tab is focused (instead of browser notification):

**New Files:**
- [x] `NotificationToast.tsx` - Sonner-based custom toast with icon per notification type

**SSE Handler:**
- [x] `useSseConnection.ts` - Added `showNotificationForEvent()` helper with tab visibility check
- [x] `useSseConnection.ts` - Shows browser notification if tab hidden, in-app toast if visible

**Behavior:**
- Click dismisses toast and navigates to `/planner/md/gesellschaft/{id}#comment-{uuid}`
- Uses `formatUsername()` for proper author display
- Includes PLANNER_RECOMMENDED navigation (requires backend `plannerId` + `plannerTitle`)

**Backend:**
- [x] `NotificationService.notifyPlannerRecommended()` - Now includes `plannerId` and `plannerTitle` in SSE payload

### SSE Connection Stability Fixes (2026-01-18 Session 5)
Fixed SSE connections dying unexpectedly:

**Nginx Configuration:**
- [x] `nginx.conf` - Added dedicated SSE location block with:
  - `proxy_buffering off`, `proxy_cache off`, `chunked_transfer_encoding off`
  - `proxy_set_header X-Accel-Buffering no`
  - 1-hour timeout matching backend (`proxy_read_timeout 3600s`)
  - Regex pattern for all SSE endpoints: `sse/subscribe`, `planner/[^/]+/events`, `planner/[^/]+/comments/events`

**Duplicate Emitter Fix:**
- [x] `SseService.subscribe()` - Remove existing emitter for same deviceId before adding new (reconnection case)
- [x] `PlannerCommentSseService.subscribe()` - Same fix for planner-centric SSE

**Issue:** On reconnect, old emitter wasn't removed, causing heartbeat to fail on stale connection and log spurious "unsubscribed" messages.

---

## Feature Status

### Core Features
- [x] F1: Comment display (arca.live layout)
- [x] F2: Action buttons (reply/edit/delete/vote/report/noti)
- [x] F3: Comment writer (Tiptap WYSIWYG, focus-expand)
- [x] F4: Hierarchical threading (unlimited depth, visual flatten: mobile 3 / desktop 10)
- [x] F5: Notification toggle (author mutes comment, owner mutes planner)
- [x] F6: Voting system (upvote-only, immutable)
- [ ] F7: Report system (temporarily disabled - see TODO.md FEATURE-001)
- [x] F8: Deleted comment placeholder
- [x] F9: Real-time "X new comments" banner
- [x] F10: Character counter (always visible)
- [x] F11: Edit indicator "(modified)"
- [x] F12: Browser push notifications (when tab hidden)
- [x] F13: Planner publish notifications (one-time, respects user setting)

### Publish State Enforcement
- [x] P1: Published + no comments → show writer only
- [x] P2: Published + comments → full interactivity
- [x] P3: Unpublished + no comments → hide section
- [x] P4: Unpublished + comments → read-only display

### Error Handling
- [x] E1: 403 on write to unpublished
- [x] E2: 403 on reply to deleted comment
- [x] E3: 403 on vote to deleted comment
- [x] E4: 409 on duplicate vote
- [x] E5: 429 on rate limit (toast + cooldown)

---

## Testing Checklist

### Backend Tests
- [x] CommentService notification flag check
- [x] CommentController toggle endpoint (200)
- [x] CommentController report endpoint (201, 409 duplicate)

### Frontend Tests
- [x] CommentSchemas: valid CommentNode
- [x] CommentSchemas: deleted comment with empty content
- [x] CommentSchemas: updated comment (isUpdated: true)
- [x] CommentSchemas: nested replies
- [x] CommentSchemas: deeply nested replies
- [x] CommentSchemas: invalid UUID rejection
- [x] CommentVoteResponseSchema: valid/invalid
- [x] CommentReportResponseSchema: valid/invalid
- [x] CommentReportReasonSchema: enum validation

### Manual Verification
- [ ] Create comment on published planner
- [ ] Reply to comment (verify nesting)
- [ ] Reply at depth 5 (verify flatten)
- [ ] Edit own comment (verify modified indicator)
- [ ] Delete own comment (verify placeholder)
- [ ] Upvote comment (verify button disabled)
- [ ] Toggle notification (verify icon state)
- [ ] Report comment (verify button disabled)
- [ ] View unpublished planner with comments (verify read-only)
- [ ] Language switch (verify i18n)

---

## Summary

| Metric | Count |
|--------|-------|
| Total Steps | 34 |
| Steps Complete | 34 |
| Features | 13 |
| Features Implemented | 12 (1 disabled) |
| Unit Tests | 9 |
| Unit Tests Passed | 9 |
| Manual Tests | 10 |
| Manual Tests Passed | 0 |
| **Overall** | **Code Complete, Awaiting Manual Verification** |
