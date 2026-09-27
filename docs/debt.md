# Debt

Parking lot for found work, pruned on every write. Not a queue: entries are captured so
they stop occupying attention, and are pulled only by a deliberate defrag or design
session. Each entry names the condition under which it becomes worth doing. An entry
leaves when the work landed, when its condition can no longer occur, or when a design
session judged it not worth doing; retirement is deletion, and the git history is the
record.

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
- `DegradationIT.evictPooledPrimaryConnections` still cannot evict the primary pool: the
  routing target for PRIMARY is the `GtidCapturingDataSource` wrapper, not a
  `HikariDataSource`, so the helper's instanceof walk skips it. Replica and bulkhead are
  covered since the lazy-proxy descent fix; the primary needs an unwrap step.
- No circuit breaker fronts any Redis role or the primary datasource; the 3 s command
  bound and Hikari's connection timeout cap what one call can hold, so under a dependency
  that stays slow rather than dead every request still pays the full bound before its
  typed 503. Worth doing once throughput under a sustained-latency toxic is measured
  (`DegradationIT` proves the cut and the blackhole, not the slow steady state) and shows
  request-thread saturation, or once production sees that state; the breaker sits on top
  of the bound, not in place of it.
- 2026-09-23 — Latency from Korea is the client→edge leg, not the origin. From a Korean ISP,
  `dante-planner.com` and `api.` land on SIN, HKG or NRT, while `www.cloudflare.com` on the
  same network answers from ICN: ICMP 107 ms against 10 ms, TCP connect 194 ms against 18 ms,
  warm-connection request→first byte 280–305 ms against 9.5 ms. On one reused connection,
  `/cdn-cgi/trace` (answered by the colo) and `published/{id}` differ by 4–25 ms, and an
  edge-cache HIT on a hashed asset costs the same ~300 ms, so the origin, the tunnel and
  Traefik add almost nothing and an edge cache buys no latency for these clients. A warm
  request costs about three ICMP round trips, which is unexplained. Why the zone lands
  off-shore is unverified: Cloudflare's docs attribute distant colos to anycast and ISP
  peering without naming plan tiers, and community reports of the same symptom include
  Argo-enabled zones. The same build loaded locally with API responses replayed from fixtures
  reaches first content on the detail page in 3.9 s at an emulated 300 ms round trip, 1.6 s at
  100 ms, 0.48 s at 10 ms and 0.46 s at 0 ms, so at this zone's round trip nearly all of the
  load is waiting, and at an in-country round trip the waterfall barely matters. A repeat visit
  in production serves every chunk from the browser cache and still takes 1.2–1.4 s, all of it
  round trips (HTML revalidation and API calls). The zone is on the Free plan. Community and
  wiki sources (no Cloudflare statement found) say Korean visitors reach ICN only on Enterprise,
  and Cloudflare's own blog puts Seoul transit at 15× Europe with 2% of Korean traffic peered.
  From the same Korean ISP, CloudFront (ICN57, 17 ms connect), Bunny (KR1, 20 ms), Fastly (ICN,
  35 ms) and Cloudflare's own Enterprise site (ICN, 15 ms) all answer in-country, while this
  zone answers from HKG at 299 ms. CloudFront's flat-rate plans include every edge location; the
  zone served 20.9 M requests and 480 GB in 30 days, which is Business-tier volume ($200 a
  month), and Korea was 13% of those requests (US 18%, Vietnam, Thailand, the Philippines and
  Indonesia about 25% together). Three ways to serve Korea in-country: Cloudflare Enterprise
  (price not public); every visitor on CloudFront's flat-rate Business plan ($200 a month for
  this volume; staying over a plan's allowance may move traffic to "fewer or more distant edge
  locations"); or Korea alone on an in-country CDN (about 2.8 M requests a month, inside
  CloudFront Pro's 10 M at $15), which needs per-country DNS routing that Cloudflare DNS on the
  Free plan may not provide (unverified). The API hostname sits behind the same Cloudflare
  tunnels, Access and load balancer, so moving it too reopens ADR 011 and ADR 091. Worth doing
  when Korean users are a stated priority, weighed against ADR 115's fewer round trips, which
  help every region.
- `published/{id}` p99 on Seoul is ~4 s while p50 is ≤1 ms (fleet dashboard, 2026-09-16).
  Uninvestigated. Candidates: a handful of JIT-cold requests after a rollout dominating a
  low-traffic p99, GTID-gated reads waiting on replica catch-up before falling to the
  primary, replica-miss re-checks over the WAN, or GC pauses on the 2 GB hosts. Worth
  doing once the request count behind the percentile is known and shows the tail is more
  than a few requests, or when a user reports a stalled detail load.
- The load balancer's cookie session affinity (`__cflb`) is honored regardless of the
  client's geography: a cookie issued in one region routes later requests to that
  region's pool from anywhere. Harmless today (identical code in both regions, writes
  always reach the single primary) and useful as a measurement instrument (see the entry
  above). Becomes a decision when regional behavior ever diverges, when affinity is
  reconsidered, or when a per-region hostname is wanted in prod — the cookie already
  provides the pin without a public bypass of health-based failover.
- The `FeatureBoundaryTest` frozen internal edges (34 entries on 2026-09-18) resolve to
  eight types reached across a boundary, and thaw in three lanes. Mechanical, one commit
  each, the staleness test deletes the entry: `WebConfig` reaches `planner.entity.MDCategory`
  for a converter that Boot would register itself as a planner-owned `Converter` bean;
  `CommentEngagementService` reaches `planner.validation.VoteUniquenessValidator`;
  `AdminService` builds a `moderation.entity.ModerationAction` that a moderation `record`
  operation should own. Seam design, an RFC each: two moderation services mutate
  `comment.entity.PlannerComment` and call `comment.validation.CommentStateValidator`, so
  comment needs hide, delete and restore operations for moderation; four moderation and
  comment classes read `planner.entity.Planner`, so planner needs a moderation read model and
  an access check comment can call. Worth doing when the owning feature's service is next
  edited for another reason.
- `user.entity` is a de facto shared domain model: `User`, `UserRole`, `RestrictionState`
  and `UserSettings` are read by 24 frozen origins across every feature, including
  `shared.security` and `auth.token`. The enum half (7 origins reach only `UserRole` or
  `RestrictionState`) is a mechanical move once a shared account vocabulary exists. The
  `User` half (17 origins) is not: `Planner` maps `User` as a JPA association, so the
  alternative is id-valued references across aggregates, a schema and query change. Closing
  it means deciding what a planner knows about its owner, the row or the id — a shared
  kernel the rule permits by design, or the ADR 015 decomposition applied to users. Argue
  that before moving anything; a thaw done to satisfy the rule is the wrong reason.
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
  (`ThemePackSelectorPane.getDifficultyColor`, `StartBuffCardVariant.description.color`).
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
  drift storm is invisible in production. 2026-09-22: neither passes the remote-write
  keep-list in `deploy/base/prometheus.yaml:69` either, nor do `sse.publish.unserializable`,
  `datasource.primary.undeclared`, `tombstone.check_skipped`; five of the ten custom
  counters never reach Grafana Cloud, so a panel alone would not fix it. The planned `sse.publish.unserializable` will join
  them. Closes when all three have a dashboard panel and an alert rule.
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
- 2026-08-14 — BATCH_PULL_MAX_IDS is hand-mirrored (frontend constants vs backend
  PlannerConstants) with nothing cross-checking the two; the RFC 0003/0004 wrap-up verification
  should compare them, and a contract test would hold thereafter.
- 2026-08-14 — the planner export silently drops rows whose local load failed: the success toast's
  count is truthful about the file but a partial export reads as a clean one. Reporting it needs
  copy that does not exist yet (stream 4 flagged; fold into the export decode→partition→persist
  split in stream 8).
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
- 2026-08-15 — extraction dead exports: the 16 production-dead exports stay exported because the
  stream-7 golden harness and 86 hand-written tests import them directly; the barrel never
  exposed them, so the public seam is already narrow. Un-exporting means the test-scale decision
  the user deferred.
- 2026-08-15 — six static-i18n hooks still hand-roll createStaticDataQueryOptions instead of the
  useEntityListSpec/useEntityListI18n config path (useFilterI18nData, useSearchMappings, useSkillTagI18n,
  useSanityConditionData, useColorCodes, usePlannerKeywordsI18n); two of nine migrated before the
  pattern's marginal value flattened.
- 2026-08-15 — the api.ts 401-eviction hoist's stated verification (auth-layer key factory) is
  unreachable from lib/ under the layer rule; the literal ['auth','me'] at the eviction site is
  the residue. Needs the registration seam noted in the earlier eviction entry.
- 2026-08-15 — FeaturedBoss.unitId is still bare z.string(); the branded-ids rule-1 sweep missed
  it.
- 2026-08-15 — NoteEditor.handlePaste policy is still inline; only its primitives moved to
  noteUtils. The policy extraction remains open.
- 2026-08-15 — jsx-a11y ships eight of nine rules; prefer-tag-over-role is off per ADR 083
  (four correct-ARIA reports the rule cannot express as native tags) — cross-reference, since
  the acceptance row names this ledger.
- 2026-08-15 — epic composition audit residue (RFC 0004), accepted as debt: NotificationToast's
  sonner import and toast.info sit outside the written toast law (exemption is real, law text lags); coverage
  thresholds are zero-margin measured values and CI runs the suite without the worker cap or
  NODE_OPTIONS the sibling jobs set; six text placeholders and LoadingState's hardcoded English
  sit beside content-shaped skeletons; String()/Number() id coercions survive in seven component
  sites plus StartGiftRow's number[] prop; EGOGiftSpec.themePack and FeaturedBoss.unitId lack
  brands (the latter needs a seventh primitive the RFC never defined); the gift enhancement
  prefix is restated inline in egoGiftEncoding; StartBuffSchemas patched the flip with
  z.coerce.number(); shared/noteEditor is a blanket deep-import exemption with nine deep
  importers and a barrel that exports no components; reportFailure can
  displace a live conflict where resolutionError was built for exactly that; the two held-plan callers key by different identities; the comment
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
- 2026-08-16 — `PlannerContentDigestTest` was the only HTTP-level test asserting that
  `PlannerContentSanitizer` normalization survives the upsert endpoint. It went with the digest
  it was named for, and nothing replaced that assertion: sanitizer behavior is now covered only
  below the endpoint, so a regression in how the upsert path applies normalization reaches the
  wire untested.
- 2026-08-16 — No frontend test drives the stale-ack shape specifically: the client presents
  version N and the acknowledgement returns N+k with content unchanged. Ack adoption is covered
  only by the generic version-jump tests in `usePlannerSave.test.ts`, which do not distinguish an
  ack won by no-op arbitration from an ordinary forward version bump.
