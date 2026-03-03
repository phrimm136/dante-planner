#!/bin/bash
# Export nginx and MySQL CloudWatch log groups to S3 for long-term retention.
# Exports the previous day's UTC logs — run daily at 04:00 KST (19:00 UTC).
#
# AWS allows only one concurrent export task per account; tasks run sequentially.
#
# Prerequisites:
# - S3 bucket policy grants logs.{region}.amazonaws.com s3:GetBucketAcl + s3:PutObject
# - EC2 role has logs:CreateExportTask + logs:DescribeExportTasks permissions

set -euo pipefail

export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-us-west-2}"

S3_BUCKET="${S3_LOGS_BUCKET:-danteplanner-logs}"
EXPORT_PREFIX="cloudwatch-exports"
WAIT_TIMEOUT=600   # seconds to wait per task before giving up
POLL_INTERVAL=30

LOG_GROUPS=(
    "/ecs/danteplanner/backend"
    "/ecs/danteplanner/nginx"
    "/ecs/danteplanner/mysql"
)

# Yesterday's date range in milliseconds (UTC)
YESTERDAY=$(date -u -d "yesterday" +%Y-%m-%d)
FROM_MS=$(date -u -d "${YESTERDAY} 00:00:00" +%s%3N)
TO_MS=$(date -u -d "${YESTERDAY} 23:59:59" +%s%3N)

echo "[$(date -u)] Exporting logs for ${YESTERDAY} → s3://${S3_BUCKET}/${EXPORT_PREFIX}/${YESTERDAY}/"

wait_for_export() {
    local TASK_ID="$1"
    local LOG_GROUP="$2"
    local ELAPSED=0

    while [ "$ELAPSED" -lt "$WAIT_TIMEOUT" ]; do
        STATUS=$(aws logs describe-export-tasks \
            --task-id "$TASK_ID" \
            --region "$AWS_DEFAULT_REGION" \
            --query 'exportTasks[0].status.code' \
            --output text)

        case "$STATUS" in
            COMPLETED)
                echo "[$(date -u)] Completed: ${LOG_GROUP} (task: ${TASK_ID})"
                return 0
                ;;
            FAILED|CANCELLED)
                echo "[$(date -u)] ERROR: Export ${STATUS} for ${LOG_GROUP} (task: ${TASK_ID})"
                return 1
                ;;
        esac

        sleep "$POLL_INTERVAL"
        ELAPSED=$((ELAPSED + POLL_INTERVAL))
    done

    echo "[$(date -u)] WARNING: Export timed out after ${WAIT_TIMEOUT}s — task still running: ${TASK_ID}"
    return 1
}

for LOG_GROUP in "${LOG_GROUPS[@]}"; do
    # Derive a clean S3 prefix from the log group name (strip leading slash)
    DEST_PREFIX="${EXPORT_PREFIX}/${YESTERDAY}${LOG_GROUP}"
    TASK_NAME="${LOG_GROUP##*/}-${YESTERDAY}"

    echo "[$(date -u)] Creating export task: ${LOG_GROUP} → s3://${S3_BUCKET}/${DEST_PREFIX}"

    TASK_ID=$(aws logs create-export-task \
        --task-name  "$TASK_NAME" \
        --log-group-name "$LOG_GROUP" \
        --from "$FROM_MS" \
        --to   "$TO_MS" \
        --destination "$S3_BUCKET" \
        --destination-prefix "$DEST_PREFIX" \
        --region "$AWS_DEFAULT_REGION" \
        --query 'taskId' \
        --output text)

    echo "[$(date -u)] Task created: ${TASK_ID}"
    wait_for_export "$TASK_ID" "$LOG_GROUP"
done

echo "[$(date -u)] CloudWatch log export complete"
