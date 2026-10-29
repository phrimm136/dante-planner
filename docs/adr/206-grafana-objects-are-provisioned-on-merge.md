# 206 grafana-objects-are-provisioned-on-merge
epic: none · pr: none

## Decisions
- @grafana @provisioning — A workflow runs the cluster alert-rule import, the app alert-rule import and the fleet dashboard import, in that order, on every push to `main` that touches the Grafana provisioning directory or the workflow itself, and on manual dispatch; the Grafana token is a repository secret, and the Grafana URL, the etcd snapshot interval and the CloudWatch and Loki datasource uids are repository variables, because none of them opens anything without the token and a variable stays readable in the run log. A rule change merged to `main` reached Grafana only when someone ran the imports by hand with the datasource uids looked up and typed in, so a merged rule could sit unapplied. The Terraform grafana stack stays the target state and this workflow is the interim until it lands.
  REJECTED: the Terraform grafana stack now — it moves the provisioning seam, retires the import scripts and locks the objects against UI edits, a design change larger than the pain of the hand-run.
  REJECTED: resolving the datasource uids in the workflow with an admin token — the token in use is not org admin, and the uids are stable for the life of a stack, so a repository variable set once holds them.
  INVARIANT: every script the workflow runs is idempotent, updating or overwriting what an earlier run created — unenforced: nothing refuses a create-only script added to the run block.
  NON-GOAL: drift detection — an object edited or deleted in the UI stays that way until the next merge that touches the directory; detecting drift is the Terraform stack's job.

## Takeaway
- takeaway: provisioning that waits on a person to run it with values looked up by hand lags the merged source; once the scripts are idempotent, running them on merge with the values stored once closes the gap without moving the seam.
