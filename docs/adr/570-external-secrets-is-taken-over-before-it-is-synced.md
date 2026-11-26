# 570 external-secrets-is-taken-over-before-it-is-synced
epic: none · pr: none

## Decisions
- @deploy @eso @adoption — External Secrets is taken over by an Application with the chart version, release name, namespace and values the boot script installed, plus the scrape annotations the converger already writes. It enters without automated sync and without a finalizer, and gets both in a later change, after each region was synced by a person. The live objects already carry the label ArgoCD tracks by, so an Application with the finalizer owns them before any sync, and a revert of the first change would delete the operator. Re-check [manual]: `rg -n 'app.kubernetes.io/instance' deploy/README.md`.
  REJECTED: automated sync from the start — the first apply would run in both regions at once and before anybody read the difference.
  REJECTED: deleting the operator and installing it again — every ExternalSecret stops refreshing meanwhile, and a Pod that starts then may find no Secret.
  REJECTED: raising the version in the same change — upstream supports one minor at a time, and a fault would have two possible causes.
  INVARIANT: a revert of the change that brings automated sync is synced by the root in both regions before the Application is removed — unenforced; removing both at once deletes the operator.
  NON-GOAL: the CRDs on removal — the cascade of the installed ArgoCD leaves them.
- @deploy @eso @version — The chart version of External Secrets stands in the Application and in the settings the boot script renders, and a test holds them, the boot script's flags and the converger's annotations equal; Renovate does not raise the chart. The boot script waits for the operator before it hands the cluster to ArgoCD, so it cannot lose its step until it is replaced.
  REJECTED: taking the step out of the boot script now — a new control plane would wait for an operator that only ArgoCD installs, and ArgoCD would wait for the boot script.
  REJECTED: one Renovate manager that raises every copy — a raise of this chart is a planned unit, not a merge of a bot.
  INVARIANT: a version, flag or annotation that differs between the copies fails — the test of the Application.
  NON-GOAL: a version set in a settings file git does not track — the test cannot read it.

## Takeaway
- takeaway: when a controller finds its objects by a label, writing the label's owner is already taking the objects; decide what its removal does before it exists.
