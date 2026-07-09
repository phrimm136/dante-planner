# Task: MD Planner Editor Publish Functionality

## Description

Implement the publish functionality for the MD planner editor by connecting the frontend to the existing backend API. The publish feature allows users to:

- Toggle the publish status of their planner (publish/unpublish) via a dedicated button
- Automatically save the planner before publishing to ensure latest content is published
- See visual feedback through toast notifications (no confirmation dialogs)
- Transition to view mode after successful publish (same behavior as save)
- View button text that changes based on publishing state ("Publish" vs "Unpublish")
- See loading state while publish operation is in progress ("Publishing...")

The publish button should:
- Save the current planner state first (to avoid publishing stale content)
- If save fails, abort the publish operation
- If save succeeds, call the toggle publish API endpoint
- Show success/failure toast with i18n-compatible text
- Use lucide icon to indicate publish status visually
- Transition to view mode after successful publish

Error handling:
- Display error toast if publish API call fails (403 Forbidden, 404 Not Found, 429 Rate Limit, etc.)
- Do not show confirmation dialog - just execute the toggle operation
- Preserve planner state if publish fails (no data loss)

## Research

Before implementation, review these existing patterns:
- API client pattern in `/frontend/src/lib/plannerApi.ts` - how other CRUD operations use `ApiClient` with Zod validation
- Toggle operation pattern - backend uses `PUT` for idempotent state toggles
- Save flow in `usePlannerSave.ts` - how optimistic locking and conflict resolution work
- Toast pattern in `PlannerMDNewPage.tsx` - success/failure feedback without confirmation dialogs
- i18n key structure - nested pattern `pages.plannerMD.{feature}.{status}` for consistency
- Button loading states - how `isSaving` flag works with button text changes

Backend API endpoints (already implemented):
- `PUT /api/planner/md/{id}` - Save planner (optimistic locking with `syncVersion`)
- `PUT /api/planner/md/{id}/publish` - Toggle publish status (idempotent, no request body)

Key architectural insights:
- Save and publish are intentionally separate endpoints (SRP - Single Responsibility Principle)
- Save manages content+version, publish manages visibility
- Save uses `syncVersion` for conflict resolution, publish doesn't need it
- Publish is idempotent - calling twice returns to original state
- No SSE notification on publish toggle (rare operation, users expect refresh)

## Scope

Files to READ for context:
- `/home/user/github/LimbusPlanner/frontend/src/lib/plannerApi.ts` - API client methods
- `/home/user/github/LimbusPlanner/frontend/src/routes/PlannerMDNewPage.tsx` - Existing save handler pattern
- `/home/user/github/LimbusPlanner/frontend/src/hooks/usePlannerSave.ts` - Save flow logic
- `/home/user/github/LimbusPlanner/static/i18n/EN/common.json` - i18n structure
- `/home/user/github/LimbusPlanner/docs/architecture-map.md` - Planner CRUD patterns
- `/home/user/github/LimbusPlanner/backend/src/main/java/org/danteplanner/backend/controller/PlannerController.java` - Backend API reference

Backend files (already complete - no changes needed):
- `PlannerController.java:322-331` - `togglePublish()` endpoint
- `PlannerService.java:410-432` - Publish business logic
- `PlannerResponse.java:29` - Includes `published` field

## Target Code Area

Files to CREATE or MODIFY:

1. `/home/user/github/LimbusPlanner/frontend/src/lib/plannerApi.ts`
   - Add `togglePublish()` method after `import()` method
   - Follow existing pattern: async method with Zod validation

2. `/home/user/github/LimbusPlanner/frontend/src/routes/PlannerMDNewPage.tsx`
   - Update `handlePublish()` at lines 626-640 (replace TODO)
   - Add import for `plannerApi`
   - Implement save-then-publish flow

3. `/home/user/github/LimbusPlanner/static/i18n/EN/common.json`
   - Add `pages.plannerMD` section with publish keys

4. `/home/user/github/LimbusPlanner/static/i18n/KR/common.json`
   - Add Korean translations for publish keys

5. `/home/user/github/LimbusPlanner/static/i18n/JP/common.json`
   - Add Japanese translations for publish keys

6. `/home/user/github/LimbusPlanner/static/i18n/CN/common.json`
   - Add Chinese translations for publish keys

