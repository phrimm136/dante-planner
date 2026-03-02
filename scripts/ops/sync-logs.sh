#!/bin/bash
# Sync backend log files to S3 for analysis and retention
# Runs via cron: 0 * * * * (hourly)
#
# Prerequisites:
# - AWS CLI configured with S3 write permissions
# - .env file with S3_LOGS_BUCKET (or default danteplanner-logs)

set -euo pipefail

export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-us-west-2}"

S3_BUCKET="${S3_LOGS_BUCKET:-danteplanner-logs}"
LOGS_DIR="${BACKEND_LOGS_PATH:-/home/ec2-user/logs/backend}"

if [ ! -d "$LOGS_DIR" ]; then
    echo "[$(date)] No log directory at $LOGS_DIR — skipping"
    exit 0
fi

echo "[$(date)] Syncing $LOGS_DIR to s3://$S3_BUCKET/backend/"

aws s3 sync "$LOGS_DIR/" "s3://$S3_BUCKET/backend/" \
    --exclude "*.tmp" \
    --no-progress

echo "[$(date)] Sync complete"
