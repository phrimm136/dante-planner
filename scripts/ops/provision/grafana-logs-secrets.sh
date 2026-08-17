#!/usr/bin/env bash
# Inject the Grafana Cloud Loki push credential VALUES. The secret containers
# are Terraform-managed (terraform/secrets, cross-region replication included) —
# this script only writes versions into the PRIMARY region; AWS replicates them
# to Seoul. A missing container means the secrets stack was never applied.
# Usage: h3-provision-loki.sh   (values prompted, never echoed, never on disk)
set -euo pipefail

repo_root=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
# shellcheck source=../lib/secrets.sh
source "$repo_root/scripts/ops/lib/secrets.sh"
region=us-west-2

read -rp  "Grafana Cloud Loki numeric user ID (from the stack's Loki 'Send Logs' page): " LK_USER
read -rsp "Access-policy token with logs:write (hidden): " LK_TOKEN; echo

put() { # name value
  local name=$1 value=$2
  if printf %s "$value" | put_secret "$region" "$name"; then
    echo "  updated  $name  (replicates to ap-northeast-2)"
  else
    echo "ERROR: put-secret-value failed for $name — if the secret does not exist," >&2
    echo "the container is Terraform-managed: apply terraform/secrets first." >&2
    exit 1
  fi
}

put danteplanner/grafana/loki-username "$LK_USER"
put danteplanner/grafana/loki-password "$LK_TOKEN"
echo "done — force ESO if impatient:"
echo "  kubectl -n danteplanner annotate externalsecret grafana-loki-write force-sync=\$(date +%s) --overwrite"
