# Execution Plan: Fix Authentication Session Bugs

## Planning Gaps

None. Research mappings verified against source code.

## Execution Overview

Fix three interconnected bugs in sequential order due to dependencies:
1. Bug 1 (204) must be fixed first - enables logout to work
2. Bug 3 (refresh exclusion) is critical - enables proper session management
3. Bug 2 (error masking) and logout UX after foundation is stable

**Total files: 3** | **Complexity: Medium**

## Dependency Analysis

### Files Being Modified

| File | Impact | Depends On | Used By |
|------|--------|------------|---------|
| `lib/api.ts` | HIGH | None (foundation) | ALL hooks using ApiClient |
| `hooks/useAuthQuery.ts` | MEDIUM | api.ts | Header, SettingsPage, auth-gated UI |
| `components/Header.tsx` | HIGH | useAuthQuery | Root layout (all pages) |

### Ripple Effect Map

- api.ts 204 fix → ALL DELETE operations work (planner, comments, notifications)
- api.ts refresh fix → `/auth/me` 401 triggers refresh → session persists
- useAuthQuery error fix → 5xx shows toast instead of login button
- Header logout fix → toast + page refresh on success

### High-Risk Modifications

| Change | Risk | Mitigation |
|--------|------|------------|
| Refresh exclusion narrowing | HIGH | Exact match `/auth/refresh` and `/auth/logout` only |
| 204 handling | MEDIUM | Additive check, doesn't break existing behavior |

## Execution Order

### Phase 1: Foundation Fix (api.ts)

**P1-1. Fix 204 Response Handling** `api.ts:80`
- Check `response.status === 204` before JSON parse
- Return `undefined as T` for empty responses
- Enables: F1 (logout works)

**P1-2. Narrow Refresh Exclusion** `api.ts:53`
- FROM: `!endpoint.includes('/auth/')`
- TO: `!endpoint.endsWith('/auth/refresh') && !endpoint.endsWith('/auth/logout')`
- Enables: F3 (session persists on refresh)

### Phase 2: Error Handling (useAuthQuery.ts)

**P2-1. Add Error Type Distinction** `useAuthQuery.ts:28-31`
- Check error response status
- 401 → return null (guest)
- 5xx → toast.error() + return null
- Enables: E1 (5xx shows error feedback)

### Phase 3: User Experience (Header.tsx)

**P3-1. Add Logout Success Handler** `Header.tsx`
- Add toast.success() in logout onSuccess
- Call window.location.reload() after
- Enables: F2 (logout toast + refresh)

## Verification Checkpoints

| After | Verify |
|-------|--------|
| Phase 1 | DELETE operations work, /auth/me triggers refresh on 401 |
| Phase 2 | Backend down → error toast (not login button) |
| Phase 3 | Logout → toast → page refresh → login button |

## Risk Mitigation

| Risk | Step | Mitigation |
|------|------|------------|
| Infinite refresh loop | P1-2 | Exact string match, test immediately |
| Breaking DELETE ops | P1-1 | 204 check is additive |
| Toast spam | P2-1 | Check error type before toast |

## Pre-Commit Checklist

1. `yarn typecheck` passes
2. `yarn test` passes
3. Manual: Login → Logout → toast + refresh
4. Manual: Stop backend → refresh → error feedback
5. Manual: Login → wait 30s → refresh → still logged in

## Rollback Strategy

Each phase independently revertible:
- Phase 1: `git checkout HEAD -- frontend/src/lib/api.ts`
- Phase 2: `git checkout HEAD -- frontend/src/hooks/useAuthQuery.ts`
- Phase 3: `git checkout HEAD -- frontend/src/components/Header.tsx`
