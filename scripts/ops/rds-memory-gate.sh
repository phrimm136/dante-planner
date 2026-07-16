#!/usr/bin/env bash
# RDS FreeableMemory gate — snapshot before, compare after.
# Usage: MIN_FREEABLE_MB=<your floor> c2-memory-gate.sh before|after <region> <db-instance-id>
#   before: record the 5-min average FreeableMemory for the instance
#   after : re-measure, print the delta, and verdict against YOUR floor
# The floor (MIN_FREEABLE_MB) is deliberately not defaulted: how much headroom the
# 1GiB t4g.micro must keep is your operational call (spec pins no number).
set -euo pipefail
MODE=${1:?usage: c2-memory-gate.sh before|after <region> <db-instance-id>}
REGION=${2:?region required (us-west-2 | ap-northeast-2)}
DB=${3:?db instance identifier required}
STATE="/tmp/rds-memory-gate-${DB}.before"

measure() {
  aws cloudwatch get-metric-statistics --region "$REGION" \
    --namespace AWS/RDS --metric-name FreeableMemory \
    --dimensions Name=DBInstanceIdentifier,Value="$DB" \
    --statistics Average --period 300 \
    --start-time "$(date -u -d '10 minutes ago' +%Y-%m-%dT%H:%M:%SZ)" \
    --end-time   "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --query 'sort_by(Datapoints,&Timestamp)[-1].Average' --output text
}

bytes=$(measure)
[ "$bytes" != "None" ] || { echo "no datapoint yet — wait 5 min and retry"; exit 1; }
mb=$(printf '%.0f' "$(echo "$bytes / 1048576" | bc -l)")

case "$MODE" in
  before)
    echo "$mb" >"$STATE"
    echo "recorded: ${DB} FreeableMemory ${mb} MB -> ${STATE}"
    ;;
  after)
    : "${MIN_FREEABLE_MB:?set MIN_FREEABLE_MB=<floor in MB> — the go/no-go line is yours}"
    [ -f "$STATE" ] || { echo "no before-file at ${STATE}; run 'before' first"; exit 1; }
    before=$(cat "$STATE")
    echo "${DB}: before=${before} MB  after=${mb} MB  drop=$((before - mb)) MB  floor=${MIN_FREEABLE_MB} MB"
    if [ "$mb" -lt "$MIN_FREEABLE_MB" ]; then
      echo "VERDICT: FAIL — below your floor. Replica FAIL => scale exporter to primary-only"
      echo "(record the replica blind spot); primary FAIL => escalate before leaving it running."
      exit 1
    fi
    echo "VERDICT: PASS — perf_schema cost fits."
    ;;
  *) echo "mode must be before|after"; exit 2;;
esac
