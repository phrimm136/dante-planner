# Debt

Append-only dump for found work. Not a queue: entries are captured so they stop
occupying attention, and are pulled only by a deliberate defrag or design session.

The `static/` submodule keeps its own `static/docs/debt.md` for the data and
asset pipeline.

- `.githooks/commit-msg` lacks the commit type/scope grammar; the local
  `core.hooksPath = .githooks` bypasses the global hook, so duplicate or source
  `~/.config/git/hooks/commit-msg` here.
- Local `dev` is far ahead of origin on one disk; a push to a ref ArgoCD does not watch
  (or a second remote) would buy backup durability without triggering deployment.
- Gift-id fields are traversed twice per content validation — `IdReferenceValidator` and
  `StartBuffValidator` re-implement the same textual/duplicate checks, so one bad element
  reports twice. Ruled out of RFC 0002 scope; collapsing it changes observable API output
  and needs its own argue.
- No test proves the username-suffix retry survives a real Hibernate session after a
  failed INSERT — only the exception classifier is covered by synthesized exceptions. A
  containerized test driving a live suffix collision through the retry loop would close it.
- `?login=rate_limited` has no frontend consumer — the redirect code is distinct on the
  wire but renders as a plain home page; the SPA needs to read the `login` param.
- `PlannerContentEntityExtractor` keeps the pre-`path()` Jackson null-check idiom (~10
  copies). Ruled out of the validator-traversal conversion (it coerces where validators
  reject, and lives outside `planner/validation`); local `path()` adoption would collapse
  the checks without publishing the validation helper.
- Validator sub-rule shape still diverges: extracted `validate*` methods mix
  boolean-returning gates (false = stop checking this element) with void accumulators.
  Consolidating to a uniform void return demands removing the error short-circuit, which
  changes the error-collection strategy and its tests — needs its own pass, not a rider.
- FE publish makes two sequential round trips (awaited `syncToServer`, then a body-less
  publish). The intent route accepts a content-carrying body that would collapse it to
  one, but adopting it moves the draft flush into the publish call and must integrate
  with the save pipeline's conflict handling — its own pass, ruled a regression to keep.
- Toggle endpoints flip state instead of converging on it; make them idempotent the way
  planner publish is (repeat calls are no-ops, not inversions).
- A full review pass on `shared/` and `user/` is outstanding.
- FE `selectedKeywords` schemas still allow `.nullish()` although the two response DTOs
  now always emit an array; tighten to a plain `z.array(z.string())` and drop the
  now-dead `?? []` fallbacks in the two consumers.
- Obsolete FE snapshot in `pages/identity/__tests__/IdentityDetailPage.parity.test.tsx`
  ("renders uptie 1 with skill1 selected 2") — regenerate on the next pass through
  that area.
- Stale "a fresh account's syncEnabled is null" comments in `e2e/src/plannerFixture.ts`
  and `e2e/tests/mutation-gestures.spec.ts` — behaviorally fine post-V056, textually
  outdated.
- The `?login=` gap covers both variants, not just `rate_limited`: the FE reads neither
  `?login=error` nor `?login=rate_limited` (zero hits for `login=` in `frontend/src`);
  closing it needs `validateSearch` on the `/` route, a toast in `GlobalLayout`, and
  `login.*` copy in all 5 locales.
- `CommentEngagementService.toggleUpvote` inserts a `PlannerCommentVote` and then runs
  `incrementUpvoteCount`, which is `@Modifying(clearAutomatically = true)` without
  `flushAutomatically`; whether auto-flush covers the pending vote insert depends on
  query-space overlap between `planner_comments` and `planner_comment_votes`, which do
  not overlap. No IT asserts that a second comment upvote returns 409, so the vote
  row's durability on that path is untested. Add the duplicate-upvote IT (and consider
  `flushAutomatically = true` on the increment).
- `CsrfDoubleSubmitFilterTest.mutation_WhenTokenTampered_Rejected` is a ~1-in-64 flake:
  it tampers by prepending `"x" + token.substring(1)`, so a token that already starts
  with `x` yields an untampered "tampered" token and the filter correctly admits it.
  Fix by flipping a character instead of overwriting with a constant.
- The big-bang deployment window needs a runbook once the comment converter lands.
  Sequence: block traffic → full DB backup via `scripts/ops/access/` → deploy with
  traffic still blocked (Flyway runs the planner decomposition against prod data for
  the first time) → verify Flyway schema history and sanity counts on the new planner
  tables → pre-check `SELECT COUNT(*) FROM planner_comments WHERE content LIKE '<%'`,
  then run the Node HTML→Tiptap-JSON converter (format-detecting, idempotent, imports
  the FE extension set) → smoke-check converted-comment render and new-comment
  round-trip → reopen. Rehearse the entire window first on a prod dump restored
  locally: Flyway train plus converter against real data.
