# Task: Backend DDD/DDIA Refactoring — full B0–B12 roadmap

> Authoritative design: `docs/learning/ddd-and-ddia-explained.md` (Appendix A = targets+file:line, B = the how B1–B12, C = regression test→target map, D = 3-tier test structure, E = test gaps). This spec captures decisions, scope, invariants, and acceptance; the doc holds exhaustive per-target detail.
>
> **Project spec template note:** `docs/spec.md`'s required sections (Data Model Catalog, Normalization Layer, Rendering Mode Enumeration, Reference Per Mode) apply only to *features that consume raw game-data files*. This is a structural refactor that consumes no game data — those sections are **N/A** and intentionally omitted.

## Decisions

- **Pilot subdomain = Planner**; effort spans the whole backend but Planner leads. — concentrates risk where the design is most validated.
- **Preserve the HTTP API, JSON byte-identical** (`status`→`draft`/`saved`, `provider`→`google`, `category`→`5F`, SSE event names, error codes). — the frontend must require zero changes (evidence: FE sends/reads `draft`/`saved` in `usePlannerSave.ts`; values verified in `MDCategory`/migrations).
- **`status` → `PlannerStatus{DRAFT,SAVED}` enum, end-to-end (Option B)** with a custom lowercase `AttributeConverter`, **not** `@Enumerated(STRING)`. — the DB column is `ENUM('draft','saved')` (V001), and `@Enumerated` would write `DRAFT` and be rejected by MySQL. `@JsonValue`/`@JsonCreator` keep the wire value lowercase.
- **`@JsonCreator` returns `null` on null input; the service owns the `draft` default.** — `PlannerService:216/522` default null→draft on create; `:274-275/:411` treat null as "keep existing" on update. The enum must not coerce null→DRAFT or every partial update resets status.
- **Add `@ExceptionHandler(HttpMessageNotReadableException.class)` → 400, generic `VALIDATION_ERROR` body (no value echo).** — `GlobalExceptionHandler:112` maps `IllegalArgumentException`→403, and unhandled deser failures fall through to the generic handler→500; per `.claude/rules/backend/async/exceptions.md` the body must not echo valid values. **Accepted behavior change:** invalid `status`/`provider` now returns 400 (was 500/DB error).
- **`provider` → `AuthProviderType{GOOGLE,APPLE}` closed enum** (no further providers — user decision). Named `AuthProviderType`, **not** `OAuthProvider` (clashes with the existing strategy interface `service/oauth/OAuthProvider`). The `OAuthProviderRegistry` stays string-keyed (open bean discovery); conversion `String↔AuthProviderType` happens only at the `User` edge.
- **`category` stays `VARCHAR` (B9 Option 1).** — V012 deliberately moved it off a DB enum for MD/RR. Remove the field-level `@Pattern` (`Planner.java:42`) and the duplicated `VALID_CATEGORIES`; `isValidCategory` (plannerType-keyed, delegating to `MDCategory`/`RRCategory`) becomes the single validation authority. **No `Category` value object** (the "optional VO" is resolved OUT to keep it minimal).
- **`PlannerService` (1135 LOC) splits into 5 services + a shared guard** (B6); **`PlannerContentValidator` (1074 LOC) splits into orchestrator + 6 validators + `ValidationErrors`** (B7). Each public method already runs in its own transaction → split has no transaction-boundary risk.
- **Preserve the DDIA mechanisms intact**: atomic `incrementUpvotes`, the `trySetRecommendedNotified` CAS, `@Version` optimistic lock (→409), `@EntityGraph`/batch N+1 avoidance. A "cleaner" OO rewrite must not reintroduce read-modify-write.
- **Package-by-feature (B12) is LAST** — mechanical, behavior-neutral; tests move with subjects; cross-feature calls via interfaces.
- **Step 0 (test infrastructure) is a prerequisite, not optional.** Stand up the MySQL Flyway schema-truth tier and close the 3 High test gaps before the production refactor that relies on them.

## Resolved Ambiguities

> Proof of closure — zero questions deferred to plan.

