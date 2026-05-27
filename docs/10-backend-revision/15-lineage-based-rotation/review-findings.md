# Code Review Findings — Lineage Rotation

Adversarial review of `RefreshRotationService` (auth hot path, dark behind
`jwt.rotation.lineage-enabled=false`). Each finding validated against the actual code.

## Fixed autonomously (REJECT-grade, clear + safe)

### #1 — Lock-eviction race → replaced with fixed-size lock striping  ✅ FIXED
The earlier per-family dynamic locks (`ConcurrentHashMap<String,Object> familyLocks` +
`computeIfAbsent` + cleanup eviction via `keySet().retainAll`) had a real race: a fresh-login
family holds a lock but has no `rotationState` entry until after `generateRefreshToken` (RSA
signing). If `cleanupExpired` fired in that window (hourly, or inline at the 100K threshold),
it evicted the live lock; a sibling `rotate()` then created a *different* monitor for the same
family → lost mutual exclusion → split-brain successor minting, orphaned PENDING entries, and
eventual false-positive theft revocation forcing legitimate re-login.
**Fix:** 256 fixed lock stripes, `familyLockStripes[Math.floorMod(familyId.hashCode(), 256)]`.
Same familyId always maps to the same monitor (mutual exclusion preserved); no per-family lock
lifecycle, so no leak and no eviction race. Eviction logic deleted from `cleanupExpired`.

### #2 — `rotate()` admitted access tokens via legacy branch → guarded  ✅ FIXED
`legacy = jti==null || familyId==null` is also true for access tokens; `rotate()` had no
`isRefreshToken()` guard. Caller-gated today, but a public security method must self-guard.
**Fix:** reject non-refresh tokens as `Rejected(INVALID)` right after validation, before the
legacy branch, regardless of the legacy-admit flag. Test added (rejected even when legacy-admit on).

## Deferred — need a decision, NOT auto-fixed

### #3 — `MAX_ROTATION_STATE_SIZE` is not a hard cap  ✅ FIXED (2026-05-28)
`cleanupExpired` only removes *expired* entries, so the inline size-triggered cleanup scanned
the whole map while holding a stripe lock (serializing unrelated families).
**Fix applied (option a):** renamed to `CLEANUP_TRIGGER_THRESHOLD` with honest Javadoc (trigger,
not a hard cap; growth is TTL-bounded; hourly `@Scheduled` is the primary reclaimer), and moved
the size-triggered `cleanupExpired()` call OUT of the `synchronized` block to after the lock
releases in `rotate()`. Did NOT add an eviction-under-pressure policy (availability tradeoff
left for the Redis migration).

### #4 — Legacy family/jti synthesis collides for same user, same second
`iat` is seconds-precision; two legacy refresh tokens for the same user issued in the same
second synthesize the same family_id + jti and stomp each other (false theft).
Realistically rare (a user doesn't get multiple refresh tokens per second). The spec already
acknowledged collision risk, and adding entropy would defeat the determinism that makes legacy
retries enter the supersede branch. **Leaving as-is unless you want collision detection.**

### #5 — Legacy retries tagged `legacy_admitted`, not `retry_superseded`
Deliberate Phase 4 choice: the `legacy` flag short-circuits the `retry` tag. Operators watching
`retry_superseded` for Set-Cookie delivery failures won't see legacy-originated retries during
the 7-day legacy window. Observability-only; pick whichever tag semantics you prefer for ops.

## Runtime toggle (2026-05-28)
The `jwt.rotation.lineage-enabled` flag is now runtime-mutable via the internal API instead of a
startup-bound `@Value` (no staging env exists, so the toggle is the kill switch). Backed by the
`LineageRotationFlag` singleton; flips are instantly visible to the filter + facade.

```
# enable (cutover)
curl -X POST 'http://<host>/api/internal/feature-flags/lineage-rotation?enabled=true' \
     -H 'X-Internal-Api-Key: <key>'
# disable (instant rollback — no restart)
curl -X POST 'http://<host>/api/internal/feature-flags/lineage-rotation?enabled=false' \
     -H 'X-Internal-Api-Key: <key>'
```
`application.properties` still seeds the startup default (`false`). Toggling off mid-flight is safe:
stale `rotationState` entries are ignored by the flag-off path (same semantics as a server restart).

## Status
- #1, #2 fixed and verified earlier.
- #3 FIXED (cleanup out of lock + honest rename).
- #5 (logout-everywhere) shipped; lineage flag made runtime-togglable per ops need.
- Full backend suite 847 / 0 fail.
- Remaining deferred: #4 (legacy synth same-second collision — rare, spec-acknowledged) and the
  metric-tag note (legacy retries tagged `legacy_admitted`). Both low-priority, no code pending.