- RESOLVED 2026-08-12 — `DegradationIT` F2 (rate-limit Redis cut → 503) order-dependence
  was not leaked sibling state: bucket4j's `withRequestTimeout` and Lettuce's command
  timeout shared the same 3s constant, so every rate-limit stall raced two equal timers
  and the exception type depended on the winner (`RedisException` → typed 503,
  `io.github.bucket4j.TimeoutException` → catch-all 500); sibling tests merely biased
  the race. Fixed by mapping bucket4j's `TimeoutException` in the same handler.
- `DegradationIT.evictPooledPrimaryConnections` still cannot evict the primary pool: the
  routing target for PRIMARY is the `GtidCapturingDataSource` wrapper, not a
  `HikariDataSource`, so the helper's instanceof walk skips it. Replica and bulkhead are
  covered since the lazy-proxy descent fix; the primary needs an unwrap step.
- `user.entity` is a de facto shared domain model: `User`, `UserRole` and `RestrictionState`
  are read by 25 classes across every feature, including `shared.security` and
  `auth.token`. The entity half of the user-internals boundary rule is therefore a freeze
  against new edges, not a boundary anything is close to satisfying — closing it means
  deciding what the account types look like outside the `user` feature (a shared value type,
  a projection, or an id plus a lookup), which is design-lane work, not a mechanical move.
- The backend patterns still declared in `.claude/hooks/forbidden-patterns.json` predate
  ADR 070 and should be audited for migration to checkstyle: field injection
  (`@Autowired private`), entity-typed `ResponseEntity` returns from controllers, `.get()`
  on `Optional`, `@Transactional` on private methods, string concatenation inside `@Query`,
  empty catch blocks, `@RequestBody` without `@Valid`, change-history phrasing in durable
  records, `Boolean.TRUE.equals(...)`, plus the file-specific pairs (`@Transactional` in a
  Controller, `@Query` in a Service). Each needs either a checkstyle equivalent — a
  `RegexpSinglelineJava` id with id-scoped suppressions, or an ArchUnit rule where the check
  is structural — or a stated reason it is Claude-only working process and belongs in the
  hook.
- No CI lane asserts query shape: statement-count and EXPLAIN access-path checks against
  seeded, ANALYZE'd data exist only as measurement-form prose in the portfolio catalog. A
  regression in pagination depth cost or FULLTEXT access path ships silently today.
- The full vitest suite (~7600 tests) now trips the per-worker heap cap on most runs
  (`ERR_WORKER_OUT_OF_MEMORY`, one worker, cumulative heap — no single hot file; the run
  is green under `--logHeapUsage`, whose forced per-file GC masks it). Needs a worker
  memory/pool tuning pass in `vitest.config`, not a test fix.
- RESOLVED 2026-08-13 — e2e oauth-journey vs the id_token verifier: the stub minted an
  `alg:none` id_token the post-853e1211 `GoogleIdTokenVerifier` rejects at decode, and
  nothing pointed `GOOGLE_OAUTH_JWKS_URI` at the stub. Fixed by signing the stub's
  id_token RS256 with the e2e test RSA key, serving a `/jwks` route, and wiring
  `GOOGLE_OAUTH_JWKS_URI`/`GOOGLE_OAUTH_ISSUER` in both stub-facing compose files.
- RESOLVED 2026-08-13 — the publish/unpublish gesture specs failed on two stacked
  defects. Real bug: `useAppSse.applyPlannerUpsert` wrote the server's flat SSE planner
  payload into `plannerQueryKeys.detail(id)`, whose consumers expect a nested
  SaveablePlanner — any save's SSE echo shape-corrupted the open detail page and the
  next render crashed into the error boundary (`isMDPlanner` reading `.config` of a flat
  row). Fixed by invalidate-never-patch, per the ruled sync-stream direction. Masked
  underneath: the specs expected the pre-intent-endpoint contract (`PUT /{id}/publish`
  with a body) while the API is body-less `POST /{id}/publish|unpublish`; specs updated.
- `local-multiregion-up.sh` step 5 verifies replication with a fixed `sleep 4`, which is
  too short under load (transient "did not start" failures on a busy box) — replace with
  a bounded poll of `Replica_SQL_Running`. Step 2's flyway grep was fixed 2026-08-13 to
  match "is up to date" on re-runs; the sleep remains.
- Two alt-text sites still carry hardcoded English because no registered namespace names
  them and `static/` was out of scope for the pass: `shared/skill/components/SkillInfoPanel.tsx`
  (`alt={isDefenseSkill ? 'Defense' : 'Attack'}` — `database:skill.defense` covers one arm,
  nothing covers "Attack") and `pages/identity/components/ResistancePanel.tsx`
  (`Slash`/`Pierce`/`Blunt` — labels exist in `plannerKeywords.json`, which is not in
  `NAMESPACES` and is lazy-loaded by a planner hook, so an identity component cannot reach
  them). Adding the three damage types plus an "Attack" sibling to `database` closes both.
  `SkillInfoPanel`'s attack-weight squares also lost their count to the accessibility tree
  when they took `alt=""`; conveying it needs an interpolated `aria-label` on the container.
