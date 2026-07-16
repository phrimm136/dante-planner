#!/usr/bin/env bash
# Store the Grafana Cloud remote_write credentials in Secrets
# Manager, BOTH regions (each region's SecretStore resolves only its own).
# Usage: h1-provision-remote-write.sh
# Values prompted, never echoed, never written to disk — secrets never enter the repo.
set -euo pipefail

read -rp  "Grafana Cloud stack numeric ID (remote_write username): " GC_USER
read -rsp "Access-policy token with metrics:write (hidden): " GC_TOKEN; echo

put() { # region name value
  local region=$1 name=$2 value=$3
  if aws secretsmanager create-secret --region "$region" --name "$name" \
       --secret-string "$value" >/dev/null 2>&1; then
    echo "  created  $region  $name"
  else
    aws secretsmanager put-secret-value --region "$region" --secret-id "$name" \
      --secret-string "$value" >/dev/null
    echo "  updated  $region  $name"
  fi
}

for region in us-west-2 ap-northeast-2; do
  echo "== $region"
  put "$region" danteplanner/grafana/remote-write-username "$GC_USER"
  put "$region" danteplanner/grafana/remote-write-password "$GC_TOKEN"
done
echo "done — ESO will materialize the grafana-remote-write Secret within its 1h refresh"
echo "(or force it: kubectl -n danteplanner annotate externalsecret grafana-remote-write force-sync=\$(date +%s) --overwrite)"
