# Task: Relocate planner keyword migration + validation out of the JPA converter

Archives the design debate of 2026-07-06 → 2026-07-08 (single session): investigating the prod 500 on `PUT /api/planner/md/{id}` (`Invalid keywords: [AccelBullet]`), shipping the interim converter fix + FE two-tier, a `/review`, and the extended backend-architecture debate that resolved this refactor.

Implementation-grade mechanics live in `mechanics.md` — read it before building the backend pipeline.

**N/A:** `docs/spec.md` "Data-Driven Features" sections (Data Model Catalog, Normalization Layer, Rendering Mode Enumeration, Reference Per Mode, Implementation Order) — N/A: this refactor relocates existing keyword-handling logic and consumes no raw game-data files. The keyword catalog (`VALID_KEYWORDS`/`plannerKeywords.json`) is produced by the untouched data pipeline.

## Decisions

- **D1 (taste): Move keyword migration + membership validation out of the JPA `KeywordSetConverter` into the content-processing pipeline.** A persistence-layer converter runs during Hibernate flush at transaction commit, so a `throw` there is wrapped as `InvalidDataAccessApiUsageException` and surfaces as a **500** (this was the prod bug). Domain transformation and validation belong upstream in a layer that can fail cleanly as a **400**. *Principle: never put logic that can reject at a flush-time persistence boundary — keep converters mechanical and move validation to where a failure is a clean client error.*
- **D2 (taste): Canonical-tree flow `req → parse → migrate → validate → serialize → save`.** Parse the content JSON once into a `JsonNode`, mutate/validate the tree, serialize once. *Principle: canonicalize external input at the trust boundary — parse to the model, operate on the model, serialize once; the server owns its storage representation rather than persisting the client's raw bytes.*
- **D3 (taste): BE derives the `selected_keywords` SET column from `content.selectedKeywords`; delete the redundant top-level `selectedKeywords` from the request DTO and stop the FE sending it.** *Principle: a denormalized read-model projection must be derived server-side from its single source, never sent as a redundant client copy the server trusts to stay in sync.*
- **D4 (taste): Split the converter's two old responsibilities by concern — `KeywordMigrator` (transform: remap renames, keep unknown) and `KeywordValidator` (check: membership).** `KeywordMigrator` lives in `planner.converter` (default placement — it replaces the converter's transform role), NOT `planner.validation`; `KeywordValidator` lives in `planner.validation` beside `IdReferenceValidator`. *Principle: migration is a transform and validation is a check — they are separate concerns even when they act on the same field.*
- **D5 (taste): Migrate always; gate keyword rejection to `strictMode` (publish).** A rename always remaps; a genuinely-unknown keyword is rejected 400 **only** when publishing, and tolerated on a draft sync. *Principle: migrate what's migratable and reject the rest loudly, but gate the loud reject to the operation's real strictness so routine (draft) writes never newly-break — the same two-tier already used on the FE.*
- **D6 (evidence): The SET-column derivation filters to valid members (drops unknown); the content blob keeps unknowns.** Forced: the MySQL `SET` column physically rejects non-members (`V002` DDL), and D5 leaves unknowns unrejected on draft — so the derived Set must be filtered to stay storable, while the opaque content blob is untouched (FE migrates it on read). (evidence: `selected_keywords SET(...)` DDL + D5)
- **D7 (taste): Demote `KeywordSetConverter` to a pure `Set ↔ CSV` serializer** (both directions; read path drops its whitelist filter too). Safe because upstream validation guarantees a clean write set and the MySQL `SET` definition is itself the read-side whitelist. *Principle: persistence converters stay mechanical; the domain whitelist lives once, upstream.*
- **D8 (evidence): Version-conflict (`syncVersion`) + limit-count stay inside `@Transactional`; no orchestrator/`PlannerWriter` split, no transaction extraction.** Extracting the syncVersion check reopens a lost-write TOCTOU (`Planner` has both `syncVersion` and JPA `@Version`); count+insert must be atomic; and the `published` flag the validator needs is already read for free inside the existing upsert transaction. A split would trade that free read for a paid one at single-row-upsert scale for no gain.
- **D9 (evidence): Deleting `selectedKeywords` from the DTO is backward-compatible; deploy BE-first.** `JacksonConfig.java:30` disables `FAIL_ON_UNKNOWN_PROPERTIES` ("forward compatibility"), so a stale client still sending the field is ignored, not errored. Read path (responses/list chips) is untouched.
- **D10 (evidence): The already-shipped FE two-tier (`migrateKeywords`-on-read + FE publish reject) STAYS.** Guest planners live only in IndexedDB and never reach the BE, so FE keyword handling is the sole defense for local-only data — not made redundant by the BE work.
- **D11 (default): Preserve keyword-drift observability with a `warn`/counter at the migrate-or-validate point.** 4xx validation errors are normally excluded from Sentry, so the 400 alone would go unseen; a log/metric keeps a forgotten `RENAME_MAP` entry visible.
- **D12 (note, out of scope): The interim converter fix** (remap+drop+`warn`, already implemented, uncommitted) is superseded by this refactor. Whether to ship it first to kill the live prod 500 while the refactor lands is an operational deploy choice, not a spec requirement.
- **D13 (note, out of scope): `PlannerContentSanitizer` is dead code** (never wired) — reviving it (server-side Tiptap URL sanitization) composes with the same tree pass but ships as a separate security-labeled change.

