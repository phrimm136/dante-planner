# Task: JWT Token-Type Enforcement + Audit-Log Disambiguation

Archives the design debate of the 2026-07-17 session that began with a `TOKEN_INVALID` WARN-spam
investigation on `GET /api/planner/{plannerId}/comments` and converged on a token-type-confusion
hardening. Supersedes that conversation as the build contract.

Implementation-grade mechanics (the three-parser contract, the full call-site routing table, the
exception-to-reason mapping, and the migration-safety proof) live in `mechanics.md` — read it
before building the parser split.

N/A declarations:
- `docs/spec.md` "Data-Driven Features" — N/A. This task touches the auth/security layer, not raw
  game-data files; there is no data catalog, normalization layer, or rendering mode.
- Metered-external-sink budget invariant — N/A. The change is internal auth; it ships no data to a
  per-series/per-GB/per-request billed service.

## Decisions

- Decision 1 (taste): Enforce token type **structurally at the parser** (`require(type=...)` bound
  into the JWT parser), not with a per-consumer `isAccessToken()` guard — because an invariant
  enforced by construction cannot be forgotten by the next consumer, whereas a per-call-site check
  has already been forgotten once (this exact bug: two of three refresh callers guarded, the access
  path did not) and would have to be re-added on every future validation. Generalizable principle:
  push a security invariant into the object that cannot be bypassed rather than relying on caller
  discipline.
- Decision 2 (taste): Right-size the isolation to the threat — **one signing key with type-bound
  parsers**, not separate access/refresh signing keys — because the threat is type-confusion
  between tokens signed by our own key, which a claim requirement fully defeats; separate keys buy
  key-leakage blast-radius isolation the single-service threat model does not need, at the cost of
  doubled cross-region key rotation through the Secrets Manager → ESO pipeline. Generalizable
  principle: match a hardening's cost to the actual threat model, not to the theoretical maximum.
- Decision 3 (default): Keep a **neutral (type-agnostic) parser** for helpers that READ the type
  claim (`getTokenType`) — a method whose job is to discover the type cannot run through a parser
  that presupposes it (evidence: `getTokenType` at `JwtTokenService.java:174`).
- Decision 4: Map the type-mismatch parse failure (jjwt `IncorrectClaimException` /
  `MissingClaimException`) to `InvalidTokenException.Reason.INVALID_TYPE`, caught **before** the
  generic `JwtException → MALFORMED` catch — so the previously-dead `INVALID_TYPE` enum becomes
  reachable and wrong-type tokens are logged distinctly from corrupted ones (evidence:
  `InvalidTokenException.Reason.INVALID_TYPE` exists but is never thrown by `validateToken`, while
  `mapReasonToErrorCode` already handles it at `JwtAuthenticationFilter.java:207` — a stranded
  branch that documents the abandoned intent).
- Decision 5 (default): Disambiguate the audit log by appending the `InvalidTokenException.Reason`
  in parentheses to the existing event code, not by adding a new field or event — because a
  repo-wide search for `"Security event"` found zero parsers, so a backward-compatible label
  enrichment preserves any future prefix grep (evidence: `JwtAuthenticationFilter.java:366-372`).
  **Already delivered** to the working tree at `JwtAuthenticationFilter.java:189`.

## Description

Two outcomes from one investigation:

1. **Audit-log disambiguation (delivered).** `TOKEN_INVALID` collapses four distinct
   `InvalidTokenException.Reason` values (`MALFORMED`, `INVALID_SIGNATURE`, `MISSING_CLAIMS`,
   `INVALID_TYPE`) into one opaque log label, so an operator cannot tell a corrupted cookie from a
   wrong-key token. The security event must carry the specific reason.

2. **Token-type enforcement (to build).** `JwtAuthenticationFilter` authenticates any
   validly-signed, unexpired JWT without checking its `type` claim, so a **refresh** JWT placed in
   the `accessToken` cookie authenticates the request as a `NORMAL` user — bypassing the lineage
   theft-detection that only the rotation path consults. Enforce, structurally at the parser, that:
   the access-validation path accepts only `type=access`, the refresh/rotation path accepts only
   `type=refresh`, and type-reading helpers stay neutral. A wrong-type token is rejected as
   `INVALID_TYPE` and the request proceeds as guest. No legitimate user is logged out.

## Scope

