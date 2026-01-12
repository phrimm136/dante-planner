# Task: Local-First Auto-Save Architecture

## Description

Change the planner auto-save mechanism to reduce server load from ~18,000 requests/hour to ~100 requests/hour while maintaining data safety and multi-device sync capabilities. The system should:

**Auto-Save Behavior:**
- Auto-saves trigger every 2 seconds after user edits (existing debounce timing preserved)
- All auto-saves write to IndexedDB only (both authenticated and guest users)
- No network requests during auto-save operations
- Auto-save only fires once per edit change, not continuously (verified behavior)
- State comparison prevents redundant saves when state hasn't changed

**Manual Save Behavior:**
- Manual save button triggers explicit save to server (authenticated users only)
- Manual saves update sync version and trigger SSE notifications for cross-device sync
- Manual saves update "Last synced" timestamp
- Guest users' manual saves write to IndexedDB only (no server option)
- Successfully saved planners show "Synced" status for authenticated users

**Draft Limit Removal:**
- Remove `MAX_GUEST_DRAFTS = 3` limit and `enforceGuestDraftLimit()` enforcement
- Allow unlimited local drafts (browser quota ~50MB-1GB is natural limit)
- No artificial quota checking or oldest-draft deletion logic

**Status Display:**
- Keep existing enum (`type PlannerStatus = 'draft' | 'saved'`) - no backend changes
- Change display text via i18n keys:
  - Authenticated users: "Unsynced" (draft) / "Synced" (saved)
  - Guest users: "Local" (always, no sync concept)
- Status badge appears in top-right corner of planner list cards (existing position)

**Unsaved Changes Warning:**
- Show browser beforeunload warning for authenticated users with unsynced changes
- Warning text: "You have unsynced changes. Are you sure you want to leave?"
- No warning for guest users (data always saved locally)
- No warning for authenticated users after successful manual save
- Warning only shows on browser close/reload, not in-app navigation

**Last Synced Timestamp:**
- Display "Last synced: X minutes ago" near save button for authenticated users
- Show "Never synced" if planner never manually saved to server
- Update timestamp only after successful manual save to server
- Hidden for guest users (no server sync concept)
- Use relative time format (e.g., "a few seconds ago", "5 minutes ago")

**Cross-Device Sync:**
- Existing syncVersion-based conflict detection remains unchanged
- SSE notifications continue to work for manual saves
- Conflict dialog shows when editing on multiple devices then manually saving
- User chooses "Overwrite" or "Discard" to resolve conflicts

**Validation Handling:**
- IndexedDB accepts incomplete planners (missing theme packs, etc.)
- Server still validates on manual save (complete data required)
- Separates work-in-progress (local) from published state (server)

## Research

- Existing dual-storage adapter pattern in `usePlannerStorageAdapter.ts` (lines 83-149)
- Auto-save debounce implementation in `usePlannerSave.ts` (lines 391-409, 488-515)
- State comparison logic in `stateToComparableString()` (lines 192-212)
- Conflict resolution flow in `usePlannerSave.ts` (lines 438-478)
- SSE integration in `usePlannerSync.ts` for real-time updates
- Current status display logic in planner list card component
- beforeunload API for browser close warnings
- date-fns library for relative timestamp formatting (`formatDistanceToNow`)

## Scope

**Read for context:**
- `frontend/src/hooks/usePlannerSave.ts` - Auto-save orchestration with debounce
- `frontend/src/hooks/usePlannerStorageAdapter.ts` - Routes saves to server or IndexedDB
- `frontend/src/hooks/usePlannerSync.ts` - Server CRUD + SSE connection
- `frontend/src/hooks/usePlannerStorage.ts` - IndexedDB operations
- `frontend/src/routes/PlannerMDPage.tsx` - Personal planner list
- `frontend/src/hooks/useMDUserPlannersData.ts` - Fetches user planners
- `frontend/src/routes/PlannerMDNewPage.tsx` - Planner editor page
- `frontend/src/routes/PlannerMDEditPage.tsx` - Edit existing planner
- `frontend/src/lib/constants.ts` - MAX_GUEST_DRAFTS constant
- Planner list card component (find via Grep for status badge rendering)

## Target Code Area

**Phase 0 (Remove Draft Limit):**
- `frontend/src/lib/constants.ts` (line 524) - Remove MAX_GUEST_DRAFTS constant
- `frontend/src/hooks/usePlannerStorage.ts` (lines 108, 397-417, 443) - Remove enforceGuestDraftLimit() method and interface
- `frontend/src/hooks/usePlannerSave.ts` (line 344) - Remove enforceGuestDraftLimit() call

