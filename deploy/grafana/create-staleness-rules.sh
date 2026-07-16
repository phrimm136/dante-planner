#!/usr/bin/env bash
# create rule M (per-cluster staleness meta) — ONLY after
# remote_write data is confirmed flowing, because an absent-detector fires on
# the pre-wiring absence itself.
# Usage: GRAFANA_URL=... GRAFANA_TOKEN=... h2-create-rule-m.sh
set -euo pipefail
: "${GRAFANA_URL:?set GRAFANA_URL}"
: "${GRAFANA_TOKEN:?set GRAFANA_TOKEN}"
auth=(-H "Authorization: Bearer ${GRAFANA_TOKEN}" -H "Content-Type: application/json")
noprov=(-H "X-Disable-Provenance: true")

echo "== pre-flight: is up{cluster=...} actually arriving in Grafana Cloud?"
for c in oregon seoul; do
  resp=$(curl -s -w '\n%{http_code}' "${auth[@]}" \
    "${GRAFANA_URL}/api/datasources/proxy/uid/grafanacloud-prom/api/v1/query?query=count(up%7Bcluster%3D%22${c}%22%7D)")
  code=${resp##*$'\n'}; body=${resp%$'\n'*}
  if [ "$code" != 200 ]; then
    echo "   PRE-FLIGHT QUERY FAILED for cluster=${c} (HTTP ${code}) — this is a query/permission"
    echo "   problem, NOT evidence of missing data. Response:"
    printf '%s\n' "$body" | head -c 400; echo
    exit 1
  fi
  n=$(printf '%s' "$body" | jq -r '.data.result[0].value[1] // "MISSING"')
  echo "   cluster=${c}: ${n} up-series"
  if [ "$n" = MISSING ] || [ "$n" = 0 ]; then
    echo "ABORT: query succeeded but returned no series for cluster=${c} —"
    echo "creating M now would page immediately. Raw response:"
    printf '%s\n' "$body" | head -c 400; echo
    exit 1
  fi
done

FOLDER_UID=$(curl -s "${auth[@]}" "${GRAFANA_URL}/api/folders" |
  jq -r '[.[] | select(.title=="danteplanner-alerts")][0].uid // empty')
[ -n "$FOLDER_UID" ] || { echo "folder danteplanner-alerts not found"; exit 1; }

for c in oregon seoul; do
  resp=$(jq -n --arg ds grafanacloud-prom --arg folder "$FOLDER_UID" --arg c "$c" '
    { title:("staleness-meta-" + $c), ruleGroup:"cluster-rules", folderUID:$folder,
      condition:"C", for:"2m", noDataState:"OK", execErrState:"OK",
      data:[
        {refId:"A", relativeTimeRange:{from:600,to:0}, datasourceUid:$ds,
         model:{refId:"A", expr:("absent_over_time(up{cluster=\"" + $c + "\"}[10m])"), instant:true}},
        {refId:"B", relativeTimeRange:{from:0,to:0}, datasourceUid:"__expr__",
         model:{refId:"B", type:"reduce", reducer:"last", expression:"A",
                datasource:{type:"__expr__",uid:"__expr__"}}},
        {refId:"C", relativeTimeRange:{from:0,to:0}, datasourceUid:"__expr__",
         model:{refId:"C", type:"threshold", expression:"B",
                conditions:[{evaluator:{type:"gt",params:[0]}}],
                datasource:{type:"__expr__",uid:"__expr__"}}}]}' |
    curl -s -w '\n%{http_code}' "${auth[@]}" "${noprov[@]}" -X POST \
      "${GRAFANA_URL}/api/v1/provisioning/alert-rules" -d @-)
  code=${resp##*$'\n'}; body=${resp%$'\n'*}
  if [ "$code" = 201 ]; then
    printf '%s' "$body" | jq -r '"   created: " + .title + "  (uid " + .uid + ")"'
  else
    echo "   FAILED staleness-meta-${c} (HTTP ${code}): ${body}"; exit 1
  fi
done
echo "done — staleness detection is live (fires when a cluster stops remote-writing for 10m)"
