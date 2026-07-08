# Task: Privacy-Respecting Sync Architecture (Obsidian Model)

## Description

Refactor the planner sync system to follow the Obsidian sync model where local storage (IndexedDB) is the source of truth and server is an optional backup/sync layer. This addresses Western gamer privacy concerns about automatic data upload while maintaining convenience for users who want cloud sync.

### Core Behavior Changes

**Local-First Architecture:**
- All planners live in IndexedDB first
- Auto-save (2s debounce) writes to IndexedDB only, never touches server
- Manual save writes to IndexedDB, then optionally syncs to server if enabled
- App works fully offline without authentication

**User-Controlled Sync:**
- New `user_settings` table stores sync and notification preferences
- First-login dialog forces explicit sync choice (no skip allowed) - GDPR compliant
- Sync toggle in Settings page allows changing preference anytime
- Disabling sync does not delete server data - just stops mirroring

**Publish Independent of Sync:**
- Users can publish planners to Gesellschaft even with sync OFF
- Publishing does one-time upload of that specific planner
- "Unpublished changes" indicator when local differs from published version
- Explicit "Update" action to push changes to published planner

**Conflict Resolution:**
- Detect conflicts via `syncVersion` mismatch (server-authoritative counter)
- Show resolution dialog with three options:
  - Keep Local (recommended - Obsidian model default)
  - Use Server (discard local changes)
  - Keep Both (fork - create copy of server version)
- Batch dialog for multiple conflicts when reconnecting after offline period

### Planner List Indicators (Minimal Approach)

Show only exceptions that need user attention:
- (nothing) = Normal/synced state
- `Draft` = Never manually saved
- `Unsynced` = Auth + Sync ON + local changes not pushed
- `Unpublished changes` = Published + local differs from server

### SSE Events (Settings-Aware)

Single SSE endpoint at `/api/sse/subscribe` with event types:
- `sync:planner` - Another device pushed changes (requires `syncEnabled`)
- `notify:comment` - Someone commented on your planner (requires `notifyComments`)
- `notify:recommended` - Your planner reached recommended threshold (requires `notifyRecommendations`)
- `notify:published` - Someone published a new planner (requires `notifyNewPublications`, rate-limited)

Backend filters events by user settings before emitting. No connection needed if all settings are OFF.

## Research

### Existing Patterns to Study
- `hooks/usePlannerStorage.ts` - Current IndexedDB operations
- `hooks/usePlannerStorageAdapter.ts` - Current adapter pattern (to be split)
- `hooks/usePlannerSave.ts` - Current save orchestration
- `hooks/usePlannerSync.ts` - Current SSE handling
- `service/PlannerSseService.java` - Current SSE implementation
- `routes/SettingsPage.tsx` - Current settings UI patterns
- `components/settings/UsernameSection.tsx` - Settings section pattern

### Technical Decisions Made
- `syncVersion` vs `updatedAt`: Both kept - syncVersion for conflict detection (server-controlled), updatedAt for display
- Settings storage: Flat columns in `user_settings` table, not JSON blob
- SSE path: `/api/sse/subscribe` in new `SseController` (separate from REST controllers)
- Adapters: Split into SaveAdapter (local) and SyncAdapter (server)

### External References
- Obsidian Sync model for local-first architecture
- GDPR explicit consent requirements
- 2024-2025 gaming privacy controversies (Microsoft Copilot, 2K EULA) for context

## Scope

### Files to READ for Context
- `docs/architecture-map.md` - System architecture
- `frontend/src/hooks/usePlannerStorage.ts` - IndexedDB operations
- `frontend/src/hooks/usePlannerStorageAdapter.ts` - Current adapter
- `frontend/src/hooks/usePlannerSave.ts` - Save orchestration
- `frontend/src/hooks/usePlannerSync.ts` - SSE handling
- `frontend/src/routes/SettingsPage.tsx` - Settings page
- `backend/src/main/java/org/danteplanner/backend/service/PlannerSseService.java` - SSE service
- `backend/src/main/java/org/danteplanner/backend/entity/User.java` - User entity

## Target Code Area

### Backend - NEW Files
- `resources/db/migration/V022__CreateUserSettingsTable.sql` - Migration
- `entity/UserSettings.java` - Settings entity with @MapsId to User
- `repository/UserSettingsRepository.java` - JPA repository
- `service/UserSettingsService.java` - Settings CRUD + cache invalidation
- `service/SseService.java` - Connection pool, settings cache, event dispatch
- `controller/SseController.java` - `/api/sse/subscribe` endpoint
- `dto/user/UserSettingsResponse.java` - Response DTO
- `dto/user/UpdateUserSettingsRequest.java` - Partial update request DTO