## Description

Relocate planner-keyword handling so the JPA converter stops doing domain work:

1. In the content-save pipeline, parse `content` once, run `KeywordMigrator` on the tree (remap renamed keyword ids via `RENAME_MAP`, keep unknowns), then validate — `KeywordValidator` checks membership against `VALID_KEYWORDS` and rejects unknowns **only in `strictMode`** (publish), accumulating into the existing combined `PlannerValidationException` (400).
2. Derive the `selected_keywords` SET column server-side from the migrated `content.selectedKeywords`, filtered to valid members. Delete the redundant top-level `selectedKeywords` request field from the DTO and from the FE's upsert request.
3. Serialize the (possibly migrated) tree once and store it as the content — canonical storage.
4. Demote `KeywordSetConverter` to a pure `Set ↔ CSV` serializer; move `VALID_KEYWORDS` to `KeywordValidator` and repoint `KeywordParityTest`.

Apply the migrate→validate→derive steps at every keyword write set-point (update, create, import) and the publish path — gated to `MIRROR_DUNGEON`.

## Scope
- `backend/.../planner/service/PlannerCommandService.java` (write set-points)
- `backend/.../planner/validation/PlannerContentValidator.java` + `IdReferenceValidator`, `ValidationContext`, `ValidationErrors`
- `backend/.../planner/converter/KeywordSetConverter.java`
- `backend/.../planner/entity/Planner.java` (converter field, `@Version`, `syncVersion`)
- `backend/.../planner/dto/UpsertPlannerRequest.java`, `UpdatePlannerRequest.java`, import DTO
- `backend/.../shared/config/JacksonConfig.java` (FAIL_ON_UNKNOWN evidence)
- `backend/.../planner/specification/PlannerSpecifications.java`, `PlannerRepository.java` (SET-column consumers)
- `frontend/src/pages/planner/hooks/usePlannerSyncAdapter.ts`, `types/PlannerTypes.ts`, `lib/plannerApi.ts`
- Already-shipped (context, stays): `frontend/src/shared/gameData/keywordNormalize.ts`, `pages/planner/lib/plannerValidation.ts`

