# 517 main-runs-the-full-gate-because-a-user-repo-has-no-merge-queue
epic: none · pr: none

## Decisions
- @ci @gates — PR Gate runs on `pull_request` and on `push` to main. A pull request runs the frontend checks and the backend unit tier. A push runs the whole backend `check`, e2e and both builds, and `deploy-fleet.yml` deploys only after that run is green. Both events skip a lane whose inputs did not change. A newer push cancels the running push run, and the push diff starts at the head of the last green push run, so a burst of merges ends in one test run and one deploy, and a cancelled run leaves no change untested. ADR 516 put the full lanes in the merge queue, and GitHub refused the ruleset's `merge_queue` rule: the repository belongs to a user account, and the merge queue is offered only to organization repositories. Until this change, a pull request merged on the unit tier and deployed without e2e. Supersedes the merge-queue decision of ADR 516; its mutation and plugin decisions stand. Re-check: `gh api repos/phrimm136/dante-planner --jq .owner.type` prints `User`.
  REJECTED: moving the repository to an organization for the merge queue — the transfer touches the Cloudflare Pages integration, ArgoCD's repository URL and the deploy key, and the user chose this change.
  REJECTED: the full lanes on every pull request — each pull request of a burst would run e2e and the builds, and the burst would deploy once per merge.
  INVARIANT: no commit deploys unless a push run of PR Gate that contains it is green — enforced by `deploy-fleet.yml`'s `workflow_run` gate on `conclusion == 'success'`.
  NON-GOAL: main holds only tested commits; a commit can reach main and fail its push run, and it then blocks the deploy until a fix lands.

## Takeaway
- takeaway: check that the platform offers a feature before designing on it.
