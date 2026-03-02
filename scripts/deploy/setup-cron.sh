#!/bin/bash
set -euo pipefail
SCRIPT_NAME="$(basename "$0")"
trap 'echo "[ERROR] $SCRIPT_NAME failed at line $LINENO (exit code: $?)" >&2; exit 40' ERR

DEPLOY_DIR="/opt/danteplanner"
cd "$DEPLOY_DIR"

chmod +x scripts/ops/aws-backup.sh scripts/ops/update-cloudflare-ips.sh scripts/ops/sync-logs.sh

# Create log files with proper ownership BEFORE registering crontab
sudo touch /var/log/danteplanner-backup.log /var/log/cloudflare-ip-update.log /var/log/danteplanner-sync-logs.log
sudo chown ec2-user:ec2-user /var/log/danteplanner-backup.log /var/log/cloudflare-ip-update.log /var/log/danteplanner-sync-logs.log

# Backup: 02:00 KST daily (17:00 UTC)
BACKUP_CRON="0 17 * * * /opt/danteplanner/scripts/ops/aws-backup.sh >> /var/log/danteplanner-backup.log 2>&1"
# Cloudflare IP update: 03:00 KST daily (18:00 UTC)
CF_IP_CRON="0 18 * * * /opt/danteplanner/scripts/ops/update-cloudflare-ips.sh >> /var/log/cloudflare-ip-update.log 2>&1"
# Log sync to S3: hourly
SYNC_LOGS_CRON="0 * * * * /opt/danteplanner/scripts/ops/sync-logs.sh >> /var/log/danteplanner-sync-logs.log 2>&1"

# Idempotent crontab update: remove old entries, add fresh ones
# grep -v exits 1 when it outputs nothing (empty crontab or all lines matched),
# which triggers pipefail. || true absorbs that without swallowing real failures.
{ crontab -l 2>/dev/null | grep -v -e "aws-backup.sh" -e "update-cloudflare-ips.sh" -e "sync-logs.sh" || true; echo "$BACKUP_CRON"; echo "$CF_IP_CRON"; echo "$SYNC_LOGS_CRON"; } | crontab -

echo "Cron setup complete"
