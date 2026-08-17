# Task: Planner List Page (/planner/md)

## Description

Create a planner list page that allows users to browse, manage, and discover Mirror Dungeon plans. The page has two primary views accessible via tabs:

### My Plans View
- Guest users see plans stored in IndexedDB
- Logged-in users see plans fetched from the server
- Shows both drafts and saved plans
- Context menu actions: Edit, Duplicate, Publish/Unpublish, Delete

### Community View
- Shows published plans from all users
- Sub-filter toggle: "All" vs "Recommended" (plans with net votes >= threshold)
- Recommended threshold is backend-configured (default: 10)
- Guest users can view but cannot vote, bookmark, or fork
- Context menu actions: View, Fork, Bookmark/Unbookmark, Upvote/Downvote

### Card Display
Each plan card shows:
- Category badge (5F, 10F, 15F)
- Title (max 2 lines)
- Keywords (max 3 chips)
- Vote counts (upvotes and downvotes)
- View count
- Last modified date (formatted for user's timezone)
- Author name (currently "Anonymous")
- Bookmark indicator (community view, logged-in only)

### Filtering & Search
- Category filter: 5F, 10F, 15F (inline pills, not sidebar)
- Keyword filter (multi-select)
- Search bar (searches title)
- Sort options: Recent, Popular (views), Top Voted
- Pagination

### URL State
All filter states persist in URL for shareable links:
`/planner/md?view=community&filter=recommended&category=15F&page=2&sort=votes&q=burn`

### Timezone Handling
Since international users access this site, all timestamps must:
- Store as UTC in database/IndexedDB
- Display in user's local timezone
- Use relative time for recent items ("2 hours ago")
- Use absolute date for older items (localized format)

---

## Research

### Backend Patterns
- Existing `PlannerController.java` endpoints and response DTOs
- `PublicPlannerResponse.java` structure
- Voting system in `PlannerService.castVote()`
- Existing pagination patterns in repository layer

### Frontend Patterns
- `IdentityPage.tsx` for list page structure
- `FilterPageLayout.tsx` (reference only - using inline pills instead)
- `ResponsiveCardGrid.tsx` for card grid
- `usePlannerStorage.ts` for IndexedDB operations
- Context menu implementation (may need new shadcn component)

### Data Flow
- How `useSuspenseQuery` works with pagination
- URL search params with TanStack Router
- How to detect guest vs authenticated state

### Timezone Libraries
- Check if project uses date-fns, dayjs, or native Intl
- Existing date formatting patterns in codebase

---

## Scope

### Files to Read for Context
- `frontend/src/routes/IdentityPage.tsx` - List page pattern
- `frontend/src/routes/EGOPage.tsx` - Alternative list pattern
- `frontend/src/components/identity/IdentityCard.tsx` - Card pattern
- `frontend/src/hooks/usePlannerStorage.ts` - IndexedDB operations
- `frontend/src/types/PlannerTypes.ts` - Existing planner types
- `frontend/src/lib/constants.ts` - MD_CATEGORIES, PLANNER_KEYWORDS
- `backend/src/main/java/org/danteplanner/backend/controller/PlannerController.java`
- `backend/src/main/java/org/danteplanner/backend/dto/planner/PublicPlannerResponse.java`

---

## Target Code Area

### Backend - New Files
- `db/migration/V00X__add_view_count_and_bookmarks.sql`
- `entity/PlannerBookmark.java`
- `entity/PlannerBookmarkId.java`
- `repository/PlannerBookmarkRepository.java`
- `dto/planner/ForkResponse.java`

### Backend - Modified Files
- `entity/Planner.java` - add viewCount column
- `dto/planner/PublicPlannerResponse.java` - add viewCount, lastModifiedAt, userVote, isBookmarked
- `service/PlannerService.java` - add bookmark, fork, view count methods
- `controller/PlannerController.java` - add bookmark, fork endpoints
- `application.properties` - add planner.recommended.min-net-votes

### Frontend - New Files
- `routes/PlannerListPage.tsx`
- `components/plannerList/PlannerListTabs.tsx`
- `components/plannerList/PlannerListToolbar.tsx`
- `components/plannerList/PlannerListFilterPills.tsx`
- `components/plannerList/PlannerCard.tsx`
- `components/plannerList/PlannerCardContextMenu.tsx`
- `components/plannerList/PlannerEmptyState.tsx`
- `components/plannerList/PlannerListPagination.tsx`
- `hooks/usePlannerListData.ts`
- `hooks/usePlannerListFilters.ts`
- `hooks/usePlannerVote.ts`
- `hooks/usePlannerBookmark.ts`
- `hooks/usePlannerFork.ts`
- `hooks/usePlannerPublish.ts`
- `types/PlannerListTypes.ts`
- `schemas/PlannerListSchemas.ts`
- `lib/plannerListQueries.ts`

### i18n Files
- `static/i18n/EN/plannerList.json`
- `static/i18n/JP/plannerList.json`
- `static/i18n/KR/plannerList.json`

---

## Testing Guidelines

### Manual UI Testing

**My Plans View (Guest):**
1. Clear browser storage and navigate to /planner/md
2. Verify "My Plans" tab is active by default
3. Verify empty state shows "No plans yet" with CTA
4. Create a plan via /planner/md/new and save
5. Return to /planner/md
6. Verify the plan card appears with correct title, category, date
7. Right-click the card
8. Verify context menu shows: Edit, Duplicate, Delete
9. Click "Edit"
10. Verify navigation to /planner/md/{id}
11. Return and right-click again
12. Click "Delete"
13. Verify confirmation dialog appears
14. Confirm delete
15. Verify card is removed

**My Plans View (Logged In):**
1. Log in and navigate to /planner/md
2. Verify plans from server are shown
3. Right-click a saved plan
4. Verify "Publish" action appears
5. Click "Publish"
6. Verify card shows published indicator
7. Right-click again
8. Verify "Unpublish" action now appears

**Community View (Guest):**
1. Navigate to /planner/md
2. Click "Community" tab
3. Verify published plans from all users are shown
4. Verify vote buttons are disabled or hidden
5. Verify bookmark icon is not shown
6. Right-click a card
7. Verify only "View" action is available

**Community View (Logged In):**
1. Log in and click "Community" tab
2. Verify vote counts are shown on cards
3. Right-click a card
4. Verify context menu shows: View, Fork, Bookmark, Upvote, Downvote
5. Click "Upvote"
6. Verify upvote count increases by 1
7. Right-click same card
8. Verify "Remove Vote" appears instead of "Upvote"
9. Click "Fork"
10. Verify success toast appears
11. Switch to "My Plans" tab
12. Verify forked plan appears as draft

**Filtering:**
1. Navigate to /planner/md?view=community
2. Click "15F" category pill
3. Verify only 15F plans are shown
4. Verify URL updates to include category=15F
5. Click "Recommended" toggle
6. Verify only plans with net votes >= threshold are shown
7. Enter search text in search bar
8. Verify results filter by title match
9. Refresh page
10. Verify filters persist from URL

**Pagination:**
1. Navigate to community view with many plans
2. Verify pagination shows at bottom
3. Click page 2
4. Verify URL updates to page=2
5. Verify different plans are shown
6. Use browser back button
7. Verify returns to page 1

**Timezone:**
1. Change system timezone to different zone
2. Refresh page
3. Verify dates display in local timezone
4. Verify "2 hours ago" style for recent items
5. Verify "Dec 30, 2024" style for older items

### Automated Functional Verification

**View Toggle:**
- [ ] Tab switch: Clicking "My Plans"/"Community" switches view
- [ ] Tab persistence: URL view param updates on tab change
- [ ] Active state: Current tab has visual indicator

**Card Display:**
- [ ] Category badge: Shows correct 5F/10F/15F
- [ ] Title truncation: Long titles truncate with ellipsis at 2 lines
- [ ] Keyword chips: Maximum 3 shown, overflow hidden
- [ ] Vote display: Shows both upvote and downvote counts
- [ ] View count: Shows view count with icon
- [ ] Date format: Relative for < 7 days, absolute for older
- [ ] Timezone: All dates in user's local timezone

**Context Menu:**
- [ ] Right-click opens: Menu appears at cursor position
- [ ] Click outside closes: Menu dismisses on outside click
- [ ] Escape closes: Menu dismisses on Escape key
- [ ] Action executes: Clicking action triggers correct behavior

**Filtering:**
- [ ] Category filter: Filters by selected category
- [ ] Multi-category: Multiple categories can be selected (OR logic)
- [ ] Recommended toggle: Filters to net votes >= threshold
- [ ] Search: Filters by title substring (case-insensitive)
- [ ] Clear filters: Reset button clears all filters

**Pagination:**
- [ ] Page navigation: Previous/Next buttons work
- [ ] Page numbers: Direct page number links work
- [ ] URL sync: Page number reflects in URL
- [ ] Boundary: Cannot go below 1 or above max

**Data Sources:**
- [ ] Guest IndexedDB: My Plans loads from local storage
- [ ] Auth server: My Plans loads from API when logged in
- [ ] Community API: Always loads from server API

### Edge Cases

**Empty States:**
- [ ] No plans (My Plans): Shows "Create your first plan" CTA
- [ ] No results (Community): Shows "No plans match filters"
- [ ] No recommended: Shows "No recommended plans yet"

**Authentication:**
- [ ] Guest vote attempt: Shows login prompt or disabled
- [ ] Guest bookmark attempt: Shows login prompt or disabled
- [ ] Guest fork attempt: Shows login prompt or disabled
- [ ] Session expiry: Gracefully handles 401 during mutation

**Error Handling:**
- [ ] Network error: Shows retry option
- [ ] API error: Shows error message with retry
- [ ] IndexedDB error: Falls back gracefully

**Data Integrity:**
- [ ] Delete confirmation: Requires explicit confirm
- [ ] Publish confirmation: Shows warning about public visibility
- [ ] Fork conflict: Handles if source planner deleted

**Performance:**
- [ ] Large list: Handles 100+ cards without lag
- [ ] Rapid filter changes: Debounces API calls
- [ ] Image loading: Cards render before images load

### Integration Points

**With Planner Editor:**
- [ ] Edit action: Navigates to /planner/md/{id} with correct data
- [ ] New button: Navigates to /planner/md/new
- [ ] Back navigation: Returns to list with filters preserved

**With Planner Viewer:**
- [ ] View action: Navigates to /planner/md/{id}/view
- [ ] Viewer shows: Correct plan data displayed

**With Auth System:**
- [ ] Login state: UI updates when user logs in/out
- [ ] Token refresh: Mutations work after token refresh

**With IndexedDB:**
- [ ] Sync on login: Option to import local drafts to server
- [ ] Guest limit: Respects MAX_GUEST_DRAFTS
