#!/usr/bin/env bash
# Delete any leftover 'delivery-drill' scratch rules (drill-alert-delivery.sh
# interrupted before its own cleanup). Idempotent.
set -euo pipefail
: "${GRAFANA_URL:?set GRAFANA_URL}"
: "${GRAFANA_TOKEN:?set GRAFANA_TOKEN}"
auth=(-H "Authorization: Bearer ${GRAFANA_TOKEN}")

uids=$(curl -s "${auth[@]}" "${GRAFANA_URL}/api/v1/provisioning/alert-rules" |
  jq -r '.[] | select(.title=="delivery-drill") | .uid')
[ -n "$uids" ] || { echo "no delivery-drill rules found — nothing firing"; exit 0; }
for uid in $uids; do
  code=$(curl -s -o /dev/null -w '%{http_code}' "${auth[@]}" \
    -H "X-Disable-Provenance: true" -X DELETE \
    "${GRAFANA_URL}/api/v1/provisioning/alert-rules/${uid}")
  echo "deleted ${uid}: HTTP ${code} (204 = gone)"
done
