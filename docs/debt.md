# Debt

Parking lot for found work, pruned on every write. Not a queue: entries are captured so
they stop occupying attention, and are pulled only by a deliberate defrag or design
session. Each entry names the condition under which it becomes worth doing. An entry
leaves when the work landed, when its condition can no longer occur, or when a design
session judged it not worth doing; retirement is deletion, and the git history is the
record.

The data checkout behind `static/` keeps its own `static/docs/debt.md` for the data and
asset pipeline.


- Frontend planner content has two shape checks that never meet: the load gate is a deliberately
  loose `z.record(string, unknown)` (a strict gate mass-discarded saves after the Zod 4 bump,
  4463accf), strict `MDPlannerContent*Schema`s exist only inside `validateSaveablePlanner`, and the
  sync-boundary validator re-derives shape by hand. Worth doing when the shared content schema
  lands: take the sync boundary's shape half from that schema and keep the load gate loose.
- Planner content is held twice once the typed tree lands: the stored JSON (what the entity
  writes) and the typed tree (what rules and consumers read). Writing the tree back instead would
  leave one source, but the serializer must keep every raw sinner key as received, null apart from
  absent, floors past the category's count untouched, and section notes opaque, or `PlannerChange`
  sees every resend as a change and ADR 136's hidden floors are lost. Worth doing when a consumer
  needs a content shape the stored JSON cannot give, or the double hold causes a drift bug.
- A 429's `Retry-After` is on the wire, but `CorsConfig` does not list it as an exposed header, so
  the frontend's cross-origin fetches cannot read it (inferred from the config, not checked
  against prod), and the SSE client waits its own reconnect delay regardless. Worth doing when a
  client is meant to honor the server's wait: expose the header and read it in the retry paths.
- Rate-limit changes that bypass `rate-limit.config-version`: an environment variable (relaxed
  binding, e.g. `RATE_LIMIT_CRUD_CAPACITY`) or a profile override (`application-loadtest.properties`)
  changes a limit without the version, so live buckets keep the old one; and a rollback past a
  version bump keeps the newer limits until the bucket TTL, because bucket4j ignores a lower
  version. `deploy/` sets no rate-limit values today. Worth doing the first time a limit is set
  outside `application.properties`.
- ADR 079's @report and @gates decisions are not in the tree: `NotificationType.REPORT_RECEIVED`
  has no producer, effect arm or `DomainEventType` counterpart (it appears only in the enum), and
  no matrix test requires every notification type to be wired, so the unwired value compiles as
  the ADR says it must not. Worth doing before the next notification type is added, or before
  moderators are expected to hear about reports in-app.
