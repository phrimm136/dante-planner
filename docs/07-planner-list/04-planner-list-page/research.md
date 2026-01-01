# Research: Planner List Page

## Clarifications Resolved

| Question | Decision |
|----------|----------|
| Date formatting threshold | HH:mm for < 24 hours, MM/DD for older (timezone-aware) |
| Bookmark persistence | Backend persisted (new entity + endpoints needed) |
| Context menu approach | Dropdown-menu with onContextMenu event binding |
| Pagination component | Add shadcn pagination via CLI |
| Data source branching | Conditional hook checking auth state |

---

## Spec-to-Code Mapping

### Backend - New Files
- `entity/PlannerBookmark.java` - Composite key entity for user+planner
- `entity/PlannerBookmarkId.java` - Embeddable composite key
- `repository/PlannerBookmarkRepository.java` - JPA repository
- `dto/planner/BookmarkResponse.java` - Toggle response DTO
- `dto/planner/ForkResponse.java` - Fork result DTO
- `db/migration/V00X__add_view_count_and_bookmarks.sql` - Schema changes

### Backend - Modified Files
- `entity/Planner.java` - Add viewCount column
- `dto/planner/PublicPlannerResponse.java` - Add viewCount, lastModifiedAt, userVote, isBookmarked
- `service/PlannerService.java` - Add bookmark(), fork(), incrementViewCount() methods
- `controller/PlannerController.java` - Add /bookmark, /fork endpoints

### Frontend - New Files
- `routes/PlannerListPage.tsx` - Main page with tabs + URL state
- `components/plannerList/PlannerListTabs.tsx` - My Plans / Community tabs
- `components/plannerList/PlannerListToolbar.tsx` - Search + sort controls
- `components/plannerList/PlannerListFilterPills.tsx` - Inline category filter
- `components/plannerList/PlannerCard.tsx` - Plan card component
- `components/plannerList/PlannerCardContextMenu.tsx` - Right-click dropdown
- `components/plannerList/PlannerEmptyState.tsx` - No plans messaging
- `hooks/usePlannerListData.ts` - Conditional fetch (IndexedDB vs API)
- `hooks/usePlannerListFilters.ts` - URL state management
- `hooks/usePlannerVote.ts` - Vote mutation
- `hooks/usePlannerBookmark.ts` - Bookmark mutation
- `hooks/usePlannerFork.ts` - Fork mutation
- `hooks/usePlannerPublish.ts` - Publish/unpublish mutation
- `schemas/PlannerListSchemas.ts` - Response validation
- `lib/formatDate.ts` - Relative/absolute date utility
- `lib/plannerListQueries.ts` - Query key factory

### i18n Files
- `static/i18n/{EN,JP,KR}/plannerList.json`

---

## Pattern Enforcement (MANDATORY)

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| PlannerListPage.tsx | routes/IdentityPage.tsx | Page structure, Suspense wrapping, filter layout |
| PlannerCard.tsx | components/identity/IdentityCard.tsx | Card layout, badge, metadata display |
| PlannerListFilterPills.tsx | components/common/CompactSinnerFilter.tsx | Multi-select toggle pills |
| PlannerCardContextMenu.tsx | components/ui/dropdown-menu.tsx | Menu item structure |
| usePlannerListData.ts | hooks/useIdentityListData.ts | useSuspenseQuery + Zod validation |
| PlannerListSchemas.ts | schemas/IdentitySchemas.ts | Type-first Zod pipeline |
| PlannerService.java | service/PlannerService.java | Transaction handling, existing vote pattern |
| PlannerBookmark.java | entity/PlannerVote.java | Composite key entity pattern |

---

## Existing Utilities (CHECK BEFORE CREATING)

| Category | Location | Existing |
|----------|----------|----------|
| Constants | lib/constants.ts | MD_CATEGORIES, PLANNER_KEYWORDS, CARD_GRID |
| Search | components/common/SearchBar.tsx | Debounced search input |
| Card grid | components/common/ResponsiveCardGrid.tsx | Responsive card layout |
| Auth state | hooks/useAuthQuery.ts | useAuthQuery() returns User or null |
| IndexedDB | hooks/usePlannerStorage.ts | loadPlanner(), savePlanner(), deletePlanner() |
| API client | lib/api.ts | ApiClient.get(), post(), delete() |
| i18n | hooks via i18next | useTranslation() |
| Tabs | components/ui/tabs.tsx | shadcn Tabs component |
| Dropdown | components/ui/dropdown-menu.tsx | DropdownMenu components |

### Missing (Must Create)
- Date formatting utility (formatDate.ts)
- Pagination component (add via shadcn CLI)
- URL state hook pattern (usePlannerListFilters.ts)
- Bookmark/Fork mutations
- PublicPlannerResponse schema

---

## Gap Analysis

### Can Reuse Directly
- SearchBar.tsx
- ResponsiveCardGrid.tsx
- useAuthQuery()
- usePlannerStorage.ts (for guest My Plans)
- MD_CATEGORIES, PLANNER_KEYWORDS constants
- shadcn tabs, dropdown-menu

### Needs Modification
- lib/constants.ts - Add page size constant
- schemas/PlannerSchemas.ts - Add list response schema

### Must Create
- Backend bookmark entity + repository + endpoints
- Backend fork endpoint
- All plannerList/ components
- All plannerList hooks
- Date formatting utility
- URL state management hook

---

## Technical Constraints

### Frontend
- useSuspenseQuery MUST have Suspense ancestor
- Conditional hook pattern for auth-based data source
- URL state via TanStack Router useSearch/useNavigate
- Zod validation for all API responses
- No manual memo (React Compiler handles it)

### Backend
- Controller -> Service -> Repository layering
- DTOs at API boundaries (no direct entity exposure)
- Constructor injection only
- @Transactional on Service methods
- Pageable for list endpoints

### Date Formatting Rules
- < 24 hours: Show HH:mm (e.g., "14:32")
- >= 24 hours: Show MM/DD (e.g., "12/30")
- All times in user's local timezone via Intl.DateTimeFormat

---

## Testing Requirements

### Manual UI Tests
- Tab switching between My Plans / Community
- Card display with votes, date, category badge
- Right-click context menu appears correctly
- Category filter pills update URL and filter results
- Guest users cannot vote/bookmark
- Empty states display correctly
- Pagination updates URL and shows different pages

### Automated Tests
- usePlannerListData returns correct data source based on auth
- formatDate() returns correct format based on age
- Vote mutation updates count optimistically
- Bookmark toggle works for authenticated users
- Fork creates new draft entry
- URL search params persist on navigation

### Edge Cases
- Empty My Plans (guest) -> CTA to create
- Empty search results -> "No plans match"
- Session expiry during mutation -> graceful 401 handling
- Network offline -> error state or cached data
