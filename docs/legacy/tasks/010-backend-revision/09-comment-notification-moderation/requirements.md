# Task: Immutable Voting System + Notifications + Admin Moderation

## Description

Implement three interconnected systems to prevent voting abuse, notify users of important events, and enable admin content curation:

### 1. Immutable Voting System (Maximum Immutability)
Users can vote ONCE on planners and comments. Votes are **completely immutable** - no changes, no cancellation, no switching between UP/DOWN. This is the maximum security policy against voting manipulation.

**Voting Behavior:**
- User clicks upvote/downvote button → Shows confirmation warning ("Votes are permanent and cannot be changed")
- User confirms → Vote created, counter increments
- Both vote buttons become disabled (cannot vote again in any direction)
- Button shows "✓ Upvoted" or "✓ Downvoted" based on user's vote
- Attempting to vote again (any direction) → Returns HTTP 409 Conflict error
- Vote counters ONLY increment, never decrement
- User account deletion → Votes reassigned to sentinel user (id=0), preserving counts

**Mutual Exclusivity:**
- If user voted UP: Cannot vote DOWN (409 Conflict)
- If user voted DOWN: Cannot vote UP (409 Conflict)
- If user voted UP: Cannot remove vote (409 Conflict)
- Vote type is permanent from creation moment

**Database Changes:**
- Remove soft-delete infrastructure from vote tables (`deleted_at`, `updated_at` columns)
- Keep only: user_id, planner_id/comment_id, vote_type, created_at
- Votes have no setters (enforce immutability at entity level)
- No vote type change method (immutable field)

### 2. Notification System
Notify users when important events occur related to their content.

**Notification Types:**
- `PLANNER_RECOMMENDED`: User's planner crossed upvote threshold (becomes "hot post")
- `COMMENT_RECEIVED`: Someone commented on user's planner
- `REPLY_RECEIVED`: Someone replied to user's comment
- `REPORT_RECEIVED`: User's content was reported (admin/moderator only)

**User Interactions (Header Dialog, NOT Separate Page):**
- **Location**: Notification bell icon in header, LEFT of language dropdown button
- **UI**: Dialog popup (not full page route)
- Click bell → Dialog opens with notification list (max 20 recent, scrollable)
- Notification shows: Type icon, Title, Timestamp (relative), Read/Unread indicator
- Click notification → Navigate to related content (planner/comment), dialog closes
- "Mark all as read" button at dialog footer
- Dismiss individual notification (swipe or X button) → Soft-delete
- Unread count badge on bell icon (red dot with number)
- Dialog width: 400px, max-height: 600px, positioned below bell icon

**Business Rules:**
- Notifications deduplicated via UNIQUE constraint (user_id, content_id, notification_type)
- Recommended notification fires ONLY on threshold transition (net votes 9→10), not repeated crossings
- Atomic database flag prevents race condition duplicate notifications
- Comment notifications: Don't notify if comment author = planner owner
- Reply notifications: Don't notify if reply author = parent comment author
- Cleanup job: Soft-delete read notifications after 90 days, hard-delete after 1 year

### 3. Admin Moderation System (arca.live Pattern)
Admins can manually curate the "Recommended" list by hiding specific planners WITHOUT deleting votes or content.

**Moderation Actions:**
- Admin marks planner as "hidden from recommended" with reason (e.g., "Suspected bot voting")
- Planner removed from `/api/planner/md/recommended` public list
- Vote counts unchanged (transparency - users see real vote numbers)
- Planner still accessible via direct link
- Owner can still view/edit planner
- Admin can unhide planner to restore to recommended list

**Admin Dashboard:**
- "Recommended Review" tab: Lists current recommended planners with vote analytics
- "Hidden Planners" tab: Lists all hidden planners with hide reason, admin, timestamp
- Suspicious pattern detection: Automated job flags planners with unusual voting patterns
  - >80% votes from accounts <24 hours old
  - Vote bursts (10+ votes within 5 minutes)
  - Zero engagement (high votes but no comments/views)

### 4. Planner Lifecycle Clarification
Users have two separate actions for managing published planners:

**Unpublish (NEW endpoint):**
- Sets `published = false`
- Hides from public lists
- Owner can still edit
- Reversible (can re-publish anytime)
- Use case: User wants to edit planner privately

**Delete (existing endpoint):**
- Sets `deleted_at = NOW()` (soft-delete)
- Hides from all lists (personal + public)
- Votes remain intact
- Not reversible by user (admin recovery only)
- Use case: User wants to permanently remove planner

**UI Flow:**
- Planner detail page: "⋮ More Options" dropdown
  - If published: "Unpublish" option
  - If unpublished: "Publish" option
  - Always: "Delete..." option (requires typing planner title to confirm)