- The SSE shutdown drain (`AbstractSseService`'s `ContextClosedEvent` listener) completes emitters
  on the closing thread, and `complete()` takes the same write lock a send holds while blocked on a
  stalled client's full receive window, so one stalled peer delays shutdown until the socket write
  times out (the connector's default, unverified). Worth doing if a pod is seen exceeding its
  termination grace with an SSE send on the closing thread's stack; handing completion to another
  thread needs an exception to the no-new-pools rule.
- No workflow renders or validates `deploy/` before ArgoCD syncs it from main: `pr-gate.yml` has
  no deploy lane and `deploy-fleet.yml` runs `kustomize edit set image` only, so a broken overlay
  surfaces at sync time. `deploy/overlays/oregon/kustomization.yaml:11` also names a
  `deploy-oregon.yml` that does not exist (the bumper is `deploy-fleet.yml`). Worth doing the next
  time a base manifest or overlay patch changes shape: a PR-gate step running `kustomize build`
  (v5.8.1, as deploy-fleet pins) on both overlays plus a schema check.
- `frontend/scripts/` sits outside every gate but tests: `yarn lint` and `yarn fmt:check`
  cover only `src`, and the older scripts (compiler-bailouts, generate-sitemap,
  detect-hardcoded-text, dev-r2-sync) are in no tsconfig, so they are never typechecked.
  Worth doing when a script change breaks CI or a deploy.
- `/planner/md/gesellschaft` and `/planner/md/new` inherit the `/planner/md/:id` rule's `Link`
  preloads: Pages applies every matching `_headers` rule and a `:id` placeholder matches a
  static sibling segment, so those pages also fetch the personal viewer's chunks. The dynamic
  rule keeps its preloads on purpose. Worth doing when `! Link` detach semantics can be
  verified against a real Pages deploy, or when the sibling pages' load time regresses.
- `POST /api/auth/apple/callback` is a stub that always answers 400, and a real Apple
  `form_post` would be rejected by `CsrfDoubleSubmitFilter` (no `X-CSRF-Token`) before the
  rate limiter runs. Worth doing when Apple sign-in is built: exempt the callback from CSRF
  and replace the stub.
- `RateLimitInterceptor.skipCharge`'s fail-closed rethrow (the typed 503) has no production
  caller since both OAuth callbacks redirect, and no interceptor-level test. Worth doing the
  next time a fail-closed policy gains a non-navigation endpoint, or when the branch is judged
  dead.
- `spring.jpa.properties.hibernate.jdbc.batch_size=30` and `order_inserts=true` have no bulk
  insert left to serve and no test guarding them since the import endpoint was removed, and
  `PlannerLimitValidator.requireRoomFor`'s batch count is always 1. Worth doing when either is
  next touched: drop the settings and the parameter, or add the bulk path that needs them.
- The migration smoke seed's planner 1 still carries enhanced gift ids (19001, 29004) in its
  entity filter rows, which `rebuild_planner_filters` has collapsed to base ids since V055, so
  the seed is not a faithful post-migration state. Worth doing the next time the seed changes.
- `ContentTombstoneStoreTest`'s two `…WhenRedisFails_LogsAndDoesNotThrow` tests attach a logback
  `ListAppender` and saw no event once in a parallel unit run (passes alone and on the rerun), which
  points at another test reconfiguring logback concurrently (unverified). Worth doing when it fails
  a CI run: diagnose with a repeated parallel run before touching the appender.
- Rate-limit assertions flake under full-suite load: a charge that times out on the rate-limit
  Redis fails open, so a test expecting a refusal or a charged bucket sees none
  (`PublishedPlannerDeviceCookieIT` device bucket, `PlannerCommentSseControllerIT` 429). Both pass
  alone. Worth doing when either fails a CI run: give rate-limit tests a timeout budget or a
  dedicated Redis.
- The PR gate's migration job only replays new `*.sql` files through the Flyway CLI, so a Java
  migration (`db/migration/V0NN__*.java`; none exists now, the keyword backfill moved to the
  startup scheduler `PlannerKeywordBackfill`) would never run against the populated seed in CI;
  a Java migration that calls live `PlannerKeywords` would also change on a later rename, and
  Java migrations carry no checksum. Worth doing before
  the next Java migration: run the backend's Flyway (Java included) over the seed in CI, and
  freeze a migration's logic inside the migration class.
- `PlannerRepositoryConstraintIT` "Different user same planner allows separate votes" deadlocked
  once on the `planner_votes` insert during a full suite run and passes alone. Worth doing when it
  fails a CI run.
- Coexistence window (ADR 030 @projection): `UpsertPlannerRequest.selectedKeywords`,
  the `planner_keywords_toplevel_total` metric and the e2e payload's
  top-level copy are removed once the frontend build that omits the copy
  (`feat/keyword-policy-c-fe`) is live and the metric's match/mismatch outcomes have read zero for
  seven consecutive days.
- The plan-keyword skill (`.claude/skills/plan-keyword/SKILL.md`) is stale: it names
  `KeywordSetConverter` for the valid set (now `PlannerKeywords`), `frontend/src/lib/constants.ts`
  (now `shared/gameData/constants.ts`), a SET-migration procedure on a dropped table, and
  `MySQLIntegrationTest` (now `MySQLIT`), and it predates content-derived keywords. Worth doing
  before the next keyword is added.
- `UserAccountLifecycleService.reactivateAccount` loads the user without a lock, so a reactivation
  racing a deactivation of the same account is unordered (deactivation and publish are serialized by
  owner-row locks). Worth doing if an account shows planners published while deactivated or
  markers left after reactivation.
- `.githooks/commit-msg` lacks the commit type/scope grammar; the local
  `core.hooksPath = .githooks` bypasses the global hook, so duplicate or source
  `~/.config/git/hooks/commit-msg` here.
- No test proves the username-suffix retry survives a real Hibernate session after a
  failed INSERT — only the exception classifier is covered by synthesized exceptions. A
  containerized test driving a live suffix collision through the retry loop would close it.
- `PlannerContentEntityExtractor` keeps the pre-`path()` Jackson null-check idiom (~10
  copies). Ruled out of the validator-traversal conversion (it coerces where validators
  reject, and lives in `planner/searchindex`, outside `planner/validation`); local `path()` adoption would collapse
  the checks without publishing the validation helper.
- Toggle endpoints flip state instead of converging on it; make them idempotent the way
  planner publish is (repeat calls are no-ops, not inversions).
- A full review pass on `shared/` and `user/` is outstanding.
- FE `selectedKeywords` schemas still allow `.nullish()` although the two response DTOs
  now always emit an array; tighten to a plain `z.array(z.string())` and drop the
  now-dead `?? []` fallbacks in the two consumers.
- Stale "a fresh account's syncEnabled is null" comments in `e2e/src/plannerFixture.ts`
  and `e2e/tests/mutation-gestures.spec.ts` — behaviorally fine post-V056, textually
  outdated.
- The frontend reads no `?login=` value (`error`, `rate_limited`, `unavailable`), so every
  OAuth failure redirect lands on a plain home page; closing it needs `validateSearch` on
  the `/` route, a toast in `GlobalLayout`, and `login.*` copy in every locale. Worth doing
  when a user reports a silent failed login.
- `CommentEngagementService.toggleUpvote` inserts a `PlannerCommentVote` and then runs
  `incrementUpvoteCount`, which is `@Modifying(clearAutomatically = true)` without
  `flushAutomatically`; whether auto-flush covers the pending vote insert depends on
  query-space overlap between `planner_comments` and `planner_comment_votes`, which do
  not overlap. No IT asserts that a second comment upvote returns 409, so the vote
  row's durability on that path is untested. Add the duplicate-upvote IT (and consider
  `flushAutomatically = true` on the increment).
- `DegradationIT.evictPooledPrimaryConnections` still cannot evict the primary pool: the
  routing target for PRIMARY is the `GtidCapturingDataSource` wrapper, not a
  `HikariDataSource`, so the helper's instanceof walk skips it. Replica and bulkhead are
  covered since the lazy-proxy descent fix; the primary needs an unwrap step.
- No circuit breaker fronts any Redis role or the primary datasource's writes (one guards
  Oregon's primary read fallback); the 3 s command
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
- `user.entity` is a de facto shared domain model: `User`, `UserRole`, `RestrictionState`
  and `UserSettings` are read by 24 frozen origins across every feature, including
  `shared.security` and `auth.token`. The enum half (7 origins reach only `UserRole` or
  `RestrictionState`) is a mechanical move once a shared account vocabulary exists. The
  `User` half (17 origins) is not: `Planner` maps `User` as a JPA association, so the
  alternative is id-valued references across aggregates, a schema and query change. Closing
  it means deciding what a planner knows about its owner, the row or the id — a shared
  kernel the rule permits by design, or the ADR 015 decomposition applied to users. Argue
  that before moving anything; a thaw done to satisfy the rule is the wrong reason.
- Backend patterns with no build rule yet, candidates for checkstyle under ADR 070: field injection
  (`@Autowired private`), entity-typed `ResponseEntity` returns from controllers, `.get()`
  on `Optional`, `@Transactional` on private methods, string concatenation inside `@Query`,
  empty catch blocks, `@RequestBody` without `@Valid`, change-history phrasing in durable
  records, `Boolean.TRUE.equals(...)`, plus the file-specific pairs (`@Transactional` in a
  Controller, `@Query` in a Service). Each needs either a checkstyle equivalent — a
  `RegexpSinglelineJava` id with id-scoped suppressions, or an ArchUnit rule where the check
  is structural — or a stated reason it needs no build rule. The frontend patterns the same
  hook declared are listed for migration to oxlint or ast-grep: `PropTypes` (Zod schemas
  instead), `useQuery(` (`useSuspenseQuery` instead), hardcoded hex colors outside
  `constants.ts` and tests, manual memoization (`React.memo`, `useMemo`, `useCallback`), an
  early `return` of a loading spinner instead of a Suspense boundary, `localStorage` or
  `sessionStorage` access without a `typeof window` guard, and `useTranslation()` without an
  explicit namespace (escape comment `// i18n-ok`; `i18n.ts` and `LanguageSync.tsx` excluded).
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
- `HorizontalThemePackGallery.getFloorIndexForPack` returns `-1` for a pack absent from
  `floorSelections`, which yields the note key `floor--1` and renders "Floor 0".
- The full-suite worker OOM is survivable with `vitest run --maxWorkers=1` (green at 216
  files / 7814 tests where `--maxWorkers=2` intermittently loses a worker on a loaded box).
  That is a workaround, not the pool-tuning pass the earlier entry asks for.
- `deckFilterFacets.parity.test.ts`
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
  across every catch block.
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
  freeze names `V045` as the current head against an actual head of `V065`.
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
- 2026-08-14 — ADR enforcement is a decided practice with nothing implementing it. A subagent
  audit checks four things: each ADR's stated invariants against the code, whether an
  enforcement mechanism exists for each rule that claims one, decisions recorded as done that
  were never carried out, and whether the names an ADR relies on are still live — RFCs
  included. It triggers from wrap-up for any session touching `docs/adr/` or backend
  architecture, and emits unstaged supersession edits plus entries in this file, never a
  rewrite of an existing ADR. Closes when the wrap-up skill invokes it.
- 2026-08-14 — Two live docs still describe the planner sync SSE events removed by RFC 0003
  Stream 3: `docs/multi-region-request-paths.md` lists `created`/`updated`/`deleted` as the
  envelope-delivered family, and `docs/testing-evidence.md` quotes
  `rows.put(SseEventType.CREATED, ...)` as its matrix-test exemplar, a constant that no longer
  compiles.
- 2026-08-14 — The `planner_bookmarks` table has no writer left after RFC 0003 Stream 1
  removed the bookmark write path, and the `KnownConstraint.PLANNER_BOOKMARK` mapping is gone.
  The table is still created (V006), repointed (V052) and never dropped; only tests touch it.
  Dropping it is one decision that was not in Stream 1's change list.
- 2026-08-14 — `docs/runbooks/schema-decomposition-migration.md` gates the deprecated-toggle
  retirement on `planner.legacy_toggle` reading ~0, but RFC 0003 Stream 1 removed the counter's
  last emitter, so the metric now reads *no data*, never zero — both gates are unactionable as
  written and the runbook needs its retirement condition restated (arguably: satisfied).
  `docs/multi-region-request-paths.md` §11 also still routes the deleted
  `POST /{id}/bookmark`, and `frontend/scripts/hardcoded-text-report.json` still carries an
  entry for the deleted `usePlannerBookmark.ts`.
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
- 2026-08-14 — frontend/src/pages/planner/lib/editorStateCodec.ts:216 places a bare '' in a JSONContent slot: a genuine
  production type/runtime mismatch that stream 7b typed around in its test rather than fix
  (production out of that node's scope). Fix the slot's type or its value at the source.
- 2026-08-14 — buildSaveablePlanner pins id to a UUID via its schema, so the two planner page
  tests asserting on 'test-planner-123' cannot adopt the factory (six assertion sites). Either
  the tests move to schema-valid ids or the factory grows a test-id affordance.
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
- 2026-08-14 — the batch-conflict epoch exceeds 1 in production only when `syncKey` changes
  (`useMDUserPlannersData.ts:143` runs one sync per PersonalPlannerList mount unless `syncKey`
  changed), so the multi-batch semantics it encodes run rarely; if one is added, the empty→non-empty
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
- 2026-08-16 — Every compose flavor (default+override, oauth-gtid, multiregion, e2e, loadtest)
  runs under the same default project name, so they all mount the one
  `limbusplanner_mysql-data` volume. MySQL applies env credentials only on first init of an
  empty datadir, so switching flavors leaves the datadir on the previous stack's passwords
  and the backend fails at Flyway with error 1045. Found work: isolate the flavors — a
  distinct volume name or an explicit `name:`/`COMPOSE_PROJECT_NAME` per flavor — so a stack
  switch cannot poison dev credentials.
- 2026-08-16 — Dead client surface found by coverage audit: the `settings:invalidated` SSE event constant (declared in `lib/constants/api.ts`,
  no registered handler), `usePlannerStorage.clearCorruptedLocal` (no consumer), and the
  `my-plans` branch of `pages/planner/components/plannerList/PlannerCardContextMenu.tsx` (never mounted).
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
- 2026-08-16 — `stack:down` (in `e2e/package.json`) composes only the base file set, so after `stack:up:rig` it
  orphans four running services (`backend-oregon`, `mysql-replica`, `oauth-stub`,
  `toxiproxy`) — verified live by diffing `config --services` against running containers.
  Add a `stack:down:rig` mirroring the rig's file set, or `--remove-orphans` to
  `stack:down`.
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
- 2026-08-17 — ADR 082 declares it supersedes 071's `@sync @digest` bullets "once the
  arbitration is implemented", but carries no `supersedes:` key and left 071 with no
  `## Superseded` section. The condition is met — `SyncVersionValidator.arbitrate` and
  `PlannerChange` are live, `V060` dropped `content_digest`, and no Java or TS
  outside those two migrations references a digest — so 071 reads as a live decision for a
  column that does not exist, and the repo's own convention (status derived from structure)
  agrees with the wrong side. `docs/rfcs/0003-sync-identity-and-effect-delivery.md` and
  `0005-server-noop-conflict-arbitration.md` still show the column as target state.
- 2026-08-17 — `PlannerPublishingService.java:73` passes
  `upserted(userId, deviceId, plannerId, content)` as an argument, so the whole upsert — field
  mutation, `recordSave()`'s version bump, `onVisibleEditCommitted`, and `createAggregate`
  for an id the server has never seen — runs before `applyPublish` reaches `requireOwner`
  (`:99`), `requireTitle` (`:101`), the `Stage.PUBLISH` content gate (`:102-103`) and
  the takedown refusal in `Planner.publish()`. A stale `syncVersion` on a taken-down planner answers 409 from arbitration
  before the 403 the state warrants, which opens the client's conflict dialog for a planner
  that can never publish.
- 2026-08-17 — Several find-then-insert paths rely on a real database constraint without the
  handled violation ADR 023 pairs it with, so the race answers `UNEXPECTED_CONFLICT` plus a
  Sentry page instead of a domain outcome: `RecommendedSql.java:19` inserts into
  `planner_catalog` with no `IGNORE`/`ON DUPLICATE KEY` against its primary key (reached from
  `PlannerCatalogService.java:88` on unban), `UserSettingsService.java:98` (reached from
  login and from every SSE connect), `PlannerSubscriptionService.java:79-85`, and the bare
  `planner_stats` inserts at `PlannerCommandService.java:279-281` and `:520-522`.
- 2026-08-17 — A local draft kept through a tombstone can never be saved again. ADR 088 keeps
  it (`syncPlan.ts:61` purges only non-draft rows), the server has no resurrect path —
  `findAggregateForOwner` filters `deletedAt` (`PlannerRepository.java:65`), the pre-create
  probe does not (`:131-132`), and `planner/precondition/PlannerOwnershipValidator.java:24-25` answers 404 for the
  owner's own tombstone, with `deleted_at` never cleared anywhere — and `performSave`
  (`usePlannerSave.ts:477-489`) returns before `storage.saveToLocal` on failure, so the manual
  write is discarded too and `hasUnsyncedChanges` stays true. The 404 classifies as
  `notFound` and presents as the generic resource-not-found message; whether the client should
  drop the row, unlink it, or offer a re-create is undecided on both sides.
- 2026-08-17 — `status` is read as a per-device local fact by `syncPlan.ts:30,61` and
  `deriveSaveStatus`, and as a replicated column by the wire (`usePlannerSyncAdapter.ts:96`,
  adopted on every pull at `:52`, compared by `PlannerChange.java:22`). The two
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
  `conflict.localPlanner.metadata.title` (`:221`) and the "copy will not be published" notice
  gates on the local row's flag (`:234-236`) — while the import caller plans with
  `forkSide: 'incoming'` (`PlannerExportImportSection.tsx:345`) and forces `published:
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
  (`useMDUserPlannersData.ts:125,134`, `useSavedPlannerQuery.ts:21`) because the source is
  IndexedDB. The only server read on that path is the one-shot sync effect
  (`useMDUserPlannersData.ts:222-288`) gated per mount by `hasSyncedRef`, and the personal
  detail route reads IndexedDB alone (`PlannerMDDetailPage.tsx:57`), so the promised "a delete
  performed on another device surfaces as a 404 when the stale entry is opened" happens only
  on the community route. Freshness is per-mount, not per-focus, on exactly the path SSE
  covered.
- 2026-08-17 — `converter/KeywordParityTest.java:65-71` pins `PlannerKeywords.VALID_KEYWORDS` to the
  members of the latest `ALTER TABLE planners MODIFY COLUMN selected_keywords SET(...)`
  migration, but `V052:116` dropped `planners` and the live column is
  `planner_content.selected_keywords JSON` (`V049:29`). The test passes against a fossil
  (`V045`), and adding the next keyword by the procedure it implies means writing a migration
  against a missing table — green test, failed deploy. ADR 030 also justifies its filtering
  rule as forced by a column type that "physically rejects non-members", which stopped being
  true at V049.
- 2026-08-17 — The base-gift collapse is expressed three times and the frontend copy
  disagrees with the two backend copies on ids outside the enhancement bands:
  `planner/searchindex/PlannerContentEntityExtractor.java:129-132` and `V055__normalize_ego_gift_filter_ids.sql:50-52`
  pass them through and index them, while `pages/egoGift/lib/egoGiftEncoding.ts:29`'s
  `^([12])?(9\d{3})$` drops them. The same filter term answers differently on the published
  list (server index) and in "My Plans" (local predicate,
  `pages/planner/lib/plannerContentExtractors.ts:199-203`), and each side is pinned by tests to its own
  contract. ADR 039 names this population directly — the seed carries gift ids outside every
  valid band. The validation branch adds a fourth backend copy (`EncodedGiftId`) beside
  `GameDataRegistry.GIFT_ENHANCEMENT_PATTERN`, which `PlannerIdMigrations` also reads.
- 2026-08-17 — `PlannerCoreInfo.absent()` yields a null `createdAt`
  (`PublishedPlannerQueryService.java:54` uses it whenever the batch core load misses a row)
  and the global `NON_NULL` Jackson config omits the key, but `PlannerListSchemas.ts:19`
  declares `createdAt` required, so one catalog row without a core row rejects the entire
  paginated community page rather than one card. The sibling fields on the same DTO are
  correctly `.nullish()` (`:44,46`) and the components already tolerate an absent value.
- 2026-08-17 — `AbEventSchemas.ts:5` types `relatedEgoGifts` as `z.array(z.string())`
  rather than `EGOGiftIdSchema`, and 13 ids in `static/data/abEventSpecList.json`
  (`991001`–`993004`, six digits) violate `GIFT_ID_PATTERN`. `EGOGiftGrid.tsx:34-35` renders
  nothing for them, silently; the branded schema would have failed `dataIntegrity.test.ts` on
  the file instead. `giftListItem.ts:28` parses the same values through `EGOGiftIdSchema` and
  would throw.
- 2026-08-17 — `usePlannerFork.ts:58` still parses the published payload with an unguarded
  `JSON.parse` and assembles it with `toSaveablePlanner` (right after it), skipping the
  `validateSaveablePlanner` gate its sibling `usePublishedPlannerQuery.ts:106-133` gained, then
  writes the unvalidated content to local storage at `:138`. `usePlannerSyncAdapter.ts:41-46`
  guards the parse but likewise skips the schema gate.
- 2026-08-20 — The nightly terraform drift check has never been able to run, and its
  schedule is now off until it can. Three independent blockers. (1) Credentials: the job
  assumes `secrets.AWS_PROVISIONER_ROLE_ARN` while declaring no `environment:`, so it mints
  the OIDC subject `repo:phrimm136/dante-planner:ref:refs/heads/main`; the prod account's
  `prod-provisioner` trusts `repo:phrimm136/dante-planner:environment:production`, and the
  run fails with "Could not assume role with OIDC: Not authorized to perform
  sts:AssumeRoleWithWebIdentity". Declaring `environment: production` would satisfy the
  trust but attaches that environment's required reviewer, which an unattended cron cannot
  clear — an ungated environment plus a matching `sub` condition on the role is the shape
  that works. (2) Inputs: `TFVARS_OREGON`, `TFVARS_SEOUL`, `TFVARS_RDS`, `TFVARS_SECRETS` and
  `TFVARS_CLOUDFLARE` do not exist at repository scope or in any environment, and the workflow's own note
  explains the consequence — "a partial set is worse than none: variables that gate a
  resource default to empty, so a plan missing one reports the resource's destruction as
  drift". (3) Account: the workflow writes `backend.hcl` as
  `danteplanner-tfstate-${AWS_ACCOUNT_ID}`, which since the account cutover resolves to the
  prod bucket, so the stack list has to describe prod and only prod. Applied here:
  `global-accelerator` dropped from `STACKS` because it lives in the management account and
  holds the rollback addresses for the bake, and `cloudflare` added, which needed workspace
  support in `scripts/ops/terraform-drift-check.sh` — that stack keeps a `prod-fleet`
  workspace alongside `default`, and planning without selecting reports the whole stack as
  absent. Re-enabling the cron needs the five secrets populated with prod values and the
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
- 2026-09-11 — `ApiExceptionHandler` logs `PLANNER_NOT_FOUND` at WARN for a DELETE of a row
  the server never received, a routine outcome the client already treats as success (about 5 a
  day). Worth demoting that one method-and-code pair to INFO when the WARN stream is next used
  for alerting, or when an ADR settles a client marker for never-synced rows.
- The keyword browser builds its own grid rather than `FilteredEntityGrid`, so it still
  renders `useProgressiveCount` batches and loses a deep scroll offset on return from a
  keyword detail page. The `CardGeometry.units` field this entry once named no longer exists (`CardGeometry` is `size`,
  `mobileScale` and `rows`); recheck whether the shared grid now fits a card with no fixed height
  before folding;
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

- 2026-09-16 — about 85 `<Skeleton>` elements across 42 files still carry their own `w-`/`h-`
  box (41 of them inline `Suspense fallback={<Skeleton className="h-… w-…" />}` text stubs),
  all outside the `*Skeleton*.tsx` files the size-roots unit converted and therefore outside
  `no-sized-skeleton-tsx`, which scans only those. Condition: when a page carrying one is next
  edited, swap the stub to `TextSkeleton` (for text) or a `CardSlot` (for a card box).

- `CommentModerationService.deleteCommentByPublicId` writes the audit row before it checks whether
  the comment is already deleted, so a repeated delete leaves a second DELETE_COMMENT record;
  `deleteComment` by id does not. Worth fixing when the moderation audit trail is read as a count.
- `CommentReportService.hasReported(Long, Long)` has no main-source caller, only its test. Worth
  deleting the next time comment reporting changes.
- `GtidPinExitTest.readGet_WhenReplicaRejectsGtidAsMalformed_ClearsUnpinnedAndWarnsOnceWithoutStack`
  failed once at full suite size and passed on retry and on a full rerun (2026-10-04, Lane D S4
  gate). It counts warning log events, so it likely depends on order or logging state. Worth
  fixing when it fails a second time, or when the retry gate flags it in CI.
- The MDCategory `Converter<String, MDCategory>` in `planner.config.PlannerWebConfig` has no MVC
  consumer: no request parameter or path variable of that type exists. Worth deleting the next
  time planner web config changes.
- `backend/CLAUDE.md` says an import order is enforced, but checkstyle has no ImportOrder module.
  Worth settling (add the module in `config/checkstyle/checkstyle.xml` or drop the claim) when an
  import-order dispute comes up in review or checkstyle config is next touched.
- Ending all of a user's sessions takes two steps: a per-user cutoff (`invalidateUserTokens`,
  rejects `iat` below a stamp floored to the second, so a same-second re-login survives) plus a
  blacklist of the request's own tokens to close that second for the current device. Tokens another
  device received in the same second still pass. A per-user version claim (bumped on logout-all and
  deletion, compared on validation) would end every session in one step with no time window. Worth
  doing when the token format changes for another reason, or when a same-second bypass is reported.
- `TokenBlacklistService.clearUserInvalidation` has no main-source caller. Worth deleting with the
  version-claim change above, or the next time the blacklist service changes.
- `ConventionBaselineTest.DOMAIN_EXCEPTION_HANDLERS_FROZEN` has no staleness check, so an entry can
  outlive the handler it names. Worth adding the check the next time an exception handler moves.
- Frontend vitest levers, measured 2026-10-04 on an idle box: default 11 workers 56 s / 2.6 GB peak,
  6 workers 65 s / 2.3 GB, 4 workers 80 s / 2.1 GB, no OOM at any count. Capping workers trades speed
  for little memory; not worth it. Still unmeasured: `fs` + `JSON.parse` for static JSON instead of
  per-worker Vite transforms, a separate project for file-heavy data tests, removing the 20 partial
  `vi.mock` factories that block `isolate: false`, coverage on main only, TZ and heap in the config.
  Worth measuring when frontend test time becomes a bottleneck again.
- `PublishedPlannerSplitReadIT.viewCount_WhenClientExhaustsViewRecordBudget_...` expected 429 and got
  204 once, then passed on retry (2026-10-04, Lane D S5 gate run 1). The failed attempt took 3.0 s
  against 1.1 s, so the rate-limit bucket probably refilled mid-loop. Worth fixing with an injected
  clock for the bucket when it fails again.
- `AuthController.tokenValidator` and `AuthenticationServiceTest.response` are unused (errorprone
  UnusedVariable). Worth deleting the next time those files change.
- Two more retry-only failures at full size with in-fork class concurrency on (measurement runs,
  2026-10-04): `PlannerPublishEventIT.publish_WhenRolledBack_EmitsNoSseEvent` and a
  `PlannerResponseContractIT` no-upvotes case. With concurrency off, 0 of 3 runs retried (n too small to
  conclude). Worth tracing with the in-fork concurrency decision.
- The published-API rules check imports only. A native query can still read another module's table:
  `NotificationRepository:78` reads user's `user_settings`. Worth a rule over `@Query(nativeQuery = true)`
  text with a table-to-module map when the next native query crosses a module, and fixing that site.
- The published-API rules do not check that every `api` member has a caller outside its module, so an
  unused member can stay published. Worth an ArchUnit rule when an api package next grows.
- `.githooks/pre-commit` counts REJECTED only on the `- @` line (awk at lines 85-90), so every
  multi-line ADR bullet reports "no REJECTED clause". Worth fixing to read continuation lines the next
  time the hook changes.
- `PlannerForbiddenException` carries the same code and message for "taken down" and "not the owner",
  so neither the client nor a test can tell the two refusals apart. Worth splitting when the frontend
  needs to explain a takedown to its owner.
- `SseHeartbeatWorkerConfig` builds an 8-thread `ThreadPoolTaskScheduler` that the SSE services
  inject as a `TaskExecutor`, which the backend Async rule does not describe. Worth settling when
  the executor rule above is revisited or the SSE fan-out changes.
- The backend bans executors and `@Async` outside the outbox dispatch, where Spring practice
  allows one bounded executor with context propagation, a drain on shutdown and AFTER_COMMIT
  use. Worth revisiting when a request needs work run in parallel before it responds, such as
  several external calls, which neither the outbox nor a scheduler expresses.

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

## Alert import scripts other than import-alert-rules.sh duplicate rules on re-run (2026-08-30)

The Grafana provisioning API deduplicates by UID, not title. import-alert-rules.sh now gives
each rule a fixed uid and updates it in place; import-app-alert-rules.sh and
create-staleness-rules.sh still POST unconditionally, so re-running either against a populated
folder creates a second copy of every rule. Worth doing the next time either script must add a
rule to a provisioned stack: give each rule a fixed uid the same way.

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
- **`docs/legacy/runbooks/prod-account-cutover.md` still pins `danteplanner-mysql80`** on the
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

- `GtidCookie.decode` accepts untagged GTID sets only; MySQL 8.4's tagged form (`uuid:tag:n`)
  is treated as unusable and cleared, which drops read-your-writes for that client. The app sets
  no GTID tags today. Worth doing before anything sets `gtid_next` with a tag.
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
  average 7 KB (maximum 29.7 KB), so TCP slow start is at most a minor term. ADR 122 bans the
  chatter and ADR 123 moves writes beside the primary, where each round trip costs about 1 ms.
  Worth doing if write forwarding is rolled back, or when ADR 122's command-count test is built.
- 2026-09-23 — Two read paths take Seoul's primary across the WAN, and together with token
  rotation (about 5,800 a week) they account for the pool's ~80,000 weekly acquisitions against
  ~200 user writes. The view flush runs one primary transaction per detail read (6–8 round
  trips; 6 even for a repeat view that inserts nothing), up to 46,928 a week. `/api/sse/subscribe`
  loads notification settings through `UserSettingsService.getOrCreateEntity`, a read-write
  `@Transactional`, so every per-pod cache miss is a primary transaction (11 round trips when it
  creates the row), up to 27,335 a week. Neither is on a user's critical path, but both hold
  primary connections from the smaller WAN pool and add replication traffic. The settings load
  now goes through `UserSettingsService.getSettings` (read-only, creates no row), so that half is
  closed; whether a Seoul replica read right after an invalidation can cache a stale row for the
  five-minute TTL is unverified. The view flush leaves Seoul once ADR 116's `POST .../viewcount` is
  forwarded under ADR 123 (`write_forwarding = all`). Worth re-measuring the pool's acquisitions
  after that flip.
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

- Unit ADR 116: a public read still sets the `csrf` cookie (`CsrfDoubleSubmitFilter` mints it on
  every cookieless request) and carries Spring Security's `no-store`; the content endpoint must
  opt out of both before its Cache Rule is enabled.
- **The forwarded-for fallback trusts the leftmost hop.** `ClientIpResolver.firstHop` takes
  the client-written entry once the direct peer is a trusted proxy;
  `ClientIpResolverTest.resolve_WhenHeaderCarriesAChainOfHops_ReturnsTheLeftmost` pins that
  as the spec. Latent while `CF-Connecting-IP` is always present on the tunnel-only path. Replace
  with the container's rightmost-trusted parser (`ForwardedHeaderFilter` / `RemoteIpValve`) and
  an attacker-framed test. Worth doing before any ingress that bypasses Cloudflare exists.
- **Bucket configuration is validated per request, not at bind time.** `RateLimitProperties`
  is `@Validated` but constrains only `configVersion`, and its `BucketConfig` fields carry none
  (three of nine properties classes validated: Redis, OAuth, JWT; not re-counted), and
  `buildConfiguration` runs per request, so a zero duration fails on first use. The fix is the
  ArchUnit rule "every `@ConfigurationProperties` class is `@Validated`" and the sibling edits
  it lists; the rule, not the class, is the unit of work.

## 2026-09-18 consistency without an LLM (scheduled ratchet)

- **No scheduled run measures rule drift.** The gate runs the rule engines already wired
  (ArchUnit with freezing rules, checkstyle, oxlint plus `frontend/lint/entity-plugin.js`,
  ast-grep via `frontend/sgconfig.yml`) only on pull requests. A weekly workflow, next to
  `infra-suite.yml` and `verify-rds-ca.yml`, that runs every rule in report mode, diffs the
  per-rule violation count against a stored baseline, and opens an issue on any increase
  would make consistency a measurement rather than a per-session hope. ArchUnit's frozen
  stores are the baseline for Java; ast-grep `scan --json` plus a count file is the baseline
  for the rest. The same run is the home for the still-dormant gates: OWASP dependency-check
  and Sonar (declared, never run), and the backend patterns listed in the entry above. PIT is
  no longer dormant: its surviving-mutant ratchet blocks the PR Gate `test-mutation` job and
  runs as `pitestRatchet` in the local gate (ADR 146). Worth doing as the first rule-into-build session, since it is what makes every
  later rule measurable before it is enforced.

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
  (`PublishedPlannerQueryService.java:129`), and `recencySorted` orders by
  `firstPublishedAt DESC` with no unique tiebreaker, so rows sharing a timestamp can shift
  between pages. Worth doing when list p99 becomes a reported problem or a duplicate-across-
  pages report arrives: return a slice (limit+1) and append `plannerId` to the sort.
- 2026-09-22 — `Planner.java:62-69` declares the three inverse `@OneToOne(mappedBy=...)`
  sides without `fetch`, so they default to EAGER; no main-source caller uses an inherited
  `findById`/`findAll` today (every path goes through the `AGGREGATE_LOAD` JOIN FETCH), so
  the cost is latent. Worth pinning with `FetchType.LAZY` or an ArchUnit ban on inherited
  finders the first time a caller of an inherited `PlannerRepository` method lands.
- 2026-09-22 — `TokenBlacklistService.failOpen` increments `blacklist_check_skipped_total`
  and logs warn, but no alert rule references the counter and no rule covers Redis
  availability or command timeouts at all (`deploy/grafana/import-*-alert-rules.sh` grep
  `redis` → none; MySQL has `mysql-connectivity-lost`). A fail-open storm surfaces only via
  `backend-warn-sustained`. Worth doing before the next Redis incident drill: a rule on
  `increase(blacklist_check_skipped_total[5m]) > 0` and one on `redis_up == 0`.
- 2026-09-22 — `PlannerDriftAuditRepository.driftedUpvoteCounters` joins `planner_votes`
  without a `deleted_at IS NULL` predicate while `driftedCommentCounters` filters
  `planner_comments.deleted_at IS NULL`; V018 made votes immutable so the asymmetry may be
  correct, but nothing says so. Worth a one-line comment or a shared predicate the next time
  either query changes.
- 2026-09-22 — `logback-spring.xml:6` and `:13` header still describes "stdout → awslogs →
  CloudWatch /ecs/danteplanner/backend"; the pipeline is stdout → Alloy → Grafana Cloud Loki
  (`deploy/base/alloy-logs.yaml`). Delete the stale comment on the next touch of that file.
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
- Unit ADR 116: 2026-09-23 — Intent preload (`defaultPreload: 'intent'`) is off because the published-detail
  loader records a view. It also preloaded route chunks on hover, which hides part of a chunk
  wave per navigation. Worth doing once view recording leaves the loader: restore it.
- Unit ADR 123: 2026-09-24 — The frontend still pulls planners with `POST /api/planner/md/batch` and still
  declares `upvotes` in `ServerPlannerResponseSchema`. Pages publishes from `main` minutes after a
  merge while `deploy-fleet.yml` waits for PR Gate's push-run, so a frontend change that needs a
  new backend breaks sync until the backend lands: under `.strict()` the schema rejects the old
  backend's `upvotes`, and the old backend answers `GET /batch` through `GET /{id}` with 400.
  Worth doing once the backend serving `GET /api/planner/md/batch?ids=` and omitting `upvotes`
  is live in both regions: switch `plannerApi.batchChunks` to the GET and drop the schema's
  `upvotes` line. ADR 123's `write_forwarding = "all"` waits on this release.
- Unit ADR 123: 2026-09-24 — `POST /api/planner/md/batch` and its request-body path stay beside the GET for
  clients that have not switched, including tabs opened before the switching release, which keep
  their old bundle until reloaded. Worth doing once the frontend release using the GET is live and
  `http_server_requests_seconds_count` for that URI with method POST stays at zero for seven days
  in both clusters (label names unverified against the Mimir keep-list): delete the POST handler.
- 2026-09-27 — `UserService.findOrCreateUser`'s first lookup (`findByProviderAndProviderId`) runs
  outside a transaction, so every returning login counts one undeclared primary access and would
  throw where fail-fast is on (`DeclaredFinderRoutingIT` proves declared finders route this way).
  Worth deciding when login latency or the undeclared counter is examined: readOnly (replica lag
  right after a concurrent create) or explicit read-write; then sweep other bare declared finders.
- Unit ADR 116: 2026-09-23 — Whether an origin `Set-Cookie` still blocks caching once a Cache Rule makes a
  JSON path eligible is unmeasured. Default behavior is measured: through the real edge, origin
  `Set-Cookie`, `no-store`/`no-cache`/`max-age=0` and `private` each bypass the cache on a
  cacheable extension, request cookies stay out of the cache key, and extension-less JSON is
  never eligible without a rule (playground lab `cf-cache-cookie-probe`, pass 1). The second pass
  needs a token with Cache Rules edit on the zone; the staging token gets 403 on zone rulesets.
  Worth doing once the cookieless content endpoint exists, against that endpoint rather than the
  synthetic origin, before the Cache Rule is relied on in production.
- 2026-09-27 — Until `c54c159e`, a first run of `deploy/grafana/import-alert-rules.sh` created its
  folder with the literal title `${GRAFANA_ALERT_FOLDER:-danteplanner-alerts}`, so stray folders
  holding rules may exist (unverified). Worth doing before the next re-import: list the alert
  folders and rules in Grafana and delete the strays.
- 2026-09-27 — Unit bookmark retirement: `PlannerConstants.RETIRED_BOOKMARK_FLAG` and the two
  `isBookmarked()` accessors on `PublicPlannerResponse`/`PublishedPlannerDetailResponse` keep the
  field on the wire as `false` so frontend bundles cached before the removal still parse. Delete
  them, and `isBookmarked` from `PlannerResponseContractIT`'s field sets, once the frontend
  without the field has been in production for a full release.
## 2026-09-28 landing gate (adr/137)

- The `reference-transaction` hook refuses the whole transaction when `refs/heads/dev` is
  one of several refs updated together (`git rebase --update-refs` across dev,
  `update-ref --stdin`), so the other refs' updates are lost with it. Worth handling when
  someone works with `--update-refs` over dev; until then the single-ref landing is the
  only path in use.
  re-check: `rg -q 'refs/heads/dev \] \|\| continue' .githooks/reference-transaction && rg -q '^exit 1$' .githooks/reference-transaction`
- The landing gate's patch-id is empty for a root commit (`diff-tree -p` without `--root`),
  so an orphan branch merged with `--allow-unrelated-histories` lands unreviewed. Closing it
  means `--root` in the hook, the reviewer's recipe and the measures script together. Worth
  doing if an orphan merge ever appears on dev.
  re-check: `rg -q 'git diff-tree -p "\$c"' .githooks/reference-transaction && ! rg -q -- '--root' .githooks/reference-transaction`
- The release-sync exemption trusts `refs/remotes/origin/main`, and the Bash deny list
  refuses only direct ref writes; `git remote set-url origin .` followed by a plain fetch
  moves that ref to local commits. Worth closing (deny `git remote add|set-url` in
  bash-deny) if the measures script ever shows an unreviewed range hidden this way.
  re-check: `rg -q 'refs/remotes/origin/main' .githooks/reference-transaction`
- FE mutation testing has no working runner: Stryker 10 with the vitest runner fails in
  project setup against the multi-project `vite.config.ts` on vitest 4.1.2, and its IPC
  drops the inner error. Worth one more spike (a single-project vitest config for Stryker,
  or a sandbox that carries `../static`) when the PIT ratchet has shown its value on the
  backend; until then under-assertion on the frontend is unmeasured.
  re-check: `! git ls-files | rg -qi stryker && rg -q 'projects: \[' frontend/vite.config.ts`
- Three full-run-only failures never reproduced across isolated, `taskset -c 0`, neighbour-combo and
  eight full runs (2026-09-29..10-04): `SseFanoutIT` (a JDBC SSL communications error: MySQL
  connection SSL handshake failure during setup, not the fan-out under test; SSL stays on because
  production uses `VERIFY_CA`), `BulkheadIT` INV7 (a timing assertion: `:158` compares two
  wall-clock durations, which load can invert; a deterministic signal such as the bulkhead pool
  reaching its active-connection cap would replace it), and `EffectPlacementIT` "A committed write
  leaves an event, and every effect follows from it" (`:151`, no SSE push within 10 s, once in a
  136-class run, mid lane-B edit; the SSE publisher mock saw no interaction; it passed 3/3 alone on
  both the visibility branch and dev). They fail under the full `check` run and pass when run
  alone. Worth diagnosing if `SseFanoutIT` or `BulkheadIT` fails twice in a phase-close run; until
  then a phase close re-runs them alone before judging red. Worth doing when the test-retry report
  names one as flaky or `EffectPlacementIT` fails again in a full run: keep that run's XML and the
  classes that ran before it in the fork, check whether the outbox eager executor was saturated,
  then build the loop from them.
  re-check: `d=backend/src/test/java/org/danteplanner/backend/integration; test -f $d/SseFanoutIT.java && test -f $d/EffectPlacementIT.java && rg -q 'isGreaterThan\(singleReCheckMs\)' $d/BulkheadIT.java`
- The adr116-131 brief reserves migration V064 for J6 "drop planner_views", but V064 is taken by
  the floor-count parameter on `rebuild_planner_filters` (V062 is burned per push-order). J6 must
  renumber to the next free version when it starts.
  re-check: `test -f backend/src/main/resources/db/migration/V064__rebuild_takes_floor_count.sql && ! rg -qi 'drop table.*planner_views' backend/src/main/resources/db/migration`
- Refracted Railway planners are refused by `PlannerContentValidator` (`INVALID_CATEGORY`: the
  validator accepts MD categories only) yet the filter rebuild indexes their non-floor rows with
  floor count 0, and the drift reconciler skips them as unreadable, so the audit oracle never
  covers RR. The only path that can index an RR row is the startup keyword backfill
  (`PlannerKeywordBackfillRepository.keywordRowsAfter` has no planner-type filter); prod held no
  RR rows on 2026-09-28. Worth resolving when RR content gets rules of its own: either RR content
  validates and audits, or RR planners stop being indexed.
  re-check: `rg -q 'MDCategory.isValid\(category\)' backend/src/main/java/org/danteplanner/backend/planner/precondition/PlannerCategoryValidator.java && ! rg -qi 'planner_type' backend/src/main/java/org/danteplanner/backend/planner/repository/PlannerKeywordBackfillRepository.java`
- `FloorBoundaryConfinementTest` reads bytecode through Spring's repackaged ASM
  (`org.springframework.asm`, marked internal) because ArchUnit 1.3 exposes no string
  constants. Worth moving to a declared `org.ow2.asm` test dependency if a Spring upgrade
  breaks the package.
  re-check: `rg -q '^import org\.springframework\.asm\.' backend/src/test/java/org/danteplanner/backend/architecture/FloorBoundaryConfinementTest.java`
- `rebuild_planner_filters` (one argument, V061's CASE floor table) is kept beside
  `rebuild_planner_filters_scoped` (V065) so pods on the previous image keep rebuilding during
  the rollout. Drop the one-argument procedure in a later migration once no pod runs an image
  older than V065; until then the CASE table is a third copy of the floor counts, held equal to
  the shared table by `PlannerFilterRebuildIT`.
  re-check: `python3 -c "import glob,re,sys; s=sorted((int(re.search(r'/V(\d+)__',f).group(1)),i,m.upper()) for f in glob.glob('backend/src/main/resources/db/migration/V*.sql') for i,m in enumerate(re.findall(r'(?i)\b(create|drop) procedure (?:if exists )?rebuild_planner_filters\b',open(f).read()))); sys.exit(0 if s[-1][2]=='CREATE' else 1)"`
- 사영전투 turn-cut planner: up to three formations of up to
  twelve turns each against an 18/24-turn full-reward cut, phase-specific decks, and bosses that
  return on rotation, so a plan must render against the data version it was written for. Carry
  over from the story-battle planner: the turn/constraint model, the sin-resource ledger and run
  mode. Add: formation boundaries in the turn timeline and the turn budget. The draft spec was written without the in-game data model, so
  its storage and data-serving parts are not decisions. Worth doing once the story-battle planner
  ships and its encounter schema holds a second content type.
  re-check: `test "$(rg -c '^    [A-Z_]+\("' backend/src/main/java/org/danteplanner/backend/planner/entity/PlannerType.java)" = 2`
- Refraction Railway battle plans: a plan is a sequence of battles whose rules differ per line.
  Lines 1-3 allow a formation change at every node; from line 4 several nodes form one route
  with a single formation. Lines 2 and 5 pick a buff after each node's battle; line 6 offers,
  after each route battle, a choice that removes one of the final boss's passives, which changes
  the next battle's enemy rules. The `REFRACTED_RAILWAY` planner type is an empty placeholder
  (`RR_PLACEHOLDER`). Worth doing once the story-battle planner ships: its single-battle schema
  becomes the element of a line plan, with formation boundaries and between-battle choices per
  line.
  re-check: `test "$(rg -c '^    [A-Z_]+\("' backend/src/main/java/org/danteplanner/backend/planner/entity/RRCategory.java)" = 1 && rg -q 'RR_PLACEHOLDER' backend/src/main/java/org/danteplanner/backend/planner/entity/RRCategory.java`
- The battle planner computes an E.G.O's cost as: an exchanger override (`TryOverwrite*Cost`,
  E.G.O 21003 and 20205) replaces the base, adders (exchangers 20509 and 21209, `SystemAbility`,
  buff `GetEgoResourceAdder`) are summed on top, and the result is floored at 0. The order and
  the floor are assumed: `EgoResourceRequirement.RecalculateEgoResources` reaches them through
  virtual slots `[rax+0xc58]` and `[rax+0x178]` that were not resolved, and where
  `GetMpUseCostAdderByEGO` applies is untraced. No shipped E.G.O has both an override and an
  exchanger adder today. Worth doing when one does, or when a buff adder can land on an overridden
  cost: resolve the two slots from a vtable dump.
  re-check: `! rg -qi 'RecalculateEgoResources' frontend/src`
- The battle planner's turn editor places units on the stage from `team-position` coordinates
  over the command-view background layers, with a fixed projection. The client's camera rig
  (static ADR 368: pitch, zoom by aspect, scroll clamps, per-wave `battleCameraInfo`) is not
  reproduced in the frontend. Worth doing when a stage's fixed projection visibly misplaces units
  against its background, or when the editor gains pan and zoom.
  re-check: `! rg -qi 'battleCameraInfo' frontend/src`
- `terraform/rds`'s fleet-state gate (exact-key listing before each `terraform_remote_state`)
  has no standing test: `mock_provider` rejects the stack's `ephemeral` master-password read, so
  `terraform test` stops before planning. A copy without that block passed absent, sibling-key,
  emptied, oregon-only and both-fleet runs, and failed the sibling-key run under a prefix gate.
  Worth doing when Terraform mocks ephemeral resources, or when the wiring moves into its own
  module for another reason; either lets the five runs return as a `tests/` file, and a CI step
  must run `terraform test` for it to guard anything.
  re-check: `rg -q '^ephemeral ' terraform/rds/main.tf && test -f terraform/rds/remote-state.tf && test -z "$(git ls-files 'terraform/rds/*.tftest.hcl' 'terraform/rds/tests')"`
- `PrivilegedRouteCoverageRule` confines to the privileged prefixes only the controllers that call a
  moderation or admin service directly; a handler reaching one through another feature's service
  escapes it, and would then act with the token's role instead of the stored one. Worth doing when
  any non-moderation service starts calling a moderation service: make the rule transitive over the
  call graph (as `EffectPlacementTest` walks it).
  re-check: `rg -q 'getDirectDependenciesFromSelf' backend/src/test/java/org/danteplanner/backend/architecture/PrivilegedRouteCoverageRule.java`
- `ArchitectureRuleFixtureRatchetTest` counts only `ArchRule` fields and `@ArchTest` members, so a
  rule written as a plain `@Test` (FailureUnionBoundaryTest, BulkUpdateBoundaryTest,
  ContentWritersAdmitFloorsTest, SubjectModuleBoundaryTest's factory rules, the frozen-name
  staleness checks) escapes the fixture requirement. Worth doing when a new architecture test
  lands as a plain `@Test`: define a rule by what it checks (an ArchUnit `check`/`evaluate` call
  reached from a test in the architecture package) rather than by its declared type.
  re-check: `rg -q 'isAssignableTo\(ArchRule.class\) \|\| field.isAnnotatedWith\(ArchTest.class\)' backend/src/test/java/org/danteplanner/backend/architecture/ArchitectureRuleFixtureRatchetTest.java`
- `RestrictedPrincipalMatrixIT`'s allowed rows address random ids, so most answer 404 at their
  entity lookup: the matrix proves they clear validation and are not refused with USER_BANNED,
  not that a ban leaves them working. A ban check placed after the lookup (the order
  `PlannerCommandService` already uses for upserts) would pass unseen. Worth doing when a guard
  moves behind a lookup or a new ordinary route takes a restriction check: seed a real resource
  per allowed row, owned by the banned principal where the route needs it, and assert a 2xx.
  re-check: `rg -q 'publishedPlannerId\(\) : UUID.randomUUID\(\)' backend/src/test/java/org/danteplanner/backend/security/RestrictedPrincipalMatrixIT.java`
- `ArchitectureRuleFixtureRatchetTest` credits a negative test that reads the failure message, not
  one that checks the message names the fixture, so `hasMessageContaining("failed to check any
  classes")` would still earn credit for a rule that rejected nothing. Worth doing if a credited
  test is found asserting on anything but the fixture: match the message argument against a class
  from the fixture package at the bytecode level.
  re-check: `rg -q 'method.startsWith\(MESSAGE_ASSERTION_PREFIX\)' backend/src/test/java/org/danteplanner/backend/architecture/ArchitectureRuleFixtureRatchetTest.java`
- 2026-10-02 — The frontend reports no errors anywhere: production hides the message, the
  error screens show only a short code, and nothing reaches Loki or a reporting service, so a
  failure is known only when a user describes it. Worth doing once a deploy regression must be
  caught without a user report; Sentry or GlitchTip (source-map stacks, per-browser grouping)
  beats a home-grown beacon, which would rebuild both.
  re-check: `! rg -qi 'sentry|glitchtip' frontend/package.json && ! rg -q 'sendBeacon' frontend/src`
- 2026-10-02 — `getDB` (`frontend/src/lib/storage.ts`) clears its cached promise when an open
  reports `onerror` or `onblocked`, but not when `indexedDB.open` throws synchronously: the
  rejected promise stays cached for the page's life, so storage cannot recover without a reload.
  Harmless for a permanent `SecurityError`; worth fixing once a transient synchronous throw is
  seen in the field.
  re-check: `rg -qU 'pending = new Promise\(\(resolve, reject\) => \{\n    const request = indexedDB.open' frontend/src/lib/storage.ts`
- 2026-10-02 — `UNAVAILABLE_ERROR_BY_CODE[body?.code ?? '']` in `frontend/src/lib/api.ts` is a
  bare object lookup, so a 503 whose code is an inherited property name (`toString`) picks a
  non-constructor and `new` throws a `TypeError` instead of `BackendUnavailableError`. Inferred,
  not run. The map now also holds `WRITE_TEMPORARILY_UNAVAILABLE`, `DEADLOCK` and
  `RATE_LIMIT_*` entries. Worth a `Map` or `Object.hasOwn` guard once the backend's code set is open to anything
  but its own constants.
  re-check: `rg -qF "UNAVAILABLE_ERROR_BY_CODE[body?.code ?? '']" frontend/src/lib/api.ts`
- 2026-10-02 — Queries retry `WriteTemporarilyUnavailableError` once (it extends `Error`, not
  `RetryableUnavailableError`, so it falls through to `failureCount < 1` in
  `frontend/src/lib/queryClient.ts`, which retries `RetryableUnavailableError` up to
  `MAX_RETRYABLE_ATTEMPTS`) while `BackendUnavailableError` is
  not retried, although both now mean the server cannot serve (the backend's database-down code).
  Worth aligning when the retry policy is next touched or a DB outage shows doubled request
  volume.
  re-check: `rg -q 'class WriteTemporarilyUnavailableError extends Error' frontend/src/lib/apiErrors.ts && ! rg -q 'WriteTemporarilyUnavailableError' frontend/src/lib/queryClient.ts`
- 2026-10-02 — `frontend/tsconfig.test.json` includes only test files, so tests cannot see the
  ambient globals in `frontend/src/vite-modules.d.ts`; the build-id hook test redeclares
  `__BUILD_ID__` locally. Worth fixing once a second test needs a global from that file.
  re-check: `! rg -q 'vite-modules' frontend/tsconfig.test.json && rg -q '^declare const __BUILD_ID__' frontend/src/lib/banner/__tests__/useUpdateAvailable.test.tsx`
- In ArchUnit 1.3, `getMethodCallsFromSelf()` and `getConstructorCallsFromSelf()` omit method
  references (`Foo::bar`, `Foo::new`). Only `getAccessesFromSelf()` returns them. A red fixture
  proved this for `LogCaptureRule`. These rules read only calls, so a method reference passes
  them: `ContentWritersAdmitFloorsTest:85`, `TestIsolationConventionTest:113,343`,
  `EffectPlacementTest` (six call-graph walks) and `GuaranteePlacementTest:120`. When one of
  those rules is next touched, switch it to accesses and add a by-reference violator fixture.
  re-check: `rg -q 'get(Method|Constructor)CallsFromSelf\(\)' backend/src/test/java/org/danteplanner/backend/architecture -g '{ContentWritersAdmitFloors,TestIsolationConvention,EffectPlacement,GuaranteePlacement}Test.java'`
- `SseSubscriberConfig` sets no task executor on the Redis listener container. Spring Data Redis
  3.5 then uses a `SimpleAsyncTaskExecutor` and dispatches each pub/sub message on a new thread, so
  two pushes to one stream can reach the browser in the reverse order (reproduced 2026-10-09: 41 of
  75 rounds of back-to-back pushes had an inversion; 0 of 75 with a single-threaded executor, which
  delivered no slower; logs `.claude/reports/logs/bug5-*`). No delete event exists and no SSE counter
  goes down, so the visible effect today is root comments shown in swapped order until refetch, or a
  reply dropped until refetch when it arrives before its parent. That last case mostly needs the
  parent's outbox dispatch to be delayed, which a single-threaded listener does not fix. On 2026-10-09
  a single dispatch thread was built and then rejected. The listener calls `emitter.send`
  synchronously (`SseRedisSubscriber.onMessage` → `sseService.sendToUser`). So one client with a
  blocked socket write stalls every SSE delivery on its pod (reasoned, not measured). If a dropped
  reply is reported, look at outbox ordering for the parent's push first. Then choose between
  per-stream lanes (one single-thread executor per hash of user or planner id) and early replies
  held on the frontend. A single thread is acceptable only with a measured emitter write timeout.
  The executor patch and its regression test are in `.claude/reports/logs/bug5-*` (red without the
  executor: 20 inversions in round 1). The test goes into `SseFanoutIT` as a method. A new class
  with its own `@DynamicPropertySource` exceeds the context budget of 15 in
  `TestIsolationConventionTest`.
  re-check: `rg -q 'RedisMessageListenerContainer' backend/src/main/java/org/danteplanner/backend/shared/sse/SseSubscriberConfig.java && ! rg -q 'setTaskExecutor' backend/src/main/java/org/danteplanner/backend/shared/sse/SseSubscriberConfig.java`
- `RedisConnectionConfig.buildRateLimitProxyManager` creates a `RedisClient` with its own client
  resources and returns only the `ProxyManager`, so nothing can shut the client down. In tests,
  `RateLimitServiceIT` (one per test, 26 per run) and `RateLimitBucketLifecycleIT` (one per test)
  leave about 33 clients per run with live threads that reconnect to stopped containers; a heap dump
  on 2026-10-06 put the leaked clients at about 98 MiB. Production builds the manager once per
  context, which also never closes it. When the rate-limit wiring is next touched, or the test heap
  needs room again, return a closeable handle with the manager and close it in both places.
  re-check: `rg -q 'public static ProxyManager<byte\[\]> buildRateLimitProxyManager' backend/src/main/java/org/danteplanner/backend/shared/config/RedisConnectionConfig.java`

- `NotificationRepository.hardDeleteOldNotifications` deletes on `deleted_at < :cutoff` and no index
  leads with `deleted_at`, so the nightly purge likely scans and locks the whole `notifications` table
  (read from the schema, not EXPLAINed). Worth doing when the table grows past a size where the purge
  holds locks long enough to stall notification inserts: EXPLAIN it, then add an index or batch it.
  re-check: `rg -q 'n.deletedAt < :cutoffDate' backend/src/main/java/org/danteplanner/backend/notification/repository/NotificationRepository.java && ! rg -qi 'notifications ?\(deleted_at' backend/src/main/resources/db/migration`
- `PublishedPlannerQueryService.incrementViewCount` has no production caller since views moved to the
  buffered `PlannerViewRecorder` path; only two `PublishedPlannerQueryServiceTest` tests call it.
  Delete the method and those tests together when the planner view or query service is next touched.
  re-check: `rg -q 'public void incrementViewCount\(' backend/src/main/java/org/danteplanner/backend/planner/service/PublishedPlannerQueryService.java`
- `SentinelUserVerifier.java:15` says verification is skipped in tests because "TestDataInitializer
  handles it"; that class never loaded and the test overhaul deleted it. Fix the comment when the
  sentinel-user code is next touched.
  re-check: `rg -q 'TestDataInitializer' backend/src/main/java/org/danteplanner/backend/user/config/SentinelUserVerifier.java`
- `PlannerViewLockOrderIT.flush_WhenConcurrentFlushesShareTwoPlanners_AdvancesEveryCounter` fails in a
  full backend run at a rate of a few percent at most (CannotAcquireLockException on the planner_stats
  upsert, passing on retry); 26 full runs and 2,400 repetitions on 2026-10-07 did not reproduce it.
  Worth doing when it next fails: read that run's XML cause chain (the `backend-test-reports`
  artifact in CI). SQLState 40001 means a deadlock, error 1205 a lock-wait timeout; both failing runs
  so far took 30-40 s longer than their neighbours, which points at the timeout. Open hypotheses: a
  third transaction holding a gap lock on the nearly empty `planner_stats`; a scan in byte order
  against the pods' signed-UUID TreeMap order (the two orders disagree for about half of pairs); a
  shared context's 500 ms `PlannerViewRecorder` flush as that third party.
  re-check: `rg -q 'void flush_WhenConcurrentFlushesShareTwoPlanners_AdvancesEveryCounter' backend/src/test/java/org/danteplanner/backend/integration/PlannerViewLockOrderIT.java`
- `EGODetailPage.parity.test.tsx` can exhaust a worker's heap inside a single render (third cell,
  "threadspin 2 with awaken") when the box is under memory pressure; the run then takes about 350 s
  instead of 60 s. Seen before and after the test overhaul's snapshot change, so the snapshot size is
  not the cause. Worth doing when it recurs on an unloaded box or in CI: profile that render's heap.
  re-check: `test -f frontend/src/pages/ego/__tests__/EGODetailPage.parity.test.tsx`
