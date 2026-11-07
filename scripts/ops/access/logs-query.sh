#!/usr/bin/env bash
# Read app logs from Grafana Cloud Loki (the read half of alloy-logs.yaml).
# Usage: logs-query.sh '<logql>' [since] [limit]
#        logs-query.sh --day 2026-07-23 '<logql>' [limit]
#   since: relative window like 24h/2d (default 24h); limit default 500.
# Labels available: namespace, pod, app (backend), cluster (oregon|seoul).
# Examples:
#   logs-query.sh '{app="backend"} |= "ERROR"' 6h
#   logs-query.sh --day 2026-07-23 '{app="backend", cluster="seoul"} |= "RateLimit"'
# Auth: loki_username + observability_read_token from the danteplanner/grafana/cloud
# bundle (Secrets Manager); minted with logs:read + metrics:read, written by
# provision/grafana-secrets.sh.
set -euo pipefail

LOKI_HOST="https://logs-prod-036.grafana.net"

if [[ "${1:-}" = "--day" ]]; then
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

OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/constants.sh
source "$OPS_DIR/../lib/constants.sh"
# shellcheck source=../lib/secrets.sh
source "$OPS_DIR/../lib/secrets.sh"
BUNDLE=$SECRET_GRAFANA_BUNDLE

CREDS=$(read_bundle us-west-2 "$BUNDLE")
LK_USER=$(bundle_key "$BUNDLE" "$CREDS" loki_username)
LK_TOKEN=$(bundle_key "$BUNDLE" "$CREDS" observability_read_token)

curl -sS -G -K <(printf 'user = "%s:%s"\n' "$LK_USER" "$LK_TOKEN") "${LOKI_HOST}/loki/api/v1/query_range" \
  --data-urlencode "query=${QUERY}" \
  --data-urlencode "start=${START}" \
  --data-urlencode "end=${END}" \
  --data-urlencode "limit=${LIMIT}" \
  --data-urlencode "direction=backward" |
jq -r '.data.result[] as $s | $s.values[]
       | (.[0] | tonumber / 1e9 | todate) as $ts
       | "\($ts)  \($s.stream.cluster // "-")  \($s.stream.pod // "-")  \(.[1])"' |
sort
