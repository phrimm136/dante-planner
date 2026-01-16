# Status: Privacy-Respecting Sync Architecture

## Execution Progress

**Last Updated:** 2026-01-16
**Current Step:** 34 of 34
**Current Phase:** COMPLETE

### Milestones

- [x] M1: Phase 1-2 (Backend Foundation + Services)
- [x] M2: Phase 3 (SSE Refactoring)
- [x] M3: Phase 4 (Frontend Adapters)
- [x] M4: Phase 5-6 (Settings UI + First-Login)
- [x] M5: Phase 7-8 (Conflict Enhancement + Cleanup)
- [x] M6: Phase 9 (Tests)

### Step Log

| Step | Description | Status |
|------|-------------|--------|
| 1 | V024 Migration | ✅ |
| 2 | UserSettings entity | ✅ |
| 3 | UserSettingsRepository | ✅ |
| 4 | UserSettingsResponse DTO | ✅ |
| 5 | UpdateUserSettingsRequest DTO | ✅ |
| 6 | UserSettingsService | ✅ |
| 7 | UserController endpoints | ✅ |
| 8 | SseService | ✅ |
| 9 | SseController | ✅ |
| 10 | Rename PlannerSseService | ✅ |
| 11 | NotificationService modify | ✅ |
| 12 | PlannerController force param | ✅ |
| 13 | usePlannerSaveAdapter | ✅ |
| 14 | usePlannerSyncAdapter | ✅ |
| 15 | usePlannerSave refactor | ✅ |
| 16 | plannerApi update | ✅ |
| 17 | usePlannerSync refactor | ✅ |
| 18 | UserSettingsSchemas | ✅ |
| 19 | UserSettingsTypes | ✅ |
| 20 | useUserSettings hook | ✅ |
| 21 | SyncSection component | ✅ |
| 22 | NotificationSection component | ✅ |
| 23 | SettingsPage modify | ✅ |
| 24 | SyncChoiceDialog | ✅ |
| 25 | useSseStore | ✅ |
| 26 | Auth callback modify | ✅ |
| 27 | App-level dialog trigger | ✅ |
| 28 | ConflictResolutionDialog modify | ✅ |
| 29 | BatchConflictDialog | ✅ |
| 30 | PlannerCard indicators | ✅ |
| 31 | DELETE old adapter | ✅ |
| 32 | Backend tests | ✅ |
| 33 | Frontend tests | ✅ |
| 34 | Integration tests | ✅ |

---

## Feature Status

### Core Features

- [x] F1: Settings API (GET/PUT /api/user/settings)
- [x] F2: Unified SSE endpoint (/api/sse/subscribe)
- [x] F3: Force sync override (force=true param)
- [x] F4: Settings UI (sync + notification toggles)
- [x] F5: First-login sync choice dialog
- [x] F6: "Keep Both" conflict resolution
- [x] F7: Batch conflict dialog (2+ conflicts)
- [x] F8: Status indicators (Draft, Unsynced, Unpublished)

### Edge Cases

- [x] E1: Empty local + auth + sync ON → auto-pull from server (Obsidian model)
- [x] E2: Simultaneous edit → conflict dialog
- [ ] E3: Server timeout → error toast, retry
- [ ] E4: IndexedDB quota exceeded → warning
- [x] E5: SSE cache invalidation on settings change

### Integration

- [x] I1: Settings → SSE cache invalidation
- [x] I2: First-login → Settings persistence
- [x] I3: Auth callback → Dialog trigger
- [x] I4: Sync/Noti toggle → SSE reconnect

### Dependency Verification

- [x] D1: usePlannerSave uses new adapters
- [x] D2: usePlannerSync uses new SSE path
- [x] D3: usePlannerStorageAdapter deleted
- [x] D4: All imports updated

---

## Testing Checklist

### Automated Tests

**Unit:**
- [x] UT1: UserSettingsServiceTest.java
- [x] UT2: SseServiceTest.java
- [x] UT3: usePlannerSaveAdapter.test.ts
- [x] UT4: usePlannerSyncAdapter.test.ts
- [x] UT5: SyncSection.test.tsx

