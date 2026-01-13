# Execution Status: Fix Authentication Session Bugs

## Execution Progress

| Field | Value |
|-------|-------|
| Last Updated | 2026-01-13 |
| Current Step | 2/4 |
| Current Phase | Phase 2 & 3 (parallel) |

### Milestones

- [x] M1: Phase 1 Complete (api.ts foundation)
- [ ] M2: Phase 2 Complete (error handling)
- [ ] M3: Phase 3 Complete (logout UX)
- [ ] M4: Manual Verification Passed
- [ ] M5: TypeCheck + Tests Pass

### Step Log

| Step | File | Status |
|------|------|--------|
| P1-1 | api.ts:86 - 204 handling | ✅ done |
| P1-2 | api.ts:54-57 - refresh exclusion | ✅ done |
| P2-1 | useAuthQuery.ts - error distinction | ⏳ pending |
| P3-1 | Header.tsx - logout toast + refresh | ⏳ pending |

## Feature Status

### Core Features
- [ ] **F1**: Logout completes without JSON parse error
- [ ] **F2**: Logout shows toast notification + page refresh
- [ ] **F3**: Page refresh with valid refresh token restores session

### Edge Cases
- [ ] **E1**: 5xx errors show toast (not masked as guest)
- [ ] **E2**: Network failures show appropriate feedback
- [ ] **E3**: Rapid logout clicks → single request

## Testing Checklist

### Manual Verification
- [ ] Login with Google OAuth
- [ ] Logout → toast appears → page refreshes
- [ ] Stop backend → refresh → error feedback visible
- [ ] Login → wait 30s → refresh → still logged in
- [ ] Access /planner/md/new logged out → error boundary

### Automated
- [ ] `yarn typecheck` passes
- [ ] `yarn test` passes
- [ ] No console errors in browser

## Summary

| Metric | Value |
|--------|-------|
| Steps | 0/4 |
| Features | 0/3 |
| Edge Cases | 0/3 |
| Overall | 0% |
