#!/usr/bin/env bash
#  through-policy drill — creates a scratch always-firing rule via the
# provisioning API, lets ONE firing route through the Default policy to
# alert-dual-channel (Discord AND Slack), then deletes the rule.
# Usage: GRAFANA_URL=https://<slug>.grafana.net GRAFANA_TOKEN=<editor token> g-drill-inv6.sh
set -euo pipefail
: "${GRAFANA_URL:?set GRAFANA_URL (no trailing slash)}"
: "${GRAFANA_TOKEN:?set GRAFANA_TOKEN}"
auth=(-H "Authorization: Bearer ${GRAFANA_TOKEN}" -H "Content-Type: application/json")
noprov=(-H "X-Disable-Provenance: true")

FOLDER_UID=$(curl -s "${auth[@]}" "${GRAFANA_URL}/api/folders" |
  jq -r '[.[] | select(.title=="danteplanner-alerts")][0].uid // empty')
[ -n "$FOLDER_UID" ] || { echo "folder danteplanner-alerts not found — run import-alert-rules.sh first"; exit 1; }

echo "== creating scratch rule inv6-drill (fires immediately; vector(1) needs no stored data)"
resp=$(jq -n --arg ds grafanacloud-prom --arg folder "$FOLDER_UID" '
  { title:"inv6-drill", ruleGroup:"cluster-rules", folderUID:$folder,
    condition:"C", for:"0s", noDataState:"OK", execErrState:"OK",
    data:[
      {refId:"A", relativeTimeRange:{from:600,to:0}, datasourceUid:$ds,
       model:{refId:"A", expr:"vector(1)", instant:true}},
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
[ "$code" = 201 ] || { echo "create failed (HTTP $code): $body"; exit 1; }
UID=$(printf '%s' "$body" | jq -r .uid)
echo "   created uid $UID — the group evaluates every ~1m; the firing then routes through"
echo "   the Default policy. WATCH BOTH channels now (Discord #alerts AND Slack)."
echo
read -rp "Did the SAME 'inv6-drill' firing arrive in BOTH Discord and Slack? [y/n] " seen

echo "== deleting scratch rule"
del=$(curl -s -o /dev/null -w '%{http_code}' "${auth[@]}" "${noprov[@]}" -X DELETE \
  "${GRAFANA_URL}/api/v1/provisioning/alert-rules/${UID}")
echo "   delete HTTP ${del} (204 = gone)"

if [ "$seen" = y ]; then
  echo "DRILL PASS — record: one firing, both channels, via Default policy -> alert-dual-channel."
  echo "This proves the through-policy delivery (a per-rule firing proof"
  echo "can ride a real incident or a later manual drill)."
else
  echo "DRILL FAIL — check: Notification policies Default -> alert-dual-channel (no nested routes),"
  echo "then contact-point Test to isolate webhook vs routing. Re-run this script after fixing."
  exit 1
fi
