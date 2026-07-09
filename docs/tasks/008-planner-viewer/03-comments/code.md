# Comment System Implementation Results

## What Was Done

- Completed all 34 steps across 7 phases (Backend Data/Logic, Frontend Data/UI, Integration, i18n, Tests)
- Implemented hierarchical comment system with max visual depth (mobile: 3, desktop: 10)
- Added real-time "X new comments" banner via planner-centric SSE (`PlannerCommentSseService`)
- Implemented browser notifications (when tab hidden) and in-app toast (when tab visible)
- Added planner publish notification system (one-time, respects user settings)
- Fixed SSE connection stability (nginx config, duplicate emitter handling)
- Refactored SSE hook to use constants, added idle reset for reconnect attempts

## Files Changed

**Backend (New):**
- `entity/PlannerCommentReport.java`, `entity/PlannerSubscription.java`
- `service/PlannerCommentSseService.java`, `service/CommentReportService.java`
- `controller/PlannerCommentSseController.java`
- `dto/comment/*.java` (8 new DTOs)
- `db/migration/V025-V029__*.sql` (5 migrations)

**Backend (Modified):**
- `entity/Planner.java`, `entity/PlannerComment.java`, `entity/Notification.java`
- `service/CommentService.java`, `service/NotificationService.java`, `service/SseService.java`
- `controller/CommentController.java`, `controller/NotificationController.java`
- `config/SecurityConfig.java`, `config/RateLimitConfig.java`, `config/CorsConfig.java`

**Frontend (New):**
- `components/comment/*.tsx` (7 components)
- `components/notifications/NotificationToast.tsx`
- `hooks/useCommentsQuery.ts`, `hooks/useCommentMutations.ts`, `hooks/usePlannerCommentsSse.ts`
- `schemas/CommentSchemas.ts`, `types/CommentTypes.ts`
- `lib/browserNotification.ts`

**Frontend (Modified):**
- `hooks/useSseConnection.ts` (major refactor)
- `lib/constants.ts` (added SSE_CONNECTION, SSE_EVENTS)
- `routes/PlannerMDGesellschaftDetailPage.tsx`
- `components/plannerViewer/PlannerDetailHeader.tsx`
- `static/i18n/{EN,KR,JP,CN}/*.json`

**Infrastructure:**
- `nginx/nginx.conf` (SSE endpoint location block)

## Verification Results

- Phase 1 (Backend Data): PASS - Flyway migrations executed
- Phase 2 (Backend Logic): PASS - `./mvnw test` passes
- Phase 3 (Frontend Data): PASS - TypeScript compiles
- Phase 4 (Frontend UI): PASS - Components render
- Phase 5 (Integration): PASS - Comment flow works
- Phase 6 (i18n): PASS - All 4 languages
- Phase 7 (Tests): PASS - Unit tests pass
- Code Review: NEEDS WORK → Fixed (constants, race conditions, connection limits)

## Issues & Resolutions

- Notification duplicate constraint → Changed `content_id` to new comment ID, added `REQUIRES_NEW` transaction
- Comment delete 500 error → Changed `softDelete()` to set `content = ""` (NOT NULL column)
- SSE `ERR_INCOMPLETE_CHUNKED_ENCODING` → Added nginx SSE location block with buffering disabled
- SSE reconnection duplicates → Remove existing emitter for same deviceId before adding new
- Magic numbers in SSE hook → Moved to `constants.ts` as `SSE_CONNECTION` and `SSE_EVENTS`
- Browser notification + toast both showing → Added `isTabHidden()` check for exclusive display
