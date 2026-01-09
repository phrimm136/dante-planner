# Learning Report: Account Deletion Feature Architecture

**Date:** 2026-01-09
**Feature:** Settings Page - Account Deletion
**Status:** Complete - 27/27 tests passing

---

## Executive Summary

Implemented account deletion feature with confirmation dialog, grace period handling, and immediate logout flow. Discovered critical auth cache invalidation race condition during code review. All issues resolved through systematic refactoring.

**Key Achievement:** Found and fixed critical security bug where users remained authenticated with deleted accounts for 2 seconds before redirect.

---

## Architecture Decisions

### 1. Auth Cache Invalidation Timing

**Problem:** Where should auth cache be invalidated—mutation hook or component?

**Initial Approach:** Mutation hook's `onSuccess` invalidates cache
**Issue Discovered:** Component's `onSuccess` runs first, triggering 2s redirect before mutation's cache invalidation executes
**Root Cause:** TanStack Query executes component-level callbacks before mutation-level callbacks

**Final Solution:**
- Moved `queryClient.setQueryData(authQueryKeys.me, null)` to component's `onSuccess`
- Removed duplicate invalidation from mutation hook
- Cache clears **immediately** before redirect timer starts

**Learning:** Mutation callback execution order is: component callbacks → mutation hook callbacks. Critical state changes must happen in component callbacks if they affect subsequent component behavior.

---

### 2. API Client Generic Typing

**Challenge:** Backend returns JSON response for DELETE, but existing `ApiClient.delete()` returned `Promise<void>`

**Solution:**
- Changed signature from `delete(endpoint): Promise<void>` to `delete<T>(endpoint): Promise<T>`
- Reused existing `fetch<T>()` method for consistency
- Backward compatible: existing void callers still work

**Pattern Match:** Followed same generic type pattern as `get<T>()`, `post<T>()`, `put<T>()`

**Impact Analysis:**
- Only 1 existing usage in `plannerApi.ts` (safe - wrapper function already declared `Promise<void>`)
- All future DELETE calls now type-safe

---

### 3. Dismiss Prevention Pattern

**Source:** `ConflictResolutionDialog.tsx` lines 64-73

**Implementation:**
```
Key elements copied:
- showCloseButton={false}
- onEscapeKeyDown={preventDismissal}
- onInteractOutside={preventDismissal}
- preventDismissal = (e: Event) => e.preventDefault()
```

**Why This Matters:** Prevents accidental dismissal during destructive actions. User must make explicit choice (Cancel or Delete).

**Reusability:** This pattern applies to any high-stakes confirmation dialog (data deletion, irreversible actions, financial transactions).

---

### 4. Suspense + Auth Gating Pattern

**Source:** `UsernameSection.tsx` three-part structure

**Architecture:**
1. **Outer wrapper:** `<Suspense fallback={<Skeleton />}>`
2. **Content component:** Calls `useAuthQuery()`, checks for null user
3. **Skeleton component:** Loading state while Suspense resolves

**Auth Gating Logic:**
- `if (!user)` → Show sign-in prompt
- `if (user)` → Show authenticated content

**Why This Pattern:**
- SSR-compatible (Suspense handles promise suspension)
- Clear separation: wrapper (Suspense) vs logic (Content) vs fallback (Skeleton)
- Avoids early returns with loading spinners (anti-pattern)

---

### 5. Mutation Structure Pattern

**Source:** `useUpdateKeywordMutation` lines 85-102

**Template:**
```
useMutation({
  mutationFn: async (input) => {
    const data = await ApiClient.method<ResponseType>(endpoint, input)
    const result = Schema.safeParse(data)
    if (!result.success) {
      console.error('Validation failed:', result.error)
      throw new Error('Invalid data from server')
    }
    return result.data
  },
  onError: (error) => {
    console.error('Mutation failed:', error)
  }
})
```

**Key Elements:**
- API call with generic type parameter
- Zod schema validation with `safeParse()`
- Error logging on validation failure
- Throw descriptive error for consumer