- `ApiClient.post/put/patch` drop falsy bodies: the body is spread under a truthiness test,
  so `post('/x', false)`, `post('/x', 0)` and `post('/x', '')` send no body while still
  setting `Content-Type: application/json`. Widening the guard to `!== undefined` is a
  behavior change no current caller needs, which is why it was left as found.
- `shared/comment/lib/commentTree.ts` is inconsistent about a node with no `replies` array:
  `containsComment` and `insertComment` tolerate it (and a test pins that), while
  `updateCommentInTree` and `countComments` dereference it unguarded and would throw on the
  same input. `replies` is a declared non-optional property, so no compiler flag surfaces it.
- `components/hooks/useUrlFilters.ts` documents that "an undefined value drops its key", but
  its `setParams(updates: Partial<TParams>)` signature cannot express that under
  `exactOptionalPropertyTypes`. `usePlannerSearchFilters` works around it with a locally
  widened mapped type; the root fix is widening the shared signature to
  `{[K in keyof TParams]?: TParams[K] | undefined}`, which also serves `useMDUserFilters`
  and `useMDGesellschaftFilters`.
- `lib/constants/theme.ts` declares `DIFFICULTY_COLORS: Record<string, string>` and
  `MD_ACCENT_COLORS: Record<number, string>` — index signatures over finite domains
  (`DifficultyLabel`, the MD versions). Keying them by those literal unions would make every
  lookup non-optional at the root and let two consumer widenings be reverted
  (`ThemePackSelectorPane.getDifficultyColor`, `StartBuffCardVariant.descriptionColor`).
- DOMPurify reports itself unsupported under happy-dom and hands back its input, so any test
  outside `src/shared/sanitize/**` that asserts on sanitized output is asserting on garbage.
  `vite.config.ts` routes the sanitizer's own tests to jsdom for exactly this reason; the
  same hazard applies to every other suite that happens to sanitize.
- `StartGiftEditPane.handleGiftClick` did not fold into the shared `applyGiftToggle`: it
  works on unencoded ids, writes three store slices in one update (dropping the old row's
  gifts, setting the keyword, setting the selection), and its same-row arm is already
  `useCappedSelection.toggle` with a cap and a mirror. The recipe cascade would be wrong for
  start gifts. Folding it needs cap, mirror and keyword injection — a different function.
- `HorizontalThemePackGallery.getFloorIndexForPack` returns `-1` for a pack absent from
  `floorSelections`, which yields the note key `floor--1` and renders "Floor 0".
- Two deliberate UX regressions taken to make elements keyboard-reachable: `StartBuffCard`'s
  description sits under a full-card overlay button and can no longer be wheel-scrolled (its
  scrollbar was already hidden), and `NoteEditor` now activates on focus rather than click,
  so clicking the byte-counter strip or the border padding no longer reveals the toolbar.
  Both are reversible if the affordance turns out to matter more than the reach.
- The full-suite worker OOM is survivable with `vitest run --maxWorkers=1` (green at 216
  files / 7814 tests where `--maxWorkers=2` intermittently loses a worker on a loaded box).
  That is a workaround, not the pool-tuning pass the earlier entry asks for.
- `DeckFilterBar` renders the Reset All control twice with identical props and children (a
  desktop `resetAllButton` and an inline mobile copy); `deckFilterFacets.parity.test.ts`
  keeps a fifth hand-written enumeration of the ten filter sets in its `BASE_STATE` fixture,
  which `createDefaultDeckFilterState()` now supersedes.
- `pages/identity/lib/formatSanityCondition.ts` still exports `formatSanityConditions` with
  its own unit tests, but production no longer calls it — `useSanityConditionFormatter`
  maps the singular form over the names instead of zipping two parallel arrays.
- `vite.config.ts`'s `EDITOR_TESTS` list names `PlannerMDEditorContent.test.tsx`, which no
  longer exists.
- 2026-08-14 — The frontend ships no error-tracking SDK while the backend runs
  `io.sentry:sentry-spring-boot-starter-jakarta`; a browser-side exception reaches nobody.
  Adopting one is deferred rather than forgotten: the natural single capture point is the
  planned `showError` presenter, and wiring an SDK before it exists scatters capture calls
  across every catch block. `frontend/src/lib/storage.ts:81` meanwhile carries a false
  `Sentry will auto-capture console.error` comment — delete it when this resolves either way.
- 2026-08-14 — Two emitted metrics have no consumer: `sse.publish.dropped`
  (`shared/sse/SsePublisher.java`) and `planner_reconciler_drift_total`
  (`planner/service/PlannerDriftReconciler.java`) are read only by their own tests, and
  nothing under `deploy/grafana/` panels or alerts on either — a subscriber-drop burst or a
  drift storm is invisible in production. The planned `sse.publish.unserializable` will join
  them. Closes when all three have a dashboard panel and an alert rule.