Read for context:
- `backend/src/main/java/org/danteplanner/backend/auth/token/JwtTokenService.java` — parser build (`:56-59`), `validateToken`/`parseToken` (`:108`, `:198-216`), generators (`:65`, `:80`)
- `backend/src/main/java/org/danteplanner/backend/auth/token/TokenValidator.java` — the validator interface
- `backend/src/main/java/org/danteplanner/backend/auth/token/TokenClaims.java` — `isAccessToken`/`isRefreshToken` (`:70-77`), type constants (`:60`)
- `backend/src/main/java/org/danteplanner/backend/auth/exception/InvalidTokenException.java` — `Reason` enum
- `backend/src/main/java/org/danteplanner/backend/auth/facade/AuthenticationFacade.java` — validate call sites (`:159`, `:248`, `:259`, `:291`)
- `backend/src/main/java/org/danteplanner/backend/auth/token/RefreshRotationService.java` — validate call sites (`:188`, `:219`, `:241`) and the existing `isRefreshToken` guard (`:196`)

Create or modify:
- `backend/src/main/java/org/danteplanner/backend/auth/token/JwtTokenService.java` — add access/refresh/neutral parsers + type-aware validate paths + `INVALID_TYPE` mapping
- `backend/src/main/java/org/danteplanner/backend/auth/token/TokenValidator.java` — extend the interface with the type-scoped validation methods (per `mechanics.md`)
- `backend/src/main/java/org/danteplanner/backend/shared/security/JwtAuthenticationFilter.java` — route the access path and the auto-refresh path to their typed validators (logging enrichment already applied at `:189`)
- Tests: `JwtTokenServiceTest`, `JwtAuthenticationFilterTest`, `JwtAuthenticationFilterLineageTest`, `RefreshRotationServiceTest` (all under `backend/src/test/java/org/danteplanner/backend/...`)

## Invariants

- INV1: A JWT whose `type` claim ≠ `access` MUST NOT authenticate a request — no `SecurityContext`
  authentication is set, and the request proceeds as guest. — verify: `JwtAuthenticationFilterTest`
  (refresh JWT in access cookie → no authentication, served as guest)
- INV2: A JWT whose `type` claim ≠ `refresh` MUST NOT drive token rotation — no successor minted,
  no family touched. — verify: `RefreshRotationServiceTest` (access JWT presented → rejected, no
  rotation) + `JwtAuthenticationFilterTest` (auto-refresh path)
- INV3: Type enforcement is parser-structural — the access/refresh validators reject the opposite
  type with `Reason.INVALID_TYPE`; the neutral validator (used by `getTokenType`) accepts both. —
  verify: `JwtTokenServiceTest`
- INV4 (migration safety): No token minted by the current generators is rejected by the new
  parsers — a `generateAccessToken` output validates on the access path, a `generateRefreshToken`
  output validates on the refresh path, and the rotation service's own successor/stored refresh
  JWTs (`RefreshRotationService.java:219`, `:241`) continue to validate. — verify: round-trip tests
  in `JwtTokenServiceTest` + `RefreshRotationServiceTest`
- INV5: A wrong-type token surfaces in the audit log as `TOKEN_INVALID (INVALID_TYPE)` — the reason
  is not masked. — verify: `JwtAuthenticationFilterTest` asserting the security-event reason
- INV6 (delivered): A `TOKEN_INVALID` WARN carries its specific `Reason`
  (`MALFORMED`/`INVALID_SIGNATURE`/`INVALID_TYPE`) in the log line. — verify: `JwtAuthenticationFilterTest`

## Behavior Inventory

Seam 1 — `JwtAuthenticationFilter.doFilterInternal` access-token path (read as-is from the current implementation):

| # | Seam | Observable behavior (as-is) | Verdict |
|---|------|-----------------------------|---------|
| B1 | filter `:126-161` | Valid access token → authenticate with `ROLE_<claim role>`, default `NORMAL` | preserved |
| B2 | filter `:167-185` | Expired access token → silent auto-refresh attempt, no WARN | preserved |
| B3 | filter `:186-191` | Malformed / bad-signature access token → WARN + clear context + proceed as guest | preserved (now logs the reason — INV6) |
| B4 | filter `:126-161` | **Refresh JWT in access cookie → authenticates as `NORMAL` user** | dropped — see Decision 1 (rejected as `INVALID_TYPE`) |
| B5 | filter `:106-122` | Null access cookie → auto-refresh if refresh cookie present, else guest | preserved |
| B6 | filter `:163-166` | Revoked/blacklisted token → `TOKEN_REVOKED`, clear context, proceed as guest | preserved |
| B7 | filter `:142-146` | Sentinel user (id=0) token → blocked, proceed as guest | preserved |

