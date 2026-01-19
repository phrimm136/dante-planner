#!/bin/bash
# MySQL Backup Script for DantePlanner
# Runs via cron: 0 17 * * * (17:00 UTC = 02:00 KST)
#
# Prerequisites:
# - AWS CLI configured with S3 write permissions
# - .env file with MYSQL_* variables

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BACKUP_DIR="/tmp/danteplanner-backups"
S3_BUCKET="${S3_BACKUP_BUCKET:-danteplanner-backups}"
RETENTION_DAYS=7

# Load environment variables
if [ -f "$PROJECT_DIR/.env" ]; then
    export $(grep -v '^#' "$PROJECT_DIR/.env" | xargs)
fi

# Validate required variables
: "${MYSQL_DATABASE:?MYSQL_DATABASE not set}"
: "${MYSQL_USER:?MYSQL_USER not set}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD not set}"

# Create backup directory
mkdir -p "$BACKUP_DIR"

# Generate timestamp
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/${MYSQL_DATABASE}_${TIMESTAMP}.sql.gz"

echo "[$(date)] Starting backup of $MYSQL_DATABASE..."

# Dump database (via Docker container)
docker exec danteplanner-mysql mysqldump \
    -u "$MYSQL_USER" \
    -p"$MYSQL_PASSWORD" \
    --single-transaction \
    --no-tablespaces \
    --routines \
    --triggers \
    "$MYSQL_DATABASE" | gzip > "$BACKUP_FILE"

# Verify backup was created
if [ ! -s "$BACKUP_FILE" ]; then
    echo "[$(date)] ERROR: Backup file is empty or not created"
    exit 1
fi

BACKUP_SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
echo "[$(date)] Backup created: $BACKUP_FILE ($BACKUP_SIZE)"

# Upload to S3
echo "[$(date)] Uploading to S3..."
aws s3 cp "$BACKUP_FILE" "s3://$S3_BUCKET/mysql-backups/$(basename "$BACKUP_FILE")"

if [ $? -eq 0 ]; then
    echo "[$(date)] Upload successful"
else
    echo "[$(date)] ERROR: S3 upload failed"
    exit 1
fi

# Clean up local backup
rm -f "$BACKUP_FILE"

# Clean up old S3 backups (older than RETENTION_DAYS)
echo "[$(date)] Cleaning up backups older than $RETENTION_DAYS days..."
aws s3 ls "s3://$S3_BUCKET/mysql-backups/" | while read -r line; do
    file_date=$(echo "$line" | awk '{print $1}')
    file_name=$(echo "$line" | awk '{print $4}')
    if [ -n "$file_date" ] && [ -n "$file_name" ]; then
        file_age=$(( ($(date +%s) - $(date -d "$file_date" +%s)) / 86400 ))
        if [ "$file_age" -gt "$RETENTION_DAYS" ]; then
            echo "Deleting old backup: $file_name (${file_age} days old)"
            aws s3 rm "s3://$S3_BUCKET/mysql-backups/$file_name"
        fi
    fi
done

echo "[$(date)] Backup complete"