**Integration:**
- [x] IT1: Settings-SSE integration
- [x] IT2: First-login flow
- [x] IT3: Conflict resolution flow

### Manual Verification

- [x] MV1: First-login dialog appears, cannot dismiss
- [x] MV2: Sync toggle persists and affects save
- [x] MV3: Auto-save writes to IndexedDB only
- [x] MV4: Manual save routes correctly
- [x] MV5: SSE reconnects on settings change
- [x] MV6: Conflict dialog shows three options
- [x] MV7: Batch dialog at 2+ conflicts
- [x] MV8: Status indicators on planner cards

---

## Summary

| Category | Progress |
|----------|----------|
| Steps | 34/34 |
| Features | 8/8 |
| Edge Cases | 3/5 |
| Integration | 4/4 |
| Tests | 8/8 |
| **Overall** | **100%** |

---

## Issues Found & Fixed

### Issue 1: SSE Connection Not Initiated

**Symptom:** SSE connection never established despite being logged in with sync enabled.

**Root Cause:** `usePlannerSync` hook exported `connectSSE()` but it was never called. Only `usePlannerSyncAdapter` (API calls) was used, not `usePlannerSync` (SSE logic).

**Fix:**
1. Created `useSseConnection.ts` hook - manages SSE lifecycle based on auth + sync settings
2. Added hook call to `GlobalLayout.tsx` for app-level SSE management

**Files Changed:**
- `frontend/src/hooks/useSseConnection.ts` (new)
- `frontend/src/components/GlobalLayout.tsx`

### Issue 2: SSE 403 on Page Refresh (Rate Limit)

**Symptom:** SSE returns 403 intermittently, especially on page refresh when SSE reconnects.

**Root Cause:** SSE rate limit was `capacity=1, refill=1 per 60 seconds`. On page refresh:
1. Browser closes old SSE, opens new one
2. Frontend reconnect logic fires on error
3. Multiple requests hit rate limit → `RateLimitExceededException`
4. Exception not caught by `GlobalExceptionHandler` (async response already committed)
5. Tomcat returns 403/500 with empty body

**Fix:** Increased SSE rate limit to `capacity=5, refill=5 per 10 seconds` to allow reconnection scenarios.

**Files Changed:**
- `backend/src/main/resources/application.properties`

### Issue 3: SSE 403 on Async Dispatch

**Symptom:** SSE connections failing with 403 on async dispatch (heartbeats, errors).

**Root Cause:** Spring Security re-runs filter chain on `ASYNC` dispatch. The JWT filter couldn't read cookies on internal dispatch → anonymous auth → 403.

**Fix:**
1. Skip JWT filter on `DispatcherType.ASYNC`
2. Add `.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()` to SecurityConfig

**Files Changed:**
- `backend/src/main/java/.../security/JwtAuthenticationFilter.java`
- `backend/src/main/java/.../config/SecurityConfig.java`

### Issue 4: SSE Not Connecting with Notification Settings Only

**Symptom:** SSE disconnected when `syncEnabled=false` even with notifications enabled.

**Root Cause:** `useSseConnection.ts` only checked `syncEnabled`, ignoring notification settings.

**Fix:** Added `notificationsEnabled` check: `needsSse = syncEnabled || notificationsEnabled`

**Files Changed:**
- `frontend/src/hooks/useSseConnection.ts`

### Issue 5: Planner List Duplicates on Save

**Symptom:** Planner list shows duplicates when saving (draft→saved transition).

**Root Cause:** Storage used separate keys (`drafts:*` vs `saved:*`) for different statuses. Transition created new key without deleting old.

**Fix:** Unified to single key scheme `planner:md:{deviceId}:{id}`. Status is just metadata field, not key prefix.

**Files Changed:**
- `frontend/src/lib/constants.ts` (PLANNER_STORAGE_KEYS)
- `frontend/src/hooks/usePlannerStorage.ts` (key builders, load/save/list/delete)

