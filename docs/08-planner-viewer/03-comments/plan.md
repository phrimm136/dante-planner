# Comment System Execution Plan

## Execution Overview

Backend changes are minimal (2 entity fields + 2 new entities + 1 endpoint + migration). Frontend is the bulk of work (7 components, 3 hooks, types/schemas, i18n).

**Critical Path**: Migration → Entities → Services → Hooks → Components → Integration

---

## Dependency Analysis

### Files Being Modified

| File | Impact | Depends On | Used By |
|------|--------|------------|---------|
| `Planner.java` | High | V026 migration | All planner operations |
| `PlannerComment.java` | Medium | V026 migration | CommentService, CommentResponse |
| `CommentService.java` | Medium | Entity changes | CommentController |
| `CommentController.java` | Medium | CommentService | Frontend hooks |
| `CommentResponse.java` | Medium | PlannerComment | Frontend types |
| `PlannerMDGesellschaftDetailPage.tsx` | Low | All FE components | Page-isolated |

### High-Risk Modifications

| File | Risk | Mitigation |
|------|------|------------|
| `Planner.java` | Entity affects all queries | Default `true` ensures backward compatibility |
| `NotificationService.java` | Affects multiple features | Only add flag checks, don't modify existing |

---

## Execution Order

### Phase 1: Backend Data Layer

1. `V026__add_comment_notifications_and_reports.sql` - Add columns + report table
2. `PlannerComment.java` - Add `authorNotificationsEnabled` field (default true)
3. `Planner.java` - Add `ownerNotificationsEnabled` field (default true)
4. `PlannerCommentReport.java` - New entity
5. `PlannerCommentReportRepository.java` - New repository

### Phase 2: Backend Logic Layer

6. `CommentResponse.java` - Add `authorNotificationsEnabled` to DTO
7. `dto/comment/ToggleNotificationRequest.java` - New DTO
8. `dto/comment/CommentReportRequest.java` - New DTO
9. `dto/comment/CommentReportResponse.java` - New DTO
10. `CommentReportService.java` - Report business logic
11. `CommentService.java` - Check notification flags before notify
12. `CommentController.java` - Add toggle + report endpoints

### Phase 3: Frontend Data Layer

13. `types/CommentTypes.ts` - Comment, CommentNode, AuthorInfo
14. `schemas/CommentSchemas.ts` - Zod validation schemas
15. `hooks/useCommentsQuery.ts` - Fetch comments (useSuspenseQuery)
16. `hooks/useCommentTree.ts` - Flat → nested tree builder
17. `hooks/useCommentMutations.ts` - Create/edit/delete/vote/report/toggle

### Phase 4: Frontend UI Layer

18. `components/comment/CommentEditor.tsx` - Simplified NoteEditor
19. `components/comment/DeletedCommentPlaceholder.tsx` - [deleted] display
20. `components/comment/CommentActionButtons.tsx` - Action row
21. `components/comment/CommentCard.tsx` - arca.live layout
22. `components/comment/CommentThread.tsx` - Recursive renderer
23. `components/comment/NewCommentsBar.tsx` - Real-time banner
24. `components/comment/CommentSection.tsx` - Main container

### Phase 5: Integration

25. `PlannerDetailHeader.tsx` - Owner notification toggle (bell icon)
26. `PlannerMDGesellschaftDetailPage.tsx` - Add CommentSection

### Phase 6: i18n

27. `static/i18n/EN/planner.json` - English keys
28. `static/i18n/KR/planner.json` - Korean keys
29. `static/i18n/JP/planner.json` - Japanese keys
30. `static/i18n/CN/planner.json` - Chinese keys

### Phase 7: Tests

31. `CommentServiceTest.java` - Notification flag checks
32. `CommentControllerTest.java` - New endpoints
33. `useCommentTree.test.ts` - Tree builder (empty, nested, orphaned)
34. `CommentSchemas.test.ts` - Schema validation

---

## Verification Checkpoints

| After | Verify |
|-------|--------|
| Phase 1 | Flyway migration runs, columns exist in DB |
| Phase 2 | `./mvnw test` passes, endpoints work via curl |
| Phase 3 | TypeScript compiles, no type errors |
| Phase 4 | Components render without crashes |
| Phase 5 | Full comment flow works on published planner |
| Phase 6 | Language switch preserves UI |
| Phase 7 | All tests pass |

---

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Migration failure | Test locally first, write reversible SQL |
| Notification regression | Add flag checks only, don't modify existing flow |
| Tree builder bugs | Comprehensive unit tests before UI |
| Tiptap crash | ErrorBoundary wrap (NoteEditor pattern) |

---

## Pre-Implementation Validation

**Before Phase 1:**
- [ ] Read V025 migration for pattern
- [ ] Verify next migration number is V026
- [ ] Verify current entity state

**Before Phase 3:**
- [ ] Backend endpoints accessible
- [ ] Response structure matches DTO

**Before Phase 5:**
- [ ] All components render
- [ ] All hooks return expected shapes

---

## Rollback Strategy

| Phase | Rollback |
|-------|----------|
| Phase 1 | Delete migration, drop columns (dev only) |
| Phase 2-4 | Delete new files, revert modified |
| Phase 5 | Revert page integration |
| Phase 6 | Revert i18n changes |
