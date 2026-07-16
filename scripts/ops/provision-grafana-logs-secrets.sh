#!/usr/bin/env bash
# Store the Grafana Cloud Loki push credentials in Secrets Manager, BOTH regions.
# Usage: h3-provision-loki.sh   (values prompted, never echoed, never on disk)
set -euo pipefail

read -rp  "Grafana Cloud Loki numeric user ID (from the stack's Loki 'Send Logs' page): " LK_USER
read -rsp "Access-policy token with logs:write (hidden): " LK_TOKEN; echo

put() {
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
  put "$region" danteplanner/grafana/loki-username "$LK_USER"
  put "$region" danteplanner/grafana/loki-password "$LK_TOKEN"
done
echo "done — force ESO if impatient:"
echo "  kubectl -n danteplanner annotate externalsecret grafana-loki-write force-sync=\$(date +%s) --overwrite"