Seam 2 — `JwtTokenService.validateToken` / `parseToken` shared parser:

| # | Seam | Observable behavior (as-is) | Verdict |
|---|------|-----------------------------|---------|
| B8 | `parseToken :198-216` | Any validly-signed unexpired JWT → returns claims regardless of `type` | dropped for the typed call sites (access/refresh validators reject wrong type); preserved for the neutral helper `getTokenType` |
| B9 | `parseToken :204-214` | Expired→`EXPIRED`, malformed→`MALFORMED`, bad sig→`INVALID_SIGNATURE`, missing subject→`MISSING_CLAIMS` | preserved |

Seam 3 — `RefreshRotationService` internal validations (migration-critical):

| # | Seam | Observable behavior (as-is) | Verdict |
|---|------|-----------------------------|---------|
| B10 | rotate `:219`, `:241` | Successor & stored refresh JWTs (self-minted, `type=refresh`) validate successfully | preserved — must not break (INV4) |
| B11 | rotate `:188-196` | Presented refresh token validated, then `isRefreshToken()` guard rejects access tokens before the legacy branch | preserved (guard becomes belt-and-suspenders behind the refresh parser) |

## Done When

- [ ] Refresh JWT in the `accessToken` cookie → request served as guest, no authentication, WARN logged as `TOKEN_INVALID (INVALID_TYPE)` (local-tdd)
- [ ] Access JWT presented to the refresh / rotation path → rejected, no rotation, token family untouched (local-tdd)
- [ ] `accessParser`/`refreshParser` reject the opposite type with `Reason.INVALID_TYPE`; the neutral parser accepts both types (local-tdd)
- [ ] Round-trip: `generateAccessToken`→access validation and `generateRefreshToken`→refresh validation both succeed, and the rotation service's successor/stored validation still passes — migration safety (local-tdd)
- [ ] `TOKEN_INVALID` audit log includes the specific `Reason` — already delivered (local-tdd)
- [ ] All existing tests pass — especially `JwtAuthenticationFilterTest`, `JwtAuthenticationFilterLineageTest`, `RefreshRotationServiceTest`, `SecurityIntegrationTest` (local-tdd)

## Deferred

- Self-healing cookie clear on `MALFORMED` / `INVALID_SIGNATURE` / `INVALID_TYPE` — deferred to a
  follow-up task; until then: a broken or wrong-type `accessToken` cookie keeps emitting one WARN
  on **every** request until the cookie expires (up to 7 days), because the filter clears the
  `SecurityContext` but never deletes the cookie and the request still returns 200 as guest. This
  is the original WARN-spam amplifier — type enforcement closes the security hole but does not
  reduce the log volume from an already-broken client.

## Test Plan

> Test runner resolved from `backend/CLAUDE.md` in Step 3.

### Test Runner
- Framework: JUnit 5 (Spring Boot Test), Mockito for unit isolation
- Run command (unit scope, no containers):
  `./gradlew -p backend test -PexcludeTags=containerized --tests "org.danteplanner.backend.auth.token.*" --tests "org.danteplanner.backend.shared.security.*"`

### Tests to Write
- [ ] Refresh JWT rejected on the access path, guest served, `INVALID_TYPE` logged: `backend/src/test/java/org/danteplanner/backend/shared/security/JwtAuthenticationFilterTest.java`
- [ ] Access JWT rejected on the auto-refresh path (flag-on and flag-off): `JwtAuthenticationFilterTest.java` + `JwtAuthenticationFilterLineageTest.java`
- [ ] Access JWT rejected by `RefreshRotationService.rotate`, no rotation/family mutation: `backend/src/test/java/org/danteplanner/backend/auth/token/RefreshRotationServiceTest.java`
- [ ] Access/refresh/neutral parser type behavior + `IncorrectClaimException`→`INVALID_TYPE` mapping: `backend/src/test/java/org/danteplanner/backend/auth/token/JwtTokenServiceTest.java`
- [ ] Migration round-trip: generate→validate per type, and successor/stored refresh validation: `JwtTokenServiceTest.java` + `RefreshRotationServiceTest.java`
- [ ] Every invariant in the Invariants section has its test realized here — no invariant ships untested
- [ ] Every preserved Behavior Inventory row has its characterization test assigned — B1/B2/B3/B5/B6/B7 pinned green before the rewrite, B10/B11 pinned as the migration-safety net
