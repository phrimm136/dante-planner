# Task: MD Planner Editor Consolidation (New + Edit Pages)

## Description

Consolidate the MD planner "new" and "edit" pages to share a single implementation while maintaining separate routes. Currently, `PlannerMDNewPage.tsx` (1220 lines) handles creating new planners, while `PlannerMDEditPage.tsx` (28 lines skeleton) needs to be implemented for editing existing planners. The pages have identical UI and functionality - the only difference is initialization:

**New Mode (`/planner/md/new`):**
- Initialize state with default values (empty equipment, default category "5F", etc.)
- Show draft recovery dialog if user has unsaved work in IndexedDB
- Generate new planner ID on first save
- No ownership checks needed (user creates their own planner)

**Edit Mode (`/planner/md/$id/edit`):**
- Load existing planner via `useSavedPlannerQuery(id)`
- Initialize state from loaded planner content
- Skip draft recovery dialog (editing a saved planner, not recovering unsaved work)
- Use existing planner ID for all saves
- Verify ownership before showing editor (backend enforces, frontend shows early error)

**Shared Behavior:**
- Auto-save every 2 seconds (same `usePlannerSave` hook)
- Optimistic locking via syncVersion (conflict resolution dialog)
- Publish toggle functionality
- All 15+ state hooks (equipment, buffs, gifts, themes, notes)
- Same validation rules and constraints

**User Interactions:**
- User clicks "Edit" on planner card → navigates to `/planner/md/$id/edit`
- Editor loads planner data via Suspense boundary
- User makes changes → auto-saves to server
- If user doesn't own planner → shows error message immediately (before loading full editor)
- If planner doesn't exist → shows 404 error with link back to planner list
- Refresh during edit → loads latest auto-saved version from server

## Research

- **Existing Implementation**: Read `PlannerMDNewPage.tsx` in detail to understand:
  - All state hooks and their initialization
  - Draft recovery logic and when it triggers
  - Auto-save integration with `usePlannerSave` hook
  - Section rendering pattern (DeckBuilder, StartBuff, EGOGift, etc.)
  - Progressive rendering optimization

- **Data Loading Patterns**: Study how `useSavedPlannerQuery` works:
  - Suspense boundary behavior
  - Error handling for missing planners
  - Data deserialization (Sets from JSON arrays)

- **Route Patterns**: Check TanStack Router conventions:
  - How route params work (`$id` in path)
  - Lazy route component loading
  - Navigation between routes

- **Similar Edit Patterns**: Search codebase for other "view vs edit" patterns:
  - Check if any other pages handle optional loading
  - Look for conditional state initialization patterns

- **Ownership Verification**: Understand current backend security:
  - `PlannerService.findPlannerOrThrow()` implementation (lines 365-367)
  - `PlannerForbiddenException` error handling
  - How frontend should check `planner.userId === auth?.user?.id`

## Scope

**Files to READ for context:**
- `/home/user/github/LimbusPlanner/frontend/src/routes/PlannerMDNewPage.tsx` - Main implementation (1220 lines)
- `/home/user/github/LimbusPlanner/frontend/src/routes/PlannerMDEditPage.tsx` - Current skeleton (28 lines)
- `/home/user/github/LimbusPlanner/frontend/src/routes/PlannerMDDetailPage.tsx` - Read-only viewer for comparison
- `/home/user/github/LimbusPlanner/frontend/src/hooks/usePlannerSave.ts` - Auto-save hook with `initialPlannerId` support
- `/home/user/github/LimbusPlanner/frontend/src/hooks/usePlannerStorage.ts` - IndexedDB persistence
- `/home/user/github/LimbusPlanner/frontend/src/hooks/useSavedPlannerQuery.ts` - Server planner loading
- `/home/user/github/LimbusPlanner/frontend/src/hooks/usePlannerConfig.ts` - Version config fetching
- `/home/user/github/LimbusPlanner/frontend/src/components/plannerList/PlannerCardContextMenu.tsx` - Edit navigation trigger
- `/home/user/github/LimbusPlanner/frontend/src/lib/router.tsx` - Route definitions
- `/home/user/github/LimbusPlanner/docs/architecture-map.md` - System context and patterns

**Backend files to understand:**
- `/home/user/github/LimbusPlanner/backend/src/main/java/org/danteplanner/backend/service/PlannerService.java` - Lines 216-219 (getPlanner), 234-280 (updatePlanner), 365-367 (findPlannerOrThrow)
- `/home/user/github/LimbusPlanner/backend/src/main/java/org/danteplanner/backend/exception/PlannerForbiddenException.java` - 403 error structure

## Target Code Area

**Files to CREATE:**
- `/home/user/github/LimbusPlanner/frontend/src/routes/PlannerMDEditorContent.tsx` - Shared editor component (~1150 lines extracted from PlannerMDNewPage)

**Files to MODIFY:**
- `/home/user/github/LimbusPlanner/frontend/src/routes/PlannerMDNewPage.tsx` - Reduce to thin wrapper (~20 lines) calling `PlannerMDEditorContent`
- `/home/user/github/LimbusPlanner/frontend/src/routes/PlannerMDEditPage.tsx` - Implement with `PlannerMDEditorContent` (~30 lines with ownership checks)

