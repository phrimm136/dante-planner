# Spec-Driven Research: User Moderation System

## Spec Ambiguities

**NONE DETECTED.** All requirements explicit:
- Ban vs timeout distinction clear (separate fields, coexist)
- Authorization hierarchy defined (ADMIN > MODERATOR > NORMAL)
- Enforcement model specified (lazy evaluation at action time)
- User flow documented (SSE broadcast + profile refresh)
- Content policy explicit (existing content preserved)
- Audit trail scope and immutability defined

## Spec-to-Code Mapping

**Database Layer:**
- User.bannedAt, User.bannedBy fields → Add via V022 migration
- ModerationAction entity → New audit table with actionType, actorId, targetId, reason, durationMinutes, createdAt
- Indexes → target_id + created_at DESC for reason queries

**Backend Entity Layer:**
- User.java → Add bannedAt (Instant), bannedBy (Long), isBanned() method
- ModerationAction.java → New entity with ActionType enum (BAN, UNBAN, TIMEOUT, CLEAR_TIMEOUT, PROMOTE, DEMOTE)

**Backend Repository Layer:**
- ModerationActionRepository.java → New repository with findFirstByTargetIdAndActionTypeOrderByCreatedAtDesc()

**Backend Service Layer:**
- ModerationService.java → Add banUser(), unbanUser() with audit logging and SSE notifications
- PlannerService.java → Add checkUserNotBanned() enforcement (parallel to existing checkUserNotTimedOut())
- CommentService.java → Add both checkUserNotTimedOut() and checkUserNotBanned() (BUG FIX - missing timeout check)
- SseService.java → Add notifyAccountSuspended() method to broadcast to all user connections

**Backend Exception Layer:**
- UserBannedException.java → New exception class (pattern from UserTimedOutException)
- GlobalExceptionHandler.java → Add handler returning 403 with "USER_BANNED" error code

**Backend Controller Layer:**
- AuthController.java → Extend /me response with isBanned, isTimedOut, bannedAt, banReason, timeoutUntil, timeoutReason
- ModerationController.java → Add POST /ban, DELETE /ban endpoints with @PreAuthorize("ROLE_ADMIN")

**Backend DTO Layer:**
- UserProfileResponse.java → Add ban/timeout fields with @JsonInclude(NON_NULL)
- BanRequest.java → New DTO with targetId, reason
- TimeoutRequest.java → Validate durationMinutes against presets (30, 60, 180, 360, 1440, 10080, 43200)

**Frontend Hooks Layer:**
- useAuthQuery.ts → Handle new profile fields (isBanned, isTimedOut, reasons, expiry)
- useSseConnection.ts → Add account_suspended event handler (show banner, refresh profile)
- usePlannerSave.ts → Disable sync button when user.isBanned OR user.isTimedOut

**Frontend Components Layer:**
- BanStatusBanner.tsx → New dismissible banner with reason and contact email
- UserManagementPage.tsx → New page at /moderator/users (future phase)
- UserModerationTable.tsx → Paginated list with ban/timeout/promote actions (future phase)

**Frontend i18n Layer:**
- EN/common.json → Add planner.save.bannedMessage, planner.save.timedOutMessage
- KR/common.json → Add Korean translations
- JP/common.json → Add Japanese translations

## Spec-to-Pattern Mapping

**Timeout Pattern (Existing):**
- User.isTimedOut() checks timeoutUntil against Instant.now()
- Apply to: User.isBanned() checks bannedAt != null
- Enforcement: checkUserNotTimedOut() in PlannerService → duplicate for checkUserNotBanned()

**Exception Pattern (Existing):**
- UserTimedOutException structure with userId, Instant
- Apply to: UserBannedException with same structure
- Handler: GlobalExceptionHandler.handleUserTimedOut() returns 403 → duplicate for handleUserBanned()

**Service Enforcement Pattern (Existing):**
- PlannerService.checkUserNotTimedOut() before upsertPlanner()
- Apply to: Add checkUserNotBanned() call immediately after
- CommentService: Add BOTH checks (currently missing timeout - security gap)

**Audit Logging Pattern (Existing):**
- ModerationService logs actions via log.info()
- Apply to: Save to moderation_actions table with actor, target, reason, timestamp

**SSE Broadcasting Pattern (Existing):**
- SseService.notify*() methods broadcast to user connections
- Apply to: notifyAccountSuspended(userId, reason, type) broadcasts account_suspended event

**Role Hierarchy Pattern (Existing):**
- ModerationService.timeoutUser() checks actor.getRole() vs target.getRole()
- Apply to: banUser() reuses same role comparison logic (admin only, cannot ban other admins)

**Frontend Profile Pattern (Existing):**
- useAuthQuery.ts uses useSuspenseQuery on /auth/me
- Apply to: Handle new optional fields with optional chaining (user?.isBanned, user?.banReason)

**Frontend Event Pattern (Existing):**
- useSseConnection.ts registers event listeners via eventSource.addEventListener()
- Apply to: Add account_suspended listener with toast notification and queryClient.invalidateQueries()

