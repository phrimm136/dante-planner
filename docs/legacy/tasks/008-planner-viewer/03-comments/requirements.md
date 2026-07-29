# Task: Planner Comment System

## Description

Add a hierarchical comment system to the published planner viewer. Users can comment on published planners, reply to comments (max 5 depth), and interact with comments through various actions.

### Core Features

**Comment Display (arca.live inspired layout):**
- Row 1: Username on left (`Faust-{keyword}#{suffix}`), edit date + action buttons on right
- Row 2: Content with "(modified)" indicator when edited (i18n compatible)
- Hierarchical threading like Reddit/Lobster, max 5 depth, no "view more" page
- Replies to depth-5 comments become siblings (flatten, not deeper)

**Action Buttons (Row 1, right side):**
- Reply button (authenticated users only)
- Edit button (comment owner only)
- Delete button (owner, moderator, or admin)
- Notification toggle button (comment author only - mutes replies to their comment)
- Report button (authenticated users only)

**Comment Writer (bottom of comment section):**
- WYSIWYG editor using Tiptap (reference: NoteEditor.tsx pattern)
- Initially disabled with placeholder text
- On focus: full editor expands with toolbar, Cancel/Submit buttons appear
- No draft save functionality
- Always show character counter (remaining chars while typing)

### Notification System

**Who gets notified:**
- Top-level comment → Planner owner (if `ownerNotificationsEnabled = true`)
- Reply to comment → Parent comment author (if `authorNotificationsEnabled = true`)
- Self-comments/self-replies → No notification (skip)

**Author notification controls:**
- Comment author can toggle `authorNotificationsEnabled` on their own comment (mute replies)
- Planner owner can toggle `ownerNotificationsEnabled` on their planner (mute all top-level comments)
- These are NOT for subscribers to track - they are for AUTHORS to mute their own content

**Toggle behavior (master switch):**
- Toggle is a simple ON/OFF switch affecting ALL notifications (past and future)
- No timestamp tracking or point-in-time logic
- OFF = receive nothing, ON = receive everything

**Toggle UI placement (inline):**
- Owner toggle: Bell icon button in `PlannerDetailHeader` (next to other action buttons)
- Author toggle: Bell icon button in `CommentCard` action row (with reply/edit/delete)

### Publish State & Visibility

| Planner State | Has Comments? | Comment Section | Comment Writer | Interactivity |
|---------------|---------------|-----------------|----------------|---------------|
| Published | No | Visible (empty state + writer) | Enabled | Full |
| Published | Yes | Visible | Enabled | Full |
| Unpublished | No | Hidden | Hidden | N/A |
| Unpublished | Yes | Visible (read-only) | Disabled | None (fully read-only) |

**When unpublished with existing comments:**
- Comment section visible (preserve history, read-only)
- Writer disabled with placeholder: "Comments are disabled while this planner is unpublished"
- ALL action buttons hidden (reply, edit, delete, vote, report, notification toggle)
- No interactions allowed - purely read-only display

**Backend enforcement (mandatory):**
- `createComment()` → 403 if planner not published
- `replyToComment()` → 403 if planner not published
- `upvoteComment()` → 403 if planner not published

### Deleted Comments Handling

**Soft delete only (no cascade):**
- Deleted comments keep their ID and `parentCommentId` intact
- Only `deletedAt` timestamp set and `content` cleared
- Children keep reference to deleted parent (structure preserved)

**When a comment is deleted:**
- Show "[deleted]" placeholder preserving thread structure
- Replying to the deleted comment itself is blocked (403 Forbidden)
- Replying to children of deleted comment is allowed
- Backend returns only comment ID for deleted comments, no content

**When parent comment is deleted:**
- Children remain visible with their original nesting
- Thread structure fully preserved (parent ID still valid, just marked deleted)

### Voting System

**Upvote-only (immutable):**
- Show upvote count + button on each comment
- After voting: button disabled, shows "voted" state
- Backend rejects duplicate votes with 409 Conflict
- Voting on unpublished planners: 403 Forbidden
- Voting on deleted comments: 403 Forbidden

### Report System

**New entity required: `PlannerCommentReport`**
- Fields: `id`, `commentId`, `reporterId`, `reason`, `createdAt`
- Report reasons: TBD (spam, harassment, off-topic, other)

