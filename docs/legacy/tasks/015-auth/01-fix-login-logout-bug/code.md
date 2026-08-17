# Implementation Results: Fix Authentication Session Bugs

## What Was Done

- **P1-1**: Fixed 204 response handling in `api.ts` - returns `undefined` instead of parsing empty body
- **P1-2**: Changed 401 refresh logic to parse error body and trigger refresh only for `INVALID_TOKEN`
- **P2-1**: Added error type distinction in `useAuthQuery.ts` - 401 silent, 5xx shows toast
- **P3-1**: Added logout success handler with `toast.success()` + `window.location.reload()`
- **Bug 3 revision**: Original plan was incorrect - kept `/auth/` exclusion but parse error code instead
- Added comprehensive tests for 401 handling scenarios (33 tests total)

## Files Changed

- `frontend/src/lib/api.ts`
- `frontend/src/lib/api.test.ts` (new)
- `frontend/src/hooks/useAuthQuery.ts`
- `frontend/src/hooks/useAuthQuery.test.tsx` (renamed from .ts)
- `frontend/src/components/Header.tsx`
- `docs/learning/http-response-codes-and-handling.md`

## Verification Results

- TypeScript: **pass** (`yarn tsc --noEmit`)
- Tests: **pass** (33 tests - 19 api.ts + 14 useAuthQuery.tsx)
- Phase 1 (204 handling): **pass** - DELETE/logout operations work
- Phase 2 (error distinction): **pass** - 5xx shows toast, 401 returns null
- Phase 3 (logout UX): **pass** - toast + page refresh on logout

## Issues & Resolutions

- **Infinite refresh loop**: Original P1-2 plan caused loops when `/auth/me` triggered refresh for guests → Fixed by parsing error body (`INVALID_TOKEN` vs `UNAUTHORIZED`)
- **Toast spam on network error**: Using `status === 0` caught too many cases → Changed to `status !== null && status >= 500`
- **Test file extension**: JSX in `.ts` file caused parse error → Renamed to `.tsx`
- **Understanding before fixing**: Changed code without understanding WHY it existed → Added learning doc section

## Key Insight

Backend already distinguishes expired (`INVALID_TOKEN`) from guest (`UNAUTHORIZED`). Frontend just needed to parse the error body instead of only checking HTTP status.
