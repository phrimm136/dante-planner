# Comment System Learning Reflection

## What Was Easy

- Server-side tree building eliminated frontend complexity (no useCommentTree hook needed)
- Tiptap editor pattern already existed (NoteEditor.tsx), easy to adapt
- Backend entity modifications straightforward (notification flags, report entity)
- i18n structure consistent across 4 languages; new keys added systematically
- Vote immutability pattern already proven (usePlannerVote), replicated for comments

## What Was Challenging

- SSE connection stability required nginx buffering config and duplicate emitter cleanup
- Real-time "X new comments" required planner-centric SSE (separate from user SSE)
- Max depth flattening (depth 5 replies become siblings) needed careful parentId logic
- Notification deduplication via unique constraint required transaction scope fix (REQUIRES_NEW)
- Browser vs. in-app notification decision based on tab visibility detection

## Key Learnings

- Server-side tree building with recursive DTOs eliminates pagination concerns
- UUID exposure requires careful public_id migration pattern (separate migrations per entity)
- Soft delete + minimal response DTOs force frontend query invalidation (good pattern)
- Rich notification content requires backend enrichment at creation time, not read time
- Device-based rate limiting for SSE prevents connection exhaustion per planner
- Dual-path design: DB notifications for inbox persistence + SSE for real-time UI updates

## Spec-Driven Process Feedback

- Research.md mapping 90% accurate; useCommentTree hook correctly noted but unnecessary
- Plan.md execution order worked perfectly (migration → entities → services → hooks → components)
- Instructions provided excellent edge cases but timing specs missing (SSE backoff, cooldown)
- Status.md captured non-planned fixes methodically (CORS, SSE duplicate emitter, constraint)

## Pattern Recommendations

- Add SSE stability patterns: nginx config (buffering off), duplicate emitter cleanup, idle reset
- Document UUID public_id migration pattern: entity field + PrePersist + repository finder
- Establish soft delete pattern: clear content string (not null), preserve tree structure
- Clarify notification vs. SSE distinction in skill docs (dual-path design)

## Next Time

- Verify nginx configuration upfront for SSE endpoints (saves troubleshooting time)
- Add timing specifications to spec: backoff intervals, reconnection windows
- Test device-based rate limiting earlier in implementation
- Create integration test for unpublished planner read-only mode before marking complete
