#!/usr/bin/env bash
# Inject the Grafana Cloud remote_write credential VALUES. The secret containers
# are Terraform-managed (terraform/secrets, cross-region replication included) —
# this script only writes versions into the PRIMARY region; AWS replicates them
# to Seoul. A missing container means the secrets stack was never applied.
# Usage: h1-provision-remote-write.sh
# Values prompted, never echoed, never written to disk — secrets never enter the repo.
set -euo pipefail

read -rp  "Grafana Cloud stack numeric ID (remote_write username): " GC_USER
read -rsp "Access-policy token with metrics:write (hidden): " GC_TOKEN; echo

put() { # name value
  local name=$1 value=$2
  if aws secretsmanager put-secret-value --region us-west-2 --secret-id "$name" \
       --secret-string "$value" >/dev/null; then
    echo "  updated  $name  (replicates to ap-northeast-2)"
  else
    echo "ERROR: put-secret-value failed for $name — if the secret does not exist," >&2
    echo "the container is Terraform-managed: apply terraform/secrets first." >&2
    exit 1
  fi
}

put danteplanner/grafana/remote-write-username "$GC_USER"
put danteplanner/grafana/remote-write-password "$GC_TOKEN"
echo "done — ESO will materialize the grafana-remote-write Secret within its 1h refresh"
echo "(or force it: kubectl -n danteplanner annotate externalsecret grafana-remote-write force-sync=\$(date +%s) --overwrite)"
