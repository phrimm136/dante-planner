# Publish Functionality - Status Tracking

## Execution Progress

**Last Updated:** 2026-01-10 (Implementation Complete + Critical Fixes)
**Current Step:** 5/5
**Current Phase:** Complete - All phases + error handling improvements

### Milestones

- [x] M1: Phase 1-2 Complete (Data + API Layer)
- [x] M2: Phase 3 Complete (UI Integration)
- [ ] M3: Manual Verification Passed (Core Flow)
- [ ] M4: Manual Verification Passed (Toggle Behavior)
- [ ] M5: Error Scenarios Validated

### Step Log

- Step 1: ✅ complete - Add `published` to ServerPlannerResponseSchema
- Step 2: ✅ complete - Add `togglePublish()` to plannerApi
- Step 3: ✅ complete - Implement `handlePublish()` save-then-publish flow
- Step 4: ✅ complete - Add published state tracking + auth guard for button visibility
- Step 5: ✅ complete - Code review + critical fixes (published state initialization + better error messages)

---

## Feature Status

### Core Features

- [ ] F1: Published state tracking - Verify: Check `published` field in response after save
- [ ] F2: Toggle publish API call - Verify: Network tab shows PUT to `/api/planner/md/{id}/publish`
- [ ] F3: Save-before-publish flow - Verify: Save completes before publish API call
- [ ] F4: Error handling - Verify: Error toast on 403/404/429/network failure
- [ ] F5: Success feedback - Verify: Toast message on successful publish/unpublish
- [ ] F6: Conditional button text - Verify: Button shows "Publish" vs "Unpublish" based on state

### Edge Cases

- [ ] E1: Save failure aborts publish - Verify: Publish not attempted if save returns false
- [ ] E2: Rapid clicks prevented - Verify: Button disabled while `isPublishing` is true
- [ ] E3: Network failure no data loss - Verify: Planner state unchanged after error
- [ ] E4: Published status in list - Verify: Appears in/removed from Gesellschaft page

### Integration

- [ ] I1: Gesellschaft page shows published planners - Verify: After publish, navigate and confirm
- [ ] I2: Personal list shows published indicator - Verify: (Future enhancement - not in scope)
- [ ] I3: Rate limiting handled - Verify: Rapid publishes trigger 429 error toast

### Dependency Verification (from plan.md)

- [ ] D1: Schema consumers compile without errors after `published` field addition
- [ ] D2: API method imports resolve in PlannerMDNewPage.tsx
- [ ] D3: Toast library functional for success/error messages
- [ ] D4: i18n keys resolve without missing translation warnings

---

## Testing Checklist

### Manual Verification (from instructions.md)

**Happy Path - First Publish:**

- [ ] MV1: Open `/planner/md/new`, fill planner details
- [ ] MV2: Click "Publish" button
- [ ] MV3: Verify "Publishing..." text during operation
- [ ] MV4: Verify success toast appears
- [ ] MV5: Navigate to `/planner/md/gesellschaft`
- [ ] MV6: Verify planner appears in published list

**Happy Path - Unpublish:**

- [ ] MV7: Return to editor, click "Publish" again (toggle unpublish)
- [ ] MV8: Verify success toast
- [ ] MV9: Navigate to Gesellschaft page
- [ ] MV10: Verify planner removed from list

**Save-Before-Publish:**

- [ ] MV11: Create planner, make edits without clicking Save
- [ ] MV12: Click Publish button
- [ ] MV13: Verify auto-save occurs first (loading indicator)
- [ ] MV14: Verify publish completes with toast

**Error Scenarios:**

- [ ] MV15: Simulate 403 error → Error toast shown
- [ ] MV16: Offline mode → Network error toast shown
- [ ] MV17: Rapid clicks → Button disabled, no race condition

---

## Summary

**Steps:** 0/5 complete
**Features:** 0/6 verified
**Manual Tests:** 0/17 passed
**Overall:** 0%
