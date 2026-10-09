# 178 planted-bug-runs-gate-only-consistency-changes
epic: none · pr: none @consistency-checker @ci

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

## Takeaway
- takeaway: if a test or its subject changes, the test of that test runs. Parallel runners remove
  the wall time that a local machine's memory forced into a sequence.
