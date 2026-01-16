# Code Documentation: Privacy-Respecting Sync Architecture

## What Was Done

- Implemented 34 planned steps across 9 phases (Backend Foundation → Tests)
- Created UserSettings entity, service, and API endpoints for sync/notification preferences
- Unified SSE endpoint with settings-aware cache invalidation
- Built frontend adapters (usePlannerSaveAdapter, usePlannerSyncAdapter) separating local vs server storage
- Added first-login SyncChoiceDialog + conflict resolution (single + batch)
- Implemented "Keep Both" resolution creating local copy while reverting to server version
- Fixed 24 issues beyond original plan (SSE auth, storage keys, sync loops, conflict handling)
- Code review fixes: ThreadLocal cleanup, ID collision check, category change validation, progressive theme validation

## Files Changed

**Backend:**
- `entity/User.java`, `entity/UserSettings.java`, `repository/UserSettingsRepository.java`
- `service/UserSettingsService.java`, `service/PlannerSyncEventService.java`
- `controller/UserController.java`, `controller/SseController.java`, `controller/PlannerController.java`
- `service/PlannerService.java` (upsert, client ID, category validation, ID collision check)
- `validation/PlannerContentValidator.java` (strictMode with ThreadLocal cleanup)
- `dto/planner/UpsertPlannerRequest.java`, `repository/PlannerRepository.java`
- `config/SecurityConfig.java`, `security/JwtAuthenticationFilter.java`
- Migration `V024__add_user_settings.sql`

**Frontend:**
- `hooks/usePlannerSaveAdapter.ts`, `hooks/usePlannerSyncAdapter.ts`, `hooks/usePlannerSave.ts`
- `hooks/usePlannerSync.ts`, `hooks/usePlannerStorage.ts`, `hooks/useSseConnection.ts`
- `hooks/useMDUserPlannersData.ts`, `hooks/useUserSettings.ts`
- `components/dialogs/SyncChoiceDialog.tsx`, `components/dialogs/BatchConflictDialog.tsx`
- `components/planner/ConflictResolutionDialog.tsx`, `components/plannerList/PlannerCard.tsx`
- `routes/SettingsPage.tsx`, `routes/PlannerMDPage.tsx`, `routes/PlannerMDEditorContent.tsx`
- `lib/plannerApi.ts`, `lib/constants.ts`
- `schemas/PlannerSchemas.ts`, `schemas/UserSettingsSchemas.ts`

## Verification Results

- All 34 steps: PASS
- Features 8/8: PASS
- Edge Cases 3/5: PASS (E3 server timeout, E4 IndexedDB quota untested)
- Manual Verification 8/8: PASS
- Backend Tests: 5 failures, 8 errors (pre-existing, unrelated to sync feature)
- Frontend Tests: PASS (sync-related tests pass)

## Issues & Resolutions (Key)

- SSE 403 rate limit → Increased rate limit capacity from 1 to 5
- SSE 403 async dispatch → Skip JWT filter on ASYNC dispatch type
- Duplicate planner IDs → Server accepts client ID, uses upsert instead of create/update
- Storage duplicates → Unified key scheme `planner:md:{deviceId}:{id}` (status as metadata)
- Autosave race after conflict → Clear debounce timer + component remount on navigation
- ThreadLocal memory leak → Added `finally { currentStrictMode.remove(); }` cleanup
- ID collision wrong error → Use `existsByIdAndDeletedAtIsNull` + `PlannerForbiddenException`
