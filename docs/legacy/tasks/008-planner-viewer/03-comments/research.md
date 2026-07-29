# Comment System Research

## Clarifications Resolved

| Ambiguity | Decision |
|-----------|----------|
| Notification toggle scope | Master switch (ON/OFF affects all, no timestamp tracking) |
| Owner toggle UI placement | Inline in PlannerDetailHeader (bell icon) |
| Author toggle UI placement | Inline in CommentCard action row (bell icon) |
| Deleted comment cascade | Soft delete only (no cascade, parent ID preserved) |
| Real-time banner content | ALL new comments (top-level + replies) |
| Real-time banner persistence | Persist until clicked (user dismisses) |
| Vote on deleted comment | 403 Forbidden |
| Reply to deleted comment | 403 Forbidden (spec updated from 409) |
| Edit indicator trigger | After ANY edit (no diff check) |

---

## Spec-to-Code Mapping

### Backend (Exists)
- Comment CRUD: `CommentService.java`, `CommentController.java`
- Threading (depth ≤ 5, flatten at 5): `CommentService.createComment()`
- Immutable voting: `CommentService.toggleUpvote()` (rename to `upvote()`)
- Publish state checks: `CommentController` checks `planner.isPublished()`
- Notifications: `NotificationService.notifyCommentReceived()`, `notifyReplyReceived()`
- Rate limiting: `RateLimitConfig.checkCommentLimit()`
- Soft delete: `PlannerComment.softDelete()` sets `deletedAt`, clears `content`

### Backend (Needs Modification)
- `PlannerComment.java`: ADD `authorNotificationsEnabled` boolean (default true)
- `Planner.java`: ADD `ownerNotificationsEnabled` boolean (default true)
- `CommentService.java`: Check notification flags before calling NotificationService
- `CommentController.java`: ADD `PATCH /comments/{id}/notifications` toggle endpoint

### Backend (New)
- `PlannerCommentReport.java`: Entity with id, commentId, reporterId, reason, createdAt
- `PlannerCommentReportRepository.java`
- `CommentReportService.java`
- `dto/comment/ToggleNotificationRequest.java`
- `dto/comment/CommentReportRequest.java`
- Migration: `V0XX__add_comment_notification_and_report.sql`

### Frontend (All New)
- `hooks/useCommentsQuery.ts`: Fetch flat comment list
- `hooks/useCommentMutations.ts`: Create/edit/delete/vote/report/toggleNoti
- `hooks/useCommentTree.ts`: Flat → nested tree builder
- `components/comment/CommentSection.tsx`: Main container
- `components/comment/CommentThread.tsx`: Recursive renderer
- `components/comment/CommentCard.tsx`: arca.live layout
- `components/comment/CommentEditor.tsx`: Tiptap WYSIWYG
- `components/comment/CommentActionButtons.tsx`: Action row
- `components/comment/NewCommentsBar.tsx`: Real-time banner
- `components/comment/DeletedCommentPlaceholder.tsx`
- `types/CommentTypes.ts`, `schemas/CommentSchemas.ts`
- i18n keys in `planner.json`

---

## Pattern Enforcement

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `useCommentsQuery.ts` | `usePublishedPlannerQuery.ts` | Suspense query, Zod validation |
| `useCommentMutations.ts` | `usePlannerVote.ts` | useMutation, 409 ConflictError handling |
| `CommentEditor.tsx` | `NoteEditor.tsx` | Focus-expand, Tiptap, toolbar, char counter |
| `CommentCard.tsx` | `PublishedPlannerCard.tsx` | Card layout with action buttons |
| `CommentTypes.ts` | `PlannerListTypes.ts` | Discriminated union for deleted vs normal |
| `CommentSchemas.ts` | `PlannerListSchemas.ts` | Zod discriminated union |

---

## Pattern Copy Analysis

### NoteEditor → CommentEditor

