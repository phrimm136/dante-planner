# 516 the-merge-queue-runs-the-full-gate-and-mutation-runs-weekly
epic: none · pr: none

## Decisions
- @ci @gates — PR Gate runs on `pull_request` and `merge_group`, and no longer on `push`. A pull request runs the frontend checks and the backend unit tier (`check -PexcludeTags=containerized`). The merge queue runs the whole backend `check`, e2e and both builds on the commit it will land. On both events a job runs only when its lane's inputs changed, and `changes` diffs `merge_group.base_sha` in the queue. Main receives only commits the queue passed, so `deploy-fleet.yml` deploys on `push` to main. A static pin waited about 22 minutes for the mutation job, and then the push run on main repeated the gate before the deploy started. Of the last 100 PR Gate runs, 32 failed, test-mutation took part in 12 of them, and a build job in none. Re-check [gh]: `gh run list -w "PR Gate" -e merge_group` lists queue runs, and `gh run list -w "PR Gate" -e push` lists none after this change.
  REJECTED: a deploy gate that tests main after the merge — a failure there has already broken main, and every later pull request lands on top of it.
  REJECTED: requiring pull requests to be up to date with main — each merge would force every open Dependabot pull request to rebase and rerun.
  INVARIANT: main's history holds only commits that passed the merge queue's full lanes — enforced by the trunk-protection ruleset's merge queue.
- @ci @mutation — The mutation ratchet leaves PR Gate and runs cold in `mutation.yml` once a week and on dispatch. A rise fails the run, a fall passes, and either one goes into a single open issue with the counts and the commits since the last green run. The baseline is lowered by hand from that issue. `pitestRatchet` fails only on a rise and writes `build/reports/pitest/ratchet.json`. A pull-request run with PIT's incremental history counted 7 survivors in `shared.redis` where main's cold run of the next commit counted 8, so a baseline lowered from a run with history fails the next cold run. Supersedes ADR 492. Re-check [manual]: `rg -n 'pitestRatchet' .github/workflows` names only `mutation.yml`.
  REJECTED: an auto-merge pull request that lowers the baseline — a pull request opened with the workflow token starts no workflows, so its required checks never report; the repo has no GitHub App or token that could open it, and the user chose the issue.
  REJECTED: pushing the lowered baseline to main with the deploy key — the user chose the issue.
  NON-GOAL: a pull request or the merge queue waits on mutation testing.
- @ci @build — The backend resolves its Gradle plugins from the plugin portal and Maven Central. A plugin's own dependencies live on Maven Central, and the portal only redirects to it; two runs failed to resolve `spring-core` and `spring-boot-loader-tools` when the redirect failed. Re-check [assert]: `rg -n 'mavenCentral' backend/settings.gradle.kts`.

## Takeaway
- takeaway: test the commit that lands, once, and keep the slow instrument off the merge path.