## Pattern Enforcement

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| ModerationAction.java | User.java, PlannerComment.java | Instant createdAt, long IDs, enum fields |
| UserBannedException.java | UserTimedOutException.java | @Getter, RuntimeException, custom message |
| ModerationActionRepository.java | UserRepository.java | JpaRepository, @Query pagination |
| BanStatusBanner.tsx | NotificationItem.tsx | Dismissible state, reason display, contact link |

## Existing Utilities (Reuse)

**Formatters:**
- formatDistanceToNow (date-fns) → Use for displaying timeout expiry ("30 minutes from now")
- None needed for ban display (just show bannedAt timestamp)

**Validators:**
- TimeoutRequest constructor validates preset durations
- No frontend validation needed (backend enforces)

**Constants:**
- Add TIMEOUT_PRESETS to lib/constants.ts: [30, 60, 180, 360, 1440, 10080, 43200]

**Hooks:**
- useAuthQuery → Reuse for profile with new fields
- useSseConnection → Extend for account_suspended event
- useToast → Use for ban/timeout notification banner

## Gap Analysis

**Currently Missing:**
- User.bannedAt, User.bannedBy fields
- ModerationAction entity and repository
- UserBannedException class
- ModerationService ban/unban methods
- PlannerService.checkUserNotBanned()
- CommentService timeout/ban checks (SECURITY GAP - no checks at all)
- SseService.notifyAccountSuspended()
- UserBannedException handler in GlobalExceptionHandler
- Extended UserProfileResponse DTO
- Frontend BanStatusBanner component
- Frontend account_suspended SSE handler
- Frontend sync button disable logic

**Needs Modification:**
- User.java → Add ban fields, isBanned()
- ModerationService.java → Add ban methods, audit logging
- PlannerService.java → Add checkUserNotBanned()
- CommentService.java → Add checkUserNotTimedOut() AND checkUserNotBanned()
- GlobalExceptionHandler.java → Add UserBannedException handler
- AuthController.java → Extend /me response
- useAuthQuery.ts → Handle new fields
- useSseConnection.ts → Handle new event
- usePlannerSave.ts → Disable button when restricted

**Can Reuse:**
- User.isTimedOut() → Pattern for isBanned()
- UserTimedOutException → Pattern for UserBannedException
- ModerationService.timeoutUser() role checks → Pattern for banUser()
- PlannerService.checkUserNotTimedOut() → Pattern for checkUserNotBanned()
- SseService.notify*() → Pattern for notifyAccountSuspended()
- GlobalExceptionHandler timeout handler → Pattern for ban handler

## Testing Requirements

**Manual UI Tests:**
- Login as user, get banned by admin, verify banner appears
- Verify sync button disabled with tooltip showing ban reason
- Open multiple tabs, verify banner appears in all tabs (SSE broadcast)
- Banned user can still export/import planners (local operations)
- Banned user can view published planners and comments (read-only)
- Unban user, verify banner disappears after /me refresh

**Backend Unit Tests:**
- banUser() with role hierarchy (admin only, cannot ban other admins)
- unbanUser() clears bannedAt and bannedBy
- Audit trail logged with correct actionType, actor, target, reason
- checkUserNotBanned() throws UserBannedException for banned users
- checkUserNotBanned() succeeds for non-banned users
- Concurrent ban + timeout both block actions independently
- /me endpoint includes ban/timeout fields in response
- SSE notifyAccountSuspended() broadcasts to all user connections

**Backend Integration Tests:**
- End-to-end ban flow: create planner → ban user → try save → 403
- End-to-end timeout flow: timeout user → wait expiry → save succeeds
- Authorization: moderator cannot ban other moderator
- Audit trail: all actions logged with correct timestamps

**Frontend Unit Tests:**
- useAuthQuery handles new profile fields without errors
- BanStatusBanner renders when user.isBanned = true
- Sync button disabled when user.isBanned OR user.isTimedOut
- SSE event listener parses account_suspended event correctly
- Toast notification displays ban reason and contact email

## Technical Constraints

**Database:**
- Migration adds nullable columns (safe - no data migration)
- Audit table grows unbounded (spec acknowledges, archival policy recommended)
- Indexes on moderation_actions.target_id + created_at for performance

**Backend:**
- Role hierarchy enforced via UserRole enum comparison
- Enforcement adds one user lookup per action (Hibernate cached)
- SSE broadcast to multiple connections (acceptable overhead)

**Frontend:**
- /me polling frequency unchanged (30s interval)
- Optional chaining for new fields (backward compatible)
- SSE connection remains open after ban (read-only)

**Performance:**
- Reason query joins moderation_actions (indexed, fast)
- Enforcement check overhead minimal (cached user entity)
- SSE broadcast completes in <100ms

**Security:**
- Ban reason visible to user (per spec decision)
- Audit trail immutable (no delete operations)
- GDPR compliant (local export still works)