- 2026-08-16 — Comment content is HTML at rest by frontend convention alone: `CommentEditor`
  submits `editor.getHTML()` into `planner_comments.content` (`TEXT`, V015), and the backend
  acknowledges the format at exactly one site — `Notification`'s
  `truncate(stripHtml(...), 100)` snippet. Notes carry the opposite guarantee
  (`planner_content.content` is a MySQL `JSON` column, format DB-enforced since the first
  save), so the asymmetry is comment-only. Moving comments to tiptap-JSON-at-rest is
  therefore a real migration, not a flag flip: a `TEXT` column with no DB-side validation,
  no server-side HTML parser to convert with (an offline pass would need one; the
  alternative is lazy migrate-on-write through tiptap's polymorphic `setContent`), and
  coordinated changes to `CommentCard`'s sanitize-and-inject render, the `sanitizeUserHtml`
  allowlist, and `stripHtml`. No current requirement forces the move; this entry prices the
  coupling so a future format change is proposed as the project it is.
- 2026-08-16 — Every compose flavor (default+override, oauth-gtid, multiregion, e2e, loadtest)
  runs under the same default project name, so they all mount the one
  `limbusplanner_mysql-data` volume. MySQL applies env credentials only on first init of an
  empty datadir, so switching flavors leaves the datadir on the previous stack's passwords
  and the backend fails at Flyway with error 1045. Found work: isolate the flavors — a
  distinct volume name or an explicit `name:`/`COMPOSE_PROJECT_NAME` per flavor — so a stack
  switch cannot poison dev credentials.
- 2026-08-16 — Dead client surface found by coverage audit: `plannerApi.import` (no call
  site), the `settings:invalidated` SSE event constant (declared in `lib/constants/api.ts`,
  no registered handler), `usePlannerStorage.clearCorruptedLocal` (no consumer), and the
  `my-plans` branch of `PlannerCardContextMenu` (never mounted).
- 2026-08-16 — The pull pass counts purges it did not verify (`purgeLocalPlanners` discards
  `deleteLocal`'s Result) and a failed background sync marks the session synced with no
  retry and no indicator (`hasSyncedRef` set on error; the `isSyncing` consumer is a TODO in
  `PersonalPlannerList.tsx`).
- 2026-08-16 — IndexedDB planner rows carry no account identity, and the tombstone-only purge
  (ADR 088) removed the accidental cleanup the old absence heuristic performed: on a shared
  device, account B's list renders account A's rows indefinitely, and B's pull pass will
  never remove them (their ids are absent from B's listing, which now means keep). Scoping
  local rows by account — or clearing on identity change — is a design decision the sync
  model has never made; until it is, "My Plans" on a shared device is a union of every
  account's local saves. Argued 2026-08-16 and deferred by ruling: the sync-choice dialog
  now carries a shared-device warning as the standing mitigation. The argued-to shape when
  this resumes: stamp rows with the syncing account at pull/ack time (field name must avoid
  `userId`, which the legacy-key tolerance drops on read), filter "My Plans" to
  current-account plus unstamped rows, keep-and-hide on identity change, adoption only
  through the account's own saves; namespaces (blocks cross-account sharing and the guest
  upgrade) and a per-plan adoption dialog (adoption-by-copy duplicates plans in the
  logged-out view) were both killed in the debate, and unstamped-row visibility is the one
  open sub-question.
- 2026-08-16 — `stack:down` composes only the base file set, so after `stack:up:rig` it
  orphans four running services (`backend-oregon`, `mysql-replica`, `oauth-stub`,
  `toxiproxy`) — verified live by diffing `config --services` against running containers.
  Add a `stack:down:rig` mirroring the rig's file set, or `--remove-orphans` to
  `stack:down`.
- 2026-08-16 — Every planner update response serializes `lastModifiedAt` before `@PreUpdate`
  stamps it at flush, so the wire value undersells the stored one by the flush skew (~50ms
  observed) and the client adopts the stale value into IndexedDB. Harmless today (nothing
  compares the two), but any future logic trusting response `lastModifiedAt` against a read
  inherits a guaranteed mismatch; `noop-arbitration.contract.spec.ts` documents the shape by
  asserting on reads instead.
- 2026-08-16 — The unread badge can stay wrong for five minutes after a real-time push,
  reproduced on the rig: `notify:comment` invalidates and the refetch races replication (the
  notification row committed on the primary ~40ms earlier, the Seoul replica answered
  `{"unreadCount":0}`), the answer marks the query fresh, and `useUnreadCountQuery`'s
  `staleTime: STALE_TIME.MEDIUM` (5 min — the constant's own comment files notification
  inbox under SHORT) blocks every focus heal for the window. No ryw_gtid protects the read:
  the write was server-side, so the client never held a cookie for it. Candidate fixes are a
  design choice (carry state in the push against the SSE-invalidates-only convention, pin
  notification reads to the primary, or shorten the staleTime and accept the focus heal);
  `notification-push.spec.ts` asserts the push-driven refetch and the fresh-mount render, and
  should grow the push-then-badge assertion once this is decided.
- 2026-09-20 — An SSE reconnect refetches nothing for notifications: `useAppSse.ts`
  `handleConnected` invalidates only `userSettingsKeys.settings()` on a re-open, so a
  notification committed while the stream was down (the dispatcher's post-commit push is
  fire-and-forget) stays unseen until `STALE_TIME.MEDIUM` lapses or the page remounts. The
  dispatched row is durable, so nothing is lost; the delivery delay just has no upper bound
  tied to the reconnect. Worth doing when the badge-after-push entry above is decided (the
  fix is the same invalidation call in the reconnect branch, plus a reconnect assertion in
  `notification-push.spec.ts`), or when a user reports a missed notification after a
  network blip.
- 2026-08-17 — The prod-account RDS move cannot share the snapshot as-is:
  `terraform/rds/main.tf` sets `storage_encrypted = true` with no `kms_key_id`, so the
  instance encrypts under the AWS-managed `aws/rds` key, and AWS refuses to share
  snapshots encrypted with an AWS-managed key across accounts. The documented path is
  copy-with-CMK in the management account, share the CMK and the copy to
  danteplanner-prod, and restore there (which re-encrypts again under a prod key) — two
  extra full-storage snapshot copies of window time that `prod-account-rewire.md` §7's
  "snapshot-share-and-restore" line does not mention. The staging drill the runbook
  mandates would hit this on its first attempt; cheaper to provision the CMK and script
  the copy before the drill.
- 2026-08-17 — ADR 082 declares it supersedes 071's `@sync @digest` bullets "once the
  arbitration is implemented", but carries no `supersedes:` key and left 071 with no
  `## Superseded` section. The condition is met — `SyncVersionValidator.arbitrate` and
  `EffectiveNoOpPredicate` are live, `V060` dropped `content_digest`, and no Java or TS
  outside those two migrations references a digest — so 071 reads as a live decision for a
  column that does not exist, and the repo's own convention (status derived from structure)
  agrees with the wrong side. `docs/rfcs/0003-sync-identity-and-effect-delivery.md` and
  `0005-server-noop-conflict-arbitration.md` still show the column as target state.
- 2026-08-17 — Planner moderation authorizes on the URL role rule alone:
  `SecurityConfig.java:134` gates `/api/moderation/**` on `hasRole("MODERATOR")`, and
  `PlannerModerationService` (`:49`, `:69`, `:90`, `:113`) reaches
  `plannerPublishingService` with no actor lookup, so a restricted moderator keeps takedown,
  unpublish and recommended-listing authority until their token expires. Account restriction
  goes through `ModerationPolicy.requireCanRestrict`, whose own comment
  (`ModerationPolicy.java:78-84`) states a restriction withdraws authority precisely because
  banning a moderator invalidates no token — a rule the planner endpoints never consult.
- 2026-08-17 — `PlannerPublishingService.java:100` and `:133` pass
  `upserted(userId, plannerId, content)` as an argument, so the whole upsert — field
  mutation, `recordSave()`'s version bump, `onVisibleEditCommitted`, and `createAggregate`
  for an id the server has never seen — runs before `applyPublish` reaches `requireOwner`
  (`:154`), `requireTitle` (`:156`), the `ValidationPolicy.PUBLISH` content gate (`:157`) and
  the takedown refusal in `Planner.publish()`. The javadoc at `:149-150` claims the opposite
  ("a refusal leaves the planner in the state the caller found it in rather than relying on
  the rollback"). A stale `syncVersion` on a taken-down planner answers 409 from arbitration
  before the 403 the state warrants, which opens the client's conflict dialog for a planner
  that can never publish.
- 2026-08-17 — `PlannerFilterService.onFilterRebuildRequested` (`:53-55`) is an
  `AFTER_COMMIT` listener writing `planner_entity_filter` / `planner_keyword_filter` under
  `REQUIRES_NEW` — the shape ADR 072 names and rejects ("REJECTED: `REQUIRES_NEW` writes from
  after-commit listeners — the crash window above, with no durable record to retry from").
  A crash between commit and listener leaves a published planner absent from entity and
  keyword search with no outbox row, no relay, and no alarm; only a manual
  `CALL rebuild_planner_filters(...)` recovers it. `EffectPlacementTest.java:184-197`
  implements the dispatcher direction of the ADR's rule (listeners reaching the dispatcher
  are exactly the eager hop) but not its converse, so this listener passes; `:199-221`
  likewise enforces "within the notification package" where the ADR says "only by the
  dispatcher".
- 2026-08-17 — The restriction gate ADR 078 describes as following the planner's public state
  exists only in `PlannerCommandService.upsertAggregate` (`:356-358`). `deletePlanner`
  (`:466-490`) has none while explicitly unpublishing a published planner (`:472-475`) and
  dropping its catalog row (`:478`), so a banned user can still move a planner out of public
  view; `importPlanners` (`:503`) and `createAggregate` (`:267`) call `getUser` rather than
  `checkNotRestricted`. The unreachable `updatePlanner` (`:424-457`, no controller route)
  carries no gate and a `CarriedWrite` missing `gameContentVersion` and
  `contentSchemaVersion`, so two conjuncts of the no-op predicate are unconditionally true
  there the day a caller is added.
- 2026-08-17 — ADR 085 states the device stamp is compared "like every other field the write
  path stamps" and that "the biconditional invariant admits no exception", but
  `PlannerPublishingService.java:136-140` routes publish/unpublish-with-body through the
  3-arg `upsertAggregate` overload, which hardcodes `deviceId = null`
  (`PlannerCommandService.java:330`); `applyKeywordsAndDeviceId` then skips the stamp and
  `EffectiveNoOpPredicate.java:50` reads a null carried value as unchanged. The same stale
  cross-device resend is a 409 through `PUT /{id}` and an `ACK_NO_OP` through
  `POST /{id}/publish`, so that client is handed another device's version as an
  acknowledgement of its own write.
- 2026-08-17 — Several find-then-insert paths rely on a real database constraint without the
  handled violation ADR 023 pairs it with, so the race answers `UNEXPECTED_CONFLICT` plus a
  Sentry page instead of a domain outcome: `RecommendedSql.java:42-55` inserts into
  `planner_catalog` with no `IGNORE`/`ON DUPLICATE KEY` against its primary key (reached from
  `PlannerCatalogService.java:88` on unban), `UserSettingsService.java:91-94` (reached from
  login and from every SSE connect), `PlannerSubscriptionService.java:79-85`, and the bare
  `planner_stats` inserts at `PlannerCommandService.java:279-281` and `:520-522`.
- 2026-08-17 — "Left public view" writes a replica tombstone on one path only. Owner
  soft-delete registers one after commit and, when no transaction is active, writes it inline
  before commit (`PlannerCommandService.java:479-488`) — a pre-commit cross-store effect whose
  rollback leaves an hour-long tombstone for a live planner, and `ContentTombstoneStore.java:62-65`
  states a positive tombstone is the only gate on a replica hit. Moderator takedown and
  unpublish (`PlannerPublishingService.withdrawFromPublicView`, `:213-219`) write none, so a
  taken-down planner is served unmasked from a lagging replica for the replication window.
- 2026-08-17 — The comment and reply effect arms re-read by bare id rather than with the raise
  site's predicate: `CommentCommandService.java:60,62` gates the write on
  `checkNotRestricted` plus `checkPublished`, while `CommentReceivedEffect.java:48-60` and
  `ReplyReceivedEffect.java:46-58` check only `isDeleted()`. Dropping the published predicate
  is deliberate for the owner notification and documented at
  `PublishedPlannerQueryService.java:99-112`, but the same arms also push `COMMENT_ADDED` to
  the planner's public comment channel, which the raise site would have refused; and
  `ReplyReceivedEffect.java:68-70` loads the parent comment by bare id with no `isDeleted()`
  check, so a withdrawn parent still receives its reply notification.
- 2026-08-17 — A local draft kept through a tombstone can never be saved again. ADR 088 keeps
  it (`syncPlan.ts:57-60` purges only non-draft rows), the server has no resurrect path —
  `findAggregateForOwner` filters `deletedAt` (`PlannerRepository.java:65`), the pre-create
  probe does not (`:131-132`), and `PlannerOwnershipValidator.java:45-49` answers 404 for the
  owner's own tombstone, with `deleted_at` never cleared anywhere — and `performSave`
  (`usePlannerSave.ts:477-489`) returns before `storage.saveToLocal` on failure, so the manual
  write is discarded too and `hasUnsyncedChanges` stays true. The 404 classifies as
  `notFound` and presents as the generic resource-not-found message; whether the client should
  drop the row, unlink it, or offer a re-create is undecided on both sides.
- 2026-08-17 — `status` is read as a per-device local fact by `syncPlan.ts:36,58` and
  `deriveSaveStatus`, and as a replicated column by the wire (`usePlannerSyncAdapter.ts:158`,
  adopted on every pull at `:72`, compared by `EffectiveNoOpPredicate.java:40`). The two
  readings diverge inside one interpreter: `conflictChoice.keepLocal` (`:168-175`) pushes the
  operand as-is and stamps `saved` only locally, and the list caller supplies a row whose
  status is `draft` by construction (`useMDUserPlannersData.ts:365-374`) where the editor
  caller supplies `saved` (`usePlannerSave.ts:695-696`). A batch Keep-Local therefore writes
  `draft` to the server, and every other device pulls a draft it never authored — permanently
  conflict-raising and exempt from the ADR 088 purge.
- 2026-08-17 — Applying the latest mirror decouples content from the version that names it,
  against the invariant stated at `usePlannerSyncAdapter.ts:47-55` ("keeping local bytes under
  the server's version would hide that divergence from a sync that compares versions alone").
  `usePlannerHeaderActions.ts:110-121` keeps local draft bytes and adopts the ack's version,
  and `syncPlan.ts:35` compares versions alone, so the row reads `skip` forever. On the
  community route the pushed object is the server's published copy rather than the owner's
  local one (`PlannerMDGesellschaftDetailPage.tsx:131` against the prop documented at
  `PublishedPlannerHeader.tsx:51`), so a second device can receive published content stamped
  with a `contentVersion` its bytes were never validated against.
- 2026-08-17 — `BatchConflictDialog` hardcodes the local side as the copy — row labels read
  `conflict.localPlanner.metadata.title` (`:282`) and the "copy will not be published" notice
  gates on the local row's flag (`:297-301`) — while the import caller plans with
  `forkSide: 'incoming'` (`PlannerExportImportSection.tsx:366-375`) and forces `published:
  false` on the imported side (`:404`). Importing a published planner over a local
  unpublished row silently unpublishes the copy without the notice, and warns about a copy
  whose publication was never at stake; the comment at `:281` also claims a "Server" →
  "Imported" relabel the dialog never performs (`BatchConflictDialog.tsx:292-294`). The same
  path applies three of `ConflictForkMetadata`'s eight fields (`:396-406` against
  `conflictChoice.ts:43-53`), so an imported Keep-Both copy inherits the source's timestamps
  and `status`, sorting into the list at the original's position and landing as a
  tombstone-immune draft under a fresh id.
- 2026-08-17 — `presentedVersion` is forward-only by construction
  (`usePlannerSave.ts:335-344`), but the server moves a version backwards on create:
  `UpsertPlannerRequest.withId` drops the requested value and `PlannerContent` defaults to 1,
  so a client sending 12 for an unknown id is answered 1 with no error. `adoptAck` assigns it
  correctly and `presentedVersion` maxes it back up against the stale prop, so the next save
  presents an old version against a row at 1 and takes a 409 for a planner nobody else
  touched. Reachable through any id the server has re-created, and through the import fork
  above.
- 2026-08-17 — ADR 073 removed the SSE planner event family on the grounds that
  "`refetchOnWindowFocus` on the server-backed planner queries becomes the freshness
  mechanism in their place", but every personal-planner query disables it
  (`useMDUserPlannersData.ts:203,215`, `useSavedPlannerQuery.ts:42`) because the source is
  IndexedDB. The only server read on that path is the one-shot sync effect
  (`useMDUserPlannersData.ts:222-288`) gated per mount by `hasSyncedRef`, and the personal
  detail route reads IndexedDB alone (`PlannerMDDetailPage.tsx:57`), so the promised "a delete
  performed on another device surfaces as a 404 when the stale entry is opened" happens only
  on the community route. Freshness is per-mount, not per-focus, on exactly the path SSE
  covered.
- 2026-08-17 — The denormalized keyword column is written verbatim from the client's copy
  (`UpsertPlannerRequest.java:51` → `PlannerCommandService.java:192-196`), which ADR 030
  records as rejected ("derived server-side from the content tree… REJECTED: accepting a
  client-supplied projection alongside its source"). A null means "leave unchanged"
  (`:193`), so a save carrying a changed content tree without the copy strands the column;
  and nothing can detect it, because the drift audit takes its keyword oracle from the stored
  column itself (`PlannerDriftReconciler.rebuildExpectedIndexes`,
  `PlannerDriftAuditRepository.java:218-219`) rather than from the content JSON.
- 2026-08-17 — ADR 030 also records that an unknown keyword is "rejected only when
  publishing and tolerated on a draft sync", but `PlannerKeywords.normalize` (`:75-88`) drops
  unknowns at every tier and `PlannerContentValidator` inspects keywords under no
  `ValidationPolicy`, `PUBLISH` included. Publishing with an unknown keyword succeeds and
  discards it; the two-tier rule survives only in the client, which the same ADR justifies as
  the sole defense for guest documents alone.
- 2026-08-17 — `KeywordParityTest.java:64-71` pins `PlannerKeywords.VALID_KEYWORDS` to the
  members of the latest `ALTER TABLE planners MODIFY COLUMN selected_keywords SET(...)`
  migration, but `V052:116` dropped `planners` and the live column is
  `planner_content.selected_keywords JSON` (`V049:29`). The test passes against a fossil
  (`V045`), and adding the next keyword by the procedure it implies means writing a migration
  against a missing table — green test, failed deploy. ADR 030 also justifies its filtering
  rule as forced by a column type that "physically rejects non-members", which stopped being
  true at V049.
- 2026-08-17 — The base-gift collapse is expressed three times and the frontend copy
  disagrees with the two backend copies on ids outside the enhancement bands:
  `PlannerContentEntityExtractor.java:129-132` and `V055__normalize_ego_gift_filter_ids.sql:50-52`
  pass them through and index them, while `egoGiftEncoding.ts:29`'s
  `^([12])?(9\d{3})$` drops them. The same filter term answers differently on the published
  list (server index) and in "My Plans" (local predicate,
  `plannerContentExtractors.ts:199-203`), and each side is pinned by tests to its own
  contract. ADR 039 names this population directly — the seed carries gift ids outside every
  valid band.
- 2026-08-17 — `PlannerCoreInfo.absent()` yields a null `createdAt`
  (`PublishedPlannerQueryService.java:219` uses it whenever the batch core load misses a row)
  and the global `NON_NULL` Jackson config omits the key, but `PlannerListSchemas.ts:48`
  declares `createdAt` required, so one catalog row without a core row rejects the entire
  paginated community page rather than one card. The sibling fields on the same DTO are
  correctly `.nullish()` (`:44,46`) and the components already tolerate an absent value.
- 2026-08-17 — `AbEventSchemas.ts:15-16` types `relatedEgoGifts` as `z.array(z.string())`
  rather than `EGOGiftIdSchema`, and 13 ids in `static/data/abEventSpecList.json`
  (`991001`–`993004`, six digits) violate `GIFT_ID_PATTERN`. `EGOGiftGrid.tsx:34-35` renders
  nothing for them, silently; the branded schema would have failed `dataIntegrity.test.ts` on
  the file instead. `giftListItem.ts:28` parses the same values through `EGOGiftIdSchema` and
  would throw.
- 2026-08-17 — `usePlannerFork.ts:111` still parses the published payload with an unguarded
  `JSON.parse` and assembles it with `toSaveablePlanner` (`:115-135`), skipping the
  `validateSaveablePlanner` gate its sibling `usePublishedPlannerQuery.ts:106-133` gained, then
  writes the unvalidated content to local storage at `:138`. `usePlannerSyncAdapter.ts:61-68`
  guards the parse but likewise skips the schema gate.
- 2026-08-17 — Backend content validation refuses string-form buff ids:
  `StartBuffValidator.java:41` iterates `selectedBuffIds` with `eachNumber`, whose
  `!element.isNumber()` check (`JsonTraversal.java:119-121`) rejects a textual element, while
  the sibling `selectedGiftIds` goes through `eachUniqueString` (`:85`). Any move of buff ids
  to the branded string form the rest of the entity ids use is therefore a two-sided
  migration with a hard ordering constraint — the server must accept both forms before any
  client writes strings, and numeric acceptance has to survive until a `contentSchemaVersion`
  rewrite retires stored numeric arrays, since stored content re-enters validation on a
  category change.
- 2026-08-17 — `window-schema-decomposition.yml` drains the regions in the wrong order.
  Its freeze job loops `"$OREGON_REGION:oregon" "$SEOUL_REGION:seoul"`, so Oregon is
  drained first, while `docs/runbooks/schema-decomposition-migration.md` B1 step 2 states
  the opposite and says why: "Freeze Seoul before Oregon. Seoul writes cross-region into
  Oregon's primary, so stopping the writer before its target leaves no half-applied
  cross-region work." As written the workflow stops the target before the writer, leaving
  a window where Seoul pods are still accepting writes aimed at an Oregon primary whose
  own pods are gone. The bring-up order (Oregon, then Seoul) is correct and matches B4 —
  only the drain loop is reversed. Fix is reversing that one loop; the same ordering must
  hold in any local port of the workflow.
- 2026-08-17 — The stop-the-world drain uses a node label value Kubernetes rejects, so the
  production window fails at its first step. `docs/runbooks/schema-decomposition-migration.md`
  B1 and `.github/workflows/window-schema-decomposition.yml` both patch the backend
  DaemonSet with `nodeSelector: {role: "__migrating__"}`, and the API server refuses it:
  a label value must begin and end with an alphanumeric character, which `__migrating__`
  does not. Reproduced against the prod-account fleet: "The DaemonSet backend is invalid:
  spec.template.spec.nodeSelector: Invalid value: \"__migrating__\"". The workflow is
  dispatch-only and has never run, which is why this survived. Any value that is legal and
  matches no real node label works — the nodes carry role=app / data / ingress — so
  `migrating` is enough. Four call sites: runbook lines 243 and 248 (the second is the gate
  that greps for the value), workflow lines 96 and 108.
- 2026-08-20 — The nightly terraform drift check has never been able to run, and its
  schedule is now off until it can. Three independent blockers. (1) Credentials: the job
  assumes `secrets.AWS_PROVISIONER_ROLE_ARN` while declaring no `environment:`, so it mints
  the OIDC subject `repo:phrimm136/dante-planner:ref:refs/heads/main`; the prod account's
  `prod-provisioner` trusts `repo:phrimm136/dante-planner:environment:production`, and the
  run fails with "Could not assume role with OIDC: Not authorized to perform
  sts:AssumeRoleWithWebIdentity". Declaring `environment: production` would satisfy the
  trust but attaches that environment's required reviewer, which an unattended cron cannot
  clear — an ungated environment plus a matching `sub` condition on the role is the shape
  that works. (2) Inputs: `TFVARS_OREGON`, `TFVARS_SEOUL`, `TFVARS_RDS` and `TFVARS_SECRETS`
  do not exist at repository scope or in any environment, and the workflow's own note
  explains the consequence — "a partial set is worse than none: variables that gate a
  resource default to empty, so a plan missing one reports the resource's destruction as
  drift". (3) Account: the workflow writes `backend.hcl` as
  `danteplanner-tfstate-${AWS_ACCOUNT_ID}`, which since the account cutover resolves to the
  prod bucket, so the stack list has to describe prod and only prod. Applied here:
  `global-accelerator` dropped from `STACKS` because it lives in the management account and
  holds the rollback addresses for the bake, and `cloudflare` added, which needed workspace
  support in `scripts/ops/terraform-drift-check.sh` — that stack keeps a `prod-fleet`
  workspace alongside `default`, and planning without selecting reports the whole stack as
  absent. Re-enabling the cron needs the four secrets populated with prod values and the
  credential path settled.
- 2026-08-20 — infra-suite's `edge-suites` is disabled: it cannot reach the staging origin,
  and the staging edge it drives is unmanaged. Four defects were cleared before the fifth
  proved blocking. (1) `STAGING_PROVISIONER_ROLE_ARN` existed nowhere, so the job could not
  assume a role; set as a `staging` environment secret. (2) The staging provisioning role
  granted `secretsmanager:GetSecretValue` on the RDS master password alone, so both
  `danteplanner/cloudflare/tunnel-tokens` and `danteplanner/staging/e2e-endpoints` were
  denied; fixed in `terraform/iam-bootstrap` and applied, which also carried in the
  Route53InternalZone and SsmDocuments statements from ee915a74 that staging had never
  received — it was last applied 2026-07-30, the day before that commit. (3) The readiness
  poll issued a bare curl while the suites send a Cloudflare Access service token
  (`e2e/src/staging.ts`), so it polled a 403 until `timeout 300` expired. (4) The tunnel
  tokens in Secrets Manager addressed `6a435b07`/`3779a34a`, deleted 2026-07-31; the live
  tunnels are `5cee0b27`/`77061a43`, and the secret now carries their tokens. (5) Unresolved:
  the runner still receives 403 from Access while the same service token is admitted from a
  workstation, which returns 530 (Access passed, origin unreachable). A presented token that
  no policy admits is 403 where no token at all is 302, so the token reaches Access and is
  refused there — the condition that distinguishes the two callers has not been found.

  Reconciling the edge against terraform is blocked separately. A `staging` workspace now
  exists in `terraform/cloudflare` holding nine imported resources — both tunnel configs, all
  five DNS records, both Access policies. The remaining eight cannot be adopted as the config
  stands: `access.tf` declares `zone_id = var.zone_id` on
  `cloudflare_zero_trust_access_application`, while the five live applications are
  account-scoped, so an import plans `5 to destroy` and recreates the applications guarding
  every staging hostname. Note also that one-at-a-time `terraform import` cannot adopt the two
  tunnels at all: `tunnels.tf:68` reads
  `cloudflare_zero_trust_tunnel_cloudflared.region[each.key].id` for both regions, so the
  config is unevaluable while only one is in state — config-driven `import` blocks are the
  route. Separately, the `default` workspace still lists `cloudflare_load_balancer.api[0]`,
  its monitor and both pools, deleted on 2026-08-17 to free the account's two-origin quota for
  the prod load balancer; a plain apply there will try to recreate them and hit the limit.
- 2026-08-20 — `edge-gate` in deploy-fleet is disabled, and with it the last verification
  that a release traverses the real Cloudflare path before production capacity moves. The
  cause is Bot Fight Mode: firewall events for `api-staging.dante-planner.com/healthz-local`
  record `action=managed_challenge source=botFight` against `ua=curl/8.5.0` from
  `20.15.229.149` (Microsoft ASN), once every ten seconds for the full `timeout 300` window.
  A managed challenge cannot be solved by a non-browser client, which is why the step
  reported 403 and died at exit 124, and why the Access service token made no difference —
  Bot Fight Mode runs before Access, so the token was never evaluated. Four unrelated defects
  were cleared while chasing this and each was real: a missing `STAGING_PROVISIONER_ROLE_ARN`,
  a provisioning role that could read neither of the job's two secrets, a readiness poll that
  sent no Access token, and tunnel tokens addressing tunnels deleted 2026-07-31. A browser
  User-Agent on the poll was tried and did not clear it; whether the datacenter ASN alone is
  sufficient signal was not confirmed, because Cloudflare's analytics lag left no event for
  that run at the time of writing. The zone is on the Free plan, where Bot Fight Mode is a
  zone-wide toggle with no exception mechanism — WAF skip rules for bot products need Super
  Bot Fight Mode (Pro). So the choices are: disable Bot Fight Mode zone-wide, which also
  removes it from the public production hostname; upgrade the plan and write a skip rule
  scoped to the staging hostnames; or leave the gate off. Note the job graph needed a second
  edit: `surge-up` declared `needs: [build-push, edge-gate]`, so gating edge-gate off with
  `if: false` would have skipped the surge, the tag bump and the settle in turn, disabling
  deployment rather than one check.
- 2026-08-20 — A failed dynamic import blanks the whole SPA, so every deploy breaks any tab
  that was already open. Asset filenames are content-hashed, so a new Pages build has an
  entirely different chunk set (22 new, 28 gone in the 08-17 build); a shell holding the old
  index.html then requests chunks that no longer exist. Pages rewrites unknown paths to
  index.html, so the browser reports "Expected a JavaScript-or-Wasm module script but the
  server responded with a MIME type of text/html" rather than a 404. `router.tsx:474` does set
  `defaultErrorComponent: RouteErrorComponent`, but a `lazyRouteComponent` import rejects while
  the route is still resolving, so it escapes past the route boundary to the single
  `ErrorBoundary` wrapping `<RouterProvider>` in `main.tsx:16`. Nothing anywhere catches
  "Failed to fetch dynamically imported module" — grep confirms — so there is no reload
  recovery. The usual fix is catching the import rejection and calling `location.reload()` once,
  guarded by a sessionStorage flag so it cannot loop.

- 2026-08-20 — The backend DaemonSet has no `startupProbe`, so its liveness probe polices a JVM
  that is still legitimately booting. `livenessProbe` is `initialDelaySeconds: 30`,
  `periodSeconds: 15`, `failureThreshold: 3`, a budget of roughly 75s; during the account
  cutover Flyway took 51.6s and the app reported `Started BackendApplication in 74.023 seconds`,
  so probes at 30/45/60s all hit a closed port and the container was killed with exitCode 143 —
  ten seconds after startup completed. It self-corrected because the second start had no
  migrations to apply. The dangerous version is a kill landing mid-migration: MySQL DDL is not
  transactional, so a partially applied V052-class migration would leave the schema wedged, and
  Flyway's advisory lock protects against concurrent migrators, not against the migrator being
  killed. A `startupProbe` covering worst-case boot lets liveness go back to detecting real
  hangs.

- 2026-08-20 — `docs/runbooks/prod-account-cutover.md` step 4 cannot run as written, because
  `scripts/ops/access/rds-query.sh` forces the read-only user. `SHOW REPLICA STATUS` needs
  `REPLICATION CLIENT`, which `danteplanner_ro` lacks, so the parity assertion errors with
  1227. The same user cannot `SHOW EVENTS`, so the documented `mysqldump --events` aborts after
  writing every table — leaving a 69 MB file with no `-- Dump completed` marker, which is the
  only reliable completeness check since size looks right. The `--routines` justification is
  also wrong: the stored procedure comes from
  `V053__create_rebuild_planner_filters_procedure.sql`, above the V045 dump, so no migration at
  or below V045 creates any routine, trigger or event. `@@GLOBAL.gtid_executed` is readable
  without privileges and gives the same parity answer; note RDS's own `mysql.rds_heartbeat2`
  writes advance it continuously, so exact equality between primary and replica is not a
  reachable state.

- 2026-08-20 — `terraform/.gitignore:9` ignores `*.tfvars`, which keeps
  `gitops_target_revision` — the value deciding which revision production's ArgoCD syncs — out
  of version control entirely. The rule is right for the file as a whole in a public repo, but
  tfvars mix genuinely sensitive inputs (account ids, zone ids, API tokens) with behavioural
  configuration that is not secret. Consequences: the value changes with no diff, review or
  history; `cp.sh.tftpl` does `git checkout ${gitops_revision}`, so a control-plane rebuild pins
  to whatever the applying machine's tfvars said, and two operators can produce different fleets
  from the same repo; and the drift workflow has to reconstruct all of it from `TFVARS_*`
  secrets, which is the same data maintained twice by hand. Terraform accepts multiple
  `-var-file` and auto-loads `*.auto.tfvars`, so splitting each stack into a tracked non-secret
  half and an ignored secret half would put the revision under review and shrink the secrets to
  the genuinely secret residue.

- 2026-08-20 — The provisioning role is named `danteplanner-provisioner` in the management and
  staging accounts and `prod-provisioner` in prod, and the inconsistency has already caused an
  outage of the deploy path. `~/.aws/config` carried a `prod-provisioner` profile whose
  `role_arn` named `danteplanner-provisioner`, a role that does not exist in that account; that
  value was copied into the `AWS_PROVISIONER_ROLE_ARN` GitHub secret on 2026-08-17 and stayed
  there until 2026-08-19, so every `build-push` failed with "Not authorized to perform
  sts:AssumeRoleWithWebIdentity" and no CloudTrail record in any account, because an ARN that
  resolves to no role leaves no account to log the denial. The name is set per workspace in
  `terraform/iam-bootstrap`, so aligning it is a rename plus a secret update; the cheaper
  discipline is verifying an ARN with `aws iam get-role` before propagating it anywhere.

- 2026-08-20 — Cloudflare load balancing is at its account limit of two origins, both consumed
  by the production pools, so no second environment can hold a load balancer. Creating
  `danteplanner-prod-oregon` and `-seoul` during the cutover required deleting the edge-test
  build-out. Nothing in the terraform expresses the ceiling: each workspace plans happily in
  isolation and the constraint appears only as a 400 mid-apply.

- 2026-08-20 — `test-migration` gates nothing in PR Gate: no job declares it in `needs`, so a
  build can publish while it is still running. `e2e` had the same shape and was wired into both
  builds on 2026-08-20, but `test-migration` was left alone because its condition includes
  `github.event_name == 'pull_request'` — it never runs on push to main, so adding it to
  `build-backend`'s `needs` would skip that build on every push, since a skipped dependency
  skips its dependents under the implicit `if: success()`. Wiring it in needs a condition that
  survives both trigger types, not just an edge.

- 2026-08-20 — `settle-down` only runs on the happy path, so a failure anywhere upstream leaves
  both app ASGs at 2. It declares `needs: [bump-tag, surge-up]` with no explicit `if`, which
  means the implicit `if: success()`; a failed `bump-tag` therefore skips the job whose entire
  purpose is undoing `surge-up`'s temporary scale-out. Cleanup that only happens when nothing
  went wrong is not cleanup — `if: always()` is the condition that matches the intent. The same
  job also carries `environment: production`, so it additionally waits on a human before
  capacity is returned.

- 2026-08-20 — Approving a deploy takes up to three reviews, and cannot be reduced by editing
  the workflow. `prod-provisioner` trusts only
  `repo:phrimm136/dante-planner:environment:production`, and `build-push`, `surge-up` and
  `settle-down` all assume it, so each must declare that environment to mint a matching OIDC
  subject — and declaring it is exactly what triggers the environment's required-reviewer rule.
  The credential and the gate are the same mechanism. Reducing it to one review means either
  removing the reviewer rule (unattended everywhere, including the first job), or adding a
  second ungated environment for the mechanical jobs and extending the role's trust to its
  `sub`.

- 2026-08-20 — `PlannerCatalogLifecycleIT.listRecencySort_WhenBrowsing_NewestFirstFilesortFree`
  is mitigated, not fixed. Its three rows are stamped one to three hours in the past, so every
  planner another test publishes sorts above them; the membership assertion now asks for the
  controller's maximum page (`Math.min(size, 100)`) instead of the default 20, which survives
  up to a hundred published planners in the suite's shared catalog and fails again past that.
  The relative-order assertions were already written defensively. Eliminating the coupling means
  the test not reading a global listing at all — filtering to its own rows, or running against
  an isolated catalog.
- 2026-08-20 — Two Grafana alerts stay lit for reasons that are not incidents, and both are
  rule-definition problems rather than infrastructure ones. The ArgoCD drift alert fires on the
  management fleet, whose Applications report `sync_status=OutOfSync, health_status=Missing`
  because they were deliberately drained and their `syncPolicy` cleared for the account cutover
  — that pause is what stops ArgoCD resurrecting a DaemonSet against the rollback database, so
  the condition is correct and will persist for the whole bake. `argocd_app_info` carries
  `autosync_enabled`, so scoping the rule to
  `argocd_app_info{sync_status="OutOfSync", autosync_enabled="true"}` fires only when an app
  that should be self-healing has drifted and stays quiet for one that is paused on purpose;
  that expresses the intent, where a silence or inhibition would only mute the symptom. Note
  both Mimir tenants return the same four series with the same `instance` addresses, so the rule
  cannot be scoped by tenant — whichever one it lives in sees management's paused apps.

  The replica alert was never a real condition. Only `hikaricp_connections_(active|pending)`
  passed the remote-write keep-list in `deploy/base/prometheus.yaml`, and both are instantaneous
  gauges; at Seoul's read rate a gauge samples zero almost every scrape whether or not the pool
  is serving, so a rule written against one reads as a silent replica permanently. The keep-list
  now admits the full Hikari set and the counters confirm the opposite of silence — Seoul's
  `replica` pool runs at 0.10-0.14 acquisitions/sec against `primary` at 0.048-0.078, roughly
  two reads per write. The rule needs re-pointing at
  `rate(hikaricp_connections_usage_seconds_count[...])`; waiting will not clear it, because the
  gauge it watches will keep reading zero.
- 2026-08-20 — Requests for a missing `/a/*` asset return the SPA fallback `index.html`
  with HTTP 200, and the `_headers` rule for `/a/*` stamps `max-age=31536000, immutable`
  onto that HTML too, so Cloudflare edges and browsers cache HTML under the chunk URL
  for a year. One poisoned entry served bare `index.html` after the 2026-08-20 FE deploy
  (module MIME error on `/a/BzVGhZqzEgrm.js`); an edge purge cleared it, but browsers
  that cached the HTML self-heal only when the chunk's hash changes or on a hard reload.
  Repro probe: `curl -sS -D - -o /dev/null https://dante-planner.com/a/<any-missing>.js`
  shows 200 + `text/html` + the immutable header. The durable fix is undecided: make
  `/a/*` misses real 404s at the serving layer, never cache HTML bodies under `/a/*`, or
  keep prior releases' assets available across deploys. Deciding requires naming where
  the site is actually served from — no wrangler config exists in the repo, and
  `frontend/scripts/dev-r2-sync.ts` references a `frontend/wrangler.jsonc` that is not
  checked in.
- vitest full suite: one worker OOMs (ERR_WORKER_OUT_OF_MEMORY) under parallel run while all 220 completed files pass; dataIntegrity.test.ts passes in isolation. Found 2026-08-21 during static keyword-expansion commit validation.
- 2026-08-24 — `terraform/cloudflare` splits one logical stack across two state buckets with
  inconsistent workspace names, and the stack's resources live in Cloudflare rather than in
  any AWS account, so the repo's "state lives in the account whose resources it describes"
  rule gives no answer for it. The staging edge is the staging bucket's `default` workspace
  (19 resources, both `danteplanner-staging-*` tunnels); the production edge is the prod
  bucket's `prod-fleet` workspace (10 resources). The prod bucket also carries an EMPTY
  `staging` workspace that manages nothing and shares a name used meaningfully in the other
  bucket. Selecting a workspace is therefore the only barrier between planning one
  environment and another, and the selection lives in `.terraform/environment`, which no
  command prints. A `plan` run in the prod bucket's `default` or `staging` workspace reads as
  "create the whole edge" because both are empty. Resting selection should be `prod-fleet`,
  where config and state agree. The prod bucket's `default` workspace previously held a stale
  duplicate of the retired management edge-test setup — the same objects a second state file
  in the management bucket also described — and an apply there would have recreated an
  `edge-test.dante-planner.com` load balancer and a tunnel named `danteplanner-oregon`; those
  eight addresses have been removed from state.
- 2026-09-11 — IndexedDB still holds the one-part `deviceId` singleton row in every browser
  that ran a version before ADR 096; nothing reads it and the planner listing skips one-part
  keys. Delete it inside `onupgradeneeded` at the next database version bump, whatever that
  bump is for.
- 2026-09-11 — `GlobalExceptionHandler` logs `PLANNER_NOT_FOUND` at WARN for a DELETE of a row
  the server never received, a routine outcome the client already treats as success (about 5 a
  day). Worth demoting that one method-and-code pair to INFO when the WARN stream is next used
  for alerting, or when an ADR settles a client marker for never-synced rows.
- The keyword browser builds its own grid rather than `FilteredEntityGrid`, so it still
  renders `useProgressiveCount` batches and loses a deep scroll offset on return from a
  keyword detail page. `CardGeometry.units` is now `CardUnits | null`, so the shared grid
  reserves a width-only slot for a card with no fixed height and nothing blocks the fold;
  worth doing when the keyword browser's scroll restoration is next reported or revisited.
- `images/UI/manifest.json` records `source: null` for every asset no converter claims,
  which is indistinguishable from an asset whose source was never recorded. Worth splitting
  into "hand-made" and "unknown" when an asset's provenance is first disputed.
- The theme pack list popup prefab is 310x450 while the composed art the card draws is
  650x1069, so the list card is laid out at proportions the art was never cut for. Worth
  recomposing the art at the list card's size when that card's proportions are next revisited.
- Seven paths the asset getters can produce have no file behind them: the neutral skill
  frames above tier 1 and the per-season start buff plates. Worth generating or narrowing the
  getters when a caller reaches one of them, which no current caller does.
- The theme pack card draws the art, the name and the highlight sprites only; the game's
  pin, magnifier button and attribute chips are absent. Worth adding when the theme pack
  card gets an interaction design pass.
- The EGO rank word plate measures about 6 percent wider than the game's, inside the
  measurement noise of a 1280-wide reference. Worth revisiting when a higher-resolution
  reference screenshot exists.
- 2026-09-15 — Account deactivation withdraws only the catalog rows (ADR 034 @visibility,
  `UserAccountLifecycleService.java:96`); the aggregate visibility predicate in
  `PlannerRepository.java:88-89` and `:123-125` checks `published` and `content.deleted_at`
  but never the owner's `users.deleted_at`, so for the 30-day grace window a deactivated
  owner's published planner still answers `GET /api/planner/md/published/{id}` with full
  content and the author's name (`PublishedPlannerDetailResponse.java:103-104`), and every
  caller of `checkPublished` (comment, vote, bookmark, report) still accepts writes against
  it. The drift audit's two membership queries (`PlannerDriftAuditRepository.java:138`,
  `:157`) omit the same conjunct, so those planners are reported nightly as
  `catalog_membership` "row missing" drift for the whole window (inferred from the
  predicate, not observed in logs). Decided: each predicate joins `users` and adds
  `deleted_at IS NULL` directly (rejected: deriving detail visibility from catalog-row
  presence, which the audit cannot use and which would turn a projection drift into a
  404 for a live planner). `RESTORE_ALL_OWNED_BY` is unaffected (`user_id = :userId` is the
  owner conjunct). Worth doing before the next account-deletion request lands in
  production, or before the visibility definition gains another conjunct.
- 2026-09-15 — A deactivated author's comments read differently across the two deletion
  stages. During the grace window the body is served in full with only the author fields
  blanked: `CommentTreeNode.java:72-80` masks `authorEpithet`/`authorSuffix` on
  `author.isDeleted()`, but `:92` blanks `content` on `comment.isDeleted()` alone, and the
  listing (`PlannerCommentRepository.java:30-35`) joins no user and filters nothing.
  After the purge the body is gone (`:71-72`, `SET c.userId = :sentinelId, c.content = ''`).
  `CommentServiceLayerTest.java:487-488` pins the grace-window body as visible. Decided:
  the author lifecycle governs the projection only — `fromEntity` blanks `content` under
  the same author-hidden predicate it already computes for the name fields — and the tree
  shape stays the comment flag's alone: the `:174` leaf prune keeps `comment.isDeleted()`
  and never consults the author, so a leaf masked for its author survives and reactivation
  changes no row's presence. Rejected: extending the unified predicate to the prune (a
  reactivated author's leaves reappear, and replies under them lose and regain their
  parent); blanking at soft-delete time (reactivation cannot restore the body). Purge is
  unchanged. Ships as the `fromEntity` predicate change, the flipped
  `CommentServiceLayerTest` assertion, an HTTP-level IT asserting a deactivated author's
  leaf survives with empty body (none exists today; `CommentControllerIT.java:597` covers
  comment-level deletion only), and an ADR. Worth doing with the deactivation-visibility
  entry above, since both are the same missing `users.deleted_at` conjunct read from
  different tiers.

- 2026-09-16 — 119 `<Skeleton>` elements across 48 files still carry their own `w-`/`h-`
  box (41 of them inline `Suspense fallback={<Skeleton className="h-… w-…" />}` text stubs),
  all outside the `*Skeleton*.tsx` files the size-roots unit converted and therefore outside
  `no-sized-skeleton-tsx`, which scans only those. Condition: when a page carrying one is next
  edited, swap the stub to `TextSkeleton` (for text) or a `CardSlot` (for a card box).

## Grafana objects are provisioned by bash, invisible to drift detection (2026-08-30)

Folders, alert rules, and the fleet dashboard reach Grafana through the
`deploy/grafana/*.sh` import scripts. They post once with `X-Disable-Provenance`
so the objects stay editable in the UI — which also means nothing detects a
rule deleted or edited by hand. A missing `mysql_*` alert stayed unnoticed
until the 2026-08-24 mgmt-decommission incident, and
`scripts/ops/access/db-observability-check.sh` now verifies the DB subset only
when someone runs it.

The candidate replacement is the `grafana/grafana` Terraform provider
(`grafana_folder`, `grafana_rule_group`, `grafana_contact_point`,
`grafana_dashboard`) as a stack under `terraform/`, with a scheduled
`plan -detailed-exitcode` as the drift detector; the import scripts retire
with it. The cost is the stance flip: terraform-managed objects carry
provenance and lock out UI edits, so every dashboard tweak must land in git.
Terraform covers only the Grafana side; the checker's metrics half (are
`mysql_*` series flowing) stays separate.

Design lane by the repo's routing — it moves the provisioning seam and kills
the import scripts, so `/argue` before any edit.

## import-alert-rules.sh duplicates rules on re-run (2026-08-30)

The Grafana provisioning API deduplicates by UID, not title, so re-running the
import against a populated folder creates a second copy of every rule. Adding
mysql-connectivity-lost to an already provisioned stack therefore needed a
one-off script with an existence check instead of the import itself. Fix: an
existence check per title inside post_rule (skip or update when the title is
already present), after which partial re-runs are safe and one-offs of this
class never need to exist. Same defect in import-app-alert-rules.sh and
create-staleness-rules.sh.

## Exporter endpoint secrets are hand-maintained addresses (2026-08-30)

danteplanner/mysqld-exporter/primary-endpoint and replica-endpoint are written
by hand and nothing ties them to the instances they name; the 2026-08-24
incident was them outliving the management account's databases. The durable
shape is the RDS terraform stack writing these secret versions from its own
outputs, so an address cannot outlive its instance and account moves cannot
strand it. Design call: whether the stack owns the whole secret or only the
endpoint keys, and how Seoul's certificate-name constraint (dial the RDS name,
not a Route53 alias) is encoded.

