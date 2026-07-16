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
  jq -r '[.[] | select(.title=="${FOLDER}")][0].uid // empty')
if [ -z "$FOLDER_UID" ]; then
  created=$(curl -s -w '\n%{http_code}' "${auth[@]}" -X POST "${GRAFANA_URL}/api/folders" \
    -d '{"title":"${GRAFANA_ALERT_FOLDER:-danteplanner-alerts}"}')
  code=${created##*$'\n'}
  [ "$code" = 200 ] || { echo "   creating folder failed (HTTP ${code}): ${created%$'\n'*}"; exit 1; }
  FOLDER_UID=$(printf '%s' "${created%$'\n'*}" | jq -r .uid)
fi
echo "   folder uid: ${FOLDER_UID}"

# post_rule TITLE FOR_DURATION PROMQL
post_rule() {
  local title=$1 for_dur=$2 expr=$3
  jq -n \
    --arg title "$title" --arg for "$for_dur" --arg expr "$expr" \
    --arg ds "$DS_UID" --arg folder "$FOLDER_UID" '
    {
      title: $title, ruleGroup: "cluster-rules", folderUID: $folder,
      condition: "C", for: $for, noDataState: "OK", execErrState: "OK",
      data: [
        { refId: "A", relativeTimeRange: {from: 600, to: 0}, datasourceUid: $ds,
          model: {refId: "A", expr: $expr, instant: true} },
        { refId: "B", relativeTimeRange: {from: 0, to: 0}, datasourceUid: "__expr__",
          model: {refId: "B", type: "reduce", reducer: "last", expression: "A",
                  datasource: {type: "__expr__", uid: "__expr__"}} },
        { refId: "C", relativeTimeRange: {from: 0, to: 0}, datasourceUid: "__expr__",
          model: {refId: "C", type: "threshold", expression: "B",
                  conditions: [{evaluator: {type: "gt", params: [0]}}],
                  datasource: {type: "__expr__", uid: "__expr__"}} }
      ]
    }' |
  curl -s -w '\n%{http_code}' "${auth[@]}" "${noprov[@]}" -X POST \
    "${GRAFANA_URL}/api/v1/provisioning/alert-rules" -d @- | {
    resp=$(cat); code=${resp##*$'\n'}; body=${resp%$'\n'*}
    if [ "$code" = 201 ]; then
      printf '%s' "$body" | jq -r '"   created: " + .title + "  (uid " + .uid + ")"'
    else
      echo "   FAILED ${title} (HTTP ${code}): ${body}"; exit 1
    fi
  }
}

echo "== creating rules"
post_rule "node-not-ready" "15m" \
  'kube_node_status_condition{condition="Ready",status="true"} == 0'
post_rule "backend-daemonset-unready" "5m" \
  'kube_daemonset_status_number_ready{daemonset="backend"} == 0'
post_rule "container-waiting-backoff" "10m" \
  'kube_pod_container_status_waiting_reason{reason=~"CrashLoopBackOff|ImagePullBackOff"} == 1'
post_rule "argocd-app-drift" "30m" \
  'argocd_app_info{sync_status="OutOfSync"} == 1 or argocd_app_info{health_status="Degraded"} == 1'
post_rule "eso-secret-not-ready" "15m" \
  'externalsecret_status_condition{condition="Ready",status="False"} == 1'
post_rule "etcd-snapshot-deadman" "1m" \
  "(time() - kube_etcd_snapshot_creation_timestamp_seconds) > (1.5 * ${ETCD_INTERVAL})"

echo "== done: 6 rules in folder ${FOLDER}, group cluster-rules (1m interval)"
echo "   All noDataState=OK — non-paging while their series are absent. Staleness rules are created separately by create-staleness-rules.sh."
