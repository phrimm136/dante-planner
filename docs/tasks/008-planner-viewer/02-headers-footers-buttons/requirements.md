# Task: Planner Viewer Headers, Footers, and Action Buttons

## Description

Add action buttons and metadata display to the planner detail view pages. The implementation requires **two distinct header designs** based on viewing context:

### Published Planner Header (Gesellschaft/Community View)

When viewing a published planner (either your own from community or someone else's):

**Row 1 - Metadata Bar (horizontal):**
- Left side: Back button, Author username (e.g., "Faust-LCB-A1b2C"), Published date
- Right side: Edit button (owner only), Delete button (owner only), Notification toggle bell (authenticated users only)

**Row 2 - Title:**
- Planner title in large text

**Row 3 - Stats + Link:**
- Left side: View count, Upvote count, Comment count (clickable - scrolls to comments)
- Right side: Copyable URL with copy button

**Footer (below content):**
- Upvote button (authenticated users, post-consumption action)
- Duplicate button (authenticated users, not owner) - renamed from "Fork" for i18n friendliness
- Report button (authenticated users, not owner)
- Back to Top button

### Personal Planner Header (My Plans View)

When viewing your own planner from personal list:

**Row 1 - Metadata Bar (horizontal):**
- Left side: Back button, "Last edited: [date]" text
- Right side: Edit button, Delete button

**Row 2 - Title:**
- Planner title + Status badge (DRAFT / PUBLISHED / UNSYNCED)

**Footer (minimal):**
- Back to Top button only (or nothing)

### Button Design System

**Button variants by action type:**
- Primary management actions (Edit): `outline` variant, `sm` size, icon + text
- Dangerous actions (Delete): In dropdown menu with `variant="destructive"`
- Subtle/frequent actions (Notification, More): `ghost` variant, `icon-sm` size
- Footer engagement actions (Upvote, Duplicate): `outline` variant, `default` size, icon + text
- Subtle footer actions (Report): `ghost` variant, icon + text

**Responsive behavior:**
- Desktop: Icon + text for main buttons
- Mobile: Icon-only with sr-only text for accessibility

**Toggle states:**
- Notification bell: Outline when off, filled when subscribed
- Upvote: Outline when not voted, filled with primary color when voted (disabled after voting - immutable)

### Delete Behavior
- Unpublish the planner first, then soft delete
- Owner and moderator/admin can delete

### Duplicate Behavior
- Creates a copy in the user's personal drafts (unpublished)
- User is redirected to edit page of the new duplicate

### Report Behavior
- Silent acknowledgment (toast: "Report submitted")
- No visible feedback about report status to reporter

### Notification Toggle Behavior
- Owner: Default ON (auto-subscribed when publishing)
- Others: Default OFF (opt-in to watch)
- Stored in new `planner_subscription` database table

## Research

- Existing `PlannerCardContextMenu.tsx` patterns for dropdown menus and action handling
- Existing `usePlannerVote`, `usePlannerBookmark`, `usePlannerFork` hooks
- Current `PlannerMDDetailPage.tsx` structure
- shadcn/ui Button variants and DropdownMenu components
- Notification system patterns (currently disabled: `NotificationDialog.tsx.bak`, `NotificationIcon.tsx.bak`)
- Existing `useAuthQuery` for authentication checks
- Backend `PlannerService.java` for ownership validation patterns

## Scope

Files to READ for context:
- `frontend/src/components/plannerList/PlannerCardContextMenu.tsx` (action patterns)
- `frontend/src/components/ui/button.tsx` (available variants)
- `frontend/src/routes/PlannerMDDetailPage.tsx` (current detail page)
- `frontend/src/hooks/usePlannerVote.ts` (vote mutation pattern)
- `frontend/src/hooks/usePlannerFork.ts` (fork/duplicate pattern)
- `frontend/src/hooks/useAuthQuery.ts` (auth check pattern)
- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java` (ownership checks)
- `docs/architecture-map.md` (system context)

## Target Code Area

**New Frontend Components:**
- `frontend/src/components/plannerViewer/PlannerDetailHeader.tsx`
- `frontend/src/components/plannerViewer/PlannerDetailFooter.tsx`
- `frontend/src/components/plannerViewer/PlannerDetailDropdown.tsx` (overflow menu)

**New Frontend Hooks:**
- `frontend/src/hooks/usePlannerSubscription.ts` (notification toggle)
- `frontend/src/hooks/usePlannerReport.ts` (report mutation)
- `frontend/src/hooks/usePlannerDelete.ts` (delete mutation with unpublish)

**Modified Frontend:**
- `frontend/src/routes/PlannerMDDetailPage.tsx` (integrate new components)
- `frontend/src/types/PlannerTypes.ts` (add subscription types)
- `frontend/src/lib/constants.ts` (any new constants)
- `frontend/src/i18n/*/planner.json` (new translation keys)

**New Backend:**
- `backend/.../entity/PlannerSubscription.java`
- `backend/.../entity/PlannerReport.java`
- `backend/.../repository/PlannerSubscriptionRepository.java`
- `backend/.../repository/PlannerReportRepository.java`
- `backend/.../service/PlannerSubscriptionService.java`
- `backend/.../controller/PlannerController.java` (new endpoints)
- Database migration for `planner_subscription` and `planner_report` tables

## System Context (Senior Thinking)

- **Feature domain:** Planner (Viewer/Detail page)
- **Core files in this domain:**
  - `routes/PlannerMDDetailPage.tsx`
  - `components/plannerViewer/PlannerViewer.tsx`
  - `hooks/useSavedPlannerQuery.ts`
  - `controller/PlannerController.java`
  - `service/PlannerService.java`
- **Cross-cutting concerns touched:**
  - Authentication (`useAuthQuery`, `JwtAuthenticationFilter`)
  - Validation (Zod frontend, Jakarta backend)
  - i18n (translation keys for button labels)
  - Notifications (SSE integration for subscription system)
  - Rate limiting (new endpoints need rate limit config)

## Impact Analysis

**Files being modified:**
- `PlannerMDDetailPage.tsx` - Low impact (page isolated)
- `PlannerController.java` - Medium impact (add new endpoints)
- `PlannerService.java` - High impact (all planner CRUD and sync, notification integration)

**What depends on these files:**
- PlannerService is used by all planner endpoints
- New subscription table will be queried when sending notifications

**Potential ripple effects:**
- Delete endpoint must cascade to subscriptions
- Fork/Duplicate may need to NOT copy subscriptions

**High-impact files to watch:**
- `service/PlannerService.java` - core planner logic
- `config/RateLimitConfig.java` - new endpoints need rate limiting

## Risk Assessment

**Edge cases not yet defined:**
- What happens if user tries to delete while someone else is forking?
- Can owner delete a planner with active reports pending review?
- What if subscription toggle fails mid-flight on multiple devices?

**Performance concerns:**
- Subscription check on page load (need efficient query)
- Report spam potential (rate limiting required)

**Backward compatibility:**
- Fork renamed to Duplicate - existing fork hook can be reused, just relabel

**Security considerations:**
- Owner verification for edit/delete endpoints
- Rate limiting for report endpoint to prevent spam
- Subscription toggle must verify user authentication

## Testing Guidelines

### Manual UI Testing

**Published View Testing:**
1. Navigate to `/planner/md/gesellschaft`
2. Click on any published planner card
3. Verify header shows: Back button, Author name, Published date
4. Verify right side shows: Edit (if owner), Delete (if owner), Bell icon (if logged in)
5. Verify title row displays planner title
6. Verify stats row shows: View count, Upvote count, Comment count
7. Verify Copy URL button is visible on the right
8. Click Copy URL button
9. Verify toast shows "URL copied" and clipboard contains correct URL
10. Scroll to bottom of content
11. Verify footer shows: Upvote, Duplicate (if not owner), Report (if not owner), Back to Top
12. Click Back to Top button
13. Verify page scrolls to top smoothly
14. Click comment count in stats
15. Verify page scrolls to comment section

**Personal View Testing:**
1. Navigate to `/planner/md` (my plans)
2. Click on any personal planner
3. Verify header shows: Back button, "Last edited: [date]"
4. Verify right side shows: Edit, Delete (no notification bell)
5. Verify title shows with status badge (DRAFT or PUBLISHED)
6. Verify footer only has Back to Top (no Upvote/Duplicate/Report)

**Notification Toggle Testing:**
1. As logged-in user, view a published planner (not yours)
2. Click the bell icon
3. Verify bell becomes filled (subscribed state)
4. Click bell again
5. Verify bell becomes outline (unsubscribed state)
6. Refresh page
7. Verify subscription state persists

**Delete Flow Testing:**
1. As owner, view your published planner from gesellschaft
2. Click Delete button (in dropdown)
3. Verify confirmation dialog appears
4. Confirm deletion
5. Verify planner is unpublished first, then soft deleted
6. Verify redirect to list page
7. Verify planner no longer appears in gesellschaft

**Report Flow Testing:**
1. As logged-in non-owner, view a published planner
2. Click Report button in footer
3. Verify toast shows "Report submitted"
4. Verify Report button becomes disabled or hidden
5. Try clicking Report again
6. Verify duplicate report is prevented

### Automated Functional Verification

- [ ] Header variant selection: Published view shows full header, Personal view shows minimal header
- [ ] Owner detection: Edit/Delete buttons only visible to planner owner
- [ ] Auth gating: Notification toggle, Upvote, Duplicate, Report require authentication
- [ ] Not-owner gating: Duplicate and Report buttons hidden for owner viewing own planner
- [ ] Copy URL: Clipboard contains correct planner URL
- [ ] Stats display: View, Upvote, Comment counts render correctly
- [ ] Comment scroll: Clicking comment count scrolls to comment section
- [ ] Back button: Navigates to correct list page based on source
- [ ] Immutable upvote: Button disabled after voting, cannot toggle

### Edge Cases

- [ ] Guest user: Only sees Back, Title, Stats, Copy URL - no action buttons
- [ ] Owner viewing from gesellschaft: Sees Edit/Delete but not Duplicate/Report
- [ ] Non-owner viewing: Sees Duplicate/Report but not Edit/Delete
- [ ] Already subscribed: Bell shows filled state on page load
- [ ] Already voted: Upvote button shows filled and disabled state
- [ ] Already reported: Report button hidden or disabled
- [ ] Mobile viewport: Buttons collapse to icon-only
- [ ] Network error on subscription toggle: Shows error toast, reverts UI state

### Integration Points

- [ ] SSE notifications: Subscription changes reflect in notification system
- [ ] Delete cascade: Deleting planner removes related subscriptions
- [ ] Fork/Duplicate: New planner does not inherit subscriptions from original
- [ ] Rate limiting: Report endpoint respects rate limit (429 on spam)