**Moderation delete:**
- Different UI for mod/admin with reason input field
- Audit trail: who deleted, when, why

### UI/UX Details

| Feature | Decision |
|---------|----------|
| Vote button after voting | Disable + show "voted" state |
| Pagination | Load all comments at once |
| Real-time updates | "X new comments" banner between comment list and writer (all types, persist until clicked) |
| Author display | Just `Faust-{keyword}#{suffix}` (no avatar) |
| Collapse threads | No collapse (always expanded, max 5 depth manageable) |
| Sort order | Nested chronological (Reddit style - threads grouped, sorted by time within) |
| Empty state | Simple "No comments yet" text |
| Loading state | Suspense fallback with comment block skeleton |
| Character counter | Always show remaining chars while typing |
| Edit indicator | Just "(modified)" text after ANY edit (no timestamp, no diff check) |

### Sort Order: Nested Chronological

- Top-level comments sorted by creation time (oldest first)
- Replies grouped under their parent
- Within each thread, replies sorted chronologically
- New reply to old thread appears with that thread (not at bottom of page)

## Research

### Backend (already exists - verify current state)
- `entity/PlannerComment.java` - existing fields, MAX_DEPTH constant
- `service/CommentService.java` - threading logic, notification integration
- `controller/CommentController.java` - REST API endpoints
- `service/NotificationService.java` - `notifyCommentReceived()`, `notifyReplyReceived()`
- `entity/PlannerSubscription.java` - subscription pattern reference
- `config/RateLimitConfig.java` - comment rate limiting

### Frontend (patterns to follow)
- `components/noteEditor/NoteEditor.tsx` - Tiptap WYSIWYG, focus-expand pattern
- `components/plannerViewer/PlannerDetailFooter.tsx` - integration point
- `routes/PlannerMDGesellschaftDetailPage.tsx` - published planner viewer
- `hooks/usePlannerVote.ts` - immutable vote mutation pattern
- `hooks/usePlannerSubscription.ts` - toggle mutation pattern
- `lib/formatUsername.ts` - username formatting

### i18n
- `static/i18n/EN/common.json` - existing patterns
- Need new comment-specific keys in `planner.json`

## Scope

### Files to READ for context
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerComment.java`
- `backend/src/main/java/org/danteplanner/backend/service/CommentService.java`
- `backend/src/main/java/org/danteplanner/backend/controller/CommentController.java`
- `backend/src/main/java/org/danteplanner/backend/service/NotificationService.java`
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerSubscription.java`
- `frontend/src/components/noteEditor/NoteEditor.tsx`
- `frontend/src/components/plannerViewer/PlannerDetailFooter.tsx`
- `frontend/src/routes/PlannerMDGesellschaftDetailPage.tsx`
- `frontend/src/hooks/usePlannerVote.ts`
- `frontend/src/lib/formatUsername.ts`
- `docs/architecture-map.md`

## Target Code Area

### Backend Changes

**Modify:**
- `entity/PlannerComment.java` - add `authorNotificationsEnabled` field
- `entity/Planner.java` - add `ownerNotificationsEnabled` field
- `service/CommentService.java` - check notification flags before sending
- `controller/CommentController.java` - add notification toggle endpoint, publish state checks

**Create:**
- `entity/PlannerCommentReport.java` - report entity
- `repository/PlannerCommentReportRepository.java`
- `service/CommentReportService.java`
- `dto/comment/CommentReportRequest.java`
- `dto/comment/ToggleNotificationRequest.java`
- `resources/db/migration/V0XX__add_comment_notification_and_report.sql`

### Frontend Changes

**Create:**
- `components/comment/CommentSection.tsx` - main container
- `components/comment/CommentThread.tsx` - recursive thread renderer
- `components/comment/CommentCard.tsx` - single comment (arca.live layout)
- `components/comment/CommentEditor.tsx` - WYSIWYG input (NoteEditor pattern)
- `components/comment/CommentActionButtons.tsx` - reply/edit/delete/noti/report
- `components/comment/NewCommentsBar.tsx` - "X new comments" banner
- `components/comment/DeletedCommentPlaceholder.tsx`
- `hooks/useCommentsQuery.ts` - fetch comments
- `hooks/useCommentMutations.ts` - create/edit/delete/vote/report/toggleNoti
- `hooks/useCommentTree.ts` - flat → nested tree builder
- `schemas/CommentSchemas.ts` - Zod validation
- `types/CommentTypes.ts` - TypeScript types