**Phase 1 (Auto-Save Routing):**
- `frontend/src/hooks/usePlannerSave.ts` (lines 391-409) - Change debouncedSave to always use IndexedDB

**Phase 2 (Status Terminology):**
- `static/i18n/EN/common.json` - Add status i18n keys (planner.status.unsynced, synced, local)
- `static/i18n/KR/common.json` - Add Korean translations
- `static/i18n/JP/common.json` - Add Japanese translations
- `static/i18n/CN/common.json` - Add Chinese translations
- Planner list card component - Update status text logic (find via Grep)

**Phase 3 (beforeunload Warning):**
- `frontend/src/hooks/usePlannerSave.ts` - Add lastSyncedStateRef, expose hasUnsyncedChanges in return
- `frontend/src/routes/PlannerMDNewPage.tsx` - Add beforeunload event listener
- `frontend/src/routes/PlannerMDEditPage.tsx` - Add beforeunload event listener
- `static/i18n/*/common.json` - Add warning text i18n keys (optional, for future custom dialog)

**Phase 4 (Last Synced Timestamp):**
- `frontend/src/hooks/usePlannerSave.ts` - Add lastSyncedAt state, update on manual save, expose in return
- `frontend/src/routes/PlannerMDNewPage.tsx` - Display timestamp near save button
- `static/i18n/*/common.json` - Add sync timestamp i18n keys (planner.sync.lastSynced, neverSynced)
- Install `date-fns` package: `cd frontend && yarn add date-fns`

## System Context (Senior Thinking)

- **Feature domain**: Planner (Data Synchronization & Persistence)
- **Core files in this domain**:
  - `hooks/usePlannerSave.ts` (auto-save orchestration, 730 lines)
  - `hooks/usePlannerStorageAdapter.ts` (storage routing abstraction, 245 lines)
  - `hooks/usePlannerSync.ts` (server CRUD + SSE, 233 lines)
  - `routes/PlannerMDNewPage.tsx`, `PlannerMDEditPage.tsx` (editor pages)
  - `routes/PlannerMDPage.tsx` (personal planner list)
- **Cross-cutting concerns touched**:
  - i18n (status text translation across 4 languages)
  - Real-time sync (SSE for manual saves only, reduced traffic)
  - Local storage (IndexedDB for all auto-saves)
  - Error handling (quota exceeded, auth expiry, conflicts)

## Impact Analysis

**Files being modified:**

- **usePlannerSave.ts** (Medium impact)
  - Used by: PlannerMDNewPage, PlannerMDEditPage
  - What depends: Auto-save behavior, manual save button handlers
  - Ripple effects: All planner editing flows
  - Change scope: Routing logic in debouncedSave(), add state tracking

- **usePlannerStorageAdapter.ts** (Low impact)
  - Used by: usePlannerSave, useMDUserPlannersData
  - What depends: Storage routing decisions
  - Ripple effects: Minimal (abstraction layer already exists)
  - Change scope: Remove enforceGuestDraftLimit() call only

- **usePlannerStorage.ts** (Low impact)
  - Used by: usePlannerStorageAdapter
  - What depends: IndexedDB operations
  - Ripple effects: Guest mode storage only
  - Change scope: Remove enforceGuestDraftLimit() method

- **PlannerMDNewPage.tsx, PlannerMDEditPage.tsx** (Low impact - page isolated)
  - What depends: Editor UI, save button behavior
  - Ripple effects: None (page-level components)
  - Change scope: Add beforeunload listener, display timestamp

- **i18n files** (Low impact)
  - Used by: All UI components
  - What depends: Status text display
  - Ripple effects: None (additive changes only)
  - Change scope: Add new translation keys

- **constants.ts** (Low impact)
  - Used by: usePlannerStorage
  - What depends: Draft limit enforcement (being removed)
  - Ripple effects: None (removal of unused constant)

**High-impact files to watch:**
- None - changes are contained to planner domain and use existing abstraction layers

**Potential ripple effects:**
- Backend: Server load drops 99% (18K req/hr → 100 req/hr on PUT endpoint)
- Frontend: Cross-device sync becomes eventual (on manual save) instead of near real-time
- Storage: Browser quota management shifts to browser's built-in limits (~50MB-1GB)

## Risk Assessment

**Edge cases identified:**