**Optional files (deferred to TODO):**
- Frontend UX improvements documented in `docs/TODO.md` under UX-004

## System Context (Senior Thinking)

**Feature domain:** Planner (MD) - Mirror Dungeon planner editor (from architecture-map)

**Core files in this domain:**
- `routes/PlannerMDNewPage.tsx` - Current implementation
- `hooks/usePlannerStorage.ts` - IndexedDB persistence
- `hooks/usePlannerConfig.ts` - Version configuration
- `components/deckBuilder/*` - Summary+Pane pattern
- `components/startBuff/*` - Summary+EditPane pattern
- `components/startGift/*` - Summary+EditPane pattern
- `components/egoGift/EGOGiftObservation*` - Summary+EditPane pattern
- `components/floorTheme/*` - Floor theme selection
- `components/noteEditor/*` - Tiptap rich text editors

**Cross-cutting concerns touched:**
- **Validation**: Zod schemas in `schemas/PlannerSchemas.ts`, backend validation in `PlannerContentValidator.java`
- **Auth**: JWT authentication via `@AuthenticationPrincipal`, ownership verification
- **Storage**: IndexedDB (local drafts) + server sync (saved planners)
- **Real-time sync**: SSE notifications via `PlannerSseService` (not applicable to edit mode)
- **Routing**: TanStack Router with lazy loading

## Impact Analysis

**Files being modified:**
- `PlannerMDNewPage.tsx` - **Medium impact** (used by new planner creation flow, URL stays same)
- `PlannerMDEditPage.tsx` - **Low impact** (currently skeleton, not in use)
- New `PlannerMDEditorContent.tsx` - **Medium impact** (becomes shared component for both modes)

**What depends on these files:**
- Route definitions in `lib/router.tsx` (no change needed - routes stay same)
- Navigation from `PlannerCardContextMenu.tsx` to `/planner/md/$id/edit` (already implemented)
- Auto-save system via `usePlannerSave` hook (supports both modes already)

**Potential ripple effects:**
- **Draft recovery logic**: Must be conditional (only in new mode, not edit mode)
- **State initialization**: Different sources (defaults vs loaded planner)
- **usePlannerSave hook**: Already supports `initialPlannerId` parameter (no changes needed)
- **Suspense boundaries**: Edit mode adds extra loading (planner data + spec data)

**High-impact files to watch:**
- `hooks/usePlannerSave.ts` - **High impact** (used by all planner operations, should NOT be modified)
- `hooks/usePlannerStorage.ts` - **High impact** (IndexedDB layer, should NOT be modified)
- `lib/router.tsx` - **High impact** (routing changes affect navigation, NO changes needed)

## Risk Assessment

**Edge cases not yet defined:**
- ✅ **Ownership**: User tries to edit another user's planner → Backend returns 404 via `findPlannerOrThrow(userId, id)` (combines "not found" and "not owner" into 404)
- ✅ **Missing planner**: User visits `/planner/md/999/edit` where 999 doesn't exist → `useSavedPlannerQuery` returns null → show 404 error
- ✅ **Concurrent edits**: User edits same planner on two devices → optimistic locking via syncVersion → conflict resolution dialog (already implemented)
- ⚠️ **Draft recovery in edit mode**: Should NOT show draft recovery dialog when editing saved planner (different from unsaved work)
- ⚠️ **State initialization timing**: If planner loads after useState initializes, state is stale → need useEffect to sync

**Performance concerns:**
- **Suspense waterfall**: Edit mode requires both spec data AND planner data → consider parallelizing with Promise.all or separate Suspense boundaries
- **Large component re-extraction**: Moving 1150 lines of JSX → ensure no logic breakage during extraction

**Backward compatibility:**
- ✅ URLs unchanged: `/planner/md/new` and `/planner/md/$id/edit` routes preserved
- ✅ Navigation links: Already point to correct URLs
- ⚠️ Draft recovery behavior: Must NOT change for new mode (existing users expect it)

**Security considerations:**
- ✅ **Backend security complete**: Ownership verified in `PlannerService.findPlannerOrThrow()` (line 365)
- ✅ **Authentication required**: JWT via `@AuthenticationPrincipal Long userId`
- ✅ **Rate limiting**: Applied via `rateLimitConfig.checkCrudLimit()`
- ⚠️ **Frontend ownership check**: Optional UX improvement (deferred to TODO UX-004) - check `planner.userId === auth?.user?.id` before loading full editor

## Testing Guidelines

### Manual UI Testing

**New Mode (unchanged behavior):**
1. Navigate to `/planner/md/new`
2. Verify page loads with empty equipment and default category "5F"
3. Make changes to deck, buffs, or gifts
4. Close tab without saving
5. Reopen `/planner/md/new`
6. Verify draft recovery dialog appears
7. Click "Recover" → verify changes restored
8. Click "Discard" → verify fresh state