- 2026-08-14 — The frontend guard scripts `check:fp` (`ast-grep scan`) and
  `check:compiler-bailouts` (`tsx scripts/compiler-bailouts.ts`) run in no workflow under
  `.github/workflows/`, so both are local-only and a violation ships unblocked. RFC 0004
  wires them; this entry stands until that lands.
- 2026-08-14 — `deploy/CLAUDE.md:14-16` inverts a security fact: it states that an absent
  `AUTH_LOCAL_REDIS_HOST` "aliases to auth (read-local no-op)", while
  `deploy/overlays/oregon/configmap-patch.yaml` documents the opposite — Spring's relaxed
  binding cannot see `REDIS_AUTH_HOST` through the
  `${AUTH_LOCAL_REDIS_HOST:${AUTH_REDIS_HOST:localhost}}` default, so an unset value resolves
  to a dead localhost and fail-opens every revocation check. The two files also disagree on
  the variable names (`AUTH_LOCAL_REDIS_HOST`/`AUTH_REDIS_HOST` versus
  `REDIS_AUTHLOCAL_HOST`/`REDIS_AUTH_HOST`). First of the documentation fixes to make: it is
  the only one whose reader is misled about a security surface.
- 2026-08-14 — `runbooks/rds-migration.md` cannot be executed as written. The credential step
  pipes the RDS master secret through `jq -r .password` although the secret is a plain string;
  it directs the operator to a terraform output that is deliberately null; five steps run
  `docker exec` against a `mysql` container that no compose file defines; and the migration
  freeze names `V045` as the current head against an actual head of `V057`.
- 2026-08-14 — `runbooks/schema-decomposition-migration.md` contradicts itself and the fleet.
  Its quick-reference table prescribes `argocd app set … --sync-policy none`, which the body
  of the same runbook correctly rules out because this fleet runs ArgoCD in CORE mode; it
  omits `.github/workflows/window-schema-decomposition.yml`, which already automates steps the
  runbook gives by hand; and it points at `scripts/ops/lib/alarms.sh`, which does not exist.
- 2026-08-14 — `runbooks/environment-setup.md` misstates the auth contract. It gives the OAuth
  callback as `/auth/callback/google` at seven sites against the real
  `/api/auth/google/callback`; it lists `JWT_SECRET`, which nothing in the backend or the
  deploy manifests reads, while omitting the three variables that are read
  (`JWT_PRIVATE_KEY_PATH`, `JWT_PUBLIC_KEY_PATH`, `JWT_ENCRYPTION_KEY`); and its rate limits do
  not match the configured ones. Following it end to end yields an environment whose login
  does not work.
- 2026-08-14 — `Data Structure.md` is stale in every dimension — paths, field names, and entity
  counts all disagree with `static/` — so it misleads rather than under-informs. It needs a
  decision before an edit: regenerate it from the data, or archive it to `legacy/` and let
  `static/CLAUDE.md` be the single description of the data shapes.
- 2026-08-14 — RFC 0001 and RFC 0002 carry status-quo sections describing the state their own
  implementations removed, so each now argues against a world that no longer exists. An
  Implemented RFC is not edited for accuracy, so the resolution is a dated note at the head of
  those sections marking them as the pre-implementation state.
- 2026-08-14 — This file's own entries predate the dating convention and carry no date, so
  nothing distinguishes a week-old find from a year-old one; date them on the next defrag
  pass. One is also wrong on the facts: the `PlannerContentEntityExtractor` entry justifies its
  exclusion partly by the class living outside `planner/validation`, and it does not — the
  class sits at `backend/src/main/java/org/danteplanner/backend/planner/validation/`. The
  coercion-versus-rejection half of that reasoning stands on its own.
- 2026-08-14 — ADR enforcement is a decided practice with nothing implementing it. A subagent
  audit checks four things: each ADR's stated invariants against the code, whether an
  enforcement mechanism exists for each rule that claims one, decisions recorded as done that
  were never carried out, and whether the names an ADR relies on are still live — RFCs
  included. It triggers from wrap-up for any session touching `docs/adr/` or backend
  architecture, and emits unstaged supersession edits plus entries in this file, never a
  rewrite of an existing ADR. Closes when the wrap-up skill invokes it.
- 2026-08-14 — The frontend/backend SSE parity test compares the frontend enum against a
  hand-transcribed copy of the backend constants in the same file
  (`frontend/src/shared/sse/schemas/__tests__/SseEnvelopeSchemas.test.ts`), so both sides go
  stale together and the guard cannot detect the drift it exists for — demonstrated when RFC
  0003 Stream 3 removed `created`/`updated`/`deleted` server-side and the test stayed green.
  The frontend listener/schema removal itself is RFC 0004 Stream 5's contract; this entry is
  only the guard's structural blindness, which survives that removal. A real guard reads the
  backend enum (generated artifact or shared fixture), not a transcription.
