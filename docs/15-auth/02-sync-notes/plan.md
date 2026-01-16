# Execution Plan: Privacy-Respecting Sync Architecture

## Execution Overview

Bottom-up implementation: backend foundation → frontend adapters → UI components.
- IndexedDB remains source of truth (Obsidian model)
- Server sync is user-controlled via settings
- First-login dialog forces explicit GDPR-compliant choice

---

## Dependency Analysis

### Files Being Modified

| File | Impact | Depends On | Used By |
|------|--------|------------|---------|
| `PlannerSseService.java` | Medium | ObjectMapper | PlannerController, NotificationService |
| `NotificationService.java` | Medium | UserSettings (new) | PlannerController, CommentController |
| `usePlannerStorageAdapter.ts` | High | usePlannerStorage, usePlannerSync | usePlannerSave (DELETE after split) |
| `usePlannerSave.ts` | Medium | Split adapters | PlannerMDEditorContent.tsx |
| `usePlannerSync.ts` | Medium | plannerApi | usePlannerStorageAdapter.ts |

### Ripple Effect Map

- UserSettings entity changes → UserSettingsService, SseService, UserController
- SseService.java created → PlannerSseService routing, NotificationService routing
- usePlannerSaveAdapter created → usePlannerSave imports, adapter deletion
- SSE path changes → plannerApi.ts, usePlannerSync.ts

### High-Risk Modifications

| File | Risk | Mitigation |
|------|------|------------|
| `usePlannerStorageAdapter.ts` | High - split/delete | Create new adapters first, verify, then delete |
| `usePlannerSave.ts` | Medium - core logic | Preserve auto-save behavior (IndexedDB only) |
| `PlannerSseService.java` | Medium - rename | Keep existing methods during migration |

---

## Execution Order

### Phase 1: Backend Foundation (Steps 1-5)

1. **V024__CreateUserSettingsTable.sql** - Migration with user_id PK, sync/notify columns
   - Enables: Settings storage

2. **entity/UserSettings.java** - @MapsId to User, nullable syncEnabled
   - Depends on: Step 1
   - Pattern: entity/User.java

3. **repository/UserSettingsRepository.java** - JpaRepository interface
   - Depends on: Step 2
   - Pattern: repository/UserRepository.java

4. **dto/user/UserSettingsResponse.java** - Response DTO
   - Depends on: Step 2
   - Pattern: dto/user/UserDeletionResponse.java

5. **dto/user/UpdateUserSettingsRequest.java** - Partial update request
   - Depends on: Step 2
   - Pattern: dto/user/UpdateUsernameKeywordRequest.java

### Phase 2: Backend Services (Steps 6-7)

6. **service/UserSettingsService.java** - Lazy creation, partial update
   - Depends on: Steps 2-5
   - Pattern: service/UserService.java

7. **Modify UserController.java** - Add GET/PUT `/api/user/settings`
   - Depends on: Step 6
   - Enables: F1 (Settings API)

**CHECKPOINT 1**: Test settings API endpoints

### Phase 3: SSE Refactoring (Steps 8-12)

8. **service/SseService.java** - Connection pool, settings cache, event dispatch
   - Depends on: Step 6
   - Pattern: service/PlannerSseService.java

9. **controller/SseController.java** - GET `/api/sse/subscribe`
   - Depends on: Step 8
   - Enables: F2 (Unified SSE endpoint)

10. **Rename PlannerSseService → PlannerSyncEventService** - Route through SseService
    - Depends on: Step 8

11. **Modify NotificationService.java** - Check settings, route through SseService
    - Depends on: Steps 6, 8

12. **Modify PlannerController.java** - Add `force=true` query param
    - Depends on: None
    - Enables: F3 (Conflict override)

**CHECKPOINT 2**: Test SSE new endpoint, event filtering, force param

### Phase 4: Frontend Adapters (Steps 13-17)

13. **hooks/usePlannerSaveAdapter.ts** - IndexedDB operations only
    - Depends on: None
    - Pattern: usePlannerStorage.ts

14. **hooks/usePlannerSyncAdapter.ts** - Server API operations only
    - Depends on: None
    - Pattern: lib/plannerApi.ts

15. **Refactor usePlannerSave.ts** - Use split adapters, sync-aware manual save
    - Depends on: Steps 13-14