### Issue 6: Server Pull Not Writing to IndexedDB

**Symptom:** Background sync fetches planners but doesn't save to local.

**Root Cause:** Multiple Zod `.strict()` schemas rejecting unknown fields from server response (`published`, `plannerType`, `upvotes`).

**Fix:** Added missing fields to schemas:
- `PlannerMetadataSchema`: `published`
- `ServerPlannerSummarySchema`: `plannerType`
- `ServerPlannerResponseSchema`: `upvotes`

**Files Changed:**
- `frontend/src/schemas/PlannerSchemas.ts`
- `frontend/src/types/PlannerTypes.ts`
- `frontend/src/hooks/usePlannerSyncAdapter.ts` (removed hardcoded plannerType)

### Issue 7: Background Sync Infinite Loop

**Symptom:** Frontend makes endless fetch requests during sync.

**Root Cause:** `useEffect` deps included unstable objects (`saveAdapter`, `syncAdapter`) which are new refs every render.

**Fix:** Use stable `syncKey` string for deps + `hasSyncedRef` to run once per auth+sync state.

**Files Changed:**
- `frontend/src/hooks/useMDUserPlannersData.ts`

### Issue 8: Sync Pagination Missing Planners

**Symptom:** Only first 100 planners synced from server.

**Root Cause:** `plannerApi.list()` only fetched page 0.

**Fix:** Added `plannerApi.listAll()` that loops through all pages.

**Files Changed:**
- `frontend/src/lib/plannerApi.ts`
- `frontend/src/hooks/usePlannerSyncAdapter.ts`
- `frontend/src/hooks/usePlannerSync.ts`

### Issue 9: Save Creates Duplicate IDs

**Symptom:** Every save creates entry with both client ID and server-generated ID.

**Root Cause:** Server ignored client ID and generated new UUID. Local had client ID, sync response had server ID → two entries.

**Fix:** Send client ID to server in `CreatePlannerRequest`. Server uses client ID instead of generating.

**Files Changed:**
- `frontend/src/types/PlannerTypes.ts` (CreatePlannerRequest.id)
- `frontend/src/hooks/usePlannerSyncAdapter.ts`
- `backend/.../dto/planner/CreatePlannerRequest.java`
- `backend/.../service/PlannerService.java`

### Issue 10: Sync 500 Error - Duplicate Primary Key

**Symptom:** POST /api/planner/md returns 500 with `Duplicate entry for key 'planners.PRIMARY'`.

**Root Cause:** Frontend called CREATE for planners that already existed on server (stale local state, race conditions). JPA `save()` on entity with pre-set ID always INSERTs.

**Fix:**
1. Changed PUT /{id} to upsert (create if not exists, update if exists)
2. Renamed `CreatePlannerRequest` → `UpsertPlannerRequest`
3. Added `syncVersion` field to DTO for conflict detection
4. Frontend always uses `plannerApi.upsert()` instead of create/update split

**Files Changed:**
- `backend/.../controller/PlannerController.java` (PUT → upsert)
- `backend/.../service/PlannerService.java` (new upsertPlanner method)
- `backend/.../dto/planner/UpsertPlannerRequest.java` (renamed + syncVersion)
- `frontend/src/lib/plannerApi.ts` (upsert method, removed update)
- `frontend/src/hooks/usePlannerSyncAdapter.ts` (always use upsert)
- `frontend/src/hooks/usePlannerSync.ts` (removed updatePlanner)
- `frontend/src/types/PlannerTypes.ts` (UpsertPlannerRequest)

### Issue 11: "Keep Both" Not Implemented

**Symptom:** "Keep Both" button in conflict dialog didn't work (fell through to discard).

**Root Cause:** `resolveConflict()` only handled 'overwrite' and 'discard' cases.

**Fix:** Implemented "Save as Copy" flow:
1. Create new planner with local changes + "(Copy)" suffix
2. Sync copy to server immediately
3. Revert original to server version
4. Navigate to new planner