## Research

### Existing Patterns to Follow
- **Optimistic locking**: Study `PlannerCommentVote.java` `@Version` pattern (line 46-48)
- **Composite key with Persistable**: Study `PlannerCommentVote.java` implementation (lines 22-71)
- **Soft-delete pattern**: Study `Planner.java` `softDelete()` method (lines 125-127)
- **Atomic counters**: Study `PlannerRepository` `incrementUpvotes()`, `decrementUpvotes()` methods
- **User deletion integration**: Study `UserAccountLifecycleService` patterns for reassigning content
- **Rate limiting**: Study `RateLimitConfig.java` Bucket4j patterns
- **Header dialog pattern**: Study existing Header.tsx dropdowns (Language, User) for dialog positioning
- **Existing vote API response**: Study `PlannerCardContextMenu.tsx` lines 135-144 (current toggle behavior to REMOVE)

### Current Vote API Behavior (BREAKING CHANGE)
**Current Implementation (TO BE REMOVED):**
```typescript
// PlannerCardContextMenu.tsx lines 135-142
const handleUpvote = () => {
  const newVote = planner.userVote === 'UP' ? null : 'UP'  // Toggle: UP → null
  voteMutation.mutate({ plannerId: planner.id, voteType: newVote })
}

const handleDownvote = () => {
  const newVote = planner.userVote === 'DOWN' ? null : 'DOWN'  // Toggle: DOWN → null
  voteMutation.mutate({ plannerId: planner.id, voteType: newVote })
}
```

**New Implementation (Maximum Immutability):**
```typescript
const handleVote = (voteType: 'UP' | 'DOWN') => {
  if (planner.userVote !== null) {
    // Already voted - show error, do NOT allow toggle or change
    toast.error("You've already voted on this planner. Votes are permanent.");
    return;
  }

  // First vote only
  voteMutation.mutate({ plannerId: planner.id, voteType });
}
```

**API Parameter Change:**
- **Before**: `voteType: 'UP' | 'DOWN' | null` (null = remove vote)
- **After**: `voteType: 'UP' | 'DOWN'` (null NOT allowed, 400 Bad Request)

### Database Atomicity Patterns
- Investigate MySQL `UPDATE ... SET field = value WHERE id = ? AND condition IS NULL` for atomic flag setting
- Review JPA `@Modifying` query patterns for atomic operations
- Study composite UNIQUE constraints for deduplication

### Frontend State Management
- Review TanStack Query cache invalidation patterns for notification updates
- Study existing error handling for 409 Conflict responses
- Review localStorage patterns for "warning shown" flags
- Study Header.tsx dropdown positioning (align="end") for notification dialog

### Admin Role/Permission System
- Identify existing `ROLE_ADMIN` or `ROLE_MODERATOR` role definitions
- Review `@PreAuthorize` patterns in existing controllers
- Check if admin role infrastructure exists or needs creation

## Scope

### Files to READ for Context

#### Backend Architecture
- `docs/architecture-map.md` - System overview, file dependencies
- `backend/CLAUDE.md` - Backend development patterns

#### Existing Vote System (CRITICAL - Understanding Current Toggle Behavior)
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerVote.java` - Current vote entity
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerCommentVote.java` - Composite key pattern
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerVoteRepository.java`
- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java` - Vote casting logic (castVote method)
- `frontend/src/components/plannerList/PlannerCardContextMenu.tsx` - Current toggle implementation (lines 134-144)
- `frontend/src/hooks/usePlannerVote.ts` - Vote mutation hook
- `frontend/src/types/PlannerListTypes.ts` - `userVote` field (line 99)
- `frontend/src/schemas/PlannerListSchemas.ts` - `userVote` schema (line 58)

#### Existing Comment System
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerComment.java`
- `backend/src/main/java/org/danteplanner/backend/service/CommentService.java`
- `backend/src/main/java/org/danteplanner/backend/dto/comment/CommentResponse.java` - `hasUpvoted` field (line 24)

#### Planner Entity & Repository
- `backend/src/main/java/org/danteplanner/backend/entity/Planner.java` - Will add notification flag, moderation fields
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerRepository.java` - Will add atomic methods

#### User Lifecycle
- `backend/src/main/java/org/danteplanner/backend/service/UserAccountLifecycleService.java` - User deletion patterns

#### Exception Handling
- `backend/src/main/java/org/danteplanner/backend/exception/GlobalExceptionHandler.java`
- `backend/src/main/java/org/danteplanner/backend/exception/*Exception.java` - Pattern for custom exceptions