## db-observability-check.sh runs only when someone remembers to (2026-08-30)

The checker (metric families per cluster, connectivity, DB alert-rule health)
is a plain script with no enforcement. The PR gate is the wrong home — it
probes live prod state, not the diff. The fit is a scheduled workflow with an
OIDC read role and a Grafana token secret, alerting on nonzero exit; it is the
meta-monitoring layer that catches a deleted alarm, which the alarms themselves
cannot. Blocked considerations: CI currently resolves its account from GitHub
secrets, and no Editor-capable Grafana token exists in Secrets Manager yet.

## Two cluster alert rules can never fire (2026-08-30)

node-not-ready and backend-daemonset-unready use raw comparison filters
(`... == 0`), which return the matched series with its original value 0; the
shared threshold node fires on value > 0, so the condition is unsatisfiable.
The `== 1` rules in the same group work, and mysql-connectivity-lost uses
`== bool 0` for this reason. Fix is the same one-token change (`== bool 0`) in
both expressions, plus re-provisioning; worth a drill afterwards since these
two have silently never paged.

## 2026-09-02 MySQL 8.4 upgrade (adr/092)

- **`terraform/seoul` untargeted plan replaces all three fleet instances** (cp, data,
  ingress) on an AMI data-source drift, with the launch template and SSM associations
  following. The upgrade was applied with `-target` on the replica and its parameter
  group; the fleet diff is untouched and still pending.
