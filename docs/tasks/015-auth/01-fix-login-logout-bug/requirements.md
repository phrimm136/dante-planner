# Task: Fix Authentication Session Bugs

## Description
Fix three interconnected authentication bugs affecting session management:

### Bug 1: Logout Response Handling
The logout endpoint returns HTTP 204 (No Content), but the ApiClient unconditionally calls `response.json()` on all successful responses. Since 204 has no body, this throws a SyntaxError, causing the logout mutation to fail silently. Users click logout but may still appear logged in until the cache expires.

**Expected behavior:**
- Logout request succeeds
- Show popup toast confirming logout
- Refresh the page (pages without privilege will show error boundary)
- Auth cache is cleared

### Bug 2: Guest Response Masking Errors
The `/api/auth/me` endpoint returns 401 for guests, and the frontend catch block returns `null` for ANY error. This conflates "guest user" (401) with "server error" (500) or network failures. Users see "Sign In" button instead of error feedback when the backend is down.

**Expected behavior:**
- Guest users (no token): Return null, show login UI
- Server errors (5xx): Show error toast, don't mask as guest state
- Network failures: Show appropriate error feedback

### Bug 3: Session Reset on Page Refresh (Critical)
Session resets within 1 minute of page refresh despite having 15min access token and 7-day refresh token. Root cause: `api.ts:53` excludes ALL `/auth/` endpoints from the 401 refresh flow, including `/api/auth/me`. When access token expires and `/auth/me` returns 401, the refresh mechanism is never triggered.

**Expected behavior:**
- Page refresh with valid refresh token should restore session
- Only `/auth/refresh` and `/auth/logout` should skip the refresh retry
- `/auth/me` returning 401 should trigger token refresh

## Design Decisions
1. **Toast system**: Sonner
2. **Error UI for 5xx**: Toast only (server logs full error state)
3. **Multi-tab logout**: Server-side only - rely on existing token blacklisting. Other tabs fail gracefully on next API call (401 → redirect). No BroadcastChannel sync needed.

## Research
- Current ApiClient 204 handling patterns in other endpoints (DELETE operations)
- Toast notification system location and usage patterns
- Error boundary behavior for unauthenticated pages
- TanStack Query error handling patterns for distinguishing error types

## Scope
Files to READ for context:
- `frontend/src/lib/api.ts` - ApiClient implementation
- `frontend/src/hooks/useAuthQuery.ts` - Auth query and mutation hooks
- `frontend/src/components/Header.tsx` - Logout button usage
- `backend/src/main/java/org/danteplanner/backend/controller/AuthController.java` - Auth endpoints
- `frontend/src/lib/queryClient.ts` - Query client configuration
- `frontend/src/components/ui/sonner.tsx` or toast system - For toast notifications

## Target Code Area
Files to MODIFY:
- `frontend/src/lib/api.ts` - Fix 204 handling, narrow refresh exclusion
- `frontend/src/hooks/useAuthQuery.ts` - Add error type distinction, logout navigation
- `frontend/src/components/Header.tsx` - Add toast and page refresh on logout

## System Context (Senior Thinking)
- Feature domain: Authentication (from architecture-map)
- Core files in this domain:
  - Frontend: `routes/auth/callback/google.tsx`, `lib/api.ts`, `hooks/useAuthQuery.ts`
  - Backend: `controller/AuthController.java`, `service/JwtService.java`, `security/JwtAuthenticationFilter.java`
- Cross-cutting concerns touched:
  - Auth Tokens: HttpOnly cookies managed by backend
  - API Client: `lib/api.ts` - affects ALL API calls
  - Error Handling: `components/common/ErrorBoundary.tsx`
  - Cache: TanStack Query cache invalidation

## Impact Analysis
- Files being modified:
  - `lib/api.ts` (HIGH impact - ALL API calls go through this)
  - `hooks/useAuthQuery.ts` (MEDIUM impact - auth-dependent components)
  - `Header.tsx` (HIGH impact - global layout, appears on all pages)
- What depends on these files:
  - `api.ts`: Every hook using ApiClient, all data fetching
  - `useAuthQuery.ts`: Header, SettingsPage, any auth-gated UI
  - `Header.tsx`: Root layout, all pages
- Potential ripple effects:
  - Changing 204 handling affects all DELETE endpoints
  - Changing refresh exclusion must not create infinite loops
  - Logout navigation must not break OAuth popup flow
- High-impact files to watch:
  - `lib/api.ts` - modify with extreme care, test all API operations

## Risk Assessment
- Edge cases:
  - Concurrent API calls during token refresh (existing queue handles this)
  - Logout during pending requests
  - Network failure during refresh attempt
  - Multiple tabs with different session states
- Performance concerns:
  - Token refresh should not cause redundant refetches
- Backward compatibility:
  - Existing DELETE endpoints (returning 204) must continue working
  - OAuth popup flow must not break with new logout behavior
- Security considerations:
  - Refresh token must only be used for legitimate refresh endpoint
  - Token blacklisting on logout must still work
  - No exposure of tokens in error messages

## Testing Guidelines

### Manual UI Testing

**Bug 1: Logout**
1. Login with Google OAuth
2. Verify user avatar/name shows in Header
3. Click user menu and select Logout
4. Verify toast notification appears confirming logout
5. Verify page refreshes
6. Verify login button appears in Header
7. Try accessing `/planner/md/new` (auth-required page)
8. Verify error boundary or redirect to login

**Bug 2: Error Distinction**
1. Stop the backend server
2. Refresh the page
3. Verify error feedback appears (not just login button)
4. Restart backend
5. Refresh page
6. Verify normal behavior resumes

**Bug 3: Session Persistence**
1. Login with Google OAuth
2. Wait 30 seconds (don't wait for full 15min expiry in dev)
3. Refresh the page (F5)
4. Verify user is still logged in
5. Check Network tab - should see `/api/auth/me` call
6. If 401, should see subsequent `/api/auth/refresh` call
7. Session should be restored without manual re-login

**Token Expiry Test (requires backend config change for testing)**
1. Temporarily set access token expiry to 1 minute in backend
2. Login
3. Wait 70 seconds
4. Perform any API action (e.g., save planner)
5. Verify action succeeds (refresh should happen transparently)
6. Restore original token expiry

### Automated Functional Verification
- [ ] 204 Response: ApiClient returns successfully for 204 responses without JSON parsing error
- [ ] Logout mutation: Completes without error, clears auth cache
- [ ] Refresh trigger: 401 on `/auth/me` triggers refresh attempt
- [ ] Refresh skip: 401 on `/auth/refresh` does NOT retry (prevents infinite loop)
- [ ] Refresh skip: 401 on `/auth/logout` does NOT retry
- [ ] Error distinction: 5xx errors are not masked as guest state
- [ ] Cache update: Auth query cache is null after logout

### Edge Cases
- [ ] Rapid logout clicks: Only one logout request fires
- [ ] Logout during pending request: Pending requests fail gracefully
- [ ] Refresh failure: If refresh fails, user is redirected to home (existing behavior)
- [ ] 204 with empty body: Returns undefined/empty object, not error
- [ ] Network failure on logout: Shows error toast, doesn't leave user in limbo

### Integration Points
- [ ] Header component: Shows correct auth state after login/logout
- [ ] Protected routes: ErrorBoundary shows for unauthenticated users
- [ ] OAuth popup: Login flow still works after logout changes
- [ ] Toast system: Logout toast appears and disappears appropriately
