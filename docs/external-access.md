# External Access

How to reach production systems from a workstation. Every access script lives in
`scripts/ops/access/`; one-time credential provisioning lives in
`scripts/ops/provision/`. All
current paths are read-only by construction: SQL authenticates as a SELECT-only
MySQL user, log and metrics queries use a read-scoped observability token.

Transport is SSM Session Manager (port forwarding through an instance), not SSH:
no inbound ports on any node, IAM controls who can connect, and every session is
recorded in CloudTrail.

## Scripts

| Script | Target | Notes |
|--------|--------|-------|
| `access/rds-tunnel.sh start\|stop\|status [oregon\|seoul]` | RDS network path | Local port 3306 (override `RDS_TUNNEL_PORT`); oregon = primary, seoul = replica |
| `access/rds-query.sh "SQL" [site]` / `-f file.sql [site]` | Prod MySQL | Auto-starts the tunnel; throwaway `mysql:8` client container |
| `access/logs-query.sh '<logql>' [since] [limit]` / `--day YYYY-MM-DD '<logql>'` | Grafana Cloud Loki | Labels: `namespace`, `pod`, `app` (backend), `cluster` (oregon\|seoul) |
| `access/metrics-query.sh '<promql>' [since] [step]` | Grafana Cloud Mimir | The dashboard's Prometheus panels; CloudWatch panels query via `aws cloudwatch` directly |

```bash
scripts/ops/access/rds-query.sh "SELECT COUNT(*) FROM planner_catalog"
scripts/ops/access/rds-query.sh -f query.sql seoul
scripts/ops/access/logs-query.sh '{app="backend"} |= "ERROR"' 24h
scripts/ops/access/logs-query.sh --day 2026-07-23 '{app="backend", cluster="seoul"}'
```

## One-time setup

1. Store the read-only credentials first: `scripts/ops/provision/rds-readonly-secrets.sh`.
   The password is generated in-process (base64 alphabet, so SQL-quote-safe)
   and goes straight to Secrets Manager — it is never displayed or typed.
2. Create the MySQL user: `scripts/ops/rds-bootstrap-readonly-user.sh`.
3. Mint a Grafana Cloud access-policy token with `logs:read` + `metrics:read`
   scopes, then store
   it: `scripts/ops/provision/grafana-read-secrets.sh`.
4. `terraform -chdir=terraform/secrets apply` — the secret names are enrolled in
   `secret_names`, so apply attaches the Seoul replica to each container.

Workstation prerequisites: AWS CLI with credentials, `session-manager-plugin`,
`docker`, `jq`.

## Credential handling

Secrets Manager is the only credential store (`danteplanner/rds/readonly-*`,
`danteplanner/grafana/observability-read-token`). Scripts fetch values per invocation and
pass them through environment variables only — never argv, never a file. The
provision scripts prompt with hidden input for the same reason.

## Adding an access script

New targets (control plane, individual pods) follow the same shape:

- The script goes in `scripts/ops/access/`, named `<target>-<verb>.sh`.
- Reuse SSM as transport: `AWS-StartPortForwardingSessionToRemoteHost` for a
  network path, `aws ssm send-command` for one-shot remote execution.
- Any new credential gets a Secrets Manager container, a `*-secrets.sh`
  injector in `scripts/ops/provision/`, and enrollment in
  `terraform/secrets/variables.tf` `secret_names`.
- Default to the least privilege that serves the daily need; write paths need an
  explicit decision, not a broader grant.
