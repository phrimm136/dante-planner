# Task: Account Deletion Feature

## Description

Add account deletion capability to the Settings page. Users should be able to delete their accounts through a confirmation flow that requires typing "DELETE" to proceed. The feature integrates with the existing backend soft-delete system that provides a 30-day grace period with automatic reactivation if the user logs back in.

**User Flow:**
- Authenticated users see a "Delete Account" button in a danger zone section
- Clicking the button opens a confirmation dialog that cannot be dismissed accidentally
- Users must type "DELETE" (exact match) to enable the delete button
- Dialog displays consequences: account scheduled for deletion, all planners/comments deleted, immediate logout
- Dialog shows grace period information: 30 days, can cancel by logging back in
- On confirmation, account deletion is initiated
- Success shows a toast message with the permanent deletion date from server response
- After 2 seconds, user is logged out and redirected to home page
- On error, toast shows failure message and dialog remains open for retry

**Unauthenticated Behavior:**
- Show sign-in prompt instead of delete button

**Visual Design:**
- Place delete section in danger zone styling (red border, red background tint)
- Use destructive button variant (red button)
- Separate from other settings with visual divider

**Confirmation Requirements:**
- Type "DELETE" exactly to enable delete button
- Prevent dialog dismissal via ESC key or clicking outside
- No close button on dialog (must choose Cancel or Delete)

**Post-Deletion Behavior:**
- Display toast with grace period date from server: "Account scheduled for deletion on [permanentDeleteScheduledAt]. Log in within [gracePeriodDays] days to cancel."
- Wait 2 seconds
- Clear authentication cache (triggers logout)
- Redirect to home page

**Critical: API Response Handling**
- Backend returns 200 OK with JSON body (not 204 No Content)
- Response contains: message, deletedAt, permanentDeleteScheduledAt, gracePeriodDays
- Frontend must parse response to show deletion date in toast

## Research

- **API client DELETE method**: Current implementation returns `Promise<void>` but backend returns JSON response - needs fix
- **Backend response format**: Verify `UserDeletionResponse` structure matches `{ message, deletedAt, permanentDeleteScheduledAt, gracePeriodDays }`
- **Existing patterns**: Study `UsernameSection.tsx` for Suspense wrapper + auth gating pattern
- **Dialog patterns**: Study `ConflictResolutionDialog.tsx` for destructive action dialog with dismiss prevention
- **Mutation patterns**: Study `useUpdateKeywordMutation` for mutation + cache invalidation pattern
- **Auth cache invalidation**: Understand how `queryClient.setQueryData(authQueryKeys.me, null)` triggers logout
- **Toast implementation**: Check existing toast usage with `sonner` library
- **Navigation**: Check TanStack Router redirect patterns after mutation
- **Date formatting**: Research how to format ISO timestamp for user-friendly display in toast

## Scope

**Files to read for context:**
- `frontend/src/lib/api.ts` - **CRITICAL**: Check current `delete()` implementation and usage
- `frontend/src/components/settings/UsernameSection.tsx` - Suspense wrapper pattern, auth gating, OAuth flow reference
- `frontend/src/hooks/useUserSettingsQuery.ts` - Mutation pattern with cache invalidation
- `frontend/src/hooks/useAuthQuery.ts` - Auth state checking and query keys
- `frontend/src/components/planner/ConflictResolutionDialog.tsx` - Destructive dialog pattern with dismiss prevention
- `frontend/src/routes/SettingsPage.tsx` - Current page structure
- `frontend/src/types/UserSettingsTypes.ts` - Existing type patterns
- `frontend/src/schemas/UserSettingsSchemas.ts` - Existing schema patterns
- `docs/architecture-map.md` - Settings page pattern (lines 402-428), User Management (lines 33-35)

**Before implementation:**
- Search codebase for existing `ApiClient.delete(` usage to assess impact of API change
- Verify no other components depend on delete returning void

## Target Code Area

**New files to create:**
- `frontend/src/components/settings/AccountDeleteSection.tsx` - Main component with Suspense wrapper
- `frontend/src/components/settings/AccountDeleteDialog.tsx` - Confirmation dialog component

**Files to modify:**
- **`frontend/src/lib/api.ts`** - Fix `delete()` method to support generic typed responses (breaking change)
- `frontend/src/types/UserSettingsTypes.ts` - Add `UserDeletionResponse` interface
- `frontend/src/schemas/UserSettingsSchemas.ts` - Add `UserDeletionResponseSchema` Zod schema
- `frontend/src/hooks/useUserSettingsQuery.ts` - Add `useDeleteAccountMutation` hook with typed delete call
- `frontend/src/routes/SettingsPage.tsx` - Integrate new section with danger zone styling

