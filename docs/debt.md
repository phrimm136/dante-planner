# Debt

Append-only dump for found work. Not a queue: entries are captured so they stop
occupying attention, and are pulled only by a deliberate defrag or design session.

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
- `DegradationIT` F2 (rate-limit Redis cut → 503) is order-dependent: it passes when the
  class runs alone and fails in full-class order, on clean HEAD as well as after the
  2026-08-10 fix batch, so something an earlier test leaves behind (container state or
  bucket state) breaks it. Route through /diagnose before touching the assertion.
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
