#!/bin/bash
# Install and configure the CloudWatch Agent on Amazon Linux 2.
# Idempotent — safe to run multiple times.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/traps.sh
source "$SCRIPT_DIR/lib/traps.sh"
install_err_trap "$EXIT_PREREQUISITES"

AGENT_CTL="/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl"
CONFIG_SOURCE="${SCRIPT_DIR}/cloudwatch-agent-config.json"
PROMETHEUS_SOURCE="${SCRIPT_DIR}/prometheus.yaml"
PROMETHEUS_TARGET="/opt/aws/amazon-cloudwatch-agent/etc/prometheus.yaml"

# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

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

    if [ ! -f "$PROMETHEUS_SOURCE" ]; then
        log_error "Prometheus scrape config not found: $PROMETHEUS_SOURCE"
        exit 1
    fi

    local DECLARED_PATH
    DECLARED_PATH=$(sed -n 's/.*"prometheus_config_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$CONFIG_SOURCE")
    if [ "$DECLARED_PATH" != "$PROMETHEUS_TARGET" ]; then
        log_error "prometheus_config_path in $(basename "$CONFIG_SOURCE") is '${DECLARED_PATH:-<absent>}', but this script installs to '$PROMETHEUS_TARGET'"
        exit 1
    fi

    # Must land before fetch-config: the agent reads prometheus_config_path on start
    log_info "Installing Prometheus scrape config to $PROMETHEUS_TARGET"
    sudo cp "$PROMETHEUS_SOURCE" "$PROMETHEUS_TARGET"

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
