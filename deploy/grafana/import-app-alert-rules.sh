#!/usr/bin/env bash
# Provision the app-level alert rules (group app-rules) — fleet-era successors to
# the legacy DantePlanner-* CloudWatch alarms that watched the retired single-EC2
# host. Thresholds carry over 1:1 from the legacy alarms; per-node values were
# tuned for a t3.medium, so retune if fleet nodes diverge from that profile.
#
# Usage:
#   GRAFANA_URL=https://<slug>.grafana.net \
#   GRAFANA_TOKEN=<service-account token, Editor role> \
#   bash import-app-alert-rules.sh
set -euo pipefail

: "${GRAFANA_URL:?set GRAFANA_URL, e.g. https://yourslug.grafana.net (no trailing slash)}"
: "${GRAFANA_TOKEN:?set GRAFANA_TOKEN (service-account token with Editor role)}"
DS_UID="${DS_UID:-grafanacloud-prom}"
: "${CW_DS_UID:?set CW_DS_UID (uid of the Grafana CloudWatch datasource the fleet dashboard binds as DS_CW)}"

auth=(-H "Authorization: Bearer ${GRAFANA_TOKEN}" -H "Content-Type: application/json")
# X-Disable-Provenance keeps the rules editable in the UI afterwards; without it the
# provisioning API marks them provenance=api and locks them against UI edits.
noprov=(-H "X-Disable-Provenance: true")

FOLDER="${GRAFANA_ALERT_FOLDER:-danteplanner-alerts}"
FOLDER_UID=$(curl -s "${auth[@]}" "${GRAFANA_URL}/api/folders" |
  jq -r --arg t "$FOLDER" '[.[] | select(.title==$t)][0].uid // empty')
[ -n "$FOLDER_UID" ] || { echo "folder ${FOLDER} not found — run import-alert-rules.sh first"; exit 1; }
echo "   folder uid: ${FOLDER_UID}"

if [ -z "${LOKI_DS_UID:-}" ]; then
  datasources=$(curl -s -w '\n%{http_code}' "${auth[@]}" "${GRAFANA_URL}/api/datasources") || datasources=$'\n000'
  code=${datasources##*$'\n'}
  [ "$code" = 200 ] || { echo "listing datasources failed (HTTP ${code}) — re-run with LOKI_DS_UID=<uid of the Loki datasource>"; exit 1; }
  mapfile -t lokis < <(printf '%s' "${datasources%$'\n'*}" | jq -r '.[] | select(.type=="loki") | "\(.name)=\(.uid)"')
  [ "${#lokis[@]}" -gt 0 ] || { echo "no datasource of type loki found — re-run with LOKI_DS_UID=<uid of the Loki datasource>"; exit 1; }
  [ "${#lokis[@]}" -eq 1 ] || { echo "${#lokis[@]} datasources of type loki found (${lokis[*]}) — set LOKI_DS_UID=<uid of the logs datasource>" >&2; exit 1; }
  LOKI_DS_UID=${lokis[0]##*=}
fi
echo "   loki datasource uid: ${LOKI_DS_UID}"

rules=$(curl -s -w '\n%{http_code}' "${auth[@]}" "${GRAFANA_URL}/api/v1/provisioning/alert-rules") || rules=$'\n000'
code=${rules##*$'\n'}
[ "$code" = 200 ] || { echo "listing alert rules failed (HTTP ${code}): ${rules%$'\n'*}"; exit 1; }
EXISTING=$(printf '%s' "${rules%$'\n'*}" | jq -c --arg f "$FOLDER_UID" '[.[] | select(.folderUID==$f) | {title, uid}]')
created=0 updated=0

# submit_rule TITLE JSON — PUTs over the folder's rule with that title, else POSTs
submit_rule() {
  local title=$1 json=$2
  local uids uid method path want verb resp code body
  mapfile -t uids < <(printf '%s' "$EXISTING" | jq -r --arg t "$title" '.[] | select(.title==$t) | .uid')
  if [ "${#uids[@]}" -gt 0 ]; then
    uid=${uids[0]}
    for dup in "${uids[@]:1}"; do
      echo "   warning: ${title} also exists as uid ${dup} — delete that duplicate in the Grafana UI"
    done
    method=PUT want=200 verb=updated path="${GRAFANA_URL}/api/v1/provisioning/alert-rules/${uid}"
    json=$(printf '%s' "$json" | jq -c --arg uid "$uid" '. + {uid: $uid}')
  else
    method=POST want=201 verb=created path="${GRAFANA_URL}/api/v1/provisioning/alert-rules"
  fi
  resp=$(printf '%s' "$json" |
    curl -s -w '\n%{http_code}' "${auth[@]}" "${noprov[@]}" -X "$method" "$path" -d @-) || resp=$'\n000'
  code=${resp##*$'\n'}; body=${resp%$'\n'*}
  if [ "$code" = "$want" ]; then
    printf '%s' "$body" | jq -r --arg v "$verb" '"   " + $v + ": " + .title + "  (uid " + .uid + ")"'
    if [ "$verb" = created ]; then created=$((created + 1)); else updated=$((updated + 1)); fi
  else
    echo "   FAILED ${title} (HTTP ${code}): ${body}"; exit 1
  fi
}

echo "== creating or updating app rules (legacy CW parity)"
payloads=$(FOLDER_UID="$FOLDER_UID" DS_UID="$DS_UID" LOKI_DS_UID="$LOKI_DS_UID" CW_DS_UID="$CW_DS_UID" \
  python3 "$(dirname "$0")/rules.py" payloads app-rules)
while IFS= read -r payload; do
  submit_rule "$(printf '%s' "$payload" | jq -r .title)" "$(printf '%s' "$payload" | jq .body)"
done <<< "$payloads"

echo "== done: ${created} created, ${updated} updated in folder ${FOLDER}, group app-rules"
echo "   All noDataState=OK except seoul-replica-lag (Alerting) — non-paging while a series is absent; staleness-meta owns absence."
