# Research: Planner Headers, Footers, and Action Buttons

## Clarifications Resolved

| Question | Decision |
|----------|----------|
| Route design | **Separate routes**: `/planner/md/gesellschaft/$id` for published view, `/planner/md/$id` for personal |
| Delete flow | **Auto-unpublish then delete**: Single action, backend handles sequence |
| Auto-subscribe | **Auto-subscribe on publish**: Owner automatically subscribed, can toggle off later |

---

## Spec-to-Code Mapping

| Requirement | Target File | Action |
|-------------|-------------|--------|
| Published planner route | `lib/router.tsx` | Add new route `/planner/md/gesellschaft/$id` |
| Published detail page | New: `routes/PlannerMDGesellschaftDetailPage.tsx` | Full header with stats/author/actions |
| Personal detail page | Modify: `routes/PlannerMDDetailPage.tsx` | Minimal header with status badge |
| Header component | New: `components/plannerViewer/PlannerDetailHeader.tsx` | Props-based variant (published/personal) |
| Footer component | New: `components/plannerViewer/PlannerDetailFooter.tsx` | Engagement buttons for published view |
| Overflow dropdown | New: `components/plannerViewer/PlannerDetailDropdown.tsx` | Delete action in menu |
| Subscription hook | New: `hooks/usePlannerSubscription.ts` | Toggle mutation |
| Report hook | New: `hooks/usePlannerReport.ts` | Silent submit mutation |
| Delete hook | New: `hooks/usePlannerDelete.ts` | Auto-unpublish + soft delete |
| Backend subscription | New entity, repo, service, controller methods | CRUD for planner_subscription |
| Backend report | New entity, repo, service, controller methods | Create-only for planner_report |
| Database migration | New Flyway migrations | Tables: planner_subscription, planner_report |

---

## Pattern Enforcement

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `usePlannerSubscription.ts` | `hooks/usePlannerVote.ts` | Mutation structure, invalidation, error handling |
| `usePlannerReport.ts` | `hooks/usePlannerBookmark.ts` | Toggle mutation, silent failure |
| `usePlannerDelete.ts` | `hooks/usePlannerFork.ts` | Mutation with navigation callback |
| `PlannerDetailHeader.tsx` | `components/plannerList/PlannerCardContextMenu.tsx` | Responsive buttons, dropdown structure |
| `PlannerDetailFooter.tsx` | `components/plannerList/PlannerCardContextMenu.tsx` | Button variants, auth gating |
| `PlannerMDGesellschaftDetailPage.tsx` | `routes/PlannerMDDetailPage.tsx` | Page structure, data loading |
| Backend subscription entity | `entity/PlannerVote.java` | Composite key pattern, timestamps |
| Backend report entity | `entity/Notification.java` | User reference, content reference |

---

## Existing Utilities to Reuse

| Utility | Location | Usage |
|---------|----------|-------|
| Vote mutation | `hooks/usePlannerVote.ts` | Pattern for subscription hook |
| Fork mutation | `hooks/usePlannerFork.ts` | Reuse for duplicate (rename label) |
| Bookmark mutation | `hooks/usePlannerBookmark.ts` | Pattern for report hook |
| Auth check | `hooks/useAuthQuery.ts` | Gate buttons by auth state |
| Context menu | `components/plannerList/PlannerCardContextMenu.tsx` | Dropdown + button patterns |
| Button component | `components/ui/button.tsx` | Variants: outline, ghost, destructive |
| Toast | `sonner` library (check existing usage) | Success/error notifications |

---

## Gap Analysis

**Missing - Must Create:**
- Route: `/planner/md/gesellschaft/$id`
- Page: `PlannerMDGesellschaftDetailPage.tsx`
- Component: `PlannerDetailHeader.tsx`
- Component: `PlannerDetailFooter.tsx`
- Component: `PlannerDetailDropdown.tsx`
- Hook: `usePlannerSubscription.ts`
- Hook: `usePlannerReport.ts`
- Hook: `usePlannerDelete.ts`
- Hook: `usePublishedPlannerQuery.ts` (fetch single published planner)
- Backend: PlannerSubscription entity + repo + service
- Backend: PlannerReport entity + repo + service
- Backend: DELETE endpoint with auto-unpublish
- Backend: POST subscribe/unsubscribe endpoints
- Backend: POST report endpoint
- Flyway: V022 migration for new tables
- i18n: New keys for all button labels and toasts

**Modify:**
- `lib/router.tsx` - Add gesellschaft detail route
- `routes/PlannerMDDetailPage.tsx` - Add minimal personal header
- `PlannerController.java` - New endpoints
- `PlannerService.java` - Subscription/report/delete logic
- `static/i18n/{lang}/planner.json` - Button labels

**Reuse As-Is:**
- `usePlannerFork.ts` - Duplicate action (rename label only)
- `usePlannerVote.ts` - Footer upvote button
- `useAuthQuery.ts` - Auth gating
- `Button` variants - All styling needs covered

---

## Backend Endpoints Needed

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/planner/md/published/{id}` | Public | Fetch single published planner |
| POST | `/api/planner/md/{id}/subscribe` | Auth | Toggle subscription |
| POST | `/api/planner/md/{id}/report` | Auth | Submit report (rate limited) |
| DELETE | `/api/planner/md/{id}` | Owner | Auto-unpublish + soft delete |

---

## Database Tables Needed

**planner_subscription:**
- user_id (FK, PK)
- planner_id (FK, PK)
- enabled (boolean, default true)
- created_at (timestamp)

**planner_report:**
- id (PK, auto)
- user_id (FK)
- planner_id (FK)
- created_at (timestamp)
- UNIQUE(user_id, planner_id) - prevent duplicate reports

---

## Testing Requirements

**Manual UI Tests:**
- Published header displays author, date, stats, actions
- Personal header displays "Last edited", status badge
- Bell icon toggles filled/outline on click
- Copy URL places correct URL in clipboard
- Delete shows confirmation, removes planner
- Duplicate creates new planner, redirects to edit
- Report shows toast, disables button
- Upvote fills icon, disables button (immutable)
- Mobile: buttons show icon-only

**Automated Tests:**
- Subscription toggle mutation success/error
- Report rate limiting (429 on spam)
- Delete cascade to subscriptions
- Auth gating on all protected actions
- Route params parsed correctly

---

## Technical Constraints

- Rate limit report endpoint (RateLimitConfig.java pattern)
- Cascade delete subscriptions when planner deleted
- Auto-subscribe owner when publishing (PlannerService.togglePublish)
- Fork/Duplicate must NOT copy subscriptions
- i18n: "Duplicate" not "Fork" for translation friendliness
- Mobile: `sr-only` text for icon-only buttons (accessibility)