- **`terraform/rds` untargeted plan destroys the fleet peering routes and 3306 ingress
  rules** because `fleet_peering_connection_id`, `fleet_cluster_security_group_id`,
  `fleet_vpc_cidr`, and `seoul_peering_connection_id` live in no committed var-file for
  prod. Every prod apply of that stack needs them on the command line or `-target`.
- **`docs/runbooks/prod-account-cutover.md` still pins `danteplanner-mysql80`** on the
  restore instruction; the group is now `danteplanner-mysql84`.

## 2026-09-18 comment sweep follow-ups

- **Surviving comments are untested claims.** The sweep left only comments stating a dependency
  behaviour or a hand-parsed format (about 90 in `backend/src/main`, about 470 in
  `frontend/src`); each is a claim a test could pin, after which the comment is redundant.
  Worth doing per package when that package is next edited: turn the fact into a named test,
  delete the comment. The candidate list is the set of remaining `/** */` and `//` blocks.
- **Three deleted invariants have no home.** `PlannerViewRecorder.flush` orders planner-id
  locks to avoid AB-BA deadlock across pods; `RecommendedSql` exposes `c`/`s`/`m` aliases
  callers splice against; `PlannerDriftAuditRepository` followed a naming convention. All three
  were rationale under the sweep rubric. The first two are invariants: record each in an ADR
  or pin it with a test before the next change to those files.
