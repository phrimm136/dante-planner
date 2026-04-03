#!/bin/bash
# Install and configure the CloudWatch Agent on Amazon Linux 2.
# Idempotent — safe to run multiple times.
set -euo pipefail
SCRIPT_NAME="$(basename "$0")"
trap 'echo "[ERROR] $SCRIPT_NAME failed at line $LINENO (exit code: $?)" >&2; exit 10' ERR

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
AGENT_CTL="/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl"
CONFIG_SOURCE="${SCRIPT_DIR}/cloudwatch-agent-config.json"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

install_agent() {
    if [ -x "$AGENT_CTL" ]; then
        log_info "CloudWatch Agent already installed, skipping download"
        return 0
    fi

    log_info "Downloading CloudWatch Agent RPM..."
    local RPM_URL="https://s3.amazonaws.com/amazoncloudwatch-agent/amazon_linux/amd64/latest/amazon-cloudwatch-agent.rpm"
    local RPM_PATH="/tmp/amazon-cloudwatch-agent.rpm"

    curl -fsSL -o "$RPM_PATH" "$RPM_URL"
    sudo rpm -U "$RPM_PATH"
    rm -f "$RPM_PATH"

    if [ ! -x "$AGENT_CTL" ]; then
        log_error "Installation failed: $AGENT_CTL not found"
        exit 1
    fi

    log_info "CloudWatch Agent installed"
}

configure_and_start() {
    if [ ! -f "$CONFIG_SOURCE" ]; then
        log_error "Config not found: $CONFIG_SOURCE"
        exit 1
    fi

    log_info "Applying config from $CONFIG_SOURCE"
    $AGENT_CTL -a fetch-config -m ec2 -c "file:$CONFIG_SOURCE" -s

    # Verify agent is running
    local STATUS
    STATUS=$($AGENT_CTL -a status -m ec2 2>&1 || true)
    if echo "$STATUS" | grep -q '"status": "running"'; then
        log_info "CloudWatch Agent is running"
    else
        log_error "Agent failed to start. Status:"
        echo "$STATUS"
        exit 1
    fi
}

main() {
    log_info "CloudWatch Agent setup starting"
    install_agent
    configure_and_start
    log_info "CloudWatch Agent setup complete"
}

main "$@"