16. **Update plannerApi.ts** - SSE path to `/api/sse/subscribe`, add force param
    - Depends on: Step 9

17. **Refactor usePlannerSync.ts** - New SSE path, settings filtering, reconnect
    - Depends on: Step 16

**CHECKPOINT 3**: Test auto-save, manual save routing, SSE connection

### Phase 5: Frontend Settings UI (Steps 18-23)

18. **schemas/UserSettingsSchemas.ts** - Zod schemas for settings
    - Pattern: schemas/PlannerSchemas.ts

19. **types/UserSettingsTypes.ts** - Types inferred from schemas
    - Depends on: Step 18

20. **hooks/useUserSettings.ts** - Query and mutation hooks
    - Depends on: Steps 18-19
    - Pattern: hooks/useUserSettingsQuery.ts

21. **components/settings/SyncSection.tsx** - Sync toggle UI
    - Depends on: Step 20
    - Pattern: components/settings/UsernameSection.tsx

22. **components/settings/NotificationSection.tsx** - Notification toggles
    - Depends on: Step 20
    - Pattern: SyncSection.tsx

23. **Modify SettingsPage.tsx** - Add new sections
    - Depends on: Steps 21-22
    - Enables: F4 (Settings UI)

**CHECKPOINT 4**: Test settings page, toggle persistence

### Phase 6: First-Login Dialog (Steps 24-27)

24. **components/dialogs/SyncChoiceDialog.tsx** - Non-dismissible choice
    - Depends on: Step 20
    - Pattern: ConflictResolutionDialog.tsx

25. **stores/useSseStore.ts** - Zustand store for SSE state
    - Depends on: None

26. **Modify auth/callback/google.tsx** - Trigger dialog on syncEnabled=null
    - Depends on: Step 24

27. **App-level dialog integration** - Layout state for first-login trigger
    - Depends on: Steps 24, 26
    - Enables: F5 (First-login dialog)

**CHECKPOINT 5**: Test first-login flow, dialog cannot dismiss

### Phase 7: Conflict Enhancement (Steps 28-30)

28. **Modify ConflictResolutionDialog.tsx** - Add "Keep Both" option
    - Depends on: None
    - Enables: F6 (Keep Both resolution)

29. **components/dialogs/BatchConflictDialog.tsx** - Multi-conflict UI
    - Depends on: Step 28
    - Pattern: ConflictResolutionDialog.tsx
    - Enables: F7 (Batch conflicts)

30. **Modify PlannerCard.tsx** - Status indicators (Draft, Unsynced, Unpublished)
    - Depends on: Step 15
    - Enables: F8 (Status indicators)

### Phase 8: Cleanup (Step 31)

31. **DELETE usePlannerStorageAdapter.ts** - Verify imports updated first
    - Depends on: Steps 13-17 verified

### Phase 9: Tests (Steps 32-34)

32. **Backend unit tests** - UserSettingsServiceTest, SseServiceTest
    - Depends on: Steps 1-12

33. **Frontend unit tests** - Adapter tests, SyncSection test
    - Depends on: Steps 13-30

34. **Integration tests** - Settings-SSE, first-login flow, conflict flow
    - Depends on: All steps

---

## Verification Checkpoints

| # | After Step | Verification |
|---|------------|--------------|
| 1 | 7 | GET/PUT settings API works |
| 2 | 12 | SSE new endpoint, filtering, force param |
| 3 | 17 | Adapters split, save flow intact |
| 4 | 23 | Settings UI complete, toggles persist |
| 5 | 27 | First-login dialog works |

---

## Risk Mitigation

| Risk | Step | Mitigation |
|------|------|------------|
| Browser data cleared + sync ON | Phase 4 | Detect empty local, offer server pull |
| Simultaneous edit | Step 12 | syncVersion conflict → dialog |
| Server timeout | Step 14 | Error toast, keep local, allow retry |
| IndexedDB quota | Step 13 | Catch QuotaExceededError, show warning |
| SSE cache stale | Step 17 | Invalidate immediately, reconnect |

---

## Rollback Strategy

**Backend**: V024 is additive (safe). Old SSE endpoint remains during migration.

**Frontend**: Keep old adapter until new ones verified. Auto-save unchanged.

**Critical failure**: Revert adapter changes. Old path still functional.
