#!/bin/bash
set -euo pipefail
SCRIPT_NAME="$(basename "$0")"
trap 'echo "[ERROR] $SCRIPT_NAME failed at line $LINENO (exit code: $?)" >&2; exit 40' ERR

DEPLOY_DIR="/opt/danteplanner"
cd "$DEPLOY_DIR"

chmod +x scripts/aws-backup.sh scripts/update-cloudflare-ips.sh

# Create log files with proper ownership BEFORE registering crontab
sudo touch /var/log/danteplanner-backup.log /var/log/cloudflare-ip-update.log
sudo chown ec2-user:ec2-user /var/log/danteplanner-backup.log /var/log/cloudflare-ip-update.log

# Backup: 02:00 KST daily (17:00 UTC)
BACKUP_CRON="0 17 * * * /opt/danteplanner/scripts/aws-backup.sh >> /var/log/danteplanner-backup.log 2>&1"
# Cloudflare IP update: 03:00 KST daily (18:00 UTC)
CF_IP_CRON="0 18 * * * /opt/danteplanner/scripts/update-cloudflare-ips.sh >> /var/log/cloudflare-ip-update.log 2>&1"

# Idempotent crontab update: remove old entries, add fresh ones
(crontab -l 2>/dev/null | grep -v "aws-backup.sh" | grep -v "update-cloudflare-ips.sh"; echo "$BACKUP_CRON"; echo "$CF_IP_CRON") | crontab -

echo "Cron setup complete"