- `.claude/hooks/check-output-redirect.sh` shows `yarn --cwd frontend vitest run > …` as its
  redirect example, which skips the heap the `test` script sets. Change the example to
  `yarn --cwd frontend test run` when the hook is next edited.
  re-check: `rg -qF 'yarn --cwd frontend vitest run >' .claude/hooks/check-output-redirect.sh`
- The EGO detail page can livelock in tests: a tier or tab click inside `act()` while the skill and
  passive description boundaries are still suspended on the keyword-formatter static-data waterfall
  makes React re-commit with no DOM change and starves the pending fetch (diagnosed 2026-10-08 with a
  200 ms delayed queryFn and a Profiler commit counter; the parity tests now prefetch the data). Not
  checked in a real browser, where no synchronous `act()` flush starves the fetch. Worth doing when
  a user reports a frozen EGO detail page after switching tiers during load: reproduce in Chromium
  with throttled static-data requests.
  re-check: `rg -q 'prefetchDescriptionData' frontend/src/pages/ego/__tests__/EGODetailPage.parity.test.tsx && ! git ls-files e2e | rg -qi ego`
- `tsc --noEmit -p e2e/tsconfig.json` reports 35 TS6142 errors ("`--jsx` is not set"):
  `e2e/tests/schema-portability.contract.spec.ts` imports `PlannerSchemas`, which reaches the barrels
  `pages/themePack/index.ts` and `shared/filter/index.ts` through `editorStateCodec`, and those
  barrels re-export `.tsx` components. Not from
  the test overhaul (its e2e edits import only node modules and Playwright types). The PR gate does
  not typecheck e2e, so spec type errors reach CI unseen. Worth doing when an e2e spec needs a
  type-level guarantee or the e2e typecheck joins a gate: import the needed frontend modules directly instead of through the
  barrels, or set `jsx` in e2e's tsconfig.
  re-check: `! rg -q '"jsx"' e2e/tsconfig.json && rg -qF "@/pages/planner/schemas/PlannerSchemas'" e2e/tests/schema-portability.contract.spec.ts`
