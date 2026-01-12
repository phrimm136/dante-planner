# Local-First Auto-Save Execution Plan

## Planning Gaps
**NONE** - All information is complete. Research has identified all files, patterns, and implementation requirements.

## Execution Overview

This task transforms the planner auto-save mechanism from server-centric to local-first architecture in 4 sequential phases:

1. **Phase 0 (Draft Limit Removal)**: Remove artificial draft limit to allow unlimited local storage
2. **Phase 1 (Auto-Save Routing)**: Redirect all auto-saves from server to IndexedDB only
3. **Phase 2 (Status Terminology)**: Update status badge terminology (Unsynced/Synced/Local)
4. **Phase 3 (beforeunload Warning)**: Add browser warning for unsynced changes
5. **Phase 4 (Last Synced Timestamp)**: Display relative sync time with date-fns

Expected outcome: 99% server load reduction (18K req/hr → 100 req/hr) while preserving data safety via IndexedDB persistence, manual server sync, and user awareness features.

## Dependency Analysis (Senior Thinking)

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| `usePlannerSave.ts` | Medium | usePlannerStorageAdapter, usePlannerStorage | PlannerMDNewPage, PlannerMDEditPage |
| `usePlannerStorage.ts` | Low | constants.ts (MAX_GUEST_DRAFTS) | usePlannerStorageAdapter |
| `constants.ts` | Low | None | usePlannerStorage |
| `PlannerMDNewPage.tsx` | Low | usePlannerSave | Route system |
| `PlannerMDEditPage.tsx` | Low | usePlannerSave | Route system |
| `PlannerCard.tsx` | Low | useAuthQuery, useTranslation | Planner list pages |
| i18n files (EN/KR/JP/CN) | Low | None | All UI components |
| `package.json` (frontend) | Low | None | Build system |

### Ripple Effect Map

**If `usePlannerSave.ts` changes:**
- Auto-save behavior changes in both new and edit pages
- beforeunload warning logic affects browser close behavior
- Manual save still routes through adapter (no change to conflict resolution)

**If i18n files change:**
- Status badge text updates across all planner list cards
- Timestamp display text updates in editor pages
- No component code changes required (i18n key lookup handles it)

**If `usePlannerStorage.ts` changes:**
- Draft limit enforcement removed
- Storage operations continue working identically
- Guest users can create unlimited local planners

### High-Risk Modifications

**usePlannerSave.ts (lines 391-409):**
- **Risk**: Breaking auto-save routing could prevent local persistence
- **Mitigation**: Change only routing destination (adapter → localStorage), preserve all other logic
- **Verification**: Network tab shows 0 requests during auto-save, IndexedDB shows saved data

**i18n files:**
- **Risk**: Missing translation keys could show raw key strings in UI
- **Mitigation**: Add keys to all 4 languages simultaneously in single step
- **Verification**: Test language switch to KR/JP/CN to confirm translations appear

## Execution Order

### Phase 0: Remove Draft Limit (Cleanup)
1. **constants.ts (line 524)**: Delete MAX_GUEST_DRAFTS constant
   - Depends on: none
   - Enables: F0 (unlimited local storage)

2. **usePlannerStorage.ts (lines 108, 397-417, 443)**: Remove enforceGuestDraftLimit() method and interface
   - Depends on: Step 1
   - Enables: F0

3. **usePlannerSave.ts (line 344)**: Remove enforceGuestDraftLimit() call
   - Depends on: Step 2
   - Enables: F0

### Phase 1: Auto-Save Routing (Core Change)
4. **usePlannerSave.ts (lines 391-409)**: Modify debouncedSave() routing
   - Change: Call `localStorage.savePlanner()` directly instead of `adapter.savePlanner()`
   - Preserve: Manual save() continues using adapter for auth-based routing
   - Depends on: none (independent from Phase 0)
   - Enables: F1 (local-first auto-save), F2 (server load reduction)

### Phase 2: Status Terminology (UI Display)
5. **i18n files (all 4 languages)**: Add status translation keys
   - Add to EN: `planner.status.unsynced`, `.synced`, `.local`
   - Add to KR: Korean translations
   - Add to JP: Japanese translations
   - Add to CN: Chinese translations
   - Depends on: none
   - Enables: F3 (status badge display)

