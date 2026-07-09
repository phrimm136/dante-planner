#!/bin/bash
# MySQL execution telemetry emitter for DantePlanner
# Runs via cron: */5 * * * * (see scripts/deploy/setup-cron.sh)
#
# Publishes to the DantePlanner namespace, one batched PutMetricData call:
# - mysql_query_latency_avg_ms / mysql_query_lock_avg_ms  (per statement digest)
# - mysql_innodb_deadlocks / lock_timeouts / row_lock_waits (cumulative — chart with RATE(),
#   counters reset to zero on mysql restart, which shows as a one-sample negative blip)
# - mysql_blocked_sessions / active_transactions / longest_transaction_s / threads_running
#   / history_list_length (point-in-time gauges)
# When sessions are blocked, the blocking pairs are printed to stdout, which the
# CloudWatch agent ships to /danteplanner/query-stats for Insights forensics.
#
# Prerequisites:
# - AWS CLI configured with cloudwatch:PutMetricData
# - .env file with MYSQL_DATABASE
# - performance_schema requires root; the password is expanded inside the mysql
#   container from its own environment (the host .env does not carry it)

set -euo pipefail

# Cron runs with minimal PATH - ensure docker and aws are found
export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-us-west-2}"

PROJECT_DIR="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"
NAMESPACE="DantePlanner"

# Load environment variables
if [ -f "$PROJECT_DIR/.env" ]; then
    export $(grep -v '^#' "$PROJECT_DIR/.env" | xargs)
fi

: "${MYSQL_DATABASE:?MYSQL_DATABASE not set}"

run_mysql() {
    docker exec -i danteplanner-mysql \
        sh -c 'exec mysql -u root -p"$MYSQL_ROOT_PASSWORD" -N -B "$@"' mysql-client "$@"
}

# ── Statement layer: per-digest latency + lock time ─────────────────────────
# Timer columns are PICOSECONDS (1 ms = 1e9 ps). Selection is by SUM_TIMER_WAIT
# (total pain), display value is the per-call average. The #digest suffix keeps
# distinct digests with a shared 48-char prefix from merging into one CW series.
# Counters are cumulative since server start (= since last deploy).
DIGEST_SQL=$(cat <<SQL
SELECT CONCAT(LEFT(REPLACE(DIGEST_TEXT, '"', ''), 48), ' #', LEFT(DIGEST, 8)) AS label,
       ROUND(AVG_TIMER_WAIT / 1e9, 3) AS avg_ms,
       ROUND(SUM_LOCK_TIME / COUNT_STAR / 1e9, 3) AS lock_ms
FROM events_statements_summary_by_digest
WHERE SCHEMA_NAME = '${MYSQL_DATABASE}'
  AND COUNT_STAR >= 10
  AND DIGEST_TEXT NOT REGEXP '^(COMMIT|SET |SHOW |BEGIN)'
ORDER BY SUM_TIMER_WAIT DESC
LIMIT 12
SQL
)