### Backend - MODIFY Files
- `service/PlannerSseService.java` - Rename to `PlannerSyncEventService`, emit through SseService
- `service/NotificationService.java` - Emit through SseService, check notification settings
- `controller/UserController.java` - Add GET/PUT `/api/user/settings` endpoints
- `controller/PlannerController.java` - Add `force=true` query param for conflict override

### Frontend - NEW Files
- `hooks/usePlannerSaveAdapter.ts` - IndexedDB operations (rename from usePlannerStorage)
- `hooks/usePlannerSyncAdapter.ts` - Server API operations
- `hooks/useUserSettings.ts` - User settings query/mutation
- `components/settings/SyncSection.tsx` - Sync toggle UI
- `components/settings/NotificationSection.tsx` - Notification toggles UI
- `components/dialogs/SyncChoiceDialog.tsx` - First-login sync choice
- `components/dialogs/ConflictResolutionDialog.tsx` - Conflict resolution UI
- `stores/useSseStore.ts` - SSE connection state (Zustand)

### Frontend - MODIFY Files
- `hooks/usePlannerSave.ts` - Use new adapters, simplified auto-save
- `hooks/usePlannerSync.ts` - Rename, update SSE path, filter by settings
- `routes/SettingsPage.tsx` - Add Sync and Notifications sections
- `routes/auth/callback/google.tsx` - Check for first-login dialog trigger
- `components/plannerList/PlannerCard.tsx` - Add status indicators
- `lib/plannerApi.ts` - Update SSE path to `/api/sse/subscribe`

### Frontend - DELETE Files
- `hooks/usePlannerStorageAdapter.ts` - Replaced by split adapters

## System Context (Senior Thinking)

- **Feature domain**: Authentication / Planner Sync / Settings
- **Core files in this domain**:
  - Auth: `controller/AuthController.java`, `service/JwtService.java`
  - Planner Sync: `hooks/usePlannerSync.ts`, `service/PlannerSseService.java`
  - Settings: `routes/SettingsPage.tsx`, `components/settings/*`
- **Cross-cutting concerns touched**:
  - Authentication (settings require auth)
  - SSE infrastructure (new controller, event filtering)
  - IndexedDB (adapter refactoring)
  - User preferences (new table)

## Impact Analysis

### Files Being Modified

| File | Impact Level | Reason |
|------|--------------|--------|
| `PlannerSseService.java` | Medium | Rename + routing through SseService |
| `NotificationService.java` | Medium | Check settings before emitting |
| `usePlannerSave.ts` | Medium | Adapter split, simplified logic |
| `usePlannerSync.ts` | Medium | New SSE path, settings-aware |
| `SettingsPage.tsx` | Low | Add new sections |
| `plannerApi.ts` | Low | Update SSE endpoint |

### What Depends on These Files
- `usePlannerSave.ts` - Used by `PlannerMDEditorContent.tsx`, `PlannerMDEditPage.tsx`
- `usePlannerSync.ts` - Used by planner pages for real-time updates
- SSE infrastructure - Used for multi-device sync and notifications

### Potential Ripple Effects
- Existing planners: No data migration needed, settings created lazily on first access
- SSE reconnection: May need to handle settings refresh
- Offline users: Auto-save continues working, manual sync blocked gracefully

### High-Impact Files to Watch
- `SseService.java` (new) - Central event dispatch, affects all real-time features
- `usePlannerSaveAdapter.ts` (new) - All local storage operations flow through this

## Risk Assessment

### Edge Cases Not Yet Defined
- User clears browser data while sync ON - server has data, local empty
- Two users on same account editing simultaneously (shared account)
- Server timeout during sync - should retry or show error?
- IndexedDB quota exceeded - how to handle gracefully?

### Performance Concerns
- SSE settings cache invalidation - must be fast to avoid stale filtering
- Multiple conflict detection on app startup - batch check vs sequential

### Backward Compatibility
- Existing users get `syncEnabled = NULL` triggering first-login dialog
- Existing planners continue to work, settings created on first access
- Current SSE endpoint deprecated after migration period

### Security Considerations
- Settings endpoint requires authentication
- SSE connection validates auth token
- No PII in SSE events (only IDs and types)

## Testing Guidelines

### Manual UI Testing

**First-Login Dialog:**
1. Clear browser data or use incognito
2. Navigate to app and complete OAuth login
3. Verify sync choice dialog appears immediately
4. Verify dialog cannot be dismissed without choosing
5. Select "Enable Cloud Sync"
6. Verify dialog closes and app loads normally
7. Navigate to Settings page
8. Verify Sync toggle is ON

**Sync Toggle:**
1. Navigate to Settings page (authenticated)
2. Locate Sync section
3. Toggle sync OFF
4. Verify confirmation dialog explains behavior
5. Confirm toggle OFF
6. Create new planner and save
7. Open browser DevTools Network tab
8. Verify no server requests on save (local only)
9. Toggle sync ON
10. Verify local planners push to server

