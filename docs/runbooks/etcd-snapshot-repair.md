# etcd snapshot schedule repair

This runbook turns on the etcd snapshot schedule of a running control plane (CP).
Run it once per region, Seoul first.

## What was found

On 2026-11-24 neither CP had any etcd snapshot. Both clusters were built on 2026-08-17.
`k3s etcd-snapshot ls` was empty, and no `ETCDSnapshotFile` object existed.

The cause is the k3s unit file. The boot script passed the schedule inside `INSTALL_K3S_EXEC`.
The k3s installer put a backslash before each `*` of that argument.
The unit now holds the line `'0 \*/6 \* \* \*' \` after `'--etcd-snapshot-schedule-cron' \`.
That cron never fires.

The `etcd-snapshot-deadman` alert stayed quiet for the same reason.
It fired only on an old snapshot, and no snapshot existed.
It now also fires when a cluster's kube-state-metrics is up and serves no snapshot series.

New CPs write the schedule into `/etc/rancher/k3s/config.yaml` instead.
A running CP keeps its unit file until this repair runs.

## What the repair does

`scripts/ops/provision/k3s-snapshot-schedule-repair.sh --site <site>` runs on the CP through SSM.
It takes the lock directory `/run/k3s-snapshot-schedule-repair.lock` first.
A second run refuses while the directory exists. Every ending of a run removes it.

Before it changes anything, it checks these things:

- The line after `'--etcd-snapshot-schedule-cron' \` is the known malformed line.
- `systemctl show -p KillMode k3s` prints `KillMode=process`. The next section depends on it.
- At least one node is Ready. It records which nodes are Ready, and prints only the counts.
  A status that begins with `Ready` counts, so a cordoned node (`Ready,SchedulingDisabled`)
  is Ready. A node that is not Ready before the run is reported. The run does not check
  it afterwards, because the run did not cause it.

Then it runs these steps:

1. It takes one snapshot with `k3s etcd-snapshot save`. It then waits for a new
   `ETCDSnapshotFile` with the label `etcd.k3s.cattle.io/snapshot-storage-node=s3`.
   That label marks the S3 copy on the k3s etcd-snapshot page
   (https://docs.k3s.io/cli/etcd-snapshot). An S3 snapshot from before the run does not count.
   This step is the first proof that the CP can write the snapshot bucket.
2. It copies `/etc/systemd/system/k3s.service` to a dated `.bak-` file beside it.
3. It replaces the malformed line with `'0 */6 * * *' \`.
4. It runs `systemctl daemon-reload` and `systemctl restart --no-block k3s`.
   The unit has `Type=notify` and `TimeoutStartSec=0`, so a plain restart can wait without
   limit. The systemctl manual says that with `--no-block` the job "is only verified and
   enqueued" (https://www.freedesktop.org/software/systemd/man/latest/systemctl.html).
5. It waits up to 300 seconds. The wait ends with success when k3s is active, the API
   `/readyz` answers `ok`, and every node that was Ready before is Ready again with a node
   Lease renewed after the restart. The Lease is the clock because the kubelet renews it about
   every 10 seconds, while it rewrites the Node status only about every 5 minutes when nothing
   changes (https://kubernetes.io/docs/reference/node/node-status/,
   https://kubernetes.io/docs/concepts/architecture/leases/). So a Ready status cached from
   before the restart cannot pass. The wait ends early when systemd reports k3s failed, or
   when the restart counter of the unit rises, which means k3s exited and systemd restarted it.
6. It prints the local and S3 snapshot counts and the cron line of the unit.

From step 2 on, every way out of the run that is not success goes through one rollback.
The rollback copies the backup over the unit, runs `systemctl daemon-reload`, restarts k3s
with `--no-block` and waits up to 300 seconds in the same way. A run stopped by a signal
takes the same rollback.

The hours in the cron come from `etcd_snapshot_interval_hours` in
`terraform/modules/fleet/variables.tf`.

## Time budgets

The script computes each budget from the one before it:

- Run on the CP: at most 2 x 120 seconds for the save and its S3 copy, 300 seconds for the
  restart, 300 seconds for the rollback, and 120 seconds for everything else. That is 960 seconds.
- SSM command timeout: the run's budget plus 60 seconds, 1020 seconds.
- Wait on your machine: the SSM timeout plus 60 seconds, 1080 seconds.

The script prints the SSM command id first. When the wait on your machine ends first, the
script says so and exits 6. The run on the CP can still be going. Do not run the script again
until `aws ssm get-command-invocation --command-id <id>` shows that the command has ended.
A lock left after SSM killed a run stays until the CP reboots. Remove it only when no
repair command is in progress on the instance.

## Endings

The last line of every run is one of these five. The exit code of the script follows it.

| Last line | Exit | What to do |
|-----------|------|------------|
| `REPAIRED` | 0 | Verify with `ops verify fleet --site <site>`. |
| `ALREADY REPAIRED` | 0 | Nothing. The unit holds the repaired line, k3s is active and `/readyz` answers `ok`. |
| `NOTHING CHANGED: <reason>` | 3 (0 for a dry run) | Read the reason. The unit is unchanged. Fix the cause, then run the dry run again. |
| `ROLLED BACK, k3s serving: <reason>` | 4 | The unit holds the malformed line again and the cluster serves. Read the k3s journal for the reason, then decide before a new run. |
| `ROLLED BACK, k3s NOT serving: <reason>; run <commands>` | 5 | A person must act now. Run the printed commands on the CP through SSM. The cluster API is down. |

A run that ends without one of these lines exits 7. Run the dry run before anything else.

## What stops during the restart

The k3s restart stops the API server until step 5 sees `/readyz` answer `ok`.
Until then these things stop:

- Scheduling of new pods.
- ArgoCD sync.
- The External Secrets refresh of Kubernetes Secrets.
- Every `kubectl` call, the fleet verification included.

These things keep running:

- Pods on the other nodes. Their kubelets and containers do not run under the CP's unit.
- Pods on the CP itself. On 2026-11-24 the k3s unit on both CPs held `KillMode=process`,
  and `systemctl show -p KillMode k3s` printed the same value.
  The systemd manual (https://www.freedesktop.org/software/systemd/man/latest/systemd.kill.html)
  says that with `process` "only the main process itself is killed".
  It also says "Processes remaining alive after stop are left in their control group".
  The main process is the k3s server. The container processes stay alive.
- User traffic through the ingress node to the backend pods.

## Order

Do Seoul first. A failure there shows the problem before Oregon is touched.

1. Read the current state. The dry run sends only reads.

   ```bash
   AWS_PROFILE=prod scripts/ops/provision/k3s-snapshot-schedule-repair.sh --site seoul --dry-run
   ```

   The output names the lock state, the unit line as found, the unit's `KillMode=` line, the
   value systemd uses, the count of Ready nodes, whether their leases renewed in the last
   minute, the k3s state, and the local and S3 snapshot counts. The last line is
   `NOTHING CHANGED: dry run`.
   Stop when the KillMode lines do not say `KillMode=process`, when the lock is held, or when
   the leases did not all renew.

2. Run the repair on Seoul.

   ```bash
   AWS_PROFILE=prod scripts/ops/provision/k3s-snapshot-schedule-repair.sh --site seoul
   ```

   The last line is `REPAIRED`. The S3 count is 1 or more. Any other last line: see Endings.

3. Verify Seoul.

   ```bash
   .claude/skills/ops/bin/ops verify fleet --site seoul
   ```

   The SNAPSHOTS line shows a count above 0, an S3 count above 0 and a fresh newest age.
   The SNAPSHOT METRIC line shows one series or more.

4. Repeat steps 1 to 3 with `--site oregon`.

5. Wait 6 hours for the first scheduled snapshot. Run `ops verify fleet` for each site again.
   The count is higher than after step 2.

## Rollback

The script restores the backup by itself on every failure after step 1.
To roll back by hand, run these commands on the CP through SSM:

```bash
cp -p /etc/systemd/system/k3s.service.bak-<timestamp> /etc/systemd/system/k3s.service
systemctl daemon-reload
systemctl restart --no-block k3s
systemctl is-active k3s
k3s kubectl get --raw /readyz
```

Repeat the last two commands until they print `active` and `ok`.

The rollback brings back the malformed line, so no schedule runs.
The snapshot from step 1 stays in place.

## Confirm in Mimir

Each cluster has a series once kube-state-metrics reads the new snapshot:

```bash
.claude/skills/ops/bin/ops metrics 'count by (cluster) (kube_etcd_snapshot_creation_timestamp_seconds)' --since 1h
```

The result has one row for `oregon` and one for `seoul`.
The newest snapshot age per cluster, in seconds:

```bash
.claude/skills/ops/bin/ops metrics 'time() - max by (cluster) (kube_etcd_snapshot_creation_timestamp_seconds)' --since 1h
```

Each value stays under 32400 (1.5 times the 6-hour schedule).
