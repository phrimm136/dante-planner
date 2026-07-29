# Mechanics: JWT Token-Type Enforcement

Companion to `requirements.md`. Transcribed from the 2026-07-17 design session.

Binding vs. reference: the parser contract, the call-site routing table, the exception mapping, and
the migration-safety numbers below are **binding** — implementation must not diverge without
re-debate. The failure-mode walkthrough that produced them (the refresh-as-access replay trace, the
rolling-deploy analysis) is **reference**; it lives in the session, not here.

## 1. Three-parser contract

jjwt 0.13.0 (`io.jsonwebtoken:jjwt-api:0.13.0`) supports `.require(claimName, value)` on the parser
builder, which asserts the claim at parse time and throws before returning `Claims`. Replace the
single `jwtParser` field (`JwtTokenService.java:56-59`) with three parsers, all sharing
`verifyWith(publicKey)` and the injected `clock`:

| Parser | Extra builder constraint | Purpose |
|--------|--------------------------|---------|
| `accessParser` | `.require(CLAIM_TYPE, TokenClaims.TYPE_ACCESS)` | validates tokens presented as access tokens |
| `refreshParser` | `.require(CLAIM_TYPE, TokenClaims.TYPE_REFRESH)` | validates tokens presented as refresh tokens |
| `neutralParser` | none (current behavior) | type-reading helpers that must accept either type |

The single signing key is unchanged — type is a claim constraint, not a key split (Decision 2).

The `TokenValidator` interface gains type-scoped entry points so callers select a parser by method,
not by passing a parser object. Suggested shape (names are plan-mode's to finalize; the **routing
below is binding**):
- `validateAccessToken(String)` → `accessParser`
- `validateRefreshToken(String)` → `refreshParser`
- existing `validateToken(String)` → `neutralParser` (kept for `getTokenType` and any genuinely
  type-agnostic helper)

## 2. Call-site routing (binding)

Every current `validateToken` call site routes to the parser for the token it actually handles.
A miswired row is a mass-logout: mis-routing any refresh row to the access parser breaks refresh,
and refresh is what sustains the 7-day session (INV4).

| Call site | Token handled | Route to |
|-----------|---------------|----------|
| `JwtAuthenticationFilter.java:126` | access (primary auth) | `accessParser` |
| `JwtAuthenticationFilter.java:237` | refresh (auto-refresh, flag-off) | `refreshParser` |
| `AuthenticationFacade.java:159` | refresh (refreshTokens) | `refreshParser` |
| `AuthenticationFacade.java:248` | access (logout) | `accessParser` |
| `AuthenticationFacade.java:259` | refresh (logout) | `refreshParser` |
| `AuthenticationFacade.java:291` | access (logout-everywhere) | `accessParser` |
| `RefreshRotationService.java:188` | presented refresh | `refreshParser` |
| `RefreshRotationService.java:219` | successor refresh (self-minted) | `refreshParser` |
| `RefreshRotationService.java:241` | stored refresh (from Redis) | `refreshParser` |
| `JwtTokenService.java:174` (`getTokenType`) | either — READS the type | `neutralParser` (never type-bound) |
| `JwtTokenService.java:149` (`getUserIdFromToken`) | either | `neutralParser` (no prod callers) |
| `JwtTokenService.java:156` (`isTokenExpired`) | either | `neutralParser` (no prod callers) |

Lineage path note: `RefreshRotationService.rotate` (`:188-196`) keeps its existing
`!claims.isRefreshToken()` guard — with the refresh parser in front it becomes belt-and-suspenders,
not dead weight, and preserves the fix recorded in the `rotate()`-legacy-branch gotcha.

## 3. Exception → Reason mapping (binding)

A type-bound parser throws jjwt `IncorrectClaimException` (value mismatch) or `MissingClaimException`
(claim absent) on a wrong-type token. Both extend `ClaimJwtException → JwtException`, so the current
`parseToken` catch chain (`:204-214`) would swallow them into the generic `JwtException → MALFORMED`
branch. Add a catch for these **before** the generic `JwtException` catch, mapping to
`InvalidTokenException.Reason.INVALID_TYPE`:

```
} catch (IncorrectClaimException | MissingClaimException e) {   // NEW, before JwtException
    throw new InvalidTokenException(InvalidTokenException.Reason.INVALID_TYPE, e);
} catch (JwtException e) {
    throw new InvalidTokenException(InvalidTokenException.Reason.MALFORMED, e);
}
```

This makes `Reason.INVALID_TYPE` reachable for the first time; `mapReasonToErrorCode`
(`JwtAuthenticationFilter.java:207`) already maps it to `TOKEN_INVALID`, and the delivered logging
enrichment (`:189`) renders it `TOKEN_INVALID (INVALID_TYPE)`.

## 4. Migration-safety proof (binding — this is why no user is logged out)

The change rejects wrong-type tokens. It is safe **only** because no legitimate live token is
wrong-type, proven by two facts whose product is the empty set:

| Fact | Value | Source |
|------|-------|--------|
| Type claim stamped on 100% of minted tokens | since **2026-01-07** | commit `1e2a28d2`; `generateAccessToken` sets `CLAIM_TYPE=access` (`:74`), `generateRefreshToken` sets `CLAIM_TYPE=refresh` (`:95`) — unconditional |
| Max refresh-token lifetime | **7 days** | `jwt.refresh-token-expiry=604800000` |
| Session date | 2026-07-17 | — |

Claim age (≈6 months) ≫ token lifetime (7 days) ⇒ **zero untyped tokens are in circulation**.
Therefore every live token is correctly typed and passes its parser. Corollary — **rolling-deploy
safe**: during a mixed old/new pod window a correctly-typed token passes both the old single parser
and the new type-bound parser, so no in-flight session breaks; the only tokens the new parsers
reject are the cross-type ones that should never have worked.

The `RefreshRotationService` `admitLegacy` / `LEGACY_*` machinery is a *different* backward-compat
axis (null `jti`/`familyId`, pre-lineage), gated by `jwt.rotation.legacy-admit-enabled=false` in
prod; it is unrelated to the `type` claim and needs no change here.