- **`shared/gtid/GtidGateConfig.java` and `GtidReadGate.java` were not swept**: both carried
  uncommitted edits in another checkout at merge time. Sweep them with the same rubric once
  those edits land.
- **Comment discipline has no gate.** Nothing fails when a restating javadoc is added. Worth
  adding when the next sweep-worthy file appears: a hook flagging javadoc whose first sentence
  starts with "Handle", "Returns", "Gets", "Sets", "Creates", or repeats the declaration name,
  plus the provenance verifier over every worker diff before merge.
- **Doc reference check.** Every backticked Java identifier in `docs/adr`, `docs/runbooks` and
  each `CLAUDE.md` should resolve against the ArchUnit-imported classes (an `@ArchTest` in the
  architecture package, `Class#method` included); paths and TypeScript symbols need a shell
  half. Worth adding at the next rename that leaves a document stale.
- **Rules ship without negatives.** `owned_errors_need_no_handler` was negative-checked by a
  throwaway probe and the probe discarded. Worth keeping a synthetic violator per architecture
  rule under a `fixtures` package that the production rules exclude, starting with the next rule
  added.

## 2026-09-18 problem-details migration (adr/113, adr/114)

- **`Problems.MESSAGE_MIRROR`** (`backend/.../shared/exception/Problems.java`) copies `detail`
  into a `message` property on every error body so a frontend release still reading `message`
  keeps working through the deploy window. Delete the constant and its line in `fill` once the
  frontend release that reads `detail` only is live in both regions; nothing else changes and
  no test asserts the mirror.