# ── Contention + transaction layers: name, value, CW unit per row ───────────
GLOBAL_SQL=$(cat <<'SQL'
SELECT 'mysql_innodb_deadlocks', m.COUNT, 'Count'
  FROM information_schema.INNODB_METRICS m WHERE m.NAME = 'lock_deadlocks'
UNION ALL
SELECT 'mysql_innodb_lock_timeouts', m.COUNT, 'Count'
  FROM information_schema.INNODB_METRICS m WHERE m.NAME = 'lock_timeouts'
UNION ALL
SELECT 'mysql_history_list_length', m.COUNT, 'Count'
  FROM information_schema.INNODB_METRICS m WHERE m.NAME = 'trx_rseg_history_len'
UNION ALL
SELECT 'mysql_innodb_row_lock_waits', gs.VARIABLE_VALUE, 'Count'
  FROM performance_schema.global_status gs WHERE gs.VARIABLE_NAME = 'Innodb_row_lock_waits'
UNION ALL
SELECT 'mysql_innodb_row_lock_time_avg_ms', gs.VARIABLE_VALUE, 'Milliseconds'
  FROM performance_schema.global_status gs WHERE gs.VARIABLE_NAME = 'Innodb_row_lock_time_avg'
UNION ALL
SELECT 'mysql_threads_running', gs.VARIABLE_VALUE, 'Count'
  FROM performance_schema.global_status gs WHERE gs.VARIABLE_NAME = 'Threads_running'
UNION ALL
SELECT 'mysql_active_transactions', COUNT(*), 'Count'
  FROM information_schema.INNODB_TRX
UNION ALL
SELECT 'mysql_longest_transaction_s', IFNULL(MAX(TIMESTAMPDIFF(SECOND, trx_started, NOW())), 0), 'Seconds'
  FROM information_schema.INNODB_TRX
UNION ALL
SELECT 'mysql_blocked_sessions', COUNT(*), 'Count'
  FROM performance_schema.data_lock_waits
SQL
)

BLOCKED_DETAIL_SQL=$(cat <<'SQL'
SELECT wait_age_secs, waiting_pid, LEFT(waiting_query, 120),
       blocking_pid, LEFT(blocking_query, 120)
FROM sys.innodb_lock_waits
ORDER BY wait_age_secs DESC
LIMIT 10
SQL
)

DIGEST_ROWS="$(run_mysql performance_schema -e "$DIGEST_SQL")"
GLOBAL_ROWS="$(run_mysql performance_schema -e "$GLOBAL_SQL")"

# ── Build one PutMetricData payload (limit 1000 entries — we use ~35) ────────
METRICS=()
add_metric() {  # name value unit [dimensions-json]
    local ENTRY="{\"MetricName\":\"$1\",\"Value\":$2,\"Unit\":\"$3\""
    [ -n "${4:-}" ] && ENTRY+=",\"Dimensions\":[$4]"
    METRICS+=("${ENTRY}}")
}

DIGESTS=0
while IFS=$'\t' read -r LABEL AVG_MS LOCK_MS; do
    [ -z "$LABEL" ] && continue
    DIM="{\"Name\":\"digest\",\"Value\":\"$LABEL\"}"
    add_metric mysql_query_latency_avg_ms "$AVG_MS" Milliseconds "$DIM"
    add_metric mysql_query_lock_avg_ms "$LOCK_MS" Milliseconds "$DIM"
    DIGESTS=$((DIGESTS + 1))
done <<< "$DIGEST_ROWS"

BLOCKED=0
while IFS=$'\t' read -r NAME VALUE UNIT; do
    [ -z "$NAME" ] && continue
    add_metric "$NAME" "$VALUE" "$UNIT"
    if [ "$NAME" = "mysql_blocked_sessions" ]; then BLOCKED="$VALUE"; fi
done <<< "$GLOBAL_ROWS"

# Heartbeat: distinguishes "no digests matched" from "cron stopped running"
add_metric QueryStatsHeartbeat "$DIGESTS" Count "{\"Name\":\"Job\",\"Value\":\"QueryStats\"}"

PAYLOAD="$(mktemp)"
trap 'rm -f "$PAYLOAD"' EXIT
(IFS=','; printf '[%s]' "${METRICS[*]}") > "$PAYLOAD"

aws cloudwatch put-metric-data \
    --namespace "$NAMESPACE" \
    --metric-data "file://$PAYLOAD"

# Forensic detail only when contention is live — count is the metric, pairs are the log
if [ "$BLOCKED" -gt 0 ] 2>/dev/null; then
    echo "[$(date)] $BLOCKED blocked session(s) — blocking pairs (age_s, waiting_pid, waiting_query, blocking_pid, blocking_query):"
    run_mysql sys -e "$BLOCKED_DETAIL_SQL"
fi

echo "[$(date)] Emitted ${#METRICS[@]} metrics ($DIGESTS digests)"
