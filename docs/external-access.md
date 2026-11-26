# External Access

How to reach production systems from a workstation. Every repeated read and every terraform
plan runs through one CLI, `.claude/skills/ops/bin/ops` (ADR 512); the access scripts it runs
live in `scripts/ops/access/`, and one-time credential provisioning lives in
`scripts/ops/provision/`. Every read command is read-only by construction: SQL authenticates
as a SELECT-only MySQL user, log and metrics queries use a read-scoped observability token. The
one command that writes is `tf apply`, which the user runs.

Transport is SSM Session Manager (port forwarding through an instance), not SSH:
no inbound ports on any node, IAM controls who can connect, and every session is
recorded in CloudTrail.

## Commands

`ops <command> [args] [--env prod|staging|mgmt]`: `--env` picks the AWS profile (`prod`, `staging`
or `mgmt` in `~/.aws/config`, default `prod`; a management-account stack defaults to `mgmt`) and
checks its SSO session first. Each run writes
`.claude/reports/logs/ops-<command>-<ts>.log` and exits with the script's code. CI does not use
the CLI; it calls the scripts by path with its own credentials. The Bash hook
`.claude/hooks/ops-redirect.sh` refuses a direct call to a wrapped script and names the `ops`
command. `ops list` prints every command, `ops --help` the details.

| Command | Runs | Target | Notes |
|---------|------|--------|-------|
| `tunnel start\|stop\|status [--site oregon\|seoul]` | `access/rds-tunnel.sh` | RDS network path | Local port 3306 (override `RDS_TUNNEL_PORT`); oregon = primary, seoul = replica |
| `sql "SQL" [--site …]` / `sql -f file.sql [--site …]` | `access/rds-query.sh` | Prod MySQL | Auto-starts the tunnel; throwaway `mysql:8` client container |
| `logs '<logql>' [--since D] [--limit N]` / `logs --day YYYY-MM-DD '<logql>'` | `access/logs-query.sh` | Grafana Cloud Loki | Labels: `namespace`, `pod`, `app` (backend), `cluster` (oregon\|seoul) |
| `metrics '<promql>' [--since D] [--step S] [--names]` | `access/metrics-query.sh` | Grafana Cloud Mimir | `--names` adds the metric name to each line. The dashboard's Prometheus panels; CloudWatch panels query via `aws cloudwatch` directly |
| `verify db` | `access/db-observability-check.sh` | DB observability path | Metric families per cluster, `mysql_up`, the DB alert rules |
| `verify fleet [--site oregon\|seoul]` | `fleet-verify.sh` | One site's fleet (default oregon) | Nodes, pods, Argo CD apps, RDS reachability, etcd snapshot count, S3 count and newest age, the snapshot metric series; the kubeconfig fetch (`--kubeconfig`) stays a direct script call |
| `verify rules [--since D]` | `access/metrics-query.sh` | Grafana Cloud Mimir | One line per Prometheus alert rule (`ok`, `ok (absent until event)`, `NO SERIES <metric> (<sites>)`, `unreadable`) from one query over the window (default 24h); exit 0 all ok, 1 a metric has no series, 2 unreadable |
| `nodes [--site oregon\|seoul]` | `access/fleet-nodes.sh` | One site's instances and nodes (default oregon) | Joined, joining, NOT JOINED or gone per instance and node; exit 0, 4 (joining) or 1; prints the `!` terminate command of a NOT JOINED surge instance and never runs it |
| `bootlog <instance-id> [--site …] [--lines N]` | `access/boot-log.sh` | EC2 console output | The last N lines (default 60), redacted by `ops`; the script alone prints the raw output |
| `app [--site oregon\|seoul]` | `access/argocd-app.sh` | One site's root Application (default oregon) | Source path, values file, sync, health, automated sync, conditions and every resource that is not Synced |
| `tf plan <stack> [-- args]` | `terraform-run.sh … plan` | Terraform | Plan file and full text go to the log; prints the summary and, when every replace, destroy, forget and update of an `aws_instance`, `aws_autoscaling_group` or `aws_db_instance` is named with `--expect`, the `! … ops tf apply <plan file>` command the user runs. A stack with an `envs/` directory (`rds`) gets `envs/<env>.tfvars` and the profile's account id from ops and refuses `-var-file`, and `-var` for a variable that file sets or for `aws_account_id` |
| `tf apply <plan file>` | `terraform-run.sh … apply` | Terraform | The user runs it with `!`; applies only a plan file `tf plan` cleared and that has not changed since, with the stack and profile from its verdict file |
| `whoami` | `aws sts get-caller-identity` | AWS identity | The account the selected profile resolves to |
| `doctor` | — | Workstation | SSO session per profile, `session-manager-plugin`, Docker and the mysql image, `jq`, open tunnels |

