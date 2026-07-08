# Execution Plan — Backend DDD/DDIA Refactor (B0–B12)

## Phase Summary

Structural refactor of the Spring Boot backend toward DDD/DDIA with a hard
constraint: **the HTTP API and persisted schema stay byte-identical** (except
deliberate, approved corrective migrations), and every existing test stays green.
Pilot subdomain is Planner.

Strategy: land the **test infrastructure first** (Step 0) so the most dangerous
change — the `status` enum + lowercase converter against the real
`ENUM('draft','saved')` column — is guarded by a Flyway-migrated `ddl-auto=validate`
tier before it is written. Then proceed smallest-blast-radius-first through the
B-roadmap, running `./gradlew test` (full suite, Docker up) after every phase.
Package-by-feature (B12) is strictly last.

**Execution order:** STEP 0 → B3+B4+B5 → B1+B9+B10 → B7 → B6 → B2 → B12.

Authoritative HOW: `docs/learning/ddd-and-ddia-explained.md` Appendices A–E.
Test runner: JUnit5 + Mockito + AssertJ + MockMvc + Testcontainers(MySQL 8).
Command: `./gradlew test` (all tiers; scope with `--tests "*Planner*"`). Docker
must be up for the Tier-3 containerized tests.

**Execution model:** sequential, one phase per code-writer agent, `./gradlew test`
green (verified by the orchestrator, Docker up) before advancing. Step 0 is being
done inline by the orchestrator because its drift decisions are schema changes that
must not be delegated.

---

## Phases

### Phase 0: Step 0 — Test infrastructure (PREREQUISITE, in progress)

Flip the `it` profile to the real migrated schema, resolve every drift it surfaces,
then add the three High-priority test guards + a coverage gate.

**0a. Flyway / validate wiring — DONE**
- `application-it.properties`: `spring.flyway.enabled=true`, `spring.jpa.hibernate.ddl-auto=validate`
- `MySQLIntegrationTest`: register `spring.flyway.{url,user,password}` on the container (base `application.properties` pins Flyway to a prod-style DB otherwise)