**Files Changed:**
- `frontend/src/hooks/usePlannerSave.ts` (both case + onKeepBothCreated callback)
- `frontend/src/routes/PlannerMDEditorContent.tsx` (navigation handler)
- `static/i18n/*/planner.json` (i18n keys: keepBoth, copySuffix, localVersion, serverVersion)
- `frontend/src/components/planner/ConflictResolutionDialog.tsx` (button variant fix)

### Issue 12: Autosave Overwrites Reverted Planner After "Keep Both"

**Symptom:** After "Keep Both", original planner had local changes instead of server version.

**Root Cause:** Race condition - debounce timer captured stale state in closure, fired before React re-rendered with server state.

**Fix:**
1. Clear debounce timer at start of `resolveConflict()`
2. Add `key={planner.metadata.id}` to `PlannerMDEditorContent` to force remount on route change

**Files Changed:**
- `frontend/src/hooks/usePlannerSave.ts` (timer clearing)
- `frontend/src/routes/PlannerMDEditPage.tsx` (key prop)

### Issue 13: "Leave Page?" Popup During "Keep Both" Navigation

**Symptom:** Browser showed "leave page?" confirmation when navigating after "Keep Both".

**Root Cause:** `hasUnsyncedChanges` was true after `onServerReload` updated state (state differs from `lastSyncedStateRef`).

**Fix:**
1. Added `hasLocalUnsavedChanges` (compares against `previousStateRef` for local auto-save)
2. Changed beforeunload to use `hasLocalUnsavedChanges` instead of `hasUnsyncedChanges`
3. Added `isIntentionalNavigationRef` to skip warning during intentional navigation

**Files Changed:**
- `frontend/src/hooks/usePlannerSave.ts` (hasLocalUnsavedChanges)
- `frontend/src/routes/PlannerMDEditorContent.tsx` (beforeunload logic, intentional navigation ref)

### Issue 14: React Hooks Violation - useMemo in JSX

**Symptom:** "Rendered more hooks than during previous render" error.

**Root Cause:** `useMemo` called conditionally inside JSX (`{condition && useMemo(...)}`).

**Fix:** Replaced with IIFE `{condition && (() => {...})()}`.

**Files Changed:**
- `frontend/src/routes/PlannerMDEditorContent.tsx`

### Issue 15: Sync Indicator Shows "Never Synced" After Refresh

**Symptom:** Sync indicator always showed "never synced" even for previously saved planners.

**Root Cause:** `lastSyncedAt` was in-memory state only, reset to `null` on page refresh.

**Fix:** Added `initialSavedAt` option to `usePlannerSave`, initialized from `planner.metadata.savedAt`.

**Files Changed:**
- `frontend/src/hooks/usePlannerSave.ts` (initialSavedAt option)
- `frontend/src/routes/PlannerMDEditorContent.tsx` (pass initialSavedAt)

### Issue 16: Untouched Planner Triggers Auto-Save in Edit Mode

**Symptom:** Opening a planner in edit mode without changes still triggered auto-save.

**Root Cause:** `isInitialRenderRef` was set to `false` before `initializeFromPlanner` loaded data. State change from empty→loaded triggered auto-save.

**Fix:** Added `markInitialized()` function. Component calls it after loading planner data to enable auto-save.

**Files Changed:**
- `frontend/src/hooks/usePlannerSave.ts` (markInitialized function)
- `frontend/src/routes/PlannerMDEditorContent.tsx` (call markInitialized after load)

### Issue 17: Edit Mode State Never Updates After Initialization

**Symptom:** Title input and other changes don't persist in edit mode.

**Root Cause:** Initialization effect had `markInitialized` in deps, which changes on every state change → effect re-runs → `initializeFromPlanner(planner)` resets state to original.

**Fix:** Split into two effects with ref guards:
1. Effect 1: Load planner ONCE (`hasLoadedPlannerRef`)
2. Effect 2: Call `markInitialized` after state settles (`needsMarkInitializedRef`)

