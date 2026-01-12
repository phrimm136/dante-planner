# Local-First Auto-Save Research

## Spec Ambiguities
- **NONE FOUND** - Specification is comprehensive and well-defined

## Spec-to-Code Mapping

### Phase 0: Remove Draft Limit
- `lib/constants.ts:524` - Delete MAX_GUEST_DRAFTS constant
- `hooks/usePlannerStorage.ts:108` - Remove enforceGuestDraftLimit() from interface
- `hooks/usePlannerStorage.ts:397-417` - Delete enforceGuestDraftLimit() method implementation
- `hooks/usePlannerSave.ts:344` - Remove call to enforceGuestDraftLimit()

### Phase 1: Auto-Save Routing (Core Change)
- `hooks/usePlannerSave.ts:391-409` - Modify debouncedSave() to route ALL auto-saves to IndexedDB
- Change: Call localStorage.savePlanner() directly instead of adapter.savePlanner()
- Preserve: Manual save() continues using adapter for conditional routing (auth→server, guest→IndexedDB)

### Phase 2: Status Terminology
- `static/i18n/EN/common.json` - Add planner.status.unsynced, .synced, .local
- `static/i18n/KR/common.json` - Add Korean translations
- `static/i18n/JP/common.json` - Add Japanese translations
- `static/i18n/CN/common.json` - Add Chinese translations
- Planner list card component - Import useAuthQuery(), conditionally display status based on auth state

### Phase 3: beforeunload Warning
- `hooks/usePlannerSave.ts` - Add lastSyncedStateRef for tracking, expose hasUnsyncedChanges in return
- `routes/PlannerMDNewPage.tsx` - Add beforeunload event listener in useEffect
- `routes/PlannerMDEditPage.tsx` - Add beforeunload event listener in useEffect
- Pattern: Handler checks hasUnsyncedChanges && isAuthenticated before showing warning

### Phase 4: Last Synced Timestamp
- Install dependency: `cd frontend && yarn add date-fns`
- `hooks/usePlannerSave.ts` - Add lastSyncedAt state, update on manual save success, expose in return
- `routes/PlannerMDNewPage.tsx` - Display relative timestamp near save button using formatDistanceToNow
- `static/i18n/*/common.json` - Add planner.sync.lastSynced, .neverSynced keys

## Pattern Enforcement

### Phase 1: usePlannerSave.ts Modification
- **MUST Read First**: `usePlannerStorageAdapter.ts` lines 80-149 (dual storage abstraction)
- **Pattern to Copy**: Adapter routing logic (isAuthenticated check)
- **Key Change**: Auto-save bypasses adapter, manual save preserves adapter routing

### Phase 2: PlannerCard Status Display
- **MUST Read First**: `components/plannerList/PlannerCard.tsx` (existing card structure)
- **Pattern to Copy**: Badge display pattern (top-right corner, similar to bookmark icon)
- **Dependencies**: useAuthQuery() for auth check, useTranslation() for i18n

### Phase 3: beforeunload Listener
- **MUST Read First**: React useEffect cleanup pattern documentation
- **Pattern**: window.addEventListener in useEffect, return cleanup function
- **Location**: Both PlannerMDNewPage.tsx and PlannerMDEditPage.tsx (independent per tab)

### Phase 4: Timestamp Display
- **MUST Read First**: date-fns documentation for formatDistanceToNow
- **Pattern**: Import from date-fns, call with lastSyncedAt ISO string, display conditional on isAuthenticated

## Critical Implementation Details

### Auto-Save Routing Change (Phase 1)
- Current: debouncedSave() calls adapter.savePlanner() → routes to server OR IndexedDB
- New: debouncedSave() calls localStorage.savePlanner() directly → always IndexedDB
- Reason: Eliminate server requests during auto-save (99% load reduction)
- Preserve: State comparison via stateToComparableString() prevents redundant saves

### Status Display Logic (Phase 2)
- Auth users: status === 'saved' ? 'Synced' : 'Unsynced'
- Guest users: Always 'Local' (no server sync concept)
- Badge position: Top-right corner of planner card (existing pattern)
- Implementation note: Verify PlannerCard receives metadata.status in props

### beforeunload Warning Conditions (Phase 3)
- Fire when: hasUnsyncedChanges && isAuthenticated
- Skip when: !isAuthenticated (guest users)
- Skip when: !hasUnsyncedChanges (after manual save)
- Independent: Each browser tab tracks own state

### Timestamp Update Triggers (Phase 4)
- Update on: Successful manual save to server only
- No update on: Auto-save to IndexedDB
- Display: formatDistanceToNow(parseISO(lastSyncedAt))
- Reset on: Page reload (state not persisted)

## Existing Utilities

### Reusable (No Modification)
- `hooks/useAuthQuery.ts` - Auth state check
- `hooks/usePlannerStorageAdapter.ts` - Abstraction layer (keep intact)
- `hooks/usePlannerSave.ts` - stateToComparableString() utility (lines 192-212)
- `hooks/usePlannerSave.ts` - Conflict resolution logic (lines 438-478)
- `lib/formatDate.ts` - formatPlannerDate (for absolute dates, not relative time)

### Missing (Must Create)
- i18n keys: planner.status.unsynced, .synced, .local (4 languages)
- i18n keys: planner.sync.lastSynced, .neverSynced (4 languages)
- lastSyncedAt state tracking in usePlannerSave
- hasUnsyncedChanges flag in usePlannerSave
- beforeunload listeners in editor pages
- date-fns dependency

## Gap Analysis

