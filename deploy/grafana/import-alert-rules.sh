#!/usr/bin/env bash
# Provision the Grafana-managed alert rules via the provisioning API.
# Staleness rules are provisioned separately by create-staleness-rules.sh once remote_write data flows.
#
# Usage:
#   GRAFANA_URL=https://<slug>.grafana.net \
#   GRAFANA_TOKEN=<service-account token, Editor role> \
#   ETCD_INTERVAL=43200 \
#   bash import-alert-rules.sh
#
# Live values (URL, token, interval) arrive only via env vars; nothing is written back.
set -euo pipefail

: "${GRAFANA_URL:?set GRAFANA_URL, e.g. https://yourslug.grafana.net (no trailing slash)}"
: "${GRAFANA_TOKEN:?set GRAFANA_TOKEN (service-account token with Editor role)}"
: "${ETCD_INTERVAL:?set ETCD_INTERVAL in seconds (k3s etcd-snapshot interval; k3s default 43200)}"

auth=(-H "Authorization: Bearer ${GRAFANA_TOKEN}" -H "Content-Type: application/json")
# X-Disable-Provenance keeps the rules editable in the UI afterwards; without it the
# provisioning API marks them provenance=api and locks them against UI edits.
noprov=(-H "X-Disable-Provenance: true")

# Grafana Cloud stacks provision the hosted-Prometheus datasource with the stable
# UID grafanacloud-prom; listing /api/datasources needs org Admin, so we don't try.
DS_UID="${DS_UID:-grafanacloud-prom}"
echo "== verifying datasource uid ${DS_UID}"
code=$(curl -s -o /tmp/ds-check.json -w '%{http_code}' "${auth[@]}" \
  "${GRAFANA_URL}/api/datasources/uid/${DS_UID}/health" -X GET)
if [ "$code" != 200 ]; then
  # Advisory only: rule provisioning needs alert-rule write, not datasources:query,
  # so a 403 here does not predict the import's outcome.
  echo "   note: datasource health check returned HTTP ${code} (continuing anyway;"
  echo "   if rule creation 404s on the datasource, re-run with DS_UID=<uid> from"
  echo "   the datasource page URL: /connections/datasources/edit/<uid>)"
else
  echo "   datasource ok"
fi

FOLDER="${GRAFANA_ALERT_FOLDER:-danteplanner-alerts}"
echo "== ensuring folder '${FOLDER}'"
folders=$(curl -s -w '\n%{http_code}' "${auth[@]}" "${GRAFANA_URL}/api/folders")
code=${folders##*$'\n'}
[ "$code" = 200 ] || { echo "   listing folders failed (HTTP ${code}): ${folders%$'\n'*}"; exit 1; }
FOLDER_UID=$(printf '%s' "${folders%$'\n'*}" |
  jq -r --arg t "$FOLDER" '[.[] | select(.title==$t)][0].uid // empty')
if [ -z "$FOLDER_UID" ]; then
  created=$(jq -n --arg title "$FOLDER" '{title: $title}' |
    curl -s -w '\n%{http_code}' "${auth[@]}" -X POST "${GRAFANA_URL}/api/folders" -d @-)
  code=${created##*$'\n'}
  [ "$code" = 200 ] || { echo "   creating folder failed (HTTP ${code}): ${created%$'\n'*}"; exit 1; }
  FOLDER_UID=$(printf '%s' "${created%$'\n'*}" | jq -r .uid)
fi
echo "   folder uid: ${FOLDER_UID}"

# submit_rule TITLE BODY_JSON
submit_rule() {
  local title=$1 body=$2
  local uid method path want verb code
  uid=$(printf '%s' "$title" | tr '[:upper:]' '[:lower:]' | tr -c 'a-z0-9-' '-')
  path="${GRAFANA_URL}/api/v1/provisioning/alert-rules/${uid}"
  code=$(curl -s -o /dev/null -w '%{http_code}' "${auth[@]}" "$path") || code=000
  case "$code" in
    200) method=PUT want=200 verb=updated ;;
    404) method=POST want=201 verb=created path="${GRAFANA_URL}/api/v1/provisioning/alert-rules" ;;
    *) echo "   FAILED ${title}: looking up uid ${uid} returned HTTP ${code}"; exit 1 ;;
  esac
  printf '%s' "$body" | jq --arg uid "$uid" '{uid: $uid} + .' |
  curl -s -w '\n%{http_code}' "${auth[@]}" "${noprov[@]}" -X "$method" "$path" -d @- | {
    resp=$(cat); code=${resp##*$'\n'}; body=${resp%$'\n'*}
    if [ "$code" = "$want" ]; then
      printf '%s' "$body" | jq -r --arg v "$verb" '"   " + $v + ": " + .title + "  (uid " + .uid + ")"'
    else
      echo "   FAILED ${title} (HTTP ${code}): ${body}"; exit 1
    fi
  }
}

echo "== creating or updating rules"
payloads=$(FOLDER_UID="$FOLDER_UID" DS_UID="$DS_UID" python3 "$(dirname "$0")/rules.py" payloads cluster-rules)
rule_count=0
while IFS= read -r payload; do
  submit_rule "$(printf '%s' "$payload" | jq -r .title)" "$(printf '%s' "$payload" | jq -c .body)"
  rule_count=$((rule_count + 1))
done <<< "$payloads"

echo "== done: ${rule_count} rules in folder ${FOLDER}, group cluster-rules (1m interval)"
echo "   All noDataState=OK — non-paging while their series are absent. Staleness rules are created separately by create-staleness-rules.sh."
