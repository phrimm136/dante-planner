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

`scripts/ops/provision/k3s-snapshot-schedule-repair.sh --site <site>` runs these steps on the CP
through SSM. It stops at the first failure and names the step.

1. It takes one snapshot with `k3s etcd-snapshot save`. It then waits for a new
   `ETCDSnapshotFile` with the label `etcd.k3s.cattle.io/snapshot-storage-node=s3`.
   That label marks the S3 copy on the k3s etcd-snapshot page
   (https://docs.k3s.io/cli/etcd-snapshot). No new S3 copy is a failure at this step.
   The message says the local snapshot was written and the S3 copy is missing.
   Read the k3s journal for the upload error: `journalctl -u k3s --since '-15 min'`.
   This step is the first proof that the CP can write the snapshot bucket.
2. It copies `/etc/systemd/system/k3s.service` to a dated `.bak-` file beside it.
3. It replaces the malformed line with `'0 */6 * * *' \`.
4. It runs `systemctl daemon-reload` and restarts k3s.
5. It waits up to 300 seconds for `/readyz` and for every node to be Ready.
   Past that bound it restores the backup, restarts k3s again and reports the failure.
6. It prints the local and S3 snapshot counts and the cron line of the unit.

Before step 1 the script reads the line after the flag.
It refuses to change a line that is not the known malformed line.
It refuses to restart k3s when `systemctl show -p KillMode k3s` does not print
`KillMode=process`. The next section depends on that value.
A second run finds the repaired line, reports `ALREADY REPAIRED` and changes nothing.
The hours in the cron come from `etcd_snapshot_interval_hours` in
`terraform/modules/fleet/variables.tf`.

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

   The output names the unit line as found, the unit's `KillMode=` line, the value systemd uses,
   the k3s state, and the local and S3 snapshot counts.
   Stop when the KillMode lines do not say `KillMode=process`.

2. Run the repair on Seoul.

   ```bash
   AWS_PROFILE=prod scripts/ops/provision/k3s-snapshot-schedule-repair.sh --site seoul
   ```

   The last line is `REPAIRED`. The S3 count is 1 or more.

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

The script restores the backup by itself when step 5 fails.
To roll back by hand, run these commands on the CP through SSM:

```bash
cp -p /etc/systemd/system/k3s.service.bak-<timestamp> /etc/systemd/system/k3s.service
systemctl daemon-reload
systemctl restart k3s
```

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