1. **Cross-device concurrent editing:**
   - Scenario: User edits on Device A (auto-save local), edits on Device B (auto-save local), manually saves on both
   - Behavior: Second save shows conflict dialog (syncVersion mismatch)
   - Mitigation: Existing conflict resolution handles this (Overwrite/Discard choice)
   - Status: Already implemented in usePlannerSave.resolveConflict()

2. **Browser crash before manual save:**
   - Scenario: User edits for 30 mins, browser crashes, never manually saved
   - Behavior: IndexedDB persists local edits, available on next browser open
   - Risk: User might not return to same browser/device
   - Mitigation: beforeunload warning + "Last synced" timestamp creates awareness

3. **Auth session expiry during editing:**
   - Scenario: User edits for 30 mins, JWT expires, clicks manual save
   - Behavior: Manual save fails with auth error, prompts re-login
   - Mitigation: Existing auth flow handles re-login, local data preserved in IndexedDB
   - Status: No changes needed, existing error handling works

4. **IndexedDB quota exceeded:**
   - Scenario: User creates many large planners, browser quota exceeded (~50MB-1GB)
   - Behavior: Auto-save fails, error code 'quotaExceeded'
   - Mitigation: Existing error handling in usePlannerSave catches this
   - Action: Verify UI shows quota error message to user

5. **Rapid manual save clicks:**
   - Scenario: User clicks Save button twice rapidly
   - Behavior: Second click ignored (isSaving flag disables button)
   - Mitigation: Existing isSaving state prevents duplicate saves
   - Status: Already implemented, no changes needed

6. **Incomplete planner validation:**
   - Scenario: User auto-saves planner with missing theme packs
   - Behavior: IndexedDB accepts incomplete data (no validation)
   - Server behavior: Manual save validates, rejects if incomplete
   - Mitigation: User sees validation error on manual save attempt

7. **Auto-save mechanism clarification:**
   - Verified behavior: Auto-save fires once per edit after 2s, NOT continuously looping
   - State comparison in debouncedSave() prevents redundant saves
   - Dependency array [state, debouncedSave] is correct, no infinite loop
   - Current issue: Server validation rejects incomplete planners, fixed by local-first

**Performance concerns:**
- IndexedDB write speed: ~5-10ms per save (negligible, non-blocking async)
- SSE connection overhead: No change (still needed for manual saves)
- CPU usage: Auto-save to IndexedDB is cheaper than network requests
- State comparison: JSON.stringify of full state (~few ms, acceptable)

**Backward compatibility:**
- Existing saved planners: No migration needed (PlannerStatus enum unchanged)
- Server API: No changes required (same endpoints, lower usage)
- Guest mode data: No changes (still IndexedDB only)
- syncVersion tracking: Preserved for conflict detection

**Security considerations:**
- Local data exposure: Same as before (IndexedDB sandboxed per origin)
- Auth token handling: No changes (still HTTP-only cookies)
- XSS protection: No changes (existing CSP headers still apply)

## Testing Guidelines

### Manual UI Testing

**Phase 0 Verification (Draft Limit Removal):**
1. Open browser as guest user
2. Navigate to `/planner/md/new`
3. Create 5 new planners without manually saving
4. Wait 2s after each edit (auto-save triggers)
5. Navigate to `/planner/md` (list view)
6. Verify all 5 planners exist in list
7. Create 10 more planners (total 15)
8. Verify no quota errors appear in console
9. Verify all 15 planners exist in list
10. Close browser completely
11. Reopen browser and navigate to `/planner/md`
12. Verify all 15 planners still exist (IndexedDB persistence)

**Phase 1 Verification (Auto-Save Routing):**
1. Open browser as authenticated user (login first)
2. Open DevTools Network tab, filter by "planner"
3. Navigate to `/planner/md/new`
4. Edit planner title to "Test Auto-Save Local-First"
5. Wait 3 seconds after last edit (exceed 2s debounce)
6. Verify in Network tab: NO PUT requests to `/api/planner/md/*`
7. Verify in Network tab: NO POST requests to `/api/planner/md`
8. Open DevTools → Application → IndexedDB → danteplanner → planner
9. Verify planner entry exists with title "Test Auto-Save Local-First"
10. Click manual "Save" button in UI
11. Verify in Network tab: PUT or POST request appears to `/api/planner/md/*`
12. Verify response status 200 or 201
13. Verify response body contains incremented syncVersion
14. As guest user, repeat steps 3-9
15. Verify guest auto-save also writes to IndexedDB only