- Authority has no single owner. The role column and the role-change and last-admin rules are
  `user`'s (`RoleChangeValidator`, ADR 173), the admin module only orchestrates, the restriction
  columns are still on `User`, and ADR 148 (user owns role and restriction) and ADR 170 (moderation
  owns restriction) disagree about who answers "may this actor act?". The target shape is a bottom `authority`
  module owning role, rank, privileged-route rules and the last-admin rule (and possibly restriction
  checks), with `user`, `moderation` and the admin endpoints above it; it needs the role column's
  ownership moved (a `user_roles` table with a dual-write window, or an ADR 169 amendment) and a
  decision on ADR 170. Worth doing when ADR 170's restriction move is scheduled, since both answer the
  same question; the analysis with file counts is in the session record of 2026-10-09.
  re-check: `test ! -d backend/src/main/java/org/danteplanner/backend/authority && rg -q 'name = "banned_at"' backend/src/main/java/org/danteplanner/backend/user/entity/User.java`

- No ast-grep scan covers `backend/src/consistency/java`: `astGrep` scans `src/main/java` and
  `astGrepTestSources` scans `src/test/java`. No rule targets the checker today. Worth doing when
  an ast-grep rule should hold for the checker's source set.
  re-check: `test -d backend/src/consistency/java && ! rg -q '"scan", "--error", "src/consistency' backend/build.gradle.kts`