**Files NOT to modify:**
- Backend files (endpoint already complete)
- i18n JSON files (use inline default values per existing pattern)
- Constants files (use inline Tailwind classes for danger zone styling)

## API Client Fix Required

### Current Problem

The existing `ApiClient.delete()` method (api.ts:130-151) assumes DELETE returns no content:

```typescript
static async delete(endpoint: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    method: 'DELETE',
    headers,
    credentials: 'include',
  });
  // ... auth handling ...
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }
  // DELETE returns no content (204)
}
```

**But:** Backend's `DELETE /api/user/me` returns 200 OK with JSON body:
```json
{
  "message": "Account scheduled for deletion",
  "deletedAt": "2026-01-09T10:30:00Z",
  "permanentDeleteScheduledAt": "2026-02-08T10:30:00Z",
  "gracePeriodDays": 30
}
```

### Required Fix

Update `delete()` to support generic typed responses, matching existing `get<T>`, `post<T>`, `put<T>` pattern:

```typescript
static async delete<T>(endpoint: string): Promise<T> {
  return this.fetch<T>(endpoint, { method: 'DELETE' });
}
```

**Why this works:**
- Reuses existing `fetch<T>` that handles auth (401 retry), errors, JSON parsing
- Consistent with other HTTP methods
- Type-safe via generic parameter
- Backward compatible: callers not expecting response can ignore return value

**Migration impact:**
- Check if any existing code calls `ApiClient.delete()` and expects void
- Search pattern: `ApiClient.delete(` in frontend/src
- If no existing usages, this is a safe change

**Alternative (if breaking change is problematic):**
- Keep `delete()` as-is (returns void)
- Add new method `deleteWithResponse<T>(endpoint): Promise<T>` for this use case
- Decision: Prefer updating `delete<T>` for consistency unless existing usage found

## System Context (Senior Thinking)

**Feature domain:** Settings / User Account Management
- Public-access page with authenticated-gated content
- Follows existing "Settings Page Pattern" (architecture-map:402-428)

**Core files in this domain:**
- `routes/SettingsPage.tsx` - Main settings page
- `components/settings/UsernameSection.tsx` - Existing settings section pattern
- `hooks/useUserSettingsQuery.ts` - Data hooks for user settings
- `hooks/useAuthQuery.ts` - Authentication state checking
- `lib/api.ts` - HTTP client (needs modification for typed DELETE)
- Backend: `controller/UserController.java` (DELETE endpoint already exists)
- Backend: `service/UserAccountLifecycleService.java` (deletion logic already exists)

**Cross-cutting concerns touched:**
- **Authentication**: Uses `useAuthQuery()` for gating, invalidates auth cache on success
- **Validation**: Zod schemas for response validation
- **HTTP Client**: Modifies `ApiClient.delete()` to support typed responses
- **i18n**: Inline default values following existing pattern
- **Toast notifications**: `sonner` library for success/error messages
- **Navigation**: TanStack Router for post-deletion redirect

**Backend status:**
- ✅ 100% implemented - no backend changes needed
- Endpoint: `DELETE /api/user/me` (UserController.java:86-101)
- Response: `UserDeletionResponse` DTO with message, dates, gracePeriodDays (200 OK, not 204)
- Soft-delete with 30-day grace period
- Automatic reactivation on re-login
- Scheduled hard-delete via cron job

## Impact Analysis

**Files being modified:**

| File | Impact Level | Why | Notes |
|------|--------------|-----|-------|
| `api.ts` | **Medium** | Changing HTTP client method signature | Search for existing `delete()` usages first |
| `UserSettingsTypes.ts` | Low | Adding new type, doesn't change existing | Safe |
| `UserSettingsSchemas.ts` | Low | Adding new schema, doesn't change existing | Safe |
| `useUserSettingsQuery.ts` | Low | Adding new mutation hook, doesn't change existing | Safe |
| `SettingsPage.tsx` | Low | Page-isolated, only structural change | Safe |
| `AccountDeleteSection.tsx` (new) | N/A | New component | N/A |
| `AccountDeleteDialog.tsx` (new) | N/A | New component | N/A |

**What depends on modified files:**
- **api.ts**: Used throughout frontend for all API calls
  - **Risk**: If any code uses `ApiClient.delete()` and expects void, type error will occur
  - **Mitigation**: Search for usages before changing; if found, use alternative approach (deleteWithResponse)