#### Configuration
- `backend/src/main/resources/application.properties` - Rate limiting, threshold values
- `backend/src/main/java/org/danteplanner/backend/config/RateLimitConfig.java`

#### Frontend Header & Dialog Patterns
- `frontend/src/components/Header.tsx` - Existing dropdown patterns (Language, User)
- `frontend/src/components/ui/dialog.tsx` - shadcn Dialog component
- `frontend/src/components/ui/dropdown-menu.tsx` - Dropdown positioning reference

## Target Code Area

### Backend - Database Migrations (NEW)
- `backend/src/main/resources/db/migration/V019__make_votes_immutable.sql`
  - Drop `deleted_at`, `updated_at` from vote tables
  - Migration strategy for existing soft-deleted votes (discard them)
- `backend/src/main/resources/db/migration/V020__create_notifications_table.sql`
  - Create notifications table with indexes, UNIQUE constraint
- `backend/src/main/resources/db/migration/V021__add_moderation_fields.sql`
  - Add `hidden_from_recommended`, `hidden_by_admin_id`, `hidden_reason`, `hidden_at` to planners
- `backend/src/main/resources/db/migration/V022__add_atomic_flags.sql`
  - Add `version` (optimistic locking) to planners
  - Add `recommended_notified_at` (atomic notification flag) to planners

### Backend - Entities (MODIFY + NEW)
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerVote.java` (MODIFY)
  - Remove `deletedAt`, `updatedAt`, `version` fields
  - Remove `softDelete()`, `reactivate()`, `isDeleted()` methods
  - Remove ALL setters (enforce immutability)
  - Make `voteType` final (cannot be changed after construction)
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerCommentVote.java` (MODIFY)
  - Same changes as PlannerVote
- `backend/src/main/java/org/danteplanner/backend/entity/Planner.java` (MODIFY)
  - Add `@Version private Long version;`
  - Add `private Instant recommendedNotifiedAt;`
  - Add `private Boolean hiddenFromRecommended = false;`
  - Add `private Long hiddenByAdminId;`
  - Add `private String hiddenReason;`
  - Add `private Instant hiddenAt;`
- `backend/src/main/java/org/danteplanner/backend/entity/Notification.java` (NEW)
- `backend/src/main/java/org/danteplanner/backend/entity/NotificationType.java` (NEW - enum)

### Backend - Repositories (MODIFY + NEW)
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerVoteRepository.java` (MODIFY)
  - Add `@Modifying @Query void reassignUserVotes(Long userId, Long sentinelUserId);`
  - Remove soft-delete query methods
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerCommentVoteRepository.java` (MODIFY)
  - Same pattern as PlannerVoteRepository
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerRepository.java` (MODIFY)
  - Add `@Modifying @Query int trySetRecommendedNotified(UUID plannerId, int threshold);`
  - Modify `findRecommendedPlanners()` to filter `hiddenFromRecommended = false`
- `backend/src/main/java/org/danteplanner/backend/repository/NotificationRepository.java` (NEW)
  - Extends JpaRepository<Notification, Long>
  - Query methods for inbox, mark-read, delete, unread count

### Backend - Services (MODIFY + NEW)
- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java` (MODIFY)
  - Update `castVote()`: Check vote existence → throw 409, NO toggle logic, NO vote type change
  - Reject `voteType = null` with 400 Bad Request
  - Add threshold transition detection with atomic flag
  - Add `unpublishPlanner()` method
  - Integrate `notificationService.notifyPlannerRecommended()`
- `backend/src/main/java/org/danteplanner/backend/service/CommentService.java` (MODIFY)
  - Update `castCommentVote()`: Same pattern as PlannerService (maximum immutability)
  - Integrate `notificationService.notifyCommentReceived()` in createComment()
  - Integrate `notificationService.notifyReplyReceived()` for replies
- `backend/src/main/java/org/danteplanner/backend/service/UserAccountLifecycleService.java` (MODIFY)
  - Update `performHardDelete()`: Call `reassignUserVotes()` for both vote repositories
- `backend/src/main/java/org/danteplanner/backend/service/NotificationService.java` (NEW)
  - Create, read, mark-read, delete notifications
  - Methods: `notifyPlannerRecommended()`, `notifyCommentReceived()`, `notifyReplyReceived()`, `notifyReportReceived()`
  - Scheduled cleanup job `@Scheduled cleanupOldNotifications()`
- `backend/src/main/java/org/danteplanner/backend/service/ModerationService.java` (NEW)
  - `hideFromRecommended()`, `unhideFromRecommended()`, `listHiddenPlanners()`
  - Scheduled pattern detection `@Scheduled detectSuspiciousVoting()`

