#!/usr/bin/env bash
# Inject the Grafana Cloud observability READ token VALUE (logs + metrics
# queries). Mint an access-policy token with logs:read + metrics:read scopes in
# the Grafana Cloud console first. Creates the container on first run (then
# enrolled in terraform/secrets secret_names); each signal authenticates with
# its own tenant user id (loki-username / remote-write-username).
# Usage: grafana-read-secrets.sh   (value prompted, never echoed, never on disk)
set -euo pipefail

repo_root=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
# shellcheck source=../lib/secrets.sh
source "$repo_root/scripts/ops/lib/secrets.sh"
region=us-west-2

read -rsp "Access-policy token with logs:read + metrics:read (hidden): " LK_TOKEN; echo

name=danteplanner/grafana/observability-read-token
if ! secret_exists "$region" "$name"; then
  create_secret "$region" "$name"
  echo "  created  $name  (already enrolled in terraform/secrets secret_names; apply for replication)"
fi
printf %s "$LK_TOKEN" | put_secret "$region" "$name"
echo "done — verify: scripts/ops/access/logs-query.sh '{app=\"backend\"}' 1h 5"
