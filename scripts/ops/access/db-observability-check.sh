#!/usr/bin/env bash
# Check the DB observability path end to end: every DB metric family the fleet
# dashboard reads is flowing per cluster, connectivity (mysql_up) is 1, and the
# DB-related Grafana alert rules exist and evaluate healthily.
# Usage: AWS_PROFILE=prod db-observability-check.sh
#   Metrics: same Secrets Manager auth as metrics-query.sh (called as a sibling).
#   Alert rules: GRAFANA_URL + GRAFANA_TOKEN env, as for import-alert-rules.sh;
#   skipped with a WARN when unset.
# Exit: 0 all OK, 1 any FAIL, 2 WARNs only.
set -euo pipefail

DIR=$(dirname "$0")
CLUSTERS=(oregon seoul)

# Exported by mysqld_exporter (deploy/base/mysqld-exporter.yaml); the set the
# fleet dashboard's DB panels query.
EXPORTER_METRICS=(
  mysql_up
  mysql_global_status_created_tmp_disk_tables
  mysql_global_status_created_tmp_tables
  mysql_global_status_handlers_total
  mysql_global_status_select_full_join
  mysql_global_status_select_scan
  mysql_global_status_slow_queries
  mysql_global_status_sort_merge_passes
)
# Exported by the backend (read-your-writes GTID gate panels).
APP_METRICS=(
  gtid_capture_total
  gtid_gate_total
)
DB_ALERT_RULES=(mysql-connectivity-lost staleness-meta-oregon staleness-meta-seoul)

fails=0 warns=0
report() { # status check detail
  printf '%-5s %-50s %s\n' "$1" "$2" "$3"
  case $1 in FAIL) fails=$((fails + 1)) ;; WARN) warns=$((warns + 1)) ;; esac
}

# mysql_* families come from the exporter and vanish together only when the
# pipeline is broken; gtid_* counters appear once traffic exercises the
# read-your-writes path, so their absence is tolerable.
classify_missing_metric() {
  case $1 in
    mysql_*) echo FAIL ;;
    *) echo WARN ;;
  esac
}

present_clusters() { # metric -> "cluster=<name>" lines for clusters with recent data
  "$DIR/metrics-query.sh" "count by (cluster) (last_over_time(${1}[15m]))" 15m 15m |
    grep -o 'cluster=[a-z-]*' | sort -u
}

echo "== metric families (last 15m)"
for m in "${EXPORTER_METRICS[@]}" "${APP_METRICS[@]}"; do
  have=$(present_clusters "$m" || true)
  for c in "${CLUSTERS[@]}"; do
    if grep -q "cluster=${c}$" <<<"$have"; then
      report OK "$m" "cluster=${c}"
    else
      report "$(classify_missing_metric "$m" "$c")" "$m" "absent in cluster=${c}"
    fi
  done
done

echo "== connectivity"
up=$("$DIR/metrics-query.sh" 'min by (cluster) (mysql_up)' 5m 5m || true)
for c in "${CLUSTERS[@]}"; do
  v=$(awk -v want="cluster=${c}" '$3 == want { last = $2 } END { print last }' <<<"$up")
  if [ "${v:-}" = 1 ]; then
    report OK "mysql_up" "cluster=${c}"
  else
    report FAIL "mysql_up" "cluster=${c} value=${v:-absent}"
  fi
done

echo "== alert rules"
if [ -z "${GRAFANA_URL:-}" ] || [ -z "${GRAFANA_TOKEN:-}" ]; then
  report WARN "alert-rules" "GRAFANA_URL/GRAFANA_TOKEN unset — skipped"
else
  auth=(-H "Authorization: Bearer ${GRAFANA_TOKEN}")
  provisioned=$(curl -sS "${auth[@]}" "${GRAFANA_URL}/api/v1/provisioning/alert-rules")
  states=$(curl -sS "${auth[@]}" "${GRAFANA_URL}/api/prometheus/grafana/api/v1/rules")
  for r in "${DB_ALERT_RULES[@]}"; do
    if ! jq -e --arg t "$r" 'map(select(.title == $t)) | length > 0' <<<"$provisioned" >/dev/null; then
      report FAIL "rule ${r}" "not provisioned"
      continue
    fi
    health=$(jq -r --arg t "$r" '[.data.groups[].rules[] | select(.name == $t)][0].health // "unknown"' <<<"$states")
    state=$(jq -r --arg t "$r" '[.data.groups[].rules[] | select(.name == $t)][0].state // "unknown"' <<<"$states")
    if [ "$health" != ok ]; then
      report FAIL "rule ${r}" "health=${health}"
    elif [ "$state" = firing ]; then
      report FAIL "rule ${r}" "firing"
    else
      report OK "rule ${r}" "health=ok state=${state}"
    fi
  done
fi

echo
echo "result: ${fails} fail, ${warns} warn"
[ "$fails" -eq 0 ] || exit 1
[ "$warns" -eq 0 ] || exit 2
