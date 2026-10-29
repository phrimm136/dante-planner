# 492 the-mutation-ratchet-runs-in-its-own-ci-job
epic: none · pr: none

## Decisions
- @harness @gates @mutation @ci — The mutation ratchet leaves `check` and runs as the PR Gate job `test-mutation`, in parallel with `test-backend` under the same backend lane condition, with pull requests restoring PIT's incremental history from the Actions cache and main's push runs measuring cold and saving it; the per-unit local gate runs `pitestRatchet` beside `check`. Inside `check`, a CI run started without history and paid the whole mutation run after the tests, in series: test-backend grew from 4 to 6 minutes to 11 to 17 minutes while its test phase alone stayed under 5, and a cold mutation run takes about 6.7 minutes locally against about 2 with history. Re-check: compare the `test-backend` and `test-mutation` job durations of recent `gh run list -w "PR Gate" -e push` runs.
  REJECTED: keeping the ratchet inside `check` with a history cache — a run without history still adds the whole mutation run after the tests.
  REJECTED: running mutation only on main pushes or nightly, without blocking — a pull request that raises a package's surviving-mutant count merges before anyone sees it.
  REJECTED: a path filter on the mutation target packages — `targetTests` spans every backend test, so deleting a test anywhere can raise a count; the filter would be the whole backend lane.
  REJECTED: restoring history on main's push runs too — PIT reuses a killed result when neither the mutated class nor its killing test changed, and it does not hash static data, test resources or dependencies, so a stale result would be saved forward on main with no cold run left to correct it.
  INVARIANT: no commit that raises a measured package's surviving-mutant count deploys — main's cold `test-mutation` run inside PR Gate, whose success the deploy pipeline requires.
  INVARIANT: no pull request that raises a count merges — unenforced: main has no required status checks, and a pull request's warm run can pass a stale killed result that main's cold run then catches before deploy.
  NON-GOAL: changing the measured packages, the thread count or the committed survivor counts — the move changes where the ratchet runs, not what it measures.
- @ci — PR Gate posts no failure comment on pull requests; GitHub's own failed-run notification already reaches the author, so the `notify-failure` job, the `notify-ci-failure` action and its `Test Notify Failure` workflow are deleted.
  REJECTED: keeping the comment as a second channel — it repeats a notification the author already gets and needs a job that lists every other job by hand.

## Takeaway
- takeaway: a slow gate belongs beside the tests, not behind them; parallel jobs cost runner minutes, serial ones cost every author's wait.
