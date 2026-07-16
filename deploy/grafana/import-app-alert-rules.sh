#!/usr/bin/env bash
# Provision the app-level alert rules (group app-rules) — fleet-era successors to
# the legacy DantePlanner-* CloudWatch alarms that watched the retired single-EC2
# host. Thresholds carry over 1:1 from the legacy alarms; per-node values were
# tuned for a t3.medium, so retune if fleet nodes diverge from that profile.
# Billing is CW-only data: AWS/Billing lives exclusively in us-east-1, queried
# through the CloudWatch datasource; the legacy CW billing/auto-recovery alarms
# stay CW-native (EC2 recover is a CW-alarm-only action) per requirements.
#
# Usage:
#   GRAFANA_URL=https://<slug>.grafana.net \
#   GRAFANA_TOKEN=<service-account token, Editor role> \
#   DS_CW_UID=<CloudWatch datasource uid, from /connections/datasources/edit/<uid>> \
#   bash import-app-alert-rules.sh
set -euo pipefail

: "${GRAFANA_URL:?set GRAFANA_URL, e.g. https://yourslug.grafana.net (no trailing slash)}"
: "${GRAFANA_TOKEN:?set GRAFANA_TOKEN (service-account token with Editor role)}"
: "${DS_CW_UID:?set DS_CW_UID (CloudWatch datasource uid)}"
DS_UID="${DS_UID:-grafanacloud-prom}"

auth=(-H "Authorization: Bearer ${GRAFANA_TOKEN}" -H "Content-Type: application/json")
# X-Disable-Provenance keeps the rules editable in the UI afterwards; without it the
# provisioning API marks them provenance=api and locks them against UI edits.
noprov=(-H "X-Disable-Provenance: true")

FOLDER="${GRAFANA_ALERT_FOLDER:-danteplanner-alerts}"
FOLDER_UID=$(curl -s "${auth[@]}" "${GRAFANA_URL}/api/folders" |
  jq -r --arg t "$FOLDER" '[.[] | select(.title==$t)][0].uid // empty')
[ -n "$FOLDER_UID" ] || { echo "folder ${FOLDER} not found — run import-alert-rules.sh first"; exit 1; }
echo "   folder uid: ${FOLDER_UID}"

