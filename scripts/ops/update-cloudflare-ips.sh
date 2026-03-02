#!/bin/bash
# Update Cloudflare IP ranges for security group or application config
# Run via cron: 0 3 * * * /opt/danteplanner/scripts/ops/update-cloudflare-ips.sh
#
# This script fetches current Cloudflare IP ranges and can:
# 1. Update AWS Security Group (if SG_ID is set)
# 2. Output for manual review

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="/var/log/cloudflare-ip-update.log"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

# Fetch current Cloudflare IPs
CF_IPV4_URL="https://www.cloudflare.com/ips-v4"
CF_IPV6_URL="https://www.cloudflare.com/ips-v6"

log "Fetching Cloudflare IP ranges..."

CF_IPV4=$(curl -sf "$CF_IPV4_URL" || echo "")
CF_IPV6=$(curl -sf "$CF_IPV6_URL" || echo "")

if [ -z "$CF_IPV4" ]; then
    log "ERROR: Failed to fetch Cloudflare IPv4 ranges"
    exit 1
fi

# Save to file for reference
CACHE_DIR="/tmp/cloudflare-ips"
mkdir -p "$CACHE_DIR"
echo "$CF_IPV4" > "$CACHE_DIR/ipv4.txt"
echo "$CF_IPV6" > "$CACHE_DIR/ipv6.txt"

# Count IPs
IPV4_COUNT=$(echo "$CF_IPV4" | wc -l)
IPV6_COUNT=$(echo "$CF_IPV6" | wc -l)
log "Found $IPV4_COUNT IPv4 and $IPV6_COUNT IPv6 ranges"

# Check if IPs changed from last run
LAST_HASH_FILE="$CACHE_DIR/last_hash.txt"
CURRENT_HASH=$(echo "$CF_IPV4$CF_IPV6" | md5sum | cut -d' ' -f1)

if [ -f "$LAST_HASH_FILE" ]; then
    LAST_HASH=$(cat "$LAST_HASH_FILE")
    if [ "$CURRENT_HASH" = "$LAST_HASH" ]; then
        log "No changes detected in Cloudflare IPs"
        exit 0
    fi
fi

log "Cloudflare IPs have changed!"
echo "$CURRENT_HASH" > "$LAST_HASH_FILE"

# Option 1: Update AWS Security Group (if configured)
SG_ID="${CLOUDFLARE_SG_ID:-}"
if [ -n "$SG_ID" ]; then
    log "Updating Security Group: $SG_ID"

    # Remove old Cloudflare rules (tagged with description "Cloudflare")
    OLD_RULES=$(aws ec2 describe-security-groups --group-ids "$SG_ID" \
        --query "SecurityGroups[0].IpPermissions[?contains(IpRanges[].Description, 'Cloudflare')]" \
        --output json 2>/dev/null || echo "[]")

    if [ "$OLD_RULES" != "[]" ] && [ -n "$OLD_RULES" ]; then
        log "Removing old Cloudflare rules..."
        aws ec2 revoke-security-group-ingress --group-id "$SG_ID" --ip-permissions "$OLD_RULES" || true
    fi

    # Add new rules for port 443
    log "Adding new Cloudflare rules for HTTPS..."
    for IP in $CF_IPV4; do
        aws ec2 authorize-security-group-ingress --group-id "$SG_ID" \
            --protocol tcp --port 443 --cidr "$IP" \
            --description "Cloudflare IPv4" 2>/dev/null || true
    done

    log "Security Group updated successfully"
fi

# Option 2: Output summary for manual review
log "=== Current Cloudflare IPv4 Ranges ==="
echo "$CF_IPV4" | tee -a "$LOG_FILE"

log "=== Current Cloudflare IPv6 Ranges ==="
echo "$CF_IPV6" | tee -a "$LOG_FILE"

# Send notification if configured
SLACK_WEBHOOK="${SLACK_WEBHOOK_URL:-}"
if [ -n "$SLACK_WEBHOOK" ]; then
    curl -sf -X POST -H 'Content-type: application/json' \
        --data "{\"text\":\"Cloudflare IPs updated: $IPV4_COUNT IPv4, $IPV6_COUNT IPv6 ranges\"}" \
        "$SLACK_WEBHOOK" || true
fi

log "Update complete"
