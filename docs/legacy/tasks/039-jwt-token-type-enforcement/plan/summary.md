# Execution Plan

## Phase Summary

Token-type-confusion hardening, sliced into one foundation phase plus two parallel routing
phases. The threat: `JwtAuthenticationFilter` authenticates any validly-signed unexpired JWT
without checking its `type` claim, so a refresh JWT in the `accessToken` cookie logs in as a
`NORMAL` user (Behavior Inventory B4). The fix is structural (Decision 1): bind the type
constraint into the JWT parser so no consumer can forget it.

Strategy:
- **Phase 01 (parser-split)** builds the primitive: three type-scoped parsers in
  `JwtTokenService` and two new `TokenValidator` entry points, plus the
  `IncorrectClaimException`/`MissingClaimException` → `INVALID_TYPE` mapping that first makes the
  stranded enum reachable. Nothing routes to the new methods yet; this phase only proves the
  parsers accept/reject by type and that all currently-minted tokens still validate (INV4).
- **Phase 02 (access-path enforcement)** re-routes the filter's access path (`:126` →
  `validateAccessToken`) and its flag-off auto-refresh (`:237` → `validateRefreshToken`), and
  pins the audit-log reason enrichment. This is the phase that closes B4.
- **Phase 03 (refresh-path routing)** re-routes the migration-critical self-minted/server-side
  call sites (`RefreshRotationService` `:188`/`:219`/`:241`, `AuthenticationFacade`
  `:159`/`:248`/`:259`/`:291`) to their typed parsers. This is where INV2's rotation half and
  INV4's successor/stored half actually live.

02 and 03 both depend only on 01 and are mutually independent (disjoint files) — parallelizable.

### Cross-cutting considerations (system-wide)

- **Routing exactness = no mass logout (mechanics §2, binding).** Every one of the nine external
  `.validateToken(` call sites must route to the parser for the token it actually handles. A
  refresh row mis-routed to `accessParser` breaks refresh, and refresh sustains the 7-day session
  (INV4). The §2 table was confirmed exhaustive against the tree (grep of `.validateToken(` on
  `backend/src/main` returns exactly those nine rows; `JwtTokenService` is the sole
  `implements TokenValidator`).
- **Migration safety (mechanics §4, binding).** The change is safe only because the `type` claim
  has been stamped on 100% of minted tokens since 2026-01-07 (commit `1e2a28d2`), ≈6 months >
  the 7-day max refresh lifetime — so zero untyped tokens are in circulation and the deploy is
  rolling-safe. Every phase that re-routes a call site is held to "no correctly-typed live token
  is rejected."
- **Stale-stub blast radius.** Re-routing a call site to a typed method breaks every unit test
  that `@Mock`s `TokenValidator` and stubs `.validateToken(...)` on that path (Mockito returns
  null for the unstubbed typed method). Updating those stubs is in-scope implementation for the
  owning phase, not optional cleanup — partitioned per phase.
- **Done When #6 gate (all existing tests pass, esp. `SecurityIntegrationTest`).** The Test Plan's
  scoped run command (`auth.token.*` + `shared.security.*`) does NOT cover the facade tests
  (`facade.*`) or `AuthControllerLogoutAllTest` (`controller.*`) that phase 03's routing touches,
  nor `SecurityIntegrationTest` (`security.*`). The authoritative regression gate for the whole
  task is:
  `./gradlew -p backend test -PexcludeTags=containerized --tests "org.danteplanner.backend.auth.token.*" --tests "org.danteplanner.backend.shared.security.*" --tests "org.danteplanner.backend.facade.*" --tests "org.danteplanner.backend.controller.*" --tests "org.danteplanner.backend.security.SecurityIntegrationTest"`

## Phase Index

| id | Slug | Phase | Kind (why) | External contract (one line) | Depends on | Test Plan items |
|----|------|-------|------------|------------------------------|------------|-----------------|
| 01 | parser-split | Type-scoped JWT parsers + INVALID_TYPE mapping | local-tdd (pure service/unit code, no containers) | `validateAccessToken`/`validateRefreshToken` reject the opposite type as `Reason.INVALID_TYPE`; `validateToken` (neutral) accepts both; every currently-minted token still validates | none | TW4 (parser type behavior + `IncorrectClaimException`→`INVALID_TYPE`); TW5a (generate→validate round-trip per type); TW7a (B8/B9 characterization); INV3, INV4 (parser half) |
| 02 | access-path-enforcement | Filter access + flag-off refresh routing, audit-log reason pin | local-tdd (filter unit tests, no containers) | Refresh JWT in the `accessToken` cookie → served as guest, no authentication, WARN `TOKEN_INVALID (INVALID_TYPE)`; access JWT on the auto-refresh path → no refresh | 01 | TW1 (refresh-in-access → guest + `INVALID_TYPE` log); TW2 (access on auto-refresh, flag-on + flag-off); TW7b (B1/B2/B3/B5/B6/B7 characterization); INV1, INV2 (filter half), INV5, INV6 |
| 03 | refresh-path-routing | Rotation + facade + logout-all routing to typed parsers | local-tdd (rotation/facade unit tests, no containers) | Access JWT presented to `RefreshRotationService.rotate` → rejected, no successor minted, no family touched; each `AuthenticationFacade` call site rejects the wrong token type; self-minted successor/stored refresh JWTs still validate | 01 | TW3 (access → rotate rejected, family untouched); TW5b (successor/stored refresh validation); TW6 (facade real-token wrong-type rejection at :159/:259 access-JWT, :248/:291 refresh-JWT); TW7c (B10/B11 characterization); INV2 (rotation half), INV4 (successor/stored half) |

## Phase Dependencies

Group A (parallel): 01
Group B (after A, parallel with each other): 02, 03