| Question | Resolution | Source |
|----------|------------|--------|
| Which lib for query-count assertions? | Hibernate `Statistics` (`hibernate.generate_statistics=true`, assert `getPrepareStatementCount()`); **no new dependency** | Default — `build.gradle.kts` has no datasource-proxy/ttddyy |
| ArchUnit available? | New test dep `com.tngtech.archunit:archunit-junit5` (latest) | Default — absent from `build.gradle.kts:67-72` |
| Coverage gate threshold? | Add `jacocoTestCoverageVerification`; **ratchet at current measured coverage** (read `jacocoTestReport` baseline), fail build below it | Default — don't-regress policy; jacoco plugin already present |
| Does `ddl-auto=validate` pass today? | Unknown until run with Docker; **resolving any surfaced entity↔migration drift (incl. corrective migrations) is in-scope** | User — accepted whole-roadmap + Step-0 risk |
| Are the 2 `"published"` literals in main statuses? | **No** — `ModerationController:113` is a JSON map key, `PlannerSpecifications:25` is a JPA attribute name. B1 must NOT touch them | Codebase |
| Exact `status`-field code sites (B1)? | `Planner.java:47`, `PlannerService.java:216`, `:522` (+ Javadoc `UpsertPlannerRequest:28`) | Codebase |
| Remove `@Pattern` on `Planner.category:42`? | Yes — `isValidCategory` (plannerType-aware) is stricter and is the authority | Codebase + decision |
| `Category` value object in scope? | **No** — keep minimal (the "optional VO" resolved out) | Default |
| Cross-DTO mapping extraction in scope? | **No** — B10 = intra-DTO dedup only (the two `PublicPlannerResponse.fromEntity` overloads); no shared base (leak risk) | Default — low value, security |
| How is the converter applied? | Explicit `@Convert(converter=…)` on the field, matching `KeywordSetConverter` usage | Codebase convention |
| `ErrorCode` enum values (B7)? | Must equal the existing client-facing strings (`EMPTY_CONTENT`, `SIZE_EXCEEDED`, …) verbatim | Codebase + exceptions.md |
| Complete SSE event-name set (B4)? | `created, updated, deleted, notify:comment, notify:published, notify:recommended` — wire strings preserved via `@JsonValue` | Codebase (grep) |
| Scope of `isOwnedBy` (B5)? | Planner ownership only (3 `PlannerService` sites); other entities' ownership out of scope | Default scoping |
| Does converting `status` break JPA Criteria (`PlannerSpecifications`)? | No — specs filter `published` (boolean) and never compare `status` to a string | Codebase |
| Test runner/framework? | JUnit5 + Mockito + AssertJ + MockMvc + Testcontainers; `./gradlew test` (all tiers, no tag filter) | CLAUDE.md + `build.gradle.kts` |
| B12 package homes for cross-cutting code? | `validation/`→`planner/` (planner-content-specific); `security/`+`service/token/`+`service/oauth/`+`AuthenticationFacade`→`auth/`; `config/`+`exception/`→`shared/` | Default taxonomy |
| Spring component-scan after repackaging? | Unaffected — `@SpringBootApplication` at `org.danteplanner.backend` scans all subpackages | Codebase/convention |
| New schema migrations for B1–B12? | None (all columns unchanged); only Step 0 may add corrective migrations if drift surfaces | Codebase (migrations + column types) |

## Description

Refactor the Spring Boot backend toward DDD/DDIA structure **without changing the HTTP API or any persisted schema** (except possibly corrective drift migrations in Step 0), keeping every existing test green. Work proceeds in ordered phases:

**Execution order:** STEP 0 → B3 + B4 + B5 → B1 + B9 + B10 → B7 → B6 → B2 → B12.

