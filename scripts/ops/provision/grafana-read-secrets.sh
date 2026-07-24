#!/usr/bin/env bash
# Inject the Grafana Cloud observability READ token VALUE (logs + metrics
# queries). Mint an access-policy token with logs:read + metrics:read scopes in
# the Grafana Cloud console first. Creates the container on first run (then
# enrolled in terraform/secrets secret_names); each signal authenticates with
# its own tenant user id (loki-username / remote-write-username).
# Usage: grafana-read-secrets.sh   (value prompted, never echoed, never on disk)
set -euo pipefail

read -rsp "Access-policy token with logs:read + metrics:read (hidden): " LK_TOKEN; echo

name=danteplanner/grafana/observability-read-token
if ! aws secretsmanager describe-secret --region us-west-2 --secret-id "$name" >/dev/null 2>&1; then
  aws secretsmanager create-secret --region us-west-2 --name "$name" >/dev/null
  echo "  created  $name  (already enrolled in terraform/secrets secret_names; apply for replication)"
fi
printf %s "$LK_TOKEN" | aws secretsmanager put-secret-value --region us-west-2 \
  --secret-id "$name" --secret-string file:///dev/stdin >/dev/null
echo "done — verify: scripts/ops/access/logs-query.sh '{app=\"backend\"}' 1h 5"