### Backend - Controllers (MODIFY + NEW)
- `backend/src/main/java/org/danteplanner/backend/controller/PlannerController.java` (MODIFY)
  - Add `PUT /api/planner/{id}/unpublish` endpoint
  - Modify vote endpoint to reject `voteType = null` (400 Bad Request)
- `backend/src/main/java/org/danteplanner/backend/controller/NotificationController.java` (NEW)
  - `GET /api/notifications/inbox` (page, size params, returns list only - no separate page count needed for dialog)
  - `POST /api/notifications/{id}/mark-read`
  - `POST /api/notifications/mark-all-read`
  - `DELETE /api/notifications/{id}`
  - `GET /api/notifications/unread-count`
- `backend/src/main/java/org/danteplanner/backend/controller/AdminModerationController.java` (NEW)
  - `POST /api/admin/planner/{id}/hide-from-recommended`
  - `POST /api/admin/planner/{id}/unhide-from-recommended`
  - `GET /api/admin/planner/hidden`

### Backend - DTOs (MODIFY + NEW)
- `backend/src/main/java/org/danteplanner/backend/dto/notification/NotificationResponse.java` (NEW)
- `backend/src/main/java/org/danteplanner/backend/dto/notification/NotificationInboxResponse.java` (NEW)
- `backend/src/main/java/org/danteplanner/backend/dto/notification/UnreadCountResponse.java` (NEW)
- `backend/src/main/java/org/danteplanner/backend/dto/moderation/ModerationResponse.java` (NEW)
- `backend/src/main/java/org/danteplanner/backend/dto/moderation/HidePlannerRequest.java` (NEW)
- `backend/src/main/java/org/danteplanner/backend/dto/planner/VoteRequest.java` (MODIFY)
  - Change validation: `voteType` NOT NULLABLE, only 'UP' or 'DOWN'
  - Add validation error message: "Vote type must be UP or DOWN. Vote removal not allowed."

### Backend - Exceptions (NEW)
- `backend/src/main/java/org/danteplanner/backend/exception/VoteAlreadyExistsException.java`
  - Mapped to HTTP 409 Conflict in GlobalExceptionHandler
  - Message: "You have already voted on this content. Votes are permanent and cannot be changed."

### Frontend - Components (NEW + MODIFY)
- `frontend/src/components/notifications/NotificationDialog.tsx` (NEW) - Dialog triggered from header bell
- `frontend/src/components/notifications/NotificationItem.tsx` (NEW) - Single notification in list
- `frontend/src/components/notifications/NotificationIcon.tsx` (NEW) - Type-based icon (trophy, message, reply, alert)
- `frontend/src/components/Header.tsx` (MODIFY) - Add notification bell button LEFT of language dropdown
- `frontend/src/components/plannerList/PlannerCardContextMenu.tsx` (MODIFY) - Remove toggle logic (lines 134-144)
- `frontend/src/components/admin/RecommendedPlannerList.tsx` (NEW) - Admin review tab
- `frontend/src/components/admin/HiddenPlannerList.tsx` (NEW) - Admin hidden planners tab
- `frontend/src/components/admin/VotingAnalytics.tsx` (NEW) - Vote distribution charts
- `frontend/src/components/admin/HideReasonModal.tsx` (NEW) - Reason input modal

### Frontend - Routes (NEW - Admin Only)
- `frontend/src/routes/AdminModerationPage.tsx` (NEW) - `/admin/moderation` page (admin dashboard)

### Frontend - Hooks (NEW + MODIFY)
- `frontend/src/hooks/useNotificationsQuery.ts` (NEW)
- `frontend/src/hooks/useUnreadCountQuery.ts` (NEW)
- `frontend/src/hooks/useMarkReadMutation.ts` (NEW)
- `frontend/src/hooks/useDeleteNotificationMutation.ts` (NEW)
- `frontend/src/hooks/useHideFromRecommendedMutation.ts` (NEW)
- `frontend/src/hooks/useUnhideFromRecommendedMutation.ts` (NEW)
- `frontend/src/hooks/usePlannerVote.ts` (MODIFY) - Remove toggle logic, add pre-vote warning state

### Frontend - Types (NEW)
- `frontend/src/types/NotificationTypes.ts`

### Frontend - Schemas (NEW)
- `frontend/src/schemas/NotificationSchemas.ts`

### Documentation (MODIFY)
- `docs/architecture-map.md` - Add notification system, moderation patterns
- `backend/CLAUDE.md` - Document immutable vote pattern, atomic operations
- `frontend/CLAUDE.md` - Document notification dialog pattern, breaking vote API change

## System Context (Senior Thinking)

### Feature Domains (from architecture-map.md)
This task spans multiple domains:

**Primary Domains:**
1. **Vote System** (Backend Domain)
   - Core files: `entity/PlannerVote.java`, `service/PlannerService.java`, `controller/PlannerController.java`
   - Pattern: Composite key with Persistable interface (PlannerCommentVote.java lines 22-23)
   - **Breaking change**: Removing vote toggle/removal functionality

2. **Notification System** (NEW - Backend Domain, Cross-cutting)
   - Will become new core domain
   - Cross-cuts: User management, Planner publishing, Comment system
   - **Frontend integration**: Header component (cross-cutting UI element)

3. **Moderation System** (NEW - Admin Domain)
   - Overlaps with: Planner publishing, Vote system
   - Admin-only endpoints (new security boundary)

### Cross-Cutting Concerns
From architecture-map.md "Cross-Cutting Concerns":

**Authentication & Authorization:**
- Notification endpoints: Authenticated users only
- Admin moderation: Requires `ROLE_ADMIN` or `ROLE_MODERATOR`
- Location: `config/SecurityConfig.java`

**Rate Limiting:**
- Vote endpoint: Already rate-limited (inherit existing pattern)
- Notification endpoints: 60 requests/minute per user (new limit)
- Location: `config/RateLimitConfig.java` (Bucket4j)

**Exception Handling:**
- New exception: `VoteAlreadyExistsException` → 409 Conflict
- Pattern: Follow `GlobalExceptionHandler.java` (architecture-map line 703)

**Validation:**
- Vote requests: Add validation `voteType NOT NULL`
- Notification requests: Pagination validation (page ≥ 0, size 1-100)
- Pattern: Jakarta validation in DTOs

**Frontend State Management:**
- Notification dialog: Local state (open/close)
- Notification list: TanStack Query cache
- Unread count: Polling query (30-second refetch)
- Pattern: Follow existing Header dropdown state management

### Existing Patterns to Reuse

**Header Dropdown Pattern** (Header.tsx lines 169-198):
```tsx
<DropdownMenu>
  <DropdownMenuTrigger asChild>
    <Button variant="ghost" size="icon">
      <Languages />
    </Button>
  </DropdownMenuTrigger>
  <DropdownMenuContent align="end">
    {/* Content */}
  </DropdownMenuContent>
</DropdownMenu>
```

**Apply to Notification Dialog:**
- Use Dialog instead of DropdownMenu (scrollable list, not menu items)
- Trigger: Button with Bell icon + badge
- Position: `align="end"` (right-aligned)
- Width: 400px, max-height: 600px

**Soft-Delete Pattern** (architecture-map line 72-88):
- `Notification.deletedAt` for user dismissal
- Query filters: `WHERE deleted_at IS NULL`

**Atomic Counter Pattern** (architecture-map line 677-678):
- `plannerRepository.incrementUpvotes()` uses SQL `UPDATE ... SET upvotes = upvotes + 1`
- Apply to: Vote counter increments (already exists, keep using)

**Batch Loading (N+1 Prevention)** (architecture-map line 283-289):
- `CommentService.getComments()` batch-loads users
- Apply to: Notification inbox (load related planners/comments in batch)

## Impact Analysis

### Breaking Changes

**CRITICAL BREAKING CHANGE: Vote API Parameter**
- **Before**: `POST /api/planner/{id}/vote` accepts `{ voteType: 'UP' | 'DOWN' | null }`
- **After**: `POST /api/planner/{id}/vote` accepts `{ voteType: 'UP' | 'DOWN' }` (null → 400 Bad Request)

**Frontend Impact:**
- All vote buttons that implement toggle logic MUST be updated
- Files affected:
  - `PlannerCardContextMenu.tsx` (lines 134-144) - Remove toggle logic
  - `usePlannerVote.ts` - Update mutation to reject null voteType
  - Any other components with vote buttons (search codebase for `voteType === 'UP' ? null`)

**Backend Impact:**
- `VoteRequest.java` DTO validation: `@NotNull VoteType voteType`
- `PlannerService.castVote()`: Validate `voteType != null`, throw 400 if null
- Existing tests: Update tests that test vote removal (now should expect 409 or 400)

### Files Being Modified - Impact Levels

**HIGH IMPACT** (from architecture-map "High-Impact Files"):
- `service/PlannerService.java` - All planner CRUD depends on this
  - Change: `castVote()` logic (existing method) - **BREAKING CHANGE**
  - Risk: Breaking vote functionality if not careful
- `repository/PlannerRepository.java` - Query methods used across app
  - Change: Add atomic methods, modify recommended query
  - Risk: Performance impact if queries not optimized