## System Context (Senior Thinking)

**Feature domain:** Planner CRUD + Publishing (from architecture-map.md)

**Core files in this domain:**
- Frontend: `plannerApi.ts`, `PlannerMDNewPage.tsx`, `usePlannerSave.ts`
- Backend: `PlannerController.java`, `PlannerService.java`, `PlannerResponse.java`

**Cross-cutting concerns touched:**
- i18n: All toast messages must use translation keys
- API Client: Uses `ApiClient.put()` with Zod validation
- State management: `isPublishing` flag for loading state
- Toast notifications: sonner for user feedback

**Architectural layer:**
```
Frontend Component (PlannerMDNewPage)
    ↓
API Client (plannerApi.togglePublish)
    ↓
HTTP PUT /api/planner/md/{id}/publish
    ↓
Backend Controller (PlannerController.togglePublish)
    ↓
Service Layer (PlannerService.togglePublish)
    ↓
Repository Layer (PlannerRepository)
    ↓
Database (Planner table with published boolean)
```

## Impact Analysis

**Files being modified:**
- `plannerApi.ts` - LOW impact (adding new method, no changes to existing methods)
- `PlannerMDNewPage.tsx` - LOW impact (page isolated, replacing TODO implementation)
- `common.json` (all 4 languages) - LOW impact (adding new keys, no changes to existing keys)

**What depends on these files:**
- `plannerApi.ts`: Used by `usePlannerStorageAdapter.ts`, `usePlannerSync.ts`, planner pages
- `PlannerMDNewPage.tsx`: Standalone route, no other components depend on it
- `common.json`: All components using `useTranslation()` hook

**Potential ripple effects:**
- None expected - purely additive changes
- Existing save flow remains unchanged
- No changes to data structures or DTOs

**High-impact files to watch:**
- None - all modified files are low/medium impact
- `plannerApi.ts` is shared but we're only adding a new method

## Risk Assessment

**Edge cases identified:**

1. **Publish before first save:**
   - User clicks Publish without clicking Save first
   - Mitigation: `handlePublish()` calls `save()` first, aborts if save fails
   - Status: ✅ Handled by implementation

2. **Auto-save in progress during publish:**
   - User clicks Publish while auto-save debounce is pending
   - Mitigation: Publish triggers manual `save()` which completes immediately
   - Status: ✅ Safe (different operations)

3. **Network failure during publish:**
   - Request fails mid-flight
   - Mitigation: Error toast shown, planner remains in previous state
   - Status: ✅ No partial state (atomic operation)

4. **Rapid publish/unpublish:**
   - User clicks publish button multiple times rapidly
   - Mitigation: `isPublishing` flag disables button during operation
   - Status: ✅ UI prevents rapid clicks

5. **Save fails, publish attempted:**
   - Save operation fails (409 conflict, validation error, etc.)
   - Mitigation: Early return in `handlePublish()` if `save()` returns false
   - Status: ✅ Handled by early return check

**Performance concerns:**
- Publish operation is lightweight (single boolean toggle)
- No N+1 queries or heavy processing
- Rate limiting applied on backend (prevents abuse)

**Backward compatibility:**
- No breaking changes to existing API
- Purely additive feature
- Existing planners default to `published=false`

**Security considerations:**
- ✅ Ownership check: Backend verifies `planner.user.id == userId`
- ✅ Authentication required: `@AuthenticationPrincipal` annotation
- ✅ Rate limiting: Uses `checkCrudLimit(userId, "publish")`
- ✅ Timeout check: Blocks deleted/timed-out users
- ✅ No TOCTOU race: Simple boolean toggle (atomic operation)

**Ambiguities requiring user clarification:**

1. **Button label behavior:**
   - Should button text toggle between "Publish" and "Unpublish"?
   - Or always show "Publish" regardless of current state?
   - User decision: Toggle text (i18n compatible)

2. **Visual indicator:**
   - Should there be an icon showing current published status?
   - User decision: Consider lucide icon (Globe for published, Lock for draft)

3. **Post-publish navigation:**
   - Should user automatically navigate to Gesellschaft page after publishing?
   - Or stay on editor page?
   - User decision: Stay on editor (same as save), transition to view mode