- `useUserSettingsQuery.ts` is imported by `UsernameSection.tsx` (adding new export won't break existing)
- `SettingsPage.tsx` is a route - only affects Settings page users
- Auth cache invalidation affects `Header.tsx` (uses `useAuthQuery()` to show login state)

**Potential ripple effects:**
- API change: May affect other DELETE calls if they exist (search required)
- Auth cache invalidation (`queryClient.setQueryData(authQueryKeys.me, null)`) will trigger Header to show logged-out state
- No other components depend on the new functionality
- Isolated feature - failure doesn't affect other settings

**High-impact files involved:**
- `api.ts` - Medium impact (widely used HTTP client)
- All other files: Low impact (isolated or new)

**Pre-implementation verification:**
```bash
# Search for existing ApiClient.delete usage
grep -r "ApiClient\.delete(" frontend/src/
# If results found, assess impact and consider alternative approach
```

## Risk Assessment

**API Client Modification:**
- **Risk**: Breaking existing DELETE calls if they exist elsewhere
- **Mitigation**: Search codebase for `ApiClient.delete(` before making change
- **Fallback**: If usages found, create `deleteWithResponse<T>` instead of modifying `delete()`

**Security:**
- ✅ Authentication required (JWT validation on backend)
- ✅ Authorization handled (backend extracts user ID from JWT)
- ✅ Rate limiting applied (backend CRUD rate limit)
- ✅ Idempotent (calling DELETE twice is safe)
- ✅ Confirmation required (typing "DELETE" prevents accidents)
- ✅ No ESC dismiss (explicit choice required)

**Edge cases:**
- User deletes account, immediately re-logs in: Backend handles reactivation automatically
- Grace period expires, no re-login: Backend scheduler performs hard-delete
- User clicks delete twice: Backend returns same grace period date (idempotent)
- Network failure during delete: TanStack Query automatic retry (3 attempts)
- User offline, delete response never arrives: No optimistic update, mutation error shows toast
- User deletes on Device A, still browsing on Device B: Device B's auth token becomes invalid on next check
- **Response parsing failure**: Zod validation catches malformed response, shows error toast

**Backward compatibility:**
- ⚠️ API change may break existing code (search required)
- ✅ No breaking changes to Settings page itself
- ✅ Backend endpoint already stable

**Performance:**
- No concerns - single DELETE request, no cascade operations on frontend
- Backend handles cascade deletion asynchronously

**Concurrency:**
- User deletes and re-authenticates in same session: Backend reactivation logic handles this
- Multiple devices: Grace period starts immediately, any device can reactivate

**Errors to handle:**
- 401 Unauthorized: Show error toast, ApiClient auto-retries after refresh
- 403 Forbidden: Show "Not authorized" toast
- 500 Server Error: Show "Failed to delete, try again" toast
- Network timeout: Show "Connection lost, try again" toast
- Zod validation failure: Log error, show generic "Invalid response" toast

**Ambiguities resolved:**
- ✅ Confirmation method: Type "DELETE" (no password re-entry)
- ✅ Post-delete flow: Toast + 2s delay + logout + redirect
- ✅ API response: Backend returns JSON (not 204 No Content)
- ✅ Date formatting: Show `permanentDeleteScheduledAt` formatted as readable date

## Testing Guidelines

### Pre-Implementation Verification

**API Usage Search:**
1. Run `grep -r "ApiClient\.delete(" frontend/src/` to find all DELETE usages
2. If any results found, document them and assess impact
3. Decide: modify `delete()` or create `deleteWithResponse<T>()`

### Manual UI Testing

**Unauthenticated Flow:**
1. Navigate to Settings page while logged out
2. Verify "Username" section shows "Sign in to customize your username"
3. Verify "Delete Account" section shows "Sign in to manage your account"
4. Verify no delete button is visible

**Authenticated Flow:**
1. Log in with Google OAuth
2. Navigate to Settings page
3. Verify "Username" section shows dropdown and save button
4. Scroll down to danger zone section
5. Verify section has red border and red background tint
6. Verify "Delete Account" button is visible and red (destructive variant)

**Delete Confirmation Dialog:**
1. Click "Delete Account" button
2. Verify dialog opens and cannot be dismissed by clicking outside
3. Verify dialog has no close button (X)
4. Press ESC key - verify dialog does not close
5. Verify dialog shows warning about consequences
6. Verify dialog shows grace period information (30 days)
7. Verify confirmation input field is visible
8. Verify "Delete Account" button is disabled
9. Type "DELET" (incomplete) - verify button stays disabled
10. Type "DELETE" (exact match) - verify button becomes enabled
11. Clear input - verify button becomes disabled again

**Successful Deletion:**
1. Type "DELETE" in confirmation input
2. Click "Delete Account" button
3. Verify button shows "Deleting..." text and is disabled
4. Wait for mutation to complete
5. **Verify toast shows deletion date from server response**: "Account scheduled for deletion on [formatted date]. Log in within 30 days to cancel."
6. Verify dialog closes
7. Wait 2 seconds
8. Verify user is logged out (Header shows "Sign in" button)
9. Verify redirect to home page
10. Try to navigate back to Settings page
11. Verify delete section shows "Sign in to manage your account"

**Error Handling:**
1. Disconnect network (browser dev tools offline mode)
2. Type "DELETE" and click delete button
3. Verify error toast appears "Failed to delete account. Please try again."
4. Verify dialog remains open
5. Reconnect network and retry
6. Verify successful deletion flow

**Cancel Flow:**
1. Click "Delete Account" button
2. Dialog opens
3. Click "Cancel" button
4. Verify dialog closes
5. Verify no changes to account (still logged in)
6. Verify no toast messages

**API Response Validation:**
1. Open browser DevTools Network tab
2. Initiate account deletion
3. Inspect DELETE request to `/api/user/me`
4. Verify response status is 200 OK (not 204 No Content)
5. Verify response body contains: message, deletedAt, permanentDeleteScheduledAt, gracePeriodDays
6. Verify toast displays formatted `permanentDeleteScheduledAt` date

### Automated Functional Verification

**API Client:**
- [ ] `ApiClient.delete<T>()` accepts generic type parameter
- [ ] `delete<T>()` returns typed Promise (not void)
- [ ] `delete<T>()` reuses `fetch<T>()` method for consistency
- [ ] DELETE requests include credentials (cookies)
- [ ] 401 unauthorized triggers auto-refresh and retry
- [ ] No existing code broken by API change (search verified)

**Authentication Gating:**
- [ ] Unauthenticated users see sign-in prompt instead of delete button
- [ ] Authenticated users see delete button in danger zone styling
- [ ] Auth state changes (login/logout) update UI correctly

**Confirmation Dialog:**
- [ ] Dialog opens when delete button clicked
- [ ] Dialog cannot be dismissed by ESC key
- [ ] Dialog cannot be dismissed by clicking outside
- [ ] Dialog has no close button
- [ ] Confirmation input accepts text input
- [ ] Delete button disabled until "DELETE" typed exactly (case-sensitive)
- [ ] Delete button enabled when "DELETE" typed
- [ ] Cancel button always enabled
- [ ] Clicking cancel closes dialog without changes

**Mutation Flow:**
- [ ] Mutation sends DELETE request to `/api/user/me`
- [ ] Mutation calls `ApiClient.delete<UserDeletionResponse>()` with type parameter
- [ ] Response parsed as JSON (not ignored)
- [ ] Response validated against `UserDeletionResponseSchema`
- [ ] Zod validation catches malformed responses
- [ ] Success invalidates auth cache (`queryClient.setQueryData(authQueryKeys.me, null)`)
- [ ] Success shows toast with `permanentDeleteScheduledAt` date formatted
- [ ] Success waits 2 seconds before redirect
- [ ] Success redirects to home page
- [ ] Error shows toast with error message
- [ ] Error keeps dialog open for retry
- [ ] Mutation disables buttons while pending
- [ ] Mutation shows "Deleting..." text while pending

**Data Validation:**
- [ ] `UserDeletionResponse` type includes all required fields
- [ ] `UserDeletionResponseSchema` validates message (string), deletedAt (string), permanentDeleteScheduledAt (string), gracePeriodDays (number)
- [ ] Invalid response from server logs error and throws
- [ ] Grace period date formatted correctly from ISO timestamp
- [ ] Missing fields in response caught by Zod validation

### Edge Cases

**Input Validation:**
- [ ] Empty input: Delete button disabled
- [ ] Wrong text ("delete" lowercase): Delete button disabled
- [ ] Extra spaces ("DELETE "): Delete button disabled
- [ ] Partial text ("DEL"): Delete button disabled
- [ ] Exact match ("DELETE"): Delete button enabled

**Network Errors:**
- [ ] 401 Unauthorized: ApiClient retries after refresh, then succeeds or shows error
- [ ] 403 Forbidden: Shows "Not authorized" toast
- [ ] 500 Server Error: Shows "Failed to delete" toast, allows retry
- [ ] Network timeout: Shows "Connection lost" toast, allows retry
- [ ] No response received: Handled by TanStack Query retry logic

**Response Validation:**
- [ ] Malformed JSON: Zod validation fails, shows error toast
- [ ] Missing `gracePeriodDays`: Zod validation fails, shows error toast
- [ ] Invalid date format: Zod validation catches, shows error toast
- [ ] Empty response body: Zod validation fails, shows error toast

**State Management:**
- [ ] Dialog state resets when opened (confirmation input cleared)
- [ ] Mutation pending state prevents double-submission
- [ ] Auth cache invalidation triggers Header update
- [ ] Redirect only happens after auth cache cleared

**Concurrent Actions:**
- [ ] Clicking delete multiple times in rapid succession: Only one mutation fires
- [ ] Deleting on Device A while browsing on Device B: Device B logs out on next auth check
- [ ] Re-logging in immediately after deletion: Backend reactivates account

**Grace Period Display:**
- [ ] Backend returns `gracePeriodDays` field (should be 30)
- [ ] Backend returns `permanentDeleteScheduledAt` as ISO timestamp
- [ ] Frontend formats date for user-friendly display
- [ ] Toast shows complete message with formatted date

### Integration Points

**Authentication System:**
- [ ] `useAuthQuery()` hook returns null after cache invalidation
- [ ] Header component updates to show logged-out state
- [ ] Settings page shows sign-in prompt after logout
- [ ] OAuth login flow works after logout

**Backend Integration:**
- [ ] DELETE /api/user/me endpoint responds with 200 OK (not 204)
- [ ] Response body matches `UserDeletionResponse` structure
- [ ] Backend applies rate limiting (CRUD bucket)
- [ ] Backend returns idempotent response on duplicate calls
- [ ] Backend soft-delete fields populated correctly

**Navigation:**
- [ ] Redirect to "/" route works after deletion
- [ ] Browser history stack includes redirect
- [ ] User can navigate back to Settings page (shows sign-in prompt)

**Toast System:**
- [ ] Success toast appears with grace period date from server
- [ ] Error toast appears on mutation failure
- [ ] Toast auto-dismisses after timeout
- [ ] Multiple toasts don't stack confusingly

**HTTP Client:**
- [ ] `ApiClient.delete<T>()` works with typed responses
- [ ] Other HTTP methods (GET, POST, PUT) unaffected by change
- [ ] Auth refresh logic works with DELETE requests
- [ ] Error handling consistent across all methods

**Reactivation (Out of scope for frontend, but verify backend):**
- [ ] Re-logging in within grace period reactivates account
- [ ] Reactivated account shows no deletion warnings
- [ ] Grace period is cancelled after reactivation

## Implementation Order

1. **API Client Fix** (foundation - highest risk)
   - Search for existing `ApiClient.delete()` usages
   - Update `delete()` to support generic type parameter OR create `deleteWithResponse<T>()`
   - Test: Verify no TypeScript errors, existing code unaffected

2. **Types & Schemas** (data contract)
   - Add `UserDeletionResponse` to types
   - Add `UserDeletionResponseSchema` to schemas

3. **Data Hook** (business logic)
   - Add `useDeleteAccountMutation` to useUserSettingsQuery.ts
   - Use `ApiClient.delete<UserDeletionResponse>()` with type parameter
   - Parse response date for toast message

4. **Confirmation Dialog** (isolated UI)
   - Create `AccountDeleteDialog.tsx`
   - Implement confirmation input + validation
   - Add consequence warnings and grace period info

5. **Main Component** (integration)
   - Create `AccountDeleteSection.tsx`
   - Implement Suspense wrapper + auth gating
   - Integrate dialog and mutation
   - Add date formatting for toast

6. **Page Integration** (final assembly)
   - Update `SettingsPage.tsx`
   - Add section header + danger zone styling
   - Test complete flow end-to-end

## Success Criteria

✅ **Functional Requirements:**
- Authenticated users can delete accounts
- Confirmation requires typing "DELETE"
- Success shows toast with **actual deletion date from server response**
- User logged out after 2s delay
- Redirected to home page

✅ **API Requirements:**
- `ApiClient.delete()` supports typed responses
- DELETE request returns JSON response (not void)
- Response validated with Zod schema
- Date extracted from response and formatted

✅ **Security Requirements:**
- Auth required (JWT validation)
- Cannot be dismissed accidentally
- Idempotent (safe to retry)
- Soft-delete with grace period

✅ **UX Requirements:**
- Clear warning about consequences
- Grace period prominently displayed with **actual date**
- Danger zone visually separated
- Loading states during mutation
- Error handling with retry option

## Open Questions

- [ ] Date formatting: Use `toLocaleDateString()` or library like `date-fns`?
- [ ] If existing `ApiClient.delete()` usages found: Modify or create `deleteWithResponse<T>()`?
- [ ] Should toast show both `gracePeriodDays` and `permanentDeleteScheduledAt` or just the date?