**Modify:**
- `routes/PlannerMDGesellschaftDetailPage.tsx` - integrate CommentSection
- `static/i18n/{EN,KR,JP,CN}/planner.json` - add comment i18n keys

## System Context (Senior Thinking)

- **Feature domain**: Planner Detail (Viewer) - community planner viewing
- **Core files from architecture-map**:
  - Routes: `PlannerMDDetailPage.tsx`, `PlannerMDGesellschaftDetailPage.tsx`
  - Hooks: `usePublishedPlannerQuery.ts`, `usePlannerSubscription.ts`
  - Components: `PlannerDetailHeader.tsx`, `PlannerDetailFooter.tsx`
  - Backend: `CommentController.java`, `CommentService.java`, `NotificationService.java`
- **Cross-cutting concerns**:
  - Authentication (action button visibility)
  - i18n (all text strings)
  - Real-time updates (SSE for new comments)
  - Rate limiting (comment creation)
  - Validation (comment content length)

## Impact Analysis

### Files being modified (from architecture-map)

| File | Impact Level | What Depends On It |
|------|--------------|-------------------|
| `PlannerComment.java` | Medium | CommentService, CommentController |
| `Planner.java` | High | All planner operations |
| `CommentService.java` | Medium | CommentController |
| `NotificationService.java` | Medium | Comment, Reply, Recommendation notifications |
| `PlannerMDGesellschaftDetailPage.tsx` | Low | Page-isolated |

### Potential ripple effects
- Adding field to `Planner.java` requires migration but no functional change to existing code
- Notification flag checks must be carefully placed to not break existing flow
- New SSE event types for real-time comment updates may affect `SseService`

### High-impact files to watch
- `Planner.java` - entity change affects all planner queries
- `NotificationService.java` - notification logic affects multiple features

## Risk Assessment