# post_rule TITLE FOR_DURATION PROMQL
post_rule() {
  local title=$1 for_dur=$2 expr=$3
  jq -n \
    --arg title "$title" --arg for "$for_dur" --arg expr "$expr" \
    --arg ds "$DS_UID" --arg folder "$FOLDER_UID" '
    {
      title: $title, ruleGroup: "app-rules", folderUID: $folder,
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

echo "== creating app rules (legacy CW parity)"

# BackendErrors: any ERROR paged immediately (Sum > 0 over 60s); keep first-occurrence semantics
post_rule "backend-error-events" "1m" \
  'sum by (cluster) (increase(logback_events_total{level="error"}[5m])) > 0'

# BackendWarnings: WARN > 0 sustained 5 evaluation periods
post_rule "backend-warn-sustained" "5m" \
  'sum by (cluster) (increase(logback_events_total{level="warn"}[5m])) > 0'

# BackendSilence: < 1 log event in 5m; kube rules own app-down, this owns pipeline-dead
post_rule "backend-log-silence" "5m" \
  'sum by (cluster) (increase(logback_events_total[10m])) < 1'

# HTTP5xx: > 10 per 5m
post_rule "http-5xx-burst" "0s" \
  'sum by (cluster) (increase(http_server_requests_seconds_count{status=~"5.."}[5m])) > 10'

# HTTP429: > 20 per 5m
post_rule "http-429-burst" "0s" \
  'sum by (cluster) (increase(http_server_requests_seconds_count{status="429"}[5m])) > 20'

# HighCPU: user-mode CPU avg > 70% over 5m
post_rule "node-high-cpu" "5m" \
  'avg by (cluster, instance) (rate(node_cpu_seconds_total{mode="user"}[5m])) * 100 > 70'

# HighDisk: filesystem used > 85%
post_rule "node-high-disk" "5m" \
  '(1 - node_filesystem_avail_bytes{fstype!~"tmpfs|overlay"} / node_filesystem_size_bytes{fstype!~"tmpfs|overlay"}) * 100 > 85'

# LowMemory: available < 200 MiB sustained 3 evaluation periods
post_rule "node-low-memory" "15m" \
  'node_memory_MemAvailable_bytes < 209715200'

# HighDiskIOWrite: > 500 MiB written per 5m
post_rule "node-high-disk-write" "0s" \
  'sum by (cluster, instance) (increase(node_disk_written_bytes_total[5m])) > 524288000'

# HighNetworkIn: > 50 MiB received per 5m
post_rule "node-high-network-in" "0s" \
  'sum by (cluster, instance) (increase(node_network_receive_bytes_total{device!="lo"}[5m])) > 52428800'

# HighNetworkOut: > 200 MiB sent per 5m
post_rule "node-high-network-out" "0s" \
  'sum by (cluster, instance) (increase(node_network_transmit_bytes_total{device!="lo"}[5m])) > 209715200'

# TrafficDrop: legacy alarm paged on RequestCount Sum < 1 per 5m for 2 periods —
# i.e. total silence at the nginx layer of the old host. The fleet-era signal is
# http_server_requests_seconds_count across two clusters behind Cloudflare.
# TODO(human): choose and write the traffic-drop expression
TRAFFIC_DROP_EXPR='sum by (cluster) (increase(http_server_requests_seconds_count[5m])) < 1'
post_rule "traffic-drop" "10m" "$TRAFFIC_DROP_EXPR"

echo "== creating billing rule (CW-only metric via CloudWatch datasource)"
# AWS/Billing EstimatedCharges exists only in us-east-1 and updates a few times a
# day, hence the wide time range and 6h period. Legacy threshold: $200.
jq -n --arg cw "$DS_CW_UID" --arg folder "$FOLDER_UID" '
  { title: "billing-estimated-charges", ruleGroup: "app-rules", folderUID: $folder,
    condition: "C", for: "0s", noDataState: "OK", execErrState: "OK",
    data: [
      { refId: "A", relativeTimeRange: {from: 86400, to: 0}, datasourceUid: $cw,
        model: {refId: "A", queryMode: "Metrics", namespace: "AWS/Billing",
                metricName: "EstimatedCharges", statistic: "Maximum",
                dimensions: {Currency: "USD"}, region: "us-east-1",
                period: "21600", matchExact: true,
                datasource: {type: "cloudwatch", uid: $cw}} },
      { refId: "B", relativeTimeRange: {from: 0, to: 0}, datasourceUid: "__expr__",
        model: {refId: "B", type: "reduce", reducer: "last", expression: "A",
                datasource: {type: "__expr__", uid: "__expr__"}} },
      { refId: "C", relativeTimeRange: {from: 0, to: 0}, datasourceUid: "__expr__",
        model: {refId: "C", type: "threshold", expression: "B",
                conditions: [{evaluator: {type: "gt", params: [200]}}],
                datasource: {type: "__expr__", uid: "__expr__"}} }
    ]
  }' |
curl -s -w '\n%{http_code}' "${auth[@]}" "${noprov[@]}" -X POST \
  "${GRAFANA_URL}/api/v1/provisioning/alert-rules" -d @- | {
  resp=$(cat); code=${resp##*$'\n'}; body=${resp%$'\n'*}
  if [ "$code" = 201 ]; then
    printf '%s' "$body" | jq -r '"   created: " + .title + "  (uid " + .uid + ")"'
  else
    echo "   FAILED billing-estimated-charges (HTTP ${code}): ${body}"; exit 1
  fi
}

echo "== done: 13 rules in folder ${FOLDER}, group app-rules"
echo "   All noDataState=OK — non-paging while a series is absent; staleness-meta owns absence."
