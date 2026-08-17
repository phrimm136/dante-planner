# Execution Status: Planner Headers, Footers, and Action Buttons

## Execution Progress

- **Last Updated**: 2026-01-17
- **Current Step**: 32/32
- **Current Phase**: Complete

### Milestones

- [x] **M1**: Database schema deployed (Step 1.1)
- [x] **M2**: Backend complete (Steps 2.1-3.7)
- [x] **M3**: Frontend hooks complete (Steps 4.1-4.6)
- [x] **M4**: Components and routes integrated (Steps 5.1-6.6)
- [x] **M5**: All tests passing (Steps 7.1-7.4)

### Step Log

| Phase | Step | Status |
|-------|------|--------|
| 1 | 1.1 V025 migration | ✅ |
| 2 | 2.1 PlannerSubscription entity | ✅ |
| 2 | 2.2 PlannerSubscriptionId | ✅ |
| 2 | 2.3 PlannerReport entity | ✅ |
| 2 | 2.4 PlannerSubscriptionRepository | ✅ |
| 2 | 2.5 PlannerReportRepository | ✅ |
| 3 | 3.1 PlannerSubscriptionService | ✅ |
| 3 | 3.2 PlannerReportService | ✅ |
| 3 | 3.3 PlannerService modification | ✅ |
| 3 | 3.4 RateLimitConfig modification | ✅ |
| 3 | 3.5 PlannerController endpoints | ✅ |
| 3 | 3.6 SubscriptionResponse DTO | ✅ |
| 3 | 3.7 ReportResponse DTO | ✅ |
| 4 | 4.1 usePlannerSubscription | ✅ |
| 4 | 4.2 usePlannerReport | ✅ |
| 4 | 4.3 usePlannerDelete | ✅ |
| 4 | 4.4 usePublishedPlannerQuery | ✅ |
| 4 | 4.5 Schema updates | ✅ |
| 4 | 4.6 Type updates | ✅ |
| 5 | 5.1 PlannerDetailHeader | ✅ |
| 5 | 5.2 PlannerDetailFooter | ✅ |
| 5 | 5.3 PlannerDetailDropdown | ✅ |
| 5 | 5.4 CopyUrlButton | ✅ |
| 5 | 5.5 DeleteConfirmDialog | ✅ |
| 6 | 6.1 Router update | ✅ |
| 6 | 6.2 PlannerMDGesellschaftDetailPage | ✅ |
| 6 | 6.3 PlannerMDDetailPage modification | ✅ |
| 6 | 6.4-6.6 i18n updates | ✅ |
| 7 | 7.1 PlannerSubscriptionServiceTest | ✅ |
| 7 | 7.2 PlannerReportServiceTest | ✅ |
| 7 | 7.3 PlannerServiceTest additions | ✅ |
| 7 | 7.4 Frontend hook tests | ✅ |

---

## Feature Status

### Core Features

- [ ] **F1**: Published header (author, date, stats, actions)
- [ ] **F2**: Personal header (status badge, minimal layout)
- [ ] **F3**: Footer engagement buttons (upvote, duplicate, report)
- [ ] **F4**: Notification toggle (bell icon)
- [ ] **F5**: Delete with auto-unpublish
- [ ] **F6**: Copy URL button
- [ ] **F7**: Report submission (silent toast)

### Edge Cases

- [ ] **E1**: Guest sees no action buttons (only Back, Title, Stats)
- [ ] **E2**: Owner from gesellschaft sees Edit/Delete, not Duplicate/Report
- [ ] **E3**: Already subscribed shows filled bell
- [ ] **E4**: Already voted shows disabled upvote
- [ ] **E5**: Already reported hides/disables Report button
- [ ] **E6**: Mobile shows icon-only buttons

### Integration Points

- [ ] **I1**: SSE subscription reflects in notification system
- [ ] **I2**: Delete cascades subscriptions, preserves reports
- [ ] **I3**: Fork/Duplicate does NOT copy subscriptions
- [ ] **I4**: Report endpoint rate limited

---

## Testing Checklist

### Automated Tests

**Backend (JUnit):**
- [ ] `PlannerSubscriptionServiceTest.java`
- [ ] `PlannerReportServiceTest.java`
- [ ] `PlannerControllerTest.java` (new endpoints)

**Frontend (Vitest):**
- [ ] `usePlannerSubscription.test.ts`
- [ ] `PlannerDetailHeader.test.tsx`

### Manual Verification

- [ ] Navigate to published planner - full header displays
- [ ] Navigate to personal planner - minimal header displays
- [ ] Click bell icon - toggles filled/outline
- [ ] Click Copy URL - clipboard contains correct URL
- [ ] Click Delete - confirmation appears, planner removed
- [ ] Click Duplicate - new planner created, redirect to edit
- [ ] Click Report - toast shows "Report submitted"
- [ ] Click Upvote - icon fills, button disables
- [ ] Resize to mobile - buttons show icon-only

---

## Summary

| Metric | Value |
|--------|-------|
| Steps | 0/32 |
| Features | 0/7 |
| Edge Cases | 0/6 |
| Integrations | 0/4 |
| Tests | 0/5 |
| **Overall** | 0% |