**Edit Mode (new behavior):**
1. Create and save a planner (use existing flow)
2. Navigate to planner list (`/planner/md`)
3. Right-click on saved planner card
4. Click "Edit" in context menu
5. Verify navigation to `/planner/md/{id}/edit`
6. Verify planner data loads (title, category, equipment, etc.)
7. Verify NO draft recovery dialog appears
8. Make changes to any field
9. Wait 2+ seconds for auto-save
10. Refresh browser tab
11. Verify changes persisted (loaded from server)
12. Close editor and reopen
13. Verify latest auto-saved version loads

**Ownership Verification:**
1. As User A, create and publish a planner
2. Copy planner ID from URL
3. Log out and log in as User B
4. Manually navigate to `/planner/md/{user-a-planner-id}/edit`
5. Verify error response (currently shows 404 from backend)
6. Future UX: Should show "You don't own this planner" error page

### Automated Functional Verification

**State Initialization:**
- [ ] New mode: State initializes with `createDefaultEquipment()`, empty title, category "5F"
- [ ] Edit mode: State initializes from loaded planner (title, category, content fields)
- [ ] Edit mode: Sets are properly deserialized from JSON arrays (selectedKeywords, etc.)

**Draft Recovery:**
- [ ] New mode: Draft recovery dialog appears if IndexedDB has unsaved work
- [ ] New mode: "Recover" restores IndexedDB draft
- [ ] New mode: "Discard" clears draft and shows fresh state
- [ ] Edit mode: Draft recovery dialog NEVER appears (even if IndexedDB has data)

**Auto-save Behavior:**
- [ ] Both modes: Changes trigger auto-save after 2-second debounce
- [ ] New mode: First save creates planner and returns server-generated ID
- [ ] Edit mode: All saves update existing planner with same ID
- [ ] Both modes: syncVersion increments on each save
- [ ] Both modes: Conflict dialog appears on version mismatch (409 response)

**Navigation:**
- [ ] New mode: `/planner/md/new` route loads correctly
- [ ] Edit mode: `/planner/md/{uuid}/edit` route loads correctly
- [ ] Edit mode: Invalid UUID in URL shows error
- [ ] Edit mode: Missing planner (404) shows error with link to planner list

**Ownership:**
- [ ] Edit mode: Backend rejects edits to other users' planners (404 from `findPlannerOrThrow`)
- [ ] Edit mode: Frontend loads planner only if user owns it (deferred to UX-004)

### Edge Cases

**Missing Planner (404):**
- [ ] User visits `/planner/md/nonexistent-uuid/edit`
- [ ] Expected: `useSavedPlannerQuery` returns null → show error page with "Planner not found" message

**Unauthorized Access (404 - current behavior):**
- [ ] User B visits `/planner/md/{user-a-planner-id}/edit`
- [ ] Expected: Backend `findPlannerOrThrow(userId, id)` returns 404 (combines "not found" + "not owner")
- [ ] Frontend: Treated as missing planner

**State Initialization Timing:**
- [ ] Edit mode: Planner loads asynchronously via Suspense
- [ ] Expected: useState initializes with empty values → useEffect syncs when planner loads
- [ ] Verify: State contains loaded planner data after Suspense resolves

**Draft Recovery Confusion:**
- [ ] User edits saved planner, browser crashes (IndexedDB preserves edits)
- [ ] User reopens `/planner/md/{id}/edit`
- [ ] Expected: Loads latest server version (auto-saved before crash), NO draft recovery dialog

**Concurrent Edits:**
- [ ] User opens planner in two tabs (syncVersion: 5)
- [ ] Tab 1 saves changes (syncVersion: 6)
- [ ] Tab 2 tries to save
- [ ] Expected: 409 Conflict → `ConflictResolutionDialog` appears
- [ ] User chooses "Reload Server Version" → tab 2 loads version 6

**Empty Title Edge Case:**
- [ ] New mode: User saves planner with empty title
- [ ] Edit mode: Loads planner with empty title
- [ ] Expected: Both modes handle gracefully (backend allows empty titles)

### Integration Points

**Backend Integration:**
- [ ] `usePlannerSave` hook: Verify `initialPlannerId` parameter works for edit mode
- [ ] Auto-save: Verify PUT requests to `/api/planner/md/{id}` succeed
- [ ] Ownership: Verify backend returns 404 for unauthorized access
- [ ] Validation: Verify backend `PlannerContentValidator` runs on all saves

**Frontend Hooks:**
- [ ] `useSavedPlannerQuery`: Verify loads planner data correctly in edit mode
- [ ] `usePlannerConfig`: Verify version config loads in both modes
- [ ] `useAuthQuery`: Verify provides user ID for ownership checks (deferred to UX-004)

**Navigation:**
- [ ] `PlannerCardContextMenu`: Verify "Edit" button navigates to `/planner/md/{id}/edit`
- [ ] Route params: Verify `useParams()` extracts `id` from URL correctly
- [ ] Lazy loading: Verify route component loads via `lazyRouteComponent()`

**State Management:**
- [ ] All 15+ useState hooks: Verify work identically in both modes
- [ ] Set deserialization: Verify `selectedKeywords` converts from array to Set in edit mode
- [ ] Progressive rendering: Verify requestAnimationFrame optimization works in both modes
