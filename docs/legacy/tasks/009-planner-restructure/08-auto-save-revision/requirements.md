# Task: Unified Planner Save Hook with Conflict Resolution

## Description
Refactor the planner saving mechanism to unify auto-save and manual save into a single hook, fix critical bugs, and add proper error handling with conflict resolution dialog.

### Current Issues (identified via /arch-research)
1. **Manual save ignores syncVersion** (HIGH): `PlannerMDNewPage.tsx:538` hardcodes `syncVersion: 1` instead of using tracked version, breaking conflict detection
2. **Auto-save errors never shown** (MEDIUM): `usePlannerAutosave.ts` returns `errorCode` but page ignores it - users don't see save failures
3. **Fragile error detection**: Uses `error.message.includes('409')` instead of typed error checking
4. **Duplicate save logic**: Same `SaveablePlanner` construction in two places (auto-save hook and manual save handler)

### Required Behavior
- **Unified hook** (`usePlannerSave`): Single source of truth for both auto-save (debounced 2s) and manual save
- **Shared syncVersion tracking**: Both auto-save and manual save use the same tracked version
- **Error display**: Show toast notifications for save failures (network, validation, quota)
- **Conflict resolution**: On 409 error, show dialog with two choices:
  - **Overwrite**: Force-save local version (send with incremented syncVersion)
  - **Discard**: Reload from server (lose local changes)
- **No auto-retry**: Fail-fast for transient errors (user decision)
- **No merge strategy**: Deferred to TODO.md (UX-003)

### Network Consumption Analysis (AWS Free Tier)
- Current debounce: 2000ms - **Keep as-is** (optimal for AWS free tier)
- Typical payload: ~12KB, worst case: 50KB (backend limit)
- Dirty checking exists: Only saves when state actually changes
- Estimated monthly traffic (100 users): 3.6-90 GB (well within 100 GB free tier)

## Research
- [x] Current auto-save implementation: `hooks/usePlannerAutosave.ts`
- [x] Storage adapter routing: `hooks/usePlannerStorageAdapter.ts`
- [x] API client error handling: `lib/api.ts` (throws generic `Error('HTTP error! status: 409')`)
- [x] IndexedDB storage: `hooks/usePlannerStorage.ts`
- [x] Backend conflict handling: `GlobalExceptionHandler.java` returns 409 with `ConflictErrorResponse`
- [ ] Existing dialog patterns for similar UX (draft recovery dialog in PlannerMDNewPage)

## Scope
Read for context:
- `frontend/src/hooks/usePlannerAutosave.ts` - Current auto-save implementation
- `frontend/src/hooks/usePlannerStorageAdapter.ts` - Routes between server/IndexedDB
- `frontend/src/hooks/usePlannerStorage.ts` - IndexedDB operations
- `frontend/src/routes/PlannerMDNewPage.tsx` - Current manual save handler (lines 530-571)
- `frontend/src/lib/api.ts` - API error handling patterns
- `frontend/src/lib/constants.ts` - `AUTO_SAVE_DEBOUNCE_MS`, `MAX_GUEST_DRAFTS`
- `frontend/src/types/PlannerTypes.ts` - Existing type definitions

## Target Code Area
Files to CREATE:
- `frontend/src/hooks/usePlannerSave.ts` - New unified save hook
- `frontend/src/components/planner/ConflictResolutionDialog.tsx` - Dialog for 409 conflicts

Files to MODIFY:
- `frontend/src/types/PlannerTypes.ts` - Add conflict resolution types
- `frontend/src/routes/PlannerMDNewPage.tsx` - Use new unified hook, remove duplicate save logic
- `frontend/src/lib/api.ts` - Add typed error class for 409 conflicts

Files to DELETE:
- `frontend/src/hooks/usePlannerAutosave.ts` - Merged into usePlannerSave

## System Context (Senior Thinking)
- **Feature domain**: Planner Sync (from architecture-map)
- **Core files in this domain**:
  - `hooks/usePlannerSync.ts`
  - `hooks/usePlannerStorageAdapter.ts`
  - `hooks/usePlannerMigration.ts`
  - `lib/plannerApi.ts`
- **Cross-cutting concerns touched**:
  - Error handling (toast notifications)
  - IndexedDB storage
  - Server sync with optimistic locking
  - i18n (error message translations)

## Impact Analysis
- **Files being modified**:
  - `PlannerMDNewPage.tsx` (Low impact - page isolated)
  - `PlannerTypes.ts` (Low impact - adding new types)
  - `api.ts` (Medium impact - used by all API calls)
- **What depends on usePlannerAutosave**: Only `PlannerMDNewPage.tsx`
- **Potential ripple effects**:
  - Changing API error handling affects all API consumers
  - New hook API must be compatible with existing page state management
- **High-impact files to watch**: `lib/api.ts` (if modifying error handling)

## Risk Assessment
- **Edge cases**:
  - What if conflict dialog is shown but user closes browser? (Local changes lost)
  - What if server is unreachable during conflict resolution? (Show network error)
  - Guest mode has no server conflicts (IndexedDB only)
- **Backward compatibility**:
  - Existing IndexedDB data format unchanged
  - API contract unchanged (409 already returned)
- **Performance concerns**:
  - Dirty checking already optimized (JSON.stringify comparison)
  - 2s debounce prevents excessive saves
- **Security considerations**: None (no auth changes)

## Testing Guidelines

### Manual UI Testing
1. Open browser and navigate to /planner/md/new
2. Make a change to the planner (e.g., change title)
3. Wait 2+ seconds for auto-save indicator
4. Verify "Saving..." indicator appears briefly
5. Verify no error toast appears (successful save)
6. Click the manual "Save" button
7. Verify save succeeds with success toast

**Conflict scenario (requires two browser windows):**
8. Open same planner in two browser windows (Window A, Window B)
9. In Window A, make a change and wait for auto-save
10. In Window B, make a different change and click Save
11. Verify conflict dialog appears in Window B with "Overwrite" and "Discard" options
12. Click "Discard" - verify Window B reloads with Window A's version
13. Repeat steps 8-10
14. Click "Overwrite" - verify Window B's changes are saved

**Error scenarios:**
15. Disconnect network, make a change, wait for auto-save
16. Verify error toast appears (network error)
17. Reconnect network, verify next auto-save succeeds

### Automated Functional Verification
- [ ] Auto-save triggers after 2s debounce when state changes
- [ ] Auto-save skips if state unchanged (dirty checking)
- [ ] Manual save uses tracked syncVersion (not hardcoded 1)
- [ ] 409 conflict shows dialog with two options
- [ ] "Overwrite" force-saves local version
- [ ] "Discard" reloads from server
- [ ] Network errors show error toast
- [ ] Validation errors show error toast
- [ ] IndexedDB quota exceeded shows error toast
- [ ] Guest mode saves to IndexedDB without conflicts

### Edge Cases
- [ ] Initial render: No auto-save triggered on first load
- [ ] Rapid changes: Debounce resets on each change (only saves after 2s pause)
- [ ] Tab close during save: No guarantee (browser limitation)
- [ ] Guest mode: No conflict possible (local only)
- [ ] Unauthenticated → Authenticated: Continues to work without page reload

### Integration Points
- [ ] usePlannerStorageAdapter: Routes correctly based on auth state
- [ ] Toast notifications: Use existing toast system from sonner
- [ ] i18n: Error messages use translation keys
- [ ] SSE: Real-time updates still work after save