**Conflict Resolution:**
1. Open app on Device A, create planner, save with sync ON
2. Open app on Device B, verify planner synced
3. Turn off network on Device B
4. Edit planner on Device A, save
5. Edit same planner on Device B, save (goes to local)
6. Restore network on Device B
7. Trigger sync (page refresh or manual)
8. Verify conflict dialog appears
9. Test each resolution option:
   - Keep Local: verify server updated with local version
   - Use Server: verify local replaced with server version
   - Keep Both: verify new planner created with server data

**Publish While Sync OFF:**
1. Navigate to Settings, turn sync OFF
2. Create new planner
3. Click Publish button
4. Verify upload confirmation dialog appears
5. Confirm publish
6. Verify planner appears in Gesellschaft
7. Edit planner locally
8. Verify "Unpublished changes" indicator appears in list
9. Click Update to push changes

**SSE Event Filtering:**
1. Open app on two devices, both authenticated
2. Device A: sync ON, notifications ON
3. Device B: sync OFF, notifications ON
4. Device A: create planner, save
5. Device B: verify NO sync event received
6. Device A: publish planner
7. Device B: verify notification received (if notifyNewPublications ON)

### Automated Functional Verification

**Settings Persistence:**
- [ ] Settings saved to database on change
- [ ] Settings loaded on app startup
- [ ] Settings cached in SSE connection
- [ ] Cache invalidated on settings update

**Adapter Separation:**
- [ ] Auto-save uses SaveAdapter only (no server calls)
- [ ] Manual save uses SaveAdapter then SyncAdapter (if enabled)
- [ ] Sync OFF prevents SyncAdapter calls
- [ ] Publish bypasses sync setting for that specific planner

**Conflict Detection:**
- [ ] syncVersion mismatch triggers conflict state
- [ ] Conflict dialog shows both versions' timestamps
- [ ] Force push increments syncVersion correctly
- [ ] Discard reloads server version to local

**SSE Filtering:**
- [ ] syncEnabled=false skips sync:planner events
- [ ] notifyComments=false skips notify:comment events
- [ ] notifyRecommendations=false skips notify:recommended events
- [ ] notifyNewPublications=false skips notify:published events
- [ ] No SSE connection if all settings OFF

### Edge Cases

- [ ] First-time user: Settings row created with defaults on first access
- [ ] Existing user: syncEnabled=NULL triggers first-login dialog
- [ ] Network offline: Auto-save works, manual sync shows offline error
- [ ] SSE disconnect: Reconnects with exponential backoff
- [ ] Multiple conflicts: Batch dialog with per-item resolution option
- [ ] Publish conflict: Handles version mismatch gracefully

### Integration Points

- [ ] OAuth callback: Checks syncEnabled for dialog trigger
- [ ] Settings change: Invalidates SSE settings cache
- [ ] Planner save: Routes through correct adapters
- [ ] SSE events: Filtered by cached user settings
- [ ] Notification creation: Respects notification settings

## Implementation Checklist

### Phase 1: Backend Foundation
- [ ] V022 migration: Create `user_settings` table
- [ ] `UserSettings.java` entity with @MapsId
- [ ] `UserSettingsRepository.java`
- [ ] `UserSettingsService.java` with lazy creation
- [ ] Add GET/PUT `/api/user/settings` to UserController
- [ ] DTOs for settings request/response

### Phase 2: SSE Refactoring
- [ ] Create `SseService.java` for connection management
- [ ] Create `SseController.java` with `/api/sse/subscribe`
- [ ] Rename `PlannerSseService` to `PlannerSyncEventService`
- [ ] Route events through SseService with settings filtering
- [ ] Add settings cache per SSE connection
- [ ] Implement cache invalidation on settings change

### Phase 3: Frontend Adapters
- [ ] Create `usePlannerSaveAdapter.ts` (IndexedDB only)
- [ ] Create `usePlannerSyncAdapter.ts` (Server API only)
- [ ] Refactor `usePlannerSave.ts` to use new adapters
- [ ] Simplify auto-save to SaveAdapter only
- [ ] Add sync logic to manual save (if enabled)

### Phase 4: Settings UI
- [ ] Create `useUserSettings.ts` hook
- [ ] Create `SyncSection.tsx` component
- [ ] Create `NotificationSection.tsx` component
- [ ] Add sections to SettingsPage.tsx
- [ ] Create `SyncChoiceDialog.tsx` for first-login

### Phase 5: Conflict Resolution
- [ ] Add `force=true` param to PUT endpoint
- [ ] Create `ConflictResolutionDialog.tsx`
- [ ] Implement three resolution options
- [ ] Add batch conflict handling
- [ ] Add status indicators to planner list

### Phase 6: SSE Frontend
- [ ] Update SSE path to `/api/sse/subscribe`
- [ ] Add settings-based event filtering
- [ ] Create `useSseStore.ts` for connection state
- [ ] Conditional SSE connection based on settings