4. **Unpublish confirmation:**
   - Should unpublishing require confirmation dialog?
   - User decision: No confirmation, just toast notification

## Testing Guidelines

### Manual UI Testing

**Happy Path - First Publish:**
1. Open browser and navigate to `/planner/md/new`
2. Fill out planner details (deck, buffs, gifts, etc.)
3. Click the "Publish" button in the header
4. Verify button shows "Publishing..." during operation
5. Verify success toast appears: "Planner published successfully"
6. Verify planner transitions to view mode (same as save behavior)
7. Navigate to `/planner/md/gesellschaft` (community page)
8. Verify the published planner appears in the list
9. Return to editor, click "Publish" again
10. Verify button shows "Publishing..." during operation
11. Verify success toast appears (for unpublish)
12. Navigate to `/planner/md/gesellschaft` again
13. Verify the planner is no longer in the published list

**Save-Before-Publish Flow:**
1. Create new planner
2. Make edits without clicking Save
3. Click Publish button
4. Verify save operation occurs first (loading indicator)
5. Then verify publish operation (toast notification)

**Error Scenarios:**
1. Click Publish on a planner you don't own
2. Verify 403 Forbidden error toast appears
3. Simulate network failure (dev tools offline mode)
4. Click Publish button
5. Verify error toast appears: "Failed to publish planner"
6. Verify planner state is unchanged (no data loss)

### Automated Functional Verification

**API Integration:**
- [ ] `togglePublish()` method exists in `plannerApi.ts`
- [ ] Method uses `PUT` request to `/api/planner/md/{id}/publish`
- [ ] Method validates response with `ServerPlannerResponseSchema`
- [ ] Method accepts `PlannerId | string` as parameter

**Publish Handler:**
- [ ] `handlePublish()` calls `save()` before calling `togglePublish()`
- [ ] Early return if `save()` returns false
- [ ] `isPublishing` flag set to true during operation
- [ ] Success toast shown on successful publish
- [ ] Error toast shown on failed publish
- [ ] `isPublishing` flag reset to false in finally block

**i18n Keys:**
- [ ] `pages.plannerMD.publish.button` exists in all 4 languages
- [ ] `pages.plannerMD.publish.publishing` exists in all 4 languages
- [ ] `pages.plannerMD.publish.success` exists in all 4 languages
- [ ] `pages.plannerMD.publish.failed` exists in all 4 languages

**Button Behavior:**
- [ ] Button disabled while `isPublishing` is true
- [ ] Button text changes to "Publishing..." during operation
- [ ] Button text returns to normal after operation completes

### Edge Cases

**Save Failure Before Publish:**
- [ ] If save fails with 409 Conflict, publish is not attempted
- [ ] If save fails with validation error, publish is not attempted
- [ ] Error toast shows save failure message, not publish message

**Network Errors:**
- [ ] 403 Forbidden: Shows "Failed to publish planner" toast
- [ ] 404 Not Found: Shows "Failed to publish planner" toast
- [ ] 429 Rate Limit: Shows "Failed to publish planner" toast
- [ ] Network timeout: Shows "Failed to publish planner" toast

**Concurrent Operations:**
- [ ] Cannot click Publish while auto-save is running (button disabled)
- [ ] Cannot click Publish while manual save is running (button disabled)
- [ ] Cannot click Save while publish is running (button disabled)

**Idempotency:**
- [ ] Calling publish twice toggles back to original state
- [ ] No error on second publish call (expected behavior)
- [ ] Toast shows success for both publish and unpublish

### Integration Points

**Planner List Integration:**
- [ ] After publishing, planner appears in `/planner/md/gesellschaft`
- [ ] After unpublishing, planner removed from `/planner/md/gesellschaft`
- [ ] Published status visible in personal planner list (`/planner/md`)

**Real-time Sync:**
- [ ] No SSE notification sent on publish toggle (expected - rare operation)
- [ ] Other devices won't see publish status change until refresh (expected)

**Rate Limiting:**
- [ ] Publish operation counted in CRUD rate limit bucket
- [ ] Multiple rapid publishes trigger rate limit error (429)

**Authentication:**
- [ ] Anonymous users cannot publish (401 Unauthorized)
- [ ] Non-owners cannot publish others' planners (403 Forbidden)
- [ ] Deleted accounts cannot publish (403 Forbidden)
