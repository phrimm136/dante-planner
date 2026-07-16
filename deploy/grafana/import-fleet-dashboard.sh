#!/usr/bin/env bash
# Import (or update) the single-page fleet health dashboard.
# Usage: GRAFANA_URL=... GRAFANA_TOKEN=... j-import-fleet-dashboard.sh
set -euo pipefail
: "${GRAFANA_URL:?set GRAFANA_URL}"
: "${GRAFANA_TOKEN:?set GRAFANA_TOKEN}"
auth=(-H "Authorization: Bearer ${GRAFANA_TOKEN}" -H "Content-Type: application/json")
DIR=$(dirname "$0")

FOLDER="${GRAFANA_DASHBOARD_FOLDER:-danteplanner-dashboards}"
FOLDER_UID=$(curl -s "${auth[@]}" "${GRAFANA_URL}/api/folders" |
  jq -r --arg t "$FOLDER" '[.[] | select(.title==$t)][0].uid // empty')
if [ -z "$FOLDER_UID" ]; then
  FOLDER_UID=$(curl -s "${auth[@]}" -X POST "${GRAFANA_URL}/api/folders" \
    -d "$(jq -n --arg t "$FOLDER" '{title: $t}')" | jq -r .uid)
  echo "created folder ${FOLDER} (${FOLDER_UID})"
fi
[ -n "$FOLDER_UID" ] && [ "$FOLDER_UID" != null ] || { echo "could not find or create folder ${FOLDER}"; exit 1; }

resp=$(jq -n --slurpfile d "$DIR/fleet-dashboard.json" --arg f "$FOLDER_UID" \
    '{dashboard: $d[0], folderUid: $f, overwrite: true}' |
  curl -s -w '\n%{http_code}' "${auth[@]}" -X POST "${GRAFANA_URL}/api/dashboards/db" -d @-)
code=${resp##*$'\n'}; body=${resp%$'\n'*}
if [ "$code" = 200 ]; then
  printf '%s' "$body" | jq -r '"imported: " + .url'
else
  echo "FAILED (HTTP $code): $(printf '%s' "$body" | head -c 300)"; exit 1
fi
