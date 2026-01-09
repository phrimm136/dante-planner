# Account Deletion Feature - Execution Plan

## Planning Gaps (STOP if any)

**✅ NONE** - Research is complete and comprehensive. All patterns validated, all dependencies identified, backend verified.

**Open Decision (Non-Blocking):**
- Date formatting: Use native `toLocaleDateString()` (research shows existing pattern in ConflictResolutionDialog) vs external library (date-fns). **Recommendation: Use native to match existing codebase pattern.**

## Execution Overview

Implement account deletion feature for Settings page with typed API client, confirmation dialog, and grace period handling. The feature enables authenticated users to delete accounts through a two-step confirmation process (type "DELETE"), shows server-returned deletion date in toast, logs out after 2 seconds, and redirects home.

**Core challenge**: API client currently assumes DELETE returns void, but backend returns JSON with grace period date. Solution: Refactor `delete()` to support generics like other HTTP methods.

**Pattern compliance**: 100% - follows UsernameSection (Suspense + auth gating), ConflictResolutionDialog (dismiss prevention), and useUpdateKeywordMutation (mutation structure).

## Dependency Analysis (Senior Thinking)

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| `lib/api.ts` | **Medium** | None (HTTP client base) | All API calls (every hook using ApiClient) |
| `types/UserSettingsTypes.ts` | Low | None | `useUserSettingsQuery.ts`, new components |
| `schemas/UserSettingsSchemas.ts` | Low | `UserSettingsTypes.ts` | `useUserSettingsQuery.ts` (validation) |
| `hooks/useUserSettingsQuery.ts` | Low | `api.ts`, schemas, types, `useAuthQuery.ts` | `UsernameSection.tsx`, new `AccountDeleteSection.tsx` |
| `routes/SettingsPage.tsx` | Low | New `AccountDeleteSection.tsx` | None (route component) |
| `components/settings/AccountDeleteSection.tsx` (new) | N/A | `useUserSettingsQuery.ts`, `AccountDeleteDialog.tsx`, `useAuthQuery.ts` | `SettingsPage.tsx` |
| `components/settings/AccountDeleteDialog.tsx` (new) | N/A | UI components from shadcn/ui | `AccountDeleteSection.tsx` |

### Ripple Effect Map

**API Client Change (`api.ts`):**
- Change: `delete(): Promise<void>` → `delete<T>(): Promise<T>`
- Affected: `plannerApi.ts:89` (only existing usage - safe, already ignores return value)
- Impact: **Low** - Backward compatible (void calls still work, just ignore returned promise)
- Mitigation: Existing usage returns `Promise<void>` from wrapper function, unaffected by underlying generic change

**useUserSettingsQuery.ts exports:**
- Change: Add `useDeleteAccountMutation` export
- Consumers: Currently only `UsernameSection.tsx` imports from this file
- Impact: **None** - Additive change, no breaking modifications
- New consumer: `AccountDeleteSection.tsx` will import new hook

**SettingsPage.tsx structure:**
- Change: Add new `<section>` below UsernameSection
- Impact: **None** - Pure additive change, no existing sections modified

**Auth cache invalidation:**
- Trigger: `queryClient.setQueryData(authQueryKeys.me, null)` in mutation success
- Affected: `Header.tsx` (observes `useAuthQuery()` result)
- Impact: **Expected behavior** - Header switches to logged-out state
- Consumers: All components using `useAuthQuery()` will see null (triggers sign-in prompts)

### High-Risk Modifications