### Edge cases identified
- Orphaned replies when parent deleted (show at original depth with indicator)
- Depth-5 reply flattening (becomes sibling, uses parent's parentId)
- Self-comment/self-reply (skip notification, prevent infinite loops)
- Voting on deleted comment (backend rejects with 409)
- Reply to deleted top-level (forbidden, 409)
- Reply to children of deleted parent (allowed)
- User deletion (comments reassigned to sentinel user id=0)
- Rate limit exceeded (429, throttle UI)

### Concurrency concerns
- Upvote uses atomic increment (`incrementUpvoteCount`)
- Notification deduplication via UNIQUE constraint
- Parent deletion cascade sets `parentCommentId = NULL`

### Performance considerations
- "Load all comments" works for typical usage (<100 comments)
- If >500 comments become common, add pagination later
- N+1 prevention via batch user loading (already in backend)

### Security considerations
- Only comment author can edit/toggle-notification
- Only owner/mod/admin can delete
- Rate limiting on create/update/delete/vote
- Publish state enforced on all write operations
- XSS: content stored as plain text, sanitize on render if HTML

### Backward compatibility
- New fields have defaults (`authorNotificationsEnabled = true`, `ownerNotificationsEnabled = true`)
- Existing comments unaffected
- Existing planners get notification enabled by default

## Testing Guidelines

### Manual UI Testing

**Basic Comment Flow:**
1. Navigate to a published planner detail page (`/planner/md/gesellschaft/{id}`)
2. Scroll to bottom of page
3. Verify comment section is visible with "No comments yet" empty state
4. Verify comment writer is visible (disabled placeholder)
5. Click on the comment writer
6. Verify editor expands with toolbar and Cancel/Submit buttons
7. Type "Test comment" in the editor
8. Verify character counter shows remaining characters
9. Click Submit
10. Verify comment appears in the list with your username and "just now" timestamp
11. Verify action buttons visible: Edit, Delete, Notification toggle
12. Verify Reply button is visible (you're authenticated)

**Reply Flow:**
1. Click Reply on an existing comment
2. Verify reply editor opens (inline or modal)
3. Type "Test reply"
4. Submit the reply
5. Verify reply appears nested under parent comment (indented)
6. Verify depth indicator if applicable

**Edit Flow:**
1. Click Edit on your own comment
2. Verify editor opens with existing content
3. Modify the content
4. Submit the edit
5. Verify "(modified)" indicator appears on the comment
6. Verify content is updated

**Delete Flow:**
1. Click Delete on your own comment
2. Verify confirmation dialog appears
3. Confirm deletion
4. Verify comment shows "[deleted]" placeholder
5. Verify replies to deleted comment still visible

**Vote Flow:**
1. Find a comment you haven't voted on
2. Click upvote button
3. Verify vote count increments
4. Verify button changes to "voted" state (disabled)
5. Click upvote again
6. Verify nothing happens (button disabled) or error shown

**Notification Toggle Flow:**
1. Create a new comment
2. Click notification toggle button
3. Verify visual state changes (bell → bell-slash)
4. Have another user reply to your comment
5. Verify you do NOT receive a notification

**Unpublished Planner Flow:**
1. As planner owner, unpublish a planner with existing comments
2. Navigate to the planner detail page
3. Verify comments are visible (read-only display only)
4. Verify comment writer is disabled with "unpublished" placeholder
5. Verify ALL action buttons are hidden (reply, edit, delete, vote, report, notification toggle)
6. Verify no interactions possible - purely read-only
7. Republish the planner
8. Verify full functionality restored

**Real-time Updates Flow:**
1. Open planner detail in two browser tabs/windows
2. In Tab A, post a new comment
3. In Tab B, verify "1 new comment" banner appears between comment list and writer
4. Click the banner in Tab B
5. Verify new comment loads and banner disappears

**Max Depth Flow:**
1. Create a comment thread 5 levels deep
2. Reply to the depth-5 comment
3. Verify the new reply appears as a sibling (same depth as parent), not deeper

### Automated Functional Verification

**Comment CRUD:**
- [ ] Create: New comment appears with correct author and timestamp
- [ ] Read: Comments load with proper threading structure
- [ ] Update: Edit updates content and shows "(modified)" indicator
- [ ] Delete: Soft delete shows "[deleted]" placeholder

**Threading:**
- [ ] Replies nest under parent comment
- [ ] Max depth 5 enforced (depth-5 replies become siblings)
- [ ] Deleted parent preserves child threads
- [ ] Orphaned replies display at correct depth

**Voting:**
- [ ] Upvote increments count atomically
- [ ] Voted state persists after page refresh
- [ ] Double-vote prevented (button disabled or 409)
- [ ] Vote disabled on unpublished planners

**Notifications:**
- [ ] Top-level comment notifies planner owner
- [ ] Reply notifies parent comment author
- [ ] Self-comment/reply skips notification
- [ ] Author toggle disables notifications for their comment
- [ ] Owner toggle disables notifications for planner

**Publish State:**
- [ ] Published + no comments: Show writer only
- [ ] Published + comments: Full interactivity
- [ ] Unpublished + no comments: Hide section entirely
- [ ] Unpublished + comments: Fully read-only (no action buttons, no interactions)
- [ ] All write operations return 403 when unpublished

**Sort Order:**
- [ ] Top-level comments sorted chronologically (oldest first)
- [ ] Replies grouped under parent
- [ ] Within thread, replies sorted chronologically

### Edge Cases

- [ ] Empty state: "No comments yet" with enabled writer
- [ ] Very long comment: Character limit enforced (10,000 chars)
- [ ] Rapid submissions: Rate limit returns 429, UI shows throttle message
- [ ] Delete own comment with replies: Placeholder shown, replies preserved
- [ ] Reply to deleted comment: Blocked with 403 Forbidden
- [ ] Reply to child of deleted comment: Allowed
- [ ] Deleted user comments: Show "(deleted user)" author name
- [ ] Network error on submit: Error toast, content preserved in editor
- [ ] Concurrent edits: Last write wins, no conflict resolution

### Integration Points

- [ ] Authentication: Action buttons respect auth state
- [ ] i18n: All strings use translation keys, test language switch
- [ ] SSE: New comment events received in real-time
- [ ] Notifications: Inbox shows new comment/reply notifications
- [ ] Rate limiting: 429 response handled gracefully
- [ ] Theme: Comment components respect light/dark mode