## 2026-09-18 nginx retired, references remain (adr/011)

- **`ServiceUpdatingError` and the `SERVICE_UPDATING` entry of `UNAVAILABLE_ERROR_BY_CODE`**
  (`frontend/src/lib/api.ts`, `apiErrors.ts`) map a body nothing emits since the nginx
  maintenance flag left with the edge. Delete both when the error module is next touched;
  the problem-details migration of the backend advice is the natural moment.
- **`docs/runbooks/rds-migration.md` Zone 1 step 1** raises maintenance through
  `docker exec danteplanner-nginx`, a container that no longer exists. Restate the step when
  a maintenance mechanism exists on the tunnel path, or archive the runbook under
  `docs/legacy/` once the decommission gate it guards is closed.
- **`docs/runbooks/environment-setup.md`** describes an nginx-fronted compose topology
  (container IPs, `TRUSTED_PROXY_IPS` for the nginx range, CORS pass-through checks), and
  `SecurityProperties` and `ClientIpResolver` javadoc name nginx as the trusted proxy. Rewrite
  against the current local stack when that runbook is next followed and found wrong.

## 2026-09-18 gtid gate read-through (shared/gtid)

- **The read-your-writes pin has no exit.** `GtidCookieFilter.handleRead` clears `ryw_gtid`
  only when the replica probe succeeds; a GTID the replica will never apply (a source UUID
  from the pre-cutover primary, a rebuilt replica, or a client-supplied value that is only
  base64-checked in `GtidCookie.decode`) fails every probe, so every GET from that client
  holds a replica connection for the full 50 ms bound, is served cross-region, and for a
  malformed set logs a WARN with stack trace per request (`GtidReadGate.isCaughtUp`). The
  cookie has no max-age, and `gtid.gate{outcome=primary}` cannot tell lag from a token that
  can never validate. ADR 008 rejects timing constants on correctness paths, and a max-age
  fails open, so this is a decision to reopen there, not a patch: bounded pin versus
  fail-open, plus format validation before the value reaches SQL. Worth doing before the
  next primary cutover (the runbook in this tree), which is the event that mints such tokens
  for every client holding a cookie at the time.
