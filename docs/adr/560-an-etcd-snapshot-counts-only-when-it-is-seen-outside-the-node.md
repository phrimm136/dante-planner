# 560 an-etcd-snapshot-counts-only-when-it-is-seen-outside-the-node
epic: none · pr: none

## Decisions
- @etcd @snapshots @deadman — The snapshot alert fires for a cluster that has no snapshot series while its metrics exporter is up, as well as for one whose newest snapshot is old. Both control planes ran for months with a schedule that never fired, because the installer mangled the cron expression, and an alert written as "the newest snapshot is too old" has nothing to compare when no snapshot ever existed. Silence read as health.
  REJECTED: an alert on age alone with a note to check that the metric exists — the note is the thing nobody reads until the day the backup is needed.
  REJECTED: firing on any absent series, the exporter included — a cluster whose exporter is down already fires its own alert, and two alerts for one cause hide which one to fix.
  INVARIANT: a cluster with a live exporter and no snapshot series fires — a case with that shape is part of the rule, and it fails on the age-only expression.
  INVARIANT: an exporter restart does not fire it — the wait before firing is longer than the gap a restart leaves, and a case covers the restart.
- @etcd @snapshots @s3 — A snapshot counts as a backup only when a copy in the bucket is listed. The fleet verification fails a cluster whose snapshots exist on the node only, and the repair of the running control planes stops before it touches the service unit when the first snapshot it takes has no copy in the bucket. The right to write the bucket was granted in code and never exercised, so nothing had shown that an upload works.
  REJECTED: counting any listed snapshot — a snapshot on the disk of the one control-plane node is lost with that node, which is the case a backup is for.
  NON-GOAL: a restore — the bucket copy is proven to exist, not to rebuild a cluster; that needs a control plane that restores on boot and keeps its address.
- @etcd @snapshots @interval — The snapshot interval is one whole number of hours in the fleet's Terraform variables. The boot script builds the schedule from it, and the alert threshold and the verification limit are computed from the same number. The interval lived in the boot script as a cron string and in a repository variable as seconds, and each side assumed the other.
  REJECTED: a cron string as the one value — a threshold cannot be derived from a cron string without parsing it, and the mangled expression came from passing such a string through a shell.
  INVARIANT: the schedule settings reach k3s through its config file and not through the installer's argument string — a test renders both boot scripts and fails on a quote or a wildcard in that string, and on a snapshot setting inside it.

## Takeaway
- takeaway: a check that waits for bad data passes when there is no data; for anything that must happen on a schedule, the absence of its trace is the first failure to alert on.