- `components/Header.tsx` - Global component, appears on every page
  - Change: Add notification bell button
  - Risk: Breaking header layout on mobile

**MEDIUM IMPACT:**
- `entity/Planner.java` - Planner representation
  - Change: Add moderation fields, version field, notification flag
  - Risk: Entity mapping issues, existing data migration
- `entity/PlannerVote.java`, `PlannerCommentVote.java` - Vote entities
  - Change: Remove soft-delete infrastructure
  - Risk: Breaking existing vote queries if soft-delete still referenced
- `service/CommentService.java` - Comment CRUD
  - Change: Add notification integration
  - Risk: Breaking comment creation flow
- `controller/PlannerController.java` - REST endpoints
  - Change: Add unpublish endpoint, modify vote validation
  - Risk: Breaking existing vote API clients

**LOW IMPACT:**
- All NEW files (NotificationService, NotificationController, etc.)
  - No existing dependencies to break

### Dependencies from File Dependency Graph

**PlannerService Dependencies** (architecture-map lines 674-689):
```
PlannerService
  ├── PlannerRepository (incrementUpvotes, findRecommendedPlanners)
  ├── PlannerVoteRepository (vote persistence)
  ├── UserRepository (owner verification)
  ├── ContentVersionValidator (validation)
  └── (NEW) NotificationService (threshold notifications)
```

**Header Component Dependencies:**
- Used by: All pages (GlobalLayout wrapper)
- Related components: HeaderNav (Desktop/Mobile), DropdownMenu
- Impact: Changes affect every page in application

### Ripple Effects

**From Maximum Vote Immutability:**
- `PlannerService.castVote()`: Logic drastically simplifies (only check existence)
- Frontend vote buttons: MUST disable both UP and DOWN after any vote
- User deletion: Must reassign votes (new integration point)
- Vote count queries: Must remove `WHERE deleted_at IS NULL` filters
- **Frontend components**: All vote toggle logic must be removed (search for `? null :` pattern)

**From Notification System:**
- PlannerService: Add notification call on threshold cross
- CommentService: Add notification call on comment/reply creation
- Header component: Add notification bell (global UI change)
- Performance: Notification inbox queries will be frequent (optimize with indexes)
- Mobile layout: Notification dialog must be responsive

**From Moderation System:**
- Recommended query: Add `hidden_from_recommended = false` filter
- Admin dashboard: New frontend routes, components (separate build)
- Audit trail: Admin actions must be logged (verify AuditService exists)

## Risk Assessment

### Edge Cases from Architectural Research

**Maximum Vote Immutability - User Experience (CRITICAL):**
- **Risk:** User accidentally upvotes, realizes they wanted to downvote, cannot change
- **Mitigation:** STRONG pre-vote warning modal with explicit confirmation
- **Warning text:** "⚠️ Votes are PERMANENT. Once you choose UP or DOWN, you cannot change your vote or remove it. Are you sure you want to vote [UP/DOWN]?"
- **Confirmation:** Require clicking "I Understand" button (not just "OK")
- **Edge case to test:** User clicks vote, dismisses warning, state should reset (button re-enabled)

**Vote Immutability - User Deletion:**
- **Risk:** Deleting user breaks foreign key on votes
- **Mitigation:** Reassign votes to sentinel user (id=0)
- **Edge case to test:** Delete user with 50 votes, verify counts preserved, verify planner shows "Deleted User" as voter

**Notification Deduplication:**
- **Risk:** Planner crosses threshold multiple times (impossible with immutable votes, but defensive coding)
- **Mitigation:** `recommended_notified_at` flag (atomic) + UNIQUE constraint
- **Edge case to test:** Concurrent votes crossing threshold, verify single notification

**Race Condition - Concurrent Voting:**
- **Risk:** Two users vote simultaneously on threshold-1 planner, both try to notify
- **Mitigation:** Atomic `trySetRecommendedNotified()` query
- **Edge case to test:** Simulate concurrent votes with threading test

**Notification Dialog - Mobile Layout:**
- **Risk:** 400px dialog too wide for mobile screen
- **Mitigation:** Responsive width (max-width: 90vw on mobile)
- **Edge case to test:** Open dialog on 320px screen (iPhone SE)

**Header Bell Icon - Unread Badge Overlap:**
- **Risk:** Badge number >99 overlaps bell icon
- **Mitigation:** Show "99+" for counts >99
- **Edge case to test:** User with 150 unread notifications

## Testing Guidelines

### Manual UI Testing - Voting Flow (Maximum Immutability)