6. **PlannerCard.tsx**: Update status badge display logic
   - Import: useAuthQuery(), useTranslation()
   - Logic: Auth users show unsynced/synced based on metadata.status, guest users always show "local"
   - Depends on: Step 5 (i18n keys must exist)
   - Enables: F3

### Phase 3: beforeunload Warning (Data Safety)
7. **usePlannerSave.ts**: Add lastSyncedStateRef tracking
   - Add: `const lastSyncedStateRef = useRef<string>('')`
   - Update lastSyncedStateRef in manual save() after success
   - Expose: `hasUnsyncedChanges` in return object (compare current state vs lastSyncedStateRef)
   - Depends on: none
   - Enables: F4 (unsynced change detection)

8. **PlannerMDNewPage.tsx**: Add beforeunload event listener
   - Check: `hasUnsyncedChanges && isAuthenticated` before showing warning
   - Cleanup: Remove listener on unmount
   - Depends on: Step 7
   - Enables: F4

9. **PlannerMDEditPage.tsx**: Add beforeunload event listener
   - Same logic as Step 8
   - Depends on: Step 7
   - Enables: F4

### Phase 4: Last Synced Timestamp (User Awareness)
10. **Install date-fns dependency**: `cd frontend && yarn add date-fns`
    - Depends on: none
    - Enables: F5 (relative time formatting)

11. **i18n files (all 4 languages)**: Add sync timestamp keys
    - Add to EN: `planner.sync.lastSynced`, `.neverSynced`
    - Add to KR/JP/CN: Translations
    - Depends on: none
    - Enables: F5

12. **usePlannerSave.ts**: Add lastSyncedAt state tracking
    - Add: `const [lastSyncedAt, setLastSyncedAt] = useState<string | null>(null)`
    - Update lastSyncedAt in manual save() after success
    - Expose: `lastSyncedAt` in return object
    - Depends on: Step 10
    - Enables: F5

13. **PlannerMDNewPage.tsx**: Display timestamp near save button
    - Import: formatDistanceToNow from date-fns
    - Display: "Last synced: {relative time}" or "Never synced"
    - Conditional: Only show for authenticated users
    - Depends on: Step 11, Step 12
    - Enables: F5

14. **PlannerMDEditPage.tsx**: Display timestamp (same as Step 13)
    - Depends on: Step 11, Step 12
    - Enables: F5

### Phase 5: Testing (Verification)
15. **Unit tests for usePlannerSave**: Test auto-save routing change
    - Verify: debouncedSave calls localStorage.savePlanner
    - Verify: manual save calls adapter.savePlanner
    - Depends on: Step 4
    - Enables: I1 (automated test coverage)

16. **Integration test for status badge**: Test PlannerCard status display
    - Verify: Auth users see unsynced/synced
    - Verify: Guest users see "local"
    - Depends on: Step 6
    - Enables: I2

17. **Manual verification per phase**: Execute manual test steps from instructions.md
    - Phase 0: Create 20+ planners, verify no quota errors
    - Phase 1: Check Network tab for 0 auto-save requests
    - Phase 2: Verify status badges translate across 4 languages
    - Phase 3: Verify beforeunload warning shows/hides correctly
    - Phase 4: Verify timestamp updates on manual save, not auto-save
    - Depends on: All previous steps
    - Enables: MV1-MV5 (manual verification checklists)

## Test Steps (MANDATORY)

**Unit Tests (Phase 5, Step 15-16):**
- After Step 4: Write unit test for debouncedSave routing
- After Step 6: Write unit test for PlannerCard status logic

**Integration Tests:**
- After Step 4: Test auto-save never hits server network
- After Step 6: Test status badge displays correctly for auth/guest
- After Step 9: Test beforeunload warning triggers correctly
- After Step 14: Test timestamp updates on manual save only

**Manual Tests (Phase 5, Step 17):**
- See instructions.md sections "Manual UI Testing" for detailed 63-step verification

## Verification Checkpoints

**After Step 3 (Phase 0 complete):**
- Verify F0: Create 20+ guest planners without errors, all persist in IndexedDB

**After Step 4 (Phase 1 complete):**
- Verify F1: Auto-save writes to IndexedDB only (check DevTools Application tab)
- Verify F2: Network tab shows 0 planner API requests during 2s after edit
- Verify: Manual save still triggers PUT/POST to server

