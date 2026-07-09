# Research: Fix Authentication Session Bugs

## Spec-to-Code Mapping

| Requirement | File:Line | Modification |
|-------------|-----------|--------------|
| Bug 1: 204 Response | `lib/api.ts:80` | Check status before JSON parse |
| Bug 2: Error Distinction | `useAuthQuery.ts:28-31` | Separate 401 (guest) from 5xx (error) |
| Bug 3: Refresh Exclusion | `lib/api.ts:53` | Narrow from `/auth/*` to only `/auth/refresh`, `/auth/logout` |
| Logout Toast | `Header.tsx:303` | Add toast.success() + window.location.reload() |

## Pattern Enforcement

| File to Modify | MUST Read First | Pattern to Follow |
|----------------|-----------------|-------------------|
| `lib/api.ts` | Current fetch logic (lines 36-81) | Check `status === 204` before `.json()` |
| `lib/api.ts` | Current exclusion (line 53) | Explicit endpoint list instead of prefix match |
| `useAuthQuery.ts` | Current catch (lines 28-31) | Check error response status, re-throw non-401 |
| `Header.tsx` | Current logout button (line 303) | Add onSuccess callback with toast + reload |

## Existing Utilities (Reuse)

| Category | Location | Function |
|----------|----------|----------|
| Toast | `sonner` (imported in Header, main.tsx) | `toast.success()`, `toast.error()` |
| Cache Update | `useAuthQuery.ts:107, 140` | `queryClient.setQueryData(authQueryKeys.me, value)` |
| Error Class | `api.ts:22-31` | `ConflictError` pattern for typed errors |

## Gap Analysis

**Missing:**
- 204 handling in ApiClient.fetch()
- Error type distinction in useAuthQuery catch block
- Logout toast + page refresh in Header

**Reusable:**
- Sonner toast (already integrated)
- Query client cache pattern (existing examples)
- Mutation onSuccess/onError pattern

## Implementation Order

1. **api.ts:80** - Fix 204 response parsing (isolated, low risk)
2. **api.ts:53** - Narrow 401 refresh exclusion (critical fix)
3. **useAuthQuery.ts** - Add error type distinction (enables toast)
4. **Header.tsx** - Add logout toast + refresh (completes UX)

## Testing Checklist

### Manual UI Tests
- Login → Logout → Verify toast appears → Page refreshes → Login button visible
- Stop backend → Refresh → Verify error toast (not login button)
- Login → Wait 30s → Refresh → Verify still logged in (refresh worked)

### Functional Verification
- [ ] 204 returns empty object without JSON parse error
- [ ] Logout mutation completes, cache cleared
- [ ] 401 on `/auth/me` triggers refresh attempt
- [ ] 401 on `/auth/refresh` does NOT retry (prevents infinite loop)
- [ ] 5xx errors show toast, not masked as guest

### Edge Cases
- [ ] Rapid logout clicks: Only one request fires
- [ ] Refresh failure: Redirects to home (existing behavior)
- [ ] Network timeout: Shows error toast

## Technical Constraints

- Must use Sonner (already integrated)
- Must use useSuspenseQuery (not useQuery)
- Must propagate errors to boundary (not swallow)
- No BroadcastChannel (design decision)
- Token blacklisting handled by backend
