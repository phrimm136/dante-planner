# 178 planted-bug-runs-gate-only-consistency-changes
epic: none · pr: none · supersedes: 037 @ci @gates @consistency-checker @ci

## Decisions
- @consistency-checker @ci — The planted-bug run is a blocking `consistency` job in the PR gate. A
  matrix runs the clean app and each planted bug on separate runners. A change area gates the job
  on the checker, its generator and the read-path mechanisms, on pull requests and on pushes to main
  alike. Planted bugs test the checker. If the checker or a mechanism it watches changes, they need a
  rerun. A push to main is the deploy signal, and a full run there delays every deploy.
  REJECTED: running every planted bug in one job — the job takes the sum of all variants instead of
  the longest one.
  REJECTED: a nightly run that does not block — a change that breaks the contract merges and deploys
  before anyone sees the result.
  REJECTED: fewer seeds in CI — the detection rate loses its evidence.
  REJECTED: an unconditional run on a push to main, as for the other jobs — the run already
  passed on the pull request that changed those paths.
  INVARIANT: a pull request that changes a gated path merges only after the clean app and every
  planted bug pass — the `consistency-result` job in the PR gate, a required check outside the
  matrix. A skipped matrix job reports one check under its own name instead of one per variant.
  NON-GOAL: guarding the contract against changes outside the gated paths with planted bugs — the
  small clean run inside `gradlew check` covers those.

- @ci @gates — Verification attaches to events, not refs. The pull-request event runs
  change-filtered blocking lanes. The trunk push event runs every lane unconditionally, except the
  planted-bug lane, which follows its change area there too. The deploy pipeline consumes only the
  trunk run's verdict. Change detection is a first job that emits per-area outputs for job-level
  conditions. Branch protection counts a skipped job as satisfied, while a workflow that never
  triggered reports nothing and wedges the merge. The unconditional trunk run catches two pull
  requests that pass on their own bases and break together. The planted-bug lane tests the checker,
  not the application: the clean consistency run inside every backend lane still runs on each trunk
  push, and two changes that weaken the checker together both touch the lane's area.
  REJECTED: workflow-level path filters with required checks — structurally incompatible, the wedge
  is permanent.
  REJECTED: a merge queue — closes the stale-base race at per-merge latency and puts a third party in
  the merge path; the unconditional trunk run catches the same race minutes later at zero merge cost,
  and a paged revert is acceptable for a single-maintainer trunk.
  REJECTED: the planted-bug lane on every trunk push — every deploy waits for the slowest variant,
  while the race it would catch needs a change outside the lane's area that weakens only the
  checker.

## Takeaway
- takeaway: if a test or its subject changes, the test of that test runs. Parallel runners remove
  the wall time that a local machine's memory forced into a sequence.