- 2026-08-14 — Two live docs still describe the planner sync SSE events removed by RFC 0003
  Stream 3: `docs/multi-region-request-paths.md` lists `created`/`updated`/`deleted` as the
  envelope-delivered family, and `docs/testing-evidence.md` quotes
  `rows.put(SseEventType.CREATED, ...)` as its matrix-test exemplar, a constant that no longer
  compiles. `shared/controller/SseController.java` line 35's `sync:planner` mention was stale
  before that stream landed and is the same sweep.
- 2026-08-14 — `KnownConstraint.PLANNER_BOOKMARK` still maps `planner_bookmarks` unique-key
  violations after RFC 0003 Stream 1 removed the bookmark write path; the application no
  longer writes that table, so the mapping is unreachable. The table itself also has no
  writer left — dropping both is one decision, and neither was in Stream 1's change list.
- 2026-08-14 — `docs/runbooks/schema-decomposition-migration.md` gates the deprecated-toggle
  retirement on `planner.legacy_toggle` reading ~0, but RFC 0003 Stream 1 removed the counter's
  last emitter, so the metric now reads *no data*, never zero — both gates are unactionable as
  written and the runbook needs its retirement condition restated (arguably: satisfied).
  `docs/multi-region-request-paths.md` §11 also still routes the deleted
  `POST /{id}/bookmark`, and `frontend/scripts/hardcoded-text-report.json` still carries an
  entry for the deleted `usePlannerBookmark.ts`. `PlannerMDGesellschaftPage.tsx`'s header
  comment still advertises bookmark functionality.