| Aspect | NoteEditor | CommentEditor |
|--------|------------|---------------|
| Extensions | StarterKit, Image, Spoiler | StarterKit only (no images/spoilers) |
| Focus state | `isFocused` toggles toolbar | Same pattern |
| Blur handling | `relatedTarget` check | Same pattern |
| Content sync | 500ms debounce to parent | Submit button (no auto-sync) |
| Validation | maxBytes prop | 10,000 char limit from backend |
| Buttons | None (auto-sync) | Cancel + Submit |

### usePlannerVote → useCommentVote

| Aspect | usePlannerVote | useCommentVote |
|--------|----------------|----------------|
| Endpoint | `POST /planner/md/{id}/vote` | `POST /comments/{id}/upvote` |
| Payload | `{ voteType }` | None (upvote-only) |
| Response | `VoteResponse` | `CommentVoteResponse` |
| Error 409 | ConflictError | Same |
| Invalidation | gesellschaftQueryKeys | commentsQueryKey(plannerId) |

---

## Existing Utilities

| Category | Utility | Use? |
|----------|---------|------|
| Username | `formatUsername(keyword, suffix)` | Direct use |
| API | `ApiClient.post()`, `ApiClient.get()` | Direct use |
| i18n | `useTranslation()` | Direct use |
| Toast | `sonner` toast.error/success | Direct use |
| Suspense | `useSuspenseQuery` pattern | Reference |

---

## Gap Analysis

### Critical Path (Blockers)
1. Add `authorNotificationsEnabled` to `PlannerComment.java`
2. Add `ownerNotificationsEnabled` to `Planner.java`
3. Add `PATCH /comments/{id}/notifications` endpoint
4. Create DB migration

### Frontend (No Blockers)
- All frontend work can proceed with existing backend
- Notification toggle UI blocked until backend endpoint exists
- Report feature can be deferred (hide button initially)

### Can Reuse Directly
- `formatUsername.ts` for author display
- `ApiClient` for all requests
- `sonner` for toast notifications
- `useSuspenseQuery` pattern from existing hooks
- Rate limit 429 error handling pattern

---

## Tree Builder Algorithm

**Input**: Flat `CommentResponse[]` with `parentCommentId`
**Output**: Nested `CommentNode[]` with `replies: CommentNode[]`

**Steps**:
1. Create Map of id → node (with empty replies array)
2. Link children to parents via parentCommentId
3. Orphaned comments (null parent but depth > 0) → treat as top-level
4. Sort: top-level by createdAt ASC, replies by createdAt ASC within thread

---

## Error Code Matrix

| Action | Published | Unpublished | Deleted Target |
|--------|-----------|-------------|----------------|
| Create comment | 201 | 403 | N/A |
| Reply to comment | 201 | 403 | 403 |
| Edit own comment | 200 | 403 | N/A |
| Delete own comment | 204 | 403 | N/A |
| Upvote comment | 200 | 403 | 403 |
| Duplicate upvote | 409 | 403 | 403 |
| Toggle notification | 200 | 403 | N/A |
| Report comment | 201 | 403 | 403 |

---

## Testing Requirements

### Unit Tests
- Tree builder: empty, single, nested, orphaned, max depth
- Zod schemas: valid/invalid comment responses
- Mutation hooks: success, 409, 403, 429 handling

### Integration Tests
- CommentSection + useCommentsQuery
- CommentCard + useCommentMutations
- Real-time banner + SSE events

### Manual UI Tests
- Full CRUD flow on published planner
- Unpublished → all buttons hidden
- Depth-5 reply becomes sibling
- Deleted comment shows placeholder
- Vote button disables after vote
- Notification toggle changes icon state

---

## Implementation Order

1. **Backend first**: Add fields + migration + toggle endpoint
2. **Frontend hooks**: useCommentsQuery, useCommentMutations, useCommentTree
3. **Frontend components**: CommentCard → CommentThread → CommentSection → CommentEditor
4. **Integration**: Add CommentSection to PlannerMDGesellschaftDetailPage
5. **i18n**: Add all translation keys
6. **Polish**: Real-time banner, loading skeletons, error states