### Currently Missing
- i18n translation keys for status and timestamp (8 keys × 4 languages = 32 entries)
- lastSyncedAt timestamp tracking in usePlannerSave
- lastSyncedStateRef for beforeunload detection
- beforeunload event listeners in PlannerMDNewPage and PlannerMDEditPage
- date-fns package dependency

### Needs Modification
- usePlannerSave.ts: debouncedSave routing (Phase 1), add state tracking (Phase 3, 4)
- usePlannerStorage.ts: Remove enforceGuestDraftLimit method
- constants.ts: Delete MAX_GUEST_DRAFTS constant
- PlannerCard component: Add status badge display logic
- Editor pages: Add beforeunload listeners and timestamp display

### Can Reuse
- usePlannerStorageAdapter dual-storage abstraction
- Existing conflict resolution flow
- State comparison utility (stateToComparableString)
- SSE integration for cross-device sync
- Error handling infrastructure

## Testing Strategy

### Manual UI Testing Coverage
- Phase 0: 12 steps (draft limit removal, persistence verification)
- Phase 1: 15 steps (network silence, IndexedDB writes, manual save routing)
- Phase 2: 17 steps (status badges across auth states, i18n translation)
- Phase 3: 8 steps (warning triggers, multi-tab independence)
- Phase 4: 11 steps (timestamp accuracy, live updates, guest visibility)
- Total: 63 manual test steps

### Automated Functional Verification
- 57 assertion checkboxes across 4 phases
- 15 edge case scenarios
- 12 integration point verifications
- Focus: Routing logic, state transitions, error handling

### Key Test Scenarios
- Auto-save never hits server (verify Network tab empty during 2s debounce)
- Manual save triggers PUT/POST request to server
- Status badge updates correctly after auto-save and manual save
- beforeunload only fires for auth users with unsynced changes
- Timestamp updates on manual save, unchanged on auto-save
- Cross-device sync works via SSE on manual saves

## Technical Constraints

### Dual Storage Model
- Auto-save: IndexedDB only (both auth and guest)
- Manual save: Server (auth) or IndexedDB (guest) via adapter

### State Comparison
- stateToComparableString() serializes full planner state to JSON
- Comparison prevents redundant saves when state unchanged
- Debounce timer resets on each edit (2s quiet period required)

### beforeunload API
- Browser native, not React-controlled
- Add in useEffect with cleanup, not in component render
- Warning text controlled by browser, can't be customized

### SSE Integration
- Continues working for manual saves only
- No server updates during auto-save (no events fired)
- Cross-device sync becomes eventual (on manual save)

### i18n Loading
- Static JSON loaded at build time
- Must add keys to all 4 language files simultaneously
- Translation keys follow planner.status.* and planner.sync.* namespaces

### Date Formatting
- Need relative time ("5 minutes ago"), not absolute dates
- Requires date-fns package (formatDistanceToNow function)
- Live updates as time passes (component re-renders)

### Backward Compatibility
- PlannerStatus enum unchanged ('draft' | 'saved')
- No backend migration required
- Existing planners continue working with new display text

### Browser Quota
- Remove app-enforced MAX_GUEST_DRAFTS limit
- Browser natural quota (~50MB-1GB) becomes limit
- Let browser handle quota exceeded errors

## Impact Summary

### Server Load Reduction
- Before: ~18,000 req/hr (auto-save every 2s × concurrent users)
- After: ~100 req/hr (manual save only)
- Reduction: 99%

### Cross-Device Sync
- Before: Near real-time via frequent auto-save SSE events
- After: Eventual consistency on manual save
- Trade-off: Acceptable for game planning tool (not collaborative editing)

### Data Safety
- Auto-save to IndexedDB persists across browser sessions
- Manual save to server provides cloud backup
- beforeunload warning prevents accidental data loss
- Conflict resolution handles multi-device editing

### Files Modified
- Total: 12-15 files across 4 phases
- High-impact: None (all changes in planner domain)
- Medium-impact: usePlannerSave.ts (auto-save orchestration)
- Low-impact: Editor pages, constants, i18n files

## Implementation Sequence

### Phase 0: Remove Draft Limit (Simple Cleanup)
- Delete constant and method
- Remove call site
- Verification: Create 20+ planners without errors

### Phase 1: Auto-Save Routing (Core Change)
- Modify debouncedSave routing logic
- Preserve manual save adapter routing
- Verification: No network requests during auto-save

### Phase 2: Status Terminology (UI Display)
- Add i18n keys to 4 language files
- Update PlannerCard status badge logic
- Verification: Correct status text across auth states

### Phase 3: beforeunload Warning (Data Safety)
- Add state tracking to usePlannerSave
- Add event listeners to editor pages
- Verification: Warning shows for auth users with unsaved changes

### Phase 4: Last Synced Timestamp (User Awareness)
- Install date-fns dependency
- Add timestamp tracking to usePlannerSave
- Display relative time in editor UI
- Verification: Timestamp updates on manual save, live refresh

## Risk Mitigation

### Browser Crash Risk
- Mitigation: beforeunload warning creates awareness
- Mitigation: "Last synced" timestamp shows sync status
- Mitigation: IndexedDB persists data across crashes

### Auth Session Expiry Risk
- Mitigation: Auto-save succeeds locally (IndexedDB)
- Mitigation: Manual save catches expiry, prompts re-login
- Mitigation: Local data preserved during auth flow

### Cross-Device Conflict Risk
- Mitigation: Existing syncVersion conflict detection
- Mitigation: Conflict dialog with Overwrite/Discard options
- Mitigation: SSE still fires on manual saves

### Validation Error Risk
- Mitigation: IndexedDB accepts incomplete planners
- Mitigation: Server validates on manual save only
- Mitigation: User sees validation errors before data loss