**Phase 2 Verification (Status Terminology):**
1. As authenticated user, create new planner
2. Edit planner content (e.g., change title)
3. Wait 2s for auto-save to trigger
4. Navigate to `/planner/md` (personal planner list)
5. Locate the edited planner card
6. Verify top-right corner shows badge with text "Unsynced"
7. Open the planner again
8. Click manual "Save" button
9. Return to `/planner/md`
10. Verify badge now shows "Synced"
11. Logout (or open incognito window as guest)
12. As guest user, create and edit a planner
13. Navigate to list view
14. Verify all planner cards show "Local" badge
15. Switch UI language to Korean (Settings page)
16. Verify status badges translate correctly (동기화되지 않음 / 동기화됨 / 로컬)
17. Repeat for Japanese and Chinese languages

**Phase 3 Verification (beforeunload Warning):**
1. As authenticated user, create new planner
2. Edit planner content (title, add identity, etc.)
3. Wait 2s for auto-save to IndexedDB
4. Attempt to close browser tab (Ctrl+W or click X)
5. Verify browser shows native warning dialog
6. Verify dialog text mentions "unsynced changes"
7. Cancel the close action (click Cancel)
8. Click manual "Save" button in planner UI
9. Wait for save to complete (check Network tab for 200 response)
10. Attempt to close tab again
11. Verify NO warning dialog appears (tab closes cleanly)
12. As guest user, create and edit planner
13. Attempt to close tab
14. Verify NO warning appears (guests have no sync concept)
15. As auth user, open multiple tabs with different planners
16. Edit in Tab A, attempt to close Tab A
17. Verify warning appears only in Tab A (independent tracking)

**Phase 4 Verification (Last Synced Timestamp):**
1. As authenticated user, create new planner
2. Locate save button area in UI
3. Verify text shows "Never synced" near save button
4. Click manual "Save" button
5. Verify text immediately changes to "Last synced: a few seconds ago"
6. Wait 1 minute without refreshing page
7. Verify text updates to "Last synced: 1 minute ago" (or similar)
8. Edit planner content (trigger auto-save)
9. Wait 2s for auto-save to IndexedDB
10. Verify timestamp text remains unchanged (still shows last manual save time)
11. Wait 5 more minutes
12. Verify text updates to "Last synced: 6 minutes ago"
13. Click manual "Save" button again
14. Verify timestamp resets to "Last synced: a few seconds ago"
15. Refresh browser page
16. Verify timestamp shows "Never synced" (state not persisted)
17. As guest user, create planner
18. Verify NO timestamp displayed anywhere (no sync concept for guests)

### Automated Functional Verification

**Phase 0 (Draft Limit Removal):**
- [ ] Draft limit removed: Can create 20+ local drafts without app-enforced errors
- [ ] No quota enforcement: enforceGuestDraftLimit() method deleted
- [ ] Existing drafts persist: Browser reload shows all drafts in list
- [ ] Browser quota natural limit: App doesn't prevent storage up to browser's ~50MB-1GB limit

**Phase 1 (Auto-Save Routing):**
- [ ] Auto-save routing authenticated: All auto-saves write to IndexedDB (verify in DevTools Application tab)
- [ ] Auto-save routing guest: Guest auto-saves write to IndexedDB only
- [ ] No network during auto-save: DevTools Network tab shows 0 planner requests during 2s after edit
- [ ] Manual save routing auth: Auth users trigger PUT/POST to server on manual save
- [ ] Manual save routing guest: Guest manual saves write to IndexedDB only
- [ ] Server load reduction: Backend logs show PUT request frequency drops to manual-save-only
- [ ] State comparison works: Editing same field to same value doesn't trigger duplicate save

**Phase 2 (Status Display):**
- [ ] Status "Unsynced" displays: Auth users see "Unsynced" badge for auto-saved planners
- [ ] Status "Synced" displays: Auth users see "Synced" badge after manual save
- [ ] Status "Local" displays: Guest users see "Local" badge for all planners
- [ ] i18n English: Status text shows in English when language is EN
- [ ] i18n Korean: Status text translates to Korean when language is KR
- [ ] i18n Japanese: Status text translates to Japanese when language is JP
- [ ] i18n Chinese: Status text translates to Chinese when language is CN
- [ ] Badge position: Status appears in top-right corner of planner cards (existing position)
- [ ] Enum unchanged: Backend still receives 'draft' or 'saved', no migration needed

**Phase 3 (beforeunload Warning):**
- [ ] Warning fires for auth: Auth users with unsynced changes see browser warning on close
- [ ] Warning skips when synced: Auth users after manual save have clean close (no warning)
- [ ] Warning skips for guest: Guest users never see warning (no sync concept)
- [ ] State tracking accurate: hasUnsyncedChanges flag correctly reflects sync state
- [ ] Multi-tab independence: Each tab tracks sync state independently
- [ ] Navigation independence: Warning only fires on browser close/reload, not in-app navigation

