# Switching a root Application to the chart

Moves the root Application of one site from `deploy/overlays/<site>` to `deploy/chart`. Run it for
Seoul first, and for Oregon after Seoul has served for a while. Delete this file and
`scripts/ops/provision/gitops-chart-switch.sh` when the overlays go.

Before: `main` carries the chart, no deploy-fleet run is in progress, and
`.claude/skills/ops/bin/ops verify fleet --site <site>` is healthy. A release that lands during the
switch waits until automated sync is on again.

1. `AWS_PROFILE=prod scripts/ops/provision/gitops-chart-switch.sh --site seoul --dry-run`
   reads only. It must end with `DRY RUN: ready to switch`.
2. The same command without `--dry-run`. Its last line is the ending:

| last line | state | next |
|---|---|---|
| `SWITCHED` | chart, automated sync on | step 3 |
| `STOPPED: ...` | nothing was changed | remove the named cause |
| `NOTHING CHANGED: ...` | already on the chart | step 3 |
| `ROLLBACK DONE` | the overlay, automated sync on, Synced and Healthy | nothing |
| `NOTHING TO ROLL BACK: ...` | the overlay, automated sync on | nothing |
| `ROLLED BACK` | the old source is back, automated sync on | read what differed, above it |
| `FAILED: ...`, or no ending | the source or automated sync may be off its old value | `--rollback`, then read the Application |

3. `.claude/skills/ops/bin/ops verify fleet --site <site>` is healthy. mysqld-exporter restarted once.
   No other Pod did.

`--rollback` returns a site to its overlay with automated sync on, from the chart or from an overlay
whose sync stayed off.