- **STEP 0 — test infrastructure (prerequisite).** Flip `application-it.properties` to `flyway.enabled=true` + `ddl-auto=validate` so the MySQL Testcontainer tier validates against the real migrated schema; resolve any drift surfaced. Add: query-count assertions on the list read-paths (guards `@EntityGraph`); a real `castVote()`-through-service concurrency test (assert atomic `upvotes` and exactly-one recommended notification); ArchUnit layer-boundary rules; a Jacoco coverage gate (ratchet at current).
- **B3 — DRY.** Delete `PlannerContentValidator.VALID_CATEGORIES`; route to `MDCategory`. Extract `ValuedEnum` + a shared `EnumLookup` resolver for the 7 enums.
- **B4 — `SseEventType` enum** for the 6 event-name literals; wire strings preserved.
- **B5 — `Planner.isOwnedBy(userId)`** replacing the 3 inline ownership checks in `PlannerService`.
- **B1 — `PlannerStatus{DRAFT,SAVED}`** end-to-end + the deserialization handler; reconcile the 10 test sites (`status="published"` → `.status(SAVED)` keeping `.published(true)`).
- **B9 — `category`** Option 1: remove `@Pattern` + the DRY duplicate; centralize in `isValidCategory`.
- **B10 — DTO** intra-dedup of the two `PublicPlannerResponse.fromEntity` overloads.
- **B7 — split `PlannerContentValidator`** into orchestrator + Structural/Category/Equipment/SkillState/IdReference/StartBuff validators + `ValidationErrors` (`ErrorCode` enum); replace `currentStrictMode` `ThreadLocal` with an explicit `ValidationContext`.
- **B6 — split `PlannerService`** into `PlannerCommandService`, `PlannerQueryService`, `PlannerPublishingService`, `PublishedPlannerQueryService`, `PlannerEngagementService` + shared `PlannerAccessGuard`; preserve atomic increment + CAS in `castVote`.
- **B2 — `AuthProviderType{GOOGLE,APPLE}`** closed enum; convert at the `User` edge.
- **B12 — package-by-feature** (`planner/ moderation/ comment/ notification/ user/ auth/ shared/`); cross-feature calls via interfaces.

## Scope (read for context)

