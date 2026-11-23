# 515 a-commit-runs-the-checks-its-change-reaches
epic: none · pr: none

## Decisions
- @tooling @checks — Before a commit, `checks changed` runs only the checks that the change set reaches. The change set is every path that differs from the merge-base with `origin/main`, in the working tree and untracked. A frontend source runs `fe typecheck` and the vitest files its import graph reaches (`vitest run --changed <merge-base>`). `static.lock`, `frontend/package.json` and `frontend/yarn.lock` run every vitest file. An e2e change runs `e2e typecheck`. A backend Java source runs the test classes named after it, plus the ArchUnit suite. Any other backend file that is not documentation runs every backend test. A tooling change runs `tools test`. PR Gate runs every check on the pull request, so a whole local run that PR Gate already runs takes `--full REASON`. The commit skill required `fe gate`, `fe build`, `be check`, `be pit` and a mock deploy before every commit, and PR Gate ran all of them again. The user ruled that local runs test only what changed and CI runs the full suite, in every repository. Re-check: `checks --print changed` names each check with the path that selected it, and `checks fe test` without a file exits 2 and names `checks changed`.
  REJECTED: a commit hook that checks a stamp of the last `checks changed` run — the user excluded it.
  REJECTED: selecting backend tests through the Gradle dependency graph — Gradle has no test-impact selection, and the PR Gate backend job runs every test before a merge.
  INVARIANT: a whole run of `fe test`, `fe gate`, `be test`, `be check`, `be pit` or `e2e run` states its reason — enforced by `whole_run` in the CLI.
  INVARIANT: a session starts no whole run, neither through the CLI nor the planted-bug matrix; the user starts one from their own prompt — enforced by the full-run-guard hook and its scenario test.
  NON-GOAL: `checks changed` does not run format, the build, the mutation ratchet, the deploy test or e2e; a Java change runs `checks be lint`. PR Gate runs them.

## Takeaway
- takeaway: when CI already runs every check on the pull request, the local run before a commit is the part of it that the change can break.
