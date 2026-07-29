# Research: Privacy-Respecting Sync Architecture

## Clarifications Resolved

| Ambiguity | Decision |
|-----------|----------|
| First-login dialog | Once only, must choose. syncEnabled=NULL triggers, stored in DB permanently |
| Keep Both conflict | New UUID + "(Copy)" suffix + redirect to new planner (GitHub fork style) |
| Batch conflict threshold | 2+ conflicts triggers batch dialog |
| SSE on settings change | Reconnect immediately when any setting toggles |
| Publish while sync OFF | Check syncVersion, warn if stale before overwrite |
| Status indicators | "Draft" = never saved, "Unsynced" = has local changes not pushed |
| Re-enable sync divergence | Push A (local-only), Pull B (server-only), Conflict dialog for D/E/F |

---

## Spec-to-Code Mapping

### Backend - NEW Files
- `V022__CreateUserSettingsTable.sql` - Migration with user_id PK, sync/notify columns
- `entity/UserSettings.java` - @MapsId to User, nullable syncEnabled, boolean notifies
- `repository/UserSettingsRepository.java` - Standard JpaRepository
- `service/UserSettingsService.java` - Lazy creation, cache invalidation trigger
- `service/SseService.java` - Connection pool, settings cache, event dispatch
- `controller/SseController.java` - `/api/sse/subscribe` endpoint
- `dto/user/UserSettingsResponse.java` - Settings DTO
- `dto/user/UpdateUserSettingsRequest.java` - Partial update request

### Backend - MODIFY Files
- `PlannerSseService.java` → Rename to `PlannerSyncEventService`, route through SseService
- `NotificationService.java` - Check notification settings before emit
- `UserController.java` - Add GET/PUT `/api/user/settings`
- `PlannerController.java` - Add `force=true` param for conflict override

### Frontend - NEW Files
- `hooks/usePlannerSaveAdapter.ts` - IndexedDB operations only
- `hooks/usePlannerSyncAdapter.ts` - Server API operations only
- `hooks/useUserSettings.ts` - Settings query/mutation
- `components/settings/SyncSection.tsx` - Sync toggle with description
- `components/settings/NotificationSection.tsx` - Notification toggles
- `components/dialogs/SyncChoiceDialog.tsx` - First-login, non-dismissible
- `components/dialogs/BatchConflictDialog.tsx` - Multi-conflict resolution
- `stores/useSseStore.ts` - SSE connection state (Zustand)

### Frontend - MODIFY Files
- `hooks/usePlannerSave.ts` - Use split adapters, sync-aware manual save
- `hooks/usePlannerSync.ts` - New SSE path, settings filtering, reconnect logic
- `routes/SettingsPage.tsx` - Add Sync and Notifications sections
- `routes/auth/callback/google.tsx` - First-login dialog trigger
- `components/plannerList/PlannerCard.tsx` - Draft/Unsynced/Unpublished indicators
- `lib/plannerApi.ts` - Update SSE path to `/api/sse/subscribe`

### Frontend - DELETE Files
- `hooks/usePlannerStorageAdapter.ts` - Replaced by split adapters

---

## Pattern Enforcement

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `UserSettingsRepository.java` | `repository/UserRepository.java` | JPA interface pattern |
| `UserSettingsService.java` | `service/UserService.java` | @Transactional, constructor injection |
| `SseService.java` | `service/PlannerSseService.java` | ConcurrentHashMap, SseEmitter |
| `SseController.java` | `controller/PlannerController.java` | REST patterns, @GetMapping |
| `useUserSettings.ts` | `hooks/useUserSettingsQuery.ts` | TanStack Query, Zod validation |
| `usePlannerSyncAdapter.ts` | `lib/plannerApi.ts` | API call patterns, error handling |
| `SyncSection.tsx` | `components/settings/UsernameSection.tsx` | Section layout, toggle UI |
| `SyncChoiceDialog.tsx` | `components/dialogs/*` | Dialog pattern, Button layout |
| `BatchConflictDialog.tsx` | `components/planner/ConflictResolutionDialog.tsx` | Conflict UI, resolution options |

---

## Existing Utilities to Reuse

| Category | Location | Reusable Items |
|----------|----------|----------------|
| Conflict handling | `lib/api.ts` | ConflictError class (lines 22-30) |
| Conflict UI | `components/planner/ConflictResolutionDialog.tsx` | Base dialog, add "Keep Both" option |
| Storage ops | `hooks/usePlannerStorage.ts` | IndexedDB patterns via Dexie |
| API client | `lib/plannerApi.ts` | Fetch patterns, error handling |
| Settings section | `components/settings/UsernameSection.tsx` | Section layout pattern |
| Zod schemas | `schemas/UserSettingsSchemas.ts` | Existing schema patterns |
| SSE connection | `hooks/usePlannerSync.ts` | EventSource setup (line 113) |

---

## Gap Analysis

**Currently Missing:**
- UserSettings entity/table (no user preferences beyond username)
- Unified SseService (only PlannerSseService exists)
- SseController (SSE buried in PlannerController)
- Split adapters (single mixed adapter currently)
- First-login dialog
- Batch conflict dialog
- Status indicators on planner cards

**Needs Modification:**
- Auto-save already IndexedDB-only (correct)
- Manual save needs sync-enabled check
- SSE path change + settings filtering
- Conflict dialog needs "Keep Both" + batch mode

**Can Reuse As-Is:**
- ConflictError in api.ts
- IndexedDB operations in usePlannerStorage
- Basic conflict resolution dialog structure

---

## Sync Re-enable Flow

| Scenario | Local | Server | Action |
|----------|-------|--------|--------|
| A | Exists | Missing | Push to server |
| B | Missing | Exists | Pull to local (new device) |
| C | Same | Same | No action |
| D | Newer | Stale | Conflict dialog |
| E | Stale | Newer | Conflict dialog |
| F | Diverged | Diverged | Conflict dialog |

---

## Technical Constraints

**Backend:**
- V022 migration must be idempotent
- UserSettings uses @MapsId for one-to-one with User
- SseService needs thread-safe ConcurrentHashMap
- Settings invalidation must propagate to SSE cache immediately

**Frontend:**
- First-login dialog blocks all navigation until choice
- "Keep Both" generates new UUID + "(Copy)" suffix + redirect
- Batch dialog triggers at 2+ conflicts
- SSE reconnects immediately on settings toggle

**Cross-Cutting:**
- User deletion cascades to UserSettings
- Soft-deleted accounts must still load settings
- Rate limit settings changes (1/sec) to prevent SSE spam

---

## Testing Requirements

**Manual UI Tests:**
- First-login: Dialog appears, cannot dismiss, choice persists
- Sync toggle: OFF = no network on save, ON = server request
- Conflict: Three options work correctly, batch at 2+
- Keep Both: New planner created with "(Copy)", redirected

**Unit Tests:**
- SaveAdapter: IndexedDB operations isolated
- SyncAdapter: Server API operations isolated
- Settings hook: Query/mutation patterns

**Integration Tests:**
- Settings change invalidates SSE cache
- Publish while sync OFF shows stale warning
- Re-enable sync triggers appropriate actions per scenario
