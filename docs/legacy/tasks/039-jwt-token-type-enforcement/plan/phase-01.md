# Phase 01: Type-scoped JWT parsers + INVALID_TYPE mapping

- Kind: local-tdd — pure service/unit code; no containers. Run:
  `./gradlew -p backend test -PexcludeTags=containerized --tests "org.danteplanner.backend.auth.token.JwtTokenServiceTest"`
- Files:
  - `backend/src/main/java/org/danteplanner/backend/auth/token/JwtTokenService.java` — replace the single `jwtParser` field (`:43`, built `:56-59`) with `accessParser` / `refreshParser` / `neutralParser`; add `validateAccessToken` / `validateRefreshToken`; route existing `validateToken` to `neutralParser`; add the `INVALID_TYPE` catch to `parseToken` (`:198-216`).
  - `backend/src/main/java/org/danteplanner/backend/auth/token/TokenValidator.java` — add `validateAccessToken(String)` and `validateRefreshToken(String)` to the interface (no default bodies — `JwtTokenService` is the sole implementer, confirmed by grep).
- Tests:
  - `backend/src/test/java/org/danteplanner/backend/auth/token/JwtTokenServiceTest.java` — real invocations on the concrete service (no `TokenValidator` mock here); add the type-behavior and mapping cases below.
- External contract: `validateAccessToken(token)` accepts a `type=access` JWT and rejects a `type=refresh` JWT with `InvalidTokenException` `Reason.INVALID_TYPE`; `validateRefreshToken(token)` is the mirror; `validateToken(token)` (neutral) accepts both types unchanged. A wrong-type token maps to `INVALID_TYPE`, not `MALFORMED`. A `generateAccessToken` output validates on `validateAccessToken`, a `generateRefreshToken` output on `validateRefreshToken`.
- Behavior inventory (Seam 2 — `JwtTokenService` parser):
  - B8 `parseToken :198-216` — "Any validly-signed unexpired JWT → returns claims regardless of `type`": dropped for the typed methods (they now reject wrong type), PRESERVED for the neutral `validateToken` used by `getTokenType`. Characterization: `JwtTokenServiceTest` — neutral parser still returns claims for both types.
  - B9 `parseToken :204-214` — "Expired→`EXPIRED`, malformed→`MALFORMED`, bad sig→`INVALID_SIGNATURE`, missing subject→`MISSING_CLAIMS`": preserved. Characterization: `JwtTokenServiceTest` — the pre-existing catch order and reasons are unchanged; the new `INVALID_TYPE` catch is inserted BEFORE the generic `JwtException` branch and must not perturb these.
- Mechanics sections:
  - `mechanics.md §1` (three-parser contract) — binding: all three parsers share `verifyWith(publicKey)` + injected `clock`; only the `.require(CLAIM_TYPE, …)` constraint differs; single signing key unchanged (Decision 2).
  - `mechanics.md §3` (exception → Reason mapping) — binding: catch `IncorrectClaimException | MissingClaimException` and map to `INVALID_TYPE` BEFORE the generic `JwtException → MALFORMED` catch, or the wrong-type failure is swallowed as `MALFORMED`.
  - `mechanics.md §4` (migration-safety proof) — the round-trip cases prove no currently-minted token is rejected (INV4 parser half).
- Considerations:
  - requirements Decision 1 — enforce type structurally at the parser, not per-consumer; the `.require` binding is the invariant that cannot be bypassed.
  - requirements Decision 3 — the neutral parser must remain for `getTokenType` (`JwtTokenService.java:174`), whose job is to READ the type and so cannot presuppose it; do not delete or type-bind it.
  - requirements Decision 4 — `Reason.INVALID_TYPE` exists but was never thrown; this phase first makes it reachable. Do not add a new enum value.
  - requirements INV3 — access/refresh validators reject the opposite type with `INVALID_TYPE`; neutral accepts both (the direct subject of this phase's tests).
  - requirements INV4 — `generateAccessToken`→access and `generateRefreshToken`→refresh must both validate; use a fixed `Clock` per `backend/src/test/CLAUDE.md` (never 1 ms expiries).
  - `backend/src/main/java/org/danteplanner/backend/auth/token/CLAUDE.md` — `JwtTokenService` stays RS256-only; no symmetric path introduced by the parser split.
- Depends on: none
- Verify: `./gradlew -p backend test -PexcludeTags=containerized --tests "org.danteplanner.backend.auth.token.JwtTokenServiceTest"` green; the interface compiles with `JwtTokenService` as the only implementer.