#### Pre-Vote Warning (CRITICAL UX)
1. Log in as test user (no previous votes on planner)
2. Navigate to published planner detail page or gesellschaft list
3. Click "Upvote" button
4. **Verify:** Strong warning modal appears:
   - Title: "⚠️ Votes are PERMANENT"
   - Message: "Once you choose UP or DOWN, you cannot change your vote or remove it. Are you sure you want to UPVOTE this planner?"
   - Buttons: "Cancel" (outline) | "I Understand" (primary)
5. Click "Cancel"
6. **Verify:** Modal closes, vote not recorded, button still enabled
7. Click "Upvote" button again
8. Click "I Understand" in modal
9. **Verify:** Vote recorded, "✓ Upvoted" button disabled
10. **Verify:** "Downvote" button ALSO disabled (grayed out)

#### Vote Immutability - No Toggle
1. On same planner (already upvoted)
2. **Verify:** Both vote buttons are disabled
3. Attempt to click "Downvote" button
4. **Verify:** Click has no effect (button disabled)
5. Open browser console, manually call API: `POST /api/planner/{id}/vote` with `{ voteType: "DOWN" }`
6. **Verify:** API returns 409 Conflict with message "You have already voted on this content"
7. **Verify:** Toast notification shows: "You've already voted. Votes are permanent."

#### Vote Removal Blocked
1. Open browser console, manually call API: `POST /api/planner/{id}/vote` with `{ voteType: null }`
2. **Verify:** API returns 400 Bad Request with message "Vote type must be UP or DOWN. Vote removal not allowed."

### Manual UI Testing - Notification Dialog

#### Dialog Layout & Positioning
1. Log in as test user
2. **Verify:** Bell icon appears in header, LEFT of language dropdown
3. **Verify:** Unread count badge shows "0" or number
4. Click bell icon
5. **Verify:** Dialog opens below bell icon, aligned to right edge
6. **Verify:** Dialog width ~400px on desktop
7. Resize browser to mobile width (375px)
8. **Verify:** Dialog width adjusts to screen (max-width: 90vw)
9. **Verify:** Dialog has scrollbar if notifications exceed height

#### Notification List Display
1. With notifications in inbox
2. **Verify:** Each notification shows:
   - Icon (trophy for recommended, message for comment, reply for reply)
   - Title text (e.g., "Your planner 'Strategy' was recommended")
   - Timestamp (relative: "5 minutes ago", "2 hours ago")
   - Read/unread indicator (unread = bold or highlighted background)
3. **Verify:** Max 20 notifications shown (scrollable if more)
4. Click a notification
5. **Verify:** Navigate to related content (planner detail page)
6. **Verify:** Dialog closes after click
7. **Verify:** Notification marked as read (background changes, bold → normal)

#### Mark All as Read
1. With multiple unread notifications
2. Click "Mark all as read" button at dialog footer
3. **Verify:** All notifications marked as read (visual change)
4. **Verify:** Unread count badge updates to "0"

#### Dismiss Notification
1. Hover over notification item
2. **Verify:** "X" button appears on right side
3. Click "X" button
4. **Verify:** Notification removed from list
5. Close and reopen dialog
6. **Verify:** Dismissed notification not shown

### Manual UI Testing - Admin Moderation

#### Hide from Recommended
1. Log in as admin user
2. Navigate to `/admin/moderation`
3. Click "Recommended Review" tab
4. **Verify:** List of planners with net votes ≥ threshold
5. For a planner, click "Vote Analytics" button
6. **Verify:** Chart showing account age distribution of voters
7. Click "Hide from Recommended" button
8. **Verify:** Modal prompts for reason
9. Enter reason: "Suspected bot voting - 80% new accounts"
10. Click "Confirm"
11. **Verify:** Planner moved to "Hidden Planners" tab
12. Log out, navigate to gesellschaft recommended page
13. **Verify:** Hidden planner not in list
14. Navigate to planner via direct link
15. **Verify:** Planner still accessible, vote counts visible, NO "hidden" indicator

### Automated Functional Verification

#### Maximum Vote Immutability
- [ ] **Vote creation succeeds**: First vote on planner creates vote, increments counter
- [ ] **Vote existence check**: Second vote by same user throws `VoteAlreadyExistsException` (409)
- [ ] **Vote type immutability**: User cannot change from UP to DOWN (throws 409)
- [ ] **Vote type immutability (reverse)**: User cannot change from DOWN to UP (throws 409)
- [ ] **Vote removal blocked (API)**: `voteType: null` throws 400 Bad Request
- [ ] **Vote removal blocked (entity)**: No setter for voteType field (compile-time enforcement)
- [ ] **Counter only increments**: Vote counter never decrements (verify with vote sequence)
- [ ] **User deletion preserves votes**: Deleting user reassigns votes to id=0, counters unchanged

