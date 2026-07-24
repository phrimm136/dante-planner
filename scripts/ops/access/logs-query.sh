#!/usr/bin/env bash
# Read app logs from Grafana Cloud Loki (the read half of alloy-logs.yaml).
# Usage: logs-query.sh '<logql>' [since] [limit]
#        logs-query.sh --day 2026-07-23 '<logql>' [limit]
#   since: relative window like 24h/2d (default 24h); limit default 500.
# Labels available: namespace, pod, app (backend), cluster (oregon|seoul).
# Examples:
#   logs-query.sh '{app="backend"} |= "ERROR"' 6h
#   logs-query.sh --day 2026-07-23 '{app="backend", cluster="seoul"} |= "RateLimit"'
# Auth: danteplanner/grafana/loki-username + observability-read-token (Secrets
# Manager); minted with logs:read + metrics:read via provision/grafana-read-secrets.sh.
set -euo pipefail

LOKI_HOST="https://logs-prod-036.grafana.net"

if [ "${1:-}" = "--day" ]; then
  DAY=${2:?usage: logs-query.sh --day YYYY-MM-DD '<logql>' [limit]}
  QUERY=${3:?logql query required}
  LIMIT=${4:-500}
  START=$(date -u -d "$DAY" +%s)000000000
  END=$(date -u -d "$DAY + 1 day" +%s)000000000
else
  QUERY=${1:?usage: logs-query.sh '<logql>' [since] [limit]}
  SINCE=${2:-24h}
  LIMIT=${3:-500}
  START=$(date -u -d "-$(echo "$SINCE" | sed -E 's/m$/ minutes/;s/h$/ hours/;s/d$/ days/')" +%s)000000000
  END=$(date -u +%s)000000000
fi

secret() {
  aws secretsmanager get-secret-value --region us-west-2 \
    --secret-id "danteplanner/grafana/$1" --query SecretString --output text
}

LK_USER=$(secret loki-username)
LK_TOKEN=$(secret observability-read-token)

curl -sS -G -K <(printf 'user = "%s:%s"\n' "$LK_USER" "$LK_TOKEN") "${LOKI_HOST}/loki/api/v1/query_range" \
  --data-urlencode "query=${QUERY}" \
  --data-urlencode "start=${START}" \
  --data-urlencode "end=${END}" \
  --data-urlencode "limit=${LIMIT}" \
  --data-urlencode "direction=backward" |
jq -r '.data.result[] as $s | $s.values[] | "\(.[0][0:19] | tonumber / 1000000000 | todate)  \($s.stream.cluster // "-")  \($s.stream.pod // "-")  \(.[1])"' |
sort
