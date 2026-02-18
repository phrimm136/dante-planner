#!/bin/bash
set -euo pipefail
SCRIPT_NAME="$(basename "$0")"
trap 'echo "[ERROR] $SCRIPT_NAME failed at line $LINENO (exit code: $?)" >&2; exit 50' ERR

BASHRC_FILE="/home/ec2-user/.bashrc"

# Remove old danteplanner block if exists
sed -i '/# >>> danteplanner aliases >>>/,/# <<< danteplanner aliases <<</d' "$BASHRC_FILE" 2>/dev/null || true

# Add new block
{
  echo ''
  echo '# >>> danteplanner aliases >>>'
  echo "alias dp-logs='cd /opt/danteplanner && docker compose logs -f'"
  echo "alias dp-status='cd /opt/danteplanner && docker compose ps'"
  echo "alias dp-restart='cd /opt/danteplanner && docker compose restart'"
  echo "alias dp-backup='/opt/danteplanner/scripts/aws-backup.sh'"
  echo "alias dp-cfips='/opt/danteplanner/scripts/update-cloudflare-ips.sh'"
  echo "alias dp-deploy='cd /opt/danteplanner && git pull && docker compose pull && docker compose up -d'"
  echo ''
  echo '# Welcome message'
  echo 'echo ""'
  echo 'echo "=========================================="'
  echo 'echo "  DantePlanner EC2 Server"'
  echo 'echo "=========================================="'
  echo 'echo ""'
  echo 'echo "Available commands:"'
  echo 'echo "  dp-logs     - Follow container logs"'
  echo 'echo "  dp-status   - Show container status"'
  echo 'echo "  dp-restart  - Restart all containers"'
  echo 'echo "  dp-backup   - Run database backup to S3"'
  echo 'echo "  dp-cfips    - Update Cloudflare IP ranges"'
  echo 'echo "  dp-deploy   - Manual deploy (git pull + docker up)"'
  echo 'echo ""'
  echo 'echo "Logs:"'
  echo 'echo "  /var/log/danteplanner-backup.log"'
  echo 'echo "  /var/log/cloudflare-ip-update.log"'
  echo 'echo ""'
  echo '# <<< danteplanner aliases <<<'
} >> "$BASHRC_FILE"

echo "Shell setup complete"