- `docs/learning/ddd-and-ddia-explained.md` (full design, Appendices A–E)
- `backend/CLAUDE.md`, `.claude/rules/backend/**` (SOLID/layering/testing/exceptions/migrations rules)
- `backend/src/main/java/org/danteplanner/backend/entity/{Planner,User,MDCategory,RRCategory}.java`
- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java`, `validation/PlannerContentValidator.java`
- `backend/src/main/java/org/danteplanner/backend/dto/planner/*.java`, `config/JacksonConfig.java`, `exception/GlobalExceptionHandler.java`, `converter/KeywordSetConverter.java`
- `backend/src/main/java/org/danteplanner/backend/service/oauth/*`, `facade/AuthenticationFacade.java`, `repository/{PlannerRepository,UserRepository}.java`
- `backend/src/main/resources/db/migration/V001,V010,V012*.sql`, `backend/src/test/resources/application-{test,it}.properties`, `backend/build.gradle.kts`

## Target (create / modify)

**Create:** `entity/PlannerStatus.java`, `entity/AuthProviderType.java`, `converter/PlannerStatusConverter.java`, `converter/AuthProviderTypeConverter.java`, `entity/ValuedEnum.java` (+ `EnumLookup`), `entity/SseEventType.java`; B6 services (`PlannerCommandService`, `PlannerQueryService`, `PlannerPublishingService`, `PublishedPlannerQueryService`, `PlannerEngagementService`, `PlannerAccessGuard`); B7 validators (`StructuralValidator`, `CategoryValidator`, `EquipmentValidator`, `SkillStateValidator`, `IdReferenceValidator`, `StartBuffValidator`, `ValidationErrors`/`ErrorCode`, `ValidationContext`). **Tests:** `PlannerStatusConverterTest`, `AuthProviderTypeConverterTest`, `EnumLookupTest`, a concurrent-`castVote` test, query-count tests, ArchUnit test class. Build: ArchUnit dep + `jacocoTestCoverageVerification`.

**Modify:** `Planner.java` (status type, `publish`/`unpublish`/`isOwnedBy`, drop `@Pattern`), `User.java` (provider type), the 5 status-bearing DTOs, `PlannerService.java`, `PlannerContentValidator.java`, `GlobalExceptionHandler.java`, `JacksonConfig` (if needed), `UserService`/`AuthenticationFacade`/`UserRepository`, the 3 SSE/notification services (event-name usages), `application-it.properties`, `build.gradle.kts`, and the ~10 test sites using `status="published"` (+ `TestDataFactory`). **B12:** move all classes into feature packages.

## Impact Analysis

- **Modified (high):** `PlannerService`, `PlannerContentValidator`, `Planner`, `User` — core domain; many consumers.
- **Modified (medium):** 5 DTOs, `GlobalExceptionHandler`, OAuth/User flow, SSE services.
- **Dependencies:** `PlannerController` (wires the split services); `AuthController`/`AuthenticationFacade` (provider type); every test using `TestDataFactory`.
- **Ripple:** B12 changes every import path (mechanical). Enum conversions change every construction call site (`@Builder` `.status(...)`/`.provider(...)` become typed) across main + ~26 test files.

## Risk Assessment

- **Edge cases:** null `status` on update (keep-existing); invalid `status`/`provider` input (→400 generic); category change validated per `plannerType`; concurrent votes (atomic+CAS); `ddl-auto=validate` surfacing latent drift.
- **Performance:** must not regress N+1 (query-count tests guard it); atomic increment unchanged.
- **Security:** error bodies stay generic (no value/field echo); public DTOs must never gain owner-only fields (`content`/`status`/`syncVersion`) — B10 forbids a shared base.

## Boundaries & Invariants

- **Trust boundary:** the HTTP request body (`@RequestBody` DTOs) and OAuth callback are untrusted; validation occurs at the controller/service edge.
- **Invariant 1 — Wire contract:** for every endpoint, request and response JSON are byte-identical to pre-refactor (`status`∈{`draft`,`saved`}, `provider`=`google`, `category`=`5F`/…, SSE event names, error `code`s).
- **Invariant 2 — Vote consistency:** after N concurrent `castVote`s, `planners.upvotes` == count of `PlannerVote` rows, and the recommended notification fires **exactly once**.
- **Invariant 3 — Optimistic lock:** a stale `syncVersion`/`@Version` write yields 409, never a silent overwrite.
- **Invariant 4 — Status integrity:** a persisted `status` is always `draft` or `saved`; `published` remains a separate boolean; null status on update never mutates the stored value.
- **Invariant 5 — Authorization:** `isOwnedBy` extraction changes no authz outcome; non-owners still get 403.
- **Invariant 6 — No info disclosure:** error responses never echo invalid values, field names, or valid-value lists.
- **Invariant 7 — Schema truth:** the entities validate against the real Flyway-migrated schema (`ddl-auto=validate` green).

## Failure Modes

| Invariant | Trigger (how it breaks) | Response | Test |
|-----------|-------------------------|----------|------|
| Inv 4 status | Converter writes `DRAFT` (uppercase) | Rejected by `ENUM('draft','saved')` | `PlannerStatusConverterTest` asserts `getValue()=="draft"`; schema-truth tier persists/reads back |
| Inv 4 status | Update omits status (null) | Keep existing; no mutation | `PlannerServiceTest` update-keeps-status |
| Inv 1 contract | Invalid status/provider in body | 400 + generic `VALIDATION_ERROR` (no echo) | new controller test: `status:"garbage"` → 400 |
| Inv 2 votes | Two votes interleave (concurrency) | Atomic `incrementUpvotes` + `@Version`; no lost update | concurrent `castVote()` test (Tier 3) asserts count |
| Inv 2 votes | Many cross the threshold at once | CAS `trySetRecommendedNotified` picks one winner | concurrency test asserts exactly-one notification |
| Inv 3 lock | Stale `syncVersion` write | 409 `PlannerConflictException` | existing `PlannerControllerTest:569` |
| Inv 5 authz | Non-owner mutates planner | 403 `PlannerForbiddenException` | existing forbidden-case tests (unchanged) |
| Inv 7 schema | Entity drifts from migration | App refuses to start (`validate`) | Tier-3 context loads |
| Inv 1 (N+1) | A fetch-join is dropped in B6/B10 | query count unchanged | query-count assertion on list endpoints |

### Visualized Failure (worst row — Inv 2 under a careless B6)

1. B6 moves `castVote` into the `Planner` aggregate and "simplifies" it to `planner.setUpvotes(planner.getUpvotes()+1)` in Java.
2. Two users vote at the same instant; both read `upvotes=9`, both write `10`.
3. **Broken state:** `upvotes=10` but there are 11 `PlannerVote` rows — a lost update, and the threshold-crossing notification may double-fire or never fire.
   → The concurrent-`castVote` Tier-3 test (Step 0) fails here, because it asserts `upvotes == rowCount` and a single notification — forcing the implementer to keep the atomic `incrementUpvotes` + CAS instead of read-modify-write.

## Done When

- [ ] STEP 0: `application-it.properties` runs `flyway=true`+`ddl-auto=validate`; any drift resolved; query-count + concurrent-`castVote` + ArchUnit tests added and green; coverage gate active.
- [ ] B1: `status` is `PlannerStatus` end-to-end; `jsonPath("$.status")` still emits lowercase; invalid status → 400 generic; converter test proves lowercase persistence.
- [ ] B2: `provider` is `AuthProviderType`; OAuth login + `UNIQUE(provider,providerId)` still work; invalid provider → 400.
- [ ] B3/B4/B5/B9/B10: duplicates removed (single source of truth), event names typed, ownership check is `isOwnedBy`, `category` validated only via `isValidCategory`, DTO overloads deduped — all behavior-neutral.
- [ ] B6/B7: `PlannerService` and `PlannerContentValidator` split per the designed seams; atomic+CAS preserved; `ThreadLocal` replaced by `ValidationContext`; public APIs (controller, `validate()`) unchanged.
- [ ] B12: classes repackaged by feature; component-scan intact; cross-feature calls via interfaces; ArchUnit boundary rules green.
- [ ] **The HTTP API is byte-identical** (verified by unchanged `jsonPath`/contract assertions).
- [ ] **All existing tests pass**; `./gradlew test` green (all tiers, Docker up for Tier 3).

## Test Plan

### Test Runner
- Framework: JUnit 5 + Mockito + AssertJ + Spring MockMvc + Testcontainers (MySQL 8).
- Command: `./gradlew test` (runs all tiers; no tag filtering — Tier 3 needs Docker). Scope a phase with `./gradlew test --tests "*Planner*"`.

### Tests to Write
- [ ] `PlannerStatusConverterTest` / `AuthProviderTypeConverterTest`: assert lowercase `convertToDatabaseColumn` and round-trip (Tier 1).
- [ ] Schema-truth round-trip (Tier 3): persist `SAVED`, read back via the migrated `ENUM` column.
- [ ] Invalid-input controller tests: `status:"garbage"` and unknown `provider` → 400 with generic body (no value echo).
- [ ] Concurrent `castVote()` (Tier 3): N threads through the service; assert `upvotes==rowCount` and exactly-one recommended notification.
- [ ] Query-count assertions on `getPublishedPlanners`/`getRecommendedPlanners`/`searchPlanners` (Hibernate `Statistics`).
- [ ] ArchUnit: Controller→Service→Repository only; no field injection; DTOs not exposed cross-feature.
- [ ] `EnumLookupTest` (B3); `PlannerStatusConverter` null pass-through.
- [ ] Reconcile the 10 `status="published"` sites; every Failure-Modes `Test` cell realized.

## Verification

### Manual
1. `./gradlew jacocoTestReport` to read the baseline, set the gate, then `./gradlew test` (Docker running).
2. Hit `POST /api/.../planners` with a valid then an invalid `status`; confirm 200 then 400-generic.
3. Confirm a published-planner list response JSON is unchanged field-for-field vs `main`.

### Edge Cases
- [ ] Update with `status` omitted → stored status unchanged.
- [ ] OAuth callback with `provider:"google"` → succeeds; unknown provider → 400.
- [ ] Category change to an invalid value for the planner's type → 400 `INVALID_CATEGORY`.
- [ ] `ddl-auto=validate` startup → green (or drift fixed).