**Difference for Account Deletion:**
- Cache invalidation moved to component level (not in hook's onSuccess)
- No request body (DELETE has no payload)

---

## Critical Bug Analysis

### Bug: Auth Cache Invalidation Race Condition

**Severity:** Critical (security issue)

**Manifestation:**
- User clicks "Delete Account"
- Backend marks account as deleted
- Frontend shows success toast
- **User remains logged in for 2 seconds**
- Only then does cache invalidate and redirect

**Root Cause:**
```
Execution flow:
1. deleteAccount.mutate() called
2. API returns success response
3. Component's onSuccess() executes:
   - Shows toast
   - Starts 2s redirect timer
4. Mutation hook's onSuccess() executes:
   - Invalidates auth cache (TOO LATE)
5. After 2s: redirect fires
```

**Fix Applied:**
- Moved `queryClient.setQueryData(authQueryKeys.me, null)` from mutation's onSuccess to component's onSuccess
- Cache now invalidates **before** setTimeout
- Header immediately shows logged-out state
- User sees "Sign in" button during 2-second wait

**Verification:**
- Added test to verify cache invalidation timing
- Manual testing confirmed Header updates immediately

---

## Code Quality Issues Resolved

### 1. Duplicate Auth Cache Invalidation
**Before:** Both mutation hook and component called `setQueryData()`
**After:** Only component calls it
**Reason:** Prevents double invalidation, clarifies responsibility

### 2. Hard-coded Redirect
**Before:** `window.location.href = '/'`
**After:** `navigate({ to: '/' })`
**Reason:** Type-safe routing, maintains router state

### 3. Date Parsing Fragility
**Before:** `new Date(response.permanentDeleteAt).toLocaleDateString()`
**After:** Wrapped in try-catch with fallback to "unknown date"
**Reason:** Graceful degradation on malformed ISO timestamps

### 4. Nullable Grace Period
**Before:** `${response.gracePeriodDays} days`
**After:** `${response.gracePeriodDays ?? 30} days`
**Reason:** Defensive against backend contract changes

### 5. Mixed Export Patterns
**Before:** AccountDeleteDialog had both default and named export
**After:** Named export only
**Reason:** Consistency with codebase conventions

---

## Test Architecture

### Test Coverage: 27 tests across 3 files

**Unit Tests (7):** `useDeleteAccountMutation.test.tsx`
- API call verification
- Response validation with Zod
- Cache invalidation
- Error logging
- isPending state

**Component Tests (14):** `AccountDeleteDialog.test.tsx`
- Confirmation input validation
- Button state management
- Dismiss prevention (ESC/outside-click)
- Callback execution
- Loading state UI

**Integration Tests (6):** `AccountDeleteSection.test.tsx`
- Auth gating (unauthenticated vs authenticated)
- Dialog opening
- Success flow (toast, cache, redirect)
- Error handling
- Pending state

### Testing Challenges

**Challenge 1:** Test file extension mismatch
- `useDeleteAccountMutation.test.ts` contained JSX (QueryClientProvider)
- TypeScript parser failed
- **Fix:** Renamed to `.tsx`

**Challenge 2:** Hook mocking complexity
- Components use `useSuspenseQuery` which needs QueryClient context
- Initial mocks used `ApiClient` layer, causing "No QueryClient" errors
- **Fix:** Mocked `useQueryClient` hook to return `setQueryData` function

**Challenge 3:** Fake timers interference
- `vi.useFakeTimers()` broke React async state updates
- Tests timed out waiting for state changes
- **Fix:** Removed global fake timers, only use for specific timer tests

---

## Pattern Duplication: OAuth Login

### The Problem

Both `UsernameSection.tsx` and `AccountDeleteSection.tsx` contain identical OAuth login code (40+ lines):
- Generate PKCE state/verifier/challenge
- Build Google OAuth URL
- Open popup window
- Handle popup blocking fallback

### Why Not Extract to Shared Hook?

**Attempted:** Create `useGoogleLogin()` hook
**Blocked:** Pattern enforcement hook requires pattern file reference in research.md
**Decision:** Accept duplication as technical debt

### Trade-off Analysis

**Cost of Duplication:**
- 40+ lines repeated across 2 files
- Future auth changes need double updates
- Violates DRY principle

**Cost of Extraction:**
- Need to document new pattern in research.md
- Update pattern enforcement rules
- Create test coverage for shared hook
- Higher up-front effort

**Decision:** Document duplication, defer extraction until pattern enforcement updated

---

## Lessons for Future Features

### 1. Mutation Callback Ordering

**Always document:** When mutations have onSuccess at both hook and component level, specify execution order in plan.md

**Checklist item:** Does this mutation need state changes in component callback? If yes, note callback timing dependency.

### 2. File Extension Validation

**Pre-implementation check:** Test files containing JSX must use `.tsx` extension

**Add to plan.md template:** "Verify test file extensions match content (JSX → .tsx)"

### 3. Date/Time Edge Cases

**Any feature parsing ISO timestamps needs:**
- Defensive try-catch around `new Date()`
- `isNaN(date.getTime())` validation
- Fallback value for malformed input
- Tests for null/undefined/malformed dates

### 4. Pattern Extraction Decision Framework

**Before extracting shared code:**
1. Check pattern enforcement requirements
2. Document in research.md if new pattern
3. Estimate extraction cost vs duplication cost
4. If blocked: document as technical debt, proceed with duplication

---

## Recommendations for Skill Documentation

### Add to TanStack Query Patterns

**Mutation Callback Timing:**
- Document that `mutationFn.onSuccess` fires **after** component-level callbacks
- Show when to use component callbacks vs mutation callbacks
- Example: Auth cache invalidation before redirect

### Add to Date Handling Patterns

**Defensive ISO Timestamp Parsing:**
- Always wrap `new Date()` in try-catch for external data
- Validate with `isNaN(date.getTime())`
- Provide user-friendly fallback values
- Specify locale explicitly: `toLocaleDateString('en-US', { ... })`

### Add to Testing Patterns

**Hook Mocking for Suspense Components:**
- Mock `useQueryClient` when components call it directly
- Avoid `vi.useFakeTimers()` globally (breaks React async)
- Test file extension must match content (JSX = .tsx)

---

## Architectural Principles Validated

### 1. Separation of Concerns
- Dialog component: UI only, no business logic
- Section component: Wires dialog to mutation
- Mutation hook: API call + validation only
- Each layer has single responsibility

### 2. Type Safety End-to-End
- Generic `delete<UserDeletionResponse>()` ensures type safety
- Zod validation catches runtime mismatches
- TypeScript compilation prevents type errors

### 3. Defensive Programming
- Date parsing with fallbacks
- Null coalescing for grace period
- Try-catch around external data
- Validates all API responses

### 4. Pattern Compliance
- 100% pattern reuse from research.md
- No pattern violations detected
- All patterns documented with line references

---

## Success Metrics

**Implementation:**
- 10/10 execution steps complete
- 6/6 core features implemented
- 0 spec ambiguities encountered

**Quality:**
- 27/27 tests passing
- 0 critical issues remaining
- 0 TypeScript errors
- All code review issues resolved

**Process:**
- Research.md mapping: 100% accurate
- Plan.md execution: Followed sequentially
- Pattern compliance: Complete

---

## Conclusion

The account deletion feature successfully demonstrates:
- Systematic spec-driven development
- Pattern-first architecture
- Test-driven implementation
- Critical bug discovery through code review
- Systematic resolution of all quality issues

**Key Takeaway:** Auth cache invalidation timing bugs are subtle but critical. TanStack Query's callback execution order must be explicitly considered when mutations trigger subsequent state-dependent actions.

This implementation serves as a reference for future destructive action flows requiring confirmation dialogs, auth state changes, and grace period handling.