- 2026-08-14 — The content-digest lineage rule ("written only by onCreate and recordSave,
  never from the stored column") is enforced by review, not structure: `PlannerContent`'s
  class-level `@Builder`/`@Getter` expose a builder slot and getter for the field, and no
  ArchUnit/convention test pins the two legitimate write sites. A freeze test, or narrowing
  the builder, would make the invariant hold by construction.
- 2026-08-14 — `POST /api/planner/md/batch` (RFC 0003 Stream 1) bypasses `ByIdReadGuard`,
  which the single-planner GET goes through: on a lagging replica a just-written planner is
  silently omitted from the batch answer (indistinguishable from "not yours") instead of
  being re-checked on the primary, and a primary-deleted planner still on the replica is
  returned unmasked. Faithful to the RFC snippet; whether the sync client (RFC 0004) needs
  read-your-writes semantics from batch pull is an open design question for that RFC's
  consumer logic.
- 2026-08-14 — Two observer effects remain outside the RFC 0003 outbox: the account-suspension
  push (`moderation/listener/AccountSuspensionEventListener`, after-commit) and the settings
  invalidation published inline from `UserController`. Both lose their push if the process dies
  in the commit-to-listener window — the failure the outbox closes for the four planner/comment
  effects. Bringing them in means new `DomainEventType` values and arms: a future ADR, not a
  retrofit.
- 2026-08-14 — Outbox operational couplings accepted as specced by RFC 0003, worth revisiting
  together: the relay's ShedLock lease lives in Redis, so a Redis outage disables the component
  that exists to survive push loss (a JDBC lock provider would decouple recovery from the
  degraded dependency); application-wide `@EnableRetry` rides in `OutboxAsyncConfig`, so
  deleting the outbox would silently un-retry every SSE publish; `INSERT IGNORE` reports any
  suppressed error as "duplicate" (an FK violation from a hard-deleted recipient is
  indistinguishable from dedup — benign today because arm eligibility re-reads filter deleted
  users first); the eager executor's DiscardPolicy has no rejection counter, so pool
  saturation is observable only as relay-late notifications.
- 2026-08-14 — `CsrfDoubleSubmitFilterTest.mutation_WhenTokenTampered_Rejected` (line ~219)
  builds its tampered token as `"x" + token.substring(1)`, which is the identity
  transformation whenever the minted token already starts with `x` — the "tampered" request
  is then legitimately accepted and the test fails 200-vs-403, roughly a 1-in-64 flake per
  run. Found during RFC 0003 Stream 2's gate; pre-existing and independent of that work. Fix
  is to flip a character to a value guaranteed different (e.g. XOR or pick from a disjoint
  alphabet).
- 2026-08-14 — Outbox pushes leave on the dispatch thread after commit, so a client refetch
  triggered by NOTIFY_* carries no GTID from the write that caused it; on a lagging replica
  the refetch can miss the just-committed notification row until the next natural refetch.
  Self-healing and consistent with ADR 072's visibility bound, but the read-your-writes
  machinery (GtidWriteCapture) deliberately does not cover this path — worth a stated
  decision on whether notification reads should.
- 2026-08-14 — The reconciler's recommended_notification audit hash-joins full scans of
  domain_events and notifications nightly: domain_events indexes only (dispatched_at,
  created_at), no notifications index leads with content_id, and BIN_TO_UUID on the join
  expression forbids index use anyway. Small tables today; if either grows, add
  INDEX (aggregate_id, event_type) on domain_events and either use
  idx_notifications_planner(planner_id) (V029, BINARY(16) — NULL for pre-V029 rows) or add
  INDEX (content_id, notification_type). A pass outgrowing lockAtMostFor=PT10M would also
  let a second pod double-count planner_reconciler_drift_total.
- 2026-08-14 — catalogKeywordPairs() materializes one row per catalogued planner (UUID + two
  JSON strings) with no chunking, in the same read-only transaction that already holds
  visibleContentDocuments()'s full documents — and the content-side keyword column is
  redundant with that existing read. Fold the catalog column into the document query or chunk
  when planner counts warrant it.
- 2026-08-14 — editorStateCodec.ts:217 places a bare '' in a JSONContent slot: a genuine
  production type/runtime mismatch that stream 7b typed around in its test rather than fix
  (production out of that node's scope). Fix the slot's type or its value at the source.
- 2026-08-14 — buildSaveablePlanner pins id to a UUID via its schema, so the two planner page
  tests asserting on 'test-planner-123' cannot adopt the factory (six assertion sites). Either
  the tests move to schema-valid ids or the factory grows a test-id affordance.
- 2026-08-14 — the "planner type outside the union" branch in the planner config tests lost
  direct coverage when the bogus 'ABNORMALITY_ENCOUNTER' fixture became a valid
  REFRACTED_RAILWAY: the invalid case is now unrepresentable without a cast the lint rules
  forbid. If that guard matters, it needs a runtime-level probe (e.g. JSON ingest) instead of
  a typed fixture.
- 2026-08-14 — RFC 0003 gate debate on the recommended_notification audit, recorded with its
  revisit triggers. Three alternatives were argued and rejected: (1) moving the audit to a
  notification-side reconciler — rejected because the audit encodes the planner's latch
  semantics ("a set recommended_notified_at means a notification is owed"), and relocating it
  would embed planner business rules in the notification feature, a deeper SoC violation than
  the current observe-only raw-SQL read of the notifications dedup key (which is itself a real
  ArchUnit-invisible mechanism leak, pinned by the end-to-end IT); (2) splitting the vote from
  promotion+event to drop the audit — rejected as replacing a latch-keyed audit with a harder
  threshold-derivation liveness audit while adding a promotion component no requirement asks
  for; (3) replacing the latch with event-row existence — rejected because the CAS is the
  concurrency arbiter for which vote transaction records the event, and once-only semantics
  must not couple to domain_events retention (permanent domain state vs consumable ledger).
  Revisit trigger for (1): when cross-feature audits outgrow subject-owner placement, extract
  a shared audit home rather than per-feature reconcilers.
- 2026-08-14 — a refused write ack (requestDigest mismatch) is console.error-only; stream 4's
  classifier/presenter should surface it as a real failure instead of the editor reporting a
  clean save over a write whose lineage could not be established.
- 2026-08-14 — BATCH_PULL_MAX_IDS is hand-mirrored (frontend constants vs backend
  PlannerConstants) with nothing cross-checking the two; the RFC 0003/0004 wrap-up verification
  should compare them, and a contract test would hold thereafter.
- 2026-08-14 — PlannerSaveResult.hasLocalUnsavedChanges lost its last production reader when the
  beforeunload handler moved onto isDirty() (stream 6); the field and its two test assertions
  survive only because removal was not RFC-ordered. Fold into stream 8's usePlannerSave
  decomposition row.
- 2026-08-14 — a 404 on the comment SSE stream stops retrying silently (usePlannerCommentsSse
  passes no onStreamGone); the planner stream toasts. Inside stream 5's contract letter, but the
  "stop retrying and say so" prose is unmet for that consumer.
- 2026-08-14 — NoteEditor treats Tiptap's mount-time update as a local change: a bare mount fires
  one debounced onChange and briefly marks the planner dirty. Verified pre-existing (stream 6
  probe); matters to stream 4's error/success gating and to any future clean-state assertion.
- 2026-08-14 — RELEASE BLOCKER for RFC 0004: sync.removedOnAnotherDevice (planner ns) and the five
  errors.* keys (common ns) exist only as uncommitted edits in the static working tree; the
  submodule pointer on dev (b1c017c8) predates them, so both removal surfaces and the stream-4
  error copy render raw keys until static commits land and the gitlink bumps.
- 2026-08-14 — unverified premise: PlannerCommentSseController answers PlannerNotFoundException
  through the generic JSON handler while declaring produces=text/event-stream; the sibling
  handler hand-writes its response citing exactly that converter gap. If the 404 degrades to
  406/500, stopOnNotFound never fires and the comment stream burns its retry budget silently.
  One backend MockMvc case (404 on /api/planner/{id}/comments/events for an unpublished planner)
  settles it — backend session's lane.
- 2026-08-14 — the planner export silently drops rows whose local load failed: the success toast's
  count is truthful about the file but a partial export reads as a clean one. Reporting it needs
  copy that does not exist yet (stream 4 flagged; fold into the export decode→partition→persist
  split in stream 8).
- 2026-08-14 — the pagehide drain delivers into the in-memory store and arms a timer a discarded
  tab never fires: it rescues bfcache restores only, not true mobile tab discard. Synchronous
  persistence on pagehide would need a different storage strategy; note the commit prose
  overstates the coverage.
- 2026-08-14 — onServerReload's boolean cannot carry WHY a reload was refused, so the hook maps
  every refusal to {kind:'unknown'} and the user sees the specific toast plus a generic one.
  Becomes a real gap at the second refusal reason.
- 2026-08-14 — PlannerMDEditPage.tsx and editorStateCodec.ts coerce a missing note to the string
  '' under a field typed JSONContent — a value that violates its own declared type and seeded the
  stream 6 baseline bug. Normalizing to createEmptyNoteContent() at those two sites deletes the
  class; the downstream compensation in NoteEditor stands until then.
- 2026-08-14 — PlannerExportImportSection hand-rolls a third ConflictEffect executor (the import
  conflict flow) instead of the interpreter; it predates the interpreter and diverges (inherited
  published flag now patched pointwise). Migrating it needs the interpreter's sided forkCopy plus
  the section's progress UI wired as ops — fold into stream 8's export decode→partition→persist
  split.
- ArgoCD couples deployment to dev: any push or merge implies a rollout, which blocks
  routine backup pushes and forces implementation PRs to RFC granularity (one merge per
  RFC instead of per level). Move the deploy trigger to an explicit act — an annotated
  release tag ArgoCD tracks, or manifest/image-tag bumps only — so dev merges become
  safe at any cadence; that unlocks level-sized PRs and retires the
  push-to-an-unwatched-ref workaround.
- 2026-08-14 — the batch-conflict epoch can never exceed 1 in production (hasSyncedRef is
  never reset, one sync per PersonalPlannerList mount), so the multi-batch semantics it
  encodes are dead until a second sync trigger exists; if one is added, the empty→non-empty
  edge must become a batch-identity check or stale choices/outcomes leak across batches.
- 2026-08-14 — the batch-conflict park is not durable across remount: navigation away and
  back rebuilds the batch and pops the modal uninvited. Persisting the dismissal (per
  planner-id set) or opening parked batches collapsed would make park mean park.
- 2026-08-14 — stream 8's bookmark write-endpoint deletion (PlannerEngagementController + IT)
  is gated on the legacy-toggle counter reading zero in prod, which needs scripts/ops/access
  metrics — user's lane; the frontend deletions around it land independently.
- 2026-08-15 — the api.ts 401 cache-eviction still fires synchronously inside ApiClient.fetch
  (three tests pin the timing); hoisting it to the auth layer needs a registration seam since
  lib may not import @/shared/auth. The api↔queryClient cycle it caused is already broken, so
  this is now ergonomics, not architecture.
- 2026-08-15 — editorStateCodec still imports the ego spec list statically for maxThreadspin
  (unchecked cast included); injecting it requires deciding where the store provider sits
  relative to Suspense on four pages. Deferred by ruling; option (a) provider-inside-Suspense
  is the standing recommendation when picked up.
- 2026-08-15 — seasons/unitKeywords stay module-scope JSON in the main chunk by ruling; before
  any codegen-derived-literal move, MEASURE the two bundles' actual byte cost in the entry
  chunk — the row's value is bundle size and nothing else.
- 2026-08-15 — the branded numeric id schemas are defined but wire to nothing: current static
  data carries ALL value-role ids as strings (skills '2010111', passives ['2010111']), not
  just the two fields RFC 0004 named. The realignment is a static-pipeline change across every
  id emitter, then regeneration and a pointer bump — user's lane; the code-side brands and
  pattern derivations are ready and waiting.
- 2026-08-15 — extraction dead exports: the 16 production-dead exports stay exported because the
  stream-7 golden harness and 86 hand-written tests import them directly; the barrel never
  exposed them, so the public seam is already narrow. Un-exporting means the test-scale decision
  the user deferred.
- 2026-08-15 — six static-i18n hooks still hand-roll createStaticDataQueryOptions instead of the
  useEntityListData config path (useFilterI18nData, useSearchMappings, useSkillTagI18n,
  useSanityConditionData, useColorCodes, usePlannerKeywordsI18n); two of nine migrated before the
  pattern's marginal value flattened.
- 2026-08-15 — the passive-id/themePack numeric realignment row is REVERSED, not deferred: the
  pipeline settled on string serialization (static b290bb8b) and the branded-string design
  replaced the row's premise; recorded here so row-level accounting closes.
- 2026-08-15 — the api.ts 401-eviction hoist's stated verification (auth-layer key factory) is
  unreachable from lib/ under the layer rule; the literal ['auth','me'] at the eviction site is
  the residue. Needs the registration seam noted in the earlier eviction entry.
- 2026-08-15 — FeaturedBoss.unitId is still bare z.string(); the branded-ids rule-1 sweep missed
  it, and seven Number()/parseInt coercion sites survive in RecipeSection and IdentitySkillCard
  against rule 3.
- 2026-08-15 — NoteEditor.handlePaste policy is still inline; only its primitives moved to
  noteUtils. The policy extraction remains open.
- 2026-08-15 — jsx-a11y ships eight of nine rules; prefer-tag-over-role is off per ADR 083
  (four correct-ARIA reports the rule cannot express as native tags) — cross-reference, since
  the acceptance row names this ledger.
- 2026-08-15 — epic composition audit residue (RFC 0004), accepted as debt: suppressErrorToast/
  successParams are declared-but-unconsumed sink surface; NotificationToast's sonner import and
  toast.info sit outside the written toast law (exemption is real, law text lags); coverage
  thresholds are zero-margin measured values and CI runs the suite without the worker cap or
  NODE_OPTIONS the sibling jobs set; six text placeholders and LoadingState's hardcoded English
  sit beside content-shaped skeletons; String()/Number() id coercions survive in seven component
  sites plus StartGiftRow's number[] prop; EGOGiftSpec.themePack and FeaturedBoss.unitId lack
  brands (the latter needs a seventh primitive the RFC never defined); the gift enhancement
  prefix is restated inline in egoGiftEncoding; StartBuffSchemas patched the flip with
  z.coerce.number(); shared/noteEditor is a blanket deep-import exemption with nine deep
  importers and a barrel that exports neither components nor the registry; the pagehide drain
  lands text in the store a discarded tab never persists (probably irreducible); ADR
  stale-write-noop's REJECTED clause and the shipped ToleratedContentDigestSchema disagree on
  who owns the field's removal; reportFailure can displace a live conflict where resolutionError
  was built for exactly that; the two held-plan callers key by different identities; the comment
  SSE hook mixes throwing and safeParse idioms in one file.
- 2026-08-15 — `PLANNER_LIMIT_EXCEEDED` has no copy of its own in the i18n bundle and presents
  the generic error message, so a user who hits the server-side planner cap is not told what
  the cap is or that they hit one. The server carries the current count and the maximum only
  inside an English prose message, with no structured field a translation could interpolate.
- 2026-08-15 — `PlannerCardContextMenu.test.tsx` builds its duplicate-vote 409 with
  `CONCURRENT_WRITE`, a code that endpoint cannot emit: `CONCURRENT_WRITE` is written only from
  the optimistic-locking handler, and `PlannerContent` is the sole `@Version` entity. The codes
  a duplicate vote actually produces are `VOTE_ALREADY_EXISTS` and `DUPLICATE_ACTION`.
- 2026-08-15 — `KnownConstraint` omits `planner_comment_votes`, so a raced duplicate comment
  upvote falls to `UNEXPECTED_CONFLICT`: it answers the generic `CONFLICT` code and raises a
  Sentry alert, while the equivalent planner-vote and comment-report races are listed and
  resolve silently to `DUPLICATE_ACTION`.
- 2026-08-16 — The no-op arbitration predicate counts `planner_content.device_id` as a persisted
  field, because the upsert path stamps it and the biconditional invariant admits no field the
  write touches. The consequence is cross-device: device B resending content identical to what
  device A saved still gets a 409, since applying it would restamp the device. A caller sending
  no device cookie is worse off still — `DeviceIdResolver` mints a fresh UUID per request, so
  every one of its stale resends moves the column and can never be acknowledged. Whether the
  device stamp deserves to defeat an otherwise-identical write is a product question the
  arbitration design did not reach.
- 2026-08-16 — `PlannerContentDigestTest` was the only HTTP-level test asserting that
  `PlannerContentSanitizer` normalization survives the upsert endpoint. It went with the digest
  it was named for, and nothing replaced that assertion: sanitizer behavior is now covered only
  below the endpoint, so a regression in how the upsert path applies normalization reaches the
  wire untested.
- 2026-08-16 — The stale-write arbitration integration test pins one device cookie for the whole
  class, so two behaviors of the no-op predicate are code-verified but unexercised: the
  cross-device 409, where a second cookie resends identical content and is refused, and the
  cookieless case, where `DeviceIdResolver` mints a fresh UUID per request so no stale resend can
  ever be acknowledged. Both follow from `device_id` counting as a persisted field; neither has a
  test that would catch the day it changes.
- 2026-08-16 — No frontend test drives the stale-ack shape specifically: the client presents
  version N and the acknowledgement returns N+k with content unchanged. Ack adoption is covered
  only by the generic version-jump tests in `usePlannerSave.test.ts`, which do not distinguish an
  ack won by no-op arbitration from an ordinary forward version bump.
- 2026-08-16 — Six e2e app-suite specs (`mutation-gestures`, `note-save-gesture`) fail on the
  local rig: the planner page's IndexedDB open in `frontend/src/lib/storage.ts` dies with
  "upgrade blocked by another tab", so owner controls never render and every assertion that
  depends on them fails. The cause is upstream of the arbitration work — the failing page makes
  no planner network call before it dies, and the storage path is untouched by it — and traces
  to the device-free-key migration.