## Target
- **CREATE:** `KeywordMigrator` (`planner.converter`), `KeywordValidator` (`planner.validation`); tests `KeywordMigratorTest`, `KeywordValidatorTest`, a serializer byte-parity test, and a `RENAME_MAP` FE↔BE parity check.
- **MODIFY (BE):** `KeywordSetConverter` → pure; `PlannerContentValidator` (JsonNode seam + wire `KeywordValidator`, separate content-size check); `PlannerCommandService` (migrate→validate→derive at all set-points); `UpsertPlannerRequest`/`UpdatePlannerRequest`/import DTO (drop field); `KeywordParityTest` (repoint to `KeywordValidator.VALID_KEYWORDS`); `KeywordSetConverterTest` (reduce to round-trip).
- **MODIFY (FE):** `usePlannerSyncAdapter.ts` (drop extract+send); `PlannerTypes.ts` (drop request-DTO field).

## Invariants
- INV1: The stored SET column equals the migrated `content.selectedKeywords` filtered to `VALID_KEYWORDS` — test: service/integration test asserting derived SET matches content after save.
- INV2: A renamed keyword id in content migrates to its current id on save, in both draft and publish — test: `KeywordMigratorTest` + service test.
- INV3: A genuinely-unknown keyword → 400 on **publish**; on **draft** it is dropped from the SET column but retained in the content blob — test: `PlannerContentValidator`/service tests, both modes.
- INV4: `KeywordSetConverter.convertToDatabaseColumn` never throws and performs only join/split — test: converter round-trip + no-throw.
- INV5: For a current client (no renames), content is byte-preserved through save wherever V8≡Jackson, so the content/note size gates are unaffected — test: serializer byte-parity fixture test.
- INV6: Removing `selectedKeywords` from the request DTO does not error a stale client that still sends it — test: controller test posting the extra field → 2xx.
- INV7: FE `plannerKeywords.json` keys, `KeywordValidator.VALID_KEYWORDS`, and the latest migration SET stay equal; FE and BE `RENAME_MAP` stay equal — test: repointed `KeywordParityTest` + rename-map parity test.

## Done When
- [ ] A stale-client sync carrying a renamed keyword (e.g. `AccelBullet`) returns 200 with the keyword migrated to `9828` (the former prod-500 path).
- [ ] A genuinely-unknown keyword returns 400 on publish and is silently dropped from the SET (kept in content) on draft sync.
- [ ] Keyword filter, search, and list-card chips still work after the DTO field removal + BE derivation.
- [ ] `KeywordSetConverter` is a pure `Set ↔ CSV` serializer; `VALID_KEYWORDS` lives in `KeywordValidator`; `KeywordParityTest` repointed and green.
- [ ] A stale client posting the removed top-level `selectedKeywords` is not errored.
- [ ] All existing BE + FE tests pass; `tsc -b` green.

## Test Plan

### Test Runner
- Backend: JUnit 5 via `./gradlew -p backend test` (from repo root; `-p backend` required). Scope: `--tests "org.danteplanner.backend.planner.*"` (converter/validation/service) + `KeywordParityTest`.
- Frontend: Vitest via `yarn --cwd frontend vitest run <paths>` + `yarn --cwd frontend tsc -b`. Redirect output to `/tmp/<prefix>-<session>-<suffix>.log`.

### Tests to Write
- [ ] `KeywordMigratorTest` — remap renames; keep unknown; dedupe alias↔current collision; non-MD/absent → empty; verbatim passthrough when no change (byte-parity guard).
- [ ] `KeywordValidatorTest` — membership: unknown → error in strict mode, tolerated in lenient mode; message names only the offending id (no whitelist leak).
- [ ] Serializer byte-parity fixture test — realistic + max-size content round-trips `readTree → writeValueAsString` byte-equal to a captured V8 reference.
- [ ] `PlannerCommandService` — migrate→validate→derive at update/create/import; INV1, INV2, INV3 (both modes); INV6 (stale extra field ignored).
- [ ] `KeywordSetConverterTest` — reduced to `Set ↔ CSV` round-trip + no-throw (INV4).
- [ ] `KeywordParityTest` repointed + `RENAME_MAP` FE↔BE parity (INV7).
- [ ] Every invariant above has its test realized here.
