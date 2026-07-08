# Execution Plan: Planner Headers, Footers, and Action Buttons

## Execution Overview

Two-tier header/footer system for planner detail pages:
- **Phase 1**: Database schema for subscriptions and reports
- **Phase 2**: Backend entities and repositories
- **Phase 3**: Backend services and controller endpoints
- **Phase 4**: Frontend mutation hooks
- **Phase 5**: Frontend components (header, footer, dropdown)
- **Phase 6**: Route integration and page modifications
- **Phase 7**: Tests (backend + frontend)

**Total Steps**: 32 | **Phases**: 7

---

## Dependency Analysis

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| `PlannerService.java` | HIGH | Repositories, User entity | All planner endpoints, SSE |
| `PlannerController.java` | MEDIUM | PlannerService | Frontend API calls |
| `lib/router.tsx` | HIGH | All route pages | Navigation across app |
| `PlannerMDDetailPage.tsx` | LOW | useSavedPlannerQuery | None (leaf page) |
| `RateLimitConfig.java` | HIGH | Bucket4j | All rate-limited endpoints |

### Ripple Effect Map

- `PlannerService.togglePublish()` modified → Frontend publish flow unchanged (already handles response)
- New route in `router.tsx` → Must register in routeTree
- `PlannerSubscription` entity → Must cascade delete when planner deleted
- `PlannerReport` entity → Should NOT cascade (reports preserved for moderation)

### High-Risk Modifications

| File | Risk | Mitigation |
|------|------|------------|
| `PlannerService.java` | Central business logic | Add new methods only; minimal modify to existing delete |
| `lib/router.tsx` | All navigation flows here | Add route at end; follow existing pattern |
| `RateLimitConfig.java` | Affects all endpoints | Add new bucket only; don't modify existing |

---

## Execution Order

### Phase 1: Database Schema

| Step | Action | Enables |
|------|--------|---------|
| 1.1 | CREATE `V025__add_planner_subscriptions_and_reports.sql` | All backend features |

### Phase 2: Backend Entities & Repositories

| Step | Action | Depends On |
|------|--------|------------|
| 2.1 | CREATE `entity/PlannerSubscription.java` | 1.1 |
| 2.2 | CREATE `entity/PlannerSubscriptionId.java` | 1.1 |
| 2.3 | CREATE `entity/PlannerReport.java` | 1.1 |
| 2.4 | CREATE `repository/PlannerSubscriptionRepository.java` | 2.1, 2.2 |
| 2.5 | CREATE `repository/PlannerReportRepository.java` | 2.3 |

### Phase 3: Backend Services & Controller

| Step | Action | Depends On |
|------|--------|------------|
| 3.1 | CREATE `service/PlannerSubscriptionService.java` | 2.4 |
| 3.2 | CREATE `service/PlannerReportService.java` | 2.5 |
| 3.3 | MODIFY `PlannerService.java` - auto-unpublish on delete, auto-subscribe on publish | 3.1 |
| 3.4 | MODIFY `config/RateLimitConfig.java` - add report bucket | None |
| 3.5 | MODIFY `PlannerController.java` - add endpoints | 3.1, 3.2, 3.3, 3.4 |
| 3.6 | CREATE `dto/planner/SubscriptionResponse.java` | None |
| 3.7 | CREATE `dto/planner/ReportResponse.java` | None |

### Phase 4: Frontend Hooks

| Step | Action | Depends On |
|------|--------|------------|
| 4.1 | CREATE `hooks/usePlannerSubscription.ts` | 3.5 |
| 4.2 | CREATE `hooks/usePlannerReport.ts` | 3.5 |
| 4.3 | CREATE `hooks/usePlannerDelete.ts` | 3.5 |
| 4.4 | CREATE `hooks/usePublishedPlannerQuery.ts` | 3.5 |
| 4.5 | MODIFY `schemas/PlannerListSchemas.ts` - add response schemas | None |
| 4.6 | MODIFY `types/PlannerListTypes.ts` - add types | 4.5 |

### Phase 5: Frontend Components

