#!/usr/bin/env bash
# Fail when Grafana holds a second copy of a declared alert rule, or a rule no file declares.
# Reads only. Usage: GRAFANA_URL=... GRAFANA_TOKEN=... bash check-alert-rules.sh
set -euo pipefail
: "${GRAFANA_URL:?set GRAFANA_URL}"
: "${GRAFANA_TOKEN:?set GRAFANA_TOKEN}"

get() { curl -sS --fail-with-body -H "Authorization: Bearer ${GRAFANA_TOKEN}" "${GRAFANA_URL}$1"; }

FOLDER="${GRAFANA_ALERT_FOLDER:-danteplanner-alerts}"
folders=$(get /api/folders) || { echo "listing folders failed: ${folders}"; exit 1; }
folder_uids=$(printf '%s' "$folders" | jq -r --arg t "$FOLDER" '.[] | select(.title==$t) | .uid')
[ -n "$folder_uids" ] || { echo "folder ${FOLDER} not found"; exit 1; }
[[ "$folder_uids" != *$'\n'* ]] || { echo "more than one folder is titled ${FOLDER}, and the imports write to only one of them. Keep one; before deleting another, move its rules, since no import recreates staleness-meta-* (create-staleness-rules.sh does):"; echo "$folder_uids"; exit 1; }
live=$(get /api/v1/provisioning/alert-rules) || { echo "listing alert rules failed: ${live}"; exit 1; }
printf '%s' "$live" | python3 "$(dirname "$0")/rules.py" audit "$folder_uids"