**0b. Resolve schema-truth drift surfaced by `validate`**
Decisions (yours): keep the DB schema and adapt entities for type drift; use a
corrective migration only for the timestamp-precision case.
- `planner_comments.depth` TINYINT vs entity `int` → **DONE**: `@JdbcTypeCode(SqlTypes.TINYINT)` + `MAX_DEPTH = 127`
- 5 MySQL ENUM/SET columns (`selected_keywords`, `status`, `planner_type`, `vote_type`, `entity_type`) map to VARCHAR but report CHAR → **DONE**: `@JdbcTypeCode(SqlTypes.CHAR)` on each field
- `status` ENUM rejects `"published"` test value → **DONE**: `TestDataFactory:85` + 9 `.status("published")` sites → `"saved"` (B1 later converts these strings to `PlannerStatus.SAVED`)
- `notifications` timestamps are second-precision, breaking μs-ordering → **PENDING**:
  - migration `V042__widen_notification_timestamps_to_microseconds.sql`: `created_at`/`read_at`/`deleted_at` → `TIMESTAMP(6)`
  - update `migration-test-seed.sql` if needed (existing literals stay valid under `(6)`)
  - rewrite the μs-ordering test deterministically via native SQL (the entity's `created_at` is `@PrePersist`-set + `updatable=false`, so `setCreatedAt` never controlled it); keep microsecond assertions

**0c. New test guards + coverage gate — PENDING**
- Query-count assertions (Hibernate `Statistics`, `hibernate.generate_statistics=true`) on `getPublishedPlanners`/`getRecommendedPlanners`/`searchPlanners` — guards `@EntityGraph`/JOIN FETCH through B6/B10
- Real concurrent `castVote()`-through-service test (Tier 3): assert `planners.upvotes == PlannerVote row count` AND exactly-one recommended notification. **Red-green-red verified** (break to `setUpvotes(get()+1)`, confirm RED, restore)
- ArchUnit layer-boundary rules (+ `com.tngtech.archunit:archunit-junit5`): Controller→Service→Repository, no field injection, DTOs not exposed cross-feature
- `jacocoTestCoverageVerification` ratcheted at the measured `jacocoTestReport` baseline

**Step 0 production bugs surfaced (real, fixed/recorded)**
- `NotificationService.notifyPlannerRecommended` was missing `@Transactional(REQUIRES_NEW)` (its 3 sibling notify methods have it). The recommended notification is created by an AFTER_COMMIT listener with no live tx, so under plain REQUIRED the insert never committed — recommended-threshold notifications were never delivered in production. **Fixed** (one annotation); proven by the concurrent + sequential castVote tests.
- Published-list read path has a pre-existing `UserSettings` N+1 (author's LAZY @OneToOne loaded once per row; `@EntityGraph` fetches `user` but not `user.settings`). Out of Step-0 scope to fix; the query-count test locks the slope so B6/B10 can't add a *new* per-row query. Candidate follow-up.

- Depends on: none
- Verify: full `./gradlew test` green (Docker up); `validate` context loads; all new tests pass; coverage gate active — **DONE/verified**

### Phase 1: B3 + B4 + B5 — DRY, SseEventType, isOwnedBy (no API impact)
- B3: delete `PlannerContentValidator.VALID_CATEGORIES`, route to `MDCategory`; add `ValuedEnum` + `EnumLookup`; the 7 enums implement it
- B4: `SseEventType` enum for the 6 event names; `@JsonValue`/`getValue()` preserves wire strings; replace literals in `PlannerService`/`SseService`/`NotificationService`
- B5: `Planner.isOwnedBy(Long userId)`; replace the 3 inline ownership checks in `PlannerService`
- Tests: `EnumLookupTest`; assert emitted SSE name == original literal; existing validator/service/Forbidden suites stay green
- Depends on: Phase 0
- Verify: `./gradlew test` green; SSE wire names + ownership 403 unchanged

### Phase 2: B1 + B9 + B10 — status enum + handler, category, DTO dedup
- B1: `PlannerStatus{DRAFT,SAVED}` (`@JsonValue` lowercase; `@JsonCreator` null→null); `PlannerStatusConverter` (lowercase, mirrors `KeywordSetConverter`); thread through `Planner` (keep the Step-0 `@JdbcTypeCode(CHAR)`), 3 response DTOs, 2 request DTOs, `PlannerService`; add `@ExceptionHandler(HttpMessageNotReadableException.class)`→400 generic `VALIDATION_ERROR`; convert the Step-0 `"saved"`/`"draft"` test strings → `PlannerStatus.SAVED`/`DRAFT`
- B9: remove `@Pattern` on `Planner.category:42` + the duplicated `VALID_CATEGORIES`; `isValidCategory` is the single authority; no `Category` VO
- B10: dedup the two `PublicPlannerResponse.fromEntity` overloads (intra-DTO only; no shared base)
- Tests: `PlannerStatusConverterTest` (lowercase + null pass-through); Tier-3 round-trip persist `SAVED`; controller `status:"garbage"`→400 generic; null-status-update-keeps-existing; invalid category→400
- Depends on: Phase 1
- Verify: `jsonPath($.status)` lowercase; invalid status→400; converter persists lowercase against real ENUM; all green

### Phase 3: B7 — split PlannerContentValidator (1074 LOC)
- Orchestrator keeps `validate`/`doValidate`; extract `StructuralValidator`, `CategoryValidator` (uses `MDCategory`), `EquipmentValidator`, `SkillStateValidator`, `IdReferenceValidator`, `StartBuffValidator`, `ValidationErrors`/`ErrorCode` (strings verbatim); replace `currentStrictMode` `ThreadLocal` with `ValidationContext`
- Tests: `PlannerContentValidatorTest` anchor stays green
- Depends on: Phase 2
- Verify: `--tests "*Validator*"` then full suite green; `ErrorCode` strings byte-identical; no `ThreadLocal`

### Phase 4: B6 — split PlannerService (1135 LOC → 5 services + guard)
- `PlannerCommandService`, `PlannerQueryService`, `PlannerPublishingService`, `PublishedPlannerQueryService`, `PlannerEngagementService` + `PlannerAccessGuard`; `PlannerController` wires them. **Preserve** atomic `incrementUpvotes` + `trySetRecommendedNotified` CAS + `@Version` in `castVote`
- Tests: `PlannerControllerTest` (HTTP anchor) + `VoteNotificationFlowTest` + the Phase-0 concurrent `castVote` + query-count tests stay green; re-target `PlannerServiceTest` onto the new services
- Depends on: Phase 3
- Verify: query counts unchanged; concurrent-vote invariant holds; controller API unchanged

### Phase 5: B2 — provider → AuthProviderType enum
- `AuthProviderType{GOOGLE,APPLE}` + `AuthProviderTypeConverter` (lowercase). Convert at the `User` edge: `UserRepository.findByProviderAndProviderId` param; callers in `UserService`/`AuthenticationFacade`. `OAuthCallbackRequest.provider` stays `String`; registry stays string-keyed. Reuse B1's 400 handler
- Tests: `AuthProviderTypeConverterTest`; invalid provider→400; Tier-3 `UNIQUE(provider,providerId)`; auth/user suites green
- Depends on: Phase 2 (reuses B1 handler); independent of B6/B7
- Verify: OAuth login works; provider serializes lowercase `google`; invalid→400

### Phase 6: B12 — package-by-feature (LAST, mechanical)
- Repackage into `planner/ moderation/ comment/ notification/ user/ auth/ shared/`; cross-feature calls via service interfaces; tests move with subjects; no behavior change
- Tests: entire suite green by construction; ArchUnit boundary rules green
- Depends on: Phases 1–5
- Verify: component-scan intact; full `./gradlew test` green; ArchUnit feature-boundary rules pass

---

## Phase Dependencies

Group A (prerequisite): Phase 0
Group B (after A): Phase 1
Group C (after B): Phase 2
Group D (after C): Phase 3 → Phase 4 (sequential; B6 after B7)
Group E (after C): Phase 5 (B2 only needs B1's handler)
Group F (after all): Phase 6