**Phase 4 (Timestamp Display):**
- [ ] Timestamp updates on save: Text changes to "a few seconds ago" after manual save
- [ ] Timestamp accuracy: Relative time updates as minutes pass (1 min → 5 min → 10 min)
- [ ] Timestamp "Never synced": Shows "Never synced" for planners never manually saved
- [ ] Timestamp visibility auth: Only shown to authenticated users
- [ ] Timestamp hidden guest: Not displayed for guest users
- [ ] Auto-save independence: Timestamp unchanged when auto-save triggers
- [ ] Refresh behavior: Timestamp resets to "Never synced" on page reload (not persisted)

### Edge Cases

**Phase 0 (Draft Limit):**
- [ ] Browser quota natural limit: Browser shows quota error when exceeding browser's storage limit
- [ ] No artificial limit: Can create 100+ small planners without app-enforced errors
- [ ] Old draft deletion removed: Oldest drafts are NOT auto-deleted anymore

**Phase 1 (Auto-Save Routing):**
- [ ] Offline auto-save: IndexedDB writes succeed even when network offline
- [ ] Auth expiry during auto-save: Auto-save succeeds locally, expiry caught only on manual save
- [ ] Rapid edits: Debounce timer resets on each keystroke, only saves after 2s quiet period
- [ ] State unchanged: Editing field A → B → A doesn't save (state comparison detects no change)
- [ ] Multiple sections: Editing different sections all auto-save correctly

**Phase 2 (Status Display):**
- [ ] Status transition auto→manual: Auto-save changes "Synced" → "Unsynced" for edited planners
- [ ] Status transition manual→synced: Manual save changes "Unsynced" → "Synced"
- [ ] Guest consistency: Status always shows "Local" regardless of number of saves
- [ ] Language switch: Status badge updates immediately when language changes

**Phase 3 (beforeunload Warning):**
- [ ] Multiple tabs: Each tab independently tracks sync state, warns separately
- [ ] Login state change: Switching from guest → auth updates warning behavior
- [ ] Navigation within app: beforeunload only fires on browser close/reload, not on React Router navigation
- [ ] Manual save during warning: If user saves then closes immediately, no warning

**Phase 4 (Timestamp Display):**
- [ ] Clock skew: Relative time handles browser clock changes gracefully
- [ ] Never-saved state: "Never synced" shows for new planners until first manual save
- [ ] Session persistence: Timestamp resets on browser reload (fresh state, not persisted to IndexedDB)
- [ ] Save failure: Timestamp doesn't update if manual save fails with error

### Integration Points

**Cross-Device Sync:**
- [ ] SSE notifications: Manual save on Device A triggers SSE event on Device B
- [ ] Query invalidation: Device B refreshes list view when SSE event received
- [ ] Conflict detection: Editing same planner on A & B, then manually saving both shows conflict dialog
- [ ] Overwrite resolution: Choosing "Overwrite" forces save with incremented syncVersion
- [ ] Discard resolution: Choosing "Discard" reloads server version, discards local edits

**Backend Integration:**
- [ ] Server load: Backend logs show 99% reduction in PUT request frequency
- [ ] POST endpoint: POST `/api/planner/md` still works for create (manual save)
- [ ] PUT endpoint: PUT `/api/planner/md/{id}` still works for update (manual save only)
- [ ] SSE service: Still broadcasts planner-update events for manual saves
- [ ] syncVersion: Server increments version, client tracks correctly for conflicts

**List View Integration:**
- [ ] Planner list: Shows planners from both IndexedDB (unsynced) and server (synced)
- [ ] Status badges: Correctly reflects sync state for each planner in list
- [ ] Guest mode: List shows only IndexedDB planners with "Local" status
- [ ] Auth mode: List merges IndexedDB + server planners, shows correct status per planner
- [ ] Sorting: List still sorts by lastModifiedAt (newest first)

**Error Handling:**
- [ ] Quota exceeded: UI shows "quotaExceeded" error message when IndexedDB quota exceeded
- [ ] Network failure on manual save: Shows "saveFailed" error, local IndexedDB data preserved
- [ ] Conflict error: Shows conflict dialog with server version details (syncVersion mismatch)
- [ ] Auth error on manual save: Prompts re-login flow, local data preserved in IndexedDB
- [ ] Validation error on manual save: Server rejects incomplete planner, shows validation errors