```bash
.claude/skills/ops/bin/ops sql "SELECT COUNT(*) FROM planner_catalog"
.claude/skills/ops/bin/ops sql -f query.sql --site seoul
.claude/skills/ops/bin/ops logs '{app="backend"} |= "ERROR"' --since 24h
.claude/skills/ops/bin/ops logs --day 2026-07-23 '{app="backend", cluster="seoul"}'
.claude/skills/ops/bin/ops --env staging tf plan secrets
```

## One-time setup

1. Store the read-only credentials first: `scripts/ops/provision/rds-readonly-secrets.sh`.
   The password is generated in-process (base64 alphabet, so SQL-quote-safe)
   and goes straight to Secrets Manager — it is never displayed or typed.
2. Create the MySQL user: `scripts/ops/rds-bootstrap-readonly-user.sh`.
3. Mint a Grafana Cloud access-policy token with `logs:read` + `metrics:read`
   scopes, then store
   it with `scripts/ops/provision/grafana-secrets.sh`. That script writes the whole
   `danteplanner/grafana/cloud` bundle, so every run supplies all five current values, not only
   the read token.
4. `.claude/skills/ops/bin/ops tf plan secrets`, then the printed `! … ops tf apply <plan file>` — the
   secret names are enrolled in `secret_names`, so apply attaches the Seoul replica to each container.

Workstation prerequisites: AWS CLI with the `prod` and `staging` SSO profiles,
`session-manager-plugin`, `docker`, `jq`; `ops doctor` reports each one.

Terraform is run through `scripts/ops/terraform-run.sh`, never the raw binary. It refuses a
command whose credentials belong to a different account than the state bucket it would write,
and one whose directory holds an auto-loaded `terraform.tfvars`, then hands off unchanged.
Plans run as `ops tf plan <stack>` and applies as `ops tf apply <plan file>`, both of which call it;
apply is a command only the user runs, typed with `!`, and the hook refuses every form of it that it
can see in a Bash-tool command, which guards ordinary commands but is no sandbox.

## Credential handling

Secrets Manager is the only credential store (the `danteplanner/rds/readonly` bundle with
`username` and `password`, and `observability_read_token` in the `danteplanner/grafana/cloud`
bundle). Scripts fetch values per invocation and pass them through environment variables
only — never argv, never a file. The provision scripts prompt with hidden input for the same
reason.

## Adding an access script

New targets (control plane, individual pods) follow the same shape:

- The script goes in `scripts/ops/access/`, named `<target>-<verb>.sh`.
- A read the workstation repeats gets an `ops` command: a row in the CLI's `DISPATCH_TABLE`
  (`.claude/skills/ops/lib/opscli/commands.py`), its argument mapping, the hook's replacement for a direct call,
  and the pinned table in the CLI's tests. Write paths stay scripts run from runbooks.
- Reuse SSM as transport: `AWS-StartPortForwardingSessionToRemoteHost` for a
  network path, `aws ssm send-command` for one-shot remote execution.
- Any new credential gets a Secrets Manager container, a `*-secrets.sh`
  injector in `scripts/ops/provision/`, and enrollment in
  `terraform/secrets/variables.tf` `secret_names`.
- Default to the least privilege that serves the daily need; write paths need an
  explicit decision, not a broader grant.