- `PlannerFilterRowsConcurrencyIT.syncFilterRows_WhenTwoNeverSyncedPlannersShareAnIndexGap_BothCommitEveryRound`
  (then `PlannerCatalogSyncConcurrencyIT.syncPlanner_…`) failed once with "Deadlock found" in
  `rebuild_planner_filters_scoped` during an integration-package run (seed 5881360375230807038,
  2026-10-10) and passed on its retry and on the next run. That run predates ADR 185's in-write
  sync and V067's rebuild by difference. Worth diagnosing when it fails again: replay the seed
  with `-PtestSeed`.
  re-check: `rg -q 'void syncFilterRows_WhenTwoNeverSyncedPlannersShareAnIndexGap_BothCommitEveryRound' backend/src/test/java/org/danteplanner/backend/integration/PlannerFilterRowsConcurrencyIT.java`

- 2026-10-13 — The Seoul-to-Oregon failover has never been exercised in production; the
  portfolio's multi-region piece claims region-outage tolerance with no outcome line for it.
  Worth doing in a chosen low-traffic window, with the owner's approval: disable the Seoul app's
  ArgoCD self-heal, scale the Seoul backend to 0, record status code and `X-Served-By` every
  second from a client, restore, re-enable sync. Load balancer defaults apply: `/healthz-local`
  every 60 s, `monitor_retries = 2` (read as two failures to drop, not checked against
  Cloudflare's definition of `retries`), and `NEAS`, `SEAS`, `SAS` and `OC` each steer to
  `["seoul", "oregon"]`. Session affinity is `cookie`, so record whether the client holds a `__cflb`
  cookie. Expect 2-3 minutes of errors for Asian users (inferred from the monitor settings).
  re-check: `rg -q 'NEAS = \["seoul", "oregon"\]' terraform/cloudflare/variables.tf && ! rg -qi 'scale the Seoul backend' docs/runbooks`
- 2026-10-13 — `GtidCapturingDataSource`'s class comment and the 39d9fc41 message say the
  `afterCommit` lookup ran on a fresh pooled connection; the 2026-10-08 reproduction showed the
  same connection, with the GTID overwritten by Hibernate's post-commit `SET autocommit=1`. Worth
  doing whenever that class is next touched.
  re-check: `rg -q 'fresh pooled connection' backend/src/main/java/org/danteplanner/backend/shared/gtid/GtidCapturingDataSource.java`


- The backend's start-buff rule parses each season's buff ids with `Long.parseLong` on every check,
  so a non-numeric key in a season's `startBuffs.json` throws at save time instead of failing
  startup. Worth moving into the season load when a season file can carry a non-numeric key.
  re-check: `rg -q 'mapToLong\(Long::parseLong\)' backend/src/main/java/org/danteplanner/backend/planner/validation/startbuff/StartBuffRules.java`

- The frontend id normalizer (`plannerIdNormalize.ts` `migrateArray`) treats `null` as "drop this id",
  so a `null` element in a planner's gift or start-buff arrays disappears on every load, while the
  backend reports it as `INVALID_FIELD_TYPE`. The shared content corpus passes only because the
  frontend replay skips the id normalizer. Worth fixing when a stored planner is found holding a
  `null` element; the fix turns today's silent heal into a save that the backend refuses.
  re-check: `rg -q 'if \(result === null\) continue' frontend/src/pages/planner/lib/plannerIdNormalize.ts`

- Mirror Dungeon run tracking by screen capture (a competitor site offers it) is unbuilt. A web
  page cannot read game state: the client's `Player.log` carries only errors, and memory or
  packet reading needs an installed program with terms-of-service risk, so `getDisplayMedia` is
  the only browser path. Known constraints: a skilled player clears a gift-acquisition popup in
  about 0.3 s, so capture needs 10-15 fps with each frame handled in under ~66 ms (crop to the
  icon inside the letterboxed game area, then match a perceptual hash against every gift; OCR is
  too slow). Fusion removes gifts: recipe gifts can infer their ingredients from the
  `recipe` field in `EGOGiftSchemas.ts`, but plain fusion cannot. Reading the owned-gift list
  screen instead of the popup avoids both problems. Theme-pack choice is one click, so manual
  entry comes first. Open: popup duration and blur (measure from a gameplay video with
  `ffmpeg -vf fps=15`), how small an icon crop stays distinct, and whether an acquisition sound
  cue exists. Worth doing when run tracking becomes a planned feature.
  re-check: `! rg -q 'getDisplayMedia' frontend/src`

- 2026-10-17 — CN localization updates are detected (Discord release alerts) but applied by hand:
  `static/scripts/update_cn_lang.py`, the CN i18n regeneration, the static commit, the gitlink/lock
  bump and the deploy are separate manual steps. Full automation needs two things the asset-release
  unit provides: the raw static-data tables in a bucket CI can read (CN regeneration consumes them)
  and a data release pointer the backend adopts without a deploy. Worth doing once that unit lands;
  until then one local command (`static cn-update`) wrapping download, regeneration, commit and
  lock bump is the cheap form.
  re-check: `! rg -q 'cn-update' .claude/skills/static-checkout/bin/static && ! rg -qi 'update_cn_lang' .github/workflows`

## 2026-10-27 trunk hygiene follow-ups (PR #322)

- 58 of 193 ADRs still trigger the pre-commit "no REJECTED clause" note after its parse was fixed (down from 115); whether each is a forced, corollary or summary bullet is unchecked. Worth doing when an ADR convention pass is next run.
  re-check: `awk 'function c(){if(o&&!r)b[cur]=1;o=0;r=0} FNR==1{c();cur=FILENAME;s=0} /^## /{c();s=($0~/^## Decisions/);next} s&&/^- @/{c();o=1} o&&/REJECTED/{r=1} END{c();n=0;for(f in b)n++;print n;exit n==0}' docs/adr/[0-9]*.md`
- `RaiseBatcher.java:91` waits on the real clock for the caller-side bound (waitBound + margin). In a
  fresh JVM under heavy CPU load the first Redis client connect exceeds it and whichever
  `RaiseBatcherTest` connects first fails; the two clock-driven tests now use a 30 s bound, the rest
  still use 1.5 s. Worth doing if the class fails in CI on its first connecting test: warm the
  Lettuce client once per class.
  re-check: `rg -q 'request.raised\(\).get\(bound.toNanos\(\), TimeUnit.NANOSECONDS\)' backend/src/main/java/org/danteplanner/backend/shared/freshness/RaiseBatcher.java && rg -q 'WAIT_BOUND = Duration.ofSeconds\(1\);' backend/src/test/java/org/danteplanner/backend/shared/freshness/RaiseBatcherTest.java`
- No `components/ui/textarea` primitive: `DeckImportDialog` and `ModerationReasonDialog` each
  hand-style a `<textarea>` against `components/ui/CLAUDE.md`. Worth doing when either is next
  touched: add the shadcn textarea and use it in both.
  re-check: `test ! -e frontend/src/components/ui/textarea.tsx || rg -q '<textarea' frontend/src/pages/planner/components/deckBuilder/DeckImportDialog.tsx frontend/src/shared/moderation/components/ModerationReasonDialog.tsx`
- No code completes a planner's comment-stream emitters when the planner is deleted; the stream
  lingers until the 1 h timeout, and the e2e spec pins only what the client does today (a gone
  planner opens no stream). ADR 183 @comments @visibility implies the removal should end the
  stream. Streams live on every pod, so an AFTER_COMMIT listener on the committing pod is not
  enough: the removal needs a `domain_events` row, a dispatcher arm and an SSE envelope that each
  pod's `PlannerCommentSseService.deliver` turns into `complete()` on that planner's emitters
  (backend CLAUDE.md:15). Worth doing as its own unit; the e2e spec then regains the open-stream incident.
  re-check: `! rg -q 'complete\(\)' backend/src/main/java/org/danteplanner/backend/comment`
- After PR #322 deploys: `write_forwarding` goes probe → all (ADR 123), the POST `/batch` handler
  and the top-level `selectedKeywords` copy wait for seven days of zero counters (entries above).
  re-check: `rg -q '@PostMapping\("/batch"\)' backend/src/main/java/org/danteplanner/backend/planner/controller/PlannerQueryController.java`
- CI `test-frontend` died once with "Worker terminated due to reaching memory limit: JS heap out
  of memory" on PR #328 (386 of 387 files passed, zero failures) and passed on rerun of the same
  commit. Worth doing on the second occurrence:
  find the file that was running from the log and either split it or raise the worker heap
  (`pr-gate.yml` NODE_OPTIONS) with the measurement that justifies it.
  re-check: `rg -qF '"test:coverage": "NODE_OPTIONS=--max-old-space-size=4096 vitest run --coverage"' frontend/package.json`