| File | Risk | Mitigation |
|------|------|------------|
| `api.ts` | **Medium** - Breaking change if DELETE calls expect void elsewhere | Audit complete: Only 1 usage in `plannerApi.ts:89`, safe to modify (wrapper function already declares `Promise<void>` return type, TypeScript won't complain) |
| All others | **Low** - Isolated/new files | Standard testing and validation |

## Execution Order

**Phase 1: Foundation (API Contract Fix)**

1. **lib/api.ts**: Refactor `delete()` to support generic typed responses
   - Depends on: None
   - Enables: F1 (backend communication)
   - Pattern: Match existing `get<T>`, `put<T>`, `post<T>` signature
   - Change: Replace manual DELETE implementation with `return this.fetch<T>(endpoint, { method: 'DELETE' })`

**Phase 2: Data Layer (Types & Schemas)**

2. **types/UserSettingsTypes.ts**: Add `UserDeletionResponse` interface
   - Depends on: None
   - Enables: F1 (type safety)
   - Fields: `message: string, deletedAt: string, permanentDeleteAt: string, gracePeriodDays: number`
   - Note: Backend uses `permanentDeleteAt` (not `permanentDeleteScheduledAt`)

3. **schemas/UserSettingsSchemas.ts**: Add `UserDeletionResponseSchema` Zod schema
   - Depends on: Step 2 (types)
   - Enables: F1 (runtime validation)
   - Validation: All fields required, strings for ISO dates, number for gracePeriodDays

**Phase 3: Logic Layer (Mutation Hook)**

4. **hooks/useUserSettingsQuery.ts**: Add `useDeleteAccountMutation` hook
   - Depends on: Steps 1-3 (API client, types, schemas)
   - Enables: F1, F2, F3 (deletion logic, cache invalidation, error handling)
   - Pattern: Copy `useUpdateKeywordMutation` structure exactly
   - Mutation: `ApiClient.delete<UserDeletionResponse>('/api/user/me')`
   - Validation: `UserDeletionResponseSchema.safeParse(data)`
   - Success: `queryClient.setQueryData(authQueryKeys.me, null)` (triggers logout)
   - Error: `console.error()` logging

**Phase 4: Interface Layer (UI Components)**

5. **components/settings/AccountDeleteDialog.tsx**: Create confirmation dialog
   - Depends on: None (UI only)
   - Enables: F4, F5 (confirmation input, dismiss prevention)
   - Pattern: Copy `ConflictResolutionDialog.tsx` structure
   - Props: `open: boolean, onConfirm: () => void, onCancel: () => void, isPending: boolean`
   - State: `confirmationInput: string` (local state)
   - Validation: Delete button enabled only when `confirmationInput === "DELETE"`
   - Dismiss prevention: `showCloseButton={false}, onEscapeKeyDown={preventDismissal}, onInteractOutside={preventDismissal}`

6. **components/settings/AccountDeleteSection.tsx**: Create main section component
   - Depends on: Steps 4-5 (mutation hook, dialog)
   - Enables: F1-F6 (complete feature integration)
   - Pattern: Copy `UsernameSection.tsx` Suspense wrapper structure
   - Structure: Outer wrapper (Suspense) + Content (logic) + Skeleton (fallback)
   - Auth gating: `useAuthQuery()` null check → show sign-in prompt
   - Dialog state: `useState<boolean>(false)` for open/close
   - Mutation flow:
     - Call `deleteAccount.mutate()`
     - On success: Parse `permanentDeleteAt` → Format date → Show toast → Wait 2s → Redirect
     - On error: Show error toast, keep dialog open
   - Date formatting: `new Date(response.permanentDeleteAt).toLocaleDateString()`
   - Redirect: `window.location.href = '/'` (after auth cache clear + 2s delay)

**Phase 5: Integration (Page Assembly)**

7. **routes/SettingsPage.tsx**: Integrate AccountDeleteSection
   - Depends on: Step 6 (AccountDeleteSection component)
   - Enables: F6 (user-facing feature)
   - Changes: Add new `<section>` below existing UsernameSection
   - Styling: Add danger zone styling (red border: `border-destructive`, red bg: `bg-destructive/10`)
   - Section header: Add `<h2>` with "Danger Zone" heading above new section

**Phase 6: Tests (Verification)**

8. **Write unit tests for mutation hook**
   - Test: `useDeleteAccountMutation` calls correct endpoint with type parameter
   - Test: Response validation with Zod schema (valid data passes, invalid fails)
   - Test: Cache invalidation on success (`authQueryKeys.me` set to null)
   - Test: Error logging on failure

9. **Write component tests for dialog**
   - Test: Confirmation input updates state correctly
   - Test: Delete button disabled when input is empty/incorrect
   - Test: Delete button enabled when input is exactly "DELETE"
   - Test: onConfirm callback fires when delete button clicked
   - Test: onCancel callback fires when cancel button clicked
   - Test: Dismiss prevention handlers block ESC and outside-click

10. **Write integration tests for AccountDeleteSection**
    - Test: Unauthenticated state shows sign-in prompt (no delete button)
    - Test: Authenticated state shows delete button
    - Test: Clicking delete opens dialog
    - Test: Successful deletion shows toast with formatted date
    - Test: Successful deletion waits 2s then redirects
    - Test: Error during deletion shows error toast and keeps dialog open

## Test Steps (MANDATORY)

**Tests are explicit steps in Execution Order (Phase 6, Steps 8-10).**

Unit tests written after Step 4 (mutation hook complete).
Component tests written after Step 5-6 (dialog + section complete).
Integration tests written after Step 7 (page integration complete).

## Verification Checkpoints

**After Step 1 (API Client):**
- Verify F1-partial: TypeScript compilation succeeds, no errors in plannerApi.ts usage

**After Step 4 (Mutation Hook):**
- Verify F1: Mutation hook calls DELETE with type parameter, validates response

**After Step 6 (Main Component):**
- Verify F2: Auth gating works (sign-in prompt for unauthenticated)
- Verify F3: Dialog opens on button click
- Verify F4: Confirmation input validation works
- Verify F5: Dismiss prevention works (ESC/outside-click blocked)

**After Step 7 (Page Integration):**
- Verify F6: Complete user flow (delete → toast with date → 2s wait → logout → redirect)
- Verify E1: Error handling shows toast and keeps dialog open
- Verify I1: Header updates to logged-out state after cache invalidation

**After Steps 8-10 (All Tests):**
- Verify T1-T3: All unit tests pass
- Verify T4-T6: All component tests pass
- Verify T7-T10: All integration tests pass

## Risk Mitigation (from instructions.md Risk Assessment)

| Risk | Step Affected | Mitigation |
|------|---------------|------------|
| API Client breaking existing DELETE calls | Step 1 | Pre-verified: Only 1 usage in plannerApi.ts:89, safe (wrapper declares Promise<void>, ignores return) |
| Response field name mismatch | Step 2-3 | Backend verified: Field is `permanentDeleteAt` (not `permanentDeleteScheduledAt`), schema matches |
| Zod validation failure on malformed response | Step 4 | safeParse pattern catches errors, mutation throws with console.error log |
| Auth cache invalidation timing | Step 6 | Use queryClient.setQueryData synchronously before redirect, wait 2s for toast visibility |
| Network failure during delete | Step 6 | TanStack Query auto-retry (3 attempts), error toast shows on final failure, dialog stays open for manual retry |
| User clicks delete twice rapidly | Step 6 | Mutation isPending state disables button, prevents double-submission |

## Pre-Implementation Validation Gate (ALL pattern copy tasks)

**BEFORE Step 5 execution (AccountDeleteDialog), verify research completed:**

| Validation Category | Check | Blocker if Missing |
|---------------------|-------|-------------------|
| **Reference Completeness** | Read all 117 lines of ConflictResolutionDialog.tsx? | YES |
| **Contract Alignment** | Dialog props match pattern (open, callbacks, isPending)? | YES |
| **Dependency Resolution** | All Dialog UI components available (shadcn/ui)? | YES |
| **Structure Documentation** | Dismiss prevention pattern (lines 64-73) documented? | YES |
| **Difference Justification** | Confirmation input is additive, doesn't violate pattern? | NO (documented in research) |

**BEFORE Step 6 execution (AccountDeleteSection), verify research completed:**

| Validation Category | Check | Blocker if Missing |
|---------------------|-------|-------------------|
| **Reference Completeness** | Read all 204 lines of UsernameSection.tsx? | YES |
| **Contract Alignment** | Suspense wrapper + Content + Skeleton structure matches? | YES |
| **Dependency Resolution** | useAuthQuery, toast, Button, Skeleton available? | YES |
| **Structure Documentation** | Auth gating pattern (lines 110-122) documented? | YES |
| **Difference Justification** | Dialog state + mutation flow are additive features? | NO (justified in research) |

**BEFORE Step 4 execution (useDeleteAccountMutation), verify:**

| Validation Category | Check | Blocker if Missing |
|---------------------|-------|-------------------|
| **Reference Completeness** | Read all 103 lines of useUpdateKeywordMutation? | YES |
| **Contract Alignment** | Mutation structure matches (mutationFn, onSuccess, onError)? | YES |
| **Dependency Resolution** | ApiClient.delete<T>() available (Step 1 complete)? | YES (sequential dependency) |
| **Structure Documentation** | safeParse validation pattern (lines 88-92) documented? | YES |
| **Difference Justification** | No request body (DELETE has no payload)? | NO (HTTP DELETE has no body, justified) |

**Execution Rule**: Do NOT proceed if critical blockers unresolved.

## Dependency Verification Steps

**After Step 1 (API Client modified):**
- Test: Run TypeScript compiler on plannerApi.ts (only existing consumer)
- Verify: No type errors, Promise<void> return type still valid

**After Step 4 (useUserSettingsQuery.ts exports added):**
- Test: Import new hook in temporary file, verify no module resolution errors
- Verify: Existing UsernameSection.tsx still compiles (uses same file)

**After Step 6 (AccountDeleteSection created):**
- Test: Import in SettingsPage.tsx, verify Suspense boundary compiles
- Verify: useAuthQuery integration works (mock auth state in test)

**After Step 7 (SettingsPage.tsx modified):**
- Manual test: Navigate to /settings, verify both sections render
- Verify: UsernameSection unaffected by new section below it

## Rollback Strategy

**If Step 1 fails (API client breaks existing code):**
- Alternative: Create new method `deleteWithResponse<T>()` instead of modifying `delete()`
- Impact: Use `ApiClient.deleteWithResponse<UserDeletionResponse>()` in mutation hook
- Safe stopping point: Step 1 incomplete, no downstream changes made yet

**If Step 4 fails (mutation hook errors):**
- Rollback: Remove added hook from useUserSettingsQuery.ts
- Safe stopping point: Types/schemas exist but unused (no runtime impact)
- Resume: Debug mutation structure, refer to useUpdateKeywordMutation pattern

**If Step 6 fails (component integration errors):**
- Rollback: Remove AccountDeleteSection.tsx, keep dialog (reusable)
- Safe stopping point: Hook exists but unused, dialog isolated
- Resume: Debug Suspense boundary, auth gating, or mutation flow

**If Step 7 fails (page integration breaks layout):**
- Rollback: Remove new section from SettingsPage.tsx
- Safe stopping point: Components exist but not exposed to users
- Resume: Debug styling, section ordering, or Suspense boundary
