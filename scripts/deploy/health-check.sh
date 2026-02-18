#!/bin/bash
set -euo pipefail
SCRIPT_NAME="$(basename "$0")"
trap 'echo "[ERROR] $SCRIPT_NAME failed at line $LINENO (exit code: $?)" >&2' ERR

MAX_ATTEMPTS=5
WAIT_TIME=5
TOTAL_WAIT=0
MAX_TOTAL_WAIT=120

for attempt in $(seq 1 $MAX_ATTEMPTS); do
  echo "Health check attempt $attempt (waited ${TOTAL_WAIT}s so far)..."

  if curl -sf http://localhost/health && curl -sf http://localhost/actuator/health; then
    echo "Health check passed on attempt $attempt"
    exit 0
  fi

  # Exponential backoff: 5s, 10s, 20s, 40s
  TOTAL_WAIT=$((TOTAL_WAIT + WAIT_TIME))
  if [ $TOTAL_WAIT -ge $MAX_TOTAL_WAIT ]; then
    echo "Exceeded max wait time of ${MAX_TOTAL_WAIT}s"
    break
  fi

  echo "Health check failed, waiting ${WAIT_TIME}s before retry..."
  sleep $WAIT_TIME
  WAIT_TIME=$((WAIT_TIME * 2))
done

echo "Health check failed after $MAX_ATTEMPTS attempts"
exit 1