- 2026-09-23 — Seoul writes spend most of their WAN round trips on transaction and driver
  chatter. Mimir 7-day means, Seoul against Oregon: Save `PUT /api/planner/md/{id}` (sent on
  an explicit save with sync on; the editor's autosave writes only IndexedDB) 2583 against
  76 ms, publish 3663 against 205 ms, `PUT /api/user/settings` 1179 against 24 ms
  (about 19, 27 and 9 round trips at 130 ms). A general-log trace of the causal harness's
  primary, with Seoul routing, warm pools and 1 s idle before each request, counts 10 round
  trips for a repeat Save on the normal path (`syncVersion` carried; `?force=true` counts the
  same): one UPDATE, three SELECTs, and six of chatter
  (`SET autocommit=0`, `SET autocommit=1`, `COMMIT`, Hikari's validation ping after 500 ms
  idle, and two `SELECT @@session.transaction_read_only`). Settings counts 9, matching
  production; publish counts 32. Every read inside a write request goes to the primary. The
  read-only probes come from `Connection.isReadOnly()`, one of them
  `GtidCapturingDataSource.captureCommittedGtid`, which also runs on every read-only
  transaction where REPLICA maps onto the capturing wrapper. A raw-driver probe shows
  `useLocalSessionState=true` removes both probes and the setup `SET autocommit=1`;
  `TransactionSynchronizationManager.isCurrentTransactionReadOnly()` would answer the
  wrapper's question without the driver. The per-transaction autocommit pair needs the pool
  to start with autocommit off plus `hibernate.connection.provider_disables_autocommit`,
  untested. Across both regions the primary receives 2.3 `SET` statements per `COMMIT`
  (`SHOW GLOBAL STATUS` over 20.9 days of uptime, 71,487 connections). Saving a published
  planner costs 19 round trips on warm connections and 25 on a freshly evicted pool: the
  `AFTER_COMMIT` filter rebuild is a second transaction with its own ping and autocommit pair,
  and a young connection prepares six statements. The Seoul primary pool replaces each of its
  10 connections about every 26 minutes (Hikari's default `maxLifetime`), at a mean 1.21 s per
  creation against 0.036 s in Oregon, so a Save often lands on a connection that has not
  prepared its statements. The pool's mean acquire (95 ms, one validation ping) and mean hold
  (0.44 s) put the Seoul→Oregon round trip nearer 95 ms than ADR 009's 130 ms. Planner bodies
  average 7 KB (maximum 29.7 KB), so TCP slow start is at most a minor term. Worth doing as the
  write round-trip unit that ADR 009's single-round-trip rule already demands.
- 2026-09-23 — Two read paths take Seoul's primary across the WAN, and together with token
  rotation (about 5,800 a week) they account for the pool's ~80,000 weekly acquisitions against
  ~200 user writes. The view flush runs one primary transaction per detail read (6–8 round
  trips; 6 even for a repeat view that inserts nothing), up to 46,928 a week. `/api/sse/subscribe`
  loads notification settings through `UserSettingsService.getOrCreateEntity`, a read-write
  `@Transactional`, so every per-pod cache miss is a primary transaction (11 round trips when it
  creates the row), up to 27,335 a week. Neither is on a user's critical path, but both hold
  primary connections from the smaller WAN pool and add replication traffic. Worth doing with
  the write round-trip unit.
- **A region flap clears the cookie without the guarantee.** In the replica-disabled region
  the probe runs against the primary and always succeeds, so a client steered there for one
  GET has `ryw_gtid` cleared, and its next GET in the replica region reads the lagging
  replica unguarded. ADR 008 states the read-path contract per region; the later geo-steering
  decision did not revisit the cookie's lifecycle across regions. Becomes worth deciding when
  geo steering is changed again or when a stale-read report coincides with a region switch.
- **The gate assumes the probe and the read reach the same replica.** `WAIT_FOR_EXECUTED_
  GTID_SET` runs on one pooled connection and the request's read uses another; sound with
  one replica behind the endpoint, void the day the endpoint resolves to more than one host.
  No guard and no ADR names the assumption. Worth an ADR line the moment a second replica or
  a reader endpoint is proposed.
- **Tagged GTIDs crash a committed write.** `GtidWriteCapture.parseInterval` parses every
  colon segment as a number; a MySQL 8.3+ tagged GTID (`uuid:tag:1-3`) throws, and the
  exception escapes the filter as a 500 after the commit. Only reachable if something sets a
  tagged `gtid_next`, which nothing does; worth a guard when the union parser is next touched.

## 2026-09-18 rate-limit read-through (shared/ratelimit)

- **A lowered limit never reaches a live bucket.** `RateLimitService.tryConsume` passes the
  bandwidth as a creation-time supplier and `RedisConnectionConfig.buildRateLimitProxyManager`
  sets no implicit configuration replacement, so an existing key keeps its old bandwidth until
  it idles for the full hour of TTL. The key you lower a limit for during an incident is the
  one that never idles. Bucket4j's versioned `withImplicitConfigurationReplacement` is the
  documented fix; the test is one line in the Redis-backed limiter test (change bandwidth,
  consume, assert). Worth doing before the next limit change is relied on in an incident.
- **Every anonymous request mints a device cookie the identity discards.**
  `RateLimitInterceptor.chargeBucket` resolves the device id before choosing the subject, and
  `DeviceIdResolver.resolve` sets a one-year cookie when none is present; behind Cloudflare
  the identifier is the public IP and the id is unused, yet Set-Cookie goes out on every
  public read, which also defeats any future edge caching of those responses. Move the mint
  behind the private-IP branch (a `Supplier` parameter). It is one of four blockers measured
  on an anonymous `GET /api/planner/md/published` response: the same response sets `csrf`
  (seven days), carries Spring Security's `Cache-Control: no-cache, no-store, max-age=0,
  must-revalidate`, and is JSON, which Cloudflare caches only under a Cache Rule. Worth doing
  with the first `Cache-Control` on a public read, or as the sibling edit of a
  resolver-does-not-write rule.
- **The forwarded-for fallback trusts the leftmost hop.** `ClientIpResolver.firstHop` takes
  the client-written entry once the direct peer is a trusted proxy;
  `ClientIpResolverTest.resolve_WhenHeaderCarriesAChainOfHops_ReturnsTheLeftmost` pins that
  as the spec. Latent while `CF-Connecting-IP` is always present on the tunnel-only path. Replace
  with the container's rightmost-trusted parser (`ForwardedHeaderFilter` / `RemoteIpValve`) and
  an attacker-framed test. Worth doing before any ingress that bypasses Cloudflare exists.
- **The 429 carries no Retry-After.** `tryConsume(1)` discards the probe that knows the wait;
  `SseCapacityExceededException` sets the header on the SSE path and the 503 helper on the
  degradation path, so the rate-limit 429 is the odd one out. Fix with
  `tryConsumeAndReturnRemaining` and the one status-to-required-headers contract test that
  covers all three. Worth doing when the error mapping is next touched.
- **Bucket configuration is validated per request, not at bind time.** `RateLimitProperties`
  carries no constraints (three of nine properties classes do: Redis, OAuth, JWT), and
  `buildConfiguration` runs per request, so a zero duration fails on first use. The fix is the
  ArchUnit rule "every `@ConfigurationProperties` class is `@Validated`" and the sibling edits
  it lists; the rule, not the class, is the unit of work.
- **Rate limiting fails closed on a Redis outage while the blacklist fails open, and no
  decision chose either for the limiter.** `ApiExceptionHandler.
  handleRateLimitRedisUnavailable` maps a limiter outage to 503 for every rate-limited
  handler, `PUBLIC_READ` included, after up to a four-second future timeout. The 503 entered
  in 38794748 as a fix for an untyped 500, not as a policy; ADR 049 covers endpoint coverage
  only. ADR 010 rejected fail-closed for the blacklist because a store outage becomes a total
  outage, and the same Redis loss here takes down anonymous reads that never need Redis. The
  circuit-breaker entry above covers the mechanism; the per-subject-class policy (open for
  anonymous reads, closed for writes) needs an ADR beside 010. The limiter store is a
  per-region ephemeral `redis-ratelimit` pod, so the loss of one region's pod alone turns that
  region's published-planner reads (`PublishedPlannerController`, `PlannerController`, both
  `PUBLIC_READ`) into 503 while its database and auth Redis are healthy. Worth deciding at the next
  rate-limit or degradation design session, or before any document describes the Redis
  failure behavior as uniform.

## 2026-09-18 consistency without an LLM (scheduled ratchet)

- **No scheduled run measures rule drift.** The gate runs the rule engines already wired
  (ArchUnit with freezing rules, checkstyle, oxlint plus `frontend/lint/entity-plugin.js`,
  ast-grep via `frontend/sgconfig.yml`) only on pull requests. A weekly workflow, next to
  `infra-suite.yml` and `verify-rds-ca.yml`, that runs every rule in report mode, diffs the
  per-rule violation count against a stored baseline, and opens an issue on any increase
  would make consistency a measurement rather than a per-session hope. ArchUnit's frozen
  stores are the baseline for Java; ast-grep `scan --json` plus a count file is the baseline
  for the rest. The same run is the home for the declared-but-dormant gates: PIT (declared in
  `backend/build.gradle.kts`, scored 42 percent against a 50 percent threshold, never in the
  gate), OWASP dependency-check and Sonar (declared, never run), and the backend patterns in
  `.claude/hooks/forbidden-patterns.json` whose hook is not wired (entry above). Each is
  reactivated as a report-mode job with a ratchet, never as a blocking gate on day one. Worth
  doing as the first rule-into-build session, since it is what makes every later rule
  measurable before it is enforced.

- 2026-09-21 — Nothing rolls a failed backend rollout back. `deploy/base/spring-daemonset.yaml`
  sets no `updateStrategy` (default RollingUpdate, `maxUnavailable: 1`), ArgoCD runs
  `automated` + `selfHeal` (which converges on Git, never on the previous image, and refuses
  `argocd app rollback` while automated sync is on), and no Argo Rollouts or analysis step
  exists. What happens today when the new pod fails readiness: the rollout halts on the first
  node, the surge node keeps serving the old image, `deploy-fleet.yml`'s
  `rollout status --timeout=600s` fails the job, and the surge stays up by design
  (`:333-337`). The bad image remains on the first node until a human reverts the tag-bump
  commit. Closing it means choosing between a `settle-down` failure branch that reverts the
  image-tag bump commit (Git-driven, matches CORE-mode ArgoCD) and Argo Rollouts with an
  analysis template on `/actuator/health/readiness`; the former is one workflow step and
  needs an `if:` on a job that today has none (the 2026-08-20 `settle-down` entry is the same
  gap). Worth doing before the next deploy that ships a schema-coupled change, or once a
  failed rollout is observed in production.
- 2026-09-22 — `GET /api/planner/md/published` returns `Page<...>`, so every list request
  issues a `COUNT(*)` over the filtered `planner_catalog` set
  (`PublishedPlannerQueryService.java:126`), and `recencySorted` orders by
  `firstPublishedAt DESC` with no unique tiebreaker, so rows sharing a timestamp can shift
  between pages. Worth doing when list p99 becomes a reported problem or a duplicate-across-
  pages report arrives: return a slice (limit+1) and append `plannerId` to the sort.
- 2026-09-22 — `Planner.java:67-74` declares the three inverse `@OneToOne(mappedBy=...)`
  sides without `fetch`, so they default to EAGER; no main-source caller uses an inherited
  `findById`/`findAll` today (every path goes through the `AGGREGATE_LOAD` JOIN FETCH), so
  the cost is latent. Worth pinning with `FetchType.LAZY` or an ArchUnit ban on inherited
  finders the first time a caller of an inherited `PlannerRepository` method lands.
- 2026-09-23 — `User.settings` (`User.java:96`) is the inverse side of a `@OneToOne`, which
  Hibernate loads eagerly despite `FetchType.LAZY` because bytecode enhancement is off. Every
  aggregate load that joins the user runs one extra `user_settings` SELECT: a Save pays it on
  the primary (in a general-log trace of the causal harness it is one of the draft Save's three
  reads) and a published detail read pays it on the replica. No main-source code calls
  `getSettings()` or `setSettings()`. Deleting the association drops the read; the mapping
  carries `cascade = ALL, orphanRemoval = true`, and V024's `fk_user_settings_user` is
  `ON DELETE CASCADE`, so user deletion still removes the row, provided no path relies on the
  JPA cascade before the user row is gone (unchecked). Under write forwarding the Save's copy
  costs about 1 ms in Oregon and the content cache leaves the read's copy to edge misses. Worth
  doing on the next touch of `User`, or if write forwarding is rolled back.
- 2026-09-22 — `TokenBlacklistService.failOpen` increments `blacklist_check_skipped_total`
  and logs warn, but no alert rule references the counter and no rule covers Redis
  availability or command timeouts at all (`deploy/grafana/import-*-alert-rules.sh` grep
  `redis` → none; MySQL has `mysql-connectivity-lost`). A fail-open storm surfaces only via
  `backend-warn-sustained`. Worth doing before the next Redis incident drill: a rule on
  `increase(blacklist_check_skipped_total[5m]) > 0` and one on `redis_up == 0`.
- 2026-09-22 — `PublishedPlannerQueryService.incrementViewCount` (`:95-100`) has no
  production caller; the live path is `PlannerViewRecorder`. Kept alive by three tests in
  `PublishedPlannerQueryServiceTest`. Delete method and tests on the next touch of that
  service.
- 2026-09-23 — Reads survive a primary outage only in Seoul. The Oregon overlay sets no
  `DATASOURCE_REPLICA_ENABLED`, so Oregon reads use the primary, and `/healthz-local` rewrites
  to `/actuator/health/readiness`, whose group holds `readinessState` alone, so a dead primary
  leaves Oregon healthy at the load balancer and Oregon-routed reads fail with 503. Either a
  DB-aware readiness indicator for Oregon (steering clients to Seoul) or Oregon reads on a
  replica would close it. Worth doing when a primary outage is drilled or observed, or before
  any document claims read survival without naming the region.
- 2026-09-22 — Shutdown is graceful but unbounded by anything shorter than the kill. The
  JVM is PID 1 (BusyBox ash execs a lone `-c` command; checked in
  `eclipse-temurin:21-jre-alpine`) and Boot 3.5 defaults `server.shutdown=graceful` with a
  30 s phase timeout, but SSE emitters live 1 h and nothing completes them on context close,
  so any open stream holds shutdown for the full 30 s, the same as the pod's default
  `terminationGracePeriodSeconds`; there is no `preStop`. The surge/drain deploy moves traffic
  first, so this shows only as slow pod termination. Worth doing when a rollout's 5xx or
  termination time is measured non-zero: complete emitters on `ContextClosedEvent` and set a
  phase timeout under the grace period.
- 2026-09-22 — `PlannerDriftAuditRepository.driftedUpvoteCounters` joins `planner_votes`
  without a `deleted_at IS NULL` predicate while `driftedCommentCounters` filters
  `planner_comments.deleted_at IS NULL`; V018 made votes immutable so the asymmetry may be
  correct, but nothing says so. Worth a one-line comment or a shared predicate the next time
  either query changes.
- 2026-09-22 — `logback-spring.xml:6-9` header still describes "stdout → awslogs →
  CloudWatch /ecs/danteplanner/backend"; the pipeline is stdout → Alloy → Grafana Cloud Loki
  (`deploy/base/alloy-logs.yaml`). Delete the stale comment on the next touch of that file.
- 2026-09-23 — The published-detail loader cannot fetch until a dynamically imported 1 KB
  chunk arrives. `loadPublishedPlanner` starts with
  `await import('@/pages/planner/hooks/usePublishedPlannerQuery')`; the chunk's dependencies
  already sit in the entry graph, but it is requested alongside the route component's 31
  chunks and queues behind them, so the API request starts ~800 ms after the router runs
  (1342 → 2137 ms in a cold load from Korea). A static import in `routeLoaders.ts` starts the
  fetch with the router. Worth doing with any detail-page latency work; alone it does not move
  first content, which the route's own chunks gate (next entry).
- 2026-09-23 — The read-only detail route bundles interaction-only code at module scope.
  `PlannerMDGesellschaftDetailPage` statically imports the comment section (`CommentEditor`,
  so Tiptap and ProseMirror), the full published list with toolbar and filter pills, and the
  deck-code path (`pako`). The route's second wave is 31 chunks and 1.3 MB uncompressed on top
  of the 1.4 MB entry graph, and first content waited for its last chunk (3040 ms of a
  3207 ms cold load from Korea). The viewer renders notes through `NoteEditor`, so Tiptap stays
  on the critical path until notes render without the editor. The page is three sequential code
  stages after the HTML: the entry graph (25 chunks, 1.43 MB raw on every page: `react-dom`
  532 KB, `zod` 251 KB, the router 225 KB, `tailwind-merge` 100 KB), the route (36 chunks,
  1.32 MB), and game data that render-time hooks `import()` (9 chunks: battle keyword, EGO gift
  and identity spec lists at 110–162 KB each), each stage starting only after the previous one
  executes. Alongside them the page requests 216 images (7.2 MB), the largest about 200 KB each
  for the below-the-fold list. Worth doing when detail-page load time becomes a target: lazy
  the comment editor, the below-the-fold list and the export path, then decide the note
  renderer and whether game data joins the route stage.
- 2026-09-23 — ADR 010 records that the client renders an optimistic view increment;
  `frontend/src` has none (`viewCount` is only rendered). ADR 116 moves recording to
  `POST .../viewcount` from the mounted page and keeps the best-effort buffer, so the rest of
  ADR 010's view clauses stand. Worth doing when the page starts sending that POST: either
  render the increment there or correct ADR 010's detail in place.
- 2026-09-23 — The production build's `VITE_API_BASE_URL` ends in a newline: the shipped
  `index.html` carries `href="https://api.dante-planner.com\n"`. URL parsing strips it, so
  fetches and the preconnect work. The value lives in the hosting build environment, not the
  repo. Worth fixing the next time that environment is edited, or before the value reaches a
  consumer that does not parse it as a URL.
- 2026-09-23 — Production still answers a missing planner on
  `GET /api/planner/{plannerId}/comments/events` with 500: the handler renders the 404 as JSON
  against `Accept: text/event-stream`, fails with `HttpMediaTypeNotAcceptableException`, and
  Tomcat logs an ERROR. The RFC 9457 migration (ecb85775, 2026-09-18) fixed it in code, with the
  red test in `PlannerCommentSseControllerIT` ("answers 404 ... when the planner is not
  published"), but the running pods date from the 2026-09-17 13:52Z deploy. Every 5xx in the
  metric window is this: 1,422 on Oregon from 2026-09-17 14:51Z to 2026-09-18 04:31Z, one guest
  client re-subscribing to one missing planner every ~13 s (Loki, pod backend-vhht9). Leaves at
  the next deploy once that URI shows no 5xx. An anonymous harness subscription that once
  answered 500 does not reproduce against the migrated handler: over MockMvc and over real HTTP,
  with and without `Accept: text/event-stream`, a published planner streams with 200 and a
  missing one answers 404 as `application/problem+json`.
- 2026-09-23 — Intent preload (`defaultPreload: 'intent'`) is off because the published-detail
  loader records a view. It also preloaded route chunks on hover, which hides part of a chunk
  wave per navigation. Worth doing once view recording leaves the loader: restore it.
- 2026-09-24 — The frontend still pulls planners with `POST /api/planner/md/batch` and still
  declares `upvotes` in `ServerPlannerResponseSchema`. Pages publishes from `main` minutes after a
  merge while `deploy-fleet.yml` waits for PR Gate's push-run, so a frontend change that needs a
  new backend breaks sync until the backend lands: under `.strict()` the schema rejects the old
  backend's `upvotes`, and the old backend answers `GET /batch` through `GET /{id}` with 400.
  Worth doing once the backend serving `GET /api/planner/md/batch?ids=` and omitting `upvotes`
  is live in both regions: switch `plannerApi.batchChunks` to the GET and drop the schema's
  `upvotes` line. ADR 123's `write_forwarding = "all"` waits on this release.
- 2026-09-24 — `POST /api/planner/md/batch` and its request-body path stay beside the GET for
  clients that have not switched, including tabs opened before the switching release, which keep
  their old bundle until reloaded. Worth doing once the frontend release using the GET is live and
  `http_server_requests_seconds_count` for that URI with method POST stays at zero for seven days
  in both clusters (label names unverified against the Mimir keep-list): delete the POST handler.
- 2026-09-24 — `terraform/cloudflare/README.md` "Applying" shows bare `terraform init/plan/apply`
  with no `-var-file`, against the repo rule that Terraform runs through
  `scripts/ops/terraform-run.sh` and against `environment.tfvars.example`, and `access.tf` fails
  `terraform fmt -check` (`duration` misaligned). Worth doing on the next edit of that stack.
- 2026-09-24 — The `it` profile keeps `spring.main.allow-bean-definition-overriding=true` only
  because `EffectPlacementIT.OutboxHarness` and `PlannerReconcilerIT.ReconcilerLockHarness`
  redefine `lockProvider`; with overriding on, any test bean that shadows an application bean by
  name does so silently, which is how a bare IT-only `ObjectMapper` once hid a serialization
  defect. `application-test.properties` sets the same flag for reasons not checked. Worth doing
  the next time either harness changes: give the harness beans distinct names with `@Primary`
  and drop the flag.
- 2026-09-24 — `UserService.java:59-61` says a bare derived finder "is readOnly and would hit a
  replica", but a read-only IT of the comment-SSE path showed a declared repository method
  (`existsPublishedById`) called outside a transaction counting as undeclared primary access, and
  an audit of spring-data-commons 3.5.13 found declared methods do not inherit
  `SimpleJpaRepository`'s `readOnly`. If that holds for derived finders too, the comment's premise
  is wrong (the recovery lookup still reaches the primary, so behavior is unaffected), and
  `ModerationAuditService.latestReason` on `GET /api/auth/me` and the first finder in
  `findOrCreateUser` reach the primary undeclared. Worth settling with a counter assertion the next
  time either class is touched.
- 2026-09-23 — ADR 015 skips the filter rebuild when content composition is unchanged, but a
  harness save of a published planner that re-sent identical content with a new title still ran
  `CALL rebuild_planner_filters`, on both warm and freshly evicted pools.
  `PlannerCatalogService.searchableCompositionChanged` compares the raw content strings, so a
  normalization between the request and the stored value is the likely cause (unverified). Each
  needless rebuild is a second transaction on the save's request thread. Worth doing through
  `diagnose` with the write-forwarding rollout, or sooner if publish latency is examined.
- 2026-09-23 — `X-Served-By` is missing from every response Spring Security writes (CSRF 403,
  unauthenticated 401, and anything else rejected in the security chain): `ServedByFilter` is a
  plain `@Component` without `@Order`, so it registers after the security filter chain and never
  runs for a rejected request. Region attribution for exactly the error responses therefore
  has to come from `cf-ray` and load-balancer logs instead. Worth doing the next time a 4xx needs
  attributing to a region: order the filter ahead of security.
- 2026-09-23 — Whether an origin `Set-Cookie` still blocks caching once a Cache Rule makes a
  JSON path eligible is unmeasured. Default behavior is measured: through the real edge, origin
  `Set-Cookie`, `no-store`/`no-cache`/`max-age=0` and `private` each bypass the cache on a
  cacheable extension, request cookies stay out of the cache key, and extension-less JSON is
  never eligible without a rule (playground lab `cf-cache-cookie-probe`, pass 1). The second pass
  needs a token with Cache Rules edit on the zone; the staging token gets 403 on zone rulesets.
  Worth doing once the cookieless content endpoint exists, against that endpoint rather than the
  synthetic origin, before the Cache Rule is relied on in production.