**After Step 6 (Phase 2 complete):**
- Verify F3: Auth users see "Unsynced" badge for auto-saved planners
- Verify F3: Auth users see "Synced" badge after manual save
- Verify F3: Guest users see "Local" badge for all planners
- Verify: Status badges translate correctly in KR/JP/CN

**After Step 9 (Phase 3 complete):**
- Verify F4: Auth users with unsynced changes see browser warning on close
- Verify F4: Auth users after manual save have clean close (no warning)
- Verify F4: Guest users never see warning

**After Step 14 (Phase 4 complete):**
- Verify F5: "Last synced: a few seconds ago" appears after manual save
- Verify F5: Timestamp does NOT update after auto-save
- Verify F5: "Never synced" shows for planners never manually saved
- Verify F5: Timestamp hidden for guest users

## Risk Mitigation (from instructions.md Risk Assessment)

| Risk | Step Affected | Mitigation |
|------|---------------|------------|
| Browser crash before manual save | Steps 4, 8-9 | beforeunload warning + IndexedDB persistence (data recoverable on next browser open) |
| Auth session expiry during editing | Step 4 | Auto-save succeeds locally, expiry caught only on manual save with re-login prompt |
| Cross-device concurrent editing | Step 4 | Existing syncVersion conflict detection handles this, shows conflict dialog on manual save |
| IndexedDB quota exceeded | Steps 1-3 | Browser's natural quota (~50MB-1GB) becomes limit, existing error handling shows quota error |
| Rapid manual save clicks | Steps 4, 7 | Existing isSaving flag prevents duplicate saves, button disabled during save |
| Incomplete planner validation | Step 4 | IndexedDB accepts incomplete data, server validates on manual save, user sees errors before data loss |
| State comparison infinite loop | Step 4 | stateToComparableString() already prevents redundant saves, verified no infinite loop |

## Pre-Implementation Validation Gate

**BEFORE Step 1 execution, verify research completed:**

| Validation Category | Check | Blocker if Missing |
|---------------------|-------|-------------------|
| **Reference Completeness** | Read all lines of usePlannerSave.ts, usePlannerStorageAdapter.ts, usePlannerStorage.ts? | YES |
| **Contract Alignment** | Auto-save routing change preserves manual save adapter routing? | YES |
| **Dependency Resolution** | date-fns package available after Step 10? | YES (Step 10 blocks Steps 12-14) |
| **Structure Documentation** | beforeunload pattern documented (useEffect + cleanup)? | YES |
| **Difference Justification** | Auto-save bypass adapter justified (local-first design)? | NO (documented in research.md) |

**Execution Rule**: Do NOT proceed if critical blockers unresolved.

## Dependency Verification Steps

**After Step 2 (usePlannerStorage modification):**
- Test: usePlannerStorageAdapter still calls localStorage.savePlanner successfully
- Verify: No TypeScript errors from interface change

**After Step 4 (usePlannerSave routing change):**
- Test: PlannerMDNewPage auto-save still triggers every 2s
- Test: PlannerMDEditPage auto-save still triggers every 2s
- Verify: Manual save button still works for both pages

**After Step 6 (PlannerCard status badge):**
- Test: Personal planner list (/planner/md) shows correct status badges
- Test: Community list (/planner/md/gesellschaft) shows correct badges (if applicable)

**After Step 10 (date-fns installation):**
- Verify: `yarn.lock` updated with date-fns entry
- Verify: TypeScript can import formatDistanceToNow

## Rollback Strategy

**If Step 4 fails (auto-save routing breaks):**
- Revert usePlannerSave.ts lines 391-409 to use `adapter.savePlanner()`
- Safe stop: Phase 0 (draft limit removal) can remain

**If Step 6 fails (status badge display breaks):**
- Revert PlannerCard.tsx changes
- Safe stop: Phases 0-1 (auto-save routing) can remain

**If Step 10 fails (date-fns installation fails):**
- Skip Phase 4 entirely
- Safe stop: Phases 0-3 (auto-save + status + warning) can remain

**If Step 15-17 fail (tests reveal bugs):**
- Fix bugs before proceeding to next phase
- Rollback to last working phase if unfixable
