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
