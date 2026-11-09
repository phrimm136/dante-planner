# External Access

How to reach production systems from a workstation. Every repeated read and every terraform
plan runs through one CLI, `.claude/skills/ops/bin/ops` (ADR 512); the access scripts it runs
live in `scripts/ops/access/`, and one-time credential provisioning lives in
`scripts/ops/provision/`. All
current paths are read-only by construction: SQL authenticates as a SELECT-only
MySQL user, log and metrics queries use a read-scoped observability token.

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
| `metrics '<promql>' [--since D] [--step S]` | `access/metrics-query.sh` | Grafana Cloud Mimir | The dashboard's Prometheus panels; CloudWatch panels query via `aws cloudwatch` directly |
| `verify db` | `access/db-observability-check.sh` | DB observability path | Metric families per cluster, `mysql_up`, the DB alert rules |
| `verify oregon` | `oregon-verify.sh` | Oregon fleet | Nodes, pods, Argo CD apps, RDS reachability; the kubeconfig fetch (`--kubeconfig`) stays a direct script call |
| `tf plan <stack> [-- args]` | `terraform-run.sh … plan` | Terraform | Plan file and full text go to the log; prints the summary and the `!` apply command |
| `whoami` | `aws sts get-caller-identity` | AWS identity | The account the selected profile resolves to |
| `doctor` | — | Workstation | SSO session per profile, `session-manager-plugin`, Docker and the mysql image, `jq`, open tunnels |

```bash
.claude/skills/ops/bin/ops sql "SELECT COUNT(*) FROM planner_catalog"
.claude/skills/ops/bin/ops sql -f query.sql --site seoul
.claude/skills/ops/bin/ops logs '{app="backend"} |= "ERROR"' --since 24h
.claude/skills/ops/bin/ops logs --day 2026-07-23 '{app="backend", cluster="seoul"}'
.claude/skills/ops/bin/ops --env staging tf plan secrets -- -var-file=staging.tfvars
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
4. `scripts/ops/terraform-run.sh -chdir=terraform/secrets apply` — the secret names are enrolled in
   `secret_names`, so apply attaches the Seoul replica to each container.

Workstation prerequisites: AWS CLI with the `prod` and `staging` SSO profiles,
`session-manager-plugin`, `docker`, `jq`; `ops doctor` reports each one.

Terraform is run through `scripts/ops/terraform-run.sh`, never the raw binary. It refuses a
command whose credentials belong to a different account than the state bucket it would write,
and one whose directory holds an auto-loaded `terraform.tfvars`, then hands off unchanged.
Plans run as `ops tf plan <stack>`, which calls it; apply stays a command the user runs.

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
  (`.claude/skills/ops/bin/ops`), its argument mapping, the hook's replacement for a direct call,
  and the pinned table in the CLI's tests. Write paths stay scripts run from runbooks.
- Reuse SSM as transport: `AWS-StartPortForwardingSessionToRemoteHost` for a
  network path, `aws ssm send-command` for one-shot remote execution.
- Any new credential gets a Secrets Manager container, a `*-secrets.sh`
  injector in `scripts/ops/provision/`, and enrollment in
  `terraform/secrets/variables.tf` `secret_names`.
- Default to the least privilege that serves the daily need; write paths need an
  explicit decision, not a broader grant.