| Step | Action | Depends On |
|------|--------|------------|
| 5.1 | CREATE `components/plannerViewer/PlannerDetailHeader.tsx` | 4.1, 4.6 |
| 5.2 | CREATE `components/plannerViewer/PlannerDetailFooter.tsx` | 4.1, 4.2, 4.3 |
| 5.3 | CREATE `components/plannerViewer/PlannerDetailDropdown.tsx` | 4.3 |
| 5.4 | CREATE `components/plannerViewer/CopyUrlButton.tsx` | None |
| 5.5 | CREATE `components/plannerViewer/DeleteConfirmDialog.tsx` | None |

### Phase 6: Route Integration

| Step | Action | Depends On |
|------|--------|------------|
| 6.1 | MODIFY `lib/router.tsx` - add gesellschaft detail route | None |
| 6.2 | CREATE `routes/PlannerMDGesellschaftDetailPage.tsx` | 4.4, 5.1, 5.2, 6.1 |
| 6.3 | MODIFY `routes/PlannerMDDetailPage.tsx` - add personal header | 5.1 |
| 6.4 | MODIFY `static/i18n/EN/planner.json` | None |
| 6.5 | MODIFY `static/i18n/KR/planner.json` | None |
| 6.6 | MODIFY `static/i18n/JP/planner.json` | None |

### Phase 7: Tests

| Step | Action | Depends On |
|------|--------|------------|
| 7.1 | CREATE `PlannerSubscriptionServiceTest.java` | 3.1 |
| 7.2 | CREATE `PlannerReportServiceTest.java` | 3.2 |
| 7.3 | MODIFY `PlannerControllerTest.java` | 3.5 |
| 7.4 | CREATE `usePlannerSubscription.test.ts` | 4.1 |
| 7.5 | CREATE `PlannerDetailHeader.test.tsx` | 5.1 |

---

## Verification Checkpoints

| After Step | Verification Method |
|------------|---------------------|
| 1.1 | Run Flyway migration - tables created |
| 3.5 | curl/Postman: test subscribe, report, published detail endpoints |
| 4.4 | TypeScript compiles without errors |
| 5.5 | Storybook or isolation: render header/footer variants |
| 6.2 | Navigate to `/planner/md/gesellschaft/{id}` - full header renders |
| 6.3 | Navigate to `/planner/md/{id}` - minimal header renders |

---

## Risk Mitigation

| Risk | Step | Mitigation |
|------|------|------------|
| PlannerService breaks existing | 3.3 | Add methods only; add unpublish before delete |
| Route conflict | 6.1 | TanStack Router specificity: longer path wins |
| Subscription spam | 3.1 | Rate limit: 10 ops/min |
| Report spam | 3.2, 3.4 | Rate limit: 3/hour + one per user per planner |
| Delete cascade | 1.1 | DB-level cascade for subscriptions; reports preserved |

---

## Pre-Implementation Validation

| Check | Status |
|-------|--------|
| Route design confirmed | ✅ Separate routes |
| Delete flow confirmed | ✅ Auto-unpublish then delete |
| Auto-subscribe confirmed | ✅ Owner on publish |
| Fork reusable for Duplicate | ✅ Relabel only |
| Migration number | ✅ V025 |
| Pattern files identified | ✅ All mapped in research.md |

---

## Rollback Strategy

| Point | Action |
|-------|--------|
| After Phase 1 | Rollback V025 migration |
| After Phase 3 | Backend deployable independently |
| After Phase 5 | Components exist unused |
| After Phase 6 | Full rollback: revert all in reverse |

---

## Pattern Files Reference

| New File | Copy Pattern From |
|----------|-------------------|
| `usePlannerSubscription.ts` | `hooks/usePlannerVote.ts` |
| `usePlannerReport.ts` | `hooks/usePlannerBookmark.ts` |
| `usePlannerDelete.ts` | `hooks/usePlannerFork.ts` |
| `PlannerDetailHeader.tsx` | `components/plannerList/PlannerCardContextMenu.tsx` |
| `PlannerSubscription.java` | `entity/PlannerVote.java` |
| `PlannerReport.java` | `entity/Notification.java` |