#### Notification Creation
- [ ] **Threshold transition notification**: Vote crossing threshold (9→10) creates notification
- [ ] **No repeated notification**: Vote at threshold (10→11) does NOT create duplicate
- [ ] **Notification deduplication (race)**: Concurrent votes create only ONE notification
- [ ] **Comment notification**: Comment creates COMMENT_RECEIVED notification for owner
- [ ] **No self-notification (comment)**: Owner commenting doesn't notify self
- [ ] **Reply notification**: Reply creates REPLY_RECEIVED notification for parent author
- [ ] **No self-notification (reply)**: Replying to own comment doesn't notify self
- [ ] **UNIQUE constraint**: Duplicate notification silently fails (constraint violation)

#### Notification Dialog UI
- [ ] **Unread count accurate**: Badge shows correct count of unread notifications
- [ ] **Dialog opens/closes**: Click bell toggles dialog state
- [ ] **Notification click navigation**: Click notification navigates to content, closes dialog
- [ ] **Mark all as read**: Marks all notifications, updates badge to 0
- [ ] **Dismiss notification**: Sets `deleted_at`, removes from list
- [ ] **Responsive width**: Dialog width adjusts on mobile (<400px screens)

### Edge Cases

#### Vote System - Maximum Immutability
- [ ] **Double-click vote button**: Rapid double-click only creates one vote (debounce/disable on click)
- [ ] **Vote on deleted planner**: Throws `PlannerNotFoundException` (404)
- [ ] **Vote on unpublished planner**: Throws `PlannerNotFoundException` (404)
- [ ] **Deleted user's votes**: Show as "Deleted User", counters preserved
- [ ] **Sentinel user (id=0) edge case**: Cannot log in as sentinel, cannot vote as sentinel

#### Notification Dialog
- [ ] **Empty inbox**: Shows "No notifications" message
- [ ] **Unread count >99**: Badge shows "99+"
- [ ] **Notification for deleted planner**: Shows "Planner deleted" when clicked
- [ ] **Notification for deleted comment**: Shows "Comment deleted"
- [ ] **Dialog scroll behavior**: Scrollbar appears only when >20 notifications

### Integration Points

#### User Deletion Integration
- [ ] **Delete user with votes**: Votes reassigned to id=0, counts preserved
- [ ] **Delete user with notifications**: Notifications remain (or reassigned)

#### Vote → Notification Integration
- [ ] **Vote creates notification**: Vote crossing threshold triggers notification
- [ ] **Notification in same transaction**: Vote rollback also rolls back notification
- [ ] **Notification failure rolls back vote**: Atomicity verified

#### Frontend → Backend Integration
- [ ] **Vote API rejects null**: `voteType: null` returns 400 Bad Request
- [ ] **Frontend disables both buttons**: After voting, both UP and DOWN disabled
- [ ] **Frontend handles 409**: Shows "Votes are permanent" toast
- [ ] **Notification dialog polls**: Unread count refetches every 30 seconds

## Notes

### Maximum Immutability Policy
**User voted UP:**
- Cannot vote DOWN (409 Conflict)
- Cannot remove vote (409 Conflict)
- Vote is permanent

**User voted DOWN:**
- Cannot vote UP (409 Conflict)
- Cannot remove vote (409 Conflict)
- Vote is permanent

**This is stricter than typical voting systems:**
- Reddit: Can remove vote, can switch UP ↔ DOWN
- YouTube: Can remove vote, can switch like ↔ dislike
- This system: Cannot remove, cannot switch (MAXIMUM security)

### arca.live Reference Pattern
- Korean platform with manual "Best Posts" curation
- Admins hide posts from best list WITHOUT deleting
- Vote counts remain visible (transparency)
- Quality over pure algorithm

### Implementation Priority
1. **Phase 1 (Week 1)**: Immutable votes - Foundation
2. **Phase 2 (Week 2)**: Notifications - User engagement
3. **Phase 3 (Week 3)**: Admin moderation - Quality curation
4. **Phase 4 (Week 4)**: Polish, testing, documentation

### Critical Success Factors
- **Pre-vote warning**: CRITICAL UX - users MUST understand permanence
- **Atomic operations**: Race conditions prevented at DB level
- **Notification dialog UX**: Fast, responsive, mobile-friendly
- **Admin transparency**: Hidden planners still show votes

### Open Questions to Resolve
1. **Admin role infrastructure**: Does `ROLE_ADMIN` exist?
2. **Audit service**: Implement as part of moderation phase?
3. **Notification retention**: 90-day confirmed? Configurable?
4. **Mobile notification dialog**: Full-screen on mobile or keep 90vw width?
