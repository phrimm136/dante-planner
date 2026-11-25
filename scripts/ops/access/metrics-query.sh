#!/usr/bin/env bash
# Read metrics from Grafana Cloud Mimir (the query side of deploy/chart/templates/prometheus.yaml).
# Usage: metrics-query.sh [--names] '<promql>' [since] [step]
#   since: relative window like 24h/2d (default 1h); step default 5m.
# Examples:
#   metrics-query.sh 'up'
#   metrics-query.sh 'rate(http_server_requests_seconds_count[5m])' 6h 1m
# Auth: remote_write_username (metrics tenant id) + observability_read_token from the
# danteplanner/grafana/cloud bundle (Secrets Manager), minted with metrics:read.
set -euo pipefail

MIMIR_HOST="https://prometheus-prod-56-prod-us-east-2.grafana.net"

NAMES=false
if [ "${1:-}" = --names ]; then
  NAMES=true
  shift
fi
QUERY=${1:?usage: metrics-query.sh [--names] '<promql>' [since] [step]}
SINCE=${2:-1h}
STEP=${3:-5m}
START=$(date -u -d "-$(echo "$SINCE" | sed -E 's/m$/ minutes/;s/h$/ hours/;s/d$/ days/')" +%s)
END=$(date -u +%s)

OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/constants.sh
source "$OPS_DIR/../lib/constants.sh"
# shellcheck source=../lib/secrets.sh
source "$OPS_DIR/../lib/secrets.sh"
BUNDLE=$SECRET_GRAFANA_BUNDLE

CREDS=$(read_bundle us-west-2 "$BUNDLE")
MM_USER=$(bundle_key "$BUNDLE" "$CREDS" remote_write_username)
MM_TOKEN=$(bundle_key "$BUNDLE" "$CREDS" observability_read_token)

curl -sS -G -K <(printf 'user = "%s:%s"\n' "$MM_USER" "$MM_TOKEN") \
  "${MIMIR_HOST}/api/prom/api/v1/query_range" \
  --data-urlencode "query=${QUERY}" \
  --data-urlencode "start=${START}" \
  --data-urlencode "end=${END}" \
  --data-urlencode "step=${STEP}" |
jq -r --argjson names "$NAMES" '.data.result[] | (if $names then (.metric.__name__ // "-") + "  " else "" end) as $name
  | (.metric | del(.__name__) | to_entries | map("\(.key)=\(.value)") | join(",")) as $labels
  | .values[] | "\(.[0] | todate)  \(.[1])  \($name)\($labels)"'
