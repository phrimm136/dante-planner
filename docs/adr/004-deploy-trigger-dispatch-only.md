# 004 deploy-trigger-dispatch-only

epic: none · pr: none

## Decisions
- @deploy @deploy-fleet @trigger — `deploy-fleet.yml` triggers on `workflow_dispatch` alone while the schema decomposition is unreleased. That release drops a table, so it cannot ship as a rolling image bump; it runs from a one-time gated workflow driven by a migration runbook. A push trigger fires that deploy on merge and races the window it is supposed to sit inside. REJECTED: leaving the push trigger and relying on the shared `k3s-gitops-deploy` concurrency group — the group serializes the two workflows but does not order them, so the automatic deploy can take the slot first and roll the new image onto the old schema. REJECTED: deleting the workflow and restoring it afterwards — the file is the only record of how the fleet deploys, and reconstructing it under window pressure is worse than a one-line trigger change. Precedent: `deploy.yml` was retired the same way, dispatch-only with its push trigger removed.
- @deploy @rollback @migrations — for a release carrying migrations, a failed post-deploy gate alerts and halts rather than reverting the image tag. Flyway is forward-only and `spring.jpa.hibernate.ddl-auto=none`, so a reverted image starts clean, passes its probes, and then 500s on every endpoint whose tables the migration removed. There is no startup-time backstop against a bad revert. REJECTED: automatic revert with a schema guard at boot (`ddl-auto=validate` in production) — it converts a silent 500-storm into a loud crash-loop, which is better, but it makes every unrelated entity-mapping drift a production outage.

## Takeaway
- takeaway: rollback is a property of the release, not of the pipeline. The same revert step is correct for one release and destructive for the next, so the branch has to be on what the release contains — detected the way `test-migration` already detects it, from the added files in the diff.
