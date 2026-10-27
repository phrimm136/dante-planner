# 205 the-tag-bump-pushes-with-a-deploy-key
epic: none · pr: none

## Decisions
- @deploy @bypass — A write deploy key of this repository is the sole bypass actor of the rulesets on the protected branches and the credential the fleet deploy's tag bump pushes with; the repository admin role leaves the bypass list once a deploy has shown the key's push landing. The bump commits straight to a protected branch, so its push must come from a bypass actor, and on a personal account the rulesets cannot name the github-actions app while a deploy key is a nameable actor.
  REJECTED: the owner's fine-grained personal access token carrying the admin identity — it is a personal token of the owner, it expires, and it holds contents:write on every repository it was scoped to.
  REJECTED: the workflow's GITHUB_TOKEN — its push cannot bypass the rulesets, and a push it makes does not trigger the follow-up workflows.
  INVARIANT: no workflow references the personal access token secret — enforcer: the re-check `rg -n 'GH_DEPLOY_PAT' .github` prints nothing.
  NON-GOAL: changing which workflows the bump push triggers — `[skip ci]` in the bump commit and the deploy's path filter stay as they are.

## Takeaway
- takeaway: on a personal account a workflow's privileged push needs an actor the rulesets can name; a deploy key scoped to one repository is that actor, where a personal token borrows the owner's whole reach.