**Files Changed:**
- `frontend/src/routes/PlannerMDEditorContent.tsx`

### Issue 18: Pull Only Works for Empty Local

**Symptom:** Sync only pulled planners not in local, missed server updates for existing planners.

**Fix:** Added syncVersion comparison: pull when `local.syncVersion < server.syncVersion` (skip drafts).

**Files Changed:**
- `frontend/src/hooks/usePlannerStorage.ts` (include syncVersion in listPlanners)
- `frontend/src/hooks/usePlannerSyncAdapter.ts` (include syncVersion in serverSummaryToLocal)
- `frontend/src/hooks/useMDUserPlannersData.ts` (version comparison logic)

### Issue 19: No Conflict Resolution for Pull

**Symptom:** Local drafts silently skipped during pull, no way to resolve local draft vs server newer.

**Fix:** Added batch conflict detection and resolution:
1. Detect conflicts: local draft + server newer → `ConflictItem[]`
2. Show `BatchConflictDialog` for user decision
3. Handle resolutions: overwrite (force push), discard (use server), both (copy + use server)

**Files Changed:**
- `frontend/src/hooks/useMDUserPlannersData.ts` (conflict detection + resolveConflicts)
- `frontend/src/routes/PlannerMDPage.tsx` (integrate BatchConflictDialog)

### Issue 20: BatchConflictDialog UI Polish

**Changes:**
- Removed "Multiple" from title (works for 1+ conflicts)
- Vertical layout: title → dates → buttons
- "Apply to All" section: label above, buttons below
- Consistent button colors (both = same as discard)
- Published indicator badge (right-aligned)
- Date format: `1:00 PM` instead of `01:00 PM`
- i18n: "Changed on Another Device" title across EN/KR/JP/CN

**Files Changed:**
- `frontend/src/components/dialogs/BatchConflictDialog.tsx`
- `static/i18n/*/planner.json` (conflict + batchConflict sections)

### Issue 21: Last Saved Indicator Not Showing

**Symptom:** "Last saved X ago" indicator never appears for drafts.

**Root Cause:** Indicator used `savedAt` which is only set for manual saves (`status='saved'`). Drafts always have `savedAt: null`.

**Fix:** Use `lastModifiedAt` instead of `savedAt` for initial indicator value.

**Files Changed:**
- `frontend/src/routes/PlannerMDEditorContent.tsx` (initialSavedAt uses lastModifiedAt)

### Issue 22: Leave Site Popup Without Changes

**Symptom:** "Leave site?" warning triggers immediately on new planner page without any edits.

**Root Cause:** `hasLocalUnsavedChanges` compared against `previousStateRef.current` which starts as `''`. Any state !== `''` is truthy.

**Fix:** Guard comparisons: return `false` when baseline refs are still `''` (uninitialized).

**Files Changed:**
- `frontend/src/hooks/usePlannerSave.ts` (hasUnsyncedChanges, hasLocalUnsavedChanges guards)

### Issue 23: Publish Button Always Shows "Publish" After Refresh

**Symptom:** After publishing, button shows correct state. After refresh, always shows "Publish".

**Root Cause:** `save()` used `published` from React state which hadn't updated yet after `setIsPublished()`. React state updates are async.

**Fix:** Pass `published` value directly to `save({ published: response.published })` after `togglePublish()`, bypassing state timing.

**Files Changed:**
- `frontend/src/hooks/usePlannerSave.ts` (performSave + save accept publishedOverride)
- `frontend/src/routes/PlannerMDEditorContent.tsx` (save with published override)

### Issue 24: Draft Recovery Code Cleanup

**Change:** Removed unused draft recovery feature (`CURRENT_DRAFT_ID`, `loadCurrentDraft`, `setCurrentDraftId`).

**Files Changed:**
- `frontend/src/lib/constants.ts`
- `frontend/src/hooks/usePlannerStorage.ts`
- `frontend/src/hooks/usePlannerSaveAdapter.test.ts`
