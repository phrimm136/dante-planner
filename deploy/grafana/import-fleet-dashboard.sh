#!/usr/bin/env bash
# Import (or update) the single-page fleet health dashboard.
# Usage: GRAFANA_URL=... GRAFANA_TOKEN=... j-import-fleet-dashboard.sh
set -euo pipefail
: "${GRAFANA_URL:?set GRAFANA_URL}"
: "${GRAFANA_TOKEN:?set GRAFANA_TOKEN}"
auth=(-H "Authorization: Bearer ${GRAFANA_TOKEN}" -H "Content-Type: application/json")
DIR=$(dirname "$0")

FOLDER_UID=$(curl -s "${auth[@]}" "${GRAFANA_URL}/api/folders" |
  jq -r '[.[] | select(.title=="danteplanner-dashboards")][0].uid // empty')
[ -n "$FOLDER_UID" ] || { echo "folder danteplanner-dashboards not found — run i-import-dashboards.sh first"; exit 1; }

resp=$(jq -n --slurpfile d "$DIR/fleet-dashboard.json" --arg f "$FOLDER_UID" \
    '{dashboard: $d[0], folderUid: $f, overwrite: true}' |
  curl -s -w '\n%{http_code}' "${auth[@]}" -X POST "${GRAFANA_URL}/api/dashboards/db" -d @-)
code=${resp##*$'\n'}; body=${resp%$'\n'*}
if [ "$code" = 200 ]; then
  printf '%s' "$body" | jq -r '"imported: " + .url'
else
  echo "FAILED (HTTP $code): $(printf '%s' "$body" | head -c 300)"; exit 1
fi
