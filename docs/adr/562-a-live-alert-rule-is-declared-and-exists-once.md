# 562 a-live-alert-rule-is-declared-and-exists-once
epic: none · pr: none

## Decisions
- @grafana @provisioning @duplicates — After the imports, the Grafana provision workflow reads every live alert rule and fails when a declared title exists more than once in the organization, when the managed folder holds a rule that no rule file and not the staleness script declares, or when two folders carry the managed title. Nine instances of `etcd-snapshot-deadman` fired from a rule the repository no longer knew while its own rule of that title was quiet: the import had moved to fixed uids, created new rules beside the old ones, and the old ones received no later fix. `argocd-app-drift` was seen under two uids as well. Re-check [prod-read]: `.claude/skills/ops/bin/ops metrics 'count by (alertname,grafana_rule_uid)(last_over_time(GRAFANA_ALERTS[30d]))' --since 1m --step 1m` (it shows only rules that were pending or firing).
  REJECTED: deleting the extra rules from the workflow — the run would also delete a rule somebody made in the UI, and a deletion cannot be undone, while a kept copy is rewritten by the next run.
  REJECTED: giving the app rules fixed uids like the cluster rules — a live rule keeps its uid, so each rule would be created again beside its old copy, which is how this happened.
  REJECTED: checking before the imports — a stray rule would then hold back the fix of another rule.
  REJECTED: looking for duplicates in one folder only — the folder is found by title, and folder titles are not proven unique.
  INVARIANT: a second copy of a declared rule, in any folder, fails the run and names each uid — the audit tests of the rule loader.
  NON-GOAL: a rule that is declared and absent — the staleness rules are created by hand, so their absence on a new stack is legal.
  NON-GOAL: a change made in the UI to a declared rule — the next import overwrites it, as before.

## Takeaway
- takeaway: changing how an object is identified is a migration of every live object; without removing the old ones, the same name runs twice and only one copy gets the fixes.
